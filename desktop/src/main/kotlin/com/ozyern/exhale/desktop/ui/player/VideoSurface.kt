/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import com.sun.jna.Native
import kotlinx.coroutines.delay
import java.awt.Canvas
import java.awt.Rectangle

/**
 * Where a video is drawn. mpv paints straight into this window rather than through Compose: it is the
 * one thing on screen the app doesn't draw itself, so the glass can't bend it and nothing composites
 * over it.
 *
 * Two things are reported back out. The native handle, because a window only has one once it's really
 * on screen and the player is waiting for it before it can start mpv here. And where the surface sits
 * on the screen, so the controls — which live in a window of their own, the only thing that can be
 * above this one — can be laid exactly over it.
 */
@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    onBounds: (Rectangle?) -> Unit = {},
    onGone: (Long) -> Unit = {},
    onSurface: (Long) -> Unit,
) {
    val latest by rememberUpdatedState(onSurface)
    val reportBounds by rememberUpdatedState(onBounds)
    val reportGone by rememberUpdatedState(onGone)
    var handed by remember { mutableLongStateOf(0L) }
    val canvas = remember {
        Canvas().apply {
            background = java.awt.Color.BLACK
            isFocusable = false
        }
    }
    // A window only has a handle once it's really on screen, and how long that takes is not ours to
    // decide — so it's asked for until it's there rather than hooked to one moment.
    LaunchedEffect(canvas) {
        repeat(60) {
            if (canvas.isDisplayable) {
                val handle = runCatching { Native.getComponentID(canvas) }.getOrElse {
                    println("Exhale video: no handle for the surface — ${it.message}")
                    0L
                }
                if (handle != 0L) {
                    handed = handle
                    latest(handle)
                    return@LaunchedEffect
                }
            }
            delay(50)
        }
        println("Exhale video: the surface never became displayable")
    }

    // Watched rather than listened for: a window moving doesn't tell its children, and the overlay
    // being a frame behind while the window is dragged is better than it being in the wrong place.
    LaunchedEffect(canvas) {
        var last: Rectangle? = null
        while (true) {
            val now = if (canvas.isShowing) {
                runCatching { Rectangle(canvas.locationOnScreen, canvas.size) }.getOrNull()
            } else {
                null
            }
            if (now != last) {
                last = now
                reportBounds(now)
            }
            delay(60)
        }
    }
    // Going away is news. The window this reports is about to be destroyed by the toolkit, and mpv
    // is still drawing into it until it's told otherwise.
    DisposableEffect(canvas) {
        onDispose {
            reportBounds(null)
            if (handed != 0L) reportGone(handed)
        }
    }
    SwingPanel(background = Color.Black, factory = { canvas }, modifier = modifier)
}
