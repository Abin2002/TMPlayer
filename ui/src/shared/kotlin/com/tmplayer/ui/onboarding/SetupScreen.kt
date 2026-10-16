package com.tmplayer.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.tmplayer.data.DeviceFit
import com.tmplayer.data.DeviceForm
import com.tmplayer.data.Recommendation
import com.tmplayer.data.SettingsStore
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.ui.components.deviceForm
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.nav.BackHandler
import com.tmplayer.ui.theme.Caution
import com.tmplayer.ui.theme.Corner
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.Tv
import com.tmplayer.ui.theme.focusRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The setup after sign in ([Setup]), the same screen on the phone, the television and the desktop.
 * Each step asks one thing with two answers side by side, each with a picture of what it looks
 * like. Picking one applies it at once and moves on; Skip, or a swipe towards the end on a phone,
 * moves on and changes nothing; the close at the end of the title ends the setup; Back, or a
 * swipe back, goes to the step before. Where the viewer is shows at the foot ([StepProgress]).
 *
 * A remote or a keyboard starts on the answer that is set now, which on a fresh sign in is the
 * recommended one (every setting's default). Wide screens (a television, a desktop window, a phone
 * on its side) put the words and buttons beside the answers; a phone held upright stacks them,
 * with the buttons at the foot where a thumb is.
 *
 */
@Composable
fun SetupScreen(
    settings: SettingsStore,
    state: SetupState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the Device step says about this device, and what it offers to change (CP68). */
    fit: DeviceFit = DeviceFit.Fine,
    recommendations: List<Recommendation> = emptyList(),
    /** The count the step line gives, the quick tour's pages included when it follows ([SetupAndTour]). */
    total: Int = state.steps.size,
    /** Whether something follows the last step, so Skip all still means more than Skip there. */
    moreAfter: Boolean = false,
    /** Skip all, and Back from the first step: out of the setup and whatever follows it. */
    onSkipAll: () -> Unit = onDone,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val online = OnlineMetadata.current
    val lowStorage by settings.lowStorageMode.collectAsState(initial = false)
    val lighter by settings.lighterPictures.collectAsState(initial = false)
    val saveData by settings.saveMobileData.collectAsState(initial = false)
    val posters = online?.settings?.collectAsState()?.value?.enabled ?: true
    val values = SetupValues(
        posters = posters,
        lowStorage = lowStorage,
        lighter = lighter,
        saveData = saveData,
    )
    val form = deviceForm()
    val remote = form != DeviceForm.Phone

    fun pick(choice: SetupChoice) {
        scope.launch {
            runCatching {
                choice.applyTo(
                    settings,
                    posters = online?.let { o -> { on: Boolean -> o.setEnabled(on) } },
                    recommendations = recommendations,
                )
            }
        }
        if (!state.next()) onDone()
    }

    BackHandler { if (!state.back()) onSkipAll() }

    val step = state.step
    val copy = s.setupCopy(step).let { if (step == SetupStep.Device) it.copy(title = s.fitHeadline(fit)) else it }
    val focus = remember(step) { step.choices.associateWith { FocusRequester() } }
    val holder = remember { FocusHolder() }

    val outer = when (form) {
        DeviceForm.Tv -> Modifier.padding(horizontal = Tv.SafeH, vertical = Tv.SafeV)
        DeviceForm.Desktop -> Modifier.padding(40.dp)
        DeviceForm.Phone -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)
    }
    val swipe = if (form == DeviceForm.Phone) {
        Modifier.swipeSteps(onNext = { if (!state.next()) onDone() }, onBack = { state.back() })
    } else {
        Modifier
    }
    BoxWithConstraints(
        modifier.fillMaxSize().background(Tone.background).then(swipe).then(outer),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth > maxHeight && maxWidth >= WIDE_FROM
        val words: @Composable ColumnScope.() -> Unit = {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    copy.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Tone.text,
                    modifier = Modifier.weight(1f),
                )
                if (state.canSkipAll || moreAfter) StepsCloseButton(onSkipAll)
            }
            Text(copy.body, style = MaterialTheme.typography.bodyMedium, color = Tone.muted)
        }
        val buttons: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!state.isFirst) SetupButton(s.setupBack, icon = true) { state.back() }
                // Upright on a phone Skip sits at the end, under the thumb; beside the answers it
                // follows the words.
                if (!wide) Spacer(Modifier.weight(1f))
                SetupButton(s.setupSkip) { if (!state.next()) onDone() }
            }
        }
        val progress: @Composable (Modifier) -> Unit = { m -> StepProgress(state.index, total, m) }
        if (wide) {
            Row(
                Modifier.widthIn(max = DESKTOP_MAX).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    words()
                    Spacer(Modifier.height(8.dp))
                    buttons()
                    Text(s.setupLater, style = MaterialTheme.typography.bodySmall, color = Tone.muted)
                    progress(Modifier.padding(top = 8.dp))
                }
                BoxWithConstraints(Modifier.weight(1.3f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    // Two squares side by side, as large as the width allows and the height leaves
                    // room for, with the answer's name and line under each.
                    val side = min((maxWidth - GAP) / 2 - CARD_PAD * 2, maxHeight - LABELS - CARD_PAD * 2)
                    if (step == SetupStep.Device) {
                        DeviceChoices(recommendations, focus, holder, ::pick)
                    } else {
                        Choices(step, values, focus, holder, side, ::pick)
                    }
                }
            }
        } else {
            Column(Modifier.widthIn(max = PHONE_MAX).fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    words()
                    Spacer(Modifier.height(4.dp))
                    if (step == SetupStep.Device) {
                        DeviceChoices(recommendations, focus, holder, ::pick)
                    } else {
                        Choices(step, values, focus, holder, side = null, onPick = ::pick)
                    }
                    Text(s.setupLater, style = MaterialTheme.typography.bodySmall, color = Tone.muted)
                }
                Spacer(Modifier.height(12.dp))
                buttons()
                progress(Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp))
            }
        }
    }

    // A remote or a keyboard starts each step on the answer that is set now, asked again over a
    // few frames until one of the answers has it: a window not yet focused, or a list arriving
    // under the setup, can refuse or take the first request.
    if (remote) {
        val now by rememberUpdatedState(values)
        LaunchedEffect(step) {
            repeat(FOCUS_TRIES) {
                withFrameNanos { }
                if (it > 0) delay(FOCUS_RETRY_MS)
                if (holder.step == step) return@LaunchedEffect
                runCatching { focus.getValue(now.current(step)).requestFocus() }
            }
        }
    }
}

