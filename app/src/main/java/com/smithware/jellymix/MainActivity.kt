package com.smithware.jellymix

import android.Manifest
import android.app.Application
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.smithware.jellymix.ui.theme.AccentTheme
import com.smithware.jellymix.ui.theme.JellyMixTheme
import com.smithware.jellymix.ui.theme.ThemeMode
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val viewModel: JellyMixViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val visualizerPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                viewModel.setVisualizerPermission(granted)
            }
            LaunchedEffect(Unit) {
                viewModel.setVisualizerPermission(
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                )
            }
            val state = viewModel.state
            JellyMixTheme(themeMode = state.themeMode, accentTheme = state.accentTheme) {
                JellyMixApp(
                    viewModel = viewModel,
                    onRequestVisualizerPermission = {
                        visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }
    }
}

class JellyMixViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("jellymix", Application.MODE_PRIVATE)
    private val client = JellyfinClient()
    private var player: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var lastVisualizerUpdateMs = 0L
    private val appContext = application.applicationContext

    var state by mutableStateOf(
        JellyMixState(
            serverUrl = prefs.getString("serverUrl", "http://jellyfin.local:8096").orEmpty(),
            username = prefs.getString("username", "").orEmpty(),
            token = prefs.getString("token", "").orEmpty(),
            userId = prefs.getString("userId", "").orEmpty(),
            themeMode = prefs.getString("themeMode", null).enumValueOrDefault(ThemeMode.System),
            accentTheme = prefs.getString("accentTheme", null).enumValueOrDefault(AccentTheme.Jelly),
            tracks = sampleTracks,
            currentTrack = sampleTracks.first(),
            jellyfinPlaylists = emptyList(),
            selectedPlaylistTracks = emptyList(),
            liked = prefs.getString("liked", null)?.toBooleanMap() ?: sampleTracks.associate { it.id to it.liked },
            skips = prefs.getString("skips", null)?.toIntMap() ?: sampleTracks.associate { it.id to it.skipped },
            longListens = prefs.getString("longListens", null)?.toIntMap() ?: sampleTracks.associate { it.id to 0 },
            localPlays = prefs.getString("localPlays", null)?.toIntMap() ?: sampleTracks.associate { it.id to 0 },
            recentTrackIds = prefs.getString("recentTrackIds", null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        )
    )
        private set

    init {
        if (state.token.isNotBlank() && state.userId.isNotBlank()) {
            loadLibrary()
        }
    }

    fun setServerUrl(value: String) {
        state = state.copy(serverUrl = value)
        prefs.edit().putString("serverUrl", value).apply()
    }

    fun setUsername(value: String) {
        state = state.copy(username = value)
        prefs.edit().putString("username", value).apply()
    }

    fun setPassword(value: String) {
        state = state.copy(password = value)
    }

    fun setSearchQuery(value: String) {
        state = state.copy(searchQuery = value)
    }

    fun setDiscoveryFilter(value: DiscoveryFilter) {
        state = state.copy(discoveryFilter = value)
    }

    fun setThemeMode(value: ThemeMode) {
        state = state.copy(themeMode = value)
        prefs.edit().putString("themeMode", value.name).apply()
    }

    fun setAccentTheme(value: AccentTheme) {
        state = state.copy(accentTheme = value)
        prefs.edit().putString("accentTheme", value.name).apply()
    }

    fun setVisualizerPermission(granted: Boolean) {
        val message = if (granted) {
            "Audio visualizer is ready for Jellyfin playback."
        } else {
            "Visualizer preview is active. Enable audio capture for live Jellyfin waveforms."
        }
        state = state.copy(visualizerPermissionGranted = granted, visualizerMessage = message)
        if (granted && state.isPlaying && !state.currentTrack.id.startsWith("sample-")) {
            player?.audioSessionId?.takeIf { it != 0 }?.let(::startAudioVisualizer)
        }
    }

    fun connect() {
        val serverUrl = normalizeServerUrl(state.serverUrl)
        val username = state.username.trim()
        val password = state.password
        if (serverUrl == null) {
            state = state.copy(status = "Enter a valid Jellyfin URL, like http://192.168.1.25:8096.")
            return
        }
        if (username.isBlank() || password.isBlank()) {
            state = state.copy(status = "Enter server, username, and password.")
            return
        }
        state = state.copy(serverUrl = serverUrl, isLoading = true, status = "Checking Jellyfin server...")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.publicSystemInfo(serverUrl)
                    client.authenticate(serverUrl, username, password)
                }
            }.onSuccess { session ->
                prefs.edit()
                    .putString("serverUrl", serverUrl)
                    .putString("username", username)
                    .putString("token", session.token)
                    .putString("userId", session.userId)
                    .apply()
                state = state.copy(
                    serverUrl = serverUrl,
                    token = session.token,
                    userId = session.userId,
                    password = "",
                    status = "Connected. Loading music library..."
                )
                loadLibrary()
            }.onFailure { error ->
                state = state.copy(isLoading = false, status = "Connection failed: ${error.cleanMessage()}")
            }
        }
    }

    fun loadLibrary() {
        val serverUrl = normalizeServerUrl(state.serverUrl)
        if (serverUrl == null) {
            state = state.copy(status = "Enter a valid Jellyfin URL before reloading.")
            return
        }
        if (state.token.isBlank() || state.userId.isBlank()) return
        state = state.copy(serverUrl = serverUrl, isLoading = true, status = "Loading Jellyfin music...")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    JellyfinLibraryLoad(
                        tracks = client.fetchAudioTracks(state.serverUrl, state.userId, state.token),
                        playlists = client.fetchPlaylists(state.serverUrl, state.userId, state.token)
                    )
                }
            }.onSuccess { library ->
                val remoteTracks = library.tracks
                val tracks = remoteTracks.ifEmpty { sampleTracks }
                val mergedLiked = tracks.associate { it.id to (state.liked[it.id] ?: it.liked) }
                val mergedSkips = tracks.associate { it.id to (state.skips[it.id] ?: it.skipped) }
                val mergedLong = tracks.associate { it.id to (state.longListens[it.id] ?: 0) }
                val mergedPlays = tracks.associate { it.id to (state.localPlays[it.id] ?: 0) }
                state = state.copy(
                    isLoading = false,
                    tracks = tracks,
                    currentTrack = tracks.firstOrNull() ?: state.currentTrack,
                    liked = mergedLiked,
                    skips = mergedSkips,
                    longListens = mergedLong,
                    localPlays = mergedPlays,
                    recentTrackIds = state.recentTrackIds.filter { id -> tracks.any { it.id == id } },
                    jellyfinPlaylists = library.playlists,
                    selectedPlaylist = null,
                    selectedPlaylistTracks = emptyList(),
                    status = "Loaded ${tracks.size} tracks and ${library.playlists.size} Jellyfin playlists."
                )
                persistSignals()
            }.onFailure { error ->
                state = state.copy(isLoading = false, status = "Library load failed: ${error.cleanMessage()}")
            }
        }
    }

    fun selectTrack(track: Track) {
        val queueIndex = state.queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        val queue = if (state.queue.any { it.id == track.id }) state.queue else listOf(track)
        val queueTitle = if (state.queue.any { it.id == track.id }) state.queueTitle else "Selected track"
        state = state.copy(currentTrack = track, queue = queue, queueIndex = queueIndex, queueTitle = queueTitle)
        if (state.isPlaying) playCurrentTrack()
    }

    fun startQueue(title: String, tracks: List<Track>) {
        val playableTracks = tracks.distinctBy { it.id }
        if (playableTracks.isEmpty()) {
            state = state.copy(status = "No tracks available for $title.")
            return
        }
        val queue = if (state.shuffleEnabled) playableTracks.shuffled() else playableTracks
        state = state.copy(
            currentTrack = queue.first(),
            queue = queue,
            queueIndex = 0,
            queueTitle = title,
            status = "Queued ${queue.size} tracks from $title."
        )
        if (state.isPlaying) playCurrentTrack()
    }

    fun openPlaylist(playlist: JellyfinPlaylist) {
        if (!state.isConnected) {
            state = state.copy(status = "Connect to Jellyfin before opening server playlists.")
            return
        }
        state = state.copy(
            selectedPlaylist = playlist,
            selectedPlaylistTracks = emptyList(),
            isLoading = true,
            status = "Loading ${playlist.name}..."
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.fetchPlaylistTracks(state.serverUrl, state.userId, state.token, playlist.id)
                }
            }.onSuccess { playlistTracks ->
                val mergedLiked = (state.tracks + playlistTracks).associate { it.id to (state.liked[it.id] ?: it.liked) }
                val mergedSkips = (state.tracks + playlistTracks).associate { it.id to (state.skips[it.id] ?: it.skipped) }
                val mergedLong = (state.tracks + playlistTracks).associate { it.id to (state.longListens[it.id] ?: 0) }
                val mergedPlays = (state.tracks + playlistTracks).associate { it.id to (state.localPlays[it.id] ?: 0) }
                state = state.copy(
                    isLoading = false,
                    selectedPlaylistTracks = playlistTracks,
                    liked = mergedLiked,
                    skips = mergedSkips,
                    longListens = mergedLong,
                    localPlays = mergedPlays,
                    status = "Loaded ${playlistTracks.size} tracks from ${playlist.name}."
                )
                persistSignals()
            }.onFailure { error ->
                state = state.copy(isLoading = false, status = "Playlist load failed: ${error.cleanMessage()}")
            }
        }
    }

    fun togglePlayPause() {
        if (state.isPlaying && (state.token.isBlank() || state.currentTrack.id.startsWith("sample-"))) {
            state = state.copy(
                isPlaying = false,
                visualizerBands = restingVisualizerBands(),
                visualizerMessage = "Visualizer paused.",
                status = "Paused demo playback."
            )
            return
        }
        val activePlayer = player
        if (activePlayer != null && activePlayer.isPlaying) {
            activePlayer.pause()
            releaseAudioVisualizer()
            state = state.copy(
                isPlaying = false,
                visualizerBands = restingVisualizerBands(),
                visualizerMessage = "Visualizer paused.",
                status = "Paused."
            )
            return
        }
        playCurrentTrack()
    }

    fun startRadioFromCurrent() {
        val seed = state.currentTrack
        val radioTracks = buildTrackRadio(
            seed = seed,
            tracks = state.tracks,
            liked = state.liked,
            longListens = state.longListens,
            skips = state.skips,
            localPlays = state.localPlays
        )
        startQueue("${seed.title} radio", radioTracks)
    }

    fun toggleLike() {
        val id = state.currentTrack.id
        val newLiked = !(state.liked[id] ?: false)
        state = state.copy(
            liked = state.liked + (id to newLiked),
            status = if (newLiked) "Liked ${state.currentTrack.title}." else "Removed like for ${state.currentTrack.title}."
        )
        persistSignals()
        syncFavoriteIfPossible(state.currentTrack, newLiked)
    }

    private fun syncFavoriteIfPossible(track: Track, liked: Boolean) {
        if (!state.isConnected || track.id.startsWith("sample-")) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.setFavorite(
                        serverUrl = state.serverUrl,
                        userId = state.userId,
                        itemId = track.id,
                        token = state.token,
                        favorite = liked
                    )
                }
            }.onSuccess {
                state = state.copy(status = if (liked) "Synced favorite to Jellyfin." else "Removed Jellyfin favorite.")
            }.onFailure { error ->
                state = state.copy(status = "Local like saved. Jellyfin favorite sync failed: ${error.cleanMessage()}")
            }
        }
    }

    fun markLongListen() {
        val id = state.currentTrack.id
        state = state.copy(longListens = state.longListens + (id to ((state.longListens[id] ?: 0) + 1)))
        persistSignals()
    }

    fun skip() {
        advanceQueue(countSkip = true, keepPlaying = state.isPlaying)
    }

    fun clearQueue() {
        state = state.copy(
            queue = emptyList(),
            queueIndex = 0,
            queueTitle = "Discovery queue",
            shuffleEnabled = false,
            repeatEnabled = false,
            status = "Queue cleared."
        )
    }

    fun clearSession() {
        player?.release()
        releaseAudioVisualizer()
        player = null
        prefs.edit()
            .remove("token")
            .remove("userId")
            .apply()
        state = state.copy(
            token = "",
            userId = "",
            password = "",
            tracks = sampleTracks,
            currentTrack = sampleTracks.first(),
            queue = emptyList(),
            queueIndex = 0,
            queueTitle = "Discovery queue",
            jellyfinPlaylists = emptyList(),
            selectedPlaylist = null,
            selectedPlaylistTracks = emptyList(),
            isPlaying = false,
            visualizerBands = restingVisualizerBands(),
            visualizerMessage = "Visualizer preview is active. Enable audio capture for live Jellyfin waveforms.",
            status = "Session cleared. Demo discovery mode is active."
        )
    }

    private fun advanceQueue(countSkip: Boolean, keepPlaying: Boolean) {
        val currentId = state.currentTrack.id
        if (countSkip) {
            state = state.copy(skips = state.skips + (currentId to ((state.skips[currentId] ?: 0) + 1)))
        }
        val fallbackQueue = state.rankedTracks()
        val queue = state.queue.ifEmpty { fallbackQueue }
        val nextIndex = nextQueueIndex(state.queueIndex, queue.size, state.repeatEnabled)
        val nextTrack = queue.getOrNull(nextIndex) ?: fallbackQueue.first()
        val reachedEnd = isQueueEnd(state.queueIndex, queue.size, state.repeatEnabled)
        state = state.copy(
            currentTrack = nextTrack,
            queue = queue,
            queueIndex = nextIndex,
            isPlaying = keepPlaying && !reachedEnd,
            status = if (reachedEnd) "End of queue." else "Queued ${nextTrack.title}."
        )
        persistSignals()
        if (state.isPlaying) playCurrentTrack()
    }

    fun toggleShuffle() {
        val current = state.currentTrack
        val source = state.queue.ifEmpty { state.rankedTracks() }
        val reordered = if (!state.shuffleEnabled) {
            listOf(current) + source.filterNot { it.id == current.id }.shuffled()
        } else {
            source.sortedByDescending {
                recommendationScore(it, state.liked[it.id] == true, state.longListens[it.id] ?: 0, state.skips[it.id] ?: 0)
            }
        }
        state = state.copy(
            shuffleEnabled = !state.shuffleEnabled,
            queue = reordered,
            queueIndex = reordered.indexOfFirst { it.id == current.id }.coerceAtLeast(0),
            status = if (!state.shuffleEnabled) "Shuffle on." else "Shuffle off."
        )
    }

    fun toggleRepeat() {
        state = state.copy(
            repeatEnabled = !state.repeatEnabled,
            status = if (!state.repeatEnabled) "Repeat queue on." else "Repeat queue off."
        )
    }

    private fun playCurrentTrack() {
        val track = state.currentTrack
        if (state.token.isBlank() || track.id.startsWith("sample-")) {
            releaseAudioVisualizer()
            recordPlayStart(track)
            state = state.copy(
                isPlaying = true,
                visualizerBands = syntheticVisualizerBands(track),
                visualizerMessage = "Preview visualizer is reacting to the queued demo track.",
                status = "Demo playback active. Connect Jellyfin to stream real audio."
            )
            return
        }
        val streamUrl = client.streamUrl(state.serverUrl, track.id, state.token)
        state = state.copy(status = "Buffering ${track.title}...")
        runCatching {
            releaseAudioVisualizer()
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(appContext, Uri.parse(streamUrl))
                setOnPreparedListener {
                    it.start()
                    recordPlayStart(track)
                    state = state.copy(
                        isPlaying = true,
                        visualizerBands = syntheticVisualizerBands(track),
                        status = "Playing ${track.title}."
                    )
                    startAudioVisualizer(it.audioSessionId)
                }
                setOnCompletionListener {
                    releaseAudioVisualizer()
                    markLongListen()
                    advanceQueue(countSkip = false, keepPlaying = true)
                }
                setOnErrorListener { _, _, _ ->
                    releaseAudioVisualizer()
                    state = state.copy(
                        isPlaying = false,
                        visualizerBands = restingVisualizerBands(),
                        visualizerMessage = "Visualizer paused after playback error.",
                        status = "Playback failed for ${track.title}."
                    )
                    true
                }
                prepareAsync()
            }
        }.onFailure { error ->
            releaseAudioVisualizer()
            state = state.copy(
                isPlaying = false,
                visualizerBands = restingVisualizerBands(),
                visualizerMessage = "Visualizer paused after playback error.",
                status = "Playback failed: ${error.cleanMessage()}"
            )
        }
    }

    private fun startAudioVisualizer(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (!state.visualizerPermissionGranted) {
            state = state.copy(
                visualizerBands = syntheticVisualizerBands(state.currentTrack),
                visualizerMessage = "Tap Enable live visualizer for audio-reactive Jellyfin waveforms."
            )
            return
        }
        releaseAudioVisualizer()
        runCatching {
            val captureRange = Visualizer.getCaptureSizeRange()
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = captureRange.last()
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            val now = System.currentTimeMillis()
                            if (waveform == null || now - lastVisualizerUpdateMs < 80L) return
                            lastVisualizerUpdateMs = now
                            val bands = waveform.toVisualizerBands()
                            viewModelScope.launch {
                                state = state.copy(
                                    visualizerBands = bands,
                                    visualizerMessage = "Live Jellyfin audio visualizer."
                                )
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) = Unit
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    false
                )
                enabled = true
            }
        }.onFailure { error ->
            state = state.copy(
                visualizerBands = syntheticVisualizerBands(state.currentTrack),
                visualizerMessage = "Preview visualizer active. Live capture unavailable: ${error.cleanMessage()}"
            )
        }
    }

    private fun releaseAudioVisualizer() {
        visualizer?.runCatching {
            enabled = false
            release()
        }
        visualizer = null
    }

    private fun persistSignals() {
        prefs.edit()
            .putString("liked", state.liked.toStorageString())
            .putString("skips", state.skips.toStorageString())
            .putString("longListens", state.longListens.toStorageString())
            .putString("localPlays", state.localPlays.toStorageString())
            .putString("recentTrackIds", state.recentTrackIds.joinToString(","))
            .apply()
    }

    private fun recordPlayStart(track: Track) {
        val recent = listOf(track.id) + state.recentTrackIds.filterNot { it == track.id }
        state = state.copy(
            localPlays = state.localPlays + (track.id to ((state.localPlays[track.id] ?: 0) + 1)),
            recentTrackIds = recent.take(30)
        )
        persistSignals()
    }

    override fun onCleared() {
        player?.release()
        player = null
        super.onCleared()
    }
}

