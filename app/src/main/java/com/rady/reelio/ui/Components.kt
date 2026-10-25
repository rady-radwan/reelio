package com.rady.reelio.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rady.reelio.AppGraph
import com.rady.reelio.data.MediaEntry
import com.rady.reelio.data.Thumbnails
import com.rady.reelio.data.formatAgo
import com.rady.reelio.data.formatDuration
import com.rady.reelio.data.formatSize
import com.rady.reelio.player.PlayerHub
import com.rady.reelio.video.VideoActivity
import kotlinx.coroutines.delay

/** Actions any screen can trigger; provided by the root so dialogs live in one place. */
class UiActions(
    val play: (list: List<MediaEntry>, index: Int, shuffle: Boolean, sourceId: String?) -> Unit,
    val addToPlaylist: (List<MediaEntry>) -> Unit,
    val showInfo: (MediaEntry) -> Unit,
    val openNowPlaying: () -> Unit,
)

val LocalUiActions = staticCompositionLocalOf<UiActions> { error("no actions") }

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun openVideoPlayer(ctx: Context) {
    ctx.startActivity(Intent(ctx, VideoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun MediaThumb(entry: MediaEntry, modifier: Modifier = Modifier, sizePx: Int = 256, corner: Dp = 10.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(Thumbnails.cached(entry, sizePx)?.asImageBitmap(), entry.uri, sizePx) {
        if (value == null) value = Thumbnails.load(ctx, entry, sizePx)?.asImageBitmap()
    }
    val spec = LocalThemeSpec.current
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    listOf(spec.primary.copy(alpha = 0.35f), spec.accent.copy(alpha = 0.25f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(
            if (entry.isVideo) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRow(
    entry: MediaEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    showAdded: Boolean = false,
    extraMenu: List<Pair<String, () -> Unit>> = emptyList(),
    leading: (@Composable () -> Unit)? = null,
) {
    val state by PlayerHub.state.collectAsState()
    val isCurrent = state.current?.uri == entry.uri
    var menu by remember { mutableStateOf(false) }
    val posVersion by AppGraph.positions.version.collectAsState()
    val resume = remember(entry.uri, posVersion) { if (entry.isVideo) AppGraph.positions.get(entry.uri) else 0L }
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick ?: { menu = true })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Box {
            if (entry.isVideo) {
                MediaThumb(entry, Modifier.width(112.dp).aspectRatio(16f / 9f), sizePx = 320, corner = 8.dp)
                if (entry.durationMs > 0) Text(
                    formatDuration(entry.durationMs),
                    color = Color.White, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
                if (resume > 0 && entry.durationMs > 0) LinearProgressIndicator(
                    progress = { (resume.toFloat() / entry.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).width(112.dp).height(3.dp),
                    color = MaterialTheme.colorScheme.primary, trackColor = Color.Black.copy(alpha = 0.4f),
                    drawStopIndicator = {},
                )
            } else MediaThumb(entry, Modifier.size(48.dp), sizePx = 160)
            if (isCurrent) Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary) }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isNew) NewBadge()
                Text(
                    entry.title, maxLines = if (entry.isVideo) 2 else 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            val sub = buildList {
                if (!entry.isVideo) { entry.artist.takeIf { it.isNotBlank() }?.let(::add); if (entry.durationMs > 0) add(formatDuration(entry.durationMs)) }
                else { formatSize(entry.sizeBytes).takeIf { it.isNotEmpty() }?.let(::add) }
                if (showAdded) formatAgo(entry.dateAddedSec).takeIf { it.isNotEmpty() }?.let(::add)
                if (entry.isVideo && entry.folderName.isNotEmpty()) add(entry.folderName)
            }.joinToString(" • ")
            if (sub.isNotEmpty()) Text(sub, style = subtleText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected != null) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
        } else Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            EntryMenu(entry, menu, { menu = false }, extraMenu)
        }
    }
}

@Composable
fun NewBadge() {
    Text(
        "NEW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary,
        modifier = Modifier.padding(end = 6.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun EntryMenu(entry: MediaEntry, expanded: Boolean, onDismiss: () -> Unit, extra: List<Pair<String, () -> Unit>> = emptyList()) {
    val actions = LocalUiActions.current
    val ctx = LocalContext.current
    val fav = AppGraph.playlists.lists.collectAsState().value.let { AppGraph.playlists.isFavorite(entry.uri) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { Icon(Icons.Rounded.SkipNext, null) }, onClick = {
            onDismiss(); PlayerHub.enqueue(listOf(entry), playNext = true); ctx.toast("Plays next")
        })
        DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) }, onClick = {
            onDismiss(); PlayerHub.enqueue(listOf(entry), playNext = false); ctx.toast("Added to queue")
        })
        DropdownMenuItem(text = { Text("Add to playlist…") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }, onClick = {
            onDismiss(); actions.addToPlaylist(listOf(entry))
        })
        DropdownMenuItem(
            text = { Text(if (fav) "Remove from Favorites" else "Add to Favorites") },
            leadingIcon = { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
            onClick = { onDismiss(); AppGraph.playlists.toggleFavorite(entry) },
        )
        DropdownMenuItem(text = { Text("Details") }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = {
            onDismiss(); actions.showInfo(entry)
        })
        if (extra.isNotEmpty()) HorizontalDivider()
        extra.forEach { (label, action) ->
            DropdownMenuItem(text = { Text(label) }, onClick = { onDismiss(); action() })
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = subtleText, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

/** The small player bar above the bottom tabs. */
@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val s by PlayerHub.state.collectAsState()
    val cur = s.current ?: return
    var pos by remember { mutableLongStateOf(PlayerHub.positionMs()) }
    LaunchedEffect(s.playing, cur.uri) {
        while (true) { pos = PlayerHub.positionMs(); delay(500) }
    }
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column {
            Row(
                Modifier.clickable(onClick = onOpen).padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaThumb(cur, Modifier.size(44.dp), sizePx = 160, corner = 10.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(cur.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    val sub = s.error ?: cur.artist.ifBlank { if (cur.isVideo) "Video" else "Unknown artist" }
                    Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = subtleText,
                        color = if (s.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { PlayerHub.previous() }) { Icon(Icons.Rounded.SkipPrevious, "Previous") }
                IconButton(onClick = { PlayerHub.toggle() }) {
                    Icon(if (s.playWhenReady && !s.ended) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause", modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { PlayerHub.next() }, enabled = s.hasNext) { Icon(Icons.Rounded.SkipNext, "Next") }
            }
            val d = s.durationMs
            LinearProgressIndicator(
                progress = { if (d > 0) (pos.toFloat() / d).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 6.dp).height(2.dp).clip(CircleShape),
                drawStopIndicator = {},
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
fun AddToPlaylistDialog(entries: List<MediaEntry>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val lists by AppGraph.playlists.lists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entries.size == 1) "Add to playlist" else "Add ${entries.size} items to playlist") },
        text = {
            if (creating) OutlinedTextField(name, { name = it }, label = { Text("Playlist name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            else LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { creating = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Text("New playlist", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(lists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            val n = AppGraph.playlists.add(p.id, entries)
                            ctx.toast(if (n == 0) "Already in ${p.name}" else "Added $n to ${p.name}")
                            onDismiss()
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (p.isFavorites) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic, null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(p.name)
                            Text("${p.items.size} items", style = subtleText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = {
                val p = AppGraph.playlists.create(name, entries)
                ctx.toast("Created ${p.name}")
                onDismiss()
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun InfoDialog(entry: MediaEntry, onDismiss: () -> Unit) {
    val rows = listOf(
        "Title" to entry.title,
        "Artist" to entry.artist,
        "Album" to entry.album,
        "Length" to formatDuration(entry.durationMs),
        "Size" to formatSize(entry.sizeBytes),
        "Format" to entry.extension.uppercase(),
        "Added" to formatAgo(entry.dateAddedSec),
        "Location" to (if (entry.isFile) entry.uri else entry.uri),
        "Engine" to (if (PlayerHub.usesVlc(entry)) "VLC" else "Main (Media3)"),
    ).filter { it.second.isNotBlank() && it.second != "--:--" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (k, v) ->
                    Column {
                        Text(k, style = subtleText)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

val listPadding = PaddingValues(bottom = 16.dp)

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))
