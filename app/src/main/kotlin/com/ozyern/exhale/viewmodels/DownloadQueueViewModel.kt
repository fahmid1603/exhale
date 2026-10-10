/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.viewmodels

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.playback.DownloadUtil
import com.ozyern.exhale.playback.ExoDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import androidx.media3.exoplayer.offline.DownloadManager
import javax.inject.Inject

data class DownloadItem(
    val download: Download,
    val song: Song?,
    val title: String,
)

/**
 * The queue as the page draws it: what is moving, what is waiting, what failed, and how far
 * through the current batch it all is.
 *
 * "The batch" is everything started since the oldest download still in the queue, so the songs
 * that finished a minute ago count towards "12 of 93" while last week's downloads do not.
 */
data class DownloadQueueState(
    val active: List<DownloadItem> = emptyList(),
    val waiting: List<DownloadItem> = emptyList(),
    val failed: List<DownloadItem> = emptyList(),
    val doneInBatch: Int = 0,
) {
    val pending get() = active.size + waiting.size
    val total get() = doneInBatch + pending + failed.size
    val isEmpty get() = active.isEmpty() && waiting.isEmpty() && failed.isEmpty()

    /** The batch's progress: finished songs whole, the ones moving by how far along they are. */
    val progress: Float
        get() = if (total == 0) 0f else
            ((doneInBatch + active.sumOf { it.download.percentDownloaded.coerceIn(0f, 100f).toDouble() / 100.0 }) / total)
                .toFloat().coerceIn(0f, 1f)
}

@HiltViewModel
class DownloadQueueViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadUtil: DownloadUtil,
    database: MusicDatabase,
) : ViewModel() {

    val state: StateFlow<DownloadQueueState> = combine(
        downloadUtil.downloads,
        database.allSongs()
    ) { downloadMap, allSongs ->
        val songMap = allSongs.associateBy { it.id }
        fun item(download: Download) = DownloadItem(
            download = download,
            song = songMap[download.request.id],
            title = download.request.data?.let { Util.fromUtf8Bytes(it) }?.takeIf { it.isNotBlank() }
                ?: songMap[download.request.id]?.song?.title
                ?: download.request.id
        )
        val all = downloadMap.values
        val open = all.filter { it.state != Download.STATE_COMPLETED && it.state != Download.STATE_REMOVING }
        val batchStart = open.minOfOrNull { it.startTimeMs }
        DownloadQueueState(
            active = open.filter { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_RESTARTING }
                .sortedBy { it.startTimeMs }.map(::item),
            waiting = open.filter { it.state == Download.STATE_QUEUED || it.state == Download.STATE_STOPPED }
                .sortedBy { it.startTimeMs }.map(::item),
            failed = open.filter { it.state == Download.STATE_FAILED }
                .sortedBy { it.startTimeMs }.map(::item),
            doneInBatch = if (batchStart == null) 0 else
                all.count { it.state == Download.STATE_COMPLETED && it.startTimeMs >= batchStart },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadQueueState())

    /** When YouTube last asked downloads to slow down, the moment they may carry on; 0 otherwise. */
    val holdUntil: StateFlow<Long> = downloadUtil.resolveHoldUntil

    val downloadsPaused = callbackFlow {
        val listener = object : DownloadManager.Listener {
            override fun onDownloadsPausedChanged(downloadManager: DownloadManager, downloadsPaused: Boolean) {
                trySend(downloadsPaused)
            }
        }
        downloadUtil.downloadManager.addListener(listener)
        trySend(downloadUtil.downloadManager.downloadsPaused)
        awaitClose { downloadUtil.downloadManager.removeListener(listener) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), downloadUtil.downloadManager.downloadsPaused)

    fun pauseAll() {
        val intent = Intent(context, ExoDownloadService::class.java).setAction(ExoDownloadService.PAUSE_DOWNLOADS)
        context.startService(intent)
    }

    fun resumeAll() {
        val intent = Intent(context, ExoDownloadService::class.java).setAction(ExoDownloadService.RESUME_DOWNLOADS)
        context.startService(intent)
    }

    /** Adding a failed download's own request again puts it back in the queue from where it stopped. */
    fun retry(download: Download) {
        DownloadService.sendAddDownload(context, ExoDownloadService::class.java, download.request, false)
        // Asking for a retry is asking for it to happen now, not into a queue that is paused.
        if (downloadUtil.downloadManager.downloadsPaused) resumeAll()
    }

    fun retryFailed() {
        state.value.failed.forEach { retry(it.download) }
    }

    fun removeDownload(id: String) {
        DownloadService.sendRemoveDownload(
            context,
            ExoDownloadService::class.java,
            id,
            false
        )
    }

    fun removeAll() {
        val intent = Intent(context, ExoDownloadService::class.java).setAction(ExoDownloadService.REMOVE_ALL_PENDING_DOWNLOADS)
        context.startService(intent)
    }
}
