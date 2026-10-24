package app.groove.data

import android.net.Uri

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val albumId: Long,
    val trackNo: Int,
    val discNo: Int,
    val year: Int,
    val durationMs: Long,
    val path: String,
    val folder: String,
    val dateAdded: Long,
) {
    val mediaId: String get() = id.toString()
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val tracks: List<Track>,
    val dateAdded: Long,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
    val discCount: Int get() = tracks.maxOfOrNull { it.discNo } ?: 1
}

data class Artist(
    val name: String,
    val albums: List<Album>,
) {
    val trackCount: Int get() = albums.sumOf { it.tracks.size }
}

data class Playlist(
    val id: Long,
    val name: String,
    val trackIds: List<Long>,
)

data class MusicFolder(
    val path: String,
    val trackCount: Int,
)

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatLongDuration(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h} hr ${m} min" else "${m} min"
}
