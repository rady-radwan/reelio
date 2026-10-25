package com.rady.reelio.video

import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rady.reelio.AppGraph
import com.rady.reelio.MainActivity
import com.rady.reelio.data.Subtitles
import com.rady.reelio.data.formatDuration
import com.rady.reelio.player.EngineKind
import com.rady.reelio.player.PlayerHub
import com.rady.reelio.player.RepeatMode
import com.rady.reelio.player.VideoDims
import com.rady.reelio.ui.BoostPanel
import com.rady.reelio.ui.CharsetPicker
import com.rady.reelio.ui.Chips
import com.rady.reelio.ui.EqualizerPanel
import com.rady.reelio.ui.MediaThumb
import com.rady.reelio.ui.PanelTitle
import com.rady.reelio.ui.QueuePanel
import com.rady.reelio.ui.SleepPanel
import com.rady.reelio.ui.SpeedPanel
import com.rady.reelio.ui.SubtitleStylePanel
import com.rady.reelio.ui.SubtitleText
import com.rady.reelio.ui.importFont
import com.rady.reelio.ui.rememberSubStyle
import com.rady.reelio.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class VPanel { SUBS, AUDIO, SPEED, BOOST, SLEEP, EQ, QUEUE, MORE, ASPECT }

val aspectModes = listOf(
    "fit" to "Fit", "fill" to "Stretch", "crop" to "Crop", "original" to "100%",
    "16:9" to "16:9", "4:3" to "4:3", "21:9" to "21:9", "18:9" to "18:9", "1:1" to "1:1",
)

