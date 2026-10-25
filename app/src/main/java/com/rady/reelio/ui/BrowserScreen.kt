package com.rady.reelio.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.rady.reelio.data.MediaFormats
import com.rady.reelio.player.PlayerHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageRoot(val name: String, val path: String, val removable: Boolean)

fun storageRoots(ctx: Context): List<StorageRoot> {
    val out = ArrayList<StorageRoot>()
    val internal = Environment.getExternalStorageDirectory().absolutePath
    out += StorageRoot("Phone storage", internal, false)
    runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            val sm = ctx.getSystemService(StorageManager::class.java)
            sm.storageVolumes.forEach { v ->
                val dir = v.directory ?: return@forEach
                if (dir.absolutePath != internal) out += StorageRoot(v.getDescription(ctx) ?: dir.name, dir.absolutePath, v.isRemovable)
            }
        } else {
            ctx.getExternalFilesDirs(null).filterNotNull().forEach { f ->
                val root = f.absolutePath.substringBefore("/Android/data")
                if (root != internal && out.none { it.path == root }) out += StorageRoot("SD card", root, true)
            }
        }
    }
    return out
}

class Listing(val dirs: List<File>, val media: List<File>, val subs: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserTab() {
    val ctx = LocalContext.current
    val actions = LocalUiActions.current
    val roots = remember { storageRoots(ctx) }
    var path by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val showHidden by AppGraph.settings.browserShowHidden.flow.collectAsState()
    val hasAll = remember(refresh) { AppGraph.library.hasAllFilesAccess() }

    val currentRoot = roots.firstOrNull { path?.startsWith(it.path) == true }
    BackHandler(enabled = path != null) {
        val p = path!!
        path = if (roots.any { it.path == p }) null else File(p).parent
    }

    val listing by produceState<Listing?>(null, path, refresh, showHidden) {
        val p = path
        value = if (p == null) null else withContext(Dispatchers.IO) {
            val files = File(p).listFiles()?.filter { showHidden || !it.name.startsWith(".") } ?: emptyList()
            Listing(
                dirs = files.filter { it.isDirectory }.sortedBy { it.name.lowercase() },
                media = files.filter { it.isFile && MediaFormats.isMedia(it.name) }.sortedBy { it.name.lowercase() },
                subs = files.count { it.isFile && MediaFormats.isSubtitle(it.name) },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!hasAll && Build.VERSION.SDK_INT >= 30) {
            Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(14.dp)) {
                    Text("Allow access to all files", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Needed to see every folder and to load subtitle files (.srt, .ass…) next to your videos. Reelio only reads files.",
                        style = subtleText,
                    )
                    Row(Modifier.padding(top = 6.dp)) {
                        Button(onClick = {
                            runCatching {
                                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                            }.onFailure { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        }) { Text("Allow") }
                        TextButton(onClick = { refresh++ }) { Text("I've allowed it") }
                    }
                }
            }
        }
        if (path == null) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(roots, key = { it.path }) { r ->
                    FolderRow(r.name, r.path, if (r.removable) Icons.Rounded.SdCard else Icons.Rounded.PhoneAndroid) { path = r.path }
                }
                item {
                    Text("Common folders", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                }
                val base = Environment.getExternalStorageDirectory()
                listOf("Music", "Movies", "Download", "DCIM", "Podcasts", "Audiobooks", "Recordings", "WhatsApp")
                    .map { File(base, it) }
                    .filter { it.isDirectory }
                    .forEach { f -> item(key = f.absolutePath) { FolderRow(f.name, f.absolutePath, Icons.Rounded.Folder) { path = f.absolutePath } } }
            }
        } else {
            // breadcrumb
            val crumbs = remember(path, currentRoot) {
                val rootPath = currentRoot?.path ?: "/"
                val rel = path!!.removePrefix(rootPath).trim('/').split('/').filter { it.isNotEmpty() }
                val list = mutableListOf((currentRoot?.name ?: "Storage") to rootPath)
                var acc = rootPath
                rel.forEach { acc = "$acc/$it"; list += it to acc }
                list
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                crumbs.forEachIndexed { i, (name, p) ->
                    if (i > 0) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { path = p }) {
                        Text(name, maxLines = 1, fontWeight = if (i == crumbs.lastIndex) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            val l = listing
            if (l == null) Box(Modifier.fillMaxSize())
            else {
                val entries = remember(l) { AppGraph.library.entriesForFiles(l.media) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                    if (entries.isNotEmpty()) item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${entries.size} media files" + if (l.subs > 0) " • ${l.subs} subtitle files" else "",
                                style = subtleText, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { actions.play(entries, 0, false, "dir:$path") }) { Text("Play all") }
                            TextButton(onClick = { actions.play(entries, -1, true, "dir:$path") }) { Text("Shuffle") }
                        }
                    }
                    items(l.dirs, key = { it.absolutePath }) { d ->
                        FolderRow(d.name, null, Icons.Rounded.Folder, menu = listOf(
                            "Play this folder" to {
                                val media = d.listFiles()?.filter { it.isFile && MediaFormats.isMedia(it.name) }?.sortedBy { it.name.lowercase() } ?: emptyList()
                                if (media.isEmpty()) ctx.toast("No media directly in this folder") else actions.play(AppGraph.library.entriesForFiles(media), 0, false, "dir:" + d.absolutePath)
                            },
                            "Add folder to playlist…" to {
                                val media = d.listFiles()?.filter { it.isFile && MediaFormats.isMedia(it.name) }?.sortedBy { it.name.lowercase() } ?: emptyList()
                                if (media.isEmpty()) ctx.toast("No media directly in this folder") else actions.addToPlaylist(AppGraph.library.entriesForFiles(media))
                            },
                        )) { path = d.absolutePath }
                    }
                    items(entries.size, key = { "f" + entries[it].uri }) { i ->
                        val e = entries[i]
                        MediaRow(e, onClick = {
                            // Play this file, then the rest of the same kind in this folder
                            val same = entries.filter { it.isVideo == e.isVideo }
                            actions.play(same, same.indexOfFirst { it.uri == e.uri }, false, "dir:$path")
                        }, extraMenu = listOf("Add to library index" to {
                            File(path!!).let { AppGraph.library.scanFolder(it) }; ctx.toast("Asked Android to index this folder")
                        }))
                    }
                    if (l.dirs.isEmpty() && entries.isEmpty()) item {
                        EmptyState(Icons.Rounded.FolderOpen, "No media here", if (l.subs > 0) "Only subtitle files in this folder." else "This folder has no songs or videos.")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    name: String,
    sub: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
    onClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { if (menu.isNotEmpty()) open = true })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = subtleText)
        }
        if (menu.isNotEmpty()) Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, null) }
            DropdownMenu(open, { open = false }) {
                menu.forEach { (label, act) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; act() }) }
            }
        }
    }
}

