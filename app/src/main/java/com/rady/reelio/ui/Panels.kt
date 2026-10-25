package com.rady.reelio.ui

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rady.reelio.AppGraph
import com.rady.reelio.data.Subtitles
import com.rady.reelio.data.formatDuration
import com.rady.reelio.player.PlayerHub
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

fun speedLabel(f: Float): String = String.format(Locale.US, "%.2f", f).trimEnd('0').trimEnd('.') + "×"

@Composable
fun PanelTitle(text: String, onClose: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
    }
}

@Composable
fun Chips(values: List<Pair<String, Boolean>>, onClick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        values.forEachIndexed { i, (label, sel) ->
            FilterChip(selected = sel, onClick = { onClick(i) }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
        }
    }
}

// ---------- speed ----------

@Composable
fun SpeedPanel() {
    val s by PlayerHub.state.collectAsState()
    val presets = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
    Column {
        PanelTitle("Playback speed")
        Text(speedLabel(s.speed), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Slider(
            value = s.speed, onValueChange = { PlayerHub.setSpeed((it * 20).roundToInt() / 20f) },
            valueRange = 0.25f..4f, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Chips(presets.map { (if (it == 1f) "Normal" else "${it}×") to (kotlin.math.abs(s.speed - it) < 0.01f) }) { PlayerHub.setSpeed(presets[it]) }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- sleep timer ----------

@Composable
fun SleepPanel(onDone: () -> Unit) {
    val s by PlayerHub.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Column {
        PanelTitle("Sleep timer")
        val at = s.sleepAtMs
        Text(
            when {
                at != null -> "Stops in ${formatDuration(at - now)}"
                s.sleepAfterTrack -> "Stops at the end of this track"
                else -> "Off"
            },
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val mins = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
        Chips(mins.map { "$it min" to false }) { PlayerHub.setSleepTimer(mins[it]); onDone() }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            TextButton(onClick = { PlayerHub.sleepAfterCurrent(); onDone() }) { Text("End of this track") }
            TextButton(onClick = { PlayerHub.setSleepTimer(null) }) { Text("Turn off") }
        }
    }
}

// ---------- volume boost ----------

@Composable
fun BoostPanel() {
    val boost by PlayerHub.boost.collectAsState()
    Column {
        PanelTitle("Volume boost")
        Text("$boost%", style = MaterialTheme.typography.headlineMedium,
            color = if (boost > 100) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp))
        Slider(
            value = boost.toFloat(), onValueChange = { PlayerHub.setBoost((it / 10).roundToInt() * 10) },
            valueRange = 100f..300f, steps = 19, modifier = Modifier.padding(horizontal = 20.dp),
        )
        val presets = listOf(100, 125, 150, 200, 250, 300)
        Chips(presets.map { (if (it == 100) "Off" else "$it%") to (boost == it) }) { PlayerHub.setBoost(presets[it]) }
        Text(
            "Makes quiet songs and videos louder than the phone's maximum. High levels can distort sound — and protect your ears and speakers.",
            style = subtleText, modifier = Modifier.padding(20.dp),
        )
    }
}

// ---------- equalizer ----------

@Composable
fun EqualizerPanel(scrollable: Boolean = true) {
    val eq = PlayerHub.equalizer
    val settings = AppGraph.settings
    val enabled by settings.eqEnabled.flow.collectAsState()
    val preset by settings.eqPreset.flow.collectAsState()
    Column(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier) {
        PanelTitle("Equalizer")
        if (eq == null) {
            Text("Your phone doesn't offer an equalizer for apps.", style = subtleText, modifier = Modifier.padding(20.dp))
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Enabled", Modifier.weight(1f))
            Switch(enabled, { settings.eqEnabled.value = it; PlayerHub.applyEq() })
        }
        val presets = remember { runCatching { (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) } }.getOrDefault(emptyList()) }
        Chips(listOf("Custom" to (preset < 0)) + presets.mapIndexed { i, n -> n to (preset == i) }) { i ->
            settings.eqPreset.value = i - 1
            settings.eqEnabled.value = true
            PlayerHub.applyEq()
        }
        val range = remember { runCatching { eq.bandLevelRange }.getOrNull() }
        val bands = remember { runCatching { eq.numberOfBands.toInt() }.getOrDefault(0) }
        var version by remember { mutableStateOf(0) }
        if (range != null) for (b in 0 until bands) {
            val freq = runCatching { eq.getCenterFreq(b.toShort()) / 1000 }.getOrDefault(0)
            val level = remember(version, preset) { runCatching { eq.getBandLevel(b.toShort()).toFloat() }.getOrDefault(0f) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz", Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = level, valueRange = range[0].toFloat()..range[1].toFloat(),
                    onValueChange = { v ->
                        runCatching {
                            settings.eqEnabled.value = true
                            eq.enabled = true
                            eq.setBandLevel(b.toShort(), v.toInt().toShort())
                            val all = (0 until bands).map { eq.getBandLevel(it.toShort()).toInt() }
                            settings.eqBands.value = all.joinToString(",")
                            settings.eqPreset.value = -1
                            version++
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(String.format(Locale.US, "%+.0f dB", level / 100f), Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- queue ----------

@Composable
fun QueuePanel(onClose: () -> Unit) {
    val s by PlayerHub.state.collectAsState()
    val upcoming = remember(s.queueVersion, s.current) { PlayerHub.queue.upcoming() }
    Column {
        PanelTitle("Up next", onClose)
        Text(
            (if (s.shuffle) "Smart shuffle" else "In order") + " • " + when (s.repeat) {
                com.rady.reelio.player.RepeatMode.OFF -> "no loop"
                com.rady.reelio.player.RepeatMode.ALL -> "looping the list"
                com.rady.reelio.player.RepeatMode.ONE -> "looping this one"
            } + " • ${PlayerHub.queue.size} in queue",
            style = subtleText, modifier = Modifier.padding(horizontal = 20.dp),
        )
        s.current?.let { cur ->
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaThumb(cur, Modifier.size(44.dp), sizePx = 160)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Now playing", style = subtleText, color = MaterialTheme.colorScheme.primary)
                    Text(cur.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(upcoming, key = { it.first.toString() + it.second.uri }) { (p, e) ->
                Row(Modifier.fillMaxWidth().clickable { PlayerHub.playOrderPos(p) }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MediaThumb(e, Modifier.size(40.dp), sizePx = 160)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.artist.ifBlank { formatDuration(e.durationMs) }, style = subtleText, maxLines = 1)
                    }
                    IconButton(onClick = { PlayerHub.moveInQueue(p, p - 1) }, enabled = p - 1 > PlayerHub.queue.pos) { Icon(Icons.Rounded.ArrowUpward, "Up") }
                    IconButton(onClick = { PlayerHub.moveInQueue(p, p + 1) }, enabled = p + 1 < PlayerHub.queue.pos + 1 + upcoming.size) { Icon(Icons.Rounded.ArrowDownward, "Down") }
                    IconButton(onClick = { PlayerHub.removeFromQueue(p) }) { Icon(Icons.Rounded.Close, "Remove") }
                }
            }
            if (upcoming.isEmpty()) item {
                Text(
                    if (s.repeat == com.rady.reelio.player.RepeatMode.ALL) "End of the list — it will start over." else "Nothing after this one.",
                    style = subtleText, modifier = Modifier.padding(20.dp),
                )
            }
        }
        Row(Modifier.padding(8.dp)) {
            TextButton(onClick = { PlayerHub.clearQueue(); onClose() }) { Text("Clear queue") }
        }
    }
}

// ---------- subtitles ----------

data class SubStyle(
    val scale: Int, val font: String, val fontPath: String, val bold: Boolean, val italic: Boolean,
    val color: Int, val bgOn: Boolean, val bgColor: Int, val bgOpacity: Int,
    val edge: Int, val edgeColor: Int, val edgeWidth: Int, val position: Int, val margin: Int, val align: Int,
)

@Composable
fun rememberSubStyle(): SubStyle {
    val s = AppGraph.settings
    return SubStyle(
        s.subScale.flow.collectAsState().value, s.subFont.flow.collectAsState().value, s.subFontPath.flow.collectAsState().value,
        s.subBold.flow.collectAsState().value, s.subItalic.flow.collectAsState().value, s.subColor.flow.collectAsState().value,
        s.subBgOn.flow.collectAsState().value, s.subBgColor.flow.collectAsState().value, s.subBgOpacity.flow.collectAsState().value,
        s.subEdge.flow.collectAsState().value, s.subEdgeColor.flow.collectAsState().value, s.subEdgeWidth.flow.collectAsState().value,
        s.subPosition.flow.collectAsState().value, s.subMargin.flow.collectAsState().value, s.subAlign.flow.collectAsState().value,
    )
}

val subtitleFonts = listOf(
    "sans" to "Sans (default)", "sans-medium" to "Sans Medium", "sans-condensed" to "Condensed", "sans-light" to "Light",
    "serif" to "Serif", "mono" to "Monospace", "serif-mono" to "Typewriter", "casual" to "Casual", "cursive" to "Handwriting",
    "custom" to "My font file…",
)

fun subtitleFamily(key: String, path: String): FontFamily = when (key) {
    "sans-medium" -> FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)))
    "sans-condensed" -> FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL)))
    "sans-light" -> FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.create("sans-serif-light", Typeface.NORMAL)))
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "serif-mono" -> FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.create("serif-monospace", Typeface.NORMAL)))
    "casual" -> FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.create("casual", Typeface.NORMAL)))
    "cursive" -> FontFamily.Cursive
    "custom" -> if (path.isNotBlank() && File(path).exists()) runCatching { FontFamily(Font(File(path))) }.getOrDefault(FontFamily.SansSerif) else FontFamily.SansSerif
    else -> FontFamily.SansSerif
}

