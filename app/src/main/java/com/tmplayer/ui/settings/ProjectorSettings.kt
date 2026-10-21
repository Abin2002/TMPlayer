package com.tmplayer.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.tmplayer.player.ProjectorMode
import com.tmplayer.ui.components.ChoiceLine
import com.tmplayer.ui.components.ChoiceList
import com.tmplayer.ui.components.ChoiceSheet
import com.tmplayer.ui.components.FocusOnOpen
import com.tmplayer.ui.i18n.LocalStrings

/**
 * Settings, Projector: the ceiling on the video resolution, for a projector or a weak stick
 * driving one that a 4K stream would only make stutter. Automatic takes whatever the file
 * offers; the caps keep the decoder inside what the display can show.
 *
 * A pick is saved and closes the sheet, the way the track language sheets do.
 */
@Composable
fun ProjectorSheet(
    current: Int,
    onChange: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalStrings.current
    val focus = FocusRequester()
    fun Modifier.landing(selected: Boolean) = if (selected) focusRequester(focus) else this
    fun pick(mode: Int) {
        onChange(mode)
        onClose()
    }
    val options = listOf(
        ProjectorMode.AUTO to s.settingsProjectorAuto,
        ProjectorMode.P1080 to s.settingsProjector1080p,
        ProjectorMode.P720 to s.settingsProjector720p,
        ProjectorMode.P480 to s.settingsProjector480p,
    )
    ChoiceSheet(title = s.settingsProjector, note = s.settingsProjectorDetail, onDismiss = onClose, onClose = onClose) {
        ChoiceList {
            options.forEach { (mode, label) ->
                val selected = current == mode
                ChoiceLine(
                    title = label,
                    selected = selected,
                    modifier = Modifier.landing(selected),
                ) { pick(mode) }
            }
        }
        FocusOnOpen(focus)
    }
}

/** What the Projector row says is set. */
@Composable
fun projectorSummary(mode: Int): String = when (mode) {
    ProjectorMode.P1080 -> LocalStrings.current.settingsProjector1080p
    ProjectorMode.P720 -> LocalStrings.current.settingsProjector720p
    ProjectorMode.P480 -> LocalStrings.current.settingsProjector480p
    else -> LocalStrings.current.settingsProjectorAuto
}
