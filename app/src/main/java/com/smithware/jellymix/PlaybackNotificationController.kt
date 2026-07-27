package com.smithware.jellymix

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.absoluteValue

private const val PLAYBACK_CHANNEL_ID = "jellymix_playback"
private const val PLAYBACK_NOTIFICATION_ID = 1001

class PlaybackNotificationController(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    @Volatile
    private var artworkFetchKey: String? = null
    private val session = MediaSession(context, "JellyMixPlaybackSession").apply {
        setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() = dispatchTransport(WIDGET_ACTION_PLAY)
                override fun onPause() = dispatchTransport(WIDGET_ACTION_PAUSE)
                override fun onSkipToNext() = dispatchTransport(WIDGET_ACTION_SKIP)
                override fun onSkipToPrevious() = dispatchTransport(WIDGET_ACTION_PREVIOUS)
                override fun onStop() = dispatchTransport(WIDGET_ACTION_STOP)
                override fun onSeekTo(pos: Long) {
                    if (!WidgetPlaybackBridge.seekTo(pos)) dispatchSeekFallback(pos)
                }
            }
        )
        isActive = true
    }

    fun update(state: JellyMixState, positionMs: Long = 0L) {
        if (!canPostNotifications()) return
        ensureChannel()
        session.isActive = true
        updateSession(state, positionMs)
        notificationManager.notify(PLAYBACK_NOTIFICATION_ID, buildNotification(state))
        fetchArtworkIfNeeded(state, positionMs)
    }

    fun cancel() {
        notificationManager.cancel(PLAYBACK_NOTIFICATION_ID)
        session.isActive = false
    }

    fun release() {
        cancel()
        session.release()
    }

    private fun buildNotification(state: JellyMixState): Notification {
        val track = state.currentTrack
        val artwork = notificationArtwork(track)
        val playPauseLabel = if (state.isPlaying) "Pause" else "Play"
        val playPauseIcon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val liked = state.liked[track.id] ?: track.liked
        return Notification.Builder(context, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(track.title)
            .setContentText(track.artist.ifBlank { "Unknown artist" })
            .setSubText(track.album.ifBlank { "JellyMix" })
            .setLargeIcon(artwork)
            .setContentIntent(activityIntent(requestCode = 0, action = null))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setPriority(Notification.PRIORITY_LOW)
            .setColor(0xFF1DE9B6.toInt())
            .setColorized(false)
            .addAction(notificationAction(android.R.drawable.btn_star_big_off, if (liked) "Liked" else "Like", WIDGET_ACTION_LIKE, 5))
            .addAction(notificationAction(android.R.drawable.ic_media_previous, "Previous", WIDGET_ACTION_PREVIOUS, 1))
            .addAction(notificationAction(playPauseIcon, playPauseLabel, WIDGET_ACTION_PLAY_PAUSE, 2))
            .addAction(notificationAction(android.R.drawable.ic_media_next, "Next", WIDGET_ACTION_SKIP, 3))
            .addAction(notificationAction(android.R.drawable.ic_menu_sort_by_size, if (state.shuffleEnabled) "Shuffle on" else "Shuffle", WIDGET_ACTION_SHUFFLE, 6))
            .setDeleteIntent(playbackServiceIntent(4, WIDGET_ACTION_STOP))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
            .build()
    }

    private fun updateSession(state: JellyMixState, positionMs: Long) {
        val track = state.currentTrack
        val artwork = notificationArtwork(track)
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, track.id)
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, track.artist)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationSec * 1000L)
                .build()
        )
        val playbackState = if (state.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_STOP
                )
                .setState(playbackState, positionMs.coerceAtLeast(0L), if (state.isPlaying) 1f else 0f)
                .build()
        )
    }

    private fun dispatchTransport(action: String) {
        if (!WidgetPlaybackBridge.dispatch(action)) {
            startPlaybackService(action)
        }
    }

    private fun dispatchSeekFallback(positionMs: Long) {
        val prefs = context.getSharedPreferences("jellymix", Context.MODE_PRIVATE)
        val currentTrackId = prefs.getString("currentTrackId", null).orEmpty()
        if (currentTrackId.isBlank()) return
        prefs.edit()
            .putString("playbackPositionTrackId", currentTrackId)
            .putLong("playbackPositionMs", positionMs.coerceAtLeast(0L))
            .apply()
    }

    private fun notificationAction(iconRes: Int, label: String, action: String, requestCode: Int): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, iconRes),
            label,
            playbackServiceIntent(requestCode, action)
        ).build()

    private fun activityIntent(requestCode: Int, action: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun playbackServiceIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, WidgetPlaybackService::class.java).apply {
            this.action = action
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, flags)
        } else {
            PendingIntent.getService(context, requestCode, intent, flags)
        }
    }

    private fun startPlaybackService(action: String) {
        val intent = Intent(context, WidgetPlaybackService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun notificationArtwork(track: Track): Bitmap {
        decodedJellyfinArtwork(track)?.let { return it }
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val seed = "${track.title}:${track.artist}:${track.album}".hashCode()
        val c1 = mixColor(0xFF1DE9B6.toInt(), seed)
        val c2 = mixColor(0xFF263241.toInt(), seed xor (seed shl 9))
        val c3 = mixColor(0xFFFF6B6B.toInt(), seed xor (seed shl 17))
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), intArrayOf(c1, c2, c3), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), background)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 32
        }
        canvas.drawCircle(size * 0.72f, size * 0.24f, size * 0.28f, glow)
        val text = artworkInitials(track.title).take(3)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = if (text.length > 2) 62f else 76f
            isFakeBoldText = true
        }
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, size / 2f, size / 2f - bounds.exactCenterY(), textPaint)
        return bitmap
    }

    private fun decodedJellyfinArtwork(track: Track): Bitmap? {
        val imageUrl = track.imageUrl?.takeIf { it.isNotBlank() } ?: return null
        val cacheFile = artworkCacheFile(track, imageUrl)
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return it }
        }
        return null
    }

    private fun fetchArtworkIfNeeded(state: JellyMixState, positionMs: Long) {
        val track = state.currentTrack
        val imageUrl = track.imageUrl?.takeIf { it.isNotBlank() } ?: return
        val cacheFile = artworkCacheFile(track, imageUrl)
        if (cacheFile.exists() && cacheFile.length() > 0L) return
        val fetchKey = "${track.id}:$imageUrl"
        if (artworkFetchKey == fetchKey) return
        artworkFetchKey = fetchKey
        Thread {
            val fetched = runCatching {
                downloadArtwork(cacheFile, imageUrl)
            }.getOrDefault(false)
            if (fetched) {
                updateSession(state, positionMs)
                notificationManager.notify(PLAYBACK_NOTIFICATION_ID, buildNotification(state))
            }
            if (artworkFetchKey == fetchKey) artworkFetchKey = null
        }.apply {
            name = "JellyMixNotificationArt"
            isDaemon = true
        }.start()
    }

    private fun downloadArtwork(cacheFile: File, imageUrl: String): Boolean {
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 2_500
        connection.readTimeout = 3_500
        return try {
            if (connection.responseCode !in 200..299) return false
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return false
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(bytes)
            true
        } finally {
            connection.disconnect()
        }
    }

    private fun artworkCacheFile(track: Track, imageUrl: String): File =
        File(File(context.cacheDir, "notification-art"), "${track.id.safeOfflineFileName()}-${imageUrl.hashCode().absoluteValue}.jpg")

    private fun mixColor(base: Int, seed: Int): Int {
        val shift = ((seed and 0x7fffffff) % 44) - 22
        fun clamp(value: Int): Int = value.coerceIn(0, 255)
        return Color.rgb(
            clamp(Color.red(base) + shift),
            clamp(Color.green(base) - shift / 2),
            clamp(Color.blue(base) + shift / 3)
        )
    }

    private fun artworkInitials(value: String): String =
        value
            .split(" ", "-", "_", ".", "/", "\\")
            .mapNotNull { segment -> segment.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() }
            .take(3)
            .joinToString("")
            .ifBlank { "JM" }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            "JellyMix playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playback controls for JellyMix"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
