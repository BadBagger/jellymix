package com.smithware.jellymix

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.Build
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
            val previousIntent = Intent(context, JellyMixWidgetProvider::class.java).setAction(WIDGET_ACTION_PREVIOUS)
            val playIntent = Intent(context, JellyMixWidgetProvider::class.java).setAction(WIDGET_ACTION_PLAY_PAUSE)
            val skipIntent = Intent(context, JellyMixWidgetProvider::class.java).setAction(WIDGET_ACTION_SKIP)
            val openPendingIntent = pendingActivity(context, 0, openIntent)
            val previousPendingIntent = pendingPlaybackService(context, 1, previousIntent)
            val playPendingIntent = pendingPlaybackService(context, 2, playIntent)
            val skipPendingIntent = pendingPlaybackService(context, 3, skipIntent)

            return RemoteViews(context.packageName, R.layout.widget_jellymix).apply {
                setTextViewText(R.id.widgetTitle, current.title)
                setTextViewText(R.id.widgetArtist, current.artist)
                setTextViewText(R.id.widgetContext, if (isConnected) "Jellyfin music" else "Tap to connect Jellyfin")
                setTextViewText(R.id.widgetPlayButton, if (isPlaying) "\u23F8" else "\u25B6")
                setImageViewBitmap(R.id.widgetArtwork, widgetArtwork(current))
                setContentDescription(R.id.widgetPreviousButton, context.getString(R.string.widget_previous_description))
                setContentDescription(R.id.widgetPlayButton, context.getString(R.string.widget_play_description))
                setContentDescription(R.id.widgetSkipButton, context.getString(R.string.widget_next_description))
                setOnClickPendingIntent(R.id.widgetRoot, openPendingIntent)
                setOnClickPendingIntent(R.id.widgetPreviousButton, previousPendingIntent)
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

        private fun pendingPlaybackService(context: Context, requestCode: Int, intent: Intent): PendingIntent {
            val serviceIntent = Intent(context, WidgetPlaybackService::class.java).setAction(intent.action)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, requestCode, serviceIntent, flags)
            } else {
                PendingIntent.getService(context, requestCode, serviceIntent, flags)
            }
        }

        private fun widgetArtwork(track: Track): Bitmap {
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val seed = "${track.title}:${track.artist}:${track.album}".hashCode()
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    size.toFloat(),
                    size.toFloat(),
                    intArrayOf(
                        mixColor(0xFF1DE9B6.toInt(), seed),
                        mixColor(0xFF18202A.toInt(), seed xor (seed shl 7)),
                        mixColor(0xFF6C5CE7.toInt(), seed xor (seed shl 13))
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 28f, 28f, background)
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 34
                canvas.drawCircle(size * 0.72f, size * 0.25f, size * 0.3f, this)
            }
            val text = widgetInitials(track.title)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = if (text.length > 2) 48f else 58f
                isFakeBoldText = true
            }
            val bounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, bounds)
            canvas.drawText(text, size / 2f, size / 2f - bounds.exactCenterY(), textPaint)
            return bitmap
        }

        private fun mixColor(base: Int, seed: Int): Int {
            val shift = ((seed and 0x7fffffff) % 40) - 20
            fun clamp(value: Int): Int = value.coerceIn(0, 255)
            return Color.rgb(
                clamp(Color.red(base) + shift),
                clamp(Color.green(base) - shift / 2),
                clamp(Color.blue(base) + shift / 3)
            )
        }

        private fun widgetInitials(value: String): String =
            value
                .split(" ", "-", "_", ".", "/", "\\")
                .mapNotNull { segment -> segment.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() }
                .take(3)
                .joinToString("")
                .ifBlank { "JM" }
    }
}
