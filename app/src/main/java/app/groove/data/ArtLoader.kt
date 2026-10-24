package app.groove.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Loads album covers (embedded art, then the system's album thumbnail), caching to memory and disk. */
object ArtLoader {
    private const val SIZE = 640
    private val memory = object : LruCache<Long, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val missing = ConcurrentHashMap.newKeySet<Long>()
    private val accents = ConcurrentHashMap<Long, Int>()
    private val gate = Semaphore(3)

    fun cached(albumId: Long): Bitmap? = memory.get(albumId)
    fun cachedAccent(albumId: Long): Int? = accents[albumId]

    suspend fun load(context: Context, album: Album): Bitmap? {
        memory.get(album.id)?.let { return it }
        if (album.id in missing) return null
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                memory.get(album.id)?.let { return@withPermit it }
                val dir = File(context.cacheDir, "art").apply { mkdirs() }
                val file = File(dir, "${album.id}.jpg")
                var bmp: Bitmap? = null
                if (file.exists()) bmp = BitmapFactory.decodeFile(file.path)
                if (bmp == null) {
                    bmp = extract(context, album)
                    if (bmp != null) {
                        try {
                            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        } catch (_: Exception) {
                        }
                    }
                }
                if (bmp == null) {
                    missing.add(album.id)
                } else {
                    memory.put(album.id, bmp)
                }
                bmp
            }
        }
    }

    suspend fun accent(context: Context, album: Album): Int? {
        accents[album.id]?.let { return it }
        val bmp = load(context, album) ?: return null
        return withContext(Dispatchers.Default) {
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            val swatch = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch
            swatch?.rgb?.also { accents[album.id] = it }
        }
    }

    private fun extract(context: Context, album: Album): Bitmap? {
        for (t in album.tracks.take(3)) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, t.uri)
                val bytes = r.embeddedPicture
                if (bytes != null) {
                    decodeSampled(bytes)?.let { return it }
                }
            } catch (_: Exception) {
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
        }
        // Look for cover.jpg / folder.jpg next to the music.
        val folder = album.tracks.firstOrNull()?.folder
        if (folder != null) {
            for (name in listOf("cover.jpg", "folder.jpg", "cover.png", "front.jpg", "album.jpg", "Cover.jpg", "Folder.jpg")) {
                try {
                    val f = File(folder, name)
                    if (f.canRead()) {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f.path, o)
                        o.inSampleSize = sampleSize(o.outWidth, o.outHeight)
                        o.inJustDecodeBounds = false
                        BitmapFactory.decodeFile(f.path, o)?.let { return it }
                    }
                } catch (_: Exception) {
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, album.id)
                return context.contentResolver.loadThumbnail(uri, Size(SIZE, SIZE), null)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight)
        o.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    }

    private fun sampleSize(w: Int, h: Int): Int {
        var s = 1
        while (w / (s * 2) >= SIZE && h / (s * 2) >= SIZE) s *= 2
        return s
    }
}
