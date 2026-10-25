package com.rady.reelio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.rady.reelio.data.MediaEntry
import com.rady.reelio.data.MediaFormats
import com.rady.reelio.player.PlaybackService
import com.rady.reelio.player.PlayerHub
import com.rady.reelio.ui.ReelioRoot
import com.rady.reelio.ui.ReelioTheme
import com.rady.reelio.ui.openVideoPlayer
import com.rady.reelio.ui.themeFor
import java.io.File

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN_PLAYER = "open_player" }

    private val openPlayerSignal = mutableIntStateOf(0)
    private var controller: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeKey by AppGraph.settings.theme.flow.collectAsState()
            val dark = themeFor(themeKey, isSystemInDarkTheme()).dark
            androidx.compose.runtime.LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK),
                )
            }
            ReelioTheme(themeKey) { ReelioRoot(openPlayerSignal.intValue) }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            openPlayerSignal.intValue++
            i.removeExtra(EXTRA_OPEN_PLAYER)
        }
        if (i.action == Intent.ACTION_VIEW && i.data != null) {
            val (entry, siblings) = MediaOpener.fromUri(this, i.data!!, i.type)
            PlayerHub.playSingle(entry, siblings)
            if (entry.isVideo) openVideoPlayer(this) else openPlayerSignal.intValue++
            i.data = null
        }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.library.beginVisit()
        AppGraph.library.refresh()
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onResume() {
        super.onResume()
        PlayerHub.onVideoStart = { openVideoPlayer(this) }
    }

    override fun onPause() {
        PlayerHub.onVideoStart = null
        super.onPause()
    }

    override fun onStop() {
        AppGraph.library.endVisit()
        PlayerHub.persistNow()
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }
}

/** Turns a file someone opened with Reelio into something playable, plus its neighbours in the folder. */
object MediaOpener {
    fun fromUri(ctx: Context, uri: Uri, mime: String?): Pair<MediaEntry, List<MediaEntry>?> {
        val path: String? = when (uri.scheme) {
            "file" -> uri.path
            "content" -> runCatching {
                ctx.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()?.takeIf { File(it).canRead() }
            else -> null
        }
        if (path != null) {
            val f = File(path)
            val entry = AppGraph.library.entryForPath(f.absolutePath)
            val siblings = f.parentFile?.listFiles()
                ?.filter { it.isFile && (if (entry.isVideo) MediaFormats.isVideo(it.name) else MediaFormats.isAudio(it.name)) }
                ?.sortedBy { it.name.lowercase() }
                ?.let { AppGraph.library.entriesForFiles(it) }
            return entry to siblings?.takeIf { s -> s.any { it.uri == entry.uri } }
        }
        var name = uri.lastPathSegment ?: "Media"
        var size = 0L
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) { name = c.getString(0) ?: name; size = c.getLong(1) }
            }
        }
        val type = mime ?: ctx.contentResolver.getType(uri) ?: ""
        val isVideo = type.startsWith("video") || type == "application/x-matroska" || type == "application/mp4" || MediaFormats.isVideo(name)
        return MediaEntry(uri = uri.toString(), title = name.substringBeforeLast('.'), isVideo = isVideo, sizeBytes = size) to null
    }
}
