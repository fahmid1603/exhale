/*
 * Exhale Project Original
 * ozyern (github.com/ozyern)
 *
 * DownloadQueueScreen author:
 * RajnishKMehta (github.com/RajnishKMehta)
 *
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.EmptyPlaceholder
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.PreferenceGroupTitle
import com.ozyern.exhale.ui.component.SettingsGroupCornerRadius
import com.ozyern.exhale.ui.component.settingsDividerColor
import com.ozyern.exhale.ui.component.settingsGlassGroup
import com.ozyern.exhale.ui.component.settingsGroupSurfaceColor
import com.ozyern.exhale.viewmodels.DownloadItem
import com.ozyern.exhale.viewmodels.DownloadQueueState
import com.ozyern.exhale.viewmodels.DownloadQueueViewModel
import kotlinx.coroutines.delay

/**
 * The queue, as a page you can read at a glance.
 *
 * A card at the top says where the whole batch is — how many of how many, one bar for all of it,
 * and why it is waiting when it is — and holds the three things you do to a queue: pause it, retry
 * what failed, clear it. Under it the songs, grouped by what is happening to them: the ones moving,
 * with their own bar and size; the ones waiting; and the ones that could not be downloaded, each
 * with a retry of its own. It used to be one flat list in which a failed song and a queued one
 * differed by a grey word.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    navController: NavController,
    viewModel: DownloadQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isPaused by viewModel.downloadsPaused.collectAsState()
    val holdUntil by viewModel.holdUntil.collectAsState()

    // A clock that only ticks while there is a countdown to show.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(holdUntil) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= holdUntil) break
            delay(1000)
        }
    }
    val holdSeconds = ((holdUntil - now) / 1000L).toInt().takeIf { it > 0 && state.pending > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.ozyern.exhale.ui.component.transparentTopBarColors(),
                title = { com.ozyern.exhale.ui.component.HeadingStyle { Text(stringResource(R.string.download_queue)) } },
                navigationIcon = {
                    LiquidBackButton(
                        onClick = { navController.popBackStack() },
                        icon = R.drawable.chevron_back,
                    )
                },
            )
        }
    ) { paddingValues ->
        if (state.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyPlaceholder(
                    icon = R.drawable.downloading,
                    text = stringResource(R.string.no_active_downloads)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 120.dp,
                ),
            ) {
                item(key = "summary") {
                    QueueSummary(
                        state = state,
                        isPaused = isPaused,
                        holdSeconds = holdSeconds,
                        onPauseResume = { if (isPaused) viewModel.resumeAll() else viewModel.pauseAll() },
                        onRetryFailed = viewModel::retryFailed,
                        onClear = viewModel::removeAll,
                    )
                }

                queueGroup(
                    key = "active",
                    title = { stringResource(R.string.dq_section_now) },
                    items = state.active,
                ) { item, divider ->
                    QueueRow(item = item, showDivider = divider, live = true, onRemove = { viewModel.removeDownload(item.download.request.id) })
                }

                queueGroup(
                    key = "waiting",
                    title = { stringResource(R.string.dq_section_next) },
                    items = state.waiting,
                ) { item, divider ->
                    QueueRow(item = item, showDivider = divider, live = false, onRemove = { viewModel.removeDownload(item.download.request.id) })
                }

                queueGroup(
                    key = "failed",
                    title = { stringResource(R.string.dq_section_failed) },
                    items = state.failed,
                ) { item, divider ->
                    QueueRow(
                        item = item,
                        showDivider = divider,
                        live = false,
                        onRemove = { viewModel.removeDownload(item.download.request.id) },
                        onRetry = { viewModel.retry(item.download) },
                    )
                }
            }
        }
    }
}

/** One captioned glass group of rows, or nothing at all when it has no rows. */
private fun LazyListScope.queueGroup(
    key: String,
    title: @Composable () -> String,
    items: List<DownloadItem>,
    row: @Composable (DownloadItem, Boolean) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "$key-title") {
        PreferenceGroupTitle(title = "${title()} · ${items.size}")
    }
    // The group is one card, but its rows stay separate lazy items so a 93-song queue is not
    // composed all at once: each row paints its own slice of the card, in a flat fill so the slices
    // meet without a seam (a glass rim per row would draw one between every song).
    itemsIndexed(items, key = { _, it -> "$key-${it.download.request.id}" }) { index, item ->
        val first = index == 0
        val last = index == items.lastIndex
        val r = SettingsGroupCornerRadius
        val shape = RoundedCornerShape(
            topStart = if (first) r else 0.dp,
            topEnd = if (first) r else 0.dp,
            bottomStart = if (last) r else 0.dp,
            bottomEnd = if (last) r else 0.dp,
        )
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(settingsGroupSurfaceColor().copy(alpha = 0.72f))
                .animateItem(),
        ) {
            row(item, !last)
        }
    }
}

