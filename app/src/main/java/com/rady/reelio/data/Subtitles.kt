package com.rady.reelio.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.util.Locale

data class SubCue(val startMs: Long, val endMs: Long, val text: String)

class SubtitleTrack(val file: File, val cues: List<SubCue>, val charset: String) {
    private var lastIdx = 0

    /** Text showing at [ms] (several cues can overlap). */
    fun textAt(ms: Long): String? {
        if (cues.isEmpty()) return null
        var i = lastIdx.coerceIn(0, cues.size - 1)
        if (cues[i].startMs > ms) {
            // jumped backwards: binary search
            var lo = 0; var hi = cues.size - 1
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (cues[mid].startMs <= ms) lo = mid + 1 else hi = mid
            }
            i = (lo - 1).coerceAtLeast(0)
        } else {
            while (i + 1 < cues.size && cues[i + 1].startMs <= ms) i++
        }
        lastIdx = i
        val active = ArrayList<String>(2)
        var j = i
        while (j >= 0 && j > i - 8) {
            val c = cues[j]
            if (ms >= c.startMs && ms < c.endMs) active.add(0, c.text)
            j--
        }
        return active.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}

object Subtitles {
    val charsets = listOf(
        "auto" to "Automatic",
        "UTF-8" to "Unicode (UTF-8)",
        "windows-1256" to "Arabic (Windows-1256)",
        "ISO-8859-6" to "Arabic (ISO-8859-6)",
        "windows-1252" to "Western (Windows-1252)",
        "windows-1250" to "Central European (Windows-1250)",
        "windows-1251" to "Cyrillic (Windows-1251)",
        "windows-1253" to "Greek (Windows-1253)",
        "windows-1254" to "Turkish (Windows-1254)",
        "windows-1255" to "Hebrew (Windows-1255)",
        "windows-1258" to "Vietnamese (Windows-1258)",
        "x-windows-874" to "Thai (Windows-874)",
        "GBK" to "Chinese Simplified (GBK)",
        "Big5" to "Chinese Traditional (Big5)",
        "Shift_JIS" to "Japanese (Shift_JIS)",
        "EUC-KR" to "Korean (EUC-KR)",
        "UTF-16" to "Unicode (UTF-16)",
    )

    /** Subtitle files next to the video with the same name (e.g. Movie.srt, Movie.en.srt, Subs/Movie.srt). */
    fun findFor(videoPath: String): List<File> {
        val video = File(videoPath)
        val dir = video.parentFile ?: return emptyList()
        val base = video.nameWithoutExtension.lowercase(Locale.ROOT)
        val candidates = ArrayList<File>()
        fun scan(d: File, loose: Boolean) {
            d.listFiles()?.forEach { f ->
                if (!f.isFile || !MediaFormats.isSubtitle(f.name)) return@forEach
                val n = f.nameWithoutExtension.lowercase(Locale.ROOT)
                if (n == base || n.startsWith("$base.") || n.startsWith("${base}_") || n.startsWith("$base ") || (loose && n.contains(base))) candidates += f
            }
        }
        scan(dir, false)
        listOf("Subs", "subs", "Subtitles", "subtitles", "Sub", "sub").forEach { name ->
            val d = File(dir, name)
            if (d.isDirectory) scan(d, true)
        }
        return candidates.sortedWith(compareBy({ it.nameWithoutExtension.length != video.nameWithoutExtension.length }, { it.name.length }))
    }

    /** Every subtitle file in the video's folder, for manual picking. */
    fun allInFolder(videoPath: String): List<File> =
        File(videoPath).parentFile?.listFiles()?.filter { it.isFile && MediaFormats.isSubtitle(it.name) }?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun load(file: File, charsetSetting: String, nonLatinFallback: String): SubtitleTrack {
        val bytes = file.readBytes()
        val (text, cs) = decode(bytes, charsetSetting, nonLatinFallback)
        val cues = when (MediaFormats.ext(file.name)) {
            "vtt" -> parseSrtLike(text)
            "ass", "ssa" -> parseAss(text)
            "sub" -> if (text.trimStart().startsWith("{")) parseMicroDvd(text) else parseSrtLike(text)
            "smi" -> parseSami(text)
            else -> parseSrtLike(text)
        }.sortedBy { it.startMs }
        return SubtitleTrack(file, cues, cs)
    }

