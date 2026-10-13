package com.tmplayer.ui.components

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True on a device the system itself calls short of memory (`isLowRamDevice`, the 1 GB television
 * sticks among them). Large pictures are then not decoded at all: a hero draws the soft field of
 * its thumbnail, and nothing advances or moves on its own.
 */
@Composable
fun rememberLowMemory(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val manager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.isLowRamDevice == true
    }
}
