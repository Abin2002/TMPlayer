package com.tmplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Fades a horizontal row out over its side margins ([start] and [end], the row's own padding), so
 * cards scrolled past the margin melt away instead of being cut off hard against the sidebar or the
 * window's edge. Each side fades only while there is something on that side to scroll back to: a
 * row at rest starts on its first card, whole. The start side comes in with the first few pixels
 * of scroll, so it never pops.
 */
fun Modifier.edgeFade(state: LazyListState, start: Dp, end: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val startPx = start.toPx()
        val endPx = end.toPx()
        val scrolled = if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / startPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val back = if (state.canScrollBackward) scrolled else 0f
        val on = if (state.canScrollForward) 1f else 0f
        val rtl = layoutDirection == LayoutDirection.Rtl
        val left = if (rtl) on else back
        val right = if (rtl) back else on
        val leftPx = if (rtl) endPx else startPx
        val rightPx = if (rtl) startPx else endPx
        if (left > 0f && leftPx > 0f) {
            drawRect(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 1f - left), 1f to Color.Black, startX = 0f, endX = leftPx),
                size = Size(leftPx, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (right > 0f && rightPx > 0f) {
            drawRect(
                Brush.horizontalGradient(0f to Color.Black, 1f to Color.Black.copy(alpha = 1f - right), startX = size.width - rightPx, endX = size.width),
                topLeft = Offset(size.width - rightPx, 0f),
                size = Size(rightPx, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * Keeps a card that takes focus (the remote's or the keyboard's) inside a row's side margins rather
 * than at its very edge, where [edgeFade] would fade it: the row scrolls just far enough to stand
 * the card between [start] and [end] of the row's own edges.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithinMargins(start: Dp, end: Dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val startPx = with(density) { start.toPx() }
    val endPx = with(density) { end.toPx() }
    val spec = remember(startPx, endPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val lead = startPx
                val trail = containerSize - endPx
                return when {
                    // Wider than the room between the margins: line its leading edge up.
                    size > trail - lead -> offset - lead
                    offset < lead -> offset - lead
                    offset + size > trail -> offset + size - trail
                    else -> 0f
                }
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
