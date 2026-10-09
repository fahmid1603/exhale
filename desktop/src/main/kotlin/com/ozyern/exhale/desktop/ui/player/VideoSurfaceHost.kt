/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.desktop.player.DesktopPlayer
import java.awt.Rectangle

/**
 * The window mpv paints into, kept for as long as there is something to watch.
 *
 * It lives at the root of the app rather than inside the player screen, and that is the whole point:
 * a surface inside the player is destroyed the moment the player folds away, and mpv cannot be handed
 * a new window without being rebuilt — which means re-resolving the stream and starting the song over.
 * Here it simply shrinks to a speck when no player is open to show it, and grows back into place when
 * one is. mpv follows the resize on its own; nothing reloads, and the song never stops.
 *
 * The controls go over the picture in a window of their own ([VideoControlsOverlay]), and only while
 * there is a picture on show to put them on.
 */
@Composable
fun VideoSurfaceHost(player: DesktopPlayer) {
    val slot by VideoSlot.rect
    val state by player.state.collectAsState()
    val density = LocalDensity.current
    val surfaceBounds = remember { mutableStateOf<Rectangle?>(null) }

    Box(Modifier.fillMaxSize()) {
        val place = slot
        Box(
            Modifier
                .offset { IntOffset(place?.left ?: 0, place?.top ?: 0) }
                .size(
                    // A speck rather than nothing: the window has to stay alive for mpv to keep
                    // drawing into it, and one pixel in the corner of the title bar is the price.
                    width = with(density) { (place?.width ?: 1).toDp() },
                    height = with(density) { (place?.height ?: 1).toDp() },
                ),
        ) {
            VideoSurface(
                Modifier.fillMaxSize(),
                onBounds = { surfaceBounds.value = it },
                onGone = { player.detachVideoSurface(it) },
                onSurface = { player.attachVideoSurface(it) },
            )
        }

        if (place != null) {
            VideoControlsOverlay(surfaceBounds, state, player)
        }
    }
}
