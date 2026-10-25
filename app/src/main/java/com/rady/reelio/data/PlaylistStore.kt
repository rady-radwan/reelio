package com.rady.reelio.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Playlist(
    val id: String,
    val name: String,
    val items: List<MediaEntry>,
    val createdAt: Long,
) {
    val totalMs: Long get() = items.sumOf { it.durationMs }
    val isFavorites: Boolean get() = id == PlaylistStore.FAVORITES
}

/** Playlists are kept in a small JSON file in the app's private storage. */
class PlaylistStore(ctx: Context, private val scope: CoroutineScope) {
    companion object { const val FAVORITES = "favorites" }

    private val file = File(ctx.filesDir, "playlists.json")
    private val _lists = MutableStateFlow(load())
    val lists: StateFlow<List<Playlist>> = _lists

    private fun load(): List<Playlist> {
        val out = ArrayList<Playlist>()
        runCatching {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val itemsArr = o.getJSONArray("items")
                    val items = (0 until itemsArr.length()).map { MediaEntry.fromJson(itemsArr.getJSONObject(it)) }
                    out += Playlist(o.getString("id"), o.getString("name"), items, o.optLong("created"))
                }
            }
        }
        if (out.none { it.id == FAVORITES }) out.add(0, Playlist(FAVORITES, "Favorites", emptyList(), 0))
        return out
    }

    private fun save(list: List<Playlist>) {
        _lists.value = list
        val snapshot = list
        scope.launch(Dispatchers.IO) {
            val arr = JSONArray()
            snapshot.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("created", p.createdAt)
                    put("items", JSONArray().apply { p.items.forEach { put(it.toJson()) } })
                })
            }
            val tmp = File(file.parentFile, "playlists.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }

    fun get(id: String): Playlist? = _lists.value.firstOrNull { it.id == id }

    fun create(name: String, items: List<MediaEntry> = emptyList()): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "New playlist" }, items.distinctBy { it.uri }, System.currentTimeMillis())
        save(_lists.value + p)
        return p
    }

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) {
        if (id == FAVORITES) return
        save(_lists.value.filterNot { it.id == id })
    }

    /** Adds files that aren't already in the list. Returns how many were added. */
    fun add(id: String, entries: List<MediaEntry>): Int {
        var added = 0
        update(id) { p ->
            val have = p.items.mapTo(HashSet()) { it.uri }
            val fresh = entries.filter { have.add(it.uri) }
            added = fresh.size
            p.copy(items = p.items + fresh)
        }
        return added
    }

    fun removeAt(id: String, index: Int) = update(id) { p ->
        p.copy(items = p.items.filterIndexed { i, _ -> i != index })
    }

    fun move(id: String, from: Int, to: Int) = update(id) { p ->
        if (from !in p.items.indices || to !in p.items.indices) p
        else p.copy(items = p.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun isFavorite(uri: String): Boolean = get(FAVORITES)?.items?.any { it.uri == uri } == true

    fun toggleFavorite(e: MediaEntry) {
        if (isFavorite(e.uri)) update(FAVORITES) { p -> p.copy(items = p.items.filterNot { it.uri == e.uri }) }
        else add(FAVORITES, listOf(e))
    }

    private fun update(id: String, f: (Playlist) -> Playlist) {
        save(_lists.value.map { if (it.id == id) f(it) else it })
    }
}

/** Remembers where you stopped in each video (and long audio, like audiobooks). */
class PositionStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("reelio_positions", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun get(uri: String): Long = sp.getLong(uri, 0L)

    fun put(uri: String, ms: Long) {
        sp.edit().putLong(uri, ms).putLong("t:$uri", System.currentTimeMillis()).apply()
        _version.value++
    }

    fun clear(uri: String) {
        if (!sp.contains(uri)) return
        sp.edit().remove(uri).remove("t:$uri").apply()
        _version.value++
    }

    /** Uris with a saved position, most recently watched first. */
    fun recent(): List<Pair<String, Long>> =
        sp.all.keys.filter { !it.startsWith("t:") }
            .map { it to sp.getLong("t:$it", 0L) }
            .sortedByDescending { it.second }
            .map { it.first to sp.getLong(it.first, 0L) }
}

/**
 * Smart-shuffle memory: for each playlist, which items already had their turn in the
 * current round. Kept on disk so a song isn't repeated even after closing the app.
 */
class ShuffleMemory(ctx: Context) {
    private val sp = ctx.getSharedPreferences("reelio_shuffle", Context.MODE_PRIVATE)

    fun played(source: String?): Set<String> {
        if (source == null) return emptySet()
        return runCatching {
            val arr = JSONArray(sp.getString(source, "[]"))
            (0 until arr.length()).mapTo(HashSet()) { arr.getString(it) }
        }.getOrDefault(emptySet())
    }

    fun markPlayed(source: String?, uri: String) {
        if (source == null) return
        val set = played(source).toMutableSet()
        if (set.add(uri)) sp.edit().putString(source, JSONArray(set.toList()).toString()).apply()
    }

    fun reset(source: String?) {
        if (source == null) return
        sp.edit().remove(source).apply()
    }
}
