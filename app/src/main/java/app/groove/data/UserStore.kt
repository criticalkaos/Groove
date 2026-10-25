package app.groove.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Favourites, playlists, recently played, chosen folders and settings — all saved on the phone. */
object UserStore {
    private lateinit var prefs: SharedPreferences

    /** Liked songs, newest first. */
    private val _favourites = MutableStateFlow<List<Long>>(emptyList())
    val favourites: StateFlow<List<Long>> = _favourites.asStateFlow()

    /** Favourite albums, newest first. */
    private val _favAlbums = MutableStateFlow<List<Long>>(emptyList())
    val favAlbums: StateFlow<List<Long>> = _favAlbums.asStateFlow()

    /** The user's own genre tags for each album. */
    private val _albumGenres = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val albumGenres: StateFlow<Map<Long, List<String>>> = _albumGenres.asStateFlow()

    /** Jukebox filter: null = all albums, FAV_FILTER = favourite albums, otherwise a genre. */
    private val _filter = MutableStateFlow<String?>(null)
    val filter: StateFlow<String?> = _filter.asStateFlow()

    private val _wallColumns = MutableStateFlow(4)
    val wallColumns: StateFlow<Int> = _wallColumns.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _recentAlbums = MutableStateFlow<List<Long>>(emptyList())
    val recentAlbums: StateFlow<List<Long>> = _recentAlbums.asStateFlow()

    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolders: StateFlow<Set<String>> = _selectedFolders.asStateFlow()

    private val _onlineLyrics = MutableStateFlow(true)
    val onlineLyrics: StateFlow<Boolean> = _onlineLyrics.asStateFlow()

    private val _gridMode = MutableStateFlow(false)
    val gridMode: StateFlow<Boolean> = _gridMode.asStateFlow()

