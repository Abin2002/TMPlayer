package com.tmplayer.cast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tmplayer.ui.browse.PageDiscButton
import com.tmplayer.ui.components.ChoiceLine
import com.tmplayer.ui.components.ChoiceSheet
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import kotlinx.coroutines.delay

/**
 * The Cast picker, the app's picker sheet: the Chromecasts and Google TVs on the Wi-Fi, found while
 * it is open, the one in use marked, and Stop casting under them during a cast. Picking one starts
 * the session; the player (or the next video played) goes to the TV once it is up.
 */
@Composable
fun CastPickerSheet(support: CastSupport, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    DisposableEffect(support) {
        support.startDiscovery()
        onDispose { support.stopDiscovery() }
    }
    val devices by support.devices.collectAsState()
    val session by support.session.collectAsState()
    var waitedLong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(NONE_FOUND_AFTER_MS)
        waitedLong = true
    }
    val connected = session is CastSessionState.Connected
    ChoiceSheet(
        title = s.castButton,
        onDismiss = onDismiss,
        onClose = onDismiss,
        note = when {
            devices.isNotEmpty() -> null
            waitedLong -> s.castNoneFound
            else -> s.castSearching
        },
    ) {
        devices.forEach { d ->
            ChoiceLine(
                title = d.name,
                detail = if (d.connecting) s.castConnecting(d.name) else d.model.ifBlank { null },
                selected = if (connected) d.connected else null,
                icon = TmIcons.Tv,
            ) {
                if (!d.connected) support.connect(d.id)
                onDismiss()
            }
        }
        if (connected) {
            ChoiceLine(title = s.castStop, icon = TmIcons.Close) {
                support.endSession()
                onDismiss()
            }
        }
    }
}

/**
 * The detail page's Cast button, on the page's round disc: opens the picker, so the video plays on
 * the TV from the moment Play is pressed. Drawn only where casting exists (a phone or tablet in the
 * regular build, with Google Play services).
 */
@Composable
fun CastPageButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val support = remember(context) { Cast.support(context) }
    if (!support.available) return
    val s = LocalStrings.current
    val session by support.session.collectAsState()
    var open by remember { mutableStateOf(false) }
    val label = (session as? CastSessionState.Connected)?.let { s.castCastingTo(it.name) } ?: s.castButton
    PageDiscButton(TmIcons.Cast, label, { open = true }, modifier)
    if (open) CastPickerSheet(support, onDismiss = { open = false })
}

private const val NONE_FOUND_AFTER_MS = 8_000L
