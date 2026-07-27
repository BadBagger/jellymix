package com.smithware.jellymix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder

private const val WIDGET_PLAYBACK_CHANNEL_ID = "jellymix_widget_playback"
private const val WIDGET_PLAYBACK_NOTIFICATION_ID = 1002

class WidgetPlaybackService : Service() {
    private lateinit var prefs: SharedPreferences
    private var player: MediaPlayer? = null
    private var tracks: List<Track> = emptyList()
    private var queue: List<Track> = emptyList()
    private var queueIndex: Int = 0

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("jellymix", Context.MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshState()
        startWidgetForeground()
        if (WidgetPlaybackBridge.dispatch(intent?.action)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            WIDGET_ACTION_PLAY_PAUSE -> togglePlayback()
            WIDGET_ACTION_PLAY -> playFromExternalControl()
            WIDGET_ACTION_PAUSE -> pauseFromExternalControl()
            WIDGET_ACTION_SKIP -> skip()
            WIDGET_ACTION_PREVIOUS -> previous()
            WIDGET_ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun togglePlayback() {
        val activePlayer = player
        if (activePlayer?.isPlaying == true || prefs.getBoolean("isPlaying", false)) {
            pauseFromExternalControl()
            return
        }
        playFromExternalControl()
    }

    private fun playFromExternalControl() {
        if (player?.isPlaying == true || prefs.getBoolean("isPlaying", false)) return
        if (queue.isEmpty()) {
            queue = buildDefaultQueue()
            queueIndex = 0
        }
        playCurrent()
    }

    private fun pauseFromExternalControl() {
        player?.pause()
        persistPlaybackFlag(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun skip() {
        if (queue.isEmpty()) {
            queue = buildDefaultQueue()
            queueIndex = 0
        } else if (queueIndex >= queue.lastIndex) {
            queue = buildContinuationQueue(
                seed = queue.getOrNull(queueIndex) ?: currentSeed(),
                tracks = tracks.ifEmpty { sampleTracks },
                fallbackTracks = tracks.ifEmpty { sampleTracks },
                liked = boolPrefs("liked"),
                longListens = intPrefs("longListens"),
                skips = intPrefs("skips"),
                localPlays = intPrefs("localPlays"),
                recentlyPlayedIds = recentTrackIds()
            )
            queueIndex = 0
        } else {
            queueIndex += 1
        }
        playOrPersistCurrent(prefs.getBoolean("isPlaying", false))
    }

    private fun previous() {
        if (queue.isEmpty()) queue = buildDefaultQueue()
        queueIndex = previousQueueIndex(queueIndex, queue.size, repeatEnabled = false)
        playOrPersistCurrent(prefs.getBoolean("isPlaying", false))
    }

    private fun stopPlayback() {
        player?.stop()
        player?.release()
        player = null
        persistPlaybackFlag(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playOrPersistCurrent(keepPlaying: Boolean) {
        if (keepPlaying) {
            playCurrent()
        } else {
            val track = queue.getOrNull(queueIndex) ?: currentSeed()
            persistPlayback(track, isPlaying = false, countLocalPlay = false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun playCurrent() {
        val track = queue.getOrNull(queueIndex) ?: currentSeed()
        persistPlayback(track, isPlaying = true, countLocalPlay = true)

        player?.release()
        player = null

        val token = prefs.getString("token", "").orEmpty()
        val serverUrl = prefs.getString("serverUrl", "").orEmpty()
        if (token.isBlank() || serverUrl.isBlank() || track.id.startsWith("sample-")) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        player = MediaPlayer().apply {
            setDataSource(applicationContext, Uri.parse(jellyfinStreamUrl(serverUrl, track.id, token)))
            setOnPreparedListener { it.start() }
            setOnCompletionListener { skip() }
            setOnErrorListener { _, _, _ ->
                persistPlaybackFlag(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                true
            }
            prepareAsync()
        }
    }

    private fun refreshState() {
        tracks = prefs.getString("cachedTracks", null)?.toTrackList().orEmpty()
        val library = tracks.ifEmpty { sampleTracks }
        val savedQueueIds = prefs.getString("queueIds", "").orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        queue = savedQueueIds.mapNotNull { id -> library.firstOrNull { it.id == id } }
        val currentId = prefs.getString("currentTrackId", null)
        queueIndex = prefs.getInt("queueIndex", queue.indexOfFirst { it.id == currentId }.coerceAtLeast(0))
            .coerceIn(0, queue.lastIndex.coerceAtLeast(0))
    }

    private fun buildDefaultQueue(): List<Track> =
        queueForCarMediaId(
            mediaId = CAR_JARVIS_ID,
            tracks = tracks,
            liked = boolPrefs("liked"),
            longListens = intPrefs("longListens"),
            skips = intPrefs("skips"),
            localPlays = intPrefs("localPlays"),
            recentlyPlayedIds = recentTrackIds(),
            djMode = prefs.getString("djMode", null).enumValueOrDefault(GuestDjMode.Flow),
            seed = currentSeed()
        )

    private fun currentSeed(): Track {
        val library = tracks.ifEmpty { sampleTracks }
        val currentId = prefs.getString("currentTrackId", null)
        return library.firstOrNull { it.id == currentId } ?: library.first()
    }

    private fun persistPlayback(track: Track, isPlaying: Boolean, countLocalPlay: Boolean) {
        val recent = listOf(track.id) + recentTrackIds().filterNot { it == track.id }
        val localPlays = intPrefs("localPlays")
        val nextLocalPlays = if (countLocalPlay) {
            localPlays + (track.id to ((localPlays[track.id] ?: 0) + 1))
        } else {
            localPlays
        }
        prefs.edit()
            .putString("currentTrackId", track.id)
            .putString("queueIds", queue.joinToString(",") { it.id })
            .putInt("queueIndex", queueIndex)
            .putString("queueTitle", "Widget playback")
            .putString("recentTrackIds", recent.take(30).joinToString(","))
            .putString("localPlays", nextLocalPlays.toStorageString())
            .putBoolean("isPlaying", isPlaying)
            .apply()
        JellyMixWidgetProvider.updateAll(this)
    }

    private fun persistPlaybackFlag(isPlaying: Boolean) {
        prefs.edit().putBoolean("isPlaying", isPlaying).apply()
        JellyMixWidgetProvider.updateAll(this)
    }

    private fun startWidgetForeground() {
        ensureChannel()
        val track = queue.getOrNull(queueIndex) ?: currentSeed()
        val notification = Notification.Builder(this, WIDGET_PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(track.title)
            .setContentText(track.subtitle().text)
            .setSubText("JellyMix widget control")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                WIDGET_PLAYBACK_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(WIDGET_PLAYBACK_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            WIDGET_PLAYBACK_CHANNEL_ID,
            "JellyMix widget playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playback controls launched from the JellyMix widget"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun recentTrackIds(): List<String> =
        prefs.getString("recentTrackIds", "").orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun intPrefs(key: String): Map<String, Int> =
        prefs.getString(key, "").orEmpty().toIntMap()

    private fun boolPrefs(key: String): Map<String, Boolean> =
        prefs.getString(key, "").orEmpty().toBooleanMap()

    private fun jellyfinStreamUrl(serverUrl: String, itemId: String, token: String): String =
        "$serverUrl/Audio/${Uri.encode(itemId)}/stream?Static=true&api_key=${Uri.encode(token)}"
}