private fun fixedRatio(mode: String): Float? = when (mode) {
    "16:9" -> 16f / 9f; "4:3" -> 4f / 3f; "21:9" -> 21f / 9f; "18:9" -> 2f; "1:1" -> 1f; else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(act: VideoActivity) {
    val s by PlayerHub.state.collectAsState()
    val dims by PlayerHub.video.collectAsState()
    val inPip by act.inPip
    val settings = AppGraph.settings
    val gesturesOn by settings.gestures.flow.collectAsState()
    val dtSec by settings.doubleTapSec.flow.collectAsState()
    val swipeBoost by settings.swipeBoost.flow.collectAsState()
    val boost by PlayerHub.boost.collectAsState()

    var aspect by remember { mutableStateOf(settings.defaultAspect.value) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var touchKey by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf<String?>(null) }
    var hintKey by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<VPanel?>(null) }
    var pos by remember { mutableLongStateOf(PlayerHub.positionMs()) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showRemaining by remember { mutableStateOf(false) }
    val audio = remember { act.getSystemService(AudioManager::class.java) }

    fun flash(msg: String) { hint = msg; hintKey++ }

    LaunchedEffect(Unit) { while (true) { if (!dragging) pos = PlayerHub.positionMs(); delay(100) } }
    LaunchedEffect(controls, touchKey, s.playWhenReady, panel) {
        if (controls && s.playWhenReady && panel == null) { delay(3500); controls = false }
    }
    LaunchedEffect(hintKey) { if (hint != null) { delay(900); hint = null } }
    LaunchedEffect(s.current?.uri) { zoom = 1f; pan = Offset.Zero }
    LaunchedEffect(s.current == null) { if (s.current == null) act.finish() }
    LaunchedEffect(s.ended) { if (s.ended) controls = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val cur = s.current
        if (cur != null && cur.isVideo) {
            if (s.engine == EngineKind.VLC) VlcSurface(aspect, zoom, pan) else ExoSurface(dims, aspect, zoom, pan)
        } else if (cur != null) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth(0.55f), horizontalAlignment = Alignment.CenterHorizontally) {
                MediaThumb(cur, Modifier.fillMaxWidth().aspectRatio(1f), sizePx = 800, corner = 24.dp)
            }
        }

        if (s.engine != EngineKind.VLC) SubtitleOverlay(pos, inPip, controls && !inPip)

        // Gestures
        if (!inPip) Box(
            Modifier.fillMaxSize()
                .pointerInput(locked, dtSec) {
                    detectTapGestures(
                        onTap = { controls = !controls; touchKey++ },
                        onDoubleTap = { o ->
                            if (locked) return@detectTapGestures
                            val third = size.width / 3f
                            when {
                                o.x < third -> { PlayerHub.seekBy(-dtSec * 1000L); flash("« ${dtSec}s") }
                                o.x > third * 2 -> { PlayerHub.seekBy(dtSec * 1000L); flash("${dtSec}s »") }
                                else -> PlayerHub.toggle()
                            }
                        },
                    )
                }
                .pointerInput(locked, gesturesOn, swipeBoost) {
                    if (locked || !gesturesOn) return@pointerInput
                    val edge = 28.dp.toPx()
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var mode = 0 // 1 seek, 2 brightness, 3 volume, 4 zoom, -1 ignore
                        var total = Offset.Zero
                        val startPos = PlayerHub.positionMs()
                        val dur = PlayerHub.durationMs()
                        val startBright = act.currentBrightness()
                        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val curVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val startLevel = curVol.toFloat() / maxVol + if (curVol >= maxVol && PlayerHub.boost.value > 100) (PlayerHub.boost.value - 100) / 100f else 0f
                        var lastVol = curVol
                        var seekTarget = startPos
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                if (mode == 0 || mode == 4 || mode == -1) {
                                    mode = 4
                                    zoom = (zoom * ev.calculateZoom()).coerceIn(0.5f, 6f)
                                    pan += ev.calculatePan()
                                    if (abs(zoom - 1f) < 0.04f) { zoom = 1f; pan = Offset.Zero }
                                    ev.changes.forEach { it.consume() }
                                    flash("Zoom ${(zoom * 100).roundToInt()}%")
                                }
                                continue
                            }
                            val ch = pressed.first()
                            total += ch.positionChange()
                            if (mode == 0 && total.getDistance() > slop) {
                                mode = when {
                                    abs(total.x) > abs(total.y) -> if (dur > 0) 1 else -1
                                    down.position.x < edge || down.position.x > size.width - edge || down.position.y < edge * 1.5f -> -1
                                    down.position.x < size.width / 2f -> 2
                                    else -> 3
                                }
                            }
                            when (mode) {
                                1 -> {
                                    val span = dur.coerceIn(60_000L, 300_000L)
                                    seekTarget = (startPos + total.x / size.width * span).toLong().coerceIn(0L, dur)
                                    seekPreview = seekTarget
                                    ch.consume()
                                }
                                2 -> {
                                    val b = (startBright - total.y / size.height * 1.3f).coerceIn(0.01f, 1f)
                                    act.setBrightness(b)
                                    flash("Brightness ${(b * 100).roundToInt()}%")
                                    ch.consume()
                                }
                                3 -> {
                                    val maxLevel = if (swipeBoost) 3f else 1f
                                    val level = (startLevel - total.y / size.height * 1.6f).coerceIn(0f, maxLevel)
                                    if (level <= 1f) {
                                        val v = (level * maxVol).roundToInt()
                                        if (v != lastVol) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); lastVol = v }
                                        if (PlayerHub.boost.value != 100) PlayerHub.setBoost(100)
                                        flash("Volume ${(level * 100).roundToInt()}%")
                                    } else {
                                        if (lastVol != maxVol) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0); lastVol = maxVol }
                                        val b = ((level * 100) / 10).roundToInt() * 10
                                        if (b != PlayerHub.boost.value) PlayerHub.setBoost(b)
                                        flash("Boost ${PlayerHub.boost.value}%")
                                    }
                                    ch.consume()
                                }
                            }
                        }
                        if (mode == 1) { PlayerHub.seekTo(seekTarget); seekPreview = null }
                    }
                },
        )

        // Seek preview and quick hints
        seekPreview?.let { t ->
            val diff = t - PlayerHub.positionMs()
            CenterBadge("${formatDuration(t)}  (${if (diff >= 0) "+" else "−"}${formatDuration(abs(diff))})", Modifier.align(Alignment.Center))
        }
        hint?.let { if (seekPreview == null) CenterBadge(it, Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) }

        // Controls
        AnimatedVisibility(controls && !inPip, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            if (locked) Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { locked = false; flash("Unlocked"); touchKey++ },
                    modifier = Modifier.align(Alignment.CenterStart).padding(24.dp).size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                ) { Icon(Icons.Rounded.LockOpen, "Unlock", tint = Color.White) }
            } else Box(Modifier.fillMaxSize()) {
                // top bar
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { act.finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(cur?.title ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        val q = PlayerHub.queue
                        Text(
                            listOfNotNull(
                                if (q.size > 1) "${q.pos + 1} of ${q.size}" else null,
                                if (dims.width > 0) "${dims.width}×${dims.height}" else null,
                                if (s.engine == EngineKind.VLC) "VLC" else null,
                                if (s.shuffle) "shuffle" else null,
                            ).joinToString(" • "),
                            color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = { panel = VPanel.SUBS }) { Icon(Icons.Rounded.ClosedCaption, "Subtitles", tint = Color.White) }
                    IconButton(onClick = { panel = VPanel.AUDIO }) { Icon(Icons.Rounded.Audiotrack, "Audio track", tint = Color.White) }
                    IconButton(onClick = { panel = VPanel.MORE }) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
                }

                // center controls
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn(Icons.Rounded.SkipPrevious, "Previous", 48) { PlayerHub.previous(); touchKey++ }
                    RoundBtn(Icons.Rounded.Replay10, "Back", 48) { PlayerHub.seekBy(-10_000); touchKey++ }
                    RoundBtn(
                        when { s.ended -> Icons.Rounded.Replay; s.playWhenReady -> Icons.Rounded.Pause; else -> Icons.Rounded.PlayArrow },
                        "Play/pause", 72, filled = true,
                    ) { PlayerHub.toggle(); touchKey++ }
                    RoundBtn(Icons.Rounded.Forward10, "Forward", 48) { PlayerHub.seekBy(10_000); touchKey++ }
                    RoundBtn(Icons.Rounded.SkipNext, "Next", 48, enabled = s.hasNext) { PlayerHub.next(); touchKey++ }
                }

                // bottom bar
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    val dur = s.durationMs
                    val shown = if (dragging) (dragValue * dur).toLong() else pos
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(shown), color = Color.White, fontSize = 13.sp, modifier = Modifier.width(64.dp))
                        Slider(
                            value = if (dragging) dragValue else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                            onValueChange = { dragging = true; dragValue = it; touchKey++ },
                            onValueChangeFinished = { PlayerHub.seekTo((dragValue * dur).toLong()); pos = (dragValue * dur).toLong(); dragging = false },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                        )
                        Text(
                            if (showRemaining) "-" + formatDuration((dur - shown).coerceAtLeast(0)) else formatDuration(dur),
                            color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.width(64.dp).clickable { showRemaining = !showRemaining }.padding(start = 8.dp),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        BarBtn(Icons.Rounded.Lock, "Lock") { locked = true; flash("Screen locked — tap the lock to unlock") }
                        BarBtn(Icons.Rounded.AspectRatio, aspectModes.firstOrNull { it.first == aspect }?.second ?: "Fit") {
                            val i = aspectModes.indexOfFirst { it.first == aspect }
                            aspect = aspectModes[(i + 1) % aspectModes.size].first
                            zoom = 1f; pan = Offset.Zero
                            flash(aspectModes.first { it.first == aspect }.second)
                            touchKey++
                        }
                        BarBtn(Icons.Rounded.ScreenRotation, "Rotate") { act.toggleRotation(); touchKey++ }
                        BarBtn(Icons.Rounded.Speed, com.rady.reelio.ui.speedLabel(s.speed)) { panel = VPanel.SPEED }
                        BarBtn(Icons.AutoMirrored.Rounded.VolumeUp, if (boost > 100) "$boost%" else "Boost", highlight = boost > 100) { panel = VPanel.BOOST }
                        BarBtn(
                            if (s.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            when (s.repeat) { RepeatMode.OFF -> "Loop"; RepeatMode.ALL -> "List"; RepeatMode.ONE -> "One" },
                            highlight = s.repeat != RepeatMode.OFF,
                        ) { PlayerHub.cycleRepeat(); touchKey++ }
                        if (VideoActivity.supportsPip) BarBtn(Icons.Rounded.PictureInPictureAlt, "PiP") { act.enterPip() }
                    }
                }
            }
        }
    }

    panel?.let { p ->
        ModalBottomSheet(
            onDismissRequest = { panel = null; touchKey++ },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            when (p) {
                VPanel.SUBS -> SubtitlesPanel(act)
                VPanel.AUDIO -> AudioTracksPanel()
                VPanel.SPEED -> SpeedPanel()
                VPanel.BOOST -> BoostPanel()
                VPanel.SLEEP -> SleepPanel { panel = null }
                VPanel.EQ -> EqualizerPanel()
                VPanel.QUEUE -> QueuePanel { panel = null }
                VPanel.ASPECT -> Column {
                    PanelTitle("Picture size")
                    Chips(aspectModes.map { it.second to (it.first == aspect) }) { aspect = aspectModes[it].first; zoom = 1f; pan = Offset.Zero }
                    Text("Tip: pinch with two fingers to zoom in or out freely.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                }
                VPanel.MORE -> MorePanel(act, dims, onOpen = { panel = it })
            }
        }
    }
}

@Composable
private fun CenterBadge(text: String, modifier: Modifier) {
    Text(
        text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun RoundBtn(icon: ImageVector, desc: String, size: Int, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, desc, Modifier.size((size * 0.55f).dp),
            tint = if (filled) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = if (enabled) 1f else 0.35f),
        )
    }
}

