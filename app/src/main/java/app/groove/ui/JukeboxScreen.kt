package app.groove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.groove.data.Album
import app.groove.data.Library
import app.groove.data.UserStore
import app.groove.data.formatLongDuration
import app.groove.playback.Playback
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

@Composable
fun JukeboxScreen() {
    val all by Library.albums.collectAsState()
    val folders by UserStore.selectedFolders.collectAsState()
    val loaded by Library.loaded.collectAsState()
    val scanning by Library.scanning.collectAsState()
    val sort by UserStore.albumSort.collectAsState()
    val filter by UserStore.filter.collectAsState()
    val albums = rememberJukeboxAlbums()

    Column(Modifier.fillMaxSize()) {
        JukeboxTopBar(sort)
        when {
            folders.isEmpty() -> EmptyState(
                "Pick your music folders",
                "Choose which folders on your phone Groove should play from.",
                "Choose folders"
            ) { Nav.go(Screen.Folders) }
            !loaded || (scanning && all.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GrooveAccent)
            }
            all.isEmpty() -> EmptyState(
                "No music found",
                "There are no songs in the folders you picked.",
                "Change folders"
            ) { Nav.go(Screen.Folders) }
            else -> {
                FilterChips()
                if (albums.isEmpty()) {
                    EmptyState(
                        if (filter == UserStore.FAV_FILTER) "No favourite albums yet" else "No albums here",
                        if (filter == UserStore.FAV_FILTER) "Tap the heart on any album to add it." else "Tag albums with this genre from their album page.",
                        "Show all albums"
                    ) { UserStore.setFilter(null) }
                } else {
                    key(filter, sort) { CoverFlow(albums, sort) }
                }
            }
        }
    }
}

/** All albums in the chosen sort order, narrowed by the current filter chip. */
@Composable
fun rememberJukeboxAlbums(): List<Album> {
    val all by Library.albums.collectAsState()
    val sort by UserStore.albumSort.collectAsState()
    val filter by UserStore.filter.collectAsState()
    val favAlbums by UserStore.favAlbums.collectAsState()
    val genres by UserStore.albumGenres.collectAsState()
    return remember(all, sort, filter, favAlbums, genres) {
        val sorted = when (sort) {
            "title" -> all.sortedBy { Library.sortKey(it.title) }
            "year" -> all.sortedWith(compareByDescending<Album> { it.year }.thenBy { Library.sortKey(it.artist) })
            "added" -> all.sortedByDescending { it.dateAdded }
            else -> all
        }
        when (filter) {
            null -> sorted
            UserStore.FAV_FILTER -> sorted.filter { it.id in favAlbums }
            else -> sorted.filter { a -> genres[a.id].orEmpty().any { it == filter } }
        }
    }
}

/** All · ★ Favourites · genres in use. */
@Composable
private fun FilterChips() {
    val filter by UserStore.filter.collectAsState()
    val genres by UserStore.albumGenres.collectAsState()
    val inUse = remember(genres) { UserStore.genresInUse() }
    val options = listOf<Pair<String?, String>>(null to "All", UserStore.FAV_FILTER to "\u2605 Favourites") + inUse.map { it to it }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        items(options, key = { it.first ?: "all" }) { (value, label) ->
            FilterChip(
                selected = filter == value,
                onClick = { UserStore.setFilter(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GrooveAccent, selectedLabelColor = Color.Black,
                    containerColor = GrooveCard, labelColor = Color.White
                ),
                border = null,
            )
        }
    }
}

@Composable
private fun JukeboxTopBar(sort: String) {
    var sortMenu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Groove", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { Nav.go(Screen.Search) }) { Icon(Icons.Filled.Search, "Search") }
        IconButton(onClick = { Nav.go(Screen.Wall) }) { Icon(Icons.Filled.GridView, "Cover wall") }
        Box {
            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
            DropdownMenu(sortMenu, { sortMenu = false }) {
                listOf("artist" to "Artist", "title" to "Album title", "year" to "Year (newest)", "added" to "Recently added").forEach { (k, l) ->
                    DropdownMenuItem(
                        text = { Text(l, fontWeight = if (k == sort) FontWeight.Bold else FontWeight.Normal, color = if (k == sort) GrooveAccent else Color.White) },
                        onClick = { UserStore.setAlbumSort(k); sortMenu = false })
                }
            }
        }
        Box {
            IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(more, { more = false }) {
                DropdownMenuItem(text = { Text("Music folders") }, leadingIcon = { Icon(Icons.Filled.Folder, null) },
                    onClick = { more = false; Nav.go(Screen.Folders) })
                DropdownMenuItem(text = { Text("Genres") }, leadingIcon = { Icon(Icons.Filled.Sell, null) },
                    onClick = { more = false; Nav.go(Screen.Genres) })
                DropdownMenuItem(text = { Text("Sound & settings") }, leadingIcon = { Icon(Icons.Filled.Equalizer, null) },
                    onClick = { more = false; Nav.go(Screen.Equalizer()) })
            }
        }
    }
}