private class JellyfinClient {
    fun publicSystemInfo(serverUrl: String): JellyfinServerInfo {
        val json = requestJson("$serverUrl/System/Info/Public", "GET", null, null)
        return JellyfinServerInfo(
            serverName = json.optString("ServerName", "Jellyfin"),
            version = json.optString("Version", "")
        )
    }

    fun authenticate(serverUrl: String, username: String, password: String): JellyfinSession {
        val body = JSONObject().put("Username", username).put("Pw", password).toString()
        val json = requestJson(
            url = "$serverUrl/Users/AuthenticateByName",
            method = "POST",
            body = body,
            token = null
        )
        return JellyfinSession(
            token = json.getString("AccessToken"),
            userId = json.getJSONObject("User").getString("Id")
        )
    }

    fun fetchAudioTracks(serverUrl: String, userId: String, token: String): List<Track> {
        val views = requestJson("$serverUrl/Users/$userId/Views", "GET", null, token)
            .optJSONArray("Items")
        val musicLibraryId = (0 until (views?.length() ?: 0))
            .map { views!!.getJSONObject(it) }
            .firstOrNull { it.optString("CollectionType") == "music" }
            ?.optString("Id")

        val url = buildString {
            append("$serverUrl/Users/$userId/Items?Recursive=true&IncludeItemTypes=Audio")
            append("&Fields=Genres,UserData,RunTimeTicks,Album,Artists")
            append("&SortBy=DatePlayed,SortName&SortOrder=Descending")
            if (!musicLibraryId.isNullOrBlank()) append("&ParentId=${Uri.encode(musicLibraryId)}")
        }
        val items = requestJson(url, "GET", null, token).optJSONArray("Items") ?: return emptyList()
        return items.toTracks(serverUrl, token)
    }

