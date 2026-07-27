package com.smithware.jellymix

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.Build
import android.widget.RemoteViews
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class JellyMixWidgetStyle(val layoutId: Int, val providerClass: Class<out AppWidgetProvider>) {
    Bar(R.layout.widget_jellymix, JellyMixWidgetProvider::class.java),
    Compact(R.layout.widget_jellymix_compact, JellyMixCompactWidgetProvider::class.java),
    Showcase(R.layout.widget_jellymix_showcase, JellyMixShowcaseWidgetProvider::class.java)
}

open class JellyMixWidgetProvider(
    private val widgetStyle: JellyMixWidgetStyle = JellyMixWidgetStyle.Bar
) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildWidgetViews(context, widgetStyle))
        }
        refreshArtworkAsync(context.applicationContext, widgetStyle, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            JellyMixWidgetStyle.entries.forEach { style ->
                val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, style.providerClass))
                widgetIds.forEach { widgetId ->
                    appWidgetManager.updateAppWidget(widgetId, buildWidgetViews(context, style))
                }
                refreshArtworkAsync(context.applicationContext, style, widgetIds)
            }
        }

        private fun buildWidgetViews(context: Context, style: JellyMixWidgetStyle, artworkOverride: Bitmap? = null): RemoteViews {
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
            val subtitle = listOf(current.artist, current.album).filter { it.isNotBlank() }.joinToString(" - ")
            val artwork = artworkOverride ?: cachedArtwork(context, current) ?: widgetArtwork(current, style)

            return RemoteViews(context.packageName, style.layoutId).apply {
                setTextViewText(R.id.widgetTitle, current.title)
                setTextViewText(R.id.widgetArtist, subtitle.ifBlank { current.artist.ifBlank { "JellyMix" } })
                setTextViewText(R.id.widgetAlbum, current.album.ifBlank { "JellyMix" })
                setTextViewText(R.id.widgetContext, if (isConnected) "JellyMix" else "Tap to connect")
                setTextViewText(R.id.widgetPlayButton, if (isPlaying) "\u23F8" else "\u25B6")
                setImageViewBitmap(R.id.widgetArtwork, artwork)
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

        private fun refreshArtworkAsync(context: Context, style: JellyMixWidgetStyle, widgetIds: IntArray) {
            if (widgetIds.isEmpty()) return
            val prefs = context.getSharedPreferences("jellymix", Context.MODE_PRIVATE)
            val tracks = prefs.getString("cachedTracks", null)?.toTrackList().orEmpty().ifEmpty { sampleTracks }
            val currentId = prefs.getString("currentTrackId", null)
            val current = tracks.firstOrNull { it.id == currentId } ?: tracks.first()
            if (current.imageUrl.isNullOrBlank() || cachedArtwork(context, current) != null) return
            Thread {
                val artwork = downloadArtwork(context, current) ?: return@Thread
                val appWidgetManager = AppWidgetManager.getInstance(context)
                widgetIds.forEach { widgetId ->
                    appWidgetManager.updateAppWidget(widgetId, buildWidgetViews(context, style, artwork))
                }
            }.apply {
                name = "JellyMixWidgetArtwork"
                isDaemon = true
                start()
            }
        }

        private fun cachedArtwork(context: Context, track: Track): Bitmap? =
            artworkFile(context, track)
                .takeIf { it.exists() && it.length() > 0L }
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }

        private fun downloadArtwork(context: Context, track: Track): Bitmap? {
            val imageUrl = track.imageUrl ?: return null
            return runCatching {
                val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 6_000
                    requestMethod = "GET"
                }
                connection.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }?.let { bitmap ->
                    val scaled = Bitmap.createScaledBitmap(bitmap, 320, 320, true)
                    artworkFile(context, track).also { file ->
                        file.parentFile?.mkdirs()
                        file.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.PNG, 92, out) }
                    }
                    scaled
                }
            }.getOrNull()
        }

        private fun artworkFile(context: Context, track: Track): File =
            File(File(context.cacheDir, "widget-art"), "${track.id.hashCode()}-${track.imageUrl.hashCode()}.png")

        private fun widgetArtwork(track: Track, style: JellyMixWidgetStyle): Bitmap {
            val size = if (style == JellyMixWidgetStyle.Showcase) 320 else 192
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

class JellyMixCompactWidgetProvider : JellyMixWidgetProvider(JellyMixWidgetStyle.Compact)

class JellyMixShowcaseWidgetProvider : JellyMixWidgetProvider(JellyMixWidgetStyle.Showcase)
