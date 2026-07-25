package com.smithware.jellymix

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.service.media.MediaBrowserService

class CarPlaybackService : MediaBrowserService() {
    private lateinit var session: MediaSession
    private lateinit var prefs: SharedPreferences
    private var player: MediaPlayer? = null
    private var tracks: List<Track> = emptyList()
    private var queue: List<Track> = emptyList()
    private var queueIndex: Int = 0

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("jellymix", Context.MODE_PRIVATE)
        refreshTracks()
        session = MediaSession(this, "JellyMixCarSession").apply {
            setCallback(callback)
            isActive = true
        }
        sessionToken = session.sessionToken
        updatePlaybackState(PlaybackState.STATE_STOPPED)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(CAR_ROOT_ID, null)

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
            updatePlaybackState(PlaybackState.STATE_PAUSED)
        }

        override fun onStop() {
            player?.stop()
            player?.release()
            player = null
            updatePlaybackState(PlaybackState.STATE_STOPPED)
        }

        override fun onSkipToNext() {
            if (queue.isEmpty()) queue = buildDefaultQueue()
            if (queueIndex >= queue.lastIndex) {
                val seed = queue.getOrNull(queueIndex) ?: currentSeed()
                queue = buildAutoplayQueue(
                    seed = seed,
                    tracks = tracks.ifEmpty { sampleTracks },
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

        player?.release()
        player = null

        val token = prefs.getString("token", "").orEmpty()
        val serverUrl = prefs.getString("serverUrl", "").orEmpty()
        if (token.isBlank() || serverUrl.isBlank() || track.id.startsWith("sample-")) {
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            return
        }

        player = MediaPlayer().apply {
            setDataSource(applicationContext, Uri.parse(jellyfinStreamUrl(serverUrl, track.id, token)))
            setOnPreparedListener {
                it.start()
                updatePlaybackState(PlaybackState.STATE_PLAYING)
            }
            setOnCompletionListener {
                if (queueIndex >= queue.lastIndex) {
                    queue = buildAutoplayQueue(
                        seed = track,
                        tracks = tracks.ifEmpty { sampleTracks },
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
                updatePlaybackState(PlaybackState.STATE_ERROR)
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

    private fun updatePlaybackState(state: Int) {
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, player?.currentPosition?.toLong() ?: 0L, if (state == PlaybackState.STATE_PLAYING) 1f else 0f)
                .build()
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
            .apply { track?.imageUrl?.let { setIconUri(Uri.parse(it)) } }
            .build()
        val flag = if (playable) MediaBrowser.MediaItem.FLAG_PLAYABLE else MediaBrowser.MediaItem.FLAG_BROWSABLE
        return MediaBrowser.MediaItem(description, flag)
    }
}
