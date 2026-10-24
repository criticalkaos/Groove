package app.groove.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Equalizer and volume levelling attached to the player's audio session. */
object AudioFx {
    private lateinit var prefs: SharedPreferences
    private var eq: Equalizer? = null
    private var dp: DynamicsProcessing? = null

    var eqEnabled by mutableStateOf(false); private set
    var levelling by mutableStateOf(false); private set
    var bandLevels by mutableStateOf<List<Int>>(emptyList()); private set
    var preset by mutableStateOf(-1); private set

    var bandFreqs: List<Int> = emptyList(); private set
    var levelRange: Pair<Int, Int> = -1500 to 1500; private set
    var presetNames: List<String> = emptyList(); private set
    val available: Boolean get() = eq != null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("groove_fx", Context.MODE_PRIVATE)
        eqEnabled = prefs.getBoolean("eq", false)
        levelling = prefs.getBoolean("level", false)
        preset = prefs.getInt("preset", -1)
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
            val saved = prefs.getString("bands", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            if (saved != null && saved.size == n) {
                saved.forEachIndexed { i, v -> e.setBandLevel(i.toShort(), v.toShort()) }
            } else if (preset >= 0 && preset < presetNames.size) {
                e.usePreset(preset.toShort())
            }
            bandLevels = (0 until n).map { e.getBandLevel(it.toShort()).toInt() }
            e.enabled = eqEnabled
        } catch (_: Exception) {
            eq = null
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, false, 0, false, 0, false, 0, true
                ).build()
                val d = DynamicsProcessing(0, sessionId, config)
                // Lift quiet passages a little and tame loud ones so tracks sit at a similar volume.
                d.setInputGainAllChannelsTo(6f)
                d.setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(true, true, 0, 5f, 250f, 4f, -16f, 0f)
                )
                d.enabled = levelling
                dp = d
            } catch (_: Exception) {
                dp = null
            }
        }
    }

    fun release() {
        try { eq?.release() } catch (_: Exception) {}
        try { dp?.release() } catch (_: Exception) {}
        eq = null
        dp = null
    }

    fun enableEq(on: Boolean) {
        eqEnabled = on
        prefs.edit().putBoolean("eq", on).apply()
        try { eq?.enabled = on } catch (_: Exception) {}
    }

    fun enableLevelling(on: Boolean) {
        levelling = on
        prefs.edit().putBoolean("level", on).apply()
        try { dp?.enabled = on } catch (_: Exception) {}
    }

    fun setBand(band: Int, level: Int) {
        val e = eq ?: return
        try {
            e.setBandLevel(band.toShort(), level.toShort())
        } catch (_: Exception) {
        }
        bandLevels = bandLevels.toMutableList().also { if (band in it.indices) it[band] = level }
        preset = -1
        prefs.edit().putString("bands", bandLevels.joinToString(",")).putInt("preset", -1).apply()
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        try {
            e.usePreset(index.toShort())
            bandLevels = (0 until e.numberOfBands.toInt()).map { e.getBandLevel(it.toShort()).toInt() }
        } catch (_: Exception) {
        }
        preset = index
        prefs.edit().putInt("preset", index).remove("bands").apply()
    }

    fun reset() {
        val e = eq ?: return
        for (i in bandLevels.indices) {
            try { e.setBandLevel(i.toShort(), 0) } catch (_: Exception) {}
        }
        bandLevels = bandLevels.map { 0 }
        preset = -1
        prefs.edit().remove("bands").putInt("preset", -1).apply()
    }
}
