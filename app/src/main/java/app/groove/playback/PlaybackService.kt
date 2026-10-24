package app.groove.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.groove.data.Library
import app.groove.data.UserStore
import app.groove.ui.MainActivity
import app.groove.widget.GrooveWidget
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Keeps music playing in the background, with lock-screen and notification controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val sessionId = am.generateAudioSessionId()
        player.audioSessionId = sessionId
        AudioFx.attach(sessionId)

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Library.track(mediaItem?.mediaId)?.let { UserStore.addRecentAlbum(it.albumId) }
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && SleepTimer.stopAtEndOfTrack) {
                    SleepTimer.cancel()
                    player.pause()
                }
                GrooveWidget.refresh(this@PlaybackService)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                GrooveWidget.refresh(this@PlaybackService)
            }
        })

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri
                            ?: Library.track(item.mediaId)?.uri
                        if (uri != null) item.buildUpon().setUri(uri).build() else item
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()

        PlayerRef.player = player
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlayerRef.player = null
        AudioFx.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        GrooveWidget.refresh(this)
        super.onDestroy()
    }
}

/** Same-process handle on the player, used by the home-screen widget. */
object PlayerRef {
    @Volatile
    var player: Player? = null
}