/** Which step has an answer focused, so the first focus can be asked for until it lands. */
private class FocusHolder {
    var step by mutableStateOf<SetupStep?>(null)
}

// Three seconds in all: a television cold start can take that long to give the window its focus.
private const val FOCUS_TRIES = 40
private const val FOCUS_RETRY_MS = 75L

/**
 * The Device step's answers: the list of what would change, then Apply (where a remote starts)
 * and Not now. No pictures: the changes are settings, and their names say what they do.
 */
@Composable
private fun DeviceChoices(
    recommendations: List<Recommendation>,
    focus: Map<SetupChoice, FocusRequester>,
    holder: FocusHolder,
    onPick: (SetupChoice) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Corner.Large)).background(Tone.surfaceHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RecommendationList(recommendations)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (choice in SetupStep.Device.choices) {
                val s = LocalStrings.current
                SetupButton(
                    if (choice == SetupChoice.ApplyRecommended) s.deviceApply else s.deviceNotNow,
                    primary = choice == SetupChoice.ApplyRecommended,
                    modifier = Modifier
                        .focusRequester(focus.getValue(choice))
                        .onFocusChanged { if (it.isFocused) holder.step = SetupStep.Device },
                ) { onPick(choice) }
            }
        }
    }
}

/** The two answers side by side. [side] is the picture's edge; null lets each take half the width. */
@Composable
private fun Choices(
    step: SetupStep,
    values: SetupValues,
    focus: Map<SetupChoice, FocusRequester>,
    holder: FocusHolder,
    side: Dp?,
    onPick: (SetupChoice) -> Unit,
) {
    // As tall as the taller card, so a longer line under one answer does not leave the other short.
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(GAP), verticalAlignment = Alignment.Top) {
        for (choice in step.choices) {
            ChoiceCard(
                choice = choice,
                selected = values.current(step) == choice,
                side = side,
                modifier = (if (side == null) Modifier.weight(1f) else Modifier)
                    .focusRequester(focus.getValue(choice))
                    .onFocusChanged { if (it.isFocused) holder.step = step }
                    .arrowsMoveFocus(),
                onPick = { onPick(choice) },
            )
        }
    }
}

