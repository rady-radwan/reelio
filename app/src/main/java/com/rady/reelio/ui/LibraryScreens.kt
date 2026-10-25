package com.rady.reelio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rady.reelio.AppGraph
import com.rady.reelio.data.MediaEntry
import com.rady.reelio.data.formatDuration
import com.rady.reelio.player.PlayerHub

// ---------------- reusable list with play-all, shuffle, sort and multi-select ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaList(
    entries: List<MediaEntry>,
    sourceId: String?,
    modifier: Modifier = Modifier,
    showAdded: Boolean = false,
    newSince: Long = Long.MAX_VALUE,
    sortKey: String? = null,
    onSortChange: ((String) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    extraMenu: (MediaEntry, Int) -> List<Pair<String, () -> Unit>> = { _, _ -> emptyList() },
    emptyContent: (@Composable () -> Unit)? = null,
) {
    val actions = LocalUiActions.current
    val ctx = LocalContext.current
    var selected by remember(sourceId) { mutableStateOf(setOf<String>()) }
    val selecting = selected.isNotEmpty()
    Column(modifier) {
        if (selecting) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, "Cancel") }
                    Text("${selected.size} selected", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    val chosen = entries.filter { it.uri in selected }
                    IconButton(onClick = { selected = entries.map { it.uri }.toSet() }) { Icon(Icons.Rounded.SelectAll, "Select all") }
                    IconButton(onClick = { actions.play(chosen, 0, false, null); selected = emptySet() }) { Icon(Icons.Rounded.PlayArrow, "Play") }
                    IconButton(onClick = { PlayerHub.enqueue(chosen, playNext = true); ctx.toast("Plays next"); selected = emptySet() }) { Icon(Icons.Rounded.SkipNext, "Play next") }
                    IconButton(onClick = { PlayerHub.enqueue(chosen, false); ctx.toast("Added to queue"); selected = emptySet() }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") }
                    IconButton(onClick = { actions.addToPlaylist(chosen); selected = emptySet() }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
            header?.let { h -> item { h() } }
            if (entries.isEmpty()) {
                item { emptyContent?.invoke() }
            } else item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { actions.play(entries, 0, false, sourceId) }) {
                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play all")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { actions.play(entries, -1, true, sourceId) }) {
                        Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                    }
                    Spacer(Modifier.weight(1f))
                    if (sortKey != null && onSortChange != null) SortButton(sortKey, onSortChange)
                }
                Text(
                    "${entries.size} items • ${formatDuration(entries.sumOf { it.durationMs })}",
                    style = subtleText, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(entries.size, key = { i -> entries[i].uri + "#" + i }) { i ->
                val e = entries[i]
                MediaRow(
                    e,
                    onClick = {
                        if (selecting) selected = if (e.uri in selected) selected - e.uri else selected + e.uri
                        else actions.play(entries, i, false, sourceId)
                    },
                    onLongClick = { selected = selected + e.uri },
                    selected = if (selecting) e.uri in selected else null,
                    isNew = e.dateAddedSec > newSince,
                    showAdded = showAdded,
                    extraMenu = extraMenu(e, i),
                )
            }
        }
    }
}

val sortOptions = listOf("date" to "Date added", "title" to "Title", "artist" to "Artist", "duration" to "Length", "size" to "Size", "folder" to "Folder")

fun List<MediaEntry>.sortedByKey(key: String): List<MediaEntry> = when (key) {
    "title" -> sortedBy { it.title.lowercase() }
    "artist" -> sortedWith(compareBy({ it.artist.ifBlank { "￿" }.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() }))
    "duration" -> sortedByDescending { it.durationMs }
    "size" -> sortedByDescending { it.sizeBytes }
    "folder" -> sortedWith(compareBy({ it.folderPath.lowercase() }, { it.title.lowercase() }))
    else -> sortedByDescending { it.dateAddedSec }
}

@Composable
fun SortButton(key: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(sortOptions.firstOrNull { it.first == key }?.second ?: "Sort")
        }
        DropdownMenu(open, { open = false }) {
            sortOptions.forEach { (k, label) ->
                DropdownMenuItem(text = { Text(label, fontWeight = if (k == key) FontWeight.Bold else null) }, onClick = { onChange(k); open = false })
            }
        }
    }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
        options.forEachIndexed { i, s ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(s) }, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

// ---------------- Home: what's new and what to continue ----------------

