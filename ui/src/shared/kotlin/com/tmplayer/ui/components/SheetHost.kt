package com.tmplayer.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The platform's bottom sheet, for shared screens that want one: a phone hands in its own
 * (PhoneSheet, with its hairline and drag handle); a television and the desktop leave it null, and
 * the screen shows the same content in place instead.
 */
fun interface SheetHost {
    @Composable
    fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit)
}

/** The [SheetHost] in force, or null where the platform shows sheets' content in place. */
val LocalSheetHost = staticCompositionLocalOf<SheetHost?> { null }