    fun fetchPlaylists(serverUrl: String, userId: String, token: String): List<JellyfinPlaylist> {
        val url = "$serverUrl/Users/$userId/Items?Recursive=true&IncludeItemTypes=Playlist&SortBy=SortName"
        val items = requestJson(url, "GET", null, token).optJSONArray("Items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.getJSONObject(index)
            val id = item.optString("Id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            JellyfinPlaylist(
                id = id,
                name = item.optString("Name", "Playlist"),
                childCount = item.optInt("ChildCount", 0),
                imageUrl = imageUrl(serverUrl, id, token)
            )
        }
    }

    fun fetchPlaylistTracks(serverUrl: String, userId: String, token: String, playlistId: String): List<Track> {
        val playlistUrl = "$serverUrl/Playlists/${Uri.encode(playlistId)}/Items?UserId=${Uri.encode(userId)}&Fields=Genres,UserData,RunTimeTicks,Album,Artists"
        val items = runCatching {
            requestJson(playlistUrl, "GET", null, token).optJSONArray("Items")
        }.getOrNull()
        if (items != null) return items.toTracks(serverUrl, token)

        val fallbackUrl = "$serverUrl/Users/$userId/Items?ParentId=${Uri.encode(playlistId)}&Recursive=true&IncludeItemTypes=Audio&Fields=Genres,UserData,RunTimeTicks,Album,Artists"
        return requestJson(fallbackUrl, "GET", null, token)
            .optJSONArray("Items")
            ?.toTracks(serverUrl, token)
            .orEmpty()
    }

