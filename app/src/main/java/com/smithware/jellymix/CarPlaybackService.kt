package com.smithware.jellymix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.service.media.MediaBrowserService

private const val CAR_PLAYBACK_CHANNEL_ID = "jellymix_android_auto_playback"
private const val CAR_PLAYBACK_NOTIFICATION_ID = 1003
private const val BROWSABLE_CONTENT_STYLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
private const val PLAYABLE_CONTENT_STYLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
private const val CONTENT_STYLE_LIST_ITEM = 1
private const val CONTENT_STYLE_GRID_ITEM = 2

class CarPlaybackService : MediaBrowserService() {
    private lateinit var session: MediaSession
    private lateinit var prefs: SharedPreferences
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var player: MediaPlayer? = null
    private var tracks: List<Track> = emptyList()
    private var queue: List<Track> = emptyList()
    private var queueIndex: Int = 0
    private val carAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseForAudioFocusLoss()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.25f, 0.25f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                player?.setVolume(1f, 1f)
                if (player?.isPlaying == true) updatePlaybackState(PlaybackState.STATE_PLAYING)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("jellymix", Context.MODE_PRIVATE)
        audioManager = getSystemService(AudioManager::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(carAudioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
        refreshTracks()
        session = MediaSession(this, "JellyMixCarSession").apply {
            setCallback(callback)
            setPlaybackToLocal(carAudioAttributes)
            isActive = true
        }
        sessionToken = session.sessionToken
        updatePlaybackState(PlaybackState.STATE_STOPPED)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(
            CAR_ROOT_ID,
            Bundle().apply {
                putInt(BROWSABLE_CONTENT_STYLE_HINT, CONTENT_STYLE_GRID_ITEM)
                putInt(PLAYABLE_CONTENT_STYLE_HINT, CONTENT_STYLE_LIST_ITEM)
            }
        )

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowser.MediaItem>>) {
        refreshTracks()
        result.sendResult(
            buildCarBrowseEntries(
                parentId = parentId,
                tracks = tracks,
                liked = boolPrefs("liked"),
                longListens = intPrefs("longListens"),
                skips = intPrefs("skips"),
                localPlays = intPrefs("localPlays"),
                recentlyPlayedIds = recentTrackIds(),
                djMode = prefs.getString("djMode", null).enumValueOrDefault(GuestDjMode.Flow),
                seed = currentSeed()
            ).map { entry -> entry.toMediaItem() }
        )
    }

    override fun onDestroy() {
        player?.release()
        player = null
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        session.release()
        super.onDestroy()
    }

    private val callback = object : MediaSession.Callback() {
        override fun onPlay() {
            if (queue.isEmpty()) {
                queue = buildDefaultQueue()
                queueIndex = 0
            }
            playCurrent()
        }

        override fun onPause() {
            player?.pause()
            persistCarPlaying(false)
            updatePlaybackState(PlaybackState.STATE_PAUSED)
        }

        override fun onStop() {
            player?.stop()
            player?.release()
            player = null
            abandonAudioFocus()
            stopForeground(STOP_FOREGROUND_REMOVE)
            persistCarPlaying(false)
            updatePlaybackState(PlaybackState.STATE_STOPPED)
        }

        override fun onSkipToNext() {
            if (queue.isEmpty()) queue = buildDefaultQueue()
            if (queueIndex >= queue.lastIndex) {
                val seed = queue.getOrNull(queueIndex) ?: currentSeed()
                queue = buildContinuationQueue(
                    seed = seed,
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
            playCurrent()
        }

        override fun onSkipToPrevious() {
            if (queue.isEmpty()) queue = buildDefaultQueue()
            queueIndex = (queueIndex - 1).coerceAtLeast(0)
            playCurrent()
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            refreshTracks()
            queue = queueForCarMediaId(
                mediaId = mediaId,
                tracks = tracks,
                liked = boolPrefs("liked"),
                longListens = intPrefs("longListens"),
                skips = intPrefs("skips"),
                localPlays = intPrefs("localPlays"),
                recentlyPlayedIds = recentTrackIds(),
                djMode = prefs.getString("djMode", null).enumValueOrDefault(GuestDjMode.Flow),
                seed = currentSeed()
            )
            queueIndex = 0
            playCurrent()
        }
    }

    private fun playCurrent() {
        val track = queue.getOrNull(queueIndex) ?: return
        updateMetadata(track)
        persistCarPlayback(track)
        startCarForeground(track, isPlaying = false)

        player?.release()
        player = null

        val token = prefs.getString("token", "").orEmpty()
        val serverUrl = prefs.getString("serverUrl", "").orEmpty()
        if (token.isBlank() || serverUrl.isBlank() || track.id.startsWith("sample-")) {
            persistCarPlaying(false)
            updatePlaybackState(
                state = PlaybackState.STATE_ERROR,
                errorMessage = "Connect JellyMix to Jellyfin before Android Auto playback."
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        if (!requestAudioFocus()) {
            persistCarPlaying(false)
            updatePlaybackState(
                state = PlaybackState.STATE_PAUSED,
                errorMessage = "Android Auto did not grant JellyMix media audio focus."
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        updatePlaybackState(PlaybackState.STATE_BUFFERING)

        player = MediaPlayer().apply {
            setAudioAttributes(carAudioAttributes)
            setDataSource(applicationContext, Uri.parse(jellyfinStreamUrl(serverUrl, track.id, token)))
            setOnPreparedListener {
                it.start()
                persistCarPlaying(true)
                updatePlaybackState(PlaybackState.STATE_PLAYING)
                startCarForeground(track, isPlaying = true)
            }
            setOnCompletionListener {
                persistCarPlaying(false)
                if (queueIndex >= queue.lastIndex) {
                    queue = buildContinuationQueue(
                        seed = track,
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
                playCurrent()
            }
            setOnErrorListener { _, _, _ ->
                persistCarPlaying(false)
                updatePlaybackState(
                    state = PlaybackState.STATE_ERROR,
                    errorMessage = "JellyMix could not stream this track to Android Auto."
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                true
            }
            prepareAsync()
        }
    }

    private fun buildDefaultQueue(): List<Track> {
        refreshTracks()
        return queueForCarMediaId(
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
    }

    private fun updatePlaybackState(state: Int, errorMessage: String? = null) {
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
        val builder = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, player?.currentPosition?.toLong() ?: 0L, if (state == PlaybackState.STATE_PLAYING) 1f else 0f)
        if (!errorMessage.isNullOrBlank()) {
            builder.setErrorMessage(errorMessage)
        }
        session.setPlaybackState(
            builder.build()
        )
    }

    private fun updateMetadata(track: Track) {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, carTrackId(track.id))
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationSec * 1000L)
                .build()
        )
    }

    private fun persistCarPlayback(track: Track) {
        val recent = listOf(track.id) + recentTrackIds().filterNot { it == track.id }
        val localPlays = intPrefs("localPlays")
        prefs.edit()
            .putString("currentTrackId", track.id)
            .putString("queueIds", queue.joinToString(",") { it.id })
            .putInt("queueIndex", queueIndex)
            .putString("queueTitle", "Android Auto")
            .putString("recentTrackIds", recent.take(30).joinToString(","))
            .putString("localPlays", (localPlays + (track.id to ((localPlays[track.id] ?: 0) + 1))).toStorageString())
            .apply()
        JellyMixWidgetProvider.updateAll(this)
    }

    private fun persistCarPlaying(isPlaying: Boolean) {
        prefs.edit().putBoolean("isPlaying", isPlaying).apply()
        JellyMixWidgetProvider.updateAll(this)
    }

    private fun requestAudioFocus(): Boolean =
        audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    private fun pauseForAudioFocusLoss() {
        player?.pause()
        persistCarPlaying(false)
        updatePlaybackState(PlaybackState.STATE_PAUSED)
        queue.getOrNull(queueIndex)?.let { startCarForeground(it, isPlaying = false) }
    }

    private fun startCarForeground(track: Track, isPlaying: Boolean) {
        ensureCarPlaybackChannel()
        startForeground(CAR_PLAYBACK_NOTIFICATION_ID, carPlaybackNotification(track, isPlaying))
    }

    private fun carPlaybackNotification(track: Track, isPlaying: Boolean): Notification =
        Notification.Builder(this, CAR_PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(track.title)
            .setContentText(track.subtitle().text)
            .setSubText("Android Auto")
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

    private fun ensureCarPlaybackChannel() {
        val channel = NotificationChannel(
            CAR_PLAYBACK_CHANNEL_ID,
            "JellyMix Android Auto playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playback service used by JellyMix in Android Auto"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun refreshTracks() {
        tracks = prefs.getString("cachedTracks", null)?.toTrackList().orEmpty()
    }

    private fun currentSeed(): Track {
        val library = tracks.ifEmpty { sampleTracks }
        val currentId = prefs.getString("currentTrackId", null)
        return library.firstOrNull { it.id == currentId } ?: library.first()
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

    private fun CarBrowseEntry.toMediaItem(): MediaBrowser.MediaItem {
        val track = id.fromCarTrackId()?.let { trackId -> tracks.ifEmpty { sampleTracks }.firstOrNull { it.id == trackId } }
        val description = MediaDescription.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setExtras(
                Bundle().apply {
                    putInt(
                        if (playable) PLAYABLE_CONTENT_STYLE_HINT else BROWSABLE_CONTENT_STYLE_HINT,
                        if (playable) CONTENT_STYLE_LIST_ITEM else CONTENT_STYLE_GRID_ITEM
                    )
                }
            )
            .apply { track?.imageUrl?.let { setIconUri(Uri.parse(it)) } }
            .build()
        val flag = if (playable) MediaBrowser.MediaItem.FLAG_PLAYABLE else MediaBrowser.MediaItem.FLAG_BROWSABLE
        return MediaBrowser.MediaItem(description, flag)
    }
}