@Composable
private fun ChoiceCard(choice: SetupChoice, selected: Boolean, side: Dp?, modifier: Modifier, onPick: () -> Unit) {
    val s = LocalStrings.current
    val copy = s.choiceCopy(choice)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Corner.Large)
    val touch = deviceForm() == DeviceForm.Phone
    Column(
        modifier
            .then(if (side != null) Modifier.width(side + CARD_PAD * 2) else Modifier)
            .fillMaxHeight()
            .clip(shape)
            .background(if (focused) Tone.focusFill else Tone.surfaceHigh)
            .focusRing(focused, shape)
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interactions,
                indication = if (touch) LocalIndication.current else null,
                role = Role.Button,
                onClick = onPick,
            )
            .padding(CARD_PAD),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .then(if (side != null) Modifier.size(side) else Modifier.fillMaxWidth().aspectRatio(1f))
                .clip(RoundedCornerShape(Corner.Medium))
                .background(Tone.background),
        ) {
            ChoicePicture(choice, Modifier.fillMaxSize())
            if (selected) {
                Icon(
                    TmIcons.CheckCircle,
                    contentDescription = null,
                    tint = Tone.accent,
                    // On the picture's right whatever the reading direction: the shots are of the
                    // app as it is, and on their left sits what the answer is about.
                    modifier = Modifier
                        .align(if (choice.screenshot) AbsoluteAlignment.TopRight else Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .background(Tone.background, CircleShape),
                )
            }
        }
        val onFill = if (focused) Tone.onFocusFill else Tone.text
        Text(copy.title, style = MaterialTheme.typography.titleSmall, color = onFill, maxLines = 2)
        Text(
            copy.detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) Tone.onFocusFill.copy(alpha = 0.85f) else Tone.muted,
            maxLines = 4,
        )
        // Under the words rather than on the picture, which it would hide part of. The row is as
        // tall as the taller card, so the two still line up.
        if (choice.recommended) {
            Text(
                s.setupRecommended,
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) Tone.onFocusFill else Tone.accent,
            )
        }
    }
}

/**
 * An answer's picture, filling [modifier]'s square: the marked shot, or the drawing for the storage
 * and mobile data steps. The setup's cards and Settings' Setup choices show the same one.
 */
@Composable
internal fun ChoicePicture(choice: SetupChoice, modifier: Modifier) {
    when {
        choice.screenshot -> SetupShot(choice, modifier)
        choice.step == SetupStep.MobileData -> MobileDataArt(choice, modifier)
        else -> StorageArt(choice, modifier)
    }
}

/**
 * Skip, Skip all and Back: quiet buttons that fill under the focus like the cards do. [primary] is
 * the accent-filled one an answer like Apply gets. Also the low-end device card's buttons.
 */
