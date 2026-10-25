package com.rady.reelio.player

import com.rady.reelio.data.MediaEntry
import java.util.Collections
import kotlin.random.Random

enum class RepeatMode { OFF, ALL, ONE }

/**
 * What plays and in which order.
 *
 * [items] is the list as you built it ("as added" order). [order] is the actual play order for
 * the current round: in shuffle mode it's history + current + a smart-shuffled list of the items
 * that haven't had their turn yet, so nothing repeats until everything has played once.
 */
class PlayQueue {
    var items: List<MediaEntry> = emptyList(); private set
    private var order: MutableList<Int> = mutableListOf()
    var pos: Int = -1; private set
    var shuffle: Boolean = false; private set
    var repeat: RepeatMode = RepeatMode.OFF
    var sourceId: String? = null; private set
    var version: Int = 0; private set

    /** Called when a shuffle round finishes and a new one starts. */
    var onNewRound: (() -> Unit)? = null

    private val rnd = Random(System.nanoTime())

    val current: MediaEntry? get() = order.getOrNull(pos)?.let { items.getOrNull(it) }
    val size: Int get() = items.size
    val orderSnapshot: List<Int> get() = order.toList()

    fun set(newItems: List<MediaEntry>, startIndex: Int, shuffleOn: Boolean, source: String?, alreadyPlayed: Set<String>) {
        items = newItems.toList()
        sourceId = source
        shuffle = shuffleOn
        version++
        if (items.isEmpty()) { order = mutableListOf(); pos = -1; return }
        val start = startIndex.takeIf { it in items.indices }
        if (!shuffle) {
            order = items.indices.toMutableList()
            pos = start ?: 0
        } else buildShuffle(start, alreadyPlayed)
    }

    fun restore(newItems: List<MediaEntry>, newOrder: List<Int>, newPos: Int, shuffleOn: Boolean, rep: RepeatMode, source: String?) {
        items = newItems
        order = newOrder.filter { it in newItems.indices }.toMutableList()
        if (order.size != items.size) order = items.indices.toMutableList()
        pos = newPos.coerceIn(-1, order.size - 1)
        if (pos < 0 && order.isNotEmpty()) pos = 0
        shuffle = shuffleOn
        repeat = rep
        sourceId = source
        version++
    }

    fun clear() {
        items = emptyList(); order = mutableListOf(); pos = -1; sourceId = null; version++
    }

    private fun buildShuffle(start: Int?, alreadyPlayed: Set<String>) {
        val played = items.indices.filter { it != start && items[it].uri in alreadyPlayed }
        val unplayed = items.indices.filter { it != start && items[it].uri !in alreadyPlayed }
        when {
            start != null -> {
                order = (played + start + smartShuffle(unplayed)).toMutableList()
                pos = played.size
            }
            unplayed.isEmpty() -> { // everyone had a turn: fresh round
                onNewRound?.invoke()
                order = smartShuffle(items.indices.toList()).toMutableList()
                pos = 0
            }
            else -> {
                order = (played + smartShuffle(unplayed)).toMutableList()
                pos = played.size
            }
        }
    }

    /** Random order that also tries not to put the same artist twice in a row. */
    private fun smartShuffle(list: List<Int>, avoidFirst: Int? = null): List<Int> {
        val l = list.shuffled(rnd).toMutableList()
        for (i in 1 until l.size) {
            val prev = artistKey(l[i - 1])
            if (prev.isEmpty() || artistKey(l[i]) != prev) continue
            val j = (i + 1 until l.size).firstOrNull { artistKey(l[it]) != prev } ?: continue
            Collections.swap(l, i, j)
        }
        if (avoidFirst != null && l.size > 1 && l[0] == avoidFirst) Collections.swap(l, 0, 1 + rnd.nextInt(l.size - 1))
        return l
    }

    private fun artistKey(i: Int) = items.getOrNull(i)?.artist?.trim()?.lowercase().orEmpty()

    fun hasNext(): Boolean = items.isNotEmpty() && (pos < order.size - 1 || repeat == RepeatMode.ALL)
    fun hasPrevious(): Boolean = items.isNotEmpty() && (pos > 0 || repeat == RepeatMode.ALL)

    fun peekNext(): MediaEntry? = when {
        pos < order.size - 1 -> items[order[pos + 1]]
        repeat == RepeatMode.ALL && items.isNotEmpty() -> if (shuffle) null else items[order[0]]
        else -> null
    }

