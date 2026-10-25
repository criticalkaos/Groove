package app.groove.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.groove.data.Album
import app.groove.data.ArtLoader
import app.groove.data.Library
import app.groove.data.Track
import app.groove.data.UserStore
import app.groove.data.formatDuration
import app.groove.playback.Playback
import kotlinx.coroutines.delay

/** An album cover, with a stylish placeholder while loading or when there is no art. */
@Composable
fun AlbumArt(album: Album?, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val context = LocalContext.current
    val bmp by produceState(album?.let { ArtLoader.cached(it.id) }, album?.id) {
        value = album?.let { ArtLoader.load(context, it) }
    }
    Box(modifier.clip(RoundedCornerShape(corner)).background(GrooveCard)) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = album?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val hue = ((album?.title?.hashCode() ?: 0) and 0xffff) % 360
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(Color.hsv(hue.toFloat(), 0.55f, 0.35f), Color.hsv(((hue + 40) % 360).toFloat(), 0.6f, 0.15f))
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Album, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.fillMaxSize(0.45f))
            }
        }
    }
}

/** The album's accent colour taken from its cover, animated between albums. */
@Composable
fun rememberAccent(album: Album?): Color {
    val context = LocalContext.current
    val raw by produceState<Int?>(album?.let { ArtLoader.cachedAccent(it.id) }, album?.id) {
        value = album?.let { ArtLoader.accent(context, it) }
    }
    val target = raw?.let { brighten(Color(it)) } ?: GrooveAccent
    val animated by animateColorAsState(target, tween(600), label = "accent")
    return animated
}

private fun brighten(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
    hsv[2] = hsv[2].coerceAtLeast(0.75f)
    hsv[1] = hsv[1].coerceAtMost(0.85f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: String? = null,
    showArt: Boolean = false,
    subtitle: String? = track.artist,
    accent: Color = GrooveAccent,
    extraMenu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    val current = Playback.state.currentId == track.mediaId
    val favs by UserStore.favourites.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArt) {
            AlbumArt(Library.album(track.albumId), Modifier.size(48.dp), corner = 6.dp)
            Spacer(Modifier.width(14.dp))
        } else if (number != null) {
            Box(Modifier.width(34.dp)) {
                if (current) {
                    Icon(Icons.Filled.GraphicEq, null, tint = accent, modifier = Modifier.size(20.dp))
                } else {
                    Text(number, color = TextFaint, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (current) accent else Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle, color = TextDim, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(formatDuration(track.durationMs), color = TextFaint, style = MaterialTheme.typography.bodyMedium)
        val liked = track.id in favs
        IconButton(onClick = { UserStore.toggleFavourite(track.id) }) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (liked) "Unlike" else "Like",
                tint = if (liked) accent else TextFaint,
                modifier = Modifier.size(20.dp)
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, "More", tint = TextDim)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                TrackMenuItems(track) { menu = false }
                extraMenu?.invoke { menu = false }
            }
        }
    }
}

