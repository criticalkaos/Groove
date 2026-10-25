package app.groove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import app.groove.data.UserStore
import app.groove.playback.AudioFx
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.groove.data.Library
import app.groove.data.formatDuration
import app.groove.data.formatLongDuration
import app.groove.playback.Playback

@Composable
fun BackTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { Nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

/** A proper album page: big cover, details, and the tracklist split by disc. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumScreen(id: Long) {
    Library.albums.collectAsState().value // re-read when the library changes
    val album = Library.album(id)
    if (album == null) {
        Column { BackTopBar(""); EmptyState("Album not found", "It may be outside your chosen folders.") }
        return
    }
    val accent = rememberAccent(album)
    var menu by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    val genreMap by UserStore.albumGenres.collectAsState()
    val genres = genreMap[album.id].orEmpty()
    val hasSound = AudioFx.profiles.containsKey(AudioFx.albumKey(album.id))

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(560.dp).background(
                Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.10f), Color.Transparent))
            )
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                BackTopBar("") {
                    AlbumHeart(album.id, accent)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { Icon(Icons.Filled.SkipNext, null) },
                                onClick = { Playback.playNext(album.tracks); Toaster.show("Album plays next"); menu = false })
                            DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                                onClick = { Playback.addToQueue(album.tracks); Toaster.show("Album added to queue"); menu = false })
                            DropdownMenuItem(text = { Text("Add to playlist…") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                onClick = { Nav.addToPlaylist = album.tracks; menu = false })
                        }
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AlbumArt(album, Modifier.widthIn(max = 320.dp).fillMaxWidth(0.78f).aspectRatio(1f), corner = 12.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(album.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        album.artist, color = accent, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable { Nav.go(Screen.ArtistPage(album.artist)) }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            album.year.takeIf { it > 0 }?.toString(),
                            "${album.tracks.size} songs",
                            formatLongDuration(album.durationMs),
                            album.tracks.firstOrNull()?.path?.substringAfterLast('.')?.uppercase()
                        ).joinToString("  ·  "),
                        color = TextDim, style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { Playback.play(album.tracks) },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                            modifier = Modifier.width(140.dp)
                        ) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play") }
                        OutlinedButton(onClick = { Playback.play(album.tracks, shuffle = true) }, modifier = Modifier.width(140.dp)) {
                            Icon(Icons.Filled.Shuffle, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Shuffle", color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Genre tags and saved sound
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        genres.forEach { g ->
                            AssistChip(
                                onClick = { Nav.go(Screen.GenrePage(g)) },
                                label = { Text(g) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = accent.copy(alpha = 0.18f), labelColor = Color.White),
                                border = null,
                            )
                        }
                        AssistChip(
                            onClick = { tagging = true },
                            label = { Text(if (genres.isEmpty()) "Add genre" else "Edit") },
                            leadingIcon = { Icon(Icons.Filled.Sell, null, Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = TextDim, leadingIconContentColor = TextDim),
                        )
                        AssistChip(
                            onClick = { Nav.go(Screen.Equalizer(AudioFx.albumKey(album.id))) },
                            label = { Text(if (hasSound) "Album sound: on" else "Album sound") },
                            leadingIcon = { Icon(Icons.Filled.Equalizer, null, Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = if (hasSound) accent else TextDim,
                                leadingIconContentColor = if (hasSound) accent else TextDim
                            ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            val multiDisc = album.discCount > 1
            itemsIndexed(album.tracks, key = { _, t -> t.id }) { i, t ->
                if (multiDisc && (i == 0 || album.tracks[i - 1].discNo != t.discNo)) {
                    Text(
                        "DISC ${t.discNo}", style = MaterialTheme.typography.labelMedium, color = accent,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)
                    )
                }
                TrackRow(
                    t,
                    onClick = { Playback.play(album.tracks, i) },
                    number = "${t.trackNo.takeIf { it > 0 } ?: (i + 1)}",
                    subtitle = if (album.artist == "Various Artists" || t.artist != album.artist) t.artist else null,
                    accent = accent,
                )
            }
            item {
                Text(
                    "${album.tracks.size} songs, ${formatLongDuration(album.durationMs)}",
                    color = TextFaint, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
    if (tagging) GenreDialog(album.id) { tagging = false }
}