@Composable
private fun BarBtn(icon: ImageVector, label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = if (highlight) MaterialTheme.colorScheme.primary else Color.White
        Icon(icon, label, tint = c, modifier = Modifier.size(22.dp))
        Text(label, color = c, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun ExoSurface(dims: VideoDims, aspect: String, zoom: Float, pan: Offset) {
    val exo = PlayerHub.exoPlayer ?: return
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val cw = maxWidth.value
        val ch = maxHeight.value
        val density = LocalDensity.current
        val vr = fixedRatio(aspect) ?: dims.ratio
        val (w, h) = when {
            aspect == "fill" -> cw to ch
            aspect == "crop" -> if (cw / ch > vr) cw to cw / vr else ch * vr to ch
            aspect == "original" && dims.width > 0 -> with(density) { (dims.width * dims.pixelRatio).toDp().value to dims.height.toDp().value }
            else -> if (cw / ch > vr) ch * vr to ch else cw to cw / vr
        }
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { exo.setVideoView(it) } },
            onRelease = { exo.clearVideoView(it) },
            modifier = Modifier.requiredSize(w.dp, h.dp).graphicsLayer {
                scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
            },
        )
    }
}

@Composable
private fun VlcSurface(aspect: String, zoom: Float, pan: Offset) {
    val vlc = PlayerHub.vlcPlayer ?: return
    AndroidView(
        factory = { ctx -> VLCVideoLayout(ctx).also { vlc.attach(it) } },
        onRelease = { vlc.detach() },
        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
    )
    LaunchedEffect(aspect) { vlc.setAspect(aspect) }
}

