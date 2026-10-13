package com.tmplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * The gradients that sit between a picture and the text on it.
 *
 * Text on a picture never gets a plate or a box. It gets a long, soft fade instead, so the
 * picture runs into the page with no edge anywhere. Two- and three-stop fades band visibly over a
 * few hundred dp, which is why the long ones here carry seven stops on an eased curve: almost
 * nothing for the first third, then most of the opacity arriving late, under the text.
 *
 * [bottomLong] and [startLong] fade into [Tone.scrim], the page's own background, so they follow
 * light and dark. [tileFoot] is black in both themes, because it sits inside a tile, where the
 * page is not what the picture meets.
 *
 * Apply one with `Modifier.background(brush)` on a box drawn over the picture.
 */
object Scrims {

    /**
     * The long curve, as (position, alpha) pairs from the clear end to the solid end. At its foot
     * it reaches 0.84 of the background by the last sixth, which is what keeps text there at
     * 4.5:1 over any picture.
     */
    internal val LongCurve: List<Pair<Float, Float>> = listOf(
        0f to 0f,
        0.16f to 0.04f,
        0.32f to 0.14f,
        0.50f to 0.34f,
        0.68f to 0.62f,
        0.84f to 0.84f,
        1f to 1f,
    )

    /** The foot of a tile, as (position, alpha) pairs of black down the tile's height. */
    internal val FootCurve: List<Pair<Float, Float>> = listOf(
        0f to 0f,
        0.45f to 0f,
        0.60f to 0.70f,
        1f to 0.95f,
    )

    /**
     * The fade at the foot of a hero or a page backdrop: clear above the last [height] of
     * whatever it is drawn across, then [LongCurve] down to the background at the bottom edge.
     */
    @Composable
    @ReadOnlyComposable
    fun bottomLong(height: Dp): Brush {
        val reach = with(LocalDensity.current) { height.toPx() }
        return LongBrush(Tone.scrim, reach, Edge.Bottom)
    }

    /**
     * The same curve across a wide backdrop, solid at the start edge and clear by [reach] of the
     * width, for text that sits on the start half. It starts from the right in a right to left
     * layout, where the text does.
     */
    @Composable
    @ReadOnlyComposable
    fun startLong(reach: Float = START_REACH): Brush {
        val edge = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Edge.Right else Edge.Left
        return LongBrush(Tone.scrim, -reach, edge)
    }

    /**
     * The foot of a tile: clear for the top 45 per cent, black at 70 per cent by 0.6 of the
     * height and at 95 per cent at the bottom, for a title and a progress line drawn on the art.
     */
    val tileFoot: Brush = Brush.verticalGradient(
        colorStops = FootCurve.map { (at, alpha) -> at to Color.Black.copy(alpha = alpha) }.toTypedArray(),
    )

    /** How far across a backdrop [startLong] reaches by default: past the text column. */
    const val START_REACH = 0.6f

    private enum class Edge { Bottom, Left, Right }

    /**
     * [LongCurve] in [color], solid at [edge] and clear [reach] away from it: a length in pixels
     * when positive, a share of the width when negative (the start fade's form).
     *
     * A brush that is handed its size, rather than a plain gradient with fixed end points, so the
     * same brush serves a box of any height and stays clear above the fade.
     */
    private class LongBrush(val color: Color, val reach: Float, val edge: Edge) : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val length = if (reach < 0f) size.width * -reach else reach
            val (from, to) = when (edge) {
                Edge.Bottom -> Offset(0f, size.height - length) to Offset(0f, size.height)
                Edge.Left -> Offset(length, 0f) to Offset(0f, 0f)
                Edge.Right -> Offset(size.width - length, 0f) to Offset(size.width, 0f)
            }
            return LinearGradientShader(
                from = from,
                to = to,
                colors = LongCurve.map { color.copy(alpha = it.second) },
                colorStops = LongCurve.map { it.first },
            )
        }

        override fun equals(other: Any?): Boolean =
            other is LongBrush && other.color == color && other.reach == reach && other.edge == edge

        override fun hashCode(): Int = (color.hashCode() * 31 + reach.hashCode()) * 31 + edge.hashCode()
    }
}
