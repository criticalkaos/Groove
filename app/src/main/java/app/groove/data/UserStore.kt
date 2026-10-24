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

    private val _favourites = MutableStateFlow<Set<Long>>(emptySet())
    val favourites: StateFlow<Set<Long>> = _favourites.asStateFlow()

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
        _favourites.value = prefs.getString("favourites", "[]")!!.toLongList().toSet()
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
        val s = _favourites.value.toMutableSet()
        if (!s.add(id)) s.remove(id)
        _favourites.value = s
        prefs.edit().putString("favourites", JSONArray(s.toList()).toString()).apply()
    }

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
