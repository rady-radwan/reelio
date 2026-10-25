package com.rady.reelio.video

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.rady.reelio.AppGraph
import com.rady.reelio.MediaOpener
import com.rady.reelio.player.PlaybackService
import com.rady.reelio.player.PlayerHub
import com.rady.reelio.ui.ReelioTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class VideoActivity : ComponentActivity() {
    val inPip = mutableStateOf(false)
    private var controller: ListenableFuture<MediaController>? = null
    private var manualOrientation = false
    var keepPlayingOnLeave = false

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.getStringExtra("a")) {
                "toggle" -> PlayerHub.toggle()
                "next" -> PlayerHub.next()
                "prev" -> PlayerHub.previous()
            }
            updatePipParams()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)
        handleIntent(intent)
        if (PlayerHub.queue.current == null) { finish(); return }
        setContent {
            val theme by AppGraph.settings.theme.flow.collectAsState()
            ReelioTheme(theme) { VideoScreen(this) }
        }
        lifecycleScope.launch {
            PlayerHub.state.collectLatest { s ->
                val keep = s.playWhenReady && !s.ended && AppGraph.settings.keepScreenOn.value
                if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (inPip.value) updatePipParams()
            }
        }
        lifecycleScope.launch {
            PlayerHub.video.collectLatest { d ->
                if (d.width > 0 && AppGraph.settings.autoRotate.value && !manualOrientation) {
                    requestedOrientation = if (d.ratio >= 1.05f) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW && i.data != null) {
            val (entry, siblings) = MediaOpener.fromUri(this, i.data!!, i.type)
            PlayerHub.playSingle(entry, siblings)
            i.data = null
        }
    }

    fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun toggleRotation() {
        manualOrientation = true
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /** Screen brightness for this window only; -1 = follow the system. */
    fun setBrightness(v: Float) {
        window.attributes = window.attributes.apply { screenBrightness = v }
    }

    fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0) return b
        return runCatching { android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
    }

    private fun pipParams(): PictureInPictureParams {
        val d = PlayerHub.video.value
        val ratio = if (d.width > 0) Rational((d.width * d.pixelRatio).toInt().coerceAtLeast(1), d.height.coerceAtLeast(1)) else Rational(16, 9)
        val r = ratio.toFloat()
        val clamped = when {
            r > 2.39f -> Rational(239, 100)
            r < 0.42f -> Rational(42, 100)
            else -> ratio
        }
        val playing = PlayerHub.isPlayWhenReady
        fun action(icon: Int, title: String, key: String, code: Int) = RemoteAction(
            Icon.createWithResource(this, icon), title, title,
            PendingIntent.getBroadcast(this, code, Intent(ACTION_PIP).setPackage(packageName).putExtra("a", key), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        return PictureInPictureParams.Builder()
            .setAspectRatio(clamped)
            .setActions(
                listOf(
                    action(android.R.drawable.ic_media_previous, "Previous", "prev", 11),
                    if (playing) action(android.R.drawable.ic_media_pause, "Pause", "toggle", 12)
                    else action(android.R.drawable.ic_media_play, "Play", "toggle", 12),
                    action(android.R.drawable.ic_media_next, "Next", "next", 13),
                ),
            )
            .build()
    }

    private fun updatePipParams() = runCatching { setPictureInPictureParams(pipParams()) }

    fun enterPip() {
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (AppGraph.settings.autoPip.value && PlayerHub.isPlayWhenReady && PlayerHub.queue.current?.isVideo == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onStart() {
        super.onStart()
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onStop() {
        val leaving = !isChangingConfigurations
        if (leaving && !inPip.value && !keepPlayingOnLeave && !AppGraph.settings.backgroundVideo.value) PlayerHub.pause()
        if (leaving && inPip.value && isFinishing.not()) {
            // PiP window was closed with the X: stop like other players do, unless background play is on
            if (!AppGraph.settings.backgroundVideo.value) PlayerHub.pause()
        }
        PlayerHub.persistNow()
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    companion object {
        const val ACTION_PIP = "com.rady.reelio.PIP_ACTION"
        val supportsPip: Boolean get() = Build.VERSION.SDK_INT >= 26
    }
}
