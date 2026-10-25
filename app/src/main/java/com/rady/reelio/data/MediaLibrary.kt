package com.rady.reelio.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Reads every song and video the phone knows about, newest first, and watches for new ones. */
class MediaLibrary(
    private val ctx: Context,
    private val settings: Settings,
    private val scope: CoroutineScope,
) {
    private val _audio = MutableStateFlow<List<MediaEntry>>(emptyList())
    private val _video = MutableStateFlow<List<MediaEntry>>(emptyList())
    private val _loaded = MutableStateFlow(false)
    private val _scanning = MutableStateFlow(false)
    val audio: StateFlow<List<MediaEntry>> = _audio
    val video: StateFlow<List<MediaEntry>> = _video
    val loaded: StateFlow<Boolean> = _loaded
    val scanning: StateFlow<Boolean> = _scanning

    /** Items added after this moment get a "NEW" badge for the whole visit. */
    var newSinceSec: Long = settings.lastSeenSec.value
        private set

    private var job: Job? = null
    private var observing = false
    private var refreshPending: Job? = null

    fun hasMediaPermission(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= 33)
            listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return perms.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else hasMediaPermission()

    fun beginVisit() {
        newSinceSec = settings.lastSeenSec.value
    }

    fun endVisit() {
        settings.lastSeenSec.value = System.currentTimeMillis() / 1000
    }

    fun refresh() {
        if (!hasMediaPermission()) return
        job?.cancel()
        job = scope.launch {
            _scanning.value = true
            val (a, v) = withContext(Dispatchers.IO) { queryAudio() to queryVideo() }
            _audio.value = a
            _video.value = v
            _loaded.value = true
            _scanning.value = false
        }
        startObserving()
    }

    /** Ask Android to index files it may have missed (e.g. copied over USB). */
    fun scanFolder(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && MediaFormats.isMedia(it.name) } ?: return
        if (files.isEmpty()) return
        android.media.MediaScannerConnection.scanFile(ctx, files.map { it.absolutePath }.toTypedArray(), null, null)
    }

    private fun startObserving() {
        if (observing) return
        observing = true
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshPending?.cancel()
                refreshPending = scope.launch { delay(1500); refresh() }
            }
        }
        val cr = ctx.contentResolver
        cr.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, obs)
        cr.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, obs)
    }

    private fun queryAudio(): List<MediaEntry> {
        val list = ArrayList<MediaEntry>()
        val minMs = settings.minAudioSec.value * 1000L
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        runCatching {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(proj[0]); val iTitle = c.getColumnIndexOrThrow(proj[1])
                val iArtist = c.getColumnIndexOrThrow(proj[2]); val iAlbum = c.getColumnIndexOrThrow(proj[3])
                val iAlbumId = c.getColumnIndexOrThrow(proj[4]); val iDur = c.getColumnIndexOrThrow(proj[5])
                val iDate = c.getColumnIndexOrThrow(proj[6]); val iSize = c.getColumnIndexOrThrow(proj[7])
                val iData = c.getColumnIndexOrThrow(proj[8]); val iName = c.getColumnIndexOrThrow(proj[9])
                while (c.moveToNext()) {
                    val dur = c.getLong(iDur)
                    if (dur in 1 until minMs) continue
                    val id = c.getLong(iId)
                    val path = c.getString(iData)
                    val name = c.getString(iName) ?: path?.substringAfterLast('/') ?: ""
                    val artist = c.getString(iArtist)?.takeUnless { it == MediaStore.UNKNOWN_STRING } ?: ""
                    list += MediaEntry(
                        uri = if (!path.isNullOrBlank()) path else ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                        title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.'),
                        artist = artist,
                        album = c.getString(iAlbum) ?: "",
                        durationMs = dur,
                        isVideo = false,
                        dateAddedSec = c.getLong(iDate),
                        sizeBytes = c.getLong(iSize),
                        albumId = c.getLong(iAlbumId),
                        mediaStoreId = id,
                    )
                }
            }
        }
        return list
    }

    private fun queryVideo(): List<MediaEntry> {
        val list = ArrayList<MediaEntry>()
        val proj = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
        )
        runCatching {
            ctx.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(proj[0]); val iTitle = c.getColumnIndexOrThrow(proj[1])
                val iDur = c.getColumnIndexOrThrow(proj[2]); val iDate = c.getColumnIndexOrThrow(proj[3])
                val iSize = c.getColumnIndexOrThrow(proj[4]); val iData = c.getColumnIndexOrThrow(proj[5])
                val iName = c.getColumnIndexOrThrow(proj[6])
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val path = c.getString(iData)
                    val name = c.getString(iName) ?: path?.substringAfterLast('/') ?: ""
                    list += MediaEntry(
                        uri = if (!path.isNullOrBlank()) path else ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString(),
                        title = name.substringBeforeLast('.').ifBlank { c.getString(iTitle) ?: "Video" },
                        durationMs = c.getLong(iDur),
                        isVideo = true,
                        dateAddedSec = c.getLong(iDate),
                        sizeBytes = c.getLong(iSize),
                        mediaStoreId = id,
                    )
                }
            }
        }
        return list
    }

    fun contentUriOf(e: MediaEntry): Uri? =
        if (e.mediaStoreId < 0) null
        else ContentUris.withAppendedId(
            if (e.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            e.mediaStoreId,
        )

    /** Look a file path up in the library so we get proper tags; fall back to a bare entry. */
    fun entryForPath(path: String): MediaEntry =
        _audio.value.firstOrNull { it.uri == path } ?: _video.value.firstOrNull { it.uri == path }
        ?: MediaEntry.fromFile(File(path))

    fun entriesForFiles(files: List<File>): List<MediaEntry> {
        val byPath = HashMap<String, MediaEntry>()
        _audio.value.forEach { byPath[it.uri] = it }
        _video.value.forEach { byPath[it.uri] = it }
        return files.map { byPath[it.absolutePath] ?: MediaEntry.fromFile(it) }
    }
}
