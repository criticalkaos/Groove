package app.groove.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Everything Groove knows about the music on the phone, limited to the folders the user picked. */
object Library {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _folders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val folders: StateFlow<List<MusicFolder>> = _folders.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var allTracks: List<Track> = emptyList()
    private var byId: Map<Long, Track> = emptyMap()
    private var albumById: Map<Long, Album> = emptyMap()

    fun track(id: Long): Track? = byId[id]
    fun track(mediaId: String?): Track? = mediaId?.toLongOrNull()?.let { byId[it] }
    fun album(id: Long): Album? = albumById[id]
    fun artist(name: String): Artist? = _artists.value.firstOrNull { it.name == name }

    suspend fun scan(context: Context) {
        _scanning.value = true
        try {
            allTracks = withContext(Dispatchers.IO) { queryMediaStore(context) }
            // Every folder that holds music, plus its parent folders, so a whole "Music" folder can be ticked.
            val counts = HashMap<String, Int>()
            for (t in allTracks) {
                val parts = t.folder.trimEnd('/').split('/')
                val root = (if (parts.getOrNull(2) == "emulated") 4 else 3).coerceAtMost(parts.size - 1)
                for (k in root + 1..parts.size) {
                    val p = parts.take(k).joinToString("/")
                    counts[p] = (counts[p] ?: 0) + 1
                }
            }
            _folders.value = counts.map { (path, n) -> MusicFolder(path, n) }.sortedBy { it.path.lowercase() }
            applyFolders(UserStore.selectedFolders.value)
            _loaded.value = true
        } finally {
            _scanning.value = false
        }
    }

    /** Rebuild albums/artists from the tracks inside the chosen folders (subfolders included). */
    fun applyFolders(selected: Set<String>) {
        val chosen = allTracks.filter { t ->
            selected.any { f -> t.folder == f || t.folder.startsWith("$f/") }
        }
        _tracks.value = chosen.sortedBy { it.title.lowercase() }
        byId = chosen.associateBy { it.id }

        val albumList = chosen.groupBy { it.albumId }.map { (id, list) ->
            val sorted = list.sortedWith(compareBy<Track>({ it.discNo }, { it.trackNo }, { it.title.lowercase() }))
            val artists = list.map { it.albumArtist.ifBlank { it.artist } }.distinct()
            Album(
                id = id,
                title = list.first().album,
                artist = if (artists.size == 1) artists.first() else "Various Artists",
                year = list.maxOf { it.year },
                tracks = sorted,
                dateAdded = list.maxOf { it.dateAdded },
            )
        }.sortedWith(compareBy({ sortKey(it.artist) }, { it.year }, { it.title.lowercase() }))
        _albums.value = albumList
        albumById = albumList.associateBy { it.id }

        _artists.value = albumList.groupBy { it.artist }
            .map { (name, al) -> Artist(name, al.sortedBy { it.year }) }
            .sortedBy { sortKey(it.name) }
    }

    fun sortKey(s: String): String {
        val l = s.lowercase().trim()
        return if (l.startsWith("the ")) l.removePrefix("the ") else l
    }

    private fun queryMediaStore(context: Context): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            "album_artist",
        )
        val out = ArrayList<Track>()
        val cursor = try {
            context.contentResolver.query(
                collection, projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 30000",
                null, null
            )
        } catch (e: Exception) {
            // Some older devices don't have the album_artist column.
            context.contentResolver.query(
                collection, projection.copyOf(projection.size - 1).requireNoNulls(),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
            )
        }
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumArtistCol = c.getColumnIndex("album_artist")
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = c.getString(dataCol) ?: continue
                val ext = path.substringAfterLast('.', "").lowercase()
                if (ext !in SUPPORTED) continue
                val rawTrack = c.getInt(trackCol)
                val artist = c.getString(artistCol).clean("Unknown Artist")
                out += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(titleCol).clean(File(path).nameWithoutExtension),
                    artist = artist,
                    album = c.getString(albumCol).clean("Unknown Album"),
                    albumArtist = if (albumArtistCol >= 0) c.getString(albumArtistCol).clean("") else "",
                    albumId = c.getLong(albumIdCol),
                    trackNo = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack,
                    discNo = if (rawTrack >= 1000) rawTrack / 1000 else 1,
                    year = c.getInt(yearCol),
                    durationMs = c.getLong(durCol),
                    path = path,
                    folder = path.substringBeforeLast('/'),
                    dateAdded = c.getLong(addedCol),
                )
            }
        }
        return out
    }

    private fun String?.clean(fallback: String): String {
        val s = this?.trim()
        return if (s.isNullOrEmpty() || s == "<unknown>") fallback else s
    }

    private val SUPPORTED = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav")
}
