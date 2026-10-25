package com.rady.reelio.data

import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** One playable file. [uri] is a plain file path when we know it, otherwise a content:// uri. */
data class MediaEntry(
    val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val isVideo: Boolean,
    val dateAddedSec: Long = 0,
    val sizeBytes: Long = 0,
    val albumId: Long = -1,
    val mediaStoreId: Long = -1,
) {
    val isFile: Boolean get() = uri.startsWith("/")

    fun toUri(): Uri = if (isFile) Uri.fromFile(File(uri)) else Uri.parse(uri)

    val fileName: String get() = if (isFile) uri.substringAfterLast('/') else title

    val folderPath: String get() = if (isFile) uri.substringBeforeLast('/', "") else ""

    val folderName: String get() = folderPath.substringAfterLast('/').ifEmpty { "Other" }

    val extension: String
        get() = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    val subtitleLine: String
        get() = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" • ")

    fun toJson(): JSONObject = JSONObject().apply {
        put("u", uri); put("t", title); put("a", artist); put("al", album)
        put("d", durationMs); put("v", isVideo); put("da", dateAddedSec)
        put("s", sizeBytes); put("ai", albumId); put("id", mediaStoreId)
    }

    companion object {
        fun fromJson(o: JSONObject) = MediaEntry(
            uri = o.getString("u"),
            title = o.optString("t"),
            artist = o.optString("a"),
            album = o.optString("al"),
            durationMs = o.optLong("d"),
            isVideo = o.optBoolean("v"),
            dateAddedSec = o.optLong("da"),
            sizeBytes = o.optLong("s"),
            albumId = o.optLong("ai", -1),
            mediaStoreId = o.optLong("id", -1),
        )

        fun fromFile(f: File): MediaEntry = MediaEntry(
            uri = f.absolutePath,
            title = f.nameWithoutExtension,
            isVideo = MediaFormats.isVideo(f.name),
            dateAddedSec = f.lastModified() / 1000,
            sizeBytes = f.length(),
        )
    }
}

object MediaFormats {
    val audio = setOf(
        "mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "wma", "amr", "awb", "3ga",
        "ape", "wv", "aiff", "aif", "aifc", "mka", "ac3", "eac3", "ec3", "dts", "m4b", "m4r", "mpc",
        "tta", "ra", "weba", "caf", "au", "snd", "dsf", "dff", "mp2", "mpa", "xm", "mod", "s3m", "it",
        "mid", "midi", "spx", "alac",
    )
    val video = setOf(
        "mp4", "m4v", "mov", "mkv", "webm", "avi", "wmv", "asf", "flv", "f4v", "3gp", "3g2", "ts",
        "m2ts", "mts", "mpg", "mpeg", "vob", "ogv", "divx", "xvid", "rm", "rmvb", "mxf", "dv", "qt",
        "m2v", "m1v", "mpe", "tod", "vro", "evo", "nsv", "y4m", "amv", "hevc", "h264", "264", "265",
    )
    val subtitles = setOf("srt", "vtt", "ass", "ssa", "sub", "smi")

    /** Formats the main engine can't open — these go straight to VLC. */
    private val vlcOnly = setOf(
        "wma", "wmv", "asf", "rm", "rmvb", "ra", "ape", "wv", "mpc", "tta", "dsf", "dff", "divx",
        "xvid", "vob", "mxf", "dv", "aiff", "aif", "aifc", "caf", "au", "snd", "m2v", "m1v", "mpe",
        "xm", "mod", "s3m", "it", "mid", "midi", "tod", "vro", "evo", "nsv", "y4m", "amv", "spx",
        "hevc", "h264", "264", "265", "mp2", "mpa", "dts",
    )

    fun ext(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    fun isAudio(name: String) = ext(name) in audio
    fun isVideo(name: String) = ext(name) in video
    fun isMedia(name: String) = ext(name).let { it in audio || it in video }
    fun isSubtitle(name: String) = ext(name) in subtitles
    fun prefersVlc(extension: String) = extension in vlcOnly

    val allListed: String
        get() = (audio + video).sorted().joinToString(", ")
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.US, "%d KB", bytes / 1024)
}

fun formatAgo(sec: Long): String {
    if (sec <= 0) return ""
    val diff = System.currentTimeMillis() / 1000 - sec
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60} min ago"
        diff < 86400 -> "${diff / 3600} h ago"
        diff < 86400 * 7 -> "${diff / 86400} d ago"
        diff < 86400 * 30 -> "${diff / (86400 * 7)} wk ago"
        else -> java.text.SimpleDateFormat("MMM d, yyyy", Locale.US).format(java.util.Date(sec * 1000))
    }
}
