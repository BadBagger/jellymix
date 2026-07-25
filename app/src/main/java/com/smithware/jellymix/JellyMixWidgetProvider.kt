package com.smithware.jellymix

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class JellyMixWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildWidgetViews(context))
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, JellyMixWidgetProvider::class.java))
            widgetIds.forEach { widgetId ->
                appWidgetManager.updateAppWidget(widgetId, buildWidgetViews(context))
            }
        }

        private fun buildWidgetViews(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences("jellymix", Context.MODE_PRIVATE)
            val tracks = prefs.getString("cachedTracks", null)?.toTrackList().orEmpty().ifEmpty { sampleTracks }
            val currentId = prefs.getString("currentTrackId", null)
            val current = tracks.firstOrNull { it.id == currentId } ?: tracks.first()
            val isConnected = prefs.getString("token", "").orEmpty().isNotBlank()
            val isPlaying = prefs.getBoolean("isPlaying", false)
            val openIntent = Intent(context, MainActivity::class.java)
            val playIntent = Intent(context, MainActivity::class.java).setAction(WIDGET_ACTION_PLAY_PAUSE)
            val skipIntent = Intent(context, MainActivity::class.java).setAction(WIDGET_ACTION_SKIP)
            val openPendingIntent = pendingActivity(context, 0, openIntent)
            val playPendingIntent = pendingActivity(context, 1, playIntent)
            val skipPendingIntent = pendingActivity(context, 2, skipIntent)

            return RemoteViews(context.packageName, R.layout.widget_jellymix).apply {
                setTextViewText(R.id.widgetTitle, current.title)
                setTextViewText(R.id.widgetArtist, current.artist)
                setTextViewText(R.id.widgetContext, if (isConnected) "Jellyfin music" else "Tap to connect Jellyfin")
                setTextViewText(R.id.widgetPlayButton, if (isPlaying) "Ⅱ" else "▶")
                setOnClickPendingIntent(R.id.widgetRoot, openPendingIntent)
                setOnClickPendingIntent(R.id.widgetPlayButton, playPendingIntent)
                setOnClickPendingIntent(R.id.widgetSkipButton, skipPendingIntent)
            }
        }

        private fun pendingActivity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
