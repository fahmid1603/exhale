/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Settings search lands on the setting, not on the top of the page it lives on.
 *
 * A search result asks for the row by its title and opens the page; once the page is up, this
 * finds the row among what is on screen, scrolls its list until the row sits in the upper part of
 * the window, and lights it up briefly so the eye goes straight to it. It reads the semantics tree
 * — the same text a screen reader hears — so every settings page works without each one having to
 * know about search; a lazy list that has not built the row yet is scrolled on until it has.
 */
object SettingsJump {
    var target by mutableStateOf<String?>(null)
        private set

    fun request(title: String) {
        target = title
    }

    internal fun done() {
        target = null
    }
}

@Composable
fun SettingsJumpSpotlight(modifier: Modifier = Modifier) {
    val target = SettingsJump.target
    val root = LocalView.current as? RootForTest
    var spot by remember { mutableStateOf<Rect?>(null) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(target) {
        val wanted = target?.let(::key) ?: return@LaunchedEffect
        android.util.Log.i("JumpDbg", "start wanted=$wanted root=$root")
        val owner = root?.semanticsOwner ?: return@LaunchedEffect SettingsJump.done()
        // Let the page finish arriving: until then the search page is still on screen with the
        // same title on its result row.
        delay(420)
        var found: Rect? = null
        for (attempt in 0 until 14) {
            val nodes = owner.getAllSemanticsNodes(mergingEnabled = false)
            android.util.Log.i("JumpDbg", "attempt=$attempt nodes=${nodes.size} root=${root != null} wanted=$wanted scrollers=${nodes.filter { it.config.contains(SemanticsActions.ScrollBy) }.map { it.boundsInRoot }} texts=${nodes.mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() }.filter { it.contains("rid", true) || it.contains("heme", true) }.take(8)}")
            fun SemanticsNode.says(): Boolean =
                config.getOrNull(SemanticsProperties.Text)?.any { key(it.text) == wanted } == true
            // The title's own node first; failing that the merged tree, where a clickable row has
            // folded its title into itself and is the node that carries the text.
            val textNode = nodes.firstOrNull { it.says() }
                ?: owner.getAllSemanticsNodes(mergingEnabled = true).firstOrNull { it.says() }
            val viewportHeight = owner.rootSemanticsNode.boundsInRoot.height
            if (textNode != null) {
                val row = textNode.ancestor { it.config.contains(SemanticsActions.OnClick) } ?: textNode
                val scroller = row.ancestor { it.config.contains(SemanticsActions.ScrollBy) }
                val bounds = row.boundsInRoot
                val wantedTop = viewportHeight * 0.32f
                val delta = bounds.top - wantedTop
                if (scroller != null && kotlin.math.abs(delta) > 24f && attempt < 12) {
                    scroller.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, delta)
                    delay(120)
                    continue
                }
                found = row.boundsInRoot
                break
            }
            // Not built yet: a lazy list only composes what is near the screen. Move on down.
            val scroller = nodes.firstOrNull { it.config.contains(SemanticsActions.ScrollBy) && it.boundsInRoot.height > viewportHeight * 0.5f }
                ?: break
            scroller.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, viewportHeight * 0.6f)
            delay(140)
        }
        SettingsJump.done()
        if (found == null) return@LaunchedEffect
        spot = found
        glow.snapTo(0f)
        glow.animateTo(1f, tween(220))
        delay(900)
        glow.animateTo(0f, tween(700))
        spot = null
    }

    val color = MaterialTheme.colorScheme.primary
    val rect = spot ?: return
    Canvas(modifier.fillMaxSize()) {
        val a = glow.value
        if (a <= 0f) return@Canvas
        val inset = 6.dp.toPx()
        val r = Rect(rect.left + inset, rect.top + 2.dp.toPx(), rect.right - inset, rect.bottom - 2.dp.toPx())
        val corner = CornerRadius(16.dp.toPx())
        drawRoundRect(color.copy(alpha = 0.16f * a), Offset(r.left, r.top), Size(r.width, r.height), corner)
        drawRoundRect(
            color.copy(alpha = 0.7f * a), Offset(r.left, r.top), Size(r.width, r.height), corner,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private fun key(text: String): String = text.trim().lowercase(Locale.ROOT)

private fun SemanticsNode.ancestor(predicate: (SemanticsNode) -> Boolean): SemanticsNode? {
    var node = parent
    while (node != null) {
        if (predicate(node)) return node
        node = node.parent
    }
    return null
}
