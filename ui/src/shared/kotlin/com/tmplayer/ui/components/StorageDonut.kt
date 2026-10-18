package com.tmplayer.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmplayer.data.StorageChart
import com.tmplayer.data.StorageChart.Part
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.theme.Corner
import com.tmplayer.ui.theme.LocalDarkTheme
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.focusRing
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * What the device holds, as a ring with a legend: the phone, the television and the desktop draw
 * the same one, on the Downloads page and in Settings' Storage (user, 2026-10-18: "a pie chart
 * saying this much storage is taking up for this stuff, more useful than a bar").
 *
 * TMPlayer's own parts take the accent family, strongest first, and the device's other apps and
 * its free space are quiet greys, so what TMPlayer can give back stands out. The centre says what
 * TMPlayer holds of the whole device, or, while a part is chosen, what that part holds.
 *
 * A legend row with an action in [actions] is a button (a remote's stop on the television), and
 * the slice it names lifts while it is focused, hovered, pressed or tapped on the ring. The ring
 * sweeps in once when it first has figures; with reduced motion it is simply there.
 *
 * @param wide the ring on the left and the legend beside it (a television, the desktop, a wide
 *   window); otherwise the legend goes under it, as on a phone.
 * @param firstFocus put on the first legend row with an action, for a page that lands focus here.
 */
@Composable
fun StorageDonutPanel(
    figures: StorageChart.Figures,
    wide: Boolean,
    modifier: Modifier = Modifier,
    measured: Boolean = figures.app > 0 || figures.deviceTotal > 0,
    actions: Map<Part, () -> Unit> = emptyMap(),
    ringSize: Dp = if (wide) 168.dp else 184.dp,
    large: Boolean = false,
    firstFocus: FocusRequester? = null,
    /** A part lifted from the start, as if tapped: for a picture of the chart in a test. */
    selected: Part? = null,
) {
    // Which part is lifted: the one the legend is pointing at wins over a tap on the ring.
    var pointed by remember { mutableStateOf<Part?>(null) }
    var tapped by remember { mutableStateOf(selected) }
    val lifted = pointed ?: tapped
    val legend: @Composable (Modifier) -> Unit = { m ->
        StorageLegend(
            figures = figures,
            lifted = lifted,
            actions = actions,
            large = large,
            firstFocus = firstFocus,
            onPoint = { part, on ->
                if (on) pointed = part else if (pointed == part) pointed = null
            },
            modifier = m,
        )
    }
    if (wide) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StorageRing(figures, measured, lifted, ringSize, large) { tapped = if (tapped == it) null else it }
            Spacer(Modifier.width(if (large) 36.dp else 28.dp))
            legend(Modifier.weight(1f))
        }
    } else {
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            StorageRing(figures, measured, lifted, ringSize, large) { tapped = if (tapped == it) null else it }
            Spacer(Modifier.height(20.dp))
            legend(Modifier.fillMaxWidth())
        }
    }
}

/** The colour of [part], from the theme: the accent family for TMPlayer's parts, greys for the rest. */
@Composable
fun storagePartColor(part: Part): Color {
    val cs = MaterialTheme.colorScheme
    val ground = Tone.surface
    val dark = LocalDarkTheme.current
    // Two accent families in steps of lightness, and two greys that step down to the card: the
    // used rest of the device a mid grey, the free space barely above the card, an empty track.
    return when (part) {
        Part.Downloads -> cs.primary
        Part.Unfinished -> lerp(cs.primary, ground, 0.45f)
        Part.Recent -> cs.tertiary
        Part.Unmatched -> lerp(cs.tertiary, ground, 0.38f)
        Part.Pictures -> if (dark) cs.secondary else lerp(cs.secondary, ground, 0.3f)
        Part.OtherApps -> if (dark) lerp(cs.outline, ground, 0.55f) else cs.outlineVariant
        Part.Free -> cs.surfaceContainerHighest
    }
}

/** The name of [part], as the legend and a screen reader say it. */
@Composable
fun storagePartName(part: Part): String {
    val s = LocalStrings.current
    return when (part) {
        Part.Downloads -> s.downloadsPartDownloads
        Part.Unfinished -> s.downloadsPartUnfinished
        Part.Recent -> s.downloadsPartRecent
        Part.Unmatched -> s.downloadsPartUnmatched
        Part.Pictures -> s.downloadsPartPictures
        Part.OtherApps -> s.downloadsPartOtherApps
        Part.Free -> s.downloadsPartFree
    }
}