@Composable
internal fun SetupButton(
    label: String,
    icon: Boolean = false,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val touch = deviceForm() == DeviceForm.Phone
    Row(
        modifier
            .clip(CircleShape)
            .background(
                when {
                    focused -> Tone.focusFill
                    primary -> Tone.accent
                    else -> Tone.surfaceHigh
                },
            )
            .focusRing(focused, CircleShape)
            // The accent fill and the focus fill are near one colour in the dark theme, so a
            // focused primary button also gets an outline in the text colour.
            .then(if (primary && focused) Modifier.border(2.dp, Tone.text, CircleShape) else Modifier)
            .clickable(
                interactionSource = interactions,
                indication = if (touch) LocalIndication.current else null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = when {
            focused -> Tone.onFocusFill
            primary -> Tone.onAccent
            else -> Tone.text
        }
        if (icon) {
            Icon(TmIcons.ArrowBack, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * The storage step's picture, drawn rather than shot, since a frame of the player says nothing
 * about what is kept. Over a video's length (the bar), the part kept on the device is filled: up to
 * where it is playing and a little ahead, or with Low storage mode only a short stretch around
 * the playhead. Under it, how full the device gets. Follows the reading direction.
 */
@Composable
private fun StorageArt(choice: SetupChoice, modifier: Modifier) {
    val low = choice == SetupChoice.LowStorage
    val accent = Tone.accent
    val track = Tone.outline
    val ground = Tone.background
    val text = Tone.muted
    Column(
        modifier.padding(14.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(TmIcons.Play, contentDescription = null, tint = text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.weight(1f).height(26.dp).mirrored()) {
                val h = size.height * 0.42f
                val y = (size.height - h) / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(track, Offset(0f, y), Size(size.width, h), r)
                val from = if (low) 0.34f else 0f
                drawRoundRect(accent, Offset(size.width * from, y), Size(size.width * (0.62f - from), h), r)
                val head = Offset(size.width * 0.48f, size.height / 2)
                drawCircle(ground, radius = size.height * 0.42f, center = head)
                drawCircle(accent, radius = size.height * 0.30f, center = head)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(TmIcons.Download, contentDescription = null, tint = text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.weight(1f).height(40.dp).mirrored()) {
                val r = CornerRadius(size.height * 0.22f, size.height * 0.22f)
                drawRoundRect(track, Offset.Zero, size, r)
                val inset = size.height * 0.14f
                val inner = Size(size.width - inset * 2, size.height - inset * 2)
                drawRoundRect(ground, Offset(inset, inset), inner, r)
                val used = if (low) 0.18f else 0.78f
                drawRoundRect(
                    if (low) accent else Caution,
                    Offset(inset, inset),
                    Size(inner.width * used, inner.height),
                    r,
                )
            }
        }
    }
}

/**
 * The mobile data step's picture, drawn like the storage step's. Over a video's length, the part
 * fetched while it plays on mobile data: a short stretch ahead of the playhead when saving data,
 * or everything to the end without limits. Under it, a download: waiting for Wi-Fi (an empty bar
 * behind the Wi-Fi sign), or under way.
 */
@Composable
private fun MobileDataArt(choice: SetupChoice, modifier: Modifier) {
    val save = choice == SetupChoice.SaveData
    val accent = Tone.accent
    val track = Tone.outline
    val ground = Tone.background
    val text = Tone.muted
    Column(
        modifier.padding(14.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(TmIcons.Play, contentDescription = null, tint = text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.weight(1f).height(26.dp).mirrored()) {
                val h = size.height * 0.42f
                val y = (size.height - h) / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(track, Offset(0f, y), Size(size.width, h), r)
                val to = if (save) 0.58f else 1f
                drawRoundRect(if (save) accent else Caution, Offset(0f, y), Size(size.width * to, h), r)
                val head = Offset(size.width * 0.40f, size.height / 2)
                drawCircle(ground, radius = size.height * 0.42f, center = head)
                drawCircle(accent, radius = size.height * 0.30f, center = head)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (save) TmIcons.Wifi else TmIcons.Download,
                contentDescription = null,
                tint = text,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.weight(1f).height(26.dp).mirrored()) {
                val h = size.height * 0.42f
                val y = (size.height - h) / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(track, Offset(0f, y), Size(size.width, h), r)
                if (!save) drawRoundRect(Caution, Offset(0f, y), Size(size.width * 0.7f, h), r)
            }
        }
    }
}

/**
 * The arrow keys move between the answers and down to the buttons. A remote's D-pad does this on
 * its own; a desktop window's arrow keys do not, and Tab alone is a poor way across two cards.
 */
@Composable
private fun Modifier.arrowsMoveFocus(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val direction = when (event.key) {
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            else -> return@onPreviewKeyEvent false
        }
        focusManager.moveFocus(direction)
        true
    }
}

/** Flips a drawing for a right-to-left language, where the bar fills from the right. */
@Composable
private fun Modifier.mirrored(): Modifier =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) graphicsLayer(scaleX = -1f) else this

private val WIDE_FROM = 560.dp
private val PHONE_MAX = 600.dp
private val DESKTOP_MAX = 1120.dp
private val GAP = 12.dp
private val CARD_PAD = 8.dp

/** The room under a picture for the answer's name, its line and "Recommended". */
private val LABELS = 110.dp
