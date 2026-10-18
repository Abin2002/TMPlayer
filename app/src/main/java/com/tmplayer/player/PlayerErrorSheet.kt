package com.tmplayer.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.tmplayer.ui.settings.SendLogsButton
import com.tmplayer.ui.theme.TMPlayerTheme

/** The parts of the player's failure sheet that are built in code rather than in activity_player.xml. */
object PlayerErrorSheet {

    /**
     * "Send logs", the shared Compose button ([SendLogsButton]), hosted under Open in another app
     * in the sheet's button column, last and quietest. Hidden until a failure shows it; the
     * activity and its render test both build it here, so the test draws what the viewer sees.
     */
    fun addSendLogs(openWith: View?): View? {
        val column = openWith?.parent as? ViewGroup ?: return null
        val logs = ComposeView(column.context).apply {
            setContent { TMPlayerTheme { SendLogsButton(Modifier.padding(top = 10.dp)) } }
            visibility = View.GONE
        }
        column.addView(logs, column.indexOfChild(openWith) + 1, ViewGroup.LayoutParams(-2, -2))
        return logs
    }
}
