package com.rady.reelio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Album art and video frames, cached in memory. */
object Thumbnails {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val misses = HashSet<String>()
    private val gate = Semaphore(4)

    fun cached(e: MediaEntry, size: Int): Bitmap? = cache.get("${e.uri}#$size")

    suspend fun load(ctx: Context, e: MediaEntry, size: Int): Bitmap? {
        val key = "${e.uri}#$size"
        cache.get(key)?.let { return it }
        synchronized(misses) { if (key in misses) return null }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                val bmp = runCatching { produce(ctx, e, size) }.getOrNull()
                if (bmp != null) cache.put(key, bmp) else synchronized(misses) { misses += key }
                bmp
            }
        }
    }

    private fun produce(ctx: Context, e: MediaEntry, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29 && e.mediaStoreId >= 0) {
            val base = if (e.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val uri = android.content.ContentUris.withAppendedId(base, e.mediaStoreId)
            runCatching { return ctx.contentResolver.loadThumbnail(uri, Size(size, size), null) }
        }
        if (e.isVideo && e.isFile && Build.VERSION.SDK_INT >= 29) {
            runCatching { return ThumbnailUtils.createVideoThumbnail(File(e.uri), Size(size, size), null) }
        }
        val r = MediaMetadataRetriever()
        try {
            if (e.isFile) r.setDataSource(e.uri) else r.setDataSource(ctx, e.toUri())
            r.embeddedPicture?.let { return decodeScaled(it, size) }
            if (e.isVideo) {
                val frame = r.getFrameAtTime(2_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
                return scale(frame, size)
            }
        } finally {
            runCatching { r.release() }
        }
        return null
    }

    /** Cover picture bytes for the lock screen / notification. */
    fun artworkBytes(ctx: Context, e: MediaEntry): ByteArray? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            if (e.isFile) r.setDataSource(e.uri) else r.setDataSource(ctx, e.toUri())
            r.embeddedPicture ?: run {
                val bmp = if (e.isVideo) r.getFrameAtTime(5_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scale(it, 512) } else null
                bmp?.let { b -> ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() }
            }
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()

    /** Reads duration (and tags) for a file that isn't in the phone's media index. */
    fun probe(ctx: Context, e: MediaEntry): MediaEntry = runCatching {
        val r = MediaMetadataRetriever()
        try {
            if (e.isFile) r.setDataSource(e.uri) else r.setDataSource(ctx, e.toUri())
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            e.copy(
                durationMs = if (e.durationMs > 0) e.durationMs else dur,
                title = if (!e.isVideo && !title.isNullOrBlank()) title else e.title,
                artist = e.artist.ifBlank { artist ?: "" },
                album = e.album.ifBlank { album ?: "" },
            )
        } finally {
            runCatching { r.release() }
        }
    }.getOrDefault(e)

    private fun decodeScaled(bytes: ByteArray, size: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= size && opts.outHeight / (sample * 2) >= size) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun scale(b: Bitmap, size: Int): Bitmap {
        val ratio = size.toFloat() / maxOf(b.width, b.height)
        if (ratio >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * ratio).toInt().coerceAtLeast(1), (b.height * ratio).toInt().coerceAtLeast(1), true)
    }
}
