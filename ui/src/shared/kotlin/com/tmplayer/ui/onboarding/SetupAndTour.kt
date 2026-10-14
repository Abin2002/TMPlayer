package com.tmplayer.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tmplayer.data.DeviceFit
import com.tmplayer.data.Recommendation
import com.tmplayer.data.SettingsStore
import com.tmplayer.ui.components.deviceForm

/**
 * What a fresh sign in walks through (CP69): the setup's choices ([SetupScreen]), then the quick
 * tour ([TourScreen]), on one count. Skip all, on either part, ends both. Back from the tour's first
 * page goes to the setup's last step. With no setup steps (a build without posters, an account
 * without folders, on a device where nothing else applies) it is the tour alone.
 *
 * [onDone] is called once, at the end: the host marks the setup and the tour done.
 */
@Composable
fun SetupAndTour(
    settings: SettingsStore,
    steps: List<SetupStep>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    unreachableFavourites: Set<Long> = emptySet(),
    fit: DeviceFit = DeviceFit.Fine,
    recommendations: List<Recommendation> = emptyList(),
) {
    val form = deviceForm()
    val pages = remember(form) { Tour.pages(form) }
    val setup = remember(steps) { steps.takeIf { it.isNotEmpty() }?.let { SetupState(it) } }
    val tour = remember(pages) { TourProgress(pages) }
    var touring by remember { mutableStateOf(setup == null) }
    val total = steps.size + pages.size

    if (!touring && setup != null) {
        SetupScreen(
            settings,
            setup,
            onDone = { touring = true },
            modifier = modifier,
            unreachableFavourites = unreachableFavourites,
            fit = fit,
            recommendations = recommendations,
            total = total,
            moreAfter = true,
            onSkipAll = onDone,
        )
    } else {
        TourScreen(
            tour,
            onDone = onDone,
            modifier = modifier,
            before = steps.size,
            total = total,
            onBackFromFirst = { if (setup != null) touring = false else onDone() },
        )
    }
}

/** The quick tour on its own: taken from the offer to an updated install, or opened from Settings. */
@Composable
fun TourOnly(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val form = deviceForm()
    val tour = remember(form) { TourProgress(Tour.pages(form)) }
    TourScreen(tour, onDone = onDone, modifier = modifier)
}
