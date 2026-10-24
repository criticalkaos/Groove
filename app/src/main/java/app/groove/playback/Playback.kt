package app.groove.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.groove.data.Track
import com.google.common.util.concurrent.MoreExecutors

data class PlayState(
    val currentId: String? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val queue: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
)

/** The app's remote control for the playback service. */
object Playback {
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()

    var state by mutableStateOf(PlayState())
        private set

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = try { future.get() } catch (e: Exception) { null } ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            refresh()
            pending.forEach { it(c) }
            pending.clear()
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        controller?.release()
        controller = null
    }

    private fun run(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending += action
    }

    private fun refresh() {
        val c = controller ?: return
        state = PlayState(
            currentId = c.currentMediaItem?.mediaId,
            isPlaying = c.isPlaying,
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId },
            currentIndex = c.currentMediaItemIndex,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            speed = c.playbackParameters.speed,
        )
    }

    fun position(): Long = controller?.currentPosition ?: 0

    fun toItem(t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(t.mediaId)
        .setUri(t.uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(t.uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setAlbumArtist(t.albumArtist.ifBlank { t.artist })
                .setTrackNumber(t.trackNo)
                .build()
        )
        .build()

    fun play(tracks: List<Track>, start: Int = 0, shuffle: Boolean = false) = run { c ->
        if (tracks.isEmpty()) return@run
        c.shuffleModeEnabled = shuffle
        val startIndex = if (shuffle) tracks.indices.random() else start.coerceIn(tracks.indices)
        c.setMediaItems(tracks.map(::toItem), startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(tracks: List<Track>) = run { c ->
        if (c.mediaItemCount == 0) {
            play(tracks)
        } else {
            c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map(::toItem))
        }
    }

    fun addToQueue(tracks: List<Track>) = run { c ->
        if (c.mediaItemCount == 0) play(tracks) else c.addMediaItems(tracks.map(::toItem))
    }

    fun toggle() = run { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = run { it.seekToNextMediaItem() }
    fun previous() = run { it.seekToPrevious() }
    fun seekTo(ms: Long) = run { it.seekTo(ms) }
    fun skipTo(index: Int) = run { it.seekToDefaultPosition(index); it.play() }
    fun move(from: Int, to: Int) = run { it.moveMediaItem(from, to) }
    fun remove(index: Int) = run { it.removeMediaItem(index) }
    fun clearUpcoming() = run { c ->
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
    }
    fun setShuffle(on: Boolean) = run { it.shuffleModeEnabled = on }
    fun cycleRepeat() = run { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun setSpeed(speed: Float) = run { it.playbackParameters = PlaybackParameters(speed) }
    fun pause() = run { it.pause() }
}

/** Pauses after a set time or at the end of the current song. */
object SleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    private val stop = Runnable {
        Playback.pause()
        endsAt = 0L
    }

    var endsAt by mutableStateOf(0L)
        private set

    @Volatile
    var stopAtEndOfTrack = false

    var endOfTrackMode by mutableStateOf(false)
        private set

    fun start(minutes: Int) {
        cancel()
        endsAt = System.currentTimeMillis() + minutes * 60_000L
        handler.postDelayed(stop, minutes * 60_000L)
    }

    fun endOfTrack() {
        cancel()
        stopAtEndOfTrack = true
        endOfTrackMode = true
    }

    fun cancel() {
        handler.removeCallbacks(stop)
        endsAt = 0L
        stopAtEndOfTrack = false
        endOfTrackMode = false
    }

    fun active(): Boolean = endsAt > System.currentTimeMillis() || stopAtEndOfTrack
}