/** Draws subtitle text with the chosen font, colors, outline, shadow and background. */
@Composable
fun SubtitleText(text: String, style: SubStyle, baseSize: TextUnit, modifier: Modifier = Modifier) {
    val family = remember(style.font, style.fontPath) { subtitleFamily(style.font, style.fontPath) }
    val size = baseSize * (style.scale / 100f)
    val align = when (style.align) { 0 -> TextAlign.Start; 2 -> TextAlign.End; else -> TextAlign.Center }
    val base = TextStyle(
        fontFamily = family, fontSize = size, lineHeight = size * 1.18f, textAlign = align,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Medium,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
    )
    val edgeColor = Color(style.edgeColor)
    val withShadow = style.edge == 2 || style.edge == 3
    val withOutline = style.edge == 1 || style.edge == 3
    val bg = if (style.bgOn) Color(style.bgColor).copy(alpha = style.bgOpacity / 100f) else Color.Transparent
    Box(modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = if (style.bgOn) 10.dp else 0.dp, vertical = if (style.bgOn) 3.dp else 0.dp)) {
        if (withOutline) Text(
            text,
            style = base.copy(color = edgeColor, drawStyle = Stroke(width = style.edgeWidth * 2.2f, join = androidx.compose.ui.graphics.StrokeJoin.Round)),
        )
        Text(
            text,
            style = base.copy(
                color = Color(style.color),
                shadow = if (withShadow) Shadow(edgeColor.copy(alpha = 0.9f), Offset(3f, 3f), 6f) else null,
            ),
        )
    }
}

