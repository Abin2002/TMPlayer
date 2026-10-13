package com.tmplayer.ui.lock

import com.tmplayer.data.AppLockSettings
import com.tmplayer.i18n.L

/**
 * The App lock section's words, worked out once for the phone, the television and the desktop.
 */
object AppLockWords {

    /** The line under "App lock" on Settings' category list. */
    fun summary(lock: AppLockSettings): String = when {
        lock.kids -> L.lockSummaryKids(count = lock.kidsChats.size)
        lock.pinSet -> L.lockSummaryOn
        else -> L.lockSummaryOff
    }

    /** The line under "Allowed chats only": what it does now, or why it cannot be switched on yet. */
    fun kidsDetail(lock: AppLockSettings): String = when {
        !lock.pinSet -> L.lockKidsNeedsPin
        lock.kids -> L.lockKidsOn
        else -> L.lockKidsOff
    }

    /** The line under the screensaver's row, for [minutes] (0 is off). */
    fun screensaverDetail(minutes: Int): String =
        if (minutes <= 0) L.screensaverOffDetail else L.screensaverDetail(minutes = minutes)

    /** The screensaver's value beside its row: "Off" or "5 min". */
    fun screensaverValue(minutes: Int): String =
        if (minutes <= 0) L.commonOff else L.screensaverValue(minutes = minutes)
}
