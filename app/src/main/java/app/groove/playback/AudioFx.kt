package app.groove.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.groove.data.Library
import app.groove.data.Track
import app.groove.data.UserStore
import org.json.JSONArray
import org.json.JSONObject

/** One set of sound settings: equalizer, volume levelling and volume boost. */
data class Sound(
    val eq: Boolean = false,
    val bands: List<Int> = emptyList(),
    val preset: Int = -1,
    val level: Boolean = false,
    val boostDb: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("eq", eq).put("bands", JSONArray(bands)).put("preset", preset)
        .put("level", level).put("boost", boostDb)

    companion object {
        fun fromJson(o: JSONObject): Sound {
            val b = o.optJSONArray("bands")
            return Sound(
                eq = o.optBoolean("eq"),
                bands = if (b == null) emptyList() else (0 until b.length()).map { b.getInt(it) },
                preset = o.optInt("preset", -1),
                level = o.optBoolean("level"),
                boostDb = o.optInt("boost", 0),
            )
        }
    }
}

/**
 * Applies sound settings to the player's audio session.
 * Each song uses its album's saved sound, else its genre's, else the normal sound.
 */
object AudioFx {
    private lateinit var prefs: SharedPreferences
    private var eq: Equalizer? = null
    private var dp: DynamicsProcessing? = null
    private var le: LoudnessEnhancer? = null

    /** Settings used when no album or genre sound applies. */
    var normal by mutableStateOf(Sound()); private set

    /** Saved sound keyed by "album:<id>" or "genre:<name>". */
    var profiles by mutableStateOf<Map<String, Sound>>(emptyMap()); private set

    /** Which saved sound is playing now (null = normal). */
    var activeKey by mutableStateOf<String?>(null); private set

    /** The settings currently applied. */
    var current by mutableStateOf(Sound()); private set

    /** "Use normal sound for now" — lasts until the album changes. */
    private var bypassAlbum: Long? = null

    var bandFreqs: List<Int> = emptyList(); private set
    var levelRange: Pair<Int, Int> = -1500 to 1500; private set
    var presetNames: List<String> = emptyList(); private set
    val available: Boolean get() = eq != null
    val bandCount: Int get() = bandFreqs.size

    fun albumKey(id: Long) = "album:$id"
    fun genreKey(name: String) = "genre:$name"

    fun label(key: String?): String = when {
        key == null -> "Normal sound"
        key.startsWith("album:") -> "Album sound"
        key.startsWith("genre:") -> key.removePrefix("genre:") + " sound"
        else -> "Sound"
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("groove_fx", Context.MODE_PRIVATE)
        normal = prefs.getString("normal", null)?.let {
            try { Sound.fromJson(JSONObject(it)) } catch (_: Exception) { null }
        } ?: Sound( // settings saved by the first version
            eq = prefs.getBoolean("eq", false),
            bands = prefs.getString("bands", null)?.split(",")?.mapNotNull { it.toIntOrNull() }.orEmpty(),
            preset = prefs.getInt("preset", -1),
            level = prefs.getBoolean("level", false),
        )
        profiles = try {
            val o = JSONObject(prefs.getString("profiles", "{}")!!)
            o.keys().asSequence().associateWith { Sound.fromJson(o.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyMap()
        }
        current = normal
    }

    fun attach(sessionId: Int) {
        release()
        try {
            val e = Equalizer(0, sessionId)
            eq = e
            val n = e.numberOfBands.toInt()
            bandFreqs = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            val r = e.bandLevelRange
            levelRange = r[0].toInt() to r[1].toInt()
            presetNames = (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) }
        } catch (_: Exception) {
            eq = null
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, false, 0, false, 0, false, 0, true
                ).build()
                dp = DynamicsProcessing(0, sessionId, config)
            } catch (_: Exception) {
                dp = null
            }
        }
        try {
            le = LoudnessEnhancer(sessionId)
        } catch (_: Exception) {
            le = null
        }
        apply(current)
    }

    fun release() {
        try { eq?.release() } catch (_: Exception) {}
        try { dp?.release() } catch (_: Exception) {}
        try { le?.release() } catch (_: Exception) {}
        eq = null
        dp = null
        le = null
    }

    /** Pick the right sound for what's playing now and apply it. */
    fun refresh() {
        val track = Library.track(PlayerRef.player?.currentMediaItem?.mediaId)
        if (track != null && bypassAlbum != null && bypassAlbum != track.albumId) bypassAlbum = null
        val key = if (track == null || bypassAlbum == track.albumId) null else keyFor(track)
        activeKey = key
        apply(profiles[key] ?: normal)
    }

    private fun keyFor(track: Track): String? {
        albumKey(track.albumId).let { if (it in profiles) return it }
        for (g in UserStore.genresOf(track.albumId)) {
            genreKey(g).let { if (it in profiles) return it }
        }
        return null
    }

    fun useNormalForNow() {
        bypassAlbum = Library.track(PlayerRef.player?.currentMediaItem?.mediaId)?.albumId
        refresh()
    }

    /** Hear settings straight away while editing (refresh() puts the right ones back). */
    fun preview(s: Sound) = apply(s)

    fun save(key: String?, s: Sound) {
        if (key == null) {
            normal = s
            prefs.edit().putString("normal", s.toJson().toString()).apply()
        } else {
            profiles = profiles + (key to s)
            saveProfiles()
        }
        preview(s)
    }

    fun remove(key: String) {
        profiles = profiles - key
        saveProfiles()
    }

    /** Rename a genre's saved sound when the genre itself is renamed. */
    fun hasProfile(key: String) = key in profiles

    private fun saveProfiles() {
        val o = JSONObject()
        profiles.forEach { (k, v) -> o.put(k, v.toJson()) }
        prefs.edit().putString("profiles", o.toString()).apply()
    }

    /** Band levels for a preset (applies it live as a preview). */
    fun presetBands(index: Int): List<Int> {
        val e = eq ?: return emptyList()
        return try {
            e.usePreset(index.toShort())
            (0 until e.numberOfBands.toInt()).map { e.getBandLevel(it.toShort()).toInt() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun bandsOf(s: Sound): List<Int> = if (s.bands.size == bandCount) s.bands else List(bandCount) { 0 }

    private fun apply(s: Sound) {
        current = s
        eq?.let { e ->
            try {
                val levels = bandsOf(s)
                if (s.bands.size != bandCount && s.preset in presetNames.indices) {
                    e.usePreset(s.preset.toShort())
                } else {
                    levels.forEachIndexed { i, v -> e.setBandLevel(i.toShort(), v.toShort()) }
                }
                e.enabled = s.eq
            } catch (_: Exception) {
            }
        }
        if (Build.VERSION.SDK_INT >= 28) {
            dp?.let { d ->
                try {
                    if (s.level) {
                        // Lift quiet passages a little and tame loud ones so songs sit at a similar volume.
                        d.setInputGainAllChannelsTo(6f)
                        d.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 5f, 250f, 4f, -16f, 0f))
                    } else {
                        // Just a safety limiter so a volume boost doesn't crackle.
                        d.setInputGainAllChannelsTo(0f)
                        d.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
                    }
                    d.enabled = s.level || s.boostDb > 0
                } catch (_: Exception) {
                }
            }
        }
        le?.let { l ->
            try {
                l.setTargetGain(s.boostDb.coerceIn(0, 12) * 100)
                l.enabled = s.boostDb > 0
            } catch (_: Exception) {
            }
        }
    }
}
