package com.tmplayer.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.tmplayer.data.DeviceForm
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.onboarding.SetupChoicesList
import com.tmplayer.ui.onboarding.SetupPlace
import com.tmplayer.ui.onboarding.SetupStep
import kotlinx.coroutines.launch

/**
 * Settings, Setup choices on the desktop: the setup's answers in its order, each changeable here
 * (SetupChoicesList), with "Also in" naming the settings page the row also lives on and taking the
 * viewer there ([onJump]), as the phone's does. The Performance step's settings have no row of
 * their own on the desktop, so that one says it is set only here.
 */
@Composable
internal fun SetupChoicesContent(
    state: ShellState,
    focusStep: SetupStep?,
    onJump: (SetupStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val settings = state.settings
    val titles = SettingsCategory.entries.associateWith { titleOf(it) }
    SetupChoicesList(
        settings = settings,
        form = DeviceForm.Desktop,
        focusStep = focusStep,
        place = { step ->
            val name = SettingsCategory.holding(step)?.let { titles.getValue(it) }
            name?.let { SetupPlace(it) { onJump(step) } }
        },
        onRunSetupAgain = { scope.launch { runCatching { settings.replaySetup() } } },
        modifier = modifier,
    )
}
