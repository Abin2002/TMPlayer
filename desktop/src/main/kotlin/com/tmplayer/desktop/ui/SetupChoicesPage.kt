package com.tmplayer.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmplayer.data.DeviceForm
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.onboarding.SetupChoicesList
import com.tmplayer.ui.onboarding.SetupPlace
import com.tmplayer.ui.onboarding.SetupStep
import kotlinx.coroutines.launch

/**
 * Settings, Setup choices on the desktop: the setup's answers in its order, each changeable here
 * (SetupChoicesList), with "Also in" taking the viewer to the row on the settings page
 * ([onJump]). The Performance step's settings have no row of their own on the desktop, so that
 * one says it is set only here.
 */
@Composable
internal fun SetupChoicesPage(
    state: ShellState,
    focusStep: SetupStep?,
    onBack: () -> Unit,
    onJump: (SetupStep) -> Unit,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val settings = state.settings
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            PageHeader(
                title = s.setupChoicesTitle,
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(TmIcons.ArrowBack, contentDescription = s.aboutBackToSettings)
                    }
                },
            )
            SetupChoicesList(
                settings = settings,
                form = DeviceForm.Desktop,
                focusStep = focusStep,
                place = { step ->
                    val name = when (step) {
                        SetupStep.Posters -> s.metadataTitle
                        SetupStep.Storage -> s.settingsPlayback
                        SetupStep.Performance, SetupStep.MobileData, SetupStep.Device -> null
                    }
                    name?.let { SetupPlace(it) { onJump(step) } }
                },
                onRunSetupAgain = { scope.launch { runCatching { settings.replaySetup() } } },
                modifier = Modifier.widthIn(max = 760.dp).padding(horizontal = pageMargin(), vertical = 8.dp),
            )
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
