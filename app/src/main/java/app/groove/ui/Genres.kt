package app.groove.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.groove.data.Library
import app.groove.data.UserStore
import app.groove.playback.AudioFx
import app.groove.playback.Playback

/** Every genre you've tagged, with a peek at its covers. */
@Composable
fun GenresScreen() {
    val genreMap by UserStore.albumGenres.collectAsState()
    Library.albums.collectAsState().value
    val genres = UserStore.genresInUse()
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Genres")
        if (genres.isEmpty()) {
            EmptyState(
                "No genres yet",
                "Open any album and tap \"Add genre\" to tag it. Tagged genres appear here and as filters in the Jukebox."
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(genres, key = { it }) { g ->
                val albums = genreMap.filterValues { g in it }.keys.mapNotNull { Library.album(it) }
                val hasSound = AudioFx.profiles.containsKey(AudioFx.genreKey(g))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { Nav.go(Screen.GenrePage(g)) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (hasSound) {
                            Icon(Icons.Filled.Equalizer, "Has saved sound", tint = GrooveAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("${albums.size} album${if (albums.size == 1) "" else "s"}", color = TextDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        albums.take(6).forEach { a -> AlbumArt(a, Modifier.size(52.dp), corner = 4.dp) }
                    }
                }
            }
        }
    }
}

/** One genre: its albums, play buttons and saved sound. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreScreen(name: String) {
    val genreMap by UserStore.albumGenres.collectAsState()
    Library.albums.collectAsState().value
    val albums = genreMap.filterValues { name in it }.keys.mapNotNull { Library.album(it) }
        .sortedWith(compareBy({ Library.sortKey(it.artist) }, { it.year }))
    val tracks = albums.flatMap { it.tracks }
    val hasSound = AudioFx.profiles.containsKey(AudioFx.genreKey(name))

    Column(Modifier.fillMaxSize()) {
        BackTopBar(name)
        AlbumGrid(albums) {
            Column {
                Text(
                    "${albums.size} album${if (albums.size == 1) "" else "s"} · ${tracks.size} songs",
                    color = TextDim, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { Playback.play(tracks) }, enabled = tracks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = GrooveAccent, contentColor = Color.Black)
                    ) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play all") }
                    OutlinedButton(onClick = { Playback.play(tracks, shuffle = true) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Filled.Shuffle, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Shuffle", color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { Nav.go(Screen.Equalizer(AudioFx.genreKey(name))) },
                        label = { Text(if (hasSound) "$name sound: on" else "$name sound") },
                        leadingIcon = { Icon(Icons.Filled.Equalizer, null, Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = if (hasSound) GrooveAccent else TextDim,
                            leadingIconContentColor = if (hasSound) GrooveAccent else TextDim
                        ),
                    )
                    AssistChip(
                        onClick = { UserStore.setFilter(name); Nav.tab = 0; Nav.home() },
                        label = { Text("Show in Jukebox") },
                        leadingIcon = { Icon(Icons.Filled.Album, null, Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = TextDim, leadingIconContentColor = TextDim),
                    )
                }
            }
        }
    }
}
