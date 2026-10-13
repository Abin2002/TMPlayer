package com.tmplayer.desktop.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.tmplayer.ui.icons.TmIcons

/**
 * The window buttons' four glyphs, in one place: the Hugeicons the rest of the app draws from
 * (see `TmIcons` and docs/design/icons.md).
 */
object WindowGlyphs {

    /** A short line across the middle. */
    val Minimize: ImageVector get() = TmIcons.WindowMinimize

    /** One square. */
    val Maximize: ImageVector get() = TmIcons.WindowMaximize

    /** Two squares, one behind the other. */
    val Restore: ImageVector get() = TmIcons.WindowRestore

    /** An X. */
    val Close: ImageVector get() = TmIcons.WindowClose
}
