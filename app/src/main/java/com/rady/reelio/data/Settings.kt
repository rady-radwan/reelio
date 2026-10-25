package com.rady.reelio.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A saved setting that the UI can observe. */
class Pref<T>(
    private val sp: SharedPreferences,
    private val key: String,
    def: T,
    read: (SharedPreferences, String, T) -> T,
    private val write: (SharedPreferences.Editor, String, T) -> Unit,
) {
    private val state = MutableStateFlow(read(sp, key, def))
    val flow: StateFlow<T> get() = state
    var value: T
        get() = state.value
        set(v) {
            state.value = v
            sp.edit().also { write(it, key, v) }.apply()
        }
}

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("reelio_settings", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = Pref(sp, k, d, { s, kk, dd -> s.getString(kk, dd) ?: dd }, { e, kk, v -> e.putString(kk, v) })
    private fun int(k: String, d: Int) = Pref(sp, k, d, { s, kk, dd -> s.getInt(kk, dd) }, { e, kk, v -> e.putInt(kk, v) })
    private fun bool(k: String, d: Boolean) = Pref(sp, k, d, { s, kk, dd -> s.getBoolean(kk, dd) }, { e, kk, v -> e.putBoolean(kk, v) })
    private fun float(k: String, d: Float) = Pref(sp, k, d, { s, kk, dd -> s.getFloat(kk, dd) }, { e, kk, v -> e.putFloat(kk, v) })
    private fun long(k: String, d: Long) = Pref(sp, k, d, { s, kk, dd -> s.getLong(kk, dd) }, { e, kk, v -> e.putLong(kk, v) })

    // Look
    val theme = str("theme", "dark")

    // Playback
    val boost = int("boost", 100)                 // 100..300 %
    val speed = float("speed", 1f)
    val rememberPosition = bool("remember_pos", true)
    val backgroundVideo = bool("bg_video", true)
    val autoPip = bool("auto_pip", false)
    val gestures = bool("gestures", true)
    val doubleTapSec = int("double_tap_sec", 10)
    val defaultAspect = str("aspect", "fit")
    val skipSilence = bool("skip_silence", false)
    val keepScreenOn = bool("keep_screen_on", true)
    val autoRotate = bool("auto_rotate", true)
    val swipeBoost = bool("swipe_boost", true)     // volume swipe can go past 100 %

    // Library
    val minAudioSec = int("min_audio_sec", 5)
    val musicSort = str("music_sort", "date")
    val videoSort = str("video_sort", "date")
    val lastSeenSec = long("last_seen", 0L)
    val browserShowHidden = bool("browser_hidden", false)

    // Subtitles
    val subAuto = bool("sub_auto", true)
    val subScale = int("sub_scale", 100)            // 50..300 %
    val subFont = str("sub_font", "sans")
    val subFontPath = str("sub_font_path", "")
    val subBold = bool("sub_bold", false)
    val subItalic = bool("sub_italic", false)
    val subColor = int("sub_color", 0xFFFFFFFF.toInt())
    val subBgOn = bool("sub_bg_on", false)
    val subBgColor = int("sub_bg_color", 0xFF000000.toInt())
    val subBgOpacity = int("sub_bg_opacity", 55)    // 0..100
    val subEdge = int("sub_edge", 1)                // 0 none, 1 outline, 2 shadow, 3 both
    val subEdgeColor = int("sub_edge_color", 0xFF000000.toInt())
    val subEdgeWidth = int("sub_edge_width", 3)     // 1..8
    val subPosition = int("sub_position", 0)        // 0 bottom, 1 top
    val subMargin = int("sub_margin", 6)            // % of screen height
    val subAlign = int("sub_align", 1)              // 0 left, 1 center, 2 right
    val subCharset = str("sub_charset", "auto")
    val subNonLatin = str("sub_non_latin", "windows-1256")

    // Equalizer
    val eqEnabled = bool("eq_on", false)
    val eqPreset = int("eq_preset", -1)
    val eqBands = str("eq_bands", "")
}
