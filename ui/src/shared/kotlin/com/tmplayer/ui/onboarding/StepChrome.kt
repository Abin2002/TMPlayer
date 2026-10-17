package com.tmplayer.ui.onboarding

import com.tmplayer.ui.theme.collectIsFocusVisibleAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tmplayer.data.DeviceForm
import com.tmplayer.ui.components.deviceForm
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.focusRing
import kotlin.math.abs

/**
 * A sideways swipe across a setup step or a tour page: towards the end of the reading direction
 * goes on ([onNext]), back towards its start goes to the step before ([onBack]). A short drag does
 * nothing, so a finger resting on an answer does not turn the page.
 */
internal fun Modifier.swipeSteps(onNext: () -> Unit, onBack: () -> Unit): Modifier = composed {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val threshold = with(LocalDensity.current) { SWIPE_TURN.toPx() }
    val next by rememberUpdatedState(onNext)
    val back by rememberUpdatedState(onBack)
    pointerInput(rtl, threshold) {
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                if (abs(travelled) >= threshold) {
                    val towardsEnd = if (rtl) travelled > 0f else travelled < 0f
                    if (towardsEnd) next() else back()
                }
            },
        ) { change, amount ->
            travelled += amount
            change.consume()
        }
    }
}

/**
 * Where the viewer is in the setup and the tour, at the foot: a dot per step, the current one
 * drawn long in the accent, with "Step 3 of 9" beside them.
 */
@Composable
internal fun StepProgress(index: Int, total: Int, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(total) { i ->
                val width by animateDpAsState(if (i == index) 20.dp else 6.dp, label = "stepDot")
                val color by animateColorAsState(
                    when {
                        i == index -> Tone.accent
                        i < index -> Tone.accent.copy(alpha = 0.45f)
                        else -> Tone.surfaceHigh
                    },
                    label = "stepDotColour",
                )
                Box(Modifier.height(6.dp).width(width).clip(CircleShape).background(color))
            }
        }
        Text(s.setupStep(index + 1, total), style = MaterialTheme.typography.labelMedium, color = Tone.muted)
    }
}

/** The round close at the end of the title: out of the setup and the tour, what Skip all was. */
@Composable
internal fun StepsCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusVisibleAsState()
    val touch = deviceForm() == DeviceForm.Phone
    Box(
        modifier
            .size(CLOSE_SIZE)
            .clip(CircleShape)
            .background(if (focused) Tone.focusFill else Tone.surfaceHigh)
            .focusRing(focused, CircleShape)
            .clickable(
                interactionSource = interactions,
                indication = if (touch) LocalIndication.current else null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = s.setupSkipAll },
        contentAlignment = Alignment.Center,
    ) {
        Icon(TmIcons.Close, contentDescription = null, tint = if (focused) Tone.onFocusFill else Tone.text, modifier = Modifier.size(20.dp))
    }
}

/** How far a swipe has to travel before it turns the page. */
private val SWIPE_TURN = 56.dp
private val CLOSE_SIZE = 40.dp
