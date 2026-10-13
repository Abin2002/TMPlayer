package com.tmplayer.ui.theme

import androidx.compose.runtime.Composable

/**
 * True when the system has its animations turned off. The JVM has no portable way to ask a
 * desktop that question, so the desktop moves; see [Motion].
 */
@Composable
fun rememberReducedMotion(): Boolean = false