/** The jukebox: a 3D carousel of covers, with the centred album's details and tracks below. */
@Composable
private fun CoverFlow(albums: List<Album>, sort: String) {
    val pager = rememberPagerState(pageCount = { albums.size })
    val scope = rememberCoroutineScope()
    val focused = albums.getOrNull(pager.currentPage.coerceIn(0, albums.lastIndex))
    val accent = rememberAccent(focused)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(520.dp).background(
                Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.08f), Color.Transparent))
            )
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    val cover = min(maxWidth.value * 0.62f, 300f).dp
                    val side = (maxWidth - cover) / 2
                    HorizontalPager(
                        state = pager,
                        pageSize = PageSize.Fixed(cover),
                        contentPadding = PaddingValues(horizontal = side),
                        beyondViewportPageCount = 3,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val album = albums[page]
                        Column(
                            Modifier.graphicsLayer {
                                val off = pager.getOffsetDistanceInPages(page).coerceIn(-3f, 3f)
                                val a = abs(off).coerceAtMost(1f)
                                rotationY = -off.coerceIn(-1f, 1f) * 55f
                                val s = 1f - 0.22f * a
                                scaleX = s
                                scaleY = s
                                translationX = -off * size.width * 0.28f
                                alpha = 1f - 0.45f * (abs(off) / 2f).coerceAtMost(1f)
                                cameraDistance = 16f * density
                            }
                        ) {
                            AlbumArt(
                                album,
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable {
                                        if (page == pager.currentPage) Nav.go(Screen.AlbumPage(album.id))
                                        else scope.launch { pager.animateScrollToPage(page) }
                                    },
                                corner = 8.dp
                            )
                            // Glossy floor reflection.
                            Box(Modifier.fillMaxWidth().height(cover * 0.28f).clipToBounds()) {
                                AlbumArt(
                                    album,
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .graphicsLayer { scaleY = -1f; alpha = 0.22f; translationY = 4f },
                                    corner = 8.dp
                                )
                                Box(
                                    Modifier.matchParentSize().background(
                                        Brush.verticalGradient(listOf(Color.Transparent, GrooveBlack.copy(alpha = 0.6f), GrooveBlack))
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (focused != null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            focused.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            focused.artist, style = MaterialTheme.typography.titleMedium, color = accent,
                            modifier = Modifier.clickable { Nav.go(Screen.ArtistPage(focused.artist)) },
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(
                                focused.year.takeIf { it > 0 }?.toString(),
                                "${focused.tracks.size} songs",
                                formatLongDuration(focused.durationMs)
                            ).joinToString("  ·  "),
                            color = TextDim, style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { Playback.play(focused.tracks) },
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                            ) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play") }
                            OutlinedButton(onClick = { Playback.play(focused.tracks, shuffle = true) }) {
                                Icon(Icons.Filled.Shuffle, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Shuffle", color = Color.White)
                            }
                            AlbumHeart(focused.id, accent)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                item { LetterStrip(albums, sort) { idx -> scope.launch { pager.animateScrollToPage(idx) } } }

                item {
                    Text(
                        "SELECTIONS", style = MaterialTheme.typography.labelMedium, color = TextFaint,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(focused.tracks, key = { _, t -> "jt${t.id}" }) { i, t ->
                    TrackRow(
                        t,
                        onClick = { Playback.play(focused.tracks, i) },
                        number = jukeboxCode(focused, i),
                        subtitle = if (focused.artist == "Various Artists") t.artist else null,
                        accent = accent,
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}


/** Jukebox-style selection code: disc letter + track number (A1, A2 … B1). */
fun jukeboxCode(album: Album, index: Int): String {
    val t = album.tracks[index]
    return if (album.discCount > 1) "${'A' + (t.discNo - 1).coerceIn(0, 25)}${t.trackNo.takeIf { it > 0 } ?: (index + 1)}"
    else "${t.trackNo.takeIf { it > 0 } ?: (index + 1)}"
}

/** A–Z strip for jumping through the jukebox quickly. */
@Composable
private fun LetterStrip(albums: List<Album>, sort: String, onJump: (Int) -> Unit) {
    if (sort != "artist" && sort != "title") return
    val letters = remember(albums, sort) {
        val m = linkedMapOf<Char, Int>()
        albums.forEachIndexed { i, a ->
            val key = Library.sortKey(if (sort == "title") a.title else a.artist)
            val c = key.firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) it else '#' } ?: '#'
            if (c !in m) m[c] = i
        }
        m.toList()
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        state = rememberLazyListState()
    ) {
        items(letters, key = { it.first }) { (c, idx) ->
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(GrooveCard)
                    .clickable { onJump(idx) },
                contentAlignment = Alignment.Center
            ) { Text(c.toString(), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun AlbumGrid(albums: List<Album>, header: (@Composable () -> Unit)? = null) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (header != null) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        }
        items(albums, key = { it.id }) { a -> AlbumCard(a) }
    }
}

@Composable
fun AlbumCard(a: Album, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable { Nav.go(Screen.AlbumPage(a.id)) }) {
        AlbumArt(a, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(a.artist, a.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
            maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextDim, style = MaterialTheme.typography.bodyMedium
        )
    }
}