@Composable
private fun QueueSummary(
    state: DownloadQueueState,
    isPaused: Boolean,
    holdSeconds: Int?,
    onPauseResume: () -> Unit,
    onRetryFailed: () -> Unit,
    onClear: () -> Unit,
) {
    val progress by animateFloatAsState(targetValue = state.progress, label = "BatchProgress")
    val headline = when {
        isPaused && state.pending > 0 -> stringResource(R.string.dq_headline_paused)
        holdSeconds != null -> stringResource(R.string.dq_headline_holding)
        state.active.isNotEmpty() -> stringResource(R.string.dq_headline_downloading)
        state.waiting.isNotEmpty() -> stringResource(R.string.dq_headline_starting)
        else -> stringResource(R.string.dq_headline_failed)
    }
    val counts = buildString {
        append(stringResource(R.string.dq_done_of, state.doneInBatch, state.total))
        if (state.failed.isNotEmpty()) {
            append(" · ")
            append(stringResource(R.string.dq_failed_count, state.failed.size))
        }
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .settingsGlassGroup()
            .padding(20.dp)
            .animateContentSize(),
    ) {
        // Clear sits beside the headline, out of the way of the two pills: in the row with them
        // it was squeezed into a column of single letters once both pills were showing.
        Row(verticalAlignment = Alignment.Top) {
            Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(stringResource(R.string.dq_clear), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(counts, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (holdSeconds != null && !isPaused) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.dq_holding_note, holdSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        ThinBar(progress = progress, height = 6.dp)
        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Resume stays reachable while paused even with nothing waiting: a retried song would
            // otherwise go back into a paused queue with no way to start it from here.
            if (state.pending > 0 || isPaused) {
                Button(
                    onClick = onPauseResume,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Icon(painterResource(if (isPaused) R.drawable.play else R.drawable.pause), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (isPaused) R.string.dq_resume else R.string.pause))
                }
            }
            if (state.failed.isNotEmpty()) {
                FilledTonalButton(
                    onClick = onRetryFailed,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Icon(painterResource(R.drawable.replay), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dq_retry_failed))
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: DownloadItem,
    showDivider: Boolean,
    live: Boolean,
    onRemove: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val download = item.download
    val progress by animateFloatAsState(
        targetValue = (download.percentDownloaded / 100f).coerceIn(0f, 1f),
        label = "DownloadProgress",
    )
    val artist = item.song?.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }

    val status = when (download.state) {
        Download.STATE_DOWNLOADING -> {
            val total = download.contentLength
            val got = download.bytesDownloaded
            if (total > 0) stringResource(
                R.string.dq_size_progress,
                Formatter.formatShortFileSize(context, got),
                Formatter.formatShortFileSize(context, total),
            ) else if (got > 0) Formatter.formatShortFileSize(context, got)
            else stringResource(R.string.downloading)
        }
        Download.STATE_RESTARTING -> stringResource(R.string.restarting)
        Download.STATE_STOPPED -> stringResource(R.string.paused)
        Download.STATE_FAILED -> stringResource(R.string.failed)
        else -> stringResource(R.string.dq_waiting)
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.song?.song?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
                placeholder = painterResource(R.drawable.music_note),
                error = painterResource(R.drawable.music_note),
            )

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(artist, status).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (download.state == Download.STATE_FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (live) {
                    Spacer(Modifier.height(7.dp))
                    ThinBar(progress = progress, height = 3.dp)
                }
            }

            if (live) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            if (onRetry != null) {
                IconButton(onClick = onRetry) {
                    Icon(painterResource(R.drawable.replay), stringResource(R.string.retry), Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.remove),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                thickness = 1.dp,
                color = settingsDividerColor(),
            )
        }
    }
}

/** A rounded track with a rounded fill: the one progress shape on this page. */
@Composable
private fun ThinBar(progress: Float, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
