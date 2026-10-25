package com.rady.reelio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rady.reelio.AppGraph
import com.rady.reelio.data.MediaEntry
import com.rady.reelio.data.Playlist
import com.rady.reelio.data.formatDuration
import com.rady.reelio.player.PlayerHub
import com.rady.reelio.player.RepeatMode

@Composable
fun PlaylistsTab(open: (Route) -> Unit) {
    val lists by AppGraph.playlists.lists.collectAsState()
    val actions = LocalUiActions.current
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            items(lists, key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clickable { open(Route.PlaylistDetail(p.id)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(56.dp)) {
                        val first = p.items.firstOrNull()
                        if (first != null) MediaThumb(first, Modifier.fillMaxSize(), sizePx = 200, corner = 12.dp)
                        else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(3.dp).size(22.dp).clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (p.isFavorites) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.items.size} items • ${formatDuration(p.totalMs)}", style = subtleText)
                    }
                    IconButton(onClick = { actions.play(p.items, -1, true, "pl:" + p.id) }, enabled = p.items.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, "Shuffle") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Play in order") }, onClick = { menu = false; actions.play(p.items, 0, false, "pl:" + p.id) })
                            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; PlayerHub.enqueue(p.items, false) })
                            if (!p.isFavorites) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = p })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = p })
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Tip: Shuffle is smart — a song won't come back until every other song in the playlist has had its turn, even if you close the app.",
                    style = subtleText, modifier = Modifier.padding(16.dp),
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("New playlist") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (creating) TextInputDialog("New playlist", "", "Create", { creating = false }) { name ->
        val p = AppGraph.playlists.create(name)
        open(Route.PlaylistDetail(p.id))
    }
    renaming?.let { p -> TextInputDialog("Rename playlist", p.name, "Save", { renaming = null }) { AppGraph.playlists.rename(p.id, it) } }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${p.name}\"?") },
            text = { Text("The playlist is removed. Your files stay on the phone.") },
            confirmButton = { TextButton(onClick = { AppGraph.playlists.delete(p.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun PlaylistDetailScreen(id: String, open: (Route) -> Unit) {
    val lists by AppGraph.playlists.lists.collectAsState()
    val p = lists.firstOrNull { it.id == id } ?: return
    val actions = LocalUiActions.current
    val state by PlayerHub.state.collectAsState()
    val src = "pl:" + p.id
    val playingThis = state.sourceId == src
    MediaList(
        p.items, src,
        header = {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    p.items.firstOrNull()?.let { MediaThumb(it, Modifier.size(88.dp), sizePx = 300, corner = 18.dp) }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${p.items.size} items • ${formatDuration(p.totalMs)}", style = subtleText)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Loop:", style = subtleText, modifier = Modifier.padding(end = 8.dp))
                    listOf(RepeatMode.OFF to "Off", RepeatMode.ALL to "Playlist", RepeatMode.ONE to "One song").forEach { (m, label) ->
                        val icon = when (m) { RepeatMode.ALL -> Icons.Rounded.Repeat; RepeatMode.ONE -> Icons.Rounded.RepeatOne; else -> null }
                        FilterChip(
                            selected = state.repeat == m,
                            onClick = { PlayerHub.setRepeat(m) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (icon != null) Icon(icon, null, Modifier.size(16.dp).padding(end = 4.dp))
                                    Text(label)
                                }
                            },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Row(Modifier.padding(top = 4.dp)) {
                    FilledTonalButton(onClick = { open(Route.Picker(p.id)) }) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add songs or videos")
                    }
                }
                if (playingThis) Text(
                    if (state.shuffle) "Now playing in smart shuffle" else "Now playing in order",
                    style = subtleText, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        extraMenu = { _, i ->
            buildList {
                add("Remove from playlist" to { AppGraph.playlists.removeAt(p.id, i) })
                if (i > 0) add("Move up" to { AppGraph.playlists.move(p.id, i, i - 1) })
                if (i < p.items.size - 1) add("Move down" to { AppGraph.playlists.move(p.id, i, i + 1) })
                if (i > 0) add("Move to top" to { AppGraph.playlists.move(p.id, i, 0) })
                if (i < p.items.size - 1) add("Move to bottom" to { AppGraph.playlists.move(p.id, i, p.items.size - 1) })
            }
        },
        emptyContent = {
            EmptyState(Icons.AutoMirrored.Rounded.PlaylistPlay, "Empty playlist", "Add songs or videos with the button above, or from any list using ⋮ → Add to playlist.")
        },
    )
}

/** Pick many songs/videos at once to add to a playlist. */
@Composable
fun PickerScreen(playlistId: String, done: () -> Unit) {
    val ctx = LocalContext.current
    val audio by AppGraph.library.audio.collectAsState()
    val video by AppGraph.library.video.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<MediaEntry>()) }
    val pickedUris = remember(picked) { picked.map { it.uri }.toSet() }
    val source = if (tab == 0) audio else video
    val shown = remember(source, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) source else source.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        ChipRow(listOf("Music", "Videos"), tab) { tab = it }
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.uri }) { e ->
                MediaRow(
                    e,
                    onClick = { picked = if (e.uri in pickedUris) picked.filterNot { it.uri == e.uri } else picked + e },
                    onLongClick = { picked = if (e.uri in pickedUris) picked.filterNot { it.uri == e.uri } else picked + e },
                    selected = e.uri in pickedUris,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${picked.size} selected", Modifier.weight(1f))
            TextButton(onClick = done) { Text("Cancel") }
            Button(onClick = {
                val n = AppGraph.playlists.add(playlistId, picked)
                ctx.toast("Added $n")
                done()
            }, enabled = picked.isNotEmpty()) {
                Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(6.dp)); Text("Add")
            }
        }
    }
}
