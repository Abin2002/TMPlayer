package com.tmplayer.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tmplayer.data.ChatGate
import com.tmplayer.data.NewVideoAlerts
import com.tmplayer.data.SupportReminder
import com.tmplayer.data.Td
import com.tmplayer.desktop.os.DesktopTransferNotifier
import com.tmplayer.i18n.L
import com.tmplayer.platform.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the desktop checks while it runs, minimised too, once signed in: new videos in the starred
 * chats every [EVERY_MS] ([NewVideoAlerts], behind its switch), and whether the "Don't forget to
 * sponsor" notification is due ([SupportReminder.nudgeDue]). Both go out as system notifications
 * ([DesktopTransferNotifier.notices]); a press on one, where the system passes presses back,
 * raises the window and opens the chat or the Buy Me a Coffee window. Nothing is posted while
 * the window has the focus: the lists already show what came in.
 */
@Composable
fun DesktopChecks(state: ShellState) {
    LaunchedEffect(state) {
        delay(FIRST_AFTER_MS)
        while (true) {
            runCatching { checkOnce(state) }.onFailure { Logger.w(TAG, "check failed: ${it.message}") }
            delay(EVERY_MS)
        }
    }
}

private suspend fun checkOnce(state: ShellState) = withContext(Dispatchers.IO) {
    val settings = state.settings
    val notices = DesktopTransferNotifier.notices
    val focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow != null

    if (settings.newVideoAlertsNow()) {
        val session = withTimeoutOrNull(CONNECT_WAIT_MS) { Td.awaitConnectedSession() }
        val alert = session?.let { NewVideoAlerts.check(it.client, settings, ChatGate.awaitReady()) }
        if (alert != null && !focused) {
            notices?.notice(alert.title, alert.body) {
                DesktopTransferNotifier.raise()
                java.awt.EventQueue.invokeLater {
                    val chat = alert.chatId?.let(state::chatOf)
                    state.go(Destination.Chats)
                    if (chat != null) state.openChat(chat)
                }
            }
        }
    }

    if (!focused && notices != null && SupportReminder.claimNudge(settings, System.currentTimeMillis())) {
        notices.notice(L.notifySponsorTitle, L.notifySponsorBody) {
            DesktopTransferNotifier.raise()
            SupportReminder.requestOpen()
        }
    }
}

private const val TAG = "DesktopChecks"

/** About every 45 minutes, between the half hour and the hour the phone's three are stretched to. */
private const val EVERY_MS = 45L * 60 * 1000

/** Well after the start, which has the chat list to load first. */
private const val FIRST_AFTER_MS = 3L * 60 * 1000

private const val CONNECT_WAIT_MS = 60_000L
