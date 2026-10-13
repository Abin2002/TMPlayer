package com.tmplayer.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The window buttons' four glyphs, in one place so they can follow the app's icon set: a 24 unit
 * grid, a 1.5 stroke with round caps and joins, no fill (the Hugeicons Stroke Rounded look).
 */
object WindowGlyphs {

    /** A short line across the middle. */
    val Minimize: ImageVector by lazy { glyph("WindowMinimize") { moveTo(6f, 12f); lineTo(18f, 12f) } }

    /** One square. */
    val Maximize: ImageVector by lazy { glyph("WindowMaximize") { roundRect(6f, 6f, 18f, 18f) } }

    /** Two squares, the one behind showing only its top and right sides. */
    val Restore: ImageVector by lazy {
        glyph("WindowRestore") {
            roundRect(5f, 8f, 16f, 19f)
            moveTo(8f, 8f)
            lineTo(8f, 7f)
            arcTo(2f, 2f, 0f, false, true, 10f, 5f)
            lineTo(17f, 5f)
            arcTo(2f, 2f, 0f, false, true, 19f, 7f)
            lineTo(19f, 14f)
            arcTo(2f, 2f, 0f, false, true, 17f, 16f)
            lineTo(16f, 16f)
        }
    }

    /** An X. */
    val Close: ImageVector by lazy {
        glyph("WindowClose") {
            moveTo(6.5f, 6.5f)
            lineTo(17.5f, 17.5f)
            moveTo(17.5f, 6.5f)
            lineTo(6.5f, 17.5f)
        }
    }

    private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float = 2f) {
        moveTo(left + r, top)
        lineTo(right - r, top)
        arcTo(r, r, 0f, false, true, right, top + r)
        lineTo(right, bottom - r)
        arcTo(r, r, 0f, false, true, right - r, bottom)
        lineTo(left + r, bottom)
        arcTo(r, r, 0f, false, true, left, bottom - r)
        lineTo(left, top + r)
        arcTo(r, r, 0f, false, true, left + r, top)
        close()
    }

    private fun glyph(name: String, body: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = body,
            )
        }.build()
}