    fun decode(bytes: ByteArray, setting: String, nonLatin: String): Pair<String, String> {
        // Byte-order marks win
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to "UTF-8"
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to "UTF-16"
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) to "UTF-16"
        if (setting != "auto") {
            val cs = runCatching { Charset.forName(setting) }.getOrDefault(Charsets.UTF_8)
            return String(bytes, cs) to setting
        }
        // Valid UTF-8?
        val dec = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        runCatching { return dec.decode(ByteBuffer.wrap(bytes)).toString() to "UTF-8" }
        // Lots of high bytes means a non-Latin script (Arabic, Cyrillic...); a few means Western accents.
        var letters = 0; var high = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v >= 0x80) { high++; letters++ } else if ((v in 0x41..0x5A) || (v in 0x61..0x7A)) letters++
        }
        val name = if (letters > 0 && high.toDouble() / letters > 0.3) nonLatin else "windows-1252"
        val cs = runCatching { Charset.forName(name) }.getOrDefault(Charsets.ISO_8859_1)
        return String(bytes, cs) to name
    }

    private val timeRx = Regex("""(?:(\d+):)?(\d{1,2}):(\d{1,2})[,.](\d{1,3})\s*-->\s*(?:(\d+):)?(\d{1,2}):(\d{1,2})[,.](\d{1,3})""")

    private fun ms(h: String?, m: String, s: String, f: String): Long {
        val frac = f.padEnd(3, '0').take(3).toLong()
        return ((h?.toLongOrNull() ?: 0) * 3600 + m.toLong() * 60 + s.toLong()) * 1000 + frac
    }

    /** SRT and WebVTT. */
    fun parseSrtLike(raw: String): List<SubCue> {
        val out = ArrayList<SubCue>()
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            val m = timeRx.find(lines[i])
            if (m == null) { i++; continue }
            val g = m.groupValues
            val start = ms(g[1].ifEmpty { null }, g[2], g[3], g[4])
            val end = ms(g[5].ifEmpty { null }, g[6], g[7], g[8])
            i++
            val sb = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (timeRx.containsMatchIn(lines[i])) break
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(lines[i])
                i++
            }
            // drop a trailing cue number that belongs to the next block
            val text = cleanTags(sb.toString())
            if (text.isNotBlank()) out += SubCue(start, end, text)
        }
        return out
    }

    private val tagRx = Regex("""<[^>]+>|\{\\[^}]*\}""")
    private fun cleanTags(s: String) = s.replace(tagRx, "").replace("&nbsp;", " ").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">").trim()

    /** ASS / SSA: reads the [Events] section. */
    fun parseAss(raw: String): List<SubCue> {
        val out = ArrayList<SubCue>()
        var fmt: List<String> = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        val assTime = Regex("""(\d+):(\d{1,2}):(\d{1,2})[.:](\d{1,3})""")
        fun t(s: String): Long {
            val m = assTime.find(s.trim()) ?: return -1
            val (h, mm, ss, cs) = m.destructured
            val frac = if (cs.length == 2) cs.toLong() * 10 else cs.padEnd(3, '0').take(3).toLong()
            return (h.toLong() * 3600 + mm.toLong() * 60 + ss.toLong()) * 1000 + frac
        }
        raw.lineSequence().forEach { line0 ->
            val line = line0.trim()
            when {
                line.startsWith("Format:", true) -> fmt = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                line.startsWith("Dialogue:", true) -> {
                    val parts = line.substringAfter(':').split(',', limit = fmt.size)
                    if (parts.size < fmt.size) return@forEach
                    val start = t(parts[fmt.indexOf("start").coerceAtLeast(0)])
                    val end = t(parts[fmt.indexOf("end").coerceAtLeast(0)])
                    val ti = fmt.indexOf("text").let { if (it < 0) parts.size - 1 else it }
                    val text = parts[ti].replace(Regex("""\{[^}]*\}"""), "")
                        .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ").trim()
                    if (start >= 0 && end > start && text.isNotBlank()) out += SubCue(start, end, text)
                }
            }
        }
        return out
    }

    /** MicroDVD {start}{end}Text with frame numbers. */
    fun parseMicroDvd(raw: String): List<SubCue> {
        val rx = Regex("""^\{(\d+)\}\{(\d*)\}(.*)$""")
        var fps = 23.976
        val out = ArrayList<SubCue>()
        raw.lineSequence().forEach { line ->
            val m = rx.find(line.trim()) ?: return@forEach
            val (a, b, text) = m.destructured
            if (a == "1" && (b == "1" || b.isEmpty()) && text.trim().toDoubleOrNull() != null) {
                fps = text.trim().toDouble(); return@forEach
            }
            val s = (a.toLong() * 1000 / fps).toLong()
            val e = (b.toLongOrNull()?.let { it * 1000 / fps } ?: (s + 3000.0)).toLong()
            val clean = text.replace(Regex("""\{[^}]*\}"""), "").replace('|', '\n').trim()
            if (clean.isNotBlank()) out += SubCue(s, e, clean)
        }
        return out
    }

    /** SAMI (.smi) — basic support. */
    fun parseSami(raw: String): List<SubCue> {
        val rx = Regex("""<SYNC\s+Start\s*=\s*"?(\d+)"?[^>]*>(.*?)(?=<SYNC|</BODY|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val syncs = rx.findAll(raw).map { it.groupValues[1].toLong() to it.groupValues[2] }.toList()
        val out = ArrayList<SubCue>()
        syncs.forEachIndexed { i, (start, body) ->
            val text = body.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n").let(::cleanTags)
            val end = syncs.getOrNull(i + 1)?.first ?: (start + 4000)
            if (text.isNotBlank() && text != "&nbsp;") out += SubCue(start, end, text)
        }
        return out
    }
}
