package com.tmplayer.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.tmplayer.cast.CastPickerSheet
import com.tmplayer.i18n.Translator
import com.tmplayer.ui.components.ChoiceLine
import com.tmplayer.ui.components.ChoiceSheet
import com.tmplayer.ui.components.IconTip
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons

/** The Cast picker over the player, when the top bar's Cast button opens it. */
@UnstableApi
@Composable
internal fun CastPickerHost(cast: PlayerCast) {
    if (cast.pickerOpen.value) CastPickerSheet(cast.support, onDismiss = { cast.pickerOpen.value = false })
}

/**
 * What the phone shows while the video plays on a TV, in place of the picture: "Casting to Living
 * room TV" and what the TV is doing, a word on how it is sent when that matters, the timebar,
 * play and pause, the skips and the episode steps, the soundtrack and the subtitles, the TV's
 * volume and Stop casting. Lost or refused, it offers Reconnect and Play here instead.
 */
@UnstableApi
@Composable
internal fun CastRemoteScreen(cast: PlayerCast) {
    val s = LocalStrings.current
    val state = cast.screen.value
    val steps by cast.episodes.collectAsState()
    val formatter = Translator.messages.formatter
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF15151A), Color(0xFF050507))))
            // Every touch stays here: none reaches the stopped picture's gestures underneath.
            .clickable(enabled = true, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(TmIcons.ArrowBack, s.commonBack) { cast.back() }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(cast.titleNow(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val line = cast.subtitleNow()
                    if (line.isNotBlank()) Text(line, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RoundIcon(TmIcons.Cast, s.castButton, tint = CastBlue) { cast.openPicker() }
            }
            Spacer(Modifier.height(24.dp))
            Icon(TmIcons.Cast, null, tint = if (state.lost) Color.White.copy(alpha = 0.5f) else CastBlue, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                if (state.lost) s.castLost(state.device) else s.castCastingTo(state.device),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            val stateLine = when {
                state.lost -> null
                state.error != null -> state.error
                state.starting -> s.castStarting
                state.ended -> s.castStateEnded
                state.buffering -> s.castStateBuffering
                state.playing -> s.castStatePlaying
                else -> s.castStatePaused
            }
            if (stateLine != null) {
                Spacer(Modifier.height(4.dp))
                Text(stateLine, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            state.note?.takeIf { !state.lost }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
            }
            if (state.lost || state.error != null) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.lost) Button(onClick = cast::reconnect) { Text(s.castReconnect) }
                    OutlinedButton(onClick = cast::playHere) { Text(s.castPlayHere) }
                }
            }
            Spacer(Modifier.height(24.dp))

            // The timebar, with the times either side; a drag seeks once, where it is let go.
            val duration = state.durationMs.coerceAtLeast(1)
            val shown = scrubbing?.let { (it * duration).toLong() } ?: state.positionMs
            Slider(
                value = (shown.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    scrubbing?.let { cast.seekTo((it * duration).toLong()) }
                    scrubbing = null
                },
                enabled = !state.starting && state.error == null && !state.lost,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = CastBlue, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            )
            Row(Modifier.fillMaxWidth().widthIn(max = 720.dp)) {
                Text(formatter.clock(shown), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(formatter.clock(state.durationMs), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val previous = steps.previous
                val next = steps.next
                if (previous != null) RoundIcon(TmIcons.SkipPrevious, s.playerPreviousEpisode) { cast.playEpisode(previous) }
                RoundIcon(TmIcons.Rewind, s.notifySeekBack(Skip.BACK_MS / 1000)) { cast.seekBy(-Skip.BACK_MS) }
                IconTip(if (state.playing) s.commonPause else s.commonPlay) {
                    Box(
                        Modifier.size(72.dp).background(Color.White, CircleShape)
                            .clickable(enabled = !state.starting) { cast.togglePlay() }
                            .semantics { contentDescription = if (state.playing) s.commonPause else s.commonPlay },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.starting || (state.buffering && state.playing)) {
                            CircularProgressIndicator(Modifier.size(32.dp), color = Color.Black, strokeWidth = 3.dp)
                        } else {
                            Icon(if (state.playing) TmIcons.PauseSolid else TmIcons.PlaySolid, null, tint = Color.Black, modifier = Modifier.size(36.dp))
                        }
                    }
                }
                RoundIcon(TmIcons.FastForward, s.notifySeekForward(Skip.FORWARD_MS / 1000)) { cast.seekBy(Skip.FORWARD_MS) }
                if (next != null) RoundIcon(TmIcons.SkipNext, s.playerNextEpisode) { cast.playEpisode(next) }
            }
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.audio.size > 1) RoundIcon(TmIcons.Audio, s.playerAudio) { cast.audioPickerOpen.value = true }
                RoundIcon(TmIcons.Subtitles, s.tracksSubtitles) { cast.subtitlePickerOpen.value = true }
                RoundIcon(if (state.muted || state.volume <= 0f) TmIcons.VolumeOff else TmIcons.VolumeUp, s.castTvVolume) { cast.toggleMute() }
                Slider(
                    value = if (state.muted) 0f else state.volume,
                    onValueChange = { cast.setVolume(it) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.width(160.dp).semantics { contentDescription = s.castTvVolume },
                )
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = cast::stopCasting) {
                Icon(TmIcons.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.castStop, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (cast.audioPickerOpen.value) {
        ChoiceSheet(title = s.playerAudio, onDismiss = { cast.audioPickerOpen.value = false }, onClose = { cast.audioPickerOpen.value = false }) {
            state.audio.forEach { a -> ChoiceLine(a.label, selected = a.selected) { cast.pickAudio(a.id) } }
        }
    }
    if (cast.subtitlePickerOpen.value) {
        val inside = cast.subtitlesInsideNote()
        ChoiceSheet(
            title = s.tracksSubtitles,
            onDismiss = { cast.subtitlePickerOpen.value = false },
            onClose = { cast.subtitlePickerOpen.value = false },
            note = inside,
        ) {
            ChoiceLine(s.commonOff, selected = state.subtitles.none { it.selected }) { cast.pickSubtitle(null) }
            state.subtitles.forEach { t -> ChoiceLine(t.label, selected = t.selected) { cast.pickSubtitle(t.id) } }
        }
    }
}

/** A round icon button on the cast screen, named by its tooltip (a long press on a phone). */
@Composable
private fun RoundIcon(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    IconTip(label) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

/** Google's cast blue, which a cast on is known by. */
private val CastBlue = Color(0xFF8AB4F8)
