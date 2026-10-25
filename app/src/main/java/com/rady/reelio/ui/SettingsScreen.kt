package com.rady.reelio.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rady.reelio.AppGraph
import com.rady.reelio.BuildConfig
import com.rady.reelio.data.MediaFormats
import com.rady.reelio.data.Pref
import com.rady.reelio.player.PlayerHub
import java.io.File

@Composable
fun SettingsScreen(open: (Route) -> Unit) {
    val s = AppGraph.settings
    val theme by s.theme.flow.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Group("Theme")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            (listOf("system" to "Auto") + Themes.map { it.key to it.name }).forEach { (key, name) ->
                val spec = themeFor(key, androidx.compose.foundation.isSystemInDarkTheme())
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { s.theme.value = key }.padding(4.dp)) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(spec.background, spec.surfaceHigh, spec.primary)))
                            .border(if (theme == key) 3.dp else 1.dp, if (theme == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (theme == key) Icon(Icons.Rounded.Check, null, tint = spec.primary, modifier = Modifier.size(18.dp)) }
                    Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Group("Playback")
        Toggle("Remember where I stopped", "Videos and long audio (audiobooks, podcasts) continue from the same spot", s.rememberPosition)
        Toggle("Skip silence", "Skips quiet gaps (main engine only)", s.skipSilence) { (PlayerHub.exoPlayer)?.setSkipSilence(it) }
        Row(Modifier.fillMaxWidth().clickable { open(Route.Boost) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Volume boost & equalizer")
                Text("Boost up to 300%, adjust bass and treble", style = subtleText)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }

        Group("Video player")
        Toggle("Keep playing in the background", "When you leave the video or turn off the screen, the sound continues", s.backgroundVideo)
        Toggle("Picture-in-picture when leaving", "Shrink the video into a small window when you press Home", s.autoPip)
        Toggle("Swipe gestures", "Left side: brightness • Right side: volume • Sideways: seek • Pinch: zoom", s.gestures)
        Toggle("Swipe volume above 100%", "Keep swiping up past max volume to boost up to 300%", s.swipeBoost)
        Toggle("Rotate with the video", "Turns to landscape for wide videos and portrait for tall ones", s.autoRotate)
        Toggle("Keep the screen on", "While a video is playing", s.keepScreenOn)
        Choice("Double-tap to skip", listOf(5, 10, 15, 30).map { "$it s" }, listOf(5, 10, 15, 30).indexOf(s.doubleTapSec.flow.collectAsState().value)) {
            s.doubleTapSec.value = listOf(5, 10, 15, 30)[it]
        }
        val aspects = listOf("fit", "fill", "crop", "16:9", "4:3")
        Choice("Default picture size", listOf("Fit", "Stretch", "Crop", "16:9", "4:3"), aspects.indexOf(s.defaultAspect.flow.collectAsState().value)) {
            s.defaultAspect.value = aspects[it]
        }
        Row(Modifier.fillMaxWidth().clickable { open(Route.SubtitleSettings) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Subtitles")
                Text("Size, font, colors, outline, position, encoding", style = subtleText)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }

        Group("Library")
        val mins = listOf(0, 5, 15, 30, 60)
        Choice("Hide audio shorter than", mins.map { if (it == 0) "Show all" else "$it s" }, mins.indexOf(s.minAudioSec.flow.collectAsState().value)) {
            s.minAudioSec.value = mins[it]; AppGraph.library.refresh()
        }
        Toggle("Show hidden folders in Browse", "Folders whose names start with a dot", s.browserShowHidden)
        TextButton(onClick = { AppGraph.library.refresh() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Rescan library now") }

        Group("About")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Reelio ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
            Text("Music & video player. Two engines: Media3 (with FFmpeg audio) for everyday files, and VLC for everything else.", style = subtleText)
            Spacer(Modifier.height(8.dp))
            Text("Supported files", style = MaterialTheme.typography.labelLarge)
            Text(MediaFormats.allListed, style = subtleText)
            Spacer(Modifier.height(4.dp))
            Text("Subtitles: srt, vtt, ass, ssa, sub, smi", style = subtleText)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 6.dp))
}

@Composable
private fun Toggle(title: String, sub: String, pref: Pref<Boolean>, after: ((Boolean) -> Unit)? = null) {
    val v by pref.flow.collectAsState()
    Row(Modifier.fillMaxWidth().clickable { pref.value = !v; after?.invoke(!v) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(sub, style = subtleText)
        }
        Switch(v, { pref.value = it; after?.invoke(it) })
    }
}

@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, modifier = Modifier.padding(horizontal = 20.dp))
        Chips(options.mapIndexed { i, o -> o to (i == selected) }, onSelect)
    }
}

@Composable
fun SubtitleSettingsScreen() {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFont(ctx, uri)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubtitleStylePanel(onPickFont = { picker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")) })
        HorizontalDivider()
        Text(
            "Tip: name the subtitle file like the video (Movie.mkv → Movie.srt or Movie.en.srt) and Reelio loads it by itself. " +
                "You can also pick any subtitle file from the video player.",
            style = subtleText, modifier = Modifier.padding(20.dp),
        )
    }
}

fun importFont(ctx: Context, uri: Uri) {
    runCatching {
        val dir = File(ctx.filesDir, "fonts").apply { mkdirs() }
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "custom.ttf"
        val ext = if (name.lowercase().endsWith(".otf")) ".otf" else ".ttf"
        val out = File(dir, "custom$ext")
        ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
        android.graphics.Typeface.createFromFile(out)   // throws if it isn't a font
        AppGraph.settings.subFontPath.value = out.absolutePath
        AppGraph.settings.subFont.value = "custom"
        ctx.toast("Font loaded")
    }.onFailure { ctx.toast("That file isn't a font Reelio can use") }
}

@Composable
fun BoostEqScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BoostPanel()
        HorizontalDivider()
        EqualizerPanel(scrollable = false)
    }
}