@Composable
fun HomeTab(open: (Route) -> Unit) {
    val audio by AppGraph.library.audio.collectAsState()
    val video by AppGraph.library.video.collectAsState()
    val posVersion by AppGraph.positions.version.collectAsState()
    val since = AppGraph.library.newSinceSec
    var filter by rememberSaveable { mutableStateOf(0) }
    val actions = LocalUiActions.current
    val ctx = LocalContext.current

    val all = remember(audio, video) { (audio + video).sortedByDescending { it.dateAddedSec } }
    val newCount = remember(all, since) { all.count { it.dateAddedSec > since } }
    val shown = remember(all, filter) {
        when (filter) { 1 -> all.filter { !it.isVideo }; 2 -> all.filter { it.isVideo }; else -> all }.take(300)
    }
    val continueWatching = remember(video, posVersion) {
        val byUri = video.associateBy { it.uri }
        AppGraph.positions.recent().mapNotNull { (uri, _) -> byUri[uri] }.take(12)
    }

    MediaList(
        entries = shown,
        sourceId = "recent",
        showAdded = true,
        newSince = since,
        header = {
            Column {
                if (newCount > 0 && since > 0) {
                    Surface(
                        Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NewReleases, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("$newCount new since your last visit", fontWeight = FontWeight.SemiBold)
                                Text("Marked NEW below", style = subtleText)
                            }
                            TextButton(onClick = { actions.play(all.filter { it.dateAddedSec > since }, 0, false, "new") }) { Text("Play") }
                        }
                    }
                }
                if (continueWatching.isNotEmpty()) {
                    SectionHeader("Continue watching")
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                        items(continueWatching, key = { it.uri }) { e ->
                            val pos = AppGraph.positions.get(e.uri)
                            Column(
                                Modifier.width(176.dp).padding(horizontal = 4.dp).clip(RoundedCornerShape(12.dp))
                                    .clickable { PlayerHub.playSingle(e, video.filter { it.folderPath == e.folderPath }.sortedBy { it.title.lowercase() }); openVideoPlayer(ctx) },
                            ) {
                                Box {
                                    MediaThumb(e, Modifier.fillMaxWidth().aspectRatio(16f / 9f), sizePx = 360, corner = 12.dp)
                                    if (e.durationMs > 0) LinearProgressIndicator(
                                        progress = { (pos.toFloat() / e.durationMs).coerceIn(0f, 1f) },
                                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                                        trackColor = Color.Black.copy(alpha = 0.4f), drawStopIndicator = {},
                                    )
                                }
                                Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                Text("${formatDuration(pos)} of ${formatDuration(e.durationMs)}", style = subtleText)
                            }
                        }
                    }
                }
                SectionHeader("Recently added")
                ChipRow(listOf("All", "Music", "Videos"), filter) { filter = it }
            }
        },
        emptyContent = { EmptyState(Icons.Rounded.History, "Nothing here yet", "Songs and videos you add to your phone show up here, newest first.") },
    )
}

// ---------------- Music ----------------

@Composable
fun MusicTab(open: (Route) -> Unit) {
    val audio by AppGraph.library.audio.collectAsState()
    var view by rememberSaveable { mutableStateOf(0) }
    val sort by AppGraph.settings.musicSort.flow.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ChipRow(listOf("Songs", "Albums", "Artists", "Folders"), view) { view = it }
        when (view) {
            0 -> {
                val sorted = remember(audio, sort) { audio.sortedByKey(sort) }
                MediaList(
                    sorted, "all-songs", sortKey = sort, onSortChange = { AppGraph.settings.musicSort.value = it },
                    newSince = AppGraph.library.newSinceSec,
                    emptyContent = { EmptyState(Icons.Rounded.LibraryMusic, "No music found", "Copy songs to your phone, or use the Browse tab to find them in any folder.") },
                )
            }
            1 -> GroupGrid(
                groups = remember(audio) { audio.filter { it.album.isNotBlank() }.groupBy { it.album + "\u0000" + it.albumId }.values.sortedBy { it.first().album.lowercase() } },
                title = { it.first().album }, subtitle = { g -> g.first().artist.ifBlank { "${g.size} songs" } },
                icon = Icons.Rounded.Album,
                onOpen = { g -> open(Route.Collection(g.first().album, g.first().artist, g.sortedBy { it.title.lowercase() }, "album:" + g.first().albumId)) },
            )
            2 -> GroupList(
                groups = remember(audio) { audio.groupBy { it.artist.ifBlank { "Unknown artist" } }.toSortedMap(String.CASE_INSENSITIVE_ORDER).toList() },
                icon = Icons.Rounded.Person,
                onOpen = { (name, list) -> open(Route.Collection(name, "${list.size} songs", list.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() })), "artist:$name")) },
            )
            else -> GroupList(
                groups = remember(audio) { audio.groupBy { it.folderPath }.toSortedMap(String.CASE_INSENSITIVE_ORDER).toList() },
                icon = Icons.Rounded.Folder,
                label = { it.substringAfterLast('/').ifBlank { "Other" } },
                sub = { path, list -> "${list.size} songs • $path" },
                onOpen = { (path, list) -> open(Route.Collection(path.substringAfterLast('/'), path, list.sortedBy { it.title.lowercase() }, "folder:$path")) },
            )
        }
    }
}

