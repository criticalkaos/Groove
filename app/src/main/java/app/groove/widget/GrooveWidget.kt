package app.groove.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.groove.R
import app.groove.data.ArtLoader
import app.groove.data.Library
import app.groove.playback.PlayerRef
import app.groove.ui.MainActivity

/** Home-screen widget: cover, song, artist and play controls. */
class GrooveWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in listOf(ACTION_PREV, ACTION_TOGGLE, ACTION_NEXT)) return
        val p = PlayerRef.player
        if (p == null || p.mediaItemCount == 0) {
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
            return
        }
        when (action) {
            ACTION_PREV -> p.seekToPrevious()
            ACTION_NEXT -> p.seekToNextMediaItem()
            ACTION_TOGGLE -> if (p.isPlaying) p.pause() else p.play()
        }
        refresh(context)
    }

    companion object {
        private const val ACTION_PREV = "app.groove.widget.PREV"
        private const val ACTION_TOGGLE = "app.groove.widget.TOGGLE"
        private const val ACTION_NEXT = "app.groove.widget.NEXT"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GrooveWidget::class.java))
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.widget_groove)
            val p = PlayerRef.player
            val track = Library.track(p?.currentMediaItem?.mediaId)
            val meta = p?.currentMediaItem?.mediaMetadata
            views.setTextViewText(R.id.widget_title, track?.title ?: meta?.title ?: "Groove")
            views.setTextViewText(R.id.widget_artist, track?.artist ?: meta?.artist ?: "Tap to pick some music")
            views.setImageViewResource(
                R.id.widget_play,
                if (p?.isPlaying == true) R.drawable.ic_w_pause else R.drawable.ic_w_play
            )
            val art = track?.let { ArtLoader.cached(it.albumId) }
            if (art != null) {
                val small = android.graphics.Bitmap.createScaledBitmap(art, 200, 200, true)
                views.setImageViewBitmap(R.id.widget_art, small)
            } else {
                views.setImageViewResource(R.id.widget_art, R.drawable.ic_launcher_foreground)
            }

            val open = PendingIntent.getActivity(
                context, 10, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_art, open)
            views.setOnClickPendingIntent(R.id.widget_title, open)
            views.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, ACTION_PREV, 11))
            views.setOnClickPendingIntent(R.id.widget_play, broadcast(context, ACTION_TOGGLE, 12))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 13))
            manager.updateAppWidget(ids, views)
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, GrooveWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
    }
}
