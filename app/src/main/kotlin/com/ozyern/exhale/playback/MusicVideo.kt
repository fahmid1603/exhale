/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.utils.YTPlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * The music video for whatever is playing, when YouTube has one.
 *
 * A plain "switch to video" swaps the audio for the video's soundtrack and stops there — you
 * still look at a cover. This finds the video and hands back a picture-only stream, so the player
 * can show it over the cover while the song keeps playing from its own, better, audio stream.
 *
 * Matching is deliberately strict: the same song title once the decorations are stripped, an
 * artist in common, and a running time close enough that the video and the song can be held in
 * step. A lyric video or a fan upload that passes all three is still that song; a live cut or a
 * cover version fails the running time or the artist, and no video beats the wrong one.
 */
object MusicVideo {
    data class Found(val videoId: String, val stream: YTPlayerUtils.VideoStream, val offsetMs: Long)

    /** songId → video id, or "" when we looked and there is none. */
    private val videoIds = ConcurrentHashMap<String, String>()
    private val streams = ConcurrentHashMap<String, Pair<YTPlayerUtils.VideoStream, Long>>()

    private const val STREAM_TTL_MS = 4 * 60 * 60 * 1000L
    private const val MAX_DURATION_DRIFT_S = 45

    /**
     * A stream that stopped answering. YouTube's URLs go stale partway through a long video or after
     * enough seeking, and the cure is a new one — so it is dropped, and the next [find] asks again.
     */
    fun forgetStream(videoId: String) {
        streams.remove(videoId)
    }

    /** Whether we already know there is no video, so the switch can be hidden without a request. */
    fun knownMissing(songId: String): Boolean = videoIds[songId] == ""

    /** Whether we already know there is one. */
    fun knownPresent(songId: String): Boolean = !videoIds[songId].isNullOrEmpty()

    /**
     * Whether the song has a video, found out without fetching the video's stream: one search, its
     * answer kept, so the Video switch is offered only for songs that have something to switch to.
     */
    suspend fun has(song: MediaMetadata): Boolean = withContext(Dispatchers.IO) {
        videoIds[song.id]?.let { return@withContext it.isNotEmpty() }
        val found = runCatching { search(song) }.getOrElse { return@withContext false }
        videoIds[song.id] = found.orEmpty()
        found != null
    }

    suspend fun find(song: MediaMetadata): Found? = withContext(Dispatchers.IO) {
        val videoId = videoIds[song.id]?.takeIf { it.isNotEmpty() }
            ?: if (videoIds[song.id] == "") return@withContext null else search(song)
        videoIds[song.id] = videoId.orEmpty()
        videoId ?: return@withContext null

        val cached = streams[videoId]?.takeIf { System.currentTimeMillis() - it.second < STREAM_TTL_MS }?.first
        val stream = cached ?: YTPlayerUtils.videoStreamForDisplay(videoId).getOrNull()?.also {
            streams[videoId] = it to System.currentTimeMillis()
        } ?: return@withContext null
        Found(videoId, stream, offsetMs = 0L)
    }

    private suspend fun search(song: MediaMetadata): String? {
        val artist = song.artists.firstOrNull()?.name.orEmpty()
        val wanted = normalize(song.title)
        if (wanted.isBlank()) return null
        val results = YouTube.search("${song.title} $artist", YouTube.SearchFilter.FILTER_VIDEO)
            .getOrNull()?.items.orEmpty()
            .filterIsInstance<SongItem>()
        return results.take(8).firstOrNull { video ->
            val title = normalize(video.title)
            val titleMatches = title == wanted || title.startsWith("$wanted ") || title.contains(" $wanted") ||
                title.contains(wanted)
            val artistMatches = song.artists.any { a ->
                val name = normalize(a.name)
                name.isNotBlank() && (video.artists.any { normalize(it.name).contains(name) } || title.contains(name))
            }
            val durationOk = video.duration == null || song.duration <= 0 ||
                abs(video.duration!! - song.duration) <= MAX_DURATION_DRIFT_S
            titleMatches && artistMatches && durationOk && video.id != song.id
        }?.id ?: song.id.takeIf { results.any { it.id == song.id } }
    }

    /** Lowercase, no accents, and none of the "(Official Video)" or "[Remastered]" decorations. */
    private fun normalize(text: String): String {
        val noBrackets = text.replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
        val noFeat = noBrackets.replace(Regex("""\b(feat|ft|featuring)\.?\b.*""", RegexOption.IGNORE_CASE), " ")
        val plain = Normalizer.normalize(noFeat, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
        return plain.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
    }
}
