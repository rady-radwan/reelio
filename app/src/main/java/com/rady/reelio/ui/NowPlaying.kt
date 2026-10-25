package com.rady.reelio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rady.reelio.AppGraph
import com.rady.reelio.data.formatDuration
import com.rady.reelio.player.PlayerHub
import com.rady.reelio.player.RepeatMode
import kotlinx.coroutines.delay
import java.util.Locale

enum class Panel { SPEED, SLEEP, BOOST, EQ, QUEUE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    val s by PlayerHub.state.collectAsState()
    val boost by PlayerHub.boost.collectAsState()
    val cur = s.current
    val ctx = LocalContext.current
    val actions = LocalUiActions.current
    val spec = LocalThemeSpec.current
    var panel by remember { mutableStateOf<Panel?>(null) }
    val lists by AppGraph.playlists.lists.collectAsState()
    val fav = remember(lists, cur) { cur != null && AppGraph.playlists.isFavorite(cur.uri) }

    var pos by remember { mutableLongStateOf(PlayerHub.positionMs()) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(cur?.uri) {
        while (true) { if (!dragging) pos = PlayerHub.positionMs(); delay(250) }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(spec.primary.copy(alpha = 0.30f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
                Text(
                    if (s.shuffle) "SMART SHUFFLE" else "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                IconButton(onClick = { panel = Panel.QUEUE }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue") }
            }
            if (cur == null) {
                EmptyState(Icons.Rounded.PlayArrow, "Nothing playing", "Pick a song or video from your library.")
                return@Column
            }
            Spacer(Modifier.height(16.dp))
            MediaThumb(
                cur,
                Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(if (cur.isVideo) 16f / 9f else 1f),
                sizePx = 900, corner = 24.dp,
            )
            if (cur.isVideo) FilledTonalButton(onClick = { openVideoPlayer(ctx) }, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Rounded.Movie, null); Spacer(Modifier.size(6.dp)); Text("Watch video")
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cur.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        cur.subtitleLine.ifBlank { if (cur.isVideo) "Video" else "Unknown artist" },
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { AppGraph.playlists.toggleFavorite(cur) }) {
                    Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite",
                        tint = if (fav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
                }
            }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
            Spacer(Modifier.height(12.dp))
            val dur = s.durationMs
            Slider(
                value = if (dragging) dragValue else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { PlayerHub.seekTo((dragValue * dur).toLong()); pos = (dragValue * dur).toLong(); dragging = false },
                colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(if (dragging) (dragValue * dur).toLong() else pos), style = subtleText)
                Spacer(Modifier.weight(1f))
                Text("-" + formatDuration((dur - (if (dragging) (dragValue * dur).toLong() else pos)).coerceAtLeast(0)), style = subtleText)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ToggleIcon(Icons.Rounded.Shuffle, "Smart shuffle", s.shuffle) { PlayerHub.setShuffle(!s.shuffle) }
                IconButton(onClick = { PlayerHub.previous() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(38.dp)) }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { PlayerHub.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (s.playWhenReady && !s.ended) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause",
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = { PlayerHub.next() }, Modifier.size(56.dp), enabled = s.hasNext) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(38.dp)) }
                ToggleIcon(
                    if (s.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    when (s.repeat) { RepeatMode.OFF -> "Loop off"; RepeatMode.ALL -> "Loop list"; RepeatMode.ONE -> "Loop song" },
                    s.repeat != RepeatMode.OFF,
                ) { PlayerHub.cycleRepeat() }
            }
            Text(
                when (s.repeat) { RepeatMode.OFF -> ""; RepeatMode.ALL -> "Looping the whole list"; RepeatMode.ONE -> "Looping this song" },
                style = subtleText, color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { PlayerHub.seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, "Back 10 s") }
                SmallAction(Icons.Rounded.Speed, if (s.speed != 1f) speedLabel(s.speed) else "Speed", s.speed != 1f) { panel = Panel.SPEED }
                SmallAction(Icons.AutoMirrored.Rounded.VolumeUp, if (boost > 100) "$boost%" else "Boost", boost > 100) { panel = Panel.BOOST }
                SmallAction(Icons.Rounded.Equalizer, "EQ", AppGraph.settings.eqEnabled.flow.collectAsState().value) { panel = Panel.EQ }
                SmallAction(Icons.Rounded.Timer, if (s.sleepAtMs != null || s.sleepAfterTrack) "On" else "Sleep", s.sleepAtMs != null || s.sleepAfterTrack) { panel = Panel.SLEEP }
                SmallAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save", false) { actions.addToPlaylist(listOf(cur)) }
                IconButton(onClick = { PlayerHub.seekBy(10_000) }) { Icon(Icons.Rounded.Forward10, "Forward 10 s") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    panel?.let { p ->
        ModalBottomSheet(onDismissRequest = { panel = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = p != Panel.QUEUE)) {
            when (p) {
                Panel.SPEED -> SpeedPanel()
                Panel.SLEEP -> SleepPanel { panel = null }
                Panel.BOOST -> BoostPanel()
                Panel.EQ -> EqualizerPanel()
                Panel.QUEUE -> QueuePanel { panel = null }
            }
        }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, desc: String, on: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) {
            Icon(icon, desc, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (on) Box(Modifier.align(Alignment.BottomCenter).size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 11.sp, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