@Composable
fun GroupGrid(
    groups: List<List<MediaEntry>>,
    title: (List<MediaEntry>) -> String,
    subtitle: (List<MediaEntry>) -> String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpen: (List<MediaEntry>) -> Unit,
) {
    if (groups.isEmpty()) { EmptyState(icon, "Nothing here", "No albums found in your music."); return }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        items(groups, key = { title(it) + it.first().albumId }) { g ->
            Column(Modifier.padding(6.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen(g) }) {
                MediaThumb(g.first(), Modifier.fillMaxWidth().aspectRatio(1f), sizePx = 400, corner = 14.dp)
                Text(title(g), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                Text(subtitle(g), maxLines = 1, overflow = TextOverflow.Ellipsis, style = subtleText)
            }
        }
    }
}

@Composable
fun GroupList(
    groups: List<Pair<String, List<MediaEntry>>>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: (String) -> String = { it },
    sub: (String, List<MediaEntry>) -> String = { _, l -> "${l.size} items • ${formatDuration(l.sumOf { it.durationMs })}" },
    onOpen: (Pair<String, List<MediaEntry>>) -> Unit,
) {
    if (groups.isEmpty()) { EmptyState(icon, "Nothing here", "No media found."); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
        items(groups, key = { it.first }) { g ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(g) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(label(g.first), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(sub(g.first, g.second), maxLines = 1, overflow = TextOverflow.Ellipsis, style = subtleText)
                }
            }
        }
    }
}

// ---------------- Videos ----------------

@Composable
fun VideosTab(open: (Route) -> Unit) {
    val video by AppGraph.library.video.collectAsState()
    var view by rememberSaveable { mutableStateOf(0) }
    val sort by AppGraph.settings.videoSort.flow.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ChipRow(listOf("All videos", "Folders"), view) { view = it }
        if (view == 0) {
            val sorted = remember(video, sort) { video.sortedByKey(sort) }
            MediaList(
                sorted, "all-videos", sortKey = sort, onSortChange = { AppGraph.settings.videoSort.value = it },
                newSince = AppGraph.library.newSinceSec,
                emptyContent = { EmptyState(Icons.Rounded.Movie, "No videos found", "Videos on your phone appear here. You can also open any folder from Browse.") },
            )
        } else GroupList(
            groups = remember(video) { video.groupBy { it.folderPath }.toSortedMap(String.CASE_INSENSITIVE_ORDER).toList() },
            icon = Icons.Rounded.Folder,
            label = { it.substringAfterLast('/').ifBlank { "Other" } },
            sub = { path, list -> "${list.size} videos • $path" },
            onOpen = { (path, list) -> open(Route.Collection(path.substringAfterLast('/'), path, list.sortedBy { it.title.lowercase() }, "vfolder:$path")) },
        )
    }
}

// ---------------- Collection (album / artist / folder) ----------------

@Composable
fun CollectionScreen(r: Route.Collection) {
    MediaList(
        r.entries, r.sourceId,
        header = {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    r.entries.firstOrNull()?.let { MediaThumb(it, Modifier.size(84.dp), sizePx = 300, corner = 16.dp) }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(r.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (r.subtitle.isNotBlank()) Text(r.subtitle, style = subtleText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
    )
}

// ---------------- Search ----------------

@Composable
fun SearchResults(query: String) {
    val audio by AppGraph.library.audio.collectAsState()
    val video by AppGraph.library.video.collectAsState()
    val q = query.trim().lowercase()
    val results = remember(q, audio, video) {
        if (q.isEmpty()) emptyList()
        else (audio + video).filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) || it.fileName.lowercase().contains(q)
        }.take(400)
    }
    MediaList(
        results, "search:$q",
        emptyContent = {
            if (q.isNotEmpty()) EmptyState(Icons.Rounded.LibraryMusic, "No matches", "Try another word from the title, artist or album.")
        },
    )
}
