package app.groove.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.groove.data.UserStore
import app.groove.playback.AudioFx

@Composable
fun EqualizerScreen() {
    val onlineLyrics by UserStore.onlineLyrics.collectAsState()
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Sound & settings")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                SettingSwitch(
                    "Volume levelling",
                    "Evens out quiet and loud songs so you're not reaching for the volume.",
                    AudioFx.levelling
                ) { AudioFx.enableLevelling(it) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingSwitch("Equalizer", "Shape the sound with presets or your own settings.", AudioFx.eqEnabled) { AudioFx.enableEq(it) }
            }
            if (!AudioFx.available) {
                item {
                    Text(
                        "The equalizer isn't available on this phone.",
                        color = TextDim, modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                item {
                    if (AudioFx.presetNames.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            itemsIndexed(AudioFx.presetNames) { i, name ->
                                FilterChip(
                                    selected = AudioFx.preset == i,
                                    onClick = { AudioFx.usePreset(i); if (!AudioFx.eqEnabled) AudioFx.enableEq(true) },
                                    label = { Text(name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GrooveAccent, selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                    val (lo, hi) = AudioFx.levelRange
                    AudioFx.bandLevels.forEachIndexed { i, level ->
                        val hz = AudioFx.bandFreqs.getOrNull(i) ?: 0
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz",
                                color = TextDim, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp)
                            )
                            Slider(
                                value = level.toFloat(),
                                onValueChange = { AudioFx.setBand(i, it.toInt()); if (!AudioFx.eqEnabled) AudioFx.enableEq(true) },
                                valueRange = lo.toFloat()..hi.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = GrooveAccent),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "%+d dB".format(level / 100),
                                color = TextDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp).padding(start = 8.dp)
                            )
                        }
                    }
                    TextButton(onClick = { AudioFx.reset() }, modifier = Modifier.padding(start = 12.dp)) { Text("Reset to flat") }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingSwitch(
                    "Find lyrics online",
                    "When a song has no lyrics of its own, look them up on lrclib.net.",
                    onlineLyrics
                ) { UserStore.setOnlineLyrics(it) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = TextDim, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = GrooveAccent, checkedThumbColor = Color.Black)
        )
    }
}
