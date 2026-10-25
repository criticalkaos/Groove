package app.groove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.groove.data.Album
import app.groove.data.Library
import app.groove.data.UserStore
import app.groove.playback.Playback
import kotlinx.coroutines.launch

@Composable
private fun TabHeader(title: String) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { Nav.go(Screen.Search) }) { Icon(Icons.Filled.Search, "Search") }
    }
}

// ---------------------------------------------------------------- Artists

@Composable
fun ArtistsScreen() {
    val artists by Library.artists.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TabHeader("Artists")
        LazyColumn(Modifier.fillMaxSize()) {
            items(artists, key = { it.name }) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { Nav.go(Screen.ArtistPage(a.name)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArt(a.albums.firstOrNull(), Modifier.size(56.dp), corner = 28.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${a.albums.size} album${if (a.albums.size == 1) "" else "s"} · ${a.trackCount} songs",
                            color = TextDim, style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(name: String) {
    Library.albums.collectAsState().value
    val artist = Library.artist(name)
    // Albums by this artist plus compilations they appear on.
    val all by Library.albums.collectAsState()
    val appearsOn = remember(all, name) {
        all.filter { a -> a.artist != name && a.tracks.any { it.artist == name } }
    }
    val albums = artist?.albums.orEmpty()
    val tracks = (albums + appearsOn).flatMap { a -> a.tracks.filter { albums.contains(a) || it.artist == name } }
    val accent = rememberAccent(albums.firstOrNull())

    Column(Modifier.fillMaxSize()) {
        BackTopBar(name)
        AlbumGrid(albums + appearsOn) {
            Column {
                Text(
                    "${albums.size} album${if (albums.size == 1) "" else "s"} · ${tracks.size} songs",
                    color = TextDim, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { Playback.play(tracks) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                    ) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play all") }
                    OutlinedButton(onClick = { Playback.play(tracks, shuffle = true) }) {
                        Icon(Icons.Filled.Shuffle, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Shuffle", color = Color.White)
                    }
                }
                if (appearsOn.isNotEmpty()) {
                    Text(
                        "Includes ${appearsOn.size} album${if (appearsOn.size == 1) "" else "s"} they appear on",
                        color = TextFaint, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Songs

@Composable
fun SongsScreen() {
    val tracks by Library.tracks.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TabHeader("Songs")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { Playback.play(tracks, shuffle = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = GrooveAccent, contentColor = Color.Black)
                    ) { Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle all ${tracks.size}") }
                }
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                TrackRow(t, onClick = { Playback.play(tracks, i) }, showArt = true, subtitle = "${t.artist} · ${t.album}")
            }
        }
    }
}

// ---------------------------------------------------------------- Collection: recent, favourites, playlists

@Composable
fun CollectionScreen() {
    val recent by UserStore.recentAlbums.collectAsState()
    val favs by UserStore.favourites.collectAsState()
    val playlists by UserStore.playlists.collectAsState()
    val favAlbumIds by UserStore.favAlbums.collectAsState()
    val genreMap by UserStore.albumGenres.collectAsState()
    Library.albums.collectAsState().value
    val recentAlbums = recent.mapNotNull { Library.album(it) }
    val favAlbums = favAlbumIds.mapNotNull { Library.album(it) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TabHeader("Collection")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text("Recently played", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 12.dp))
                if (recentAlbums.isEmpty()) {
                    Text("Albums you play will show up here.", color = TextDim, modifier = Modifier.padding(horizontal = 20.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recentAlbums, key = { it.id }) { a -> AlbumCard(a, Modifier.width(140.dp)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Favourite albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
                if (favAlbums.isEmpty()) {
                    Text("Tap the heart on any album to keep it here.", color = TextDim, modifier = Modifier.padding(horizontal = 20.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favAlbums, key = { it.id }) { a -> AlbumCard(a, Modifier.width(140.dp)) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                PlaylistRow(
                    "Genres",
                    genreMap.values.flatten().distinct().size.let { if (it == 0) "Tag albums from their album page" else "$it genres" },
                    icon = { Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(GrooveCard), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Sell, null, tint = GrooveAccent)
                    } }
                ) { Nav.go(Screen.Genres) }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Playlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("New") }
                }
            }
            item {
                PlaylistRow(
                    "Liked songs", "${favs.size} songs · every song you heart",
                    icon = { Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(GrooveAccent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Favorite, null, tint = Color.Black)
                    } }
                ) { Nav.go(Screen.PlaylistPage(FAVOURITES_ID)) }
            }
            items(playlists, key = { it.id }) { p ->
                val first = p.trackIds.firstNotNullOfOrNull { Library.track(it) }
                PlaylistRow(
                    p.name, "${p.trackIds.size} songs",
                    icon = { AlbumArt(first?.let { Library.album(it.albumId) }, Modifier.size(56.dp), corner = 8.dp) }
                ) { Nav.go(Screen.PlaylistPage(p.id)) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (creating) {
        NameDialog("New playlist", "") { name ->
            creating = false
            if (name != null) UserStore.createPlaylist(name)
        }
    }
}

@Composable
private fun PlaylistRow(title: String, sub: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, color = TextDim, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = GrooveCard,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onDone(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } }
    )
}

@Composable
fun PlaylistScreen(id: Long) {
    val playlists by UserStore.playlists.collectAsState()
    val favs by UserStore.favourites.collectAsState()
    Library.albums.collectAsState().value
    val isFav = id == FAVOURITES_ID
    val playlist = playlists.firstOrNull { it.id == id }
    if (!isFav && playlist == null) {
        Column { BackTopBar(""); EmptyState("Playlist not found", "It may have been deleted.") }
        return
    }
    val ids = if (isFav) favs else playlist!!.trackIds
    val tracks = ids.mapNotNull { Library.track(it) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(if (isFav) "Liked songs" else playlist!!.name) {
            if (!isFav) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; renaming = true })
                        DeleteMenuItem("Delete playlist") { menu = false; confirmDelete = true }
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            EmptyState(
                if (isFav) "No liked songs yet" else "Empty playlist",
                if (isFav) "Tap the heart on any song to add it here." else "Use \"Add to playlist…\" on any song or album."
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { Playback.play(tracks) },
                        colors = ButtonDefaults.buttonColors(containerColor = GrooveAccent, contentColor = Color.Black)
                    ) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play") }
                    OutlinedButton(onClick = { Playback.play(tracks, shuffle = true) }) {
                        Icon(Icons.Filled.Shuffle, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Shuffle", color = Color.White)
                    }
                }
            }
            itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
                TrackRow(
                    t, onClick = { Playback.play(tracks, i) }, showArt = true,
                    extraMenu = if (isFav) null else { close ->
                        if (i > 0) DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Filled.ArrowUpward, null) },
                            onClick = { UserStore.movePlaylistItem(id, i, i - 1); close() })
                        if (i < tracks.lastIndex) DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Filled.ArrowDownward, null) },
                            onClick = { UserStore.movePlaylistItem(id, i, i + 1); close() })
                        DeleteMenuItem("Remove from playlist") { UserStore.removeFromPlaylist(id, i); close() }
                    }
                )
            }
        }
    }
    if (renaming && playlist != null) {
        NameDialog("Rename playlist", playlist.name) { n -> renaming = false; if (n != null) UserStore.renamePlaylist(id, n) }
    }
    if (confirmDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = GrooveCard,
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("The songs stay on your phone; only the playlist is removed.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; UserStore.deletePlaylist(id); Nav.back() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- Search

@Composable
fun SearchScreen() {
    var q by remember { mutableStateOf("") }
    val tracks by Library.tracks.collectAsState()
    val albums by Library.albums.collectAsState()
    val artists by Library.artists.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) {} }

    val needle = q.trim().lowercase()
    val foundArtists = if (needle.isEmpty()) emptyList() else artists.filter { needle in it.name.lowercase() }.take(5)
    val foundAlbums = if (needle.isEmpty()) emptyList() else albums.filter { needle in it.title.lowercase() || needle in it.artist.lowercase() }.take(12)
    val foundTracks = if (needle.isEmpty()) emptyList() else tracks.filter {
        needle in it.title.lowercase() || needle in it.artist.lowercase() || needle in it.album.lowercase()
    }.take(60)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            TextField(
                value = q, onValueChange = { q = it },
                placeholder = { Text("Songs, albums, artists") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GrooveCard, unfocusedContainerColor = GrooveCard,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focus)
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (foundArtists.isNotEmpty()) {
                item { SearchHeader("Artists") }
                items(foundArtists, key = { "a" + it.name }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Nav.go(Screen.ArtistPage(a.name)) }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArt(a.albums.firstOrNull(), Modifier.size(48.dp).clip(CircleShape), corner = 24.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(a.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (foundAlbums.isNotEmpty()) {
                item {
                    SearchHeader("Albums")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(foundAlbums, key = { it.id }) { a -> AlbumCard(a, Modifier.width(130.dp)) }
                    }
                }
            }
            if (foundTracks.isNotEmpty()) {
                item { SearchHeader("Songs") }
                itemsIndexed(foundTracks, key = { _, t -> "t${t.id}" }) { i, t ->
                    TrackRow(t, onClick = { Playback.play(foundTracks, i) }, showArt = true, subtitle = "${t.artist} · ${t.album}")
                }
            }
            if (needle.isNotEmpty() && foundArtists.isEmpty() && foundAlbums.isEmpty() && foundTracks.isEmpty()) {
                item { Text("Nothing matches \"$q\"", color = TextDim, modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
private fun SearchHeader(t: String) {
    Text(t, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
}

// ---------------------------------------------------------------- Folders

@Composable
fun FoldersScreen() {
    val folders by Library.folders.collectAsState()
    val selected by UserStore.selectedFolders.collectAsState()
    val scanning by Library.scanning.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choice by remember(selected) { mutableStateOf(selected) }

    fun isIncluded(path: String) = choice.any { path == it || path.startsWith("$it/") }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Music folders") {
            TextButton(onClick = { scope.launch { Library.scan(context) } }, enabled = !scanning) {
                Text(if (scanning) "Scanning…" else "Rescan")
            }
        }
        Text(
            "Tick the folders Groove should play from. Subfolders are included.",
            color = TextDim, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton(onClick = { choice = folders.map { it.path }.toSet() }) { Text("Select all") }
            TextButton(onClick = { choice = emptySet() }) { Text("Clear") }
        }
        HorizontalDivider()
        if (folders.isEmpty()) {
            Box(Modifier.weight(1f)) {
                EmptyState("No music found", "Copy some music onto your phone, then tap Rescan.")
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(folders, key = { it.path }) { f ->
                    val inc = isIncluded(f.path)
                    val viaParent = inc && f.path !in choice
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            choice = if (f.path in choice) choice - f.path
                            else if (viaParent) choice // covered by a parent folder
                            else choice + f.path
                        }.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = inc, enabled = !viaParent,
                            onCheckedChange = { on -> choice = if (on) choice + f.path else choice - f.path },
                            colors = CheckboxDefaults.colors(checkedColor = GrooveAccent, checkmarkColor = Color.Black)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.path.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                prettyPath(f.path) + " · ${f.trackCount} songs",
                                color = TextDim, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Button(
            onClick = { UserStore.setSelectedFolders(choice); Nav.back() },
            enabled = choice.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = GrooveAccent, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null); Spacer(Modifier.width(8.dp))
            Text("Use ${choice.size} folder${if (choice.size == 1) "" else "s"}")
        }
    }
}

private fun prettyPath(p: String): String =
    p.replace(Regex("^/storage/emulated/0"), "Phone").replace(Regex("^/storage/([^/]+)"), "SD card")