/** A share as the legend writes it: under one per cent is "<1%", never a "0%" beside a sliver. */
@Composable
private fun shareText(share: Double): String {
    val f = LocalStrings.current.formatter
    return if (share > 0 && share < 0.01) "<" + f.percent(0.01) else f.percent(share)
}

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun StorageRing(
    figures: StorageChart.Figures,
    measured: Boolean,
    lifted: Part?,
    size: Dp,
    large: Boolean,
    onTap: (Part) -> Unit,
) {
    val s = LocalStrings.current
    val reduced = rememberLessMotion()
    val slices = remember(figures) { StorageChart.slices(figures) }
    // Swept in once, the first time there is something to draw, and never again on a refresh:
    // figures moving under a ring that redraws itself from nothing would read as a reload.
    val sweep = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(slices.isNotEmpty()) {
        if (slices.isEmpty()) return@LaunchedEffect
        if (reduced) sweep.snapTo(1f) else sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val colours = Part.entries.associateWith { storagePartColor(it) }
    val ground = Tone.surface
    val track = Tone.surfaceHigh
    // Each slice's lift, eased, so the one being pointed at rises and the last one settles back.
    val lifts = Part.entries.associateWith { part ->
        animateFloatAsState(
            targetValue = if (part == lifted) 1f else 0f,
            animationSpec = if (reduced) tween(0) else tween(180),
            label = "storageLift",
        )
    }
    val dim by animateFloatAsState(if (lifted != null) 1f else 0f, if (reduced) tween(0) else tween(180), label = "storageDim")
    val description = s.downloadsChartCentreDescription(
        size = s.formatter.bytes(figures.app),
        total = s.formatter.bytes(figures.whole),
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(size)
                .semantics { contentDescription = description }
                .pointerInput(slices) {
                    detectTapGestures { at ->
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        val d = hypot(at.x - c.x, at.y - c.y)
                        val outer = this.size.width / 2f
                        if (d < outer * HOLE || d > outer) return@detectTapGestures
                        // Degrees clockwise from the top, the chart's own measure.
                        val deg = (Math.toDegrees(atan2((at.x - c.x).toDouble(), -(at.y - c.y).toDouble())).toFloat() + 360f) % 360f
                        StorageChart.sliceAt(slices, deg)?.let { onTap(it.part) }
                    }
                },
        ) {
            val liftPx = LIFT.toPx()
            val outer = this.size.minDimension / 2f - liftPx
            val ringInner = this.size.minDimension / 2f * HOLE
            if (slices.isEmpty() || !measured) {
                drawSegment(0f, 360f, ringInner, outer, 0f, 0f, track)
                return@Canvas
            }
            val p = sweep.value
            val gap = GAP.toPx()
            val corner = CORNER.toPx()
            // The part not swept yet is the empty track, so the ring is never a blank space, not
            // even on the first frame.
            if (p < 1f) drawSegment(360f * p, 360f * (1f - p), ringInner, outer, if (p > 0f) gap else 0f, corner, track)
            for (slice in slices) {
                val lift = lifts.getValue(slice.part).value
                val base = colours.getValue(slice.part)
                val colour = if (slice.part == lifted) base else lerp(base, ground, 0.55f * dim)
                drawSegment(
                    startDeg = slice.startDeg * p,
                    sweepDeg = slice.sweepDeg * p,
                    rIn = ringInner,
                    rOut = outer + liftPx * lift,
                    gapPx = if (slices.size > 1) gap else 0f,
                    cornerPx = corner,
                    colour = colour,
                )
            }
        }
        // The centre: what TMPlayer holds of the device, or the lifted part's own figures.
        Crossfade(targetState = lifted, label = "storageCentre") { part ->
            Column(
                // Inside the hole with room to spare above and below the middle, where it narrows.
                Modifier.widthIn(max = size * HOLE * 0.84f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!measured) {
                    Text(s.storageMeasuring, style = MaterialTheme.typography.bodySmall, color = Tone.muted, textAlign = TextAlign.Center)
                    return@Column
                }
                val label = if (part == null) s.downloadsChartApp else storagePartName(part)
                val bytes = if (part == null) figures.app else figures.bytes(part)
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Tone.muted,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    s.formatter.bytes(bytes),
                    style = MaterialTheme.typography.headlineSmall.merge(Tabular),
                    fontWeight = FontWeight.SemiBold,
                    color = Tone.text,
                    maxLines = 1,
                )
                if (figures.deviceTotal > 0) {
                    Text(
                        if (part == null) s.downloadsChartOfTotal(total = s.formatter.bytes(figures.whole)) else shareText(figures.share(part)),
                        style = MaterialTheme.typography.bodySmall.merge(Tabular),
                        color = Tone.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * One slice: an annular sector with softly rounded corners and a gap either side.
 *
 * Built inset by [cornerPx] and then stroked [cornerPx] wide with round joins in its own colour,
 * which brings it back out to its true edges with every corner rounded, on Android and Skia
 * alike. A slice too narrow for its corners gives them up rather than vanishing.
 */
private fun DrawScope.drawSegment(
    startDeg: Float,
    sweepDeg: Float,
    rIn: Float,
    rOut: Float,
    gapPx: Float,
    cornerPx: Float,
    colour: Color,
) {
    if (sweepDeg <= 0f || rOut <= rIn) return
    val centre = Offset(size.width / 2f, size.height / 2f)
    if (sweepDeg >= 359.99f && gapPx == 0f) {
        val mid = (rIn + rOut) / 2f
        drawCircle(colour, radius = mid, center = centre, style = Stroke(width = rOut - rIn))
        return
    }
    val sweepRad = sweepDeg * PI.toFloat() / 180f
    var c = min(cornerPx, (rOut - rIn) * 0.3f)
    fun span(r: Float, corner: Float): Float = sweepRad - 2f * (gapPx / 2f + corner) / r
    if (span(rIn + c, c) <= 0f) c = 0f
    val ri = rIn + c
    val ro = rOut - c
    val innerSpan = span(ri, c).coerceAtLeast(0f)
    val outerSpan = span(ro, c)
    if (outerSpan <= 0f) return
    val startRad = startDeg * PI.toFloat() / 180f
    val o0 = startRad + (sweepRad - outerSpan) / 2f
    val i1 = startRad + sweepRad - (sweepRad - innerSpan) / 2f
    fun at(r: Float, a: Float) = Offset(centre.x + r * sin(a), centre.y - r * cos(a))
    // Compose measures arcs from three o'clock; the chart measures from twelve.
    fun composeDeg(a: Float) = a * 180f / PI.toFloat() - 90f
    val path = Path().apply {
        val first = at(ro, o0)
        moveTo(first.x, first.y)
        arcTo(Rect(centre, ro), composeDeg(o0), outerSpan * 180f / PI.toFloat(), false)
        val back = at(ri, i1)
        lineTo(back.x, back.y)
        arcTo(Rect(centre, ri), composeDeg(i1), -innerSpan * 180f / PI.toFloat(), false)
        close()
    }
    drawPath(path, colour)
    if (c > 0f) drawPath(path, colour, style = Stroke(width = 2f * c, join = StrokeJoin.Round))
}

@Composable
private fun StorageLegend(
    figures: StorageChart.Figures,
    lifted: Part?,
    actions: Map<Part, () -> Unit>,
    large: Boolean,
    firstFocus: FocusRequester?,
    onPoint: (Part, Boolean) -> Unit,
    modifier: Modifier,
) {
    val parts = figures.shown
    val firstActionable = parts.firstOrNull { it in actions }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((index, part) in parts.withIndex()) {
            // A hairline where TMPlayer's own parts end and the rest of the device begins.
            if (!part.mine && index > 0 && parts[index - 1].mine) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = if (large) 6.dp else 4.dp)
                        .height(1.dp)
                        .background(Tone.outline),
                )
            }
            LegendRow(
                part = part,
                bytes = figures.bytes(part),
                share = if (figures.deviceTotal > 0) figures.share(part) else null,
                lifted = part == lifted,
                large = large,
                onClick = actions[part],
                focus = if (part == firstActionable) firstFocus else null,
                onPoint = { on -> onPoint(part, on) },
            )
        }
    }
}

@Composable
private fun LegendRow(
    part: Part,
    bytes: Long,
    share: Double?,
    lifted: Boolean,
    large: Boolean,
    onClick: (() -> Unit)?,
    focus: FocusRequester?,
    onPoint: (Boolean) -> Unit,
) {
    val s = LocalStrings.current
    val name = storagePartName(part)
    val size = s.formatter.bytes(bytes)
    val percent = share?.let { shareText(it) }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val pointing = onClick != null && (focused || hovered || pressed)
    LaunchedEffect(pointing) { onPoint(pointing) }
    val tv = !isTouch()
    val shape = RoundedCornerShape(Corner.Medium)
    // A television marks the remote's place with the focus fill; elsewhere the lifted row takes
    // a quiet tint, so the row and its slice are plainly the same thing.
    val fill by animateColorAsState(
        when {
            tv && focused -> Tone.focusFill
            lifted -> Tone.surfaceHigh
            else -> Color.Transparent
        },
        tween(140),
        label = "legendFill",
    )
    val text = if (tv && focused) Tone.onFocusFill else Tone.text
    val muted = if (tv && focused) Tone.onFocusFill else Tone.muted
    val body = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .focusRing(focused, shape)
            .background(fill)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactions, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = s.downloadsChartLegendLine(label = name, size = size, percent = percent ?: size)
            }
            .padding(horizontal = 10.dp, vertical = if (large) 6.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(if (large) 12.dp else 10.dp).clip(CircleShape).background(storagePartColor(part)))
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            style = body,
            fontWeight = if (lifted || (tv && focused)) FontWeight.SemiBold else FontWeight.Normal,
            color = text,
            // Two lines in a narrow column (the desktop's side pane) rather than an ellipsis.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(size, style = body.merge(Tabular), color = text, maxLines = 1, textAlign = TextAlign.End)
        if (percent != null) {
            Text(
                percent,
                style = body.merge(Tabular),
                color = muted,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.width(if (large) 60.dp else 50.dp),
            )
        }
    }
}

/** How much of the ring's width is hole: generous, so the centre's figures have room. */
private const val HOLE = 0.70f

/** How far a chosen slice rises out of the ring. */
private val LIFT = 5.dp

/** The space between two slices. */
private val GAP = 2.dp

/** How round a slice's corners are. */
private val CORNER = 3.dp