@Composable
private fun SubtitleOverlay(pos: Long, small: Boolean, liftForControls: Boolean) {
    val sidecar by PlayerHub.sidecar.collectAsState()
    val embedded by PlayerHub.embeddedCue.collectAsState()
    val on by PlayerHub.subtitlesOn.collectAsState()
    val delayMs by PlayerHub.subtitleDelayMs.collectAsState()
    val style = rememberSubStyle()
    if (!on) return
    val text = if (sidecar != null) sidecar?.textAt(pos - delayMs) else embedded
    if (text.isNullOrBlank()) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = ((minOf(maxWidth, maxHeight).value / 19f).coerceIn(12f, 30f) * if (small) 0.55f else 1f).sp
        var margin = maxHeight * (style.margin / 100f)
        if (liftForControls && style.position == 0) margin = maxOf(margin, 96.dp)
        val hAlign = when (style.align) { 0 -> Alignment.Start; 2 -> Alignment.End; else -> Alignment.CenterHorizontally }
        Column(
            Modifier.fillMaxSize()
                .padding(top = if (style.position == 1) margin else 0.dp, bottom = if (style.position == 0) margin else 0.dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = if (style.position == 1) Arrangement.Top else Arrangement.Bottom,
            horizontalAlignment = hAlign,
        ) {
            SubtitleText(text, style, base)
        }
    }
}

