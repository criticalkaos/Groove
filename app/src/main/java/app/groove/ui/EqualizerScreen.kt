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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.groove.data.Library
import app.groove.data.UserStore
import app.groove.playback.AudioFx
import app.groove.playback.Sound
import kotlin.math.roundToInt

/**
 * Sound settings. target = null edits the normal sound; "album:<id>" or "genre:<name>"
 * edits the saved sound for that album or genre. You hear changes straight away.
 */
@Composable
fun EqualizerScreen(target: String?) {
    val onlineLyrics by UserStore.onlineLyrics.collectAsState()
    val saved = target == null || AudioFx.profiles.containsKey(target)
    var draft by remember(target) { mutableStateOf(target?.let { AudioFx.profiles[it] } ?: AudioFx.normal) }

    // Preview while editing; put the right sound back when leaving.
    LaunchedEffect(target) { AudioFx.preview(draft) }
    DisposableEffect(target) { onDispose { AudioFx.refresh() } }

    fun update(s: Sound) {
        draft = s
        if (saved) AudioFx.save(target, s) else AudioFx.preview(s)
    }

    val title = when {
        target == null -> "Sound & settings"
        target.startsWith("album:") -> "Album sound"
        else -> target.removePrefix("genre:") + " sound"
    }
    val subject = when {
        target == null -> null
        target.startsWith("album:") -> target.removePrefix("album:").toLongOrNull()?.let { Library.album(it)?.title }
        else -> "all albums tagged " + target.removePrefix("genre:")
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title)
        LazyColumn(Modifier.fillMaxSize()) {
            if (target != null) {
                item {
                    Text(
                        if (saved) "Saved for ${subject ?: "this album"}. It switches on automatically when this music plays."
                        else "Set the sound you want for ${subject ?: "this album"}, then save. You're hearing it now.",
                        color = TextDim, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!saved) {
                            Button(
                                onClick = { AudioFx.save(target, draft); Toaster.show("$title saved") },
                                colors = ButtonDefaults.buttonColors(containerColor = GrooveAccent, contentColor = Color.Black),
                                modifier = Modifier.padding(start = 8.dp)
                            ) { Text("Save $title".replaceFirstChar { it.uppercase() }) }
                        } else {
                            TextButton(onClick = {
                                AudioFx.remove(target)
                                draft = AudioFx.normal
                                AudioFx.preview(draft)
                                Toaster.show("$title removed — normal sound will be used")
                            }) { Text("Remove $title", color = TextDim) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
            }
            item {
                SettingSwitch(
                    "Volume levelling",
                    "Evens out quiet and loud songs so you're not reaching for the volume.",
                    draft.level
                ) { update(draft.copy(level = it)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                BoostControl(draft.boostDb) { update(draft.copy(boostDb = it)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingSwitch("Equalizer", "Shape the sound with presets or your own settings.", draft.eq) {
                    update(draft.copy(eq = it))
                }
            }
            if (!AudioFx.available) {
                item {
                    Text("The equalizer isn't available on this phone.", color = TextDim, modifier = Modifier.padding(20.dp))
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
                                    selected = draft.preset == i,
                                    onClick = { update(draft.copy(eq = true, preset = i, bands = AudioFx.presetBands(i))) },
                                    label = { Text(name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GrooveAccent, selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                    val (lo, hi) = AudioFx.levelRange
                    val levels = AudioFx.bandsOf(draft)
                    levels.forEachIndexed { i, level ->
                        val hz = AudioFx.bandFreqs.getOrNull(i) ?: 0
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz",
                                color = TextDim, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp)
                            )
                            Slider(
                                value = level.toFloat(),
                                onValueChange = { v ->
                                    val nb = levels.toMutableList().also { it[i] = v.roundToInt() }
                                    update(draft.copy(eq = true, preset = -1, bands = nb))
                                },
                                valueRange = lo.toFloat()..hi.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = GrooveAccent),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "%+d dB".format(level / 100),
                                color = TextDim, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(52.dp).padding(start = 8.dp)
                            )
                        }
                    }
                    TextButton(
                        onClick = { update(draft.copy(preset = -1, bands = List(AudioFx.bandCount) { 0 })) },
                        modifier = Modifier.padding(start = 12.dp)
                    ) { Text("Reset to flat") }
                }
            }
            if (target == null) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        "Tip: albums and genres can have their own saved sound. Open an album and tap \"Album sound\".",
                        color = TextDim, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingSwitch(
                        "Find lyrics online",
                        "When a song has no lyrics of its own, look them up on lrclib.net.",
                        onlineLyrics
                    ) { UserStore.setOnlineLyrics(it) }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun BoostControl(db: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Volume boost", style = MaterialTheme.typography.titleMedium)
                Text("Push the volume past the phone's normal maximum.", color = TextDim, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (db == 0) "Off" else "+$db dB",
                color = if (db == 0) TextDim else GrooveAccent, style = MaterialTheme.typography.titleMedium
            )
        }
        Slider(
            value = db.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = GrooveAccent),
        )
        if (db > 6) {
            Text(
                "Careful: big boosts can sound harsh on phone speakers and are very loud in headphones.",
                color = Color(0xFFFFB74D), style = MaterialTheme.typography.bodyMedium
            )
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