    fun streamUrl(serverUrl: String, itemId: String, token: String): String =
        "$serverUrl/Audio/${Uri.encode(itemId)}/stream?Static=true&api_key=${Uri.encode(token)}"

    fun setFavorite(serverUrl: String, userId: String, itemId: String, token: String, favorite: Boolean) {
        val method = if (favorite) "POST" else "DELETE"
        requestText(
            url = "$serverUrl/Users/${Uri.encode(userId)}/FavoriteItems/${Uri.encode(itemId)}",
            method = method,
            body = null,
            token = token
        )
    }

    private fun imageUrl(serverUrl: String, itemId: String, token: String): String =
        "$serverUrl/Items/${Uri.encode(itemId)}/Images/Primary?fillHeight=320&fillWidth=320&quality=90&api_key=${Uri.encode(token)}"

    private fun requestJson(url: String, method: String, body: String?, token: String?): JSONObject =
        JSONObject(requestText(url, method, body, token))

    private fun requestText(url: String, method: String, body: String?, token: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Emby-Authorization", "MediaBrowser Client=\"JellyMix\", Device=\"Android\", DeviceId=\"jellymix-android\", Version=\"0.1.0\"")
            if (!token.isNullOrBlank()) setRequestProperty("X-Emby-Token", token)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                OutputStreamWriter(outputStream).use { it.write(body) }
            }
        }
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        connection.disconnect()
        if (responseCode !in 200..299) error("HTTP $responseCode ${text.take(160)}")
        return text
    }

    private fun org.json.JSONArray.toTracks(serverUrl: String, token: String): List<Track> =
        (0 until length()).mapNotNull { index ->
            val item = getJSONObject(index)
            val id = item.optString("Id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val userData = item.optJSONObject("UserData")
            val artists = item.optJSONArray("Artists")
            val artist = if (artists != null && artists.length() > 0) artists.optString(0) else item.optString("AlbumArtist", "Unknown Artist")
            val genres = item.optJSONArray("Genres")
            val genre = if (genres != null && genres.length() > 0) genres.optString(0) else "Library"
            val ticks = item.optLong("RunTimeTicks", 0L)
            Track(
                id = id,
                title = item.optString("Name", "Untitled"),
                artist = artist.ifBlank { "Unknown Artist" },
                album = item.optString("Album", "Unknown Album").ifBlank { "Unknown Album" },
                genre = genre.ifBlank { "Library" },
                mood = moodFromGenre(genre),
                durationSec = (ticks / 10_000_000L).toInt().coerceAtLeast(1),
                plays = userData?.optInt("PlayCount", 0) ?: 0,
                completion = ((userData?.optDouble("PlayedPercentage", 0.0) ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
                skipped = 0,
                liked = userData?.optBoolean("IsFavorite", false) ?: false,
                imageUrl = imageUrl(serverUrl, id, token)
            )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JellyMixApp(
    viewModel: JellyMixViewModel,
    onRequestVisualizerPermission: () -> Unit
) {
    var selectedTab by androidx.compose.runtime.remember { mutableStateOf(Tab.Home) }
    val state = viewModel.state
    val rankedTracks = state.rankedTracks()
    val visibleTracks = filterTracks(rankedTracks, state.searchQuery)
    val discoveryTracks = discoveryTracks(
        tracks = visibleTracks,
        filter = state.discoveryFilter,
        liked = state.liked,
        skips = state.skips,
        longListens = state.longListens,
        currentTrack = state.currentTrack
    )
    val recentTracks = state.recentTracks()
    val mixes = buildMixes(rankedTracks, state.liked, state.longListens)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("JellyMix", fontWeight = FontWeight.Bold) })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ConnectionCard(
                        state = state,
                        onServerUrlChange = viewModel::setServerUrl,
                        onUsernameChange = viewModel::setUsername,
                        onPasswordChange = viewModel::setPassword,
                        onConnect = viewModel::connect,
                        onReload = viewModel::loadLibrary,
                        onClearSession = viewModel::clearSession,
                        onThemeModeSelected = viewModel::setThemeMode,
                        onAccentThemeSelected = viewModel::setAccentTheme
                    )
                }
                when (selectedTab) {
                    Tab.Home -> {
                        item { HeroCard(currentTrack = state.currentTrack, serverUrl = state.serverUrl, connected = state.isConnected) }
                        item {
                            VisualizerCard(
                                state = state,
                                onEnableLiveVisualizer = onRequestVisualizerPermission
                            )
                        }
                        item { SearchCard(state.searchQuery, viewModel::setSearchQuery, visibleTracks.size) }
                        item { MixRail("Made from your listening", mixes, viewModel::startQueue, viewModel::selectTrack) }
                        if (recentTracks.isNotEmpty()) {
                            item { TrackSection("Recently played", recentTracks.take(8), state.liked, viewModel::selectTrack) }
                        }
                        if (state.queue.isNotEmpty()) {
                            item { UpNextSection(state.queue, state.queueIndex, state.liked, viewModel::selectTrack, viewModel::clearQueue) }
                        }
                        item { TrackSection("Heavy rotation", visibleTracks.take(8), state.liked, viewModel::selectTrack) }
                    }
                    Tab.Discover -> {
                        item { SearchCard(state.searchQuery, viewModel::setSearchQuery, visibleTracks.size) }
                        item { DiscoveryFilters(state.discoveryFilter, viewModel::setDiscoveryFilter) }
                        item { TrackSection(state.discoveryFilter.sectionTitle, discoveryTracks, state.liked, viewModel::selectTrack) }
                    }
                    Tab.Library -> {
                        item { SearchCard(state.searchQuery, viewModel::setSearchQuery, visibleTracks.size) }
                        item { LibrarySummary(state.tracks, state.jellyfinPlaylists) }
                        if (state.jellyfinPlaylists.isNotEmpty()) {
                            item { JellyfinPlaylistRail(state.jellyfinPlaylists, viewModel::openPlaylist) }
                        }
                        item { TrackSection("Tracks", filterTracks(state.tracks.sortedBy { it.artist }, state.searchQuery), state.liked, viewModel::selectTrack) }
                    }
                    Tab.Playlists -> {
                        if (state.jellyfinPlaylists.isNotEmpty()) {
                            item { JellyfinPlaylistRail(state.jellyfinPlaylists, viewModel::openPlaylist) }
                        }
                        state.selectedPlaylist?.let { playlist ->
                            item {
                                SelectedPlaylistSection(
                                    playlist = playlist,
                                    tracks = state.selectedPlaylistTracks,
                                    liked = state.liked,
                                    onQueueSelected = { viewModel.startQueue(playlist.name, state.selectedPlaylistTracks) },
                                    onTrackSelected = viewModel::selectTrack
                                )
                            }
                        }
                        if (state.queue.isNotEmpty()) {
                            item { UpNextSection(state.queue, state.queueIndex, state.liked, viewModel::selectTrack, viewModel::clearQueue) }
                        }
                        items(mixes) { mix -> PlaylistCard(mix, state.liked, viewModel::startQueue, viewModel::selectTrack) }
                    }
                }
            }
            PlayerBar(
                track = state.currentTrack,
                isPlaying = state.isPlaying,
                liked = state.liked[state.currentTrack.id] == true,
                status = state.status,
                queueLabel = state.queueLabel,
                visualizerBands = state.visualizerBands,
                shuffleEnabled = state.shuffleEnabled,
                repeatEnabled = state.repeatEnabled,
                onPlayPause = viewModel::togglePlayPause,
                onLike = viewModel::toggleLike,
                onLongListen = viewModel::markLongListen,
                onSkip = viewModel::skip,
                onShuffle = viewModel::toggleShuffle,
                onRepeat = viewModel::toggleRepeat,
                onStartRadio = viewModel::startRadioFromCurrent,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SearchCard(query: String, onQueryChange: (String) -> Unit, matchCount: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search songs, artists, albums, genres") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (query.isBlank()) "Showing your full music set." else "$matchCount matches",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    state: JellyMixState,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onReload: () -> Unit,
    onClearSession: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onAccentThemeSelected: (AccentTheme) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Jellyfin connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(state.serverUrl, onServerUrlChange, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.username, onUsernameChange, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onConnect, enabled = !state.isLoading, modifier = Modifier.weight(1f)) {
                    Text(if (state.isConnected) "Reconnect" else "Connect")
                }
                Button(onClick = onReload, enabled = state.isConnected && !state.isLoading, modifier = Modifier.weight(1f)) {
                    Text("Reload")
                }
            }
            Button(onClick = onClearSession, enabled = state.isConnected || state.token.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Clear session")
            }
            ThemeOptions(
                themeMode = state.themeMode,
                accentTheme = state.accentTheme,
                onThemeModeSelected = onThemeModeSelected,
                onAccentThemeSelected = onAccentThemeSelected
            )
            Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThemeOptions(
    themeMode: ThemeMode,
    accentTheme: AccentTheme,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onAccentThemeSelected: (AccentTheme) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Theme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ThemeMode.entries) { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeSelected(mode) },
                    label = { Text(mode.label) }
                )
            }
        }
        Text("Accent", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AccentTheme.entries) { accent ->
                FilterChip(
                    selected = accentTheme == accent,
                    onClick = { onAccentThemeSelected(accent) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(accent.primary)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(accent.label)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HeroCard(currentTrack: Track, serverUrl: String, connected: Boolean) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (connected) "Streaming from Jellyfin" else "Demo discovery mode", color = Color(0xFF101113), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Queued: ${currentTrack.title} from ${currentTrack.album}", color = Color(0xFF101113), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Source: $serverUrl", color = Color(0xFF101113), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun VisualizerCard(
    state: JellyMixState,
    onEnableLiveVisualizer: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Visualizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        state.visualizerMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            MusicVisualizer(
                bands = state.visualizerBands,
                isPlaying = state.isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            if (!state.visualizerPermissionGranted) {
                Button(onClick = onEnableLiveVisualizer, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable live visualizer")
                }
            }
        }
    }
}

@Composable
private fun MusicVisualizer(
    bands: List<Float>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "visualizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (compact) 760 else 1180, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "visualizerPhase"
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val lineAlpha = if (isPlaying) 0.95f else 0.35f
    val sourceBands = if (bands.isEmpty()) restingVisualizerBands() else bands

    Canvas(modifier = modifier) {
        val count = sourceBands.size.coerceAtLeast(1)
        val gap = if (compact) 3.dp.toPx() else 5.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2.dp.toPx())
        sourceBands.forEachIndexed { index, band ->
            val pulse = if (isPlaying) {
                0.74f + 0.26f * sin((phase * 6.28318f) + index * 0.61f)
            } else {
                0.26f
            }
            val normalized = (band * pulse).coerceIn(0.06f, 1f)
            val barHeight = size.height * normalized
            val x = index * (barWidth + gap) + barWidth / 2f
            val top = size.height - barHeight
            val color = when (index % 3) {
                0 -> primary
                1 -> secondary
                else -> tertiary
            }
            drawLine(
                color = color.copy(alpha = lineAlpha),
                start = androidx.compose.ui.geometry.Offset(x, size.height),
                end = androidx.compose.ui.geometry.Offset(x, top),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun DiscoveryFilters(selected: DiscoveryFilter, onSelected: (DiscoveryFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DiscoveryFilter.entries) { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(filter.label) }
            )
        }
    }
}

@Composable
private fun MixRail(
    title: String,
    mixes: List<Mix>,
    onQueueSelected: (String, List<Track>) -> Unit,
    onTrackSelected: (Track) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mixes) { mix ->
                Card(
                    modifier = Modifier
                        .width(244.dp)
                        .clickable { onQueueSelected(mix.name, mix.tracks) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Recommend, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(mix.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(mix.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${mix.tracks.size} tracks", style = MaterialTheme.typography.labelMedium)
                        mix.tracks.firstOrNull()?.let { track ->
                            Text(
                                "Starts with ${track.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onTrackSelected(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSection(title: String, tracks: List<Track>, liked: Map<String, Boolean>, onTrackSelected: (Track) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (tracks.isEmpty()) {
            EmptyState("No matching tracks", "Try another search or reload your Jellyfin library.")
        } else {
            tracks.forEach { track -> TrackRow(track, liked[track.id] == true) { onTrackSelected(track) } }
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrackRow(track: Track, liked: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(track)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${track.artist} - ${track.album}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${track.genre} / ${track.mood} / ${(track.completion * 100).roundToInt()}% complete", style = MaterialTheme.typography.labelSmall)
            }
            Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, tint = if (liked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun PlaylistCard(
    mix: Mix,
    liked: Map<String, Boolean>,
    onQueueSelected: (String, List<Track>) -> Unit,
    onTrackSelected: (Track) -> Unit
) {
    Card(
        modifier = Modifier.clickable { onQueueSelected(mix.name, mix.tracks) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(mix.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(mix.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tap card to start this queue.", style = MaterialTheme.typography.labelMedium)
            mix.tracks.take(4).forEach { track -> TrackRow(track, liked[track.id] == true) { onTrackSelected(track) } }
        }
    }
}

@Composable
private fun UpNextSection(
    queue: List<Track>,
    queueIndex: Int,
    liked: Map<String, Boolean>,
    onTrackSelected: (Track) -> Unit,
    onClearQueue: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Up next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${queueIndex + 1}/${queue.size} in queue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onClearQueue) {
                    Text("Clear")
                }
            }
            queue.drop(queueIndex + 1).take(5).forEach { track ->
                TrackRow(track, liked[track.id] == true) { onTrackSelected(track) }
            }
            if (queue.drop(queueIndex + 1).isEmpty()) {
                EmptyState("Nothing after this", "Start a mix or playlist to fill the queue.")
            }
        }
    }
}

@Composable
private fun JellyfinPlaylistRail(playlists: List<JellyfinPlaylist>, onPlaylistSelected: (JellyfinPlaylist) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Jellyfin playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(playlists) { playlist ->
                Card(
                    modifier = Modifier
                        .width(180.dp)
                        .clickable { onPlaylistSelected(playlist) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlaylistArt(playlist)
                        Text(playlist.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.childCount} items", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedPlaylistSection(
    playlist: JellyfinPlaylist,
    tracks: List<Track>,
    liked: Map<String, Boolean>,
    onQueueSelected: () -> Unit,
    onTrackSelected: (Track) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(playlist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                if (tracks.isEmpty()) "No tracks loaded yet." else "${tracks.size} playable tracks from Jellyfin.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onQueueSelected, enabled = tracks.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Play playlist queue")
            }
            tracks.take(12).forEach { track ->
                TrackRow(track, liked[track.id] == true) { onTrackSelected(track) }
            }
        }
    }
}

@Composable
private fun LibrarySummary(tracks: List<Track>, playlists: List<JellyfinPlaylist>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard("Artists", tracks.map { it.artist }.distinct().size.toString(), Modifier.weight(1f))
        StatCard("Albums", tracks.map { it.album }.distinct().size.toString(), Modifier.weight(1f))
        StatCard("Lists", playlists.size.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PlayerBar(
    track: Track,
    isPlaying: Boolean,
    liked: Boolean,
    status: String,
    queueLabel: String,
    visualizerBands: List<Float>,
    shuffleEnabled: Boolean,
    repeatEnabled: Boolean,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onLongListen: () -> Unit,
    onSkip: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onStartRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(progress = { track.completion.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(track, size = 46)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(queueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            MusicVisualizer(
                bands = visualizerBands,
                isPlaying = isPlaying,
                compact = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onLike) { Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Like") }
                IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play") }
                IconButton(onClick = onSkip) { Icon(Icons.Filled.SkipNext, contentDescription = "Skip") }
                IconButton(onClick = onRepeat) {
                    Icon(Icons.Filled.Repeat, contentDescription = "Repeat", tint = if (repeatEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onLongListen, modifier = Modifier.weight(1f)) {
                    Text("Long listen")
                }
                Button(onClick = onStartRadio, modifier = Modifier.weight(1f)) {
                    Text("Start radio")
                }
            }
        }
    }
}

@Composable
private fun AlbumArt(track: Track, size: Int = 54) {
    if (!track.imageUrl.isNullOrBlank()) {
        AsyncImage(
            model = track.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        return
    }
    val colors = when (track.genre.lowercase()) {
        "synth", "electronic" -> listOf(Color(0xFF00D9B5), Color(0xFF284BFF))
        "ambient", "classical" -> listOf(Color(0xFFB8E1FF), Color(0xFF7A89C2))
        "rock", "metal" -> listOf(Color(0xFF2E3532), Color(0xFFD8A47F))
        "hip hop", "rap" -> listOf(Color(0xFFFF6B6B), Color(0xFFFFC857))
        else -> listOf(Color(0xFFF7F3E8), Color(0xFF00D9B5))
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size((size / 3).dp)
                .clip(CircleShape)
                .background(Color(0xCC101113))
        )
    }
}

@Composable
private fun PlaylistArt(playlist: JellyfinPlaylist) {
    if (!playlist.imageUrl.isNullOrBlank()) {
        AsyncImage(
            model = playlist.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(116.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        return
    }
    Box(
        modifier = Modifier
            .size(116.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Color(0xFF101113), modifier = Modifier.size(42.dp))
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Discover("Discover", Icons.Filled.Explore),
    Library("Library", Icons.Filled.LibraryMusic),
    Playlists("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay)
}

enum class DiscoveryFilter(val label: String, val sectionTitle: String) {
    LongListens("Long listens", "Songs you keep around the longest"),
    Liked("Liked", "Favorites and strong signals"),
    LowSkips("Low skips", "Tracks you rarely skip"),
    SimilarMood("Similar mood", "More like what is queued"),
    Rediscover("Rediscover", "Worth another listen")
}

data class JellyMixState(
    val serverUrl: String,
    val username: String,
    val password: String = "",
    val token: String = "",
    val userId: String = "",
    val themeMode: ThemeMode = ThemeMode.System,
    val accentTheme: AccentTheme = AccentTheme.Jelly,
    val searchQuery: String = "",
    val discoveryFilter: DiscoveryFilter = DiscoveryFilter.LongListens,
    val tracks: List<Track>,
    val currentTrack: Track,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val queueTitle: String = "Discovery queue",
    val shuffleEnabled: Boolean = false,
    val repeatEnabled: Boolean = false,
    val jellyfinPlaylists: List<JellyfinPlaylist>,
    val selectedPlaylist: JellyfinPlaylist? = null,
    val selectedPlaylistTracks: List<Track>,
    val liked: Map<String, Boolean>,
    val skips: Map<String, Int>,
    val longListens: Map<String, Int>,
    val localPlays: Map<String, Int>,
    val recentTrackIds: List<String>,
    val visualizerBands: List<Float> = restingVisualizerBands(),
    val visualizerPermissionGranted: Boolean = false,
    val visualizerMessage: String = "Visualizer preview is active. Enable audio capture for live Jellyfin waveforms.",
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val status: String = "Ready. Connect to Jellyfin or explore demo mixes."
) {
    val isConnected: Boolean = token.isNotBlank() && userId.isNotBlank()
    val queueLabel: String =
        if (queue.isEmpty()) queueTitle else "$queueTitle ${queueIndex + 1}/${queue.size}"

    fun rankedTracks(): List<Track> =
        tracks.sortedByDescending { track ->
            recommendationScore(
                track = track,
                liked = liked[track.id] == true,
                longListens = longListens[track.id] ?: 0,
                skips = skips[track.id] ?: 0,
                localPlays = localPlays[track.id] ?: 0
            )
        }

    fun recentTracks(): List<Track> =
        recentTrackIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val mood: String,
    val durationSec: Int,
    val plays: Int,
    val completion: Float,
    val skipped: Int,
    val liked: Boolean,
    val imageUrl: String? = null
)

data class Mix(val name: String, val reason: String, val tracks: List<Track>)

data class JellyfinPlaylist(val id: String, val name: String, val childCount: Int, val imageUrl: String?)

private data class JellyfinSession(val token: String, val userId: String)

private data class JellyfinLibraryLoad(val tracks: List<Track>, val playlists: List<JellyfinPlaylist>)

data class JellyfinServerInfo(val serverName: String, val version: String)

private val sampleTracks = listOf(
    Track("sample-1", "Night Drive Home", "Glass Harbor", "After Hours", "Synth", "Late", 238, 18, 0.96f, 1, true),
    Track("sample-2", "Cloudbreak", "Mara Vale", "Soft Focus", "Indie", "Calm", 204, 13, 0.92f, 0, true),
    Track("sample-3", "Static Bloom", "Northline", "Signal Path", "Alternative", "Focused", 248, 7, 0.84f, 2, false),
    Track("sample-4", "Warm Signal", "June Reactor", "Receiver", "Electronic", "Bright", 219, 20, 0.89f, 3, true),
    Track("sample-5", "Basement Sun", "The Low Keys", "Weekend Proof", "Rock", "Drive", 187, 11, 0.73f, 4, false),
    Track("sample-6", "Blue Room", "Cassette Atlas", "Room Tone", "Ambient", "Calm", 301, 9, 0.98f, 0, true),
    Track("sample-7", "Last Train Static", "Velvet Relay", "Platform Lights", "Synth", "Late", 266, 6, 0.81f, 1, false),
    Track("sample-8", "Good Weather Lie", "Harbor Kids", "Open Windows", "Indie", "Bright", 196, 15, 0.87f, 2, true)
)

internal fun recommendationScore(track: Track, liked: Boolean, longListens: Int, skips: Int, localPlays: Int = 0): Float {
    val likeBoost = if (liked) 35f else 0f
    val completionBoost = track.completion * 30f
    val playBoost = track.plays.coerceAtMost(30) * 1.2f
    val localPlayBoost = localPlays.coerceAtMost(40) * 1.6f
    val longListenBoost = longListens * 9f
    val skipPenalty = skips * 5f
    return likeBoost + completionBoost + playBoost + localPlayBoost + longListenBoost - skipPenalty
}

internal fun buildMixes(rankedTracks: List<Track>, liked: Map<String, Boolean>, longListens: Map<String, Int>): List<Mix> {
    val longListenTracks = rankedTracks.sortedByDescending { it.durationSec + ((longListens[it.id] ?: 0) * 120) }.take(12)
    val likedTracks = rankedTracks.filter { liked[it.id] == true }.take(12)
    val rediscoverTracks = rankedTracks.sortedWith(compareBy<Track> { it.plays }.thenByDescending { it.completion }).take(12)
    val strongestGenre = likedTracks
        .groupingBy { it.genre }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    val genreTracks = strongestGenre?.let { genre -> rankedTracks.filter { it.genre == genre }.take(12) }.orEmpty()
    val strongestMood = rankedTracks
        .filter { (longListens[it.id] ?: 0) > 0 || liked[it.id] == true }
        .groupingBy { it.mood }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    val moodTracks = strongestMood?.let { mood -> rankedTracks.filter { it.mood == mood }.take(12) }.orEmpty()
    return listOf(
        Mix("Heavy Rotation", "High completion, likes, and repeat plays.", rankedTracks.take(12)),
        Mix("Long Listens", "Songs you finish or keep around the longest.", longListenTracks),
        Mix("Rediscover", "Lower-play tracks with signals worth another shot.", rediscoverTracks),
        Mix("Liked Radio", "A focused queue from your strongest favorites.", likedTracks.ifEmpty { rankedTracks.take(6) }),
        Mix("${strongestGenre ?: "Library"} Radio", "More from the sound you favor most.", genreTracks.ifEmpty { rankedTracks.take(6) }),
        Mix("${strongestMood ?: "Mood"} Flow", "A queue shaped by your current listening mood.", moodTracks.ifEmpty { rankedTracks.take(6) })
    )
}

internal fun filterTracks(tracks: List<Track>, query: String): List<Track> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return tracks
    return tracks.filter { track ->
        listOf(track.title, track.artist, track.album, track.genre, track.mood)
            .any { it.lowercase().contains(normalized) }
    }
}

internal fun discoveryTracks(
    tracks: List<Track>,
    filter: DiscoveryFilter,
    liked: Map<String, Boolean>,
    skips: Map<String, Int>,
    longListens: Map<String, Int>,
    currentTrack: Track
): List<Track> =
    when (filter) {
        DiscoveryFilter.LongListens ->
            tracks.sortedByDescending { it.durationSec + ((longListens[it.id] ?: 0) * 120) }
        DiscoveryFilter.Liked ->
            tracks.filter { liked[it.id] == true || it.liked }
        DiscoveryFilter.LowSkips ->
            tracks.sortedWith(compareBy<Track> { skips[it.id] ?: it.skipped }.thenByDescending { it.completion })
        DiscoveryFilter.SimilarMood ->
            tracks.filter { it.mood == currentTrack.mood && it.id != currentTrack.id }
                .ifEmpty { tracks.filter { it.genre == currentTrack.genre && it.id != currentTrack.id } }
        DiscoveryFilter.Rediscover ->
            tracks.filter { (liked[it.id] != true) && (longListens[it.id] ?: 0) == 0 }
                .sortedWith(compareBy<Track> { it.plays }.thenByDescending { it.completion })
    }

internal fun buildTrackRadio(
    seed: Track,
    tracks: List<Track>,
    liked: Map<String, Boolean>,
    longListens: Map<String, Int>,
    skips: Map<String, Int>,
    localPlays: Map<String, Int>
): List<Track> {
    val ranked = tracks
        .filterNot { it.id == seed.id }
        .sortedByDescending { track ->
            val similarityBoost =
                (if (track.mood == seed.mood) 50f else 0f) +
                    (if (track.genre == seed.genre) 35f else 0f) +
                    (if (track.artist == seed.artist) 15f else 0f)
            similarityBoost + recommendationScore(
                track = track,
                liked = liked[track.id] == true,
                longListens = longListens[track.id] ?: 0,
                skips = skips[track.id] ?: 0,
                localPlays = localPlays[track.id] ?: 0
            )
        }
    return (listOf(seed) + ranked).distinctBy { it.id }.take(25)
}

internal fun nextQueueIndex(currentIndex: Int, queueSize: Int, repeatEnabled: Boolean): Int {
    if (queueSize <= 1) return 0
    val next = currentIndex + 1
    return when {
        next < queueSize -> next
        repeatEnabled -> 0
        else -> queueSize - 1
    }
}

internal fun isQueueEnd(currentIndex: Int, queueSize: Int, repeatEnabled: Boolean): Boolean =
    queueSize <= 1 && !repeatEnabled || currentIndex >= queueSize - 1 && !repeatEnabled

private fun moodFromGenre(genre: String): String =
    when (genre.lowercase()) {
        "ambient", "classical", "jazz" -> "Calm"
        "electronic", "synth", "dance" -> "Drive"
        "rock", "metal", "punk" -> "Loud"
        "folk", "indie" -> "Warm"
        else -> "Library"
    }

private fun Throwable.cleanMessage(): String =
    message?.replace('\n', ' ')?.take(180) ?: javaClass.simpleName

internal fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isBlank()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val host = uri.host
    if (scheme !in setOf("http", "https") || host.isNullOrBlank()) return null
    val port = if (uri.port != -1) ":${uri.port}" else ""
    val path = uri.path?.trimEnd('/')?.takeIf { it.isNotBlank() && it != "/" }.orEmpty()
    return "$scheme://$host$port$path"
}

internal fun String.toIntMap(): Map<String, Int> =
    split(";").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
    }.toMap()

internal fun String.toBooleanMap(): Map<String, Boolean> =
    split(";").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[1].toBooleanStrictOrNull()?.let { value -> parts[0] to value } else null
    }.toMap()

internal inline fun <reified T : Enum<T>> String?.enumValueOrDefault(default: T): T =
    this?.let { value ->
        enumValues<T>().firstOrNull { it.name == value }
    } ?: default

internal fun ByteArray.toVisualizerBands(bandCount: Int = 28): List<Float> {
    if (isEmpty() || bandCount <= 0) return emptyList()
    val bucketSize = (size / bandCount).coerceAtLeast(1)
    return List(bandCount) { band ->
        val start = band * bucketSize
        val end = (start + bucketSize).coerceAtMost(size)
        if (start >= size) {
            0.06f
        } else {
            val average = (start until end).sumOf { index ->
                abs(this[index].toInt() - 128)
            } / (end - start).toFloat()
            (average / 96f).coerceIn(0.06f, 1f)
        }
    }
}

internal fun restingVisualizerBands(bandCount: Int = 28): List<Float> =
    List(bandCount) { index ->
        0.08f + ((index % 5) * 0.018f)
    }

internal fun syntheticVisualizerBands(track: Track, bandCount: Int = 28): List<Float> {
    val seed = "${track.id}-${track.title}-${track.artist}-${track.genre}-${track.mood}"
        .fold(0) { acc, char -> (acc * 31) + char.code }
    return List(bandCount) { index ->
        val wave = abs(sin((seed + index * 37).toFloat() * 0.017f))
        val rhythm = if (index % 4 == 0) 0.28f else 0f
        (0.18f + wave * 0.58f + rhythm).coerceIn(0.08f, 1f)
    }
}

internal fun Map<String, *>.toStorageString(): String =
    entries.joinToString(";") { "${it.key}=${it.value}" }