    private val _albumSort = MutableStateFlow("artist")
    val albumSort: StateFlow<String> = _albumSort.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("groove", Context.MODE_PRIVATE)
        _favourites.value = prefs.getString("favourites", "[]")!!.toLongList().distinct()
        _favAlbums.value = prefs.getString("favAlbums", "[]")!!.toLongList().distinct()
        _wallColumns.value = prefs.getInt("wallColumns", 4)
        _albumGenres.value = try {
            val o = JSONObject(prefs.getString("albumGenres", "{}")!!)
            o.keys().asSequence().associate { k ->
                val arr = o.getJSONArray(k)
                k.toLong() to (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (e: Exception) {
            emptyMap()
        }
        _recentAlbums.value = prefs.getString("recent", "[]")!!.toLongList()
        _selectedFolders.value = prefs.getStringSet("folders", emptySet())!!.toSet()
        _onlineLyrics.value = prefs.getBoolean("onlineLyrics", true)
        _gridMode.value = prefs.getBoolean("gridMode", false)
        _albumSort.value = prefs.getString("albumSort", "artist")!!
        _playlists.value = try {
            val arr = JSONArray(prefs.getString("playlists", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Playlist(o.getLong("id"), o.getString("name"), o.getJSONArray("tracks").toLongList())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---- favourites
    fun isFavourite(id: Long) = id in _favourites.value
    fun toggleFavourite(id: Long) {
        val l = _favourites.value.toMutableList()
        if (!l.remove(id)) l.add(0, id)
        _favourites.value = l
        prefs.edit().putString("favourites", JSONArray(l).toString()).apply()
    }

    fun isFavAlbum(id: Long) = id in _favAlbums.value
    fun toggleFavAlbum(id: Long) {
        val l = _favAlbums.value.toMutableList()
        if (!l.remove(id)) l.add(0, id)
        _favAlbums.value = l
        prefs.edit().putString("favAlbums", JSONArray(l).toString()).apply()
    }

    // ---- genres
    fun genresOf(albumId: Long): List<String> = _albumGenres.value[albumId].orEmpty()

    fun setAlbumGenres(albumId: Long, genres: List<String>) {
        val clean = genres.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val m = _albumGenres.value.toMutableMap()
        if (clean.isEmpty()) m.remove(albumId) else m[albumId] = clean
        _albumGenres.value = m
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k.toString(), JSONArray(v)) }
        prefs.edit().putString("albumGenres", o.toString()).apply()
        val f = _filter.value
        if (f != null && f != FAV_FILTER && m.values.none { f in it }) _filter.value = null
    }

    /** Genres in use, most-used first. */
    fun genresInUse(): List<String> = _albumGenres.value.values.flatten()
        .groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
        .map { it.key }

    /** Genres to offer when tagging: ones already used, then the starter list. */
    fun genreSuggestions(): List<String> {
        val used = genresInUse()
        return used + STARTER_GENRES.filter { g -> used.none { it.equals(g, ignoreCase = true) } }
    }

    fun setFilter(f: String?) { _filter.value = f }

    fun setWallColumns(n: Int) {
        _wallColumns.value = n
        prefs.edit().putInt("wallColumns", n).apply()
    }

    const val FAV_FILTER = "\u2605favourites"
    val STARTER_GENRES = listOf(
        "Rock", "Pop", "Jazz", "Soul", "Hip-Hop", "Electronic", "Classical",
        "Country", "Reggae", "Metal", "Folk", "Blues",
    )

    // ---- recently played albums
    fun addRecentAlbum(albumId: Long) {
        val l = _recentAlbums.value.toMutableList()
        l.remove(albumId)
        l.add(0, albumId)
        val trimmed = l.take(30)
        _recentAlbums.value = trimmed
        prefs.edit().putString("recent", JSONArray(trimmed).toString()).apply()
    }

    // ---- playlists
    fun createPlaylist(name: String, initial: List<Long> = emptyList()): Playlist {
        val p = Playlist(System.currentTimeMillis(), name.trim().ifBlank { "New playlist" }, initial)
        savePlaylists(_playlists.value + p)
        return p
    }

    fun renamePlaylist(id: Long, name: String) =
        savePlaylists(_playlists.value.map { if (it.id == id) it.copy(name = name) else it })

    fun deletePlaylist(id: Long) = savePlaylists(_playlists.value.filterNot { it.id == id })

    fun addToPlaylist(id: Long, trackIds: List<Long>) =
        savePlaylists(_playlists.value.map { if (it.id == id) it.copy(trackIds = it.trackIds + trackIds) else it })

    fun removeFromPlaylist(id: Long, index: Int) = savePlaylists(_playlists.value.map {
        if (it.id == id) it.copy(trackIds = it.trackIds.toMutableList().also { l -> if (index in l.indices) l.removeAt(index) }) else it
    })

    fun movePlaylistItem(id: Long, from: Int, to: Int) = savePlaylists(_playlists.value.map {
        if (it.id == id && from in it.trackIds.indices && to in it.trackIds.indices) {
            val l = it.trackIds.toMutableList()
            l.add(to, l.removeAt(from))
            it.copy(trackIds = l)
        } else it
    })

    private fun savePlaylists(list: List<Playlist>) {
        _playlists.value = list
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", JSONArray(p.trackIds)))
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    // ---- folders & settings
    fun setSelectedFolders(folders: Set<String>) {
        _selectedFolders.value = folders
        prefs.edit().putStringSet("folders", folders).apply()
        Library.applyFolders(folders)
    }

    fun setOnlineLyrics(on: Boolean) {
        _onlineLyrics.value = on
        prefs.edit().putBoolean("onlineLyrics", on).apply()
    }

    fun setGridMode(on: Boolean) {
        _gridMode.value = on
        prefs.edit().putBoolean("gridMode", on).apply()
    }

    fun setAlbumSort(sort: String) {
        _albumSort.value = sort
        prefs.edit().putString("albumSort", sort).apply()
    }

    fun prefs(): SharedPreferences = prefs

    private fun String.toLongList(): List<Long> = try {
        JSONArray(this).toLongList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun JSONArray.toLongList(): List<Long> = (0 until length()).map { getLong(it) }
}
