package com.smithware.jellymix

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build

private const val PLAYBACK_CHANNEL_ID = "jellymix_playback"
private const val PLAYBACK_NOTIFICATION_ID = 1001

class PlaybackNotificationController(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val session = MediaSession(context, "JellyMixPlaybackSession").apply {
        isActive = true
    }

    fun update(state: JellyMixState) {
        if (!canPostNotifications()) return
        ensureChannel()
        updateSession(state)
        notificationManager.notify(PLAYBACK_NOTIFICATION_ID, buildNotification(state))
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
        val playPauseLabel = if (state.isPlaying) "Pause" else "Play"
        val playPauseIcon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        return Notification.Builder(context, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(track.title)
            .setContentText("${track.artist} - ${track.album}")
            .setSubText(state.queueTitle)
            .setContentIntent(activityIntent(requestCode = 0, action = null))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(notificationAction(android.R.drawable.ic_media_previous, "Previous", WIDGET_ACTION_PREVIOUS, 1))
            .addAction(notificationAction(playPauseIcon, playPauseLabel, WIDGET_ACTION_PLAY_PAUSE, 2))
            .addAction(notificationAction(android.R.drawable.ic_media_next, "Skip", WIDGET_ACTION_SKIP, 3))
            .addAction(notificationAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", WIDGET_ACTION_STOP, 4))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun updateSession(state: JellyMixState) {
        val track = state.currentTrack
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, track.id)
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
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
                        PlaybackState.ACTION_STOP
                )
                .setState(playbackState, 0L, if (state.isPlaying) 1f else 0f)
                .build()
        )
    }

    private fun notificationAction(iconRes: Int, label: String, action: String, requestCode: Int): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, iconRes),
            label,
            activityIntent(requestCode, action)
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
