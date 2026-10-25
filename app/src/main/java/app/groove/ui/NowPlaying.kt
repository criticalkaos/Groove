package app.groove.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.groove.data.Library
import app.groove.data.Lyrics
import app.groove.data.LyricsRepo
import app.groove.data.Track
import app.groove.data.UserStore
import app.groove.data.formatDuration
import app.groove.playback.AudioFx
import app.groove.playback.Playback
import app.groove.playback.SleepTimer
import kotlinx.coroutines.delay

@Composable
private fun rememberPosition(): Long {
    val st = Playback.state
    var pos by remember { mutableLongStateOf(Playback.position()) }
    LaunchedEffect(st.currentId, st.isPlaying) {
        while (true) {
            pos = Playback.position()
            if (!st.isPlaying) break
            delay(250)
        }
    }
    return pos
}

@Composable
fun NowPlayingScreen() {
    val st = Playback.state
    val track = Library.track(st.currentId)
    val album = track?.let { Library.album(it.albumId) }
    val accent = rememberAccent(album)
    val favs by UserStore.favourites.collectAsState()
    val pos = rememberPosition()
    var dragging by remember { mutableStateOf<Float?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(GrooveBlack)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f), GrooveBlack)))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Nav.back() }) { Icon(Icons.Filled.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", style = MaterialTheme.typography.labelMedium, color = TextDim)
                    Text(
                        album?.title ?: "", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = album != null) { album?.let { Nav.back(); Nav.go(Screen.AlbumPage(it.id)) } }
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = track != null) { Icon(Icons.Filled.MoreVert, "More") }
                    if (track != null) {
                        DropdownMenu(menu, { menu = false }) {
                            TrackMenuItems(track) { menu = false }
                        }
                    }
                }
            }

            if (track == null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Nothing playing", color = TextDim)
                }
                return@Column
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Crossfade(showLyrics, label = "lyrics") { lyr ->
                    if (lyr) {
                        LyricsView(track, pos, accent)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AlbumArt(album, Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f), corner = 14.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artist, style = MaterialTheme.typography.titleMedium, color = TextDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { Nav.back(); Nav.go(Screen.ArtistPage(album?.artist ?: track.artist)) }
                    )
                }
                IconButton(onClick = { UserStore.toggleFavourite(track.id) }) {
                    Icon(
                        if (track.id in favs) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favourite",
                        tint = if (track.id in favs) accent else Color.White, modifier = Modifier.size(28.dp)
                    )
                }
            }

            SoundBadge(accent)
            Spacer(Modifier.height(8.dp))
            val dur = st.durationMs.coerceAtLeast(1)
            Slider(
                value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { Playback.seekTo((it * dur).toLong()) }; dragging = null },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth()) {
                val shown = dragging?.let { (it * dur).toLong() } ?: pos
                Text(formatDuration(shown), color = TextDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(formatDuration(st.durationMs), color = TextDim, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Playback.setShuffle(!st.shuffle) }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (st.shuffle) accent else TextDim)
                }
                IconButton(onClick = { Playback.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(38.dp))
                }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Color.White).clickable { Playback.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (st.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause",
                        tint = Color.Black, modifier = Modifier.size(42.dp)
                    )
                }
                IconButton(onClick = { Playback.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", Modifier.size(38.dp))
                }
                IconButton(onClick = { Playback.cycleRepeat() }) {
                    Icon(
                        if (st.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat",
                        tint = if (st.repeatMode != Player.REPEAT_MODE_OFF) accent else TextDim
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                SmallAction(Icons.Filled.Speed, if (st.speed == 1f) "Speed" else "${trimSpeed(st.speed)}×", st.speed != 1f, accent) { speedDialog = true }
                SmallAction(Icons.Filled.Bedtime, sleepLabel(), SleepTimer.active(), accent) { sleepDialog = true }
                SmallAction(Icons.Filled.Lyrics, "Lyrics", showLyrics, accent) { showLyrics = !showLyrics }
                SmallAction(Icons.AutoMirrored.Filled.QueueMusic, "Queue", false, accent) { Nav.go(Screen.Queue) }
                SmallAction(Icons.Filled.Equalizer, "Sound", AudioFx.activeKey != null || AudioFx.current.boostDb > 0, accent) {
                    Nav.back(); Nav.go(Screen.Equalizer(AudioFx.activeKey))
                }
            }
        }
    }

    if (speedDialog) SpeedDialog(st.speed) { speedDialog = false }
    if (sleepDialog) SleepDialog { sleepDialog = false }
}

private fun trimSpeed(s: Float): String = if (s % 1f == 0f) s.toInt().toString() else s.toString().trimEnd('0')

@Composable
private fun sleepLabel(): String {
    // Recompose every 30s so the countdown stays roughly current.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(SleepTimer.endsAt) {
        while (SleepTimer.endsAt > 0) { delay(30_000); tick++ }
    }
    tick.let { }
    return when {
        SleepTimer.endOfTrackMode -> "End of song"
        SleepTimer.endsAt > System.currentTimeMillis() ->
            "${((SleepTimer.endsAt - System.currentTimeMillis()) / 60_000 + 1)} min"
        else -> "Sleep"
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (on) accent else Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = if (on) accent else TextDim, maxLines = 1)
    }
}

