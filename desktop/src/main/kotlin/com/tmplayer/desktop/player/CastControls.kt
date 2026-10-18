package com.tmplayer.desktop.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmplayer.desktop.cast.CastPlaybackEngine
import com.tmplayer.desktop.cast.DesktopCast
import com.tmplayer.ui.components.TmDropdownMenu
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import kotlinx.coroutines.delay

/**
 * The player bar's Cast button and its popover: the Chromecasts and Google TVs on the network,
 * found while it is open, the one in use ticked, and Stop casting under them while a cast is on.
 */
@Composable
internal fun CastButton() {
    val s = LocalStrings.current
    val state by DesktopCast.state.collectAsState()
    val devices by DesktopCast.discovery.devices.collectAsState()
    var open by remember { mutableStateOf(false) }
    var waitedLong by remember { mutableStateOf(false) }
    DisposableEffect(open) {
        if (open) DesktopCast.discovery.start()
        onDispose { if (open) DesktopCast.discovery.stop() }
    }
    LaunchedEffect(open) {
        waitedLong = false
        if (open) {
            delay(NONE_FOUND_AFTER_MS)
            waitedLong = true
        }
    }
    val connected = (state as? DesktopCast.State.Connected)?.device
    val label = connected?.let { s.castCastingTo(it.name) } ?: s.castButton
    Box {
        Box {
            OverlayButton(TmIcons.Cast, label, onClick = { open = true })
            // The button says a cast is on, as the phone's does, with a dot in the corner.
            if (connected != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).background(CastOn, CircleShape))
            }
        }
        if (open) {
            TmDropdownMenu(expanded = true, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 260.dp, max = 380.dp)) {
                Text(
                    s.castDevices,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (devices.isEmpty()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!waitedLong) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(if (waitedLong) s.castNoneFound else s.castSearching, fontSize = 14.sp, maxLines = 3)
                    }
                }
                for (device in devices) {
                    val busy = (state as? DesktopCast.State.Connecting)?.device?.id == device.id
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val line = device.status.takeIf { it.isNotBlank() } ?: device.model
                                if (line.isNotBlank()) Text(line, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        leadingIcon = { Icon(TmIcons.Tv, null, Modifier.size(20.dp)) },
                        trailingIcon = {
                            when {
                                busy -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                connected?.id == device.id -> Icon(TmIcons.Check, null, Modifier.size(18.dp))
                            }
                        },
                        onClick = {
                            DesktopCast.connect(device)
                            open = false
                        },
                    )
                }
                if (connected != null) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(s.castStop) },
                        leadingIcon = { Icon(TmIcons.Close, null, Modifier.size(18.dp)) },
                        onClick = {
                            DesktopCast.disconnect()
                            open = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * What the picture area shows while the video is on a TV: where it is playing and in what state,
 * any word on how it is sent (the sound converted, a soundtrack the TV cannot play, subtitles that
 * stay behind), and, when the connection is lost or the TV refused the video, the way on:
 * Reconnect or Play here.
 */
@Composable
internal fun BoxScope.CastingPanel(
    cast: CastPlaybackEngine,
    status: PlaybackStatus,
    onPlayHere: () -> Unit,
) {
    val s = LocalStrings.current
    val state by DesktopCast.state.collectAsState()
    val note by cast.note.collectAsState()
    val name = cast.client.device.name
    val lost = state is DesktopCast.State.Lost || state is DesktopCast.State.Failed
    Column(
        Modifier.align(Alignment.Center).widthIn(max = 520.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(TmIcons.Cast, null, tint = if (lost) Color.White.copy(alpha = 0.5f) else CastOn, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            if (lost) s.castLost(name) else s.castCastingTo(name),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        val line = when {
            lost -> null
            status.error != null -> status.error
            !status.opened -> s.castStarting
            status.ended -> s.castStateEnded
            status.buffering -> s.castStateBuffering
            status.playing -> s.castStatePlaying
            else -> s.castStatePaused
        }
        if (line != null) Text(line, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center)
        note?.takeIf { !lost }?.let { Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, textAlign = TextAlign.Center) }
        if (lost || status.error != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (lost) Button(onClick = { DesktopCast.reconnect() }) { Text(s.castReconnect) }
                TextButton(onClick = onPlayHere) { Text(s.castPlayHere) }
            }
        }
    }
}

/** The Cast glyph's colour while a cast is on: Google's cast blue, which viewers know it by. */
private val CastOn = Color(0xFF8AB4F8)
private const val NONE_FOUND_AFTER_MS = 8_000L
