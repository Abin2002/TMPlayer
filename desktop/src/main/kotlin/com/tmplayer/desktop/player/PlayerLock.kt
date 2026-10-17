package com.tmplayer.desktop.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons

/**
 * The desktop player's Lock the screen, the phone's lock for a mouse, a touchpad, a touch screen
 * and a keyboard.
 *
 * While it is on the picture keeps playing and nothing reaches the player: clicks, double clicks,
 * drags, the wheel, the mouse's back button and every shortcut, Esc and Backspace included. What
 * any of them does instead is bring up the way out for a moment: an Unlock button low in the
 * middle, as the phone's pill sits, which a click or a finger on a touch screen presses, and
 * under it "or hold Enter", the keyboard's version of the television's held OK.
 *
 * Esc does not unlock. On the phone Back only shows the pill, and a key the whole keyboard knows
 * as "get me out" is the first one a child at the keyboard finds; the lock that let it through
 * would keep out nothing. The system's own ways out stay, as Home does on a phone: the window's
 * close button, Alt+F4, the window manager's keys. The desktop's media keys and the media
 * controls of the OS (MPRIS, SMTC) are held off too, see [PlayerHost].
 */
internal object PlayerLock {
    /** How long Enter is held to unlock: as the television's OK, long enough that a mash never does. */
    const val HOLD_MS = 2_000L

    /** How long the Unlock button stays after the last move, click or key, as the phone's pill does. */
    const val SHOW_MS = 2_500L

    /** The keyboard's unlock: Enter held for [HOLD_MS]. Every other key is swallowed. */
    fun isUnlockKey(key: Key): Boolean = key == Key.Enter || key == Key.NumPadEnter
}

/**
 * The shield over the locked player: it takes every pointer event (moves, presses, releases,
 * the wheel) so nothing under it hears them, and reports each as [onWake]. [showUnlock] is
 * whether the way out is up; [holding] whether Enter is being held toward an unlock.
 */
@Composable
internal fun BoxScope.LockShield(
    showUnlock: Boolean,
    holding: Boolean,
    onWake: () -> Unit,
    onUnlock: () -> Unit,
) {
    val s = LocalStrings.current
    val wake by rememberUpdatedState(onWake)
    Box(
        Modifier
            .matchParentSize()
            .testTag("lock-shield")
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Exit) wake()
                        // The Unlock button, a child, has had its say by now; nothing under the
                        // shield gets one.
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        AnimatedVisibility(
            visible = showUnlock,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onUnlock,
                    // The keyboard is the lock's, not the button's: Space or Enter on a focused
                    // button would be a one-press way out.
                    modifier = Modifier.testTag("unlock").focusProperties { canFocus = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.72f), contentColor = Color.White),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(TmIcons.LockOpen, null, Modifier.size(20.dp))
                        Text(s.playerUnlock, fontSize = 15.sp)
                    }
                }
                Text(
                    if (holding) s.playerKeepHoldingToUnlock else s.playerUnlockHoldEnter,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
                if (holding) {
                    val filled = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { filled.animateTo(1f, tween(PlayerLock.HOLD_MS.toInt(), easing = LinearEasing)) }
                    LinearProgressIndicator(progress = { filled.value }, modifier = Modifier.width(120.dp), color = Color.White)
                }
            }
        }
    }
}
