package app.groove.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.groove.data.Library
import app.groove.data.Track
import app.groove.data.UserStore
import app.groove.playback.Playback
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data class AlbumPage(val id: Long) : Screen
    data class ArtistPage(val name: String) : Screen
    data class PlaylistPage(val id: Long) : Screen // FAVOURITES_ID = favourites
    data object Search : Screen
    data object NowPlaying : Screen
    data object Queue : Screen
    data class Equalizer(val target: String? = null) : Screen // null = normal sound
    data object Genres : Screen
    data class GenrePage(val name: String) : Screen
    data object Wall : Screen
    data object Folders : Screen
}

const val FAVOURITES_ID = -1L

/** Simple screen stack. */
object Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var tab by mutableIntStateOf(0)
    var addToPlaylist by mutableStateOf<List<Track>?>(null)
    val top: Screen get() = stack.last()

    fun go(s: Screen) {
        if (stack.last() != s) stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Playback.connect(applicationContext)
        setContent { GrooveTheme { GrooveRoot() } }
    }

    override fun onDestroy() {
        if (isFinishing) Playback.disconnect()
        super.onDestroy()
    }
}

private val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun GrooveRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res[audioPermission] == true
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            val perms = mutableListOf(audioPermission)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            launcher.launch(perms.toTypedArray())
        }
    }
    LaunchedEffect(granted) {
        if (granted) Library.scan(context)
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Toaster.message) {
        val m = Toaster.message ?: return@LaunchedEffect
        Toaster.message = null
        snackbar.showSnackbar(m)
    }

    BackHandler(enabled = Nav.stack.size > 1) { Nav.back() }

    Box(Modifier.fillMaxSize().background(GrooveBlack)) {
        if (!granted) {
            EmptyState(
                "Let Groove see your music",
                "Groove needs permission to read the music files on your phone. Nothing leaves your device.",
                "Allow access"
            ) { launcher.launch(arrayOf(audioPermission)) }
            return@Box
        }

        val top = Nav.top
        val overlay = top is Screen.NowPlaying || top is Screen.Queue || top is Screen.Wall
        // Base layer: the browsing screens, with mini player and tabs.
        val base = Nav.stack.lastOrNull { it !is Screen.NowPlaying && it !is Screen.Queue && it !is Screen.Wall } ?: Screen.Home
        Scaffold(
            containerColor = GrooveBlack,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column(Modifier.background(GrooveBlack).navigationBarsPadding()) {
                    MiniPlayer { Nav.go(Screen.NowPlaying) }
                    if (base is Screen.Home) BottomTabs()
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                AnimatedContent(
                    targetState = base,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screens"
                ) { s ->
                    when (s) {
                        is Screen.Home -> when (Nav.tab) {
                            0 -> JukeboxScreen()
                            1 -> ArtistsScreen()
                            2 -> SongsScreen()
                            else -> CollectionScreen()
                        }
                        is Screen.AlbumPage -> AlbumScreen(s.id)
                        is Screen.ArtistPage -> ArtistScreen(s.name)
                        is Screen.PlaylistPage -> PlaylistScreen(s.id)
                        is Screen.Search -> SearchScreen()
                        is Screen.Equalizer -> EqualizerScreen(s.target)
                        is Screen.Genres -> GenresScreen()
                        is Screen.GenrePage -> GenreScreen(s.name)
                        is Screen.Folders -> FoldersScreen()
                        else -> {}
                    }
                }
            }
        }

        AnimatedContent(
            targetState = if (overlay) top else null,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
            },
            label = "overlay"
        ) { s ->
            when (s) {
                is Screen.NowPlaying -> NowPlayingScreen()
                is Screen.Queue -> QueueScreen()
                is Screen.Wall -> CoverWall()
                else -> Box(Modifier)
            }
        }

        Nav.addToPlaylist?.let { tracks ->
            AddToPlaylistDialog(tracks) { Nav.addToPlaylist = null }
        }
    }
}

@Composable
private fun BottomTabs() {
    val items = listOf(
        Triple("Jukebox", Icons.Filled.Album, 0),
        Triple("Artists", Icons.Filled.Person, 1),
        Triple("Songs", Icons.Filled.MusicNote, 2),
        Triple("Collection", Icons.Filled.LibraryMusic, 3),
    )
    NavigationBar(containerColor = GrooveBlack, tonalElevation = androidx.compose.ui.unit.Dp(0f), windowInsets = WindowInsets(0)) {
        items.forEach { (label, icon, i) ->
            NavigationBarItem(
                selected = Nav.tab == i,
                onClick = { Nav.tab = i },
                icon = { Icon(icon, label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    indicatorColor = GrooveAccent,
                    unselectedIconColor = TextDim,
                    unselectedTextColor = TextDim,
                    selectedTextColor = Color.White,
                )
            )
        }
    }
}