@Composable
private fun ColorRow(label: String, value: Int, colors: List<Long>, onPick: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { c ->
                val ci = c.toInt()
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(ci))
                        .border(if (ci == value) 3.dp else 1.dp, if (ci == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onPick(ci) },
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Int, range: IntRange, step: Int, suffix: String, onChange: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${v.roundToInt()}$suffix", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = v, valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            onValueChange = { v = it; onChange(((it / step).roundToInt() * step).coerceIn(range)) },
        )
    }
}

/** All subtitle look & position options, with a live preview. */
@Composable
fun SubtitleStylePanel(onPickFont: () -> Unit, showPreview: Boolean = true) {
    val s = AppGraph.settings
    val st = rememberSubStyle()
    val textColors = listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFFFFD54F, 0xFF80DEEA, 0xFFA5D6A7, 0xFFF48FB1, 0xFFFFAB91, 0xFFBDBDBD, 0xFF000000)
    val bgColors = listOf(0xFF000000, 0xFF263238, 0xFF1A237E, 0xFF4A148C, 0xFF3E2723, 0xFFFFFFFF)
    Column {
        if (showPreview) Box(
            Modifier.fillMaxWidth().padding(16.dp).height(120.dp).clip(RoundedCornerShape(14.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF3A4B5C), Color(0xFF7A6A55), Color(0xFF2C3B2E)))),
            contentAlignment = if (st.position == 1) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            SubtitleText("This is how your subtitles\nwill look on the video", st, 16.sp, Modifier.padding(12.dp))
        }
        LabeledSlider("Size", st.scale, 50..300, 10, "%") { s.subScale.value = it }
        Text("Font", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
        Chips(subtitleFonts.map { it.second to (it.first == st.font) }) { i ->
            val key = subtitleFonts[i].first
            if (key == "custom") onPickFont() else s.subFont.value = key
        }
        Row(Modifier.padding(horizontal = 12.dp)) {
            FilterChip(st.bold, { s.subBold.value = !st.bold }, { Text("Bold", fontWeight = FontWeight.Bold) }, Modifier.padding(4.dp))
            FilterChip(st.italic, { s.subItalic.value = !st.italic }, { Text("Italic", fontStyle = FontStyle.Italic) }, Modifier.padding(4.dp))
        }
        ColorRow("Text color", st.color, textColors) { s.subColor.value = it }
        Text("Outline & shadow", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
        Chips(listOf("None", "Outline", "Shadow", "Both").mapIndexed { i, n -> n to (st.edge == i) }) { s.subEdge.value = it }
        if (st.edge != 0) {
            ColorRow("Outline color", st.edgeColor, listOf(0xFF000000, 0xFF424242, 0xFF0D47A1, 0xFF4A148C, 0xFFFFFFFF)) { s.subEdgeColor.value = it }
            if (st.edge != 2) LabeledSlider("Outline thickness", st.edgeWidth, 1..8, 1, "") { s.subEdgeWidth.value = it }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Background box", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(st.bgOn, { s.subBgOn.value = it })
        }
        if (st.bgOn) {
            ColorRow("Background color", st.bgColor, bgColors) { s.subBgColor.value = it }
            LabeledSlider("Background opacity", st.bgOpacity, 0..100, 5, "%") { s.subBgOpacity.value = it }
        }
        Text("Position", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
        Chips(listOf("Bottom" to (st.position == 0), "Top" to (st.position == 1))) { s.subPosition.value = it }
        LabeledSlider(if (st.position == 0) "Distance from bottom" else "Distance from top", st.margin, 0..45, 1, "%") { s.subMargin.value = it }
        Chips(listOf("Left", "Center", "Right").mapIndexed { i, n -> n to (st.align == i) }) { s.subAlign.value = it }
        Spacer(Modifier.height(8.dp))
        CharsetPicker()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Load subtitles automatically", style = MaterialTheme.typography.labelLarge)
                Text("Finds a subtitle file with the same name as the video", style = subtleText)
            }
            Switch(s.subAuto.flow.collectAsState().value, { s.subAuto.value = it })
        }
        TextButton(onClick = {
            s.subScale.value = 100; s.subFont.value = "sans"; s.subBold.value = false; s.subItalic.value = false
            s.subColor.value = 0xFFFFFFFF.toInt(); s.subBgOn.value = false; s.subEdge.value = 1; s.subEdgeColor.value = 0xFF000000.toInt()
            s.subEdgeWidth.value = 3; s.subPosition.value = 0; s.subMargin.value = 6; s.subAlign.value = 1
        }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Reset to defaults") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun CharsetPicker() {
    val s = AppGraph.settings
    val cs by s.subCharset.flow.collectAsState()
    val nl by s.subNonLatin.flow.collectAsState()
    var open by remember { mutableStateOf(false) }
    var openNl by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text("Text encoding", style = MaterialTheme.typography.labelLarge)
        Text("Fixes subtitles that show strange symbols instead of letters", style = subtleText)
        Box {
            TextButton(onClick = { open = true }) { Text(Subtitles.charsets.firstOrNull { it.first == cs }?.second ?: cs) }
            androidx.compose.material3.DropdownMenu(open, { open = false }) {
                Subtitles.charsets.forEach { (k, n) ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(n) }, onClick = { s.subCharset.value = k; open = false; PlayerHub.reloadSubtitleEncoding() })
                }
            }
        }
        if (cs == "auto") Box {
            TextButton(onClick = { openNl = true }) {
                Text("Old non-Unicode files: " + (Subtitles.charsets.firstOrNull { it.first == nl }?.second ?: nl))
            }
            androidx.compose.material3.DropdownMenu(openNl, { openNl = false }) {
                Subtitles.charsets.filter { it.first != "auto" && it.first != "UTF-8" && it.first != "UTF-16" }.forEach { (k, n) ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(n) }, onClick = { s.subNonLatin.value = k; openNl = false; PlayerHub.reloadSubtitleEncoding() })
                }
            }
        }
    }
}
