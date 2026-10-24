package app.groove.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String,
)

/** Finds lyrics: a .lrc file next to the song, lyrics stored inside the file, then lrclib.net. */
object LyricsRepo {

    suspend fun find(context: Context, track: Track): Lyrics? = withContext(Dispatchers.IO) {
        sidecar(track)?.let { return@withContext it }
        embedded(context, track)?.let { return@withContext it }
        val cacheDir = File(context.filesDir, "lyrics").apply { mkdirs() }
        val cache = File(cacheDir, "${track.id}.lrc")
        val none = File(cacheDir, "${track.id}.none")
        if (cache.exists()) return@withContext parse(cache.readText(), "Online")
        if (!UserStore.onlineLyrics.value || none.exists()) return@withContext null
        val text = online(track)
        if (text != null) {
            cache.writeText(text)
            parse(text, "Online")
        } else {
            none.writeText("")
            null
        }
    }

    private fun sidecar(track: Track): Lyrics? = try {
        val f = File(track.path.substringBeforeLast('.') + ".lrc")
        if (f.canRead()) parse(f.readText(), "Lyrics file") else null
    } catch (_: Exception) {
        null
    }

    private fun embedded(context: Context, track: Track): Lyrics? = try {
        context.contentResolver.openInputStream(track.uri)?.use { readId3Lyrics(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { parse(it, "Embedded") }
    } catch (_: Exception) {
        null
    }

    private fun online(track: Track): String? {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val url = "https://lrclib.net/api/get?track_name=${enc(track.title)}" +
            "&artist_name=${enc(track.artist)}&album_name=${enc(track.album)}" +
            "&duration=${track.durationMs / 1000}"
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Groove Android music player (https://github.com/criticalkaos/groove)")
            if (c.responseCode != 200) return null
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            val synced = json.optString("syncedLyrics", "")
            val plain = json.optString("plainLyrics", "")
            when {
                synced.isNotBlank() && synced != "null" -> synced
                plain.isNotBlank() && plain != "null" -> plain
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(text: String, source: String): Lyrics? {
        val lines = mutableListOf<LyricLine>()
        var anyStamp = false
        for (raw in text.lines()) {
            val matches = stamp.findAll(raw).toList()
            if (matches.isEmpty()) {
                // skip LRC metadata tags like [ar:...]
                if (raw.trim().matches(Regex("""\[[a-zA-Z]+:.*]"""))) continue
                lines += LyricLine(-1, raw.trim())
                continue
            }
            anyStamp = true
            val lyric = raw.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                lines += LyricLine(min * 60000 + sec * 1000 + ms, lyric)
            }
        }
        if (lines.none { it.text.isNotBlank() }) return null
        return if (anyStamp) {
            Lyrics(lines.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }, true, source)
        } else {
            Lyrics(lines.dropWhile { it.text.isBlank() }, false, source)
        }
    }

    /** Minimal ID3v2.3/2.4 reader for the USLT (unsynchronised lyrics) frame. */
    private fun readId3Lyrics(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.readFully(header) < 10) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val version = header[3].toInt()
        if (version < 3) return null
        val size = synchsafe(header, 6)
        if (size <= 0 || size > 32 * 1024 * 1024) return null
        val tag = ByteArray(size)
        val read = input.readFully(tag)
        var pos = 0
        if (header[5].toInt() and 0x40 != 0) { // extended header
            val ext = if (version == 4) synchsafe(tag, 0) else be32(tag, 0) + 4
            pos += ext
        }
        while (pos + 10 <= read) {
            if (tag[pos].toInt() == 0) break
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            val fsize = if (version == 4) synchsafe(tag, pos + 4) else be32(tag, pos + 4)
            val start = pos + 10
            if (fsize <= 0 || start + fsize > read) break
            if (id == "USLT") {
                return decodeUslt(tag.copyOfRange(start, start + fsize))
            }
            pos = start + fsize
        }
        return null
    }

    private fun decodeUslt(b: ByteArray): String? {
        if (b.size < 5) return null
        val enc = b[0].toInt()
        var i = 4 // encoding + 3 byte language
        val wide = enc == 1 || enc == 2
        // skip content descriptor
        if (wide) {
            while (i + 1 < b.size && !(b[i].toInt() == 0 && b[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < b.size && b[i].toInt() != 0) i++
            i += 1
        }
        if (i >= b.size) return null
        val cs: Charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(b, i, b.size - i, cs).trimEnd('\u0000').replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun InputStream.readFully(buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun synchsafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7f) shl 21) or ((b[o + 1].toInt() and 0x7f) shl 14) or
            ((b[o + 2].toInt() and 0x7f) shl 7) or (b[o + 3].toInt() and 0x7f)

    private fun be32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or
            ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)
}