@Composable
private fun SpeedDialog(current: Float, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GrooveCard,
        title = { Text("Playback speed") },
        text = {
            Column {
                listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 2f).forEach { s ->
                    Text(
                        if (s == 1f) "Normal" else "${trimSpeed(s)}×",
                        color = if (s == current) GrooveAccent else Color.White,
                        fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { Playback.setSpeed(s); onDismiss() }.padding(vertical = 10.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SleepDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GrooveCard,
        title = { Text("Sleep timer") },
        text = {
            Column {
                val opts = listOf<Pair<String, () -> Unit>>(
                    "Off" to { SleepTimer.cancel() },
                    "End of this song" to { SleepTimer.endOfTrack() },
                    "15 minutes" to { SleepTimer.start(15) },
                    "30 minutes" to { SleepTimer.start(30) },
                    "45 minutes" to { SleepTimer.start(45) },
                    "1 hour" to { SleepTimer.start(60) },
                    "1½ hours" to { SleepTimer.start(90) },
                )
                opts.forEach { (label, act) ->
                    Text(
                        label, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { act(); onDismiss() }.padding(vertical = 10.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun LyricsView(track: Track, pos: Long, accent: Color) {
    val context = LocalContext.current
    val result by produceState<Pair<Boolean, Lyrics?>>(false to null, track.id) {
        value = true to LyricsRepo.find(context, track)
    }
    val (done, lyrics) = result
    when {
        !done -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
        lyrics == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No lyrics found for this song.\n\nTip: put a .lrc file with the same name next to the song" +
                    if (!UserStore.onlineLyrics.value) ", or turn on online lyrics in Sound settings." else ".",
                color = TextDim, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp)
            )
        }
        else -> {
            val list = rememberLazyListState()
            val current = if (lyrics.synced) lyrics.lines.indexOfLast { it.timeMs <= pos + 300 } else -1
            LaunchedEffect(current) {
                if (current >= 0) list.animateScrollToItem((current - 2).coerceAtLeast(0))
            }
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.25f)),
                contentPadding = PaddingValues(vertical = 24.dp, horizontal = 20.dp)
            ) {
                itemsIndexed(lyrics.lines) { i, line ->
                    val active = i == current
                    Text(
                        line.text.ifBlank { "♪" },
                        fontSize = if (lyrics.synced) 22.sp else 18.sp,
                        lineHeight = if (lyrics.synced) 30.sp else 26.sp,
                        fontWeight = if (lyrics.synced) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            !lyrics.synced -> Color.White
                            active -> Color.White
                            i < current -> Color.White.copy(alpha = 0.45f)
                            else -> Color.White.copy(alpha = 0.3f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (lyrics.synced) Modifier.clickable { Playback.seekTo(line.timeMs) } else Modifier)
                            .padding(vertical = 6.dp)
                    )
                }
                item {
                    Text(
                        "Lyrics: ${lyrics.source}" + if (lyrics.source == "Online") " (lrclib.net)" else "",
                        color = TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Queue

@Composable
fun QueueScreen() {
    val st = Playback.state
    val tracks = st.queue.map { Library.track(it) }
    Column(Modifier.fillMaxSize().background(GrooveBlack).pointerInput(Unit) { detectTapGestures { } }) {
        BackTopBar("Queue") {
            TextButton(onClick = { Playback.clearUpcoming() }) { Text("Clear upcoming") }
        }
        if (st.shuffle) {
            Text(
                "Shuffle is on, so songs play in a random order.",
                color = TextDim, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        val list = rememberLazyListState(initialFirstVisibleItemIndex = (st.currentIndex - 1).coerceAtLeast(0))
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            itemsIndexed(tracks, key = { i, t -> "$i-${t?.id}" }) { i, t ->
                val current = i == st.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) GrooveCard else Color.Transparent)
                        .clickable { Playback.skipTo(i) }
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArt(t?.let { Library.album(it.albumId) }, Modifier.size(44.dp), corner = 6.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t?.title ?: "Unknown", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (current) GrooveAccent else if (i < st.currentIndex) TextFaint else Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(t?.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (current) {
                        Icon(Icons.Filled.GraphicEq, null, tint = GrooveAccent, modifier = Modifier.padding(end = 8.dp))
                    } else {
                        IconButton(onClick = { Playback.move(i, i - 1) }, enabled = i > 0) {
                            Icon(Icons.Filled.ArrowUpward, "Move up", tint = TextDim)
                        }
                        IconButton(onClick = { Playback.move(i, i + 1) }, enabled = i < tracks.lastIndex) {
                            Icon(Icons.Filled.ArrowDownward, "Move down", tint = TextDim)
                        }
                        IconButton(onClick = { Playback.remove(i) }) {
                            Icon(Icons.Filled.Close, "Remove", tint = TextDim)
                        }
                    }
                }
            }
        }
    }
}


/** Shows which saved sound is playing (album or genre) and any volume boost. */
@Composable
private fun SoundBadge(accent: Color) {
    val key = AudioFx.activeKey
    val boost = AudioFx.current.boostDb
    if (key == null && boost == 0) return
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.2f))
                .clickable { menu = true }
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Equalizer, null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                listOfNotNull(key?.let { AudioFx.label(it) }, boost.takeIf { it > 0 }?.let { "Boost +$it dB" }).joinToString(" · "),
                fontSize = 12.sp, color = Color.White
            )
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(
                text = { Text("Edit ${AudioFx.label(key).lowercase()}") },
                onClick = { menu = false; Nav.back(); Nav.go(Screen.Equalizer(key)) })
            if (key != null) {
                DropdownMenuItem(
                    text = { Text("Use normal sound for this album") },
                    onClick = { menu = false; AudioFx.useNormalForNow() })
            }
        }
    }
}
