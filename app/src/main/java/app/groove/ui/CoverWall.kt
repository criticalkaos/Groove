package app.groove.ui

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.groove.data.Album
import app.groove.data.UserStore
import app.groove.playback.Playback

/** Full-screen wall of album covers — nothing else on screen. Swipe back to leave. */
@Composable
fun CoverWall() {
    val albums = rememberJukeboxAlbums()
    val columns by UserStore.wallColumns.collectAsState()
    val view = LocalView.current

    // Hide the status and navigation bars while the wall is open.
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(GrooveBlack)
            .pointerInput(Unit) {
                // Pinch to change how many covers fit across.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoom = 1f
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            zoom *= event.calculateZoom()
                            val cols = UserStore.wallColumns.value
                            if (zoom > 1.3f) {
                                UserStore.setWallColumns((cols - 1).coerceAtLeast(3)); zoom = 1f
                            } else if (zoom < 0.77f) {
                                UserStore.setWallColumns((cols + 1).coerceAtMost(6)); zoom = 1f
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        if (albums.isEmpty()) {
            Text(
                "No albums to show", color = TextDim,
                modifier = Modifier.padding(32.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(albums, key = { it.id }) { a -> WallCover(a) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallCover(album: Album) {
    var menu by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    val favAlbums by UserStore.favAlbums.collectAsState()
    Box {
        AlbumArt(
            album,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = { Nav.go(Screen.AlbumPage(album.id)) },
                    onLongClick = { menu = true }
                ),
            corner = 2.dp
        )
        DropdownMenu(menu, { menu = false }) {
            Text(
                album.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                album.artist, color = TextDim, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            DropdownMenuItem(text = { Text("Play") }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                onClick = { Playback.play(album.tracks); menu = false })
            DropdownMenuItem(text = { Text("Shuffle") }, leadingIcon = { Icon(Icons.Filled.Shuffle, null) },
                onClick = { Playback.play(album.tracks, shuffle = true); menu = false })
            val fav = album.id in favAlbums
            DropdownMenuItem(
                text = { Text(if (fav) "Remove from favourites" else "Add to favourites") },
                leadingIcon = { Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null) },
                onClick = { UserStore.toggleFavAlbum(album.id); menu = false })
            DropdownMenuItem(text = { Text("Genres…") }, leadingIcon = { Icon(Icons.Filled.Sell, null) },
                onClick = { menu = false; tagging = true })
        }
    }
    if (tagging) GenreDialog(album.id) { tagging = false }
}
