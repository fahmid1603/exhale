/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The control bar follows the Windows app's video overlay (desktop/ui/player/VideoOverlay.kt).
 */

package com.ozyern.exhale.ui.player

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.utils.YTPlayerUtils
import kotlinx.coroutines.delay

/**
 * The music video on the whole screen, as a video player: landscape, edge to edge, system bars
 * away, and controls that come up on a tap and leave on their own.
 *
 * Only this is a player. In the Now Playing screen the video is the cover — silent pictures over
 * the song with nothing to operate — and full screen is where the transport belongs. The sound is
 * still the song's own stream: every control here drives the song, and the picture follows it.
 */
@Composable
internal fun FullscreenMusicVideo(
    stream: YTPlayerUtils.VideoStream,
    onExit: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val activity = LocalContext.current as? android.app.Activity

    // Landscape while it is up, whichever way the phone was held; back to before on the way out.
    DisposableEffect(activity) {
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (before != null) activity.requestedOrientation = before }
    }

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val view = LocalView.current
        DisposableEffect(window) {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                WindowInsetsControllerCompat(window, view).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose { }
        }
        BackHandler(onBack = onExit)

        // Shown on arrival and on any tap; gone three seconds after the last one while it plays.
        var controls by remember { mutableStateOf(true) }
        var touched by remember { mutableIntStateOf(0) }
        LaunchedEffect(controls, touched, isPlaying) {
            if (controls && isPlaying) {
                delay(3_000)
                controls = false
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controls = !controls; touched++ },
                        // Double-tap either half to skip ten seconds, as video players do.
                        onDoubleTap = { at ->
                            val back = at.x < size.width / 2f
                            val target = player.currentPosition + if (back) -10_000L else 10_000L
                            player.seekTo(target.coerceIn(0L, player.duration.coerceAtLeast(0L)))
                            controls = true
                            touched++
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            MusicVideoSurface(
                stream = stream,
                positionMs = { player.currentPosition },
                isPlaying = isPlaying,
                fitInside = true,
                onError = onExit,
                modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(
                visible = controls,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(260)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    // Top: what is playing, and the way out.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                            .systemBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = metadata?.title.orEmpty(),
                                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = metadata?.artists?.joinToString { it.name }.orEmpty(),
                                style = TextStyle(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        VideoGlyph(R.drawable.fullscreen_exit, 24.dp, onClick = onExit)
                    }

                    // Bottom: the clock either side of the line, the transport under it.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))))
                            .systemBarsPadding()
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xB3111014))
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        ) {
                            VideoScrubber(player = player, onTouched = { touched++ })
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VideoGlyph(R.drawable.skip_previous, 26.dp) { player.seekToPrevious(); touched++ }
                                Spacer(Modifier.width(22.dp))
                                VideoGlyph(R.drawable.replay, 26.dp, badge = "10") {
                                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); touched++
                                }
                                Spacer(Modifier.width(22.dp))
                                VideoGlyph(if (isPlaying) R.drawable.pause else R.drawable.play, 38.dp) {
                                    if (player.isPlaying) player.pause() else player.play(); touched++
                                }
                                Spacer(Modifier.width(22.dp))
                                VideoGlyph(R.drawable.replay, 26.dp, badge = "10", mirrored = true) {
                                    val end = player.duration.coerceAtLeast(0L)
                                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(end)); touched++
                                }
                                Spacer(Modifier.width(22.dp))
                                VideoGlyph(R.drawable.skip_next, 26.dp, enabled = canSkipNext) { player.seekToNext(); touched++ }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoScrubber(player: androidx.media3.common.Player, onTouched: () -> Unit) {
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }
    val shown = if (dragging) (dragValue * duration).toLong() else position
    Row(verticalAlignment = Alignment.CenterVertically) {
        VideoTime(formatVideoTime(shown))
        Slider(
            value = if (dragging) dragValue else if (duration > 0) position.toFloat() / duration else 0f,
            onValueChange = { dragging = true; dragValue = it; onTouched() },
            onValueChangeFinished = {
                if (duration > 0) player.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        VideoTime(if (duration > 0) "−" + formatVideoTime(duration - shown) else formatVideoTime(0L))
    }
}

@Composable
private fun VideoTime(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        color = Color.White.copy(alpha = 0.8f),
    )
}

/** A bare white glyph; [badge] is drawn small in its middle (the "10" of a ten-second skip). */
@Composable
private fun VideoGlyph(
    icon: Int,
    size: Dp,
    enabled: Boolean = true,
    badge: String? = null,
    mirrored: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size + 16.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(bounded = false, color = Color.White),
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.32f),
            modifier = Modifier
                .size(size)
                .graphicsLayer { if (mirrored) scaleX = -1f },
        )
        if (badge != null) {
            Text(
                text = badge,
                style = TextStyle(fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(top = (size.value * 0.12f).dp),
            )
        }
    }
}

private fun formatVideoTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
