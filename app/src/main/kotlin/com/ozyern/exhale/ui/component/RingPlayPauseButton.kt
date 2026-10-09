/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import kotlinx.coroutines.flow.collectLatest

/**
 * Play and pause in a ring that is the song's progress: a soft frosted disc, a thin track around it,
 * and a brighter arc that fills clockwise from the top as the song plays. Loading, the arc turns
 * instead. The progress is read while drawing, so the ring moves without recomposing anything.
 *
 * The arc glides between the player's once-a-second position updates instead of stepping, and runs
 * back to the top when a new song starts rather than jumping there.
 */
@Composable
fun RingPlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color(0xFF121216)
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "ringPress",
    )

    // The drawn fraction, eased toward the real one: the player reports the position about once a
    // second, and a ring that jumped by a degree each second looked like it was ticking, not playing.
    val shown = remember { Animatable(progress().coerceIn(0f, 1f)) }
    val latest by rememberUpdatedState(progress)
    LaunchedEffect(Unit) {
        snapshotFlow { latest().coerceIn(0f, 1f) }.collectLatest { target ->
            val jump = kotlin.math.abs(target - shown.value)
            // A seek or a new song: travel there briskly. Ordinary playback: glide for a second.
            if (jump > 0.05f) {
                shown.animateTo(target, spring(dampingRatio = 1f, stiffness = 300f))
            } else {
                shown.animateTo(target, tween(1_000, easing = LinearEasing))
            }
        }
    }

    // Turned only while loading: an endless animation running behind a still button would redraw
    // the dock on every frame for nothing.
    val turn = remember { Animatable(0f) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            while (true) turn.animateTo(turn.value + 360f, tween(900, easing = LinearEasing))
        }
    }
    val loading by rememberUpdatedState(isLoading)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val give = 1f - 0.08f * press
                scaleX = give
                scaleY = give
            }
            .drawBehind {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                // The disc: frosted, a shade brighter while held.
                drawCircle(ink.copy(alpha = 0.08f + 0.05f * press))
                // The track.
                drawArc(
                    color = ink.copy(alpha = 0.20f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke),
                )
                if (loading) {
                    drawArc(
                        color = ink.copy(alpha = 0.85f),
                        startAngle = turn.value - 90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                } else {
                    val sweep = 360f * shown.value
                    if (sweep > 0.5f) {
                        drawArc(
                            color = ink.copy(alpha = 0.90f),
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressed = true
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    pressed = false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (fadeIn(tween(120)) + scaleIn(initialScale = 0.6f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)))
                    .togetherWith(fadeOut(tween(90)) + scaleOut(targetScale = 1.25f, animationSpec = tween(90)))
            },
            label = "ringPlayIcon",
        ) { playing ->
            Icon(
                painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                tint = ink,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}