    fun peekPrevious(): MediaEntry? = when {
        pos > 0 -> items[order[pos - 1]]
        repeat == RepeatMode.ALL && items.isNotEmpty() -> items[order.last()]
        else -> null
    }

    /**
     * Advance. [manual] is true when you pressed Next: with "repeat one" that still moves on,
     * and at the end of the list it wraps around like "repeat all".
     */
    fun moveNext(manual: Boolean): Boolean {
        if (items.isEmpty()) return false
        if (pos < order.size - 1) { pos++; version++; return true }
        if (repeat == RepeatMode.ALL || (manual && repeat == RepeatMode.ONE)) { newRound(); return true }
        return false
    }

    fun movePrevious(): Boolean {
        if (pos > 0) { pos--; version++; return true }
        if (repeat == RepeatMode.ALL && order.isNotEmpty()) { pos = order.size - 1; version++; return true }
        return false
    }

    private fun newRound() {
        val last = order.getOrNull(pos)
        onNewRound?.invoke()
        order = if (shuffle) smartShuffle(items.indices.toList(), avoidFirst = last).toMutableList()
        else items.indices.toMutableList()
        pos = 0
        version++
    }

    /** Play items[index] now. In shuffle it's moved to play next, so the rest keep their turn. */
    fun jumpToItem(index: Int) {
        if (index !in items.indices) return
        val p = order.indexOf(index)
        if (p < 0) return
        if (!shuffle) { pos = p; version++; return }
        when {
            p > pos -> { order.removeAt(p); order.add(pos + 1, index); pos++ }
            p < pos -> { order.removeAt(p); pos--; order.add(pos + 1, index); pos++ }
        }
        version++
    }

    fun jumpToOrderPos(p: Int) {
        val idx = order.getOrNull(p) ?: return
        jumpToItem(idx)
    }

    fun setShuffle(on: Boolean, alreadyPlayed: Set<String>) {
        if (on == shuffle) return
        shuffle = on
        val cur = order.getOrNull(pos)
        if (items.isEmpty()) { version++; return }
        if (on) buildShuffle(cur, alreadyPlayed)
        else { order = items.indices.toMutableList(); pos = cur ?: 0 }
        version++
    }

    /** Add to the queue. Returns true if the queue was empty (caller should start playing). */
    fun add(entries: List<MediaEntry>, playNext: Boolean): Boolean {
        if (entries.isEmpty()) return false
        val wasEmpty = items.isEmpty()
        val start = items.size
        items = items + entries
        val fresh = (start until items.size).toList()
        when {
            wasEmpty -> { order = fresh.toMutableList(); pos = 0 }
            playNext -> order.addAll(pos + 1, fresh)
            shuffle -> fresh.forEach { order.add(pos + 1 + rnd.nextInt(order.size - pos), it) }
            else -> order.addAll(fresh)
        }
        version++
        return wasEmpty
    }

    /** Remove the entry at play-order position [p]. Returns true if that was the current one. */
    fun removeAtOrderPos(p: Int): Boolean {
        val idx = order.getOrNull(p) ?: return false
        items = items.filterIndexed { i, _ -> i != idx }
        order.removeAt(p)
        for (i in order.indices) if (order[i] > idx) order[i] = order[i] - 1
        version++
        return when {
            p < pos -> { pos--; false }
            p == pos -> { if (pos >= order.size) pos = order.size - 1; true }
            else -> false
        }
    }

    /** Reorder upcoming entries (both positions must be after the current one). */
    fun moveUpcoming(from: Int, to: Int) {
        if (from <= pos || to <= pos || from !in order.indices || to !in order.indices) return
        order.add(to, order.removeAt(from))
        version++
    }

    /** Upcoming entries with their play-order position. */
    fun upcoming(): List<Pair<Int, MediaEntry>> =
        if (pos < 0) emptyList() else (pos + 1 until order.size).map { it to items[order[it]] }

    fun history(): List<Pair<Int, MediaEntry>> =
        if (pos <= 0) emptyList() else (0 until pos).map { it to items[order[it]] }

    fun replaceEntry(old: MediaEntry, new: MediaEntry) {
        val i = items.indexOfFirst { it.uri == old.uri }
        if (i >= 0) items = items.toMutableList().also { it[i] = new }
    }
}
