package com.tmplayer.desktop.ui

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.tmplayer.ui.theme.FocusVisibility
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

/**
 * The desktop's half of [FocusVisibility]: a mouse press hides focus, a key that moves focus shows
 * it again, so Tab and the arrows always say where they are and a click never leaves a ring.
 *
 * [install] listens to the whole process through AWT, which sees the menus and dialogs too (they
 * are layers or windows of their own, outside the page's tree). [trackFocusVisibility] does the
 * same from inside a Compose tree, for the window's root and for the tests, which have no AWT.
 */
object FocusVisible {
    @Volatile private var installed = false

    /** Starts listening, once; focus is hidden until the first key that moves it. */
    fun install() {
        if (installed) return
        installed = true
        FocusVisibility.visible = false
        Toolkit.getDefaultToolkit().addAWTEventListener(
            { event ->
                when {
                    event is MouseEvent && event.id == MouseEvent.MOUSE_PRESSED -> FocusVisibility.visible = false
                    event is KeyEvent && event.id == KeyEvent.KEY_PRESSED && event.keyCode in AWT_MOVES -> FocusVisibility.visible = true
                }
            },
            AWTEvent.MOUSE_EVENT_MASK or AWTEvent.KEY_EVENT_MASK,
        )
    }

    /** True for the keys that move focus. Enter, Space and the letters act on it and change nothing. */
    fun moves(key: Key): Boolean = key in COMPOSE_MOVES

    private val AWT_MOVES = setOf(
        KeyEvent.VK_TAB, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
        KeyEvent.VK_KP_UP, KeyEvent.VK_KP_DOWN, KeyEvent.VK_KP_LEFT, KeyEvent.VK_KP_RIGHT,
        KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN,
    )

    private val COMPOSE_MOVES = setOf(
        Key.Tab, Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
        Key.MoveHome, Key.MoveEnd, Key.PageUp, Key.PageDown,
    )
}

/** [FocusVisible] from inside a tree: a press anywhere under it hides focus, a moving key shows it. */
fun Modifier.trackFocusVisibility(): Modifier = this
    .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && FocusVisible.moves(event.key)) FocusVisibility.visible = true
        false
    }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                // The first pass, before the control under the pointer takes focus from the press.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) FocusVisibility.visible = false
            }
        }
    }

/**
 * Material's own focus layer, the tint its buttons, chips and menu rows take while focused, only
 * while focus is shown: otherwise the button just clicked stays tinted as if still hovered.
 * Pressed, hovered and dragged keep Material's figures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvideFocusVisibility(content: @Composable () -> Unit) {
    val ripple = if (FocusVisibility.visible) {
        RippleConfiguration()
    } else {
        RippleConfiguration(Color.Unspecified, NO_FOCUS_LAYER)
    }
    CompositionLocalProvider(LocalRippleConfiguration provides ripple, content = content)
}

/** Material 3's state layer opacities, with the focused one taken out. */
private val NO_FOCUS_LAYER = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0f, hoveredAlpha = 0.08f, pressedAlpha = 0.1f)
