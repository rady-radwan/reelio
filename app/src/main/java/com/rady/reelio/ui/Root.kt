package com.rady.reelio.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rady.reelio.AppGraph
import com.rady.reelio.data.MediaEntry
import com.rady.reelio.player.PlayerHub

sealed interface Route {
    data object Main : Route
    data class Collection(val title: String, val subtitle: String, val entries: List<MediaEntry>, val sourceId: String) : Route
    data class PlaylistDetail(val id: String) : Route
    data class Picker(val playlistId: String) : Route
    data object NowPlaying : Route
    data object Search : Route
    data object Settings : Route
    data object SubtitleSettings : Route
    data object Boost : Route
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Rounded.Home),
    Tab("Music", Icons.Rounded.LibraryMusic),
    Tab("Videos", Icons.Rounded.VideoLibrary),
    Tab("Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
    Tab("Browse", Icons.Rounded.Folder),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelioRoot(openPlayerSignal: Int) {
    val ctx = LocalContext.current
    val stack = remember { mutableStateListOf<Route>(Route.Main) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var addTarget by remember { mutableStateOf<List<MediaEntry>?>(null) }
    var infoTarget by remember { mutableStateOf<MediaEntry?>(null) }
    var hasPerm by remember { mutableStateOf(AppGraph.library.hasMediaPermission()) }
    val route = stack.last()
    val state by PlayerHub.state.collectAsState()
    val scanning by AppGraph.library.scanning.collectAsState()

    fun push(r: Route) { if (stack.last() != r) stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    LaunchedEffect(openPlayerSignal) {
        if (openPlayerSignal > 0 && PlayerHub.queue.current != null) push(Route.NowPlaying)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = AppGraph.library.hasMediaPermission()
        if (hasPerm) AppGraph.library.refresh()
    }
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    LaunchedEffect(Unit) {
        if (!hasPerm) permLauncher.launch(perms) else AppGraph.library.refresh()
    }

    val actions = remember {
        UiActions(
            play = { list, idx, shuffle, src ->
                PlayerHub.playList(list, idx, shuffle, src)
                if (PlayerHub.queue.current?.isVideo == true) openVideoPlayer(ctx)
            },
            addToPlaylist = { addTarget = it },
            showInfo = { infoTarget = it },
            openNowPlaying = { push(Route.NowPlaying) },
        )
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    CompositionLocalProvider(LocalUiActions provides actions) {
        Scaffold(
            topBar = {
                if (route != Route.NowPlaying) TopAppBar(
                    navigationIcon = {
                        if (route != Route.Main) IconButton(onClick = { pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    title = {
                        when (route) {
                            Route.Main -> Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(brush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))) { append("Reelio") }
                                },
                                fontWeight = FontWeight.Black, fontSize = 26.sp,
                            )
                            Route.Search -> TextField(
                                query, { query = it },
                                placeholder = { Text("Songs, artists, albums, videos") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            is Route.Collection -> Text(route.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            is Route.PlaylistDetail -> Text(AppGraph.playlists.get(route.id)?.name ?: "Playlist", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            is Route.Picker -> Text("Add to playlist")
                            Route.Settings -> Text("Settings")
                            Route.SubtitleSettings -> Text("Subtitles")
                            Route.Boost -> Text("Sound")
                            Route.NowPlaying -> {}
                        }
                    },
                    actions = {
                        if (route == Route.Main) {
                            IconButton(onClick = { push(Route.Search) }) { Icon(Icons.Rounded.Search, "Search") }
                            IconButton(onClick = { push(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                Column {
                    if (route != Route.NowPlaying && route !is Route.Picker) MiniPlayer(onOpen = {
                        if (state.current?.isVideo == true) openVideoPlayer(ctx) else push(Route.NowPlaying)
                    })
                    if (route == Route.Main) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                if (!hasPerm) {
                    PermissionScreen { permLauncher.launch(perms) }
                } else {
                    if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            if (targetState == Route.NowPlaying) slideInVertically { it } togetherWith fadeOut()
                            else if (initialState == Route.NowPlaying) fadeIn() togetherWith slideOutVertically { it }
                            else fadeIn() togetherWith fadeOut()
                        },
                        label = "route",
                    ) { r ->
                        when (r) {
                            Route.Main -> when (tab) {
                                0 -> HomeTab(::push)
                                1 -> MusicTab(::push)
                                2 -> VideosTab(::push)
                                3 -> PlaylistsTab(::push)
                                else -> BrowserTab()
                            }
                            is Route.Collection -> CollectionScreen(r)
                            is Route.PlaylistDetail -> PlaylistDetailScreen(r.id, ::push)
                            is Route.Picker -> PickerScreen(r.playlistId) { pop() }
                            Route.NowPlaying -> NowPlayingScreen { pop() }
                            Route.Search -> SearchResults(query)
                            Route.Settings -> SettingsScreen(::push)
                            Route.SubtitleSettings -> SubtitleSettingsScreen()
                            Route.Boost -> BoostEqScreen()
                        }
                    }
                }
            }
        }
        addTarget?.let { AddToPlaylistDialog(it) { addTarget = null } }
        infoTarget?.let { InfoDialog(it) { infoTarget = null } }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            Icons.Rounded.LibraryMusic,
            "Let Reelio find your music and videos",
            "Reelio needs permission to read the songs and videos on your phone. Nothing leaves your device.",
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onGrant) { Text("Allow access") }
    }
}