@Composable
private fun SubtitlesPanel(act: VideoActivity) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importFont(ctx, uri) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                var name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "subtitle.srt"
                if (com.rady.reelio.data.MediaFormats.ext(name) !in com.rady.reelio.data.MediaFormats.subtitles) name += ".srt"
                val dir = File(ctx.cacheDir, "subs").apply { mkdirs() }
                val out = File(dir, name)
                ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                PlayerHub.loadSubtitleFile(out)
                ctx.toast("Loaded ${out.name}")
            }.onFailure { ctx.toast("Couldn't open that file") }
        }
    }
    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Subtitles") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Look & position") })
        }
        if (tab == 1) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SubtitleStylePanel(onPickFont = { fontPicker.launch(arrayOf("*/*")) }, showPreview = true)
            }
            return@Column
        }
        val s by PlayerHub.state.collectAsState()
        val cur = s.current
        val on by PlayerHub.subtitlesOn.collectAsState()
        val sidecar by PlayerHub.sidecar.collectAsState()
        val tracksV by PlayerHub.tracksVersion.collectAsState()
        val delayMs by PlayerHub.subtitleDelayMs.collectAsState()
        val files by produceState(emptyList<File>(), cur?.uri) {
            value = if (cur != null && cur.isFile) withContext(Dispatchers.IO) {
                (Subtitles.findFor(cur.uri) + Subtitles.allInFolder(cur.uri)).distinctBy { it.absolutePath }
            } else emptyList()
        }
        val embedded = remember(tracksV, s.engine, cur?.uri) { PlayerHub.engine?.textTracks() ?: emptyList() }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Show subtitles", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(on, { PlayerHub.setSubtitlesOn(it) })
            }
            if (s.engine == EngineKind.VLC) Text(
                "This file plays with VLC, which loads same-name subtitles by itself. Style options apply to the main engine.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (files.isNotEmpty()) {
                SubHeader("Subtitle files in this folder")
                files.forEach { f ->
                    val active = sidecar?.file?.absolutePath == f.absolutePath
                    OptionRow(f.name, active) { PlayerHub.loadSubtitleFile(f) }
                }
            }
            if (embedded.isNotEmpty()) {
                SubHeader("Inside the video")
                embedded.forEach { t -> OptionRow(t.label, t.selected && sidecar == null && on) { PlayerHub.selectEmbeddedSubtitle(t) } }
            }
            if (files.isEmpty() && embedded.isEmpty()) Text(
                "No subtitles found. Put a subtitle file with the same name as the video in its folder, or open one below.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp),
            )
            OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Open a subtitle file…")
            }
            if (sidecar != null || s.engine == EngineKind.VLC) {
                SubHeader("Timing (if subtitles are early or late)")
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { PlayerHub.setSubtitleDelay(delayMs - 500) }) { Text("−0.5s") }
                    TextButton(onClick = { PlayerHub.setSubtitleDelay(delayMs - 100) }) { Text("−0.1s") }
                    Text(String.format(Locale.US, "%+.1f s", delayMs / 1000f), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
                    TextButton(onClick = { PlayerHub.setSubtitleDelay(delayMs + 100) }) { Text("+0.1s") }
                    TextButton(onClick = { PlayerHub.setSubtitleDelay(delayMs + 500) }) { Text("+0.5s") }
                }
                TextButton(onClick = { PlayerHub.setSubtitleDelay(0) }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Reset timing") }
                Text("Plus = show later, minus = show earlier", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            CharsetPicker()
            sidecar?.let { Text("Read as ${it.charset} • ${it.cues.size} lines", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun OptionRow(label: String, active: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(label, Modifier.padding(start = 14.dp), fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AudioTracksPanel() {
    val v by PlayerHub.tracksVersion.collectAsState()
    val s by PlayerHub.state.collectAsState()
    val tracks = remember(v, s.engine, s.current?.uri) { PlayerHub.engine?.audioTracks() ?: emptyList() }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        PanelTitle("Audio track")
        if (tracks.isEmpty()) Text("This video has one audio track.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        tracks.forEach { t -> OptionRow(t.label, t.selected) { PlayerHub.engine?.selectAudio(t) } }
    }
}

@Composable
private fun MorePanel(act: VideoActivity, dims: VideoDims, onOpen: (VPanel) -> Unit) {
    val ctx = LocalContext.current
    val s by PlayerHub.state.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        PanelTitle("More")
        val items: List<Triple<ImageVector, String, () -> Unit>> = listOf(
            Triple(Icons.Rounded.Speed, "Speed", { onOpen(VPanel.SPEED) }),
            Triple(Icons.AutoMirrored.Rounded.VolumeUp, "Volume boost", { onOpen(VPanel.BOOST) }),
            Triple(Icons.Rounded.Equalizer, "Equalizer", { onOpen(VPanel.EQ) }),
            Triple(Icons.Rounded.Timer, "Sleep timer", { onOpen(VPanel.SLEEP) }),
            Triple(Icons.Rounded.AspectRatio, "Picture size", { onOpen(VPanel.ASPECT) }),
            Triple(Icons.AutoMirrored.Rounded.QueueMusic, "Up next", { onOpen(VPanel.QUEUE) }),
            Triple(Icons.Rounded.Headphones, "Play as audio", {
                act.keepPlayingOnLeave = true
                ctx.startActivity(Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                act.finish()
            }),
            Triple(Icons.Rounded.Info, "Details", {
                val e = s.current
                if (e != null) ctx.toast(listOfNotNull(e.fileName, if (dims.width > 0) "${dims.width}×${dims.height}" else null, if (s.engine == EngineKind.VLC) "VLC engine" else "Main engine").joinToString(" • "))
            }),
        )
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                row.forEach { (icon, label, action) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = action).padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icon, label, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text(label, fontSize = 12.sp, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Shuffle", Modifier.weight(1f))
            Switch(s.shuffle, { PlayerHub.setShuffle(it) })
        }
        FilledTonalButton(onClick = { PlayerHub.cycleRepeat() }, modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                when (s.repeat) { RepeatMode.OFF -> "Loop: off"; RepeatMode.ALL -> "Loop: whole list"; RepeatMode.ONE -> "Loop: this video" },
            )
        }
    }
}