@Composable
fun TrackMenuItems(track: Track, close: () -> Unit) {
    val favs by UserStore.favourites.collectAsState()
    DropdownMenuItem(
        text = { Text("Play next") },
        leadingIcon = { Icon(Icons.Filled.SkipNext, null) },
        onClick = { Playback.playNext(listOf(track)); Toaster.show("Playing next"); close() })
    DropdownMenuItem(
        text = { Text("Add to queue") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
        onClick = { Playback.addToQueue(listOf(track)); Toaster.show("Added to queue"); close() })
    DropdownMenuItem(
        text = { Text("Add to playlist…") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
        onClick = { Nav.addToPlaylist = listOf(track); close() })
    DropdownMenuItem(
        text = { Text(if (track.id in favs) "Remove from favourites" else "Add to favourites") },
        leadingIcon = { Icon(if (track.id in favs) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null) },
        onClick = { UserStore.toggleFavourite(track.id); close() })
    DropdownMenuItem(
        text = { Text("Go to album") },
        leadingIcon = { Icon(Icons.Filled.Album, null) },
        onClick = { Nav.go(Screen.AlbumPage(track.albumId)); close() })
    val artistName = Library.album(track.albumId)?.artist ?: track.artist
    DropdownMenuItem(
        text = { Text("Go to artist") },
        leadingIcon = { Icon(Icons.Filled.Person, null) },
        onClick = { Nav.go(Screen.ArtistPage(artistName)); close() })
}

@Composable
fun AddToPlaylistDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val playlists by UserStore.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GrooveCard,
        title = { Text("Add to playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("New playlist name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        UserStore.createPlaylist(newName, tracks.map { it.id })
                        Toaster.show("Created \"${newName.trim()}\"")
                        onDismiss()
                    }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Create and add")
                }
                if (playlists.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    LazyColumn(Modifier.height((playlists.size * 48).coerceAtMost(240).dp)) {
                        items(playlists, key = { it.id }) { p ->
                            Text(
                                p.name,
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        UserStore.addToPlaylist(p.id, tracks.map { it.id })
                                        Toaster.show("Added to ${p.name}")
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val st = Playback.state
    val track = Library.track(st.currentId) ?: return
    val album = Library.album(track.albumId)
    val accent = rememberAccent(album)
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(st.currentId, st.isPlaying, st.durationMs) {
        while (true) {
            progress = if (st.durationMs > 0) Playback.position().toFloat() / st.durationMs else 0f
            if (!st.isPlaying) break
            delay(500)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.28f), GrooveCard)))
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(album, Modifier.size(44.dp), corner = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextDim, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { Playback.toggle() }) {
                Icon(if (st.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { Playback.next() }) {
                Icon(Icons.Filled.SkipNext, "Next")
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = accent,
            trackColor = Color.White.copy(alpha = 0.08f),
            drawStopIndicator = {},
        )
    }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Album, null, tint = GrooveAccent, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, color = TextDim, style = MaterialTheme.typography.bodyMedium)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(onClick = onAction) { Text(action) }
        }
    }
}

/** Little helper so non-composable code can show a toast-style message. */
object Toaster {
    var message by mutableStateOf<String?>(null)
    fun show(m: String) { message = m }
}

@Composable
fun DeleteMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Delete, null) },
        onClick = onClick
    )
}

/** Heart button for an album. */
@Composable
fun AlbumHeart(albumId: Long, tint: Color = GrooveAccent, size: Dp = 26.dp) {
    val favAlbums by UserStore.favAlbums.collectAsState()
    val on = albumId in favAlbums
    IconButton(onClick = {
        UserStore.toggleFavAlbum(albumId)
        Toaster.show(if (on) "Removed from favourite albums" else "Added to favourite albums")
    }) {
        Icon(
            if (on) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            if (on) "Remove album from favourites" else "Add album to favourites",
            tint = if (on) tint else Color.White,
            modifier = Modifier.size(size)
        )
    }
}

/** Pick genres for an album: tap to toggle, or type a new one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreDialog(albumId: Long, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(UserStore.genresOf(albumId)) }
    var extra by remember { mutableStateOf(listOf<String>()) }
    var newName by remember { mutableStateOf("") }
    val options = (chosen + UserStore.genreSuggestions() + extra).distinctBy { it.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GrooveCard,
        title = { Text("Genres") },
        text = {
            Column {
                Text(
                    "Tap to add or remove. The first genre with saved sound is used for this album.",
                    color = TextDim, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { g ->
                        val on = chosen.any { it.equals(g, ignoreCase = true) }
                        FilterChip(
                            selected = on,
                            onClick = { chosen = if (on) chosen.filterNot { it.equals(g, ignoreCase = true) } else chosen + g },
                            label = { Text(g) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GrooveAccent, selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it }, singleLine = true,
                        label = { Text("New genre") }, modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val g = newName.trim()
                            if (chosen.none { it.equals(g, ignoreCase = true) }) chosen = chosen + g
                            extra = extra + g
                            newName = ""
                        }
                    ) { Icon(Icons.Filled.Add, "Add genre") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { UserStore.setAlbumGenres(albumId, chosen); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
