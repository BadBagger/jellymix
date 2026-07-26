package com.smithware.jellymix

import android.Manifest
import android.app.Application
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.Toast
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.net.SocketTimeoutException
import java.net.URL
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal const val WIDGET_ACTION_PLAY_PAUSE = "com.smithware.jellymix.widget.PLAY_PAUSE"
internal const val WIDGET_ACTION_SKIP = "com.smithware.jellymix.widget.SKIP"
internal const val WIDGET_ACTION_PREVIOUS = "com.smithware.jellymix.widget.PREVIOUS"
internal const val WIDGET_ACTION_STOP = "com.smithware.jellymix.widget.STOP"
private const val DefaultJarvisPrompt = "Tell me what you want to hear. I can go deeper, keep it familiar, make it louder, chill it out, or build around an artist."

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
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) viewModel.refreshPlaybackNotification()
            }
            LaunchedEffect(Unit) {
                viewModel.setVisualizerPermission(
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                )
            }
            val state = viewModel.state
            LaunchedEffect(state.isPlaying) {
                if (
                    state.isPlaying &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            JellyMixTheme(themeMode = state.themeMode, accentTheme = state.accentTheme) {
                JellyMixApp(
                    viewModel = viewModel,
                    onRequestVisualizerPermission = {
                        visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }
        handleWidgetAction(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetAction(intent)
    }

    private fun handleWidgetAction(intent: Intent?) {
        when (intent?.action) {
            WIDGET_ACTION_PLAY_PAUSE -> viewModel.togglePlayPause()
            WIDGET_ACTION_SKIP -> viewModel.skip()
            WIDGET_ACTION_PREVIOUS -> viewModel.previous()
            WIDGET_ACTION_STOP -> viewModel.stopPlayback()
        }
    }
}

class JellyMixViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("jellymix", Application.MODE_PRIVATE)
    private val client = JellyfinClient()
    private var player: MediaPlayer? = null
    private var preloadedPlayer: MediaPlayer? = null
    private var preloadedTrackId: String? = null
    private var visualizer: Visualizer? = null
    private val visualizerAnalysis = VisualizerAnalysisEngine()
    private var lastVisualizerUpdateMs = 0L
    private val appContext = application.applicationContext
    private val playbackNotificationController = PlaybackNotificationController(appContext)
    private val musicAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val widgetPlaybackController = object : WidgetPlaybackController {
        override fun togglePlayPause() = this@JellyMixViewModel.togglePlayPause()
        override fun skip() = this@JellyMixViewModel.skip()
        override fun previous() = this@JellyMixViewModel.previous()
        override fun stopPlayback() = this@JellyMixViewModel.stopPlayback()
    }

    var state by mutableStateOf(
        run {
            val token = prefs.getString("token", "").orEmpty()
            val userId = prefs.getString("userId", "").orEmpty()
            val cachedLibrary = readCachedLibrary()
            val hasSavedSession = token.isNotBlank() && userId.isNotBlank()
            val initialTracks = if (hasSavedSession) cachedLibrary.tracks.ifEmpty { sampleTracks } else sampleTracks
            val initialFeatures = readAudioFeatureCache(initialTracks)
            val savedQueueIds = prefs.getString("queueIds", null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            val savedQueue = savedQueueIds.mapNotNull { id -> initialTracks.firstOrNull { it.id == id } }
            val savedCurrentTrackId = prefs.getString("currentTrackId", null)
            val initialCurrent = initialTracks.firstOrNull { it.id == savedCurrentTrackId }
                ?: savedQueue.firstOrNull()
                ?: initialTracks.first()
            val hasCachedSession = hasSavedSession && cachedLibrary.tracks.isNotEmpty()
            JellyMixState(
                serverUrl = prefs.getString("serverUrl", "http://jellyfin.local:8096").orEmpty(),
                username = prefs.getString("username", "").orEmpty(),
                token = token,
                userId = userId,
                themeMode = prefs.getString("themeMode", null).enumValueOrDefault(ThemeMode.Dark),
                accentTheme = prefs.getString("accentTheme", null).enumValueOrDefault(AccentTheme.Jelly),
                visualizerDebugOverlay = prefs.getBoolean("visualizerDebugOverlay", false),
                nowPlayingVisualizerStage = prefs.getBoolean("nowPlayingVisualizerStage", false),
                selectedTab = prefs.getString("selectedTab", null).toTabOrDefault(Tab.Home),
                mixesSegment = prefs.getString("mixesSegment", null).enumValueOrDefault(MixesSegment.Mixes),
                libraryBrowseMode = prefs.getString("libraryBrowseMode", null).enumValueOrDefault(LibraryBrowseMode.Tracks),
                tracks = initialTracks,
                rawTrackCount = prefs.getInt("rawTrackCount", initialTracks.size),
                audioFeatures = initialFeatures,
                currentTrack = initialCurrent,
                queue = savedQueue,
                queueIndex = savedQueue.indexOfFirst { it.id == initialCurrent.id }.coerceAtLeast(0),
                queueTitle = prefs.getString("queueTitle", "Discovery queue").orEmpty().ifBlank { "Discovery queue" },
                djMode = prefs.getString("djMode", null).enumValueOrDefault(GuestDjMode.Flow),
                jellyfinPlaylists = cachedLibrary.playlists,
                selectedPlaylistTracks = emptyList(),
                liked = prefs.getString("liked", null)?.toBooleanMap() ?: initialTracks.associate { it.id to it.liked },
                skips = prefs.getString("skips", null)?.toIntMap() ?: initialTracks.associate { it.id to it.skipped },
                longListens = prefs.getString("longListens", null)?.toIntMap() ?: initialTracks.associate { it.id to 0 },
                localPlays = prefs.getString("localPlays", null)?.toIntMap() ?: initialTracks.associate { it.id to 0 },
                recentTrackIds = prefs.getString("recentTrackIds", null)?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
                libraryLoaded = hasCachedSession,
                status = if (hasCachedSession) "Ready from saved library. Refreshing Jellyfin..." else "Ready. Connect to Jellyfin or explore demo mixes."
            )
        }
    )
        private set

    init {
        WidgetPlaybackBridge.register(widgetPlaybackController)
        if (state.token.isNotBlank() && state.userId.isNotBlank()) {
            loadLibrary(backgroundRefresh = state.libraryLoaded)
        }
        preloadCurrentTrack()
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

    fun setVibeQuery(value: String) {
        state = state.copy(vibeQuery = value)
    }

    fun setSelectedTab(value: Tab) {
        state = state.copy(selectedTab = value)
        prefs.edit().putString("selectedTab", value.name).apply()
    }

    fun setMixesSegment(value: MixesSegment) {
        state = state.copy(mixesSegment = value)
        prefs.edit().putString("mixesSegment", value.name).apply()
    }

    fun setLibraryBrowseMode(value: LibraryBrowseMode) {
        state = state.copy(libraryBrowseMode = value)
        prefs.edit().putString("libraryBrowseMode", value.name).apply()
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

    fun setVisualizerDebugOverlay(value: Boolean) {
        state = state.copy(visualizerDebugOverlay = value)
        prefs.edit().putBoolean("visualizerDebugOverlay", value).apply()
    }

    fun setNowPlayingVisualizerStage(value: Boolean) {
        state = state.copy(nowPlayingVisualizerStage = value)
        prefs.edit().putBoolean("nowPlayingVisualizerStage", value).apply()
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

    fun refreshPlaybackNotification() {
        playbackNotificationController.update(state)
    }

    fun seekToProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        val durationMs = (state.currentTrack.durationSec * 1000L).coerceAtLeast(1L)
        runCatching {
            player?.seekTo((durationMs * clamped).toInt())
        }
        state = state.copy(currentTrack = state.currentTrack.copy(completion = clamped))
        refreshPlaybackNotification()
        persistPlaybackState()
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
                    libraryLoaded = false,
                    status = "Connected. Loading music library..."
                )
                loadLibrary()
            }.onFailure { error ->
                state = state.copy(isLoading = false, libraryLoaded = false, status = "Connection failed: ${error.cleanMessage()}")
            }
        }
    }

    fun loadLibrary(backgroundRefresh: Boolean = false) {
        val serverUrl = normalizeServerUrl(state.serverUrl)
        if (serverUrl == null) {
            state = state.copy(status = "Enter a valid Jellyfin URL before reloading.")
            return
        }
        if (state.token.isBlank() || state.userId.isBlank()) return
        state = state.copy(
            serverUrl = serverUrl,
            isLoading = !backgroundRefresh,
            status = if (backgroundRefresh) "Refreshing Jellyfin in the background..." else "Loading Jellyfin music..."
        )
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
                val features = readAudioFeatureCache(tracks)
                val mergedLiked = tracks.associate { it.id to (state.liked[it.id] ?: it.liked) }
                val mergedSkips = tracks.associate { it.id to (state.skips[it.id] ?: it.skipped) }
                val mergedLong = tracks.associate { it.id to (state.longListens[it.id] ?: 0) }
                val mergedPlays = tracks.associate { it.id to (state.localPlays[it.id] ?: 0) }
                state = state.copy(
                    isLoading = false,
                    libraryLoaded = true,
                    tracks = tracks,
                    rawTrackCount = library.rawTrackCount.takeIf { it > 0 } ?: tracks.sumOf { 1 + it.alternates.size },
                    audioFeatures = features,
                    currentTrack = tracks.firstOrNull() ?: state.currentTrack,
                    liked = mergedLiked,
                    skips = mergedSkips,
                    longListens = mergedLong,
                    localPlays = mergedPlays,
                    recentTrackIds = state.recentTrackIds.filter { id -> tracks.any { it.id == id } },
                    jellyfinPlaylists = library.playlists,
                    selectedPlaylist = null,
                    selectedPlaylistTracks = emptyList(),
                    status = "Connected. Loaded ${tracks.size} deduped tracks from ${library.rawTrackCount.takeIf { it > 0 } ?: tracks.size} library items and ${library.playlists.size} playlists."
                )
                persistCachedLibrary(tracks, library.playlists)
                persistAudioFeatureCache(features)
                persistSignals()
            }.onFailure { error ->
                state = state.copy(
                    isLoading = false,
                    libraryLoaded = state.libraryLoaded,
                    status = if (state.libraryLoaded) {
                        "Using saved library. Jellyfin refresh failed: ${error.cleanMessage()}"
                    } else {
                        "Not connected to library: ${error.cleanMessage()}"
                    }
                )
            }
        }
    }

    fun selectTrack(track: Track) {
        val queueIndex = state.queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        val queue = if (state.queue.any { it.id == track.id }) state.queue else listOf(track)
        val queueTitle = if (state.queue.any { it.id == track.id }) state.queueTitle else "Selected track"
        state = state.copy(currentTrack = track, queue = queue, queueIndex = queueIndex, queueTitle = queueTitle)
        persistPlaybackState()
        if (state.isPlaying) playCurrentTrack() else preloadCurrentTrack()
    }

    fun startQueue(title: String, tracks: List<Track>) {
        val playableTracks = deduplicateTracks(tracks)
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
        persistPlaybackState()
        if (state.isPlaying) playCurrentTrack() else preloadCurrentTrack()
    }

    fun startShuffledQueue(title: String, tracks: List<Track>) {
        val playableTracks = deduplicateTracks(tracks)
        if (playableTracks.isEmpty()) {
            state = state.copy(status = "No tracks available for $title.")
            return
        }
        val current = playableTracks.random()
        val queue = listOf(current) + playableTracks.filterNot { it.id == current.id }.shuffled()
        state = state.copy(
            currentTrack = current,
            queue = queue,
            queueIndex = 0,
            queueTitle = "$title shuffle",
            shuffleEnabled = true,
            status = "Shuffled ${queue.size} tracks from $title."
        )
        persistPlaybackState()
        if (state.isPlaying) playCurrentTrack() else preloadCurrentTrack()
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
                val canonicalPlaylistTracks = deduplicateTracks(playlistTracks)
                val mergedLiked = (state.tracks + canonicalPlaylistTracks).associate { it.id to (state.liked[it.id] ?: it.liked) }
                val mergedSkips = (state.tracks + canonicalPlaylistTracks).associate { it.id to (state.skips[it.id] ?: it.skipped) }
                val mergedLong = (state.tracks + canonicalPlaylistTracks).associate { it.id to (state.longListens[it.id] ?: 0) }
                val mergedPlays = (state.tracks + canonicalPlaylistTracks).associate { it.id to (state.localPlays[it.id] ?: 0) }
                state = state.copy(
                    isLoading = false,
                    selectedPlaylistTracks = canonicalPlaylistTracks,
                    liked = mergedLiked,
                    skips = mergedSkips,
                    longListens = mergedLong,
                    localPlays = mergedPlays,
                    status = "Loaded ${canonicalPlaylistTracks.size} deduped tracks from ${playlistTracks.size} playlist items."
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
                audioFrame = ambientFrame(),
                visualizerMessage = "Visualizer paused.",
                status = "Paused demo playback."
            )
            persistPlaybackState()
            return
        }
        val activePlayer = player
        if (activePlayer != null && activePlayer.isPlaying) {
            activePlayer.pause()
            releaseAudioVisualizer()
            state = state.copy(
                isPlaying = false,
                visualizerBands = restingVisualizerBands(),
                audioFrame = ambientFrame(),
                visualizerMessage = "Visualizer paused.",
                status = "Paused."
            )
            persistPlaybackState()
            return
        }
        playCurrentTrack()
    }

    fun startRadioFromCurrent() {
        val seed = state.currentTrack
        val radioTracks = buildGuestDjQueue(
            mode = state.djMode,
            seed = seed,
            tracks = state.tracks,
            liked = state.liked,
            longListens = state.longListens,
            skips = state.skips,
            localPlays = state.localPlays,
            recentlyPlayedIds = state.recentTrackIds
        )
        startQueue("Jarvis DJ ${state.djMode.label}", radioTracks)
    }

    fun setDjDraft(value: String) {
        state = state.copy(djDraft = value)
    }

    fun applyGuestDjMode(mode: GuestDjMode) {
        val queue = buildGuestDjQueue(
            mode = mode,
            seed = state.currentTrack,
            tracks = state.tracks,
            liked = state.liked,
            longListens = state.longListens,
            skips = state.skips,
            localPlays = state.localPlays,
            recentlyPlayedIds = state.recentTrackIds
        )
        val message = "I switched to ${mode.label}. ${mode.description}"
        state = state.copy(
            djMode = mode,
            queue = queue,
            queueIndex = queue.indexOfFirst { it.id == state.currentTrack.id }.coerceAtLeast(0),
            queueTitle = "Jarvis DJ: ${mode.label}",
            djMessages = (state.djMessages + DjMessage("Jarvis", message)).takeLast(6),
            status = message
        )
        persistPlaybackState()
        if (state.isPlaying) playCurrentTrack() else preloadCurrentTrack()
    }

    fun sendDjPrompt(promptOverride: String? = null) {
        val prompt = (promptOverride ?: state.djDraft).trim()
        if (prompt.isBlank()) return
        val mode = inferGuestDjMode(prompt, state.djMode)
        val seed = findPromptSeed(prompt, state.tracks) ?: state.currentTrack
        val queue = buildJarvisDjQueue(
            prompt = prompt,
            mode = mode,
            seed = seed,
            tracks = state.tracks,
            liked = state.liked,
            longListens = state.longListens,
            skips = state.skips,
            localPlays = state.localPlays,
            recentlyPlayedIds = state.recentTrackIds
        )
        val current = queue.firstOrNull() ?: state.currentTrack
        val reply = jarvisDjReply(prompt, mode, current, queue)
        state = state.copy(
            currentTrack = current,
            queue = queue,
            queueIndex = 0,
            queueTitle = "Jarvis DJ",
            djMode = mode,
            djDraft = "",
            djMessages = (state.djMessages + DjMessage("You", prompt) + DjMessage("Jarvis", reply)).takeLast(6),
            status = reply
        )
        persistPlaybackState()
        if (state.isPlaying) playCurrentTrack() else preloadCurrentTrack()
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

    fun previous() {
        val queue = state.queue.ifEmpty { state.rankedTracks() }
        val previousIndex = previousQueueIndex(state.queueIndex, queue.size, state.repeatEnabled)
        val previousTrack = queue.getOrNull(previousIndex) ?: state.currentTrack
        state = state.copy(
            currentTrack = previousTrack,
            queue = queue,
            queueIndex = previousIndex,
            queueTitle = if (state.queue.isEmpty()) "Discovery queue" else state.queueTitle,
            status = "Queued ${previousTrack.title}."
        )
        persistPlaybackState()
        if (state.isPlaying) playCurrentTrack() else preloadCurrentTrack()
    }

    fun stopPlayback() {
        player?.stop()
        player?.release()
        player = null
        releasePreloadedPlayer()
        releaseAudioVisualizer()
        state = state.copy(
            isPlaying = false,
            visualizerBands = restingVisualizerBands(),
            audioFrame = ambientFrame(),
            visualizerMessage = "Visualizer stopped.",
            status = "Stopped playback."
        )
        persistPlaybackState()
        playbackNotificationController.cancel()
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
        persistPlaybackState()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val queue = state.queue.toMutableList()
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        val moving = queue.removeAt(fromIndex)
        queue.add(toIndex, moving)
        val currentIndex = queue.indexOfFirst { it.id == state.currentTrack.id }.coerceAtLeast(0)
        state = state.copy(
            queue = queue,
            queueIndex = currentIndex,
            status = "Moved ${moving.title} in the queue."
        )
        persistPlaybackState()
    }

    fun removeQueueItem(index: Int) {
        val queue = state.queue.toMutableList()
        if (index !in queue.indices) return
        val removed = queue.removeAt(index)
        val wasCurrent = removed.id == state.currentTrack.id
        val nextCurrent = if (removed.id == state.currentTrack.id) queue.getOrNull(index.coerceAtMost(queue.lastIndex)) ?: state.currentTrack else state.currentTrack
        val currentIndex = queue.indexOfFirst { it.id == nextCurrent.id }.coerceAtLeast(0)
        state = state.copy(
            currentTrack = nextCurrent,
            queue = queue,
            queueIndex = currentIndex,
            status = "Removed ${removed.title} from the queue."
        )
        persistPlaybackState()
        if (wasCurrent && state.isPlaying) playCurrentTrack() else preloadCurrentTrack()
    }

    fun playNext(track: Track) {
        val baseQueue = state.queue.ifEmpty { listOf(state.currentTrack) }
        val insertIndex = (state.queueIndex + 1).coerceIn(1, baseQueue.size)
        val queue = baseQueue.filterNot { it.id == track.id }.toMutableList()
        queue.add(insertIndex.coerceAtMost(queue.size), track)
        state = state.copy(
            queue = queue,
            queueIndex = queue.indexOfFirst { it.id == state.currentTrack.id }.coerceAtLeast(0),
            queueTitle = state.queueTitle.takeIf { state.queue.isNotEmpty() } ?: "Manual queue",
            status = "${track.title} will play next."
        )
        persistPlaybackState()
        preloadCurrentTrack()
    }

    fun addToQueue(track: Track) {
        val queue = (state.queue.ifEmpty { listOf(state.currentTrack) } + track).distinctBy { it.id }
        state = state.copy(
            queue = queue,
            queueIndex = queue.indexOfFirst { it.id == state.currentTrack.id }.coerceAtLeast(0),
            queueTitle = state.queueTitle.takeIf { state.queue.isNotEmpty() } ?: "Manual queue",
            status = "Added ${track.title} to the queue."
        )
        persistPlaybackState()
    }

    fun clearSession() {
        player?.release()
        releasePreloadedPlayer()
        releaseAudioVisualizer()
        player = null
        prefs.edit()
            .remove("token")
            .remove("userId")
            .remove("cachedTracks")
            .remove("cachedPlaylists")
            .remove("currentTrackId")
            .remove("queueIds")
            .remove("queueTitle")
            .remove("djMode")
            .remove("isPlaying")
            .apply()
        playbackNotificationController.cancel()
        state = state.copy(
            token = "",
            userId = "",
            password = "",
            tracks = sampleTracks,
            rawTrackCount = sampleTracks.size,
            audioFeatures = sampleTracks.associate { it.id to inferAudioFeatures(it) },
            currentTrack = sampleTracks.first(),
            queue = emptyList(),
            queueIndex = 0,
            queueTitle = "Discovery queue",
            djMode = GuestDjMode.Flow,
            djDraft = "",
            djMessages = listOf(DjMessage("Jarvis", DefaultJarvisPrompt)),
            jellyfinPlaylists = emptyList(),
            selectedPlaylist = null,
            selectedPlaylistTracks = emptyList(),
            libraryLoaded = false,
            isPlaying = false,
            visualizerBands = restingVisualizerBands(),
            audioFrame = ambientFrame(),
            visualizerMessage = "Visualizer preview is active. Enable audio capture for live Jellyfin waveforms.",
            status = "Session cleared. Demo discovery mode is active."
        )
        JellyMixWidgetProvider.updateAll(getApplication())
    }

    private fun advanceQueue(countSkip: Boolean, keepPlaying: Boolean) {
        val currentId = state.currentTrack.id
        if (countSkip) {
            state = state.copy(skips = state.skips + (currentId to ((state.skips[currentId] ?: 0) + 1)))
        }
        val fallbackQueue = state.rankedTracks()
        val hadQueue = state.queue.isNotEmpty()
        val queue = state.queue.ifEmpty {
            buildAutoplayQueue(
                seed = state.currentTrack,
                tracks = state.tracks,
                liked = state.liked,
                longListens = state.longListens,
                skips = state.skips,
                localPlays = state.localPlays,
                recentlyPlayedIds = state.recentTrackIds
            ).ifEmpty { fallbackQueue }
        }
        val reachedEnd = hadQueue && isQueueEnd(state.queueIndex, queue.size, state.repeatEnabled)
        val nextQueue = if (reachedEnd && !state.repeatEnabled) {
            buildAutoplayQueue(
                seed = state.currentTrack,
                tracks = state.tracks,
                liked = state.liked,
                longListens = state.longListens,
                skips = state.skips,
                localPlays = state.localPlays,
                recentlyPlayedIds = state.recentTrackIds
            )
        } else {
            queue
        }
        val nextIndex = when {
            !hadQueue -> 0
            reachedEnd && !state.repeatEnabled -> 0
            else -> nextQueueIndex(state.queueIndex, nextQueue.size, state.repeatEnabled)
        }
        val nextTrack = nextQueue.getOrNull(nextIndex) ?: fallbackQueue.first()
        state = state.copy(
            currentTrack = nextTrack,
            queue = nextQueue,
            queueIndex = nextIndex,
            queueTitle = if (!hadQueue || reachedEnd && !state.repeatEnabled) "Autoplay radio" else state.queueTitle,
            isPlaying = keepPlaying,
            status = if (!hadQueue || reachedEnd && !state.repeatEnabled) "Autoplaying ${nextTrack.title}." else "Queued ${nextTrack.title}."
        )
        persistPlaybackState()
        persistSignals()
        if (state.isPlaying) playCurrentTrack() else preloadCurrentTrack()
    }

    fun toggleShuffle() {
        val current = state.currentTrack
        val source = state.queue.ifEmpty { state.rankedTracks() }
        val reordered = if (!state.shuffleEnabled) {
            listOf(current) + source.filterNot { it.id == current.id }.shuffled()
        } else {
            source.sortedByDescending {
                recommendationScore(
                    it,
                    state.liked[it.id] == true,
                    state.longListens[it.id] ?: 0,
                    state.skips[it.id] ?: 0,
                    state.localPlays[it.id] ?: 0
                )
            }
        }
        state = state.copy(
            shuffleEnabled = !state.shuffleEnabled,
            queue = reordered,
            queueIndex = reordered.indexOfFirst { it.id == current.id }.coerceAtLeast(0),
            status = if (!state.shuffleEnabled) "Shuffle on." else "Shuffle off."
        )
        persistPlaybackState()
    }

    fun toggleRepeat() {
        state = state.copy(
            repeatEnabled = !state.repeatEnabled,
            status = if (!state.repeatEnabled) "Repeat queue on." else "Repeat queue off."
        )
        persistPlaybackState()
    }

    private fun playCurrentTrack() {
        val track = state.currentTrack
        if (state.token.isBlank() || track.id.startsWith("sample-")) {
            releaseAudioVisualizer()
            releasePreloadedPlayer()
            recordPlayStart(track)
            state = state.copy(
                isPlaying = true,
                visualizerBands = syntheticVisualizerBands(track),
                audioFrame = visualizerAnalysis.ambient(track),
                visualizerMessage = "Preview visualizer is reacting to the queued demo track.",
                status = "Demo playback active. Connect Jellyfin to stream real audio."
            )
            persistPlaybackState()
            return
        }
        val streamUrl = client.streamUrl(state.serverUrl, track.id, state.token)
        val preparedPlayer = preloadedPlayer
        if (preparedPlayer != null && preloadedTrackId == track.id) {
            runCatching {
                releaseAudioVisualizer()
                player?.release()
                preloadedPlayer = null
                preloadedTrackId = null
                player = preparedPlayer
                configureActivePlayer(preparedPlayer, track)
                preparedPlayer.start()
                recordPlayStart(track)
                state = state.copy(
                    isPlaying = true,
                    visualizerBands = syntheticVisualizerBands(track),
                    audioFrame = visualizerAnalysis.ambient(track),
                    status = "Playing ${track.title}."
                )
                persistPlaybackState()
                startAudioVisualizer(preparedPlayer.audioSessionId)
            }.onFailure { error ->
                preparedPlayer.release()
                player = null
                state = state.copy(status = "Preloaded playback failed: ${error.cleanMessage()}")
                playCurrentTrackWithoutPreload(track, streamUrl)
            }
            return
        }
        playCurrentTrackWithoutPreload(track, streamUrl)
    }

    private fun playCurrentTrackWithoutPreload(track: Track, streamUrl: String) {
        state = state.copy(
            visualizerBands = syntheticVisualizerBands(track),
            audioFrame = visualizerAnalysis.ambient(track),
            status = "Buffering ${track.title}..."
        )
        runCatching {
            releaseAudioVisualizer()
            releasePreloadedPlayer()
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(musicAudioAttributes)
                setDataSource(appContext, Uri.parse(streamUrl))
                setOnPreparedListener {
                    it.start()
                    recordPlayStart(track)
                    state = state.copy(
                        isPlaying = true,
                        visualizerBands = syntheticVisualizerBands(track),
                        audioFrame = visualizerAnalysis.ambient(track),
                        status = "Playing ${track.title}."
                    )
                    persistPlaybackState()
                    startAudioVisualizer(it.audioSessionId)
                }
                setOnCompletionListener {
                    handlePlaybackCompletion()
                }
                setOnErrorListener { _, _, _ ->
                    releaseAudioVisualizer()
                    state = state.copy(
                        isPlaying = false,
                        visualizerBands = restingVisualizerBands(),
                        audioFrame = ambientFrame(),
                        visualizerMessage = "Visualizer paused after playback error.",
                        status = "Playback failed for ${track.title}."
                    )
                    persistPlaybackState()
                    true
                }
                prepareAsync()
            }
        }.onFailure { error ->
            releaseAudioVisualizer()
            state = state.copy(
                isPlaying = false,
                visualizerBands = restingVisualizerBands(),
                audioFrame = ambientFrame(),
                visualizerMessage = "Visualizer paused after playback error.",
                status = "Playback failed: ${error.cleanMessage()}"
            )
            persistPlaybackState()
        }
    }

    private fun configureActivePlayer(mediaPlayer: MediaPlayer, track: Track) {
        mediaPlayer.setOnCompletionListener {
            handlePlaybackCompletion()
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            releaseAudioVisualizer()
            state = state.copy(
                isPlaying = false,
                visualizerBands = restingVisualizerBands(),
                audioFrame = ambientFrame(),
                visualizerMessage = "Visualizer paused after playback error.",
                status = "Playback failed for ${track.title}."
            )
            persistPlaybackState()
            true
        }
    }

    private fun handlePlaybackCompletion() {
        releaseAudioVisualizer()
        markLongListen()
        advanceQueue(countSkip = false, keepPlaying = true)
    }

    private fun preloadCurrentTrack() {
        val track = state.currentTrack
        if (state.isPlaying || state.token.isBlank() || track.id.startsWith("sample-")) return
        if (preloadedTrackId == track.id && preloadedPlayer != null) return
        releasePreloadedPlayer()
        val streamUrl = client.streamUrl(state.serverUrl, track.id, state.token)
        runCatching {
            preloadedTrackId = track.id
            preloadedPlayer = MediaPlayer().apply {
                setAudioAttributes(musicAudioAttributes)
                setDataSource(appContext, Uri.parse(streamUrl))
                setOnPreparedListener {
                    if (state.currentTrack.id != track.id || state.isPlaying) {
                        releasePreloadedPlayer()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    releasePreloadedPlayer()
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            releasePreloadedPlayer()
        }
    }

    private fun releasePreloadedPlayer() {
        preloadedPlayer?.release()
        preloadedPlayer = null
        preloadedTrackId = null
    }

    private fun startAudioVisualizer(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (!state.visualizerPermissionGranted) {
            state = state.copy(
                visualizerBands = syntheticVisualizerBands(state.currentTrack),
                audioFrame = visualizerAnalysis.ambient(state.currentTrack),
                visualizerMessage = "Tap Enable live visualizer for audio-reactive Jellyfin feedback."
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
                            val frame = visualizerAnalysis.analyzeWaveform(waveform, now)
                            viewModelScope.launch {
                                state = state.copy(
                                    visualizerBands = frame.bands,
                                    audioFrame = frame,
                                    visualizerMessage = "Live Jellyfin audio-reactive feedback."
                                )
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            val now = System.currentTimeMillis()
                            if (fft == null || now - lastVisualizerUpdateMs < 33L) return
                            lastVisualizerUpdateMs = now
                            val frame = visualizerAnalysis.analyzeVisualizerFft(fft, samplingRate, now)
                            viewModelScope.launch {
                                state = state.copy(
                                    visualizerBands = frame.bands,
                                    audioFrame = frame,
                                    visualizerMessage = "Live Jellyfin FFT feedback."
                                )
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    true
                )
                enabled = true
            }
        }.onFailure { error ->
            state = state.copy(
                visualizerBands = syntheticVisualizerBands(state.currentTrack),
                audioFrame = visualizerAnalysis.ambient(state.currentTrack),
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

    private fun persistPlaybackState() {
        prefs.edit()
            .putString("currentTrackId", state.currentTrack.id)
            .putString("queueIds", state.queue.joinToString(",") { it.id })
            .putInt("queueIndex", state.queueIndex)
            .putString("queueTitle", state.queueTitle)
            .putString("djMode", state.djMode.name)
            .putBoolean("isPlaying", state.isPlaying)
            .apply()
        JellyMixWidgetProvider.updateAll(getApplication())
        playbackNotificationController.update(state)
    }

    private fun persistCachedLibrary(tracks: List<Track>, playlists: List<JellyfinPlaylist>) {
        prefs.edit()
            .putString("cachedTracks", tracks.toTrackCacheString())
            .putString("cachedPlaylists", playlists.toPlaylistCacheString())
            .putInt("rawTrackCount", tracks.sumOf { 1 + it.alternates.size })
            .apply()
    }

    private fun readCachedLibrary(): JellyfinLibraryLoad =
        prefs.getString("cachedTracks", null)?.toTrackList().orEmpty().let { cachedTracks ->
            val dedupedTracks = deduplicateTracks(cachedTracks)
            JellyfinLibraryLoad(
                tracks = dedupedTracks,
                playlists = prefs.getString("cachedPlaylists", null)?.toPlaylistList().orEmpty(),
                rawTrackCount = prefs.getInt("rawTrackCount", cachedTracks.size)
            )
        }

    private fun readAudioFeatureCache(tracks: List<Track>): Map<String, TrackAudioFeatures> {
        val cached = prefs.getString("audioFeatures", null)?.toAudioFeatureMap().orEmpty()
        return deduplicateTracks(tracks).associate { track -> track.id to (cached[track.id] ?: inferAudioFeatures(track)) }
    }

    private fun persistAudioFeatureCache(features: Map<String, TrackAudioFeatures>) {
        prefs.edit().putString("audioFeatures", features.toAudioFeatureCacheString()).apply()
    }

    private fun recordPlayStart(track: Track) {
        val recent = listOf(track.id) + state.recentTrackIds
        state = state.copy(
            localPlays = state.localPlays + (track.id to ((state.localPlays[track.id] ?: 0) + 1)),
            recentTrackIds = recent.take(60)
        )
        persistSignals()
    }

    override fun onCleared() {
        WidgetPlaybackBridge.unregister(widgetPlaybackController)
        releasePreloadedPlayer()
        player?.release()
        player = null
        playbackNotificationController.release()
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
            append("&StartIndex=0&Limit=10000")
            append("&Fields=Genres,UserData,RunTimeTicks,Album,Artists,ImageTags,AlbumId,AlbumPrimaryImageTag,Bitrate,MediaSources,Path")
            append("&SortBy=DatePlayed,SortName&SortOrder=Descending")
            if (!musicLibraryId.isNullOrBlank()) append("&ParentId=${Uri.encode(musicLibraryId)}")
        }
        val items = requestJson(url, "GET", null, token).optJSONArray("Items") ?: return emptyList()
        return deduplicateTracks(items.toTracks(serverUrl, token))
    }

    fun fetchPlaylists(serverUrl: String, userId: String, token: String): List<JellyfinPlaylist> {
        val url = "$serverUrl/Users/$userId/Items?Recursive=true&IncludeItemTypes=Playlist&Fields=ChildCount,ItemCounts,ImageTags&SortBy=SortName"
        val items = requestJson(url, "GET", null, token).optJSONArray("Items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.getJSONObject(index)
            val id = item.optString("Id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val fetchedCount = runCatching {
                requestJson("$serverUrl/Playlists/${Uri.encode(id)}/Items?UserId=${Uri.encode(userId)}&Limit=1", "GET", null, token)
                    .optInt("TotalRecordCount", 0)
            }.getOrDefault(0)
            JellyfinPlaylist(
                id = id,
                name = item.optString("Name", "Playlist"),
                childCount = listOf(
                    item.optInt("ChildCount", 0),
                    item.optInt("RecursiveItemCount", 0),
                    item.optInt("SongCount", 0),
                    item.optJSONObject("ItemCounts")?.optInt("SongCount", 0) ?: 0,
                    fetchedCount
                ).maxOrNull() ?: 0,
                imageUrl = imageUrlOrNull(serverUrl, id, token, item.hasPrimaryImage())
            )
        }
    }

    fun fetchPlaylistTracks(serverUrl: String, userId: String, token: String, playlistId: String): List<Track> {
        val playlistUrl = "$serverUrl/Playlists/${Uri.encode(playlistId)}/Items?UserId=${Uri.encode(userId)}&Fields=Genres,UserData,RunTimeTicks,Album,Artists,ImageTags,AlbumId,AlbumPrimaryImageTag,Bitrate,MediaSources,Path"
        val items = runCatching {
            requestJson(playlistUrl, "GET", null, token).optJSONArray("Items")
        }.getOrNull()
        if (items != null) return deduplicateTracks(items.toTracks(serverUrl, token))

        val fallbackUrl = "$serverUrl/Users/$userId/Items?ParentId=${Uri.encode(playlistId)}&Recursive=true&IncludeItemTypes=Audio&Fields=Genres,UserData,RunTimeTicks,Album,Artists,ImageTags,AlbumId,AlbumPrimaryImageTag,Bitrate,MediaSources,Path"
        return deduplicateTracks(requestJson(fallbackUrl, "GET", null, token)
            .optJSONArray("Items")
            ?.toTracks(serverUrl, token)
            .orEmpty())
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

    private fun imageUrlOrNull(serverUrl: String, itemId: String, token: String, hasPrimaryImage: Boolean): String? =
        if (hasPrimaryImage) imageUrl(serverUrl, itemId, token) else null

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
            val artist = if (artists != null && artists.length() > 0) artists.optString(0) else item.optString("AlbumArtist")
            val genres = item.optJSONArray("Genres")
            val genreValues = (0 until (genres?.length() ?: 0)).mapNotNull { genreIndex -> genres?.optString(genreIndex)?.takeIf { it.isNotBlank() } }
            val metadata = cleanTrackMetadata(
                title = item.optString("Name"),
                artist = artist,
                album = item.optString("Album"),
                genres = genreValues,
                fallbackPath = item.optString("Path")
            )
            val ticks = item.optLong("RunTimeTicks", 0L)
            val albumId = item.optString("AlbumId").takeIf { it.isNotBlank() }
            val imageItemId = when {
                item.hasPrimaryImage() -> id
                !albumId.isNullOrBlank() && item.optString("AlbumPrimaryImageTag").isNotBlank() -> albumId
                else -> null
            }
            val mediaSources = item.optJSONArray("MediaSources")
            val sourceBitrate = (0 until (mediaSources?.length() ?: 0)).maxOfOrNull { sourceIndex ->
                mediaSources?.optJSONObject(sourceIndex)?.optInt("Bitrate", 0) ?: 0
            } ?: 0
            Track(
                id = id,
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                genre = metadata.genre,
                mood = moodFromGenre(metadata.genre, metadata.tags),
                durationSec = (ticks / 10_000_000L).toInt().coerceAtLeast(1),
                plays = userData?.optInt("PlayCount", 0) ?: 0,
                completion = ((userData?.optDouble("PlayedPercentage", 0.0) ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
                skipped = 0,
                liked = userData?.optBoolean("IsFavorite", false) ?: false,
                imageUrl = imageItemId?.let { imageUrl(serverUrl, it, token) },
                bitrate = maxOf(item.optInt("Bitrate", 0), sourceBitrate),
                tags = metadata.tags,
                filename = metadata.filename
            )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JellyMixApp(
    viewModel: JellyMixViewModel,
    onRequestVisualizerPermission: () -> Unit
) {
    var showNowPlaying by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showMiniPlayer by remember { mutableStateOf(true) }
    val state = viewModel.state
    LaunchedEffect(state.currentTrack.id) {
        showMiniPlayer = true
    }
    val rankedTracks = deduplicateTracks(state.rankedTracks())
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
    val recentTrackPlays = state.recentTrackPlays()
    val vibeMixes = buildVibeMixes(deduplicateTracks(state.tracks), state.vibeQuery, state.liked, state.longListens, state.localPlays, state.audioFeatures)
    val mixes = buildMixes(
        rankedTracks,
        state.liked,
        state.longListens,
        state.skips,
        state.localPlays,
        recentTracks,
        state.audioFeatures
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.selectedTab == tab && !showNowPlaying,
                        onClick = {
                            showNowPlaying = false
                            viewModel.setSelectedTab(tab)
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = if (showNowPlaying) 20.dp else 104.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.shouldShowConnectionCard && !showNowPlaying) {
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
                        onAccentThemeSelected = viewModel::setAccentTheme,
                        onVisualizerDebugOverlayChanged = viewModel::setVisualizerDebugOverlay
                    )
                    }
                }
                if (showNowPlaying) {
                    item {
                        NowPlayingPage(
                            state = state,
                            mixes = mixes,
                            onPlayPause = viewModel::togglePlayPause,
                            onLike = viewModel::toggleLike,
                            onPrevious = viewModel::previous,
                            onSkip = viewModel::skip,
                            onSeek = viewModel::seekToProgress,
                            onShuffle = viewModel::toggleShuffle,
                            onRepeat = viewModel::toggleRepeat,
                            onStartRadio = viewModel::startRadioFromCurrent,
                            onVisualizerStageChanged = viewModel::setNowPlayingVisualizerStage,
                            onDjModeSelected = viewModel::applyGuestDjMode,
                            onQueueSelected = viewModel::startQueue,
                            onShuffledQueueSelected = viewModel::startShuffledQueue,
                            onClearQueue = viewModel::clearQueue,
                            onOpenQueue = { showQueueSheet = true },
                            onTrackSelected = viewModel::selectTrack
                        )
                    }
                } else when (state.selectedTab) {
                    Tab.Home -> {
                        item {
                            HomeNowCard(
                                state = state,
                                onOpenNowPlaying = { showNowPlaying = true },
                                onPrevious = viewModel::previous,
                                onPlayPause = viewModel::togglePlayPause,
                                onLike = viewModel::toggleLike,
                                onNext = viewModel::skip,
                                onStartRadio = viewModel::startRadioFromCurrent
                            )
                        }
                        item { SpeedDialGrid(homeSpeedDialMixes(mixes), viewModel::startQueue) }
                        if (recentTrackPlays.isNotEmpty()) {
                            item { RecentTrackRail("Recently played", recentTrackPlays.take(10), viewModel::selectTrack) }
                        }
                        item { TrackRail("Heavy rotation", visibleTracks.take(10), viewModel::selectTrack) }
                    }
                    Tab.Mixes -> {
                        item { MixesSegmentControl(state.mixesSegment, viewModel::setMixesSegment) }
                        when (state.mixesSegment) {
                            MixesSegment.Mixes -> items(mixes) { mix ->
                                PlaylistCard(mix, state.liked, viewModel::startQueue, viewModel::startShuffledQueue, viewModel::selectTrack)
                            }
                            MixesSegment.Vibes -> {
                                item {
                                    VibeSearchCard(
                                        query = state.vibeQuery,
                                        onQueryChange = viewModel::setVibeQuery,
                                        resultCount = vibeMixes.size
                                    )
                                }
                                item { VibeChipRow(state.vibeQuery, viewModel::setVibeQuery) }
                                items(vibeMixes) { vibe ->
                                    VibeMixCard(
                                        mix = vibe,
                                        liked = state.liked,
                                        onQueueSelected = viewModel::startQueue,
                                        onShuffledQueueSelected = viewModel::startShuffledQueue,
                                        onTrackSelected = viewModel::selectTrack
                                    )
                                }
                            }
                            MixesSegment.Yours -> {
                                if (state.jellyfinPlaylists.isNotEmpty()) {
                                    item { JellyfinPlaylistRail(state.jellyfinPlaylists, viewModel::openPlaylist) }
                                } else {
                                    item { EmptyState("No Jellyfin playlists", "Server playlists and user-created mixes will appear here.") }
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
                                item { EmptyState("User-created mixes", "Saved personal mixes can live here when creation is added.") }
                            }
                        }
                    }
                    Tab.Discover -> {
                        item {
                            JarvisDjCard(
                                state = state,
                                onDraftChange = viewModel::setDjDraft,
                                onSendPrompt = { viewModel.sendDjPrompt() },
                                onSuggestion = viewModel::sendDjPrompt,
                                onModeSelected = viewModel::applyGuestDjMode
                            )
                        }
                        item { JarvisResultsSection(state, viewModel::sendDjPrompt, viewModel::selectTrack) }
                    }
                    Tab.Library -> {
                        val libraryTracks = filterTracks(state.tracks, state.searchQuery)
                        item { SearchCard(state.searchQuery, viewModel::setSearchQuery, visibleTracks.size) }
                        item { LibraryBrowseSegmentControl(state.libraryBrowseMode, viewModel::setLibraryBrowseMode) }
                        item { LibrarySummary(libraryTracks, state.rawTrackCount) }
                        item { LibraryActions(libraryTracks, state.libraryBrowseMode, viewModel::startQueue, viewModel::startShuffledQueue) }
                        libraryBrowseContent(
                            mode = state.libraryBrowseMode,
                            tracks = libraryTracks,
                            liked = state.liked,
                            onTrackSelected = viewModel::selectTrack,
                            onQueueSelected = viewModel::startQueue
                        )
                        item { SavedDiscoveryFilters(viewModel::setDiscoveryFilter) }
                        item { TrackSection(state.discoveryFilter.sectionTitle, discoveryTracks, state.liked, viewModel::selectTrack) }
                    }
                }
            }
            if (showQueueSheet) {
                ModalBottomSheet(onDismissRequest = { showQueueSheet = false }) {
                    QueueSheet(
                        state = state,
                        onTrackSelected = {
                            showQueueSheet = false
                            viewModel.selectTrack(it)
                        },
                        onClearQueue = viewModel::clearQueue,
                        onMoveQueueItem = viewModel::moveQueueItem,
                        onRemoveQueueItem = viewModel::removeQueueItem,
                        onPlayNext = viewModel::playNext,
                        onAddToQueue = viewModel::addToQueue
                    )
                }
            }
            if (!showNowPlaying && showMiniPlayer) {
                PlayerBar(
                    track = state.currentTrack,
                    isPlaying = state.isPlaying,
                    queueLabel = state.queueLabel,
                    onPlayPause = viewModel::togglePlayPause,
                    onSkip = viewModel::skip,
                    onPrevious = viewModel::previous,
                    onOpenNowPlaying = { showNowPlaying = true },
                    onDismiss = { showMiniPlayer = false },
                    onOpenQueue = { showQueueSheet = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun HomeMoodChips(selected: String, onSelected: (String) -> Unit) {
    val chips = listOf("Energize", "Feel good", "Workout", "Focus", "Late night", "Chill")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips) { chip ->
                FilterChip(
                    selected = selected.equals(chip, ignoreCase = true),
                    onClick = { onSelected(chip) },
                    label = { Text(chip) },
                    colors = selectedChipColors()
                )
        }
    }
}

@Composable
private fun captionColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)

@Composable
private fun lowEmphasisButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurface
)

@Composable
private fun selectedChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun PrimaryPlayButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    label: String = "Play"
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(label)
    }
}

@Composable
private fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HeartIcon(liked: Boolean, modifier: Modifier = Modifier) {
    Icon(
        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = if (liked) "Liked" else "Not liked",
        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = modifier
    )
}

@Composable
private fun TrackSubtitleLine(track: Track, modifier: Modifier = Modifier) {
    val artist = track.artist.cleanUnknown("Unknown Artist").ifBlank { track.filename.orEmpty() }
    val album = track.album.cleanUnknown("Unknown Album").trimTrailingOpenParen()
    val genre = track.genre.takeIf { album.isBlank() && it.isNotBlank() && it.lowercase() !in internalGenreLabels }.orEmpty()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (artist.isNotBlank()) {
            Text(
                artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        if (album.isNotBlank()) {
            Text(" • ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                album,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (genre.isNotBlank()) {
            Text(" • $genre", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

private fun String.trimTrailingOpenParen(): String =
    trim().replace(Regex("\\s*\\([^)]*$"), "").trim()

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

private fun mixGradient(name: String): List<Color> {
    val hash = name.hashCode().absoluteValue
    val hue = hash % 360
    fun colorAt(offset: Int, saturation: Float, value: Float): Color =
        Color.hsv(((hue + offset) % 360).toFloat(), saturation, value)
    return listOf(colorAt(0, 0.74f, 0.72f), colorAt(54, 0.62f, 0.36f), colorAt(132, 0.56f, 0.52f))
}

private fun initials(value: String): String =
    value.split(" ", "-", "_")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { "JM" }

private fun collisionAwareInitials(name: String, allNames: List<String>): String {
    if (name.equals("Liked Radio", ignoreCase = true)) return "LIKE"
    if (name.equals("Library Radio", ignoreCase = true)) return "LIB"
    val base = initials(name)
    val collisions = allNames.filter { initials(it) == base }
    if (collisions.size <= 1) return base
    val normalized = name.filter { it.isLetterOrDigit() }.uppercase()
    val distinct = normalized.take(4).ifBlank { base }
    val length = if (distinct.length >= 4) 4 else 3
    return distinct.take(length).ifBlank { base }
}

@Composable
private fun MixesSegmentControl(selected: MixesSegment, onSelected: (MixesSegment) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MixesSegment.entries) { segment ->
                FilterChip(
                    selected = selected == segment,
                    onClick = { onSelected(segment) },
                    label = { Text(segment.label) },
                    colors = selectedChipColors()
                )
        }
    }
}

@Composable
private fun LibraryBrowseSegmentControl(selected: LibraryBrowseMode, onSelected: (LibraryBrowseMode) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(LibraryBrowseMode.entries) { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    label = { Text(mode.label) },
                    colors = selectedChipColors()
                )
        }
    }
}

@Composable
private fun SavedDiscoveryFilters(onSelected: (DiscoveryFilter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Saved filters")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DiscoveryFilter.entries) { filter ->
                FilterChip(
                    selected = false,
                    onClick = { onSelected(filter) },
                    label = { Text(filter.label) },
                    colors = selectedChipColors()
                )
            }
        }
    }
}

private fun homeSpeedDialMixes(mixes: List<Mix>): List<Mix> =
    mixes.filter { it.name in setOf("Quick Shuffle", "Rediscover", "Liked Radio", "Loud Flow", "Library Radio", "Weekly Discovery") }
        .take(6)

@Composable
private fun SpeedDialGrid(
    mixes: List<Mix>,
    onQueueSelected: (String, List<Track>) -> Unit
) {
    val rows = mixes.take(6).chunked(3)
    val names = mixes.take(6).map { it.name }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Speed dial")
        rows.forEach { rowMixes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowMixes.forEach { mix ->
                    SpeedDialTile(
                        mix = mix,
                        allNames = names,
                        onClick = { onQueueSelected(mix.name, mix.tracks) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowMixes.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SpeedDialTile(
    mix: Mix,
    allNames: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(mixGradient(mix.name)))
            )
            val imageUrl = mix.tracks.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Text(
                    collisionAwareInitials(mix.name, allNames),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.20f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Text(
                    mix.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    onAccentThemeSelected: (AccentTheme) -> Unit,
    onVisualizerDebugOverlayChanged: (Boolean) -> Unit
) {
    var expanded by remember(state.hasSession) { mutableStateOf(!state.hasSession) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Jellyfin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        state.connectionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.hasSession) {
                    Button(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Hide" else "Edit")
                    }
                }
            }
            if (expanded || !state.hasSession) {
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
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onConnect, enabled = !state.isLoading, modifier = Modifier.weight(1f)) {
                    Text(if (state.hasSession) "Reconnect" else "Connect")
                }
                OutlinedButton(onClick = onReload, enabled = state.hasSession && !state.isLoading, modifier = Modifier.weight(1f)) {
                    Text("Reload")
                }
            }
            if (expanded || !state.hasSession) {
                OutlinedButton(onClick = onClearSession, enabled = state.hasSession, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear session")
                }
                ThemeOptions(
                    themeMode = state.themeMode,
                    accentTheme = state.accentTheme,
                    onThemeModeSelected = onThemeModeSelected,
                    onAccentThemeSelected = onAccentThemeSelected,
                    visualizerDebugOverlay = state.visualizerDebugOverlay,
                    onVisualizerDebugOverlayChanged = onVisualizerDebugOverlayChanged
                )
            }
            Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ThemeOptions(
    themeMode: ThemeMode,
    accentTheme: AccentTheme,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onAccentThemeSelected: (AccentTheme) -> Unit,
    visualizerDebugOverlay: Boolean,
    onVisualizerDebugOverlayChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Theme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ThemeMode.entries) { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeSelected(mode) },
                    label = { Text(mode.label) },
                    colors = selectedChipColors()
                )
            }
        }
        Text("Accent", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AccentTheme.entries) { accent ->
                FilterChip(
                    selected = accentTheme == accent,
                    onClick = { onAccentThemeSelected(accent) },
                    colors = selectedChipColors(),
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
        Text("Visualizer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        FilterChip(
            selected = visualizerDebugOverlay,
            onClick = { onVisualizerDebugOverlayChanged(!visualizerDebugOverlay) },
            label = { Text(if (visualizerDebugOverlay) "Diagnostics on" else "Diagnostics off") },
            colors = selectedChipColors()
        )
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
                Text(if (connected) "Streaming from Jellyfin" else "Demo discovery mode", color = Color(0xFF101113), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Queued: ${currentTrack.title} from ${currentTrack.album}", color = Color(0xFF101113), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Source: $serverUrl", color = Color(0xFF101113), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                frame = state.audioFrame,
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
private fun HomeNowCard(
    state: JellyMixState,
    onOpenNowPlaying: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onNext: () -> Unit,
    onStartRadio: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenNowPlaying),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(state.currentTrack, size = 96)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (state.isPlaying) "Now playing" else "Ready to play", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(state.currentTrack.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TrackSubtitleLine(state.currentTrack)
                Text(state.queueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play")
                    }
                    IconButton(onClick = onLike) {
                        HeartIcon(state.liked[state.currentTrack.id] == true)
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                    }
                    IconButton(onClick = onStartRadio) {
                        Icon(Icons.Filled.Recommend, contentDescription = "Song radio")
                    }
                    IconButton(onClick = onOpenNowPlaying) {
                        Icon(Icons.Filled.LibraryMusic, contentDescription = "Now playing")
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicVisualizer(
    bands: List<Float>,
    isPlaying: Boolean,
    frame: AudioAnalysisFrame = ambientFrame(),
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    useFeedbackTunnel: Boolean = false,
    fullscreen: Boolean = false,
    palette: List<Color>? = null,
    mode: VisualizerRenderMode = VisualizerRenderMode.FeedbackTunnel,
    debugOverlay: Boolean = false
) {
    if (useFeedbackTunnel && mode != VisualizerRenderMode.Ridgeline) {
        val effectivePalette = palette ?: listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
        var stats by remember { mutableStateOf(VisualizerDebugStats(mode = mode, bands = frame.bands, live = frame.live)) }
        Box(modifier = modifier) {
            FeedbackTunnelVisualizer(
                frame = frame,
                palette = effectivePalette,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize(),
                intensity = when (mode) {
                    VisualizerRenderMode.Fluid -> if (compact) 0.58f else 0.86f
                    else -> if (compact) 0.7f else 0.98f
                },
                sensitivity = if (mode == VisualizerRenderMode.Fluid) 0.9f else 1.0f,
                fullscreen = fullscreen,
                mode = mode,
                debugOverlay = debugOverlay,
                onStats = { stats = it }
            )
            if (debugOverlay) {
                VisualizerDebugOverlay(frame = frame, mode = mode, stats = stats)
            }
        }
        return
    }
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
        val centerY = size.height * 0.52f
        val usableHeight = size.height * if (compact) 0.46f else 0.42f
        val step = if (count <= 1) size.width else size.width / (count - 1)
        val topPath = Path()
        val bottomPath = Path()
        val fillPath = Path()
        sourceBands.forEachIndexed { index, band ->
            val pulse = if (isPlaying) {
                0.9f + 0.1f * sin((phase * 6.28318f) + band * 3.4f + index * 0.21f)
            } else {
                0.42f
            }
            val normalized = (band * pulse).coerceIn(0.05f, 1f)
            val x = index * step
            val yTop = centerY - usableHeight * normalized
            val yBottom = centerY + usableHeight * normalized * 0.82f
            if (index == 0) {
                topPath.moveTo(x, yTop)
                bottomPath.moveTo(x, yBottom)
                fillPath.moveTo(x, yTop)
            } else {
                topPath.lineTo(x, yTop)
                bottomPath.lineTo(x, yBottom)
                fillPath.lineTo(x, yTop)
            }
        }
        for (index in sourceBands.indices.reversed()) {
            val band = sourceBands[index]
            val pulse = if (isPlaying) {
                0.9f + 0.1f * sin((phase * 6.28318f) + band * 3.4f + index * 0.21f)
            } else {
                0.42f
            }
            val normalized = (band * pulse).coerceIn(0.05f, 1f)
            val x = index * step
            val yBottom = centerY + usableHeight * normalized * 0.82f
            fillPath.lineTo(x, yBottom)
        }
        fillPath.close()
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(primary.copy(alpha = if (compact) 0.18f else 0.32f), secondary.copy(alpha = 0.08f))
            )
        )
        drawPath(
            path = topPath,
            color = primary.copy(alpha = lineAlpha),
            style = Stroke(width = if (compact) 2.dp.toPx() else 4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = bottomPath,
            color = secondary.copy(alpha = lineAlpha * 0.72f),
            style = Stroke(width = if (compact) 1.5.dp.toPx() else 3.dp.toPx(), cap = StrokeCap.Round)
        )
        val nodeEvery = if (compact) 7 else 4
        sourceBands.forEachIndexed { index, band ->
            val pulse = if (isPlaying) {
                0.88f + 0.12f * sin((phase * 6.28318f) + sourceBands[index] * 4.2f + index * 0.19f)
            } else {
                0.32f
            }
            val normalized = (band * pulse).coerceIn(0.06f, 1f)
            val x = index * step
            val peak = centerY - usableHeight * normalized
            val trough = centerY + usableHeight * normalized * 0.82f
            val color = when (index % 3) {
                0 -> primary
                1 -> secondary
                else -> tertiary
            }
            if (!compact && index % nodeEvery == 0) {
                drawCircle(
                    color = color.copy(alpha = lineAlpha),
                    radius = (3.dp.toPx() + normalized * 6.dp.toPx()),
                    center = androidx.compose.ui.geometry.Offset(x, peak)
                )
            }
            drawLine(
                color = color.copy(alpha = if (compact) lineAlpha * 0.38f else lineAlpha * 0.5f),
                start = androidx.compose.ui.geometry.Offset(x, peak),
                end = androidx.compose.ui.geometry.Offset(x, trough),
                strokeWidth = if (compact) 1.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun VisualizerDebugOverlay(frame: AudioAnalysisFrame, mode: VisualizerRenderMode, stats: VisualizerDebugStats) {
    val bands = stats.bands.ifEmpty { frame.bands }.take(12).joinToString(" ") { (it * 100).roundToInt().toString() }
    val fps = if (stats.fps > 0f) stats.fps.roundToInt().toString() else "..."
    val mean = (stats.meanLuminance * 100).roundToInt()
    Box(
        modifier = Modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = "mode ${mode.label()} | fps $fps | ${if (frame.live) "live" else "fallback"} | mean $mean | resets ${stats.resetCount} | bands $bands",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.86f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun VisualizerRenderMode.label(): String = when (this) {
    VisualizerRenderMode.FeedbackTunnel -> "Feedback tunnel"
    VisualizerRenderMode.Fluid -> "Fluid"
    VisualizerRenderMode.Ridgeline -> "Ridgeline"
}

@Composable
private fun DiscoveryFilters(selected: DiscoveryFilter, onSelected: (DiscoveryFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DiscoveryFilter.entries) { filter ->
                FilterChip(
                    selected = selected == filter,
                    onClick = { onSelected(filter) },
                    label = { Text(filter.label) },
                    colors = selectedChipColors()
                )
        }
    }
}

@Composable
private fun JarvisDjCard(
    state: JellyMixState,
    onDraftChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onSuggestion: (String) -> Unit,
    onModeSelected: (GuestDjMode) -> Unit
) {
    val context = LocalContext.current
    val suggestions = listOf("Keep this vibe", "More energy", "Chill it out", "Surprise me")
    val discoverModes = listOf(GuestDjMode.Familiar, GuestDjMode.Discovery, GuestDjMode.DeepCuts)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Jarvis DJ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Tell Jarvis what you want. It will rebuild the queue and explain the next picks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(discoverModes) { mode ->
                    FilterChip(
                        selected = state.djMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.label) },
                        colors = selectedChipColors()
                    )
                }
            }
            state.djMessages.takeLast(3).forEach { message ->
                Text(
                    "${message.speaker}: ${message.text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.speaker == "Jarvis") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedTextField(
                value = state.djDraft,
                onValueChange = onDraftChange,
                label = { Text("Ask Jarvis DJ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (state.djDraft.isBlank()) {
                            Toast.makeText(context, "Type a Jarvis request first.", Toast.LENGTH_SHORT).show()
                        } else {
                            onSendPrompt()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Tune queue")
                }
                Button(onClick = { onSuggestion("Keep this vibe going") }, modifier = Modifier.weight(1f)) {
                    Text("Auto DJ")
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(suggestions) { suggestion ->
                    FilterChip(
                        selected = false,
                        onClick = { onSuggestion(suggestion.toJarvisPrompt()) },
                        label = { Text(suggestion) },
                        colors = selectedChipColors()
                    )
                }
            }
        }
    }
}

@Composable
private fun JarvisResultsSection(state: JellyMixState, onRetry: () -> Unit, onTrackSelected: (Track) -> Unit) {
    val actualMessages = state.djMessages.filterNot { it.text == DefaultJarvisPrompt }
    if (actualMessages.isEmpty() && !state.queueTitle.startsWith("Jarvis DJ")) return
    val results = state.queue
        .drop(state.queueIndex)
        .ifEmpty {
            buildGuestDjQueue(
                mode = state.djMode,
                seed = state.currentTrack,
                tracks = state.tracks,
                liked = state.liked,
                longListens = state.longListens,
                skips = state.skips,
                localPlays = state.localPlays,
                recentlyPlayedIds = state.recentTrackIds
            )
        }
    val latestJarvis = actualMessages.lastOrNull { it.speaker == "Jarvis" }?.text
    val hasError = state.status.contains("failed", ignoreCase = true) || state.status.contains("error", ignoreCase = true)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Jarvis results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                hasError -> {
                    Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry, enabled = state.djDraft.isNotBlank()) { Text("Retry") }
                }
                results.isEmpty() -> {
                    repeat(3) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
                else -> {
                    latestJarvis?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    results.take(12).forEach { track ->
                        QueueReasonRow(track, state.liked[track.id] == true, queueReason(track, state.currentTrack, state.djMode)) {
                            onTrackSelected(track)
                        }
                    }
                }
            }
        }
    }
}

private fun String.toJarvisPrompt(): String = when (this) {
    "Keep this vibe" -> "Keep this vibe going"
    "More energy" -> "Make it more high energy"
    "Chill it out" -> "Chill it out"
    else -> "Surprise me with fresh stuff"
}

@Composable
private fun VibeSearchCard(query: String, onQueryChange: (String) -> Unit, resultCount: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Vibe finder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Search by feeling, mood, activity, genre, or emotional lane.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search vibes: sad, hype, chill, angry, focus") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (query.isBlank()) "Showing core emotional playlists." else "$resultCount vibe playlists found",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VibeChipRow(query: String, onQueryChange: (String) -> Unit) {
    val chips = listOf("Chill", "Hype", "Sad", "Angry", "Focus", "Late night", "Happy", "Nostalgic", "Workout", "Rainy")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips) { chip ->
                    FilterChip(
                        selected = query.equals(chip, ignoreCase = true),
                        onClick = { onQueryChange(chip) },
                        label = { Text(chip) },
                        colors = selectedChipColors()
                    )
        }
    }
}

@Composable
private fun VibeMixCard(
    mix: Mix,
    liked: Map<String, Boolean>,
    onQueueSelected: (String, List<Track>) -> Unit,
    onShuffledQueueSelected: (String, List<Track>) -> Unit,
    onTrackSelected: (Track) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MixArtwork(mix)
            Text(mix.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(mix.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            mix.note?.let { InfoNote(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryPlayButton(onClick = { onQueueSelected(mix.name, mix.tracks) }, enabled = mix.tracks.isNotEmpty(), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onShuffledQueueSelected(mix.name, mix.tracks) }, enabled = mix.tracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Shuffle")
                }
            }
            mix.tracks.take(3).forEach { track -> QueueReasonRow(track, liked[track.id] == true) { onTrackSelected(track) } }
        }
    }
}

@Composable
private fun GuestDjModeCard(selected: GuestDjMode, onModeSelected: (GuestDjMode) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Jarvis DJ mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(selected.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GuestDjMode.entries) { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.label) },
                        colors = selectedChipColors()
                    )
                }
            }
        }
    }
}

@Composable
private fun MixArtwork(mix: Mix) {
    val tracks = mix.tracks.take(4)
    val imageUrl = tracks.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
    Box(
        modifier = Modifier
            .size(126.dp)
            .height(126.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(mixGradient(mix.name)))
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    collisionAwareInitials(mix.name, listOf(mix.name)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun MixRail(
    title: String,
    mixes: List<Mix>,
    onQueueSelected: (String, List<Track>) -> Unit,
    onShuffledQueueSelected: (String, List<Track>) -> Unit,
    onTrackSelected: (Track) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mixes) { mix ->
                Card(
                    modifier = Modifier
                        .width(244.dp)
                        .clickable(enabled = mix.tracks.isNotEmpty()) { onQueueSelected(mix.name, mix.tracks) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MixArtwork(mix)
                        Text(mix.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(mix.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        mix.note?.let { InfoNote(it) }
                        Text("${mix.tracks.size} tracks", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            PrimaryPlayButton(onClick = { onQueueSelected(mix.name, mix.tracks) }, enabled = mix.tracks.isNotEmpty(), modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { onShuffledQueueSelected(mix.name, mix.tracks) }, enabled = mix.tracks.isNotEmpty()) {
                                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle ${mix.name}")
                            }
                        }
                        mix.tracks.firstOrNull()?.let { track ->
                            Text(
                                "Starts with ${track.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
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
private fun TrackRail(title: String, tracks: List<Track>, onTrackSelected: (Track) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tracks) { track ->
                Card(
                    modifier = Modifier
                        .width(148.dp)
                        .clickable { onTrackSelected(track) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlbumArt(track, size = 128)
                        Text(track.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TrackSubtitleLine(track)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTrackRail(title: String, plays: List<RecentTrackPlay>, onTrackSelected: (Track) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(plays) { play ->
                Card(
                    modifier = Modifier
                        .width(154.dp)
                        .height(232.dp)
                        .clickable { onTrackSelected(play.track) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlbumArt(play.track, size = 128)
                        Text(play.track.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TrackSubtitleLine(play.track)
                        if (play.count > 1) {
                            Text("${play.count} plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
        SectionHeader(title)
        if (tracks.isEmpty()) {
            EmptyState("No matching tracks", "Try another search or reload your Jellyfin library.")
        } else {
            tracks.forEach { track -> TrackRow(track, liked[track.id] == true) { onTrackSelected(track) } }
        }
    }
}

private fun LazyListScope.libraryBrowseContent(
    mode: LibraryBrowseMode,
    tracks: List<Track>,
    liked: Map<String, Boolean>,
    onTrackSelected: (Track) -> Unit,
    onQueueSelected: (String, List<Track>) -> Unit
) {
    val canonical = deduplicateTracks(tracks)
    when (mode) {
        LibraryBrowseMode.Tracks -> item {
            TrackSection("Tracks", canonical.sortedBy { it.title.lowercase() }, liked, onTrackSelected)
        }
        LibraryBrowseMode.Artists -> {
            val artists = canonical
                .filter { it.artist.isNotBlank() }
                .groupBy { it.artist }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .toList()
            items(artists) { (artist, artistTracks) ->
                LibraryGroupCard(
                    title = artist,
                    subtitle = "${artistTracks.size} tracks",
                    tracks = artistTracks,
                    liked = liked,
                    onQueueSelected = { onQueueSelected(artist, artistTracks) },
                    onTrackSelected = onTrackSelected
                )
            }
        }
        LibraryBrowseMode.Albums -> {
            val albums = canonical
                .filter { it.album.isNotBlank() }
                .groupBy { it.album }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .toList()
            items(albums) { (album, albumTracks) ->
                val artist = albumTracks.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
                LibraryGroupCard(
                    title = album,
                    subtitle = listOf(artist, "${albumTracks.size} tracks").filter { it.isNotBlank() }.joinToString(" / "),
                    tracks = albumTracks,
                    liked = liked,
                    onQueueSelected = { onQueueSelected(album, albumTracks) },
                    onTrackSelected = onTrackSelected
                )
            }
        }
        LibraryBrowseMode.Genres -> {
            val genres = canonical
                .filter { it.genre.isNotBlank() }
                .groupBy { it.genre }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .toList()
            items(genres) { (genre, genreTracks) ->
                LibraryGroupCard(
                    title = genre,
                    subtitle = "${genreTracks.size} tracks",
                    tracks = genreTracks,
                    liked = liked,
                    onQueueSelected = { onQueueSelected("$genre radio", genreTracks) },
                    onTrackSelected = onTrackSelected
                )
            }
        }
    }
}

@Composable
private fun LibraryGroupCard(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    liked: Map<String, Boolean>,
    onQueueSelected: () -> Unit,
    onTrackSelected: (Track) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PrimaryPlayButton(onClick = onQueueSelected, enabled = tracks.isNotEmpty())
            }
            tracks.take(4).forEach { track ->
                QueueReasonRow(track, liked[track.id] == true) { onTrackSelected(track) }
            }
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
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(track, size = 48, showProgress = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TrackSubtitleLine(track)
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                HeartIcon(liked)
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    mix: Mix,
    liked: Map<String, Boolean>,
    onQueueSelected: (String, List<Track>) -> Unit,
    onShuffledQueueSelected: (String, List<Track>) -> Unit,
    onTrackSelected: (Track) -> Unit
) {
    Card(
        modifier = Modifier.clickable { onQueueSelected(mix.name, mix.tracks) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(mix.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(mix.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            mix.note?.let { InfoNote(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryPlayButton(onClick = { onQueueSelected(mix.name, mix.tracks) }, enabled = mix.tracks.isNotEmpty(), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onShuffledQueueSelected(mix.name, mix.tracks) }, enabled = mix.tracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Shuffle")
                }
            }
            mix.tracks.take(4).forEach { track -> TrackRow(track, liked[track.id] == true) { onTrackSelected(track) } }
        }
    }
}

@Composable
private fun UpNextSection(
    queue: List<Track>,
    queueIndex: Int,
    liked: Map<String, Boolean>,
    currentTrack: Track,
    djMode: GuestDjMode,
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
                QueueReasonRow(track, liked[track.id] == true, queueReason(track, currentTrack, djMode)) { onTrackSelected(track) }
            }
            if (queue.drop(queueIndex + 1).isEmpty()) {
                EmptyState("Autoplay follows this", "JellyMix will keep going with related tracks when this queue ends.")
            }
        }
    }
}

@Composable
private fun QueueSummaryCard(state: JellyMixState, onOpenQueue: () -> Unit, onClearQueue: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenQueue)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(state.queueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onClearQueue, enabled = state.queue.isNotEmpty()) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun QueueSheet(
    state: JellyMixState,
    onTrackSelected: (Track) -> Unit,
    onClearQueue: () -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit
) {
    val suggestions = buildAutoplayQueue(
        seed = state.currentTrack,
        tracks = state.tracks,
        liked = state.liked,
        longListens = state.longListens,
        skips = state.skips,
        localPlays = state.localPlays,
        recentlyPlayedIds = state.recentTrackIds
    ).filterNot { suggestion -> state.queue.any { it.id == suggestion.id } || suggestion.id == state.currentTrack.id }.take(4)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.queue.isEmpty()) state.queueTitle else "${state.queueIndex + 1}/${state.queue.size} in queue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onClearQueue, enabled = state.queue.isNotEmpty()) {
                Text("Clear")
            }
        }
        if (state.queue.isEmpty()) {
            EmptyState("Queue is empty", "Start a mix, vibe, playlist, or Jarvis DJ session.")
        } else {
            state.queue.drop(state.queueIndex).take(16).forEachIndexed { index, track ->
                val queueIndex = state.queueIndex + index
                QueueSheetRow(
                    track = track,
                    liked = state.liked[track.id] == true,
                    reason = if (index == 0) "Now playing" else queueReason(track, state.currentTrack, state.djMode),
                    queueIndex = queueIndex,
                    canMoveUp = queueIndex > state.queueIndex,
                    canMoveDown = queueIndex < state.queue.lastIndex,
                    onClick = { onTrackSelected(track) },
                    onMove = onMoveQueueItem,
                    onRemove = onRemoveQueueItem
                )
            }
        }
        if (suggestions.isNotEmpty()) {
            SectionHeader("Suggested")
            suggestions.forEach { track ->
                QueueSuggestionRow(
                    track = track,
                    liked = state.liked[track.id] == true,
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onClick = { onTrackSelected(track) }
                )
            }
        }
    }
}

@Composable
private fun QueueSheetRow(
    track: Track,
    liked: Boolean,
    reason: String,
    queueIndex: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    var consumedGesture by remember(track.id, queueIndex) { mutableStateOf(false) }
    QueueReasonRow(
        track = track,
        liked = liked,
        reason = reason,
        modifier = Modifier.pointerInput(track.id, queueIndex) {
            detectDragGestures(
                onDragStart = { consumedGesture = false },
                onDragEnd = { consumedGesture = false },
                onDragCancel = { consumedGesture = false }
            ) { change, dragAmount ->
                if (consumedGesture) return@detectDragGestures
                when {
                    dragAmount.x < -36f || dragAmount.x > 36f -> {
                        consumedGesture = true
                        change.consume()
                        onRemove(queueIndex)
                    }
                    dragAmount.y < -28f && canMoveUp -> {
                        consumedGesture = true
                        change.consume()
                        onMove(queueIndex, queueIndex - 1)
                    }
                    dragAmount.y > 28f && canMoveDown -> {
                        consumedGesture = true
                        change.consume()
                        onMove(queueIndex, queueIndex + 1)
                    }
                }
            }
        },
        onClick = onClick
    )
}

@Composable
private fun QueueSuggestionRow(
    track: Track,
    liked: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onClick: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QueueReasonRow(track, liked, "Suggested", onClick = onClick)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPlayNext, colors = lowEmphasisButtonColors(), modifier = Modifier.weight(1f)) {
                    Text("Play next")
                }
                OutlinedButton(onClick = onAddToQueue, modifier = Modifier.weight(1f)) {
                    Text("Add to queue")
                }
            }
        }
    }
}

@Composable
private fun QueueReasonRow(track: Track, liked: Boolean, reason: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(track, size = 48, showProgress = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TrackSubtitleLine(track)
                if (!reason.isNullOrBlank()) {
                    Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                HeartIcon(liked)
            }
        }
    }
}

@Composable
private fun AutoplayPreviewSection(state: JellyMixState, onTrackSelected: (Track) -> Unit) {
    val preview = buildGuestDjQueue(
        mode = state.djMode,
        seed = state.currentTrack,
        tracks = state.tracks,
        liked = state.liked,
        longListens = state.longListens,
        skips = state.skips,
        localPlays = state.localPlays,
        recentlyPlayedIds = state.recentTrackIds
    ).filterNot { track ->
        track.id == state.currentTrack.id ||
            state.queue.any { it.id == track.id && state.queue.indexOf(it) > state.queueIndex }
    }
        .take(4)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Autoplay preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("If the queue ends, Jarvis will keep going from ${state.currentTrack.title}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            preview.forEach { track ->
                QueueReasonRow(track, state.liked[track.id] == true, queueReason(track, state.currentTrack, state.djMode)) { onTrackSelected(track) }
            }
        }
    }
}

@Composable
private fun LibraryActions(
    tracks: List<Track>,
    mode: LibraryBrowseMode,
    onQueueSelected: (String, List<Track>) -> Unit,
    onShuffledQueueSelected: (String, List<Track>) -> Unit
) {
    val canonical = deduplicateTracks(tracks)
    val label = mode.label
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = { onQueueSelected("Library $label", canonical) }, enabled = canonical.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Play $label")
            }
            OutlinedButton(onClick = { onShuffledQueueSelected("Library $label", canonical) }, enabled = canonical.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Shuffle all")
            }
        }
    }
}

@Composable
private fun JellyfinPlaylistRail(playlists: List<JellyfinPlaylist>, onPlaylistSelected: (JellyfinPlaylist) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Jellyfin playlists")
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
                        Text(playlist.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            Text(playlist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySummary(tracks: List<Track>, rawTrackCount: Int) {
    val canonicalTracks = deduplicateTracks(tracks)
    val values = listOf(
        "Artists" to canonicalTracks.map { it.artist }.filter { it.isNotBlank() }.distinct().size.toString(),
        "Albums" to canonicalTracks.map { it.album }.filter { it.isNotBlank() }.distinct().size.toString(),
        "Tracks" to canonicalTracks.size.toString()
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                values.forEach { (label, value) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
            Text(
                "${canonicalTracks.size} deduped tracks from ${rawTrackCount.coerceAtLeast(canonicalTracks.size)} raw Jellyfin items.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, tooltip: String? = null) {
    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        Card(modifier = contentModifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (tooltip.isNullOrBlank()) {
        content(modifier)
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(tooltip) } },
            state = rememberTooltipState(),
            modifier = modifier
        ) {
            content(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NowPlayingPage(
    state: JellyMixState,
    mixes: List<Mix>,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onSeek: (Float) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onStartRadio: () -> Unit,
    onVisualizerStageChanged: (Boolean) -> Unit,
    onDjModeSelected: (GuestDjMode) -> Unit,
    onQueueSelected: (String, List<Track>) -> Unit,
    onShuffledQueueSelected: (String, List<Track>) -> Unit,
    onClearQueue: () -> Unit,
    onOpenQueue: () -> Unit,
    onTrackSelected: (Track) -> Unit
) {
    val track = state.currentTrack
    var showVisualizerStage by remember(track.id, state.nowPlayingVisualizerStage) { mutableStateOf(state.nowPlayingVisualizerStage) }
    var showFullscreenVisualizer by remember(track.id) { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                NowPlayingStage(
                    track = track,
                    bands = state.visualizerBands,
                    frame = state.audioFrame,
                    isPlaying = state.isPlaying,
                    debugOverlay = state.visualizerDebugOverlay,
                    showVisualizer = showVisualizerStage,
                    onToggle = {
                        showVisualizerStage = !showVisualizerStage
                        onVisualizerStageChanged(showVisualizerStage)
                    },
                    onFullscreen = { showFullscreenVisualizer = true }
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.album, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                NowPlayingProgress(track = track, onSeek = onSeek)
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    DrivingControlButton(
                        icon = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        size = 76,
                        iconSize = 38,
                        onClick = onPrevious
                    )
                    Spacer(Modifier.width(18.dp))
                    DrivingControlButton(
                        icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        prominent = true,
                        size = 88,
                        iconSize = 44,
                        onClick = onPlayPause
                    )
                    Spacer(Modifier.width(18.dp))
                    DrivingControlButton(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        size = 76,
                        iconSize = 38,
                        onClick = onSkip
                    )
                }
                Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    SecondaryPlayerButton(
                        icon = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        active = state.shuffleEnabled,
                        onClick = onShuffle
                    )
                    SecondaryPlayerButton(
                        icon = if (state.liked[track.id] == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        active = state.liked[track.id] == true,
                        onClick = onLike
                    )
                    SecondaryPlayerButton(
                        icon = Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        active = state.repeatEnabled,
                        onClick = onRepeat
                    )
                    SecondaryPlayerButton(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        onClick = onOpenQueue
                    )
                    Box {
                        SecondaryPlayerButton(
                            icon = Icons.Filled.MoreVert,
                            contentDescription = "More actions",
                            onClick = { showOverflowMenu = true }
                        )
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(text = { Text("Start song radio") }, onClick = { showOverflowMenu = false; onStartRadio() })
                            DropdownMenuItem(text = { Text("Go to album") }, onClick = { showOverflowMenu = false })
                            DropdownMenuItem(text = { Text("Go to artist") }, onClick = { showOverflowMenu = false })
                            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { showOverflowMenu = false })
                            DropdownMenuItem(text = { Text("Track info") }, onClick = { showOverflowMenu = false })
                        }
                    }
                }
            }
        }
        GuestDjModeCard(state.djMode, onDjModeSelected)
        if (state.queue.isNotEmpty()) {
            QueueSummaryCard(state = state, onOpenQueue = onOpenQueue, onClearQueue = onClearQueue)
        }
        AutoplayPreviewSection(state, onTrackSelected)
        MixRail("More to play", mixes.take(4), onQueueSelected, onShuffledQueueSelected, onTrackSelected)
    }
    if (showFullscreenVisualizer) {
        FullscreenVisualizerDialog(
            track = track,
            frame = state.audioFrame,
            isPlaying = state.isPlaying,
            debugOverlay = state.visualizerDebugOverlay,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onSkip,
            onSeek = onSeek,
            onDismiss = { showFullscreenVisualizer = false }
        )
    }
}

@Composable
private fun NowPlayingStage(
    track: Track,
    bands: List<Float>,
    frame: AudioAnalysisFrame,
    isPlaying: Boolean,
    debugOverlay: Boolean,
    showVisualizer: Boolean,
    onToggle: () -> Unit,
    onFullscreen: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (showVisualizer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(coverColors(track.genre)))
                    .padding(0.dp),
                contentAlignment = Alignment.Center
            ) {
                MusicVisualizer(
                    bands = bands,
                    isPlaying = isPlaying,
                    frame = frame,
                    useFeedbackTunnel = true,
                    fullscreen = false,
                    debugOverlay = debugOverlay,
                    palette = coverColors(track.genre) + listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(
                onClick = onFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA101113))
            ) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen visualizer", tint = Color.White)
            }
        } else {
            AlbumArt(track, size = maxWidth.value.roundToInt())
        }
    }
}

@Composable
private fun FullscreenVisualizerDialog(
    track: Track,
    frame: AudioAnalysisFrame,
    isPlaying: Boolean,
    debugOverlay: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    var overlayVisible by remember(track.id) { mutableStateOf(true) }
    var mode by remember(track.id) { mutableStateOf(VisualizerRenderMode.FeedbackTunnel) }
    var dragX by remember(track.id) { mutableStateOf(0f) }
    DisposableEffect(Unit) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previousKeepScreenOn }
    }
    LaunchedEffect(overlayVisible, track.id, mode) {
        if (overlayVisible) {
            delay(4_000)
            overlayVisible = false
        }
    }
    fun cycleMode(direction: Int) {
        val modes = VisualizerRenderMode.entries
        val nextIndex = (modes.indexOf(mode) + direction + modes.size) % modes.size
        mode = modes[nextIndex]
        overlayVisible = true
        Toast.makeText(context, mode.label(), Toast.LENGTH_SHORT).show()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(track.id) {
                    detectDragGestures(
                        onDragStart = { dragX = 0f },
                        onDragEnd = {
                            when {
                                dragX < -72f -> cycleMode(1)
                                dragX > 72f -> cycleMode(-1)
                            }
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f }
                    ) { change, dragAmount ->
                        dragX += dragAmount.x
                        change.consume()
                    }
                }
                .pointerInput(track.id, overlayVisible) {
                    detectTapGestures { overlayVisible = !overlayVisible }
                }
        ) {
            MusicVisualizer(
                bands = frame.bands,
                frame = frame,
                isPlaying = isPlaying,
                useFeedbackTunnel = true,
                palette = coverColors(track.genre) + listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary),
                mode = mode,
                debugOverlay = debugOverlay,
                fullscreen = true,
                modifier = Modifier.fillMaxSize()
            )
            if (overlayVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                        .padding(start = 22.dp, end = 22.dp, bottom = 18.dp, top = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.album, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(60.dp)) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(60.dp)) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        Text(mode.label(), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = track.completion.coerceIn(0f, 1f),
                        onValueChange = onSeek,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA101113))
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close visualizer", tint = Color.White)
            }
        }
    }
}

@Composable
private fun NowPlayingProgress(track: Track, onSeek: (Float) -> Unit) {
    val progress = track.completion.coerceIn(0f, 1f)
    val elapsedSec = (track.durationSec * progress).roundToInt().coerceIn(0, track.durationSec)
    val remainingSec = (track.durationSec - elapsedSec).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = progress,
                onValueChange = onSeek,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(elapsedSec), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("-${formatDuration(remainingSec)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SecondaryPlayerButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun DrivingControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    prominent: Boolean = false,
    size: Int = 60,
    iconSize: Int = 30
) {
    val containerColor = when {
        prominent -> MaterialTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val iconColor = when {
        prominent -> MaterialTheme.colorScheme.onPrimary
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerBar(
    track: Track,
    isPlaying: Boolean,
    queueLabel: String,
    onPlayPause: () -> Unit,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onDismiss: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragX by remember(track.id) { mutableStateOf(0f) }
    var dragY by remember(track.id) { mutableStateOf(0f) }
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenNowPlaying)
            .pointerInput(track.id) {
                detectDragGestures(
                    onDragStart = {
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragEnd = {
                        when {
                            dragY < -72f && abs(dragY) > abs(dragX) -> onOpenNowPlaying()
                            dragY > 72f && abs(dragY) > abs(dragX) -> onDismiss()
                            dragX < -72f && abs(dragX) > abs(dragY) -> onSkip()
                            dragX > 72f && abs(dragX) > abs(dragY) -> onPrevious()
                        }
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    }
                ) { change, dragAmount ->
                    dragX += dragAmount.x
                    dragY += dragAmount.y
                    change.consume()
                }
            }
    ) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(track, size = 42)
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenNowPlaying)
                ) {
                    Text(
                        track.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Text(
                        track.artist.ifBlank { "Unknown artist" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        queueLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onOpenQueue)
                    )
                }
                IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") }
                IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play") }
                IconButton(onClick = onSkip) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
            }
            LinearProgressIndicator(progress = { track.completion.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

@Composable
private fun AlbumArt(track: Track, size: Int = 54, showProgress: Boolean = false) {
    val colors = coverColors(track.genre).let { base ->
        if (track.imageUrl.isNullOrBlank()) mixGradient("${track.title}:${track.artist}") else base
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
        if (!track.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                initials(track.title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.92f)
            )
        }
        if (showProgress && track.completion > 0f && track.completion < 1f) {
            LinearProgressIndicator(
                progress = { track.completion.coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.28f)
            )
        }
    }
}

@Composable
private fun PlaylistArt(playlist: JellyfinPlaylist) {
    Box(
        modifier = Modifier
            .size(116.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Color(0xFF101113), modifier = Modifier.size(42.dp))
        if (!playlist.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = playlist.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun coverColors(genre: String): List<Color> =
    when (genre.lowercase()) {
        "synth", "electronic" -> listOf(Color(0xFF00D9B5), Color(0xFF284BFF))
        "ambient", "classical" -> listOf(Color(0xFFB8E1FF), Color(0xFF7A89C2))
        "rock", "metal" -> listOf(Color(0xFF2E3532), Color(0xFFD8A47F))
        "hip hop", "rap" -> listOf(Color(0xFF1DE9B6), Color(0xFF44546A))
        else -> listOf(Color(0xFFF7F3E8), Color(0xFF00D9B5))
    }

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Mixes("Mixes", Icons.AutoMirrored.Filled.PlaylistPlay),
    Discover("Discover", Icons.Filled.Explore),
    Library("Library", Icons.Filled.LibraryMusic)
}

enum class MixesSegment(val label: String) {
    Mixes("Mixes"),
    Vibes("Vibes"),
    Yours("Yours")
}

enum class LibraryBrowseMode(val label: String) {
    Artists("Artists"),
    Albums("Albums"),
    Tracks("Tracks"),
    Genres("Genres")
}

enum class DiscoveryFilter(val label: String, val sectionTitle: String) {
    LongListens("Long listens", "Songs you keep around the longest"),
    Liked("Liked", "Favorites and strong signals"),
    LowSkips("Low skips", "Tracks you rarely skip"),
    SimilarMood("Similar mood", "More like what is queued"),
    Rediscover("Rediscover", "Worth another listen")
}

enum class GuestDjMode(val label: String, val description: String) {
    Flow("Flow", "I will keep the queue close to the current mood and genre."),
    Familiar("Familiar", "I will favor songs you finish, like, or replay."),
    Discovery("Discovery", "I will push fresh tracks and avoid recent repeats."),
    DeepCuts("Deep cuts", "I will dig into lower-play songs with promising signals."),
    ArtistFocus("Artist focus", "I will stay near the current artist and album lane."),
    HighEnergy("High energy", "I will lean into loud, drive, rock, synth, and high-completion tracks."),
    Chill("Chill", "I will soften the queue toward calm, warm, ambient, and lower-pressure songs.")
}

data class JellyMixState(
    val serverUrl: String,
    val username: String,
    val password: String = "",
    val token: String = "",
    val userId: String = "",
    val themeMode: ThemeMode = ThemeMode.Dark,
    val accentTheme: AccentTheme = AccentTheme.Jelly,
    val visualizerDebugOverlay: Boolean = false,
    val nowPlayingVisualizerStage: Boolean = false,
    val selectedTab: Tab = Tab.Home,
    val mixesSegment: MixesSegment = MixesSegment.Mixes,
    val libraryBrowseMode: LibraryBrowseMode = LibraryBrowseMode.Tracks,
    val searchQuery: String = "",
    val vibeQuery: String = "",
    val discoveryFilter: DiscoveryFilter = DiscoveryFilter.LongListens,
    val djMode: GuestDjMode = GuestDjMode.Flow,
    val djDraft: String = "",
    val djMessages: List<DjMessage> = listOf(DjMessage("Jarvis", DefaultJarvisPrompt)),
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
    val rawTrackCount: Int = tracks.sumOf { 1 + it.alternates.size },
    val audioFeatures: Map<String, TrackAudioFeatures> = tracks.associate { it.id to inferAudioFeatures(it) },
    val visualizerBands: List<Float> = restingVisualizerBands(),
    val audioFrame: AudioAnalysisFrame = ambientFrame(),
    val visualizerPermissionGranted: Boolean = false,
    val visualizerMessage: String = "Visualizer preview is active. Enable audio capture for live Jellyfin waveforms.",
    val libraryLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val status: String = "Ready. Connect to Jellyfin or explore demo mixes."
) {
    val hasSession: Boolean = token.isNotBlank() && userId.isNotBlank()
    val isConnected: Boolean = hasSession && libraryLoaded
    val shouldShowConnectionCard: Boolean = !hasSession || (!libraryLoaded && !isLoading)
    val connectionLabel: String = when {
        isConnected -> "Connected to $serverUrl"
        hasSession && isLoading -> "Checking $serverUrl"
        hasSession -> "Saved login, library not loaded"
        else -> "Sign in once to load Jellyfin music"
    }
    val queueLabel: String =
        if (queue.isEmpty()) queueTitle else "$queueTitle ${queueIndex + 1}/${queue.size}"

    fun rankedTracks(): List<Track> =
        deduplicateTracks(tracks).sortedByDescending { track ->
            recommendationScore(
                track = track,
                liked = liked[track.id] == true,
                longListens = longListens[track.id] ?: 0,
                skips = skips[track.id] ?: 0,
                localPlays = localPlays[track.id] ?: 0
            )
        }

    fun recentTracks(): List<Track> =
        deduplicateTracks(recentTrackIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } })

    fun recentTrackPlays(): List<RecentTrackPlay> {
        if (recentTrackIds.isEmpty()) return emptyList()
        val collapsed = mutableListOf<RecentTrackPlay>()
        recentTrackIds.forEach { id ->
            val track = tracks.firstOrNull { it.id == id } ?: return@forEach
            val last = collapsed.lastOrNull()
            if (last?.track?.id == id) {
                collapsed[collapsed.lastIndex] = last.copy(count = last.count + 1)
            } else if (collapsed.none { it.track.id == id }) {
                collapsed += RecentTrackPlay(track, 1)
            }
        }
        return collapsed
    }
}

data class RecentTrackPlay(val track: Track, val count: Int)

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
    val imageUrl: String? = null,
    val bitrate: Int = 0,
    val tags: Set<String> = emptySet(),
    val filename: String? = null,
    val alternates: List<TrackAlternate> = emptyList()
)

data class TrackAlternate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val bitrate: Int,
    val imageUrl: String? = null
)

data class TrackSubtitle(val segments: List<String>) {
    val text: String = segments.filter { it.isNotBlank() }.joinToString(" • ")
}

data class TrackAudioFeatures(
    val bpm: Float,
    val rmsEnergy: Float,
    val spectralCentroid: Float,
    val dynamicRange: Float,
    val valence: Float,
    val vocalPresence: Float,
    val tempoStability: Float
)

data class CleanTrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val tags: Set<String>,
    val filename: String?
)

private data class TrackDedupKey(
    val title: String,
    val artist: String
)

data class Mix(
    val name: String,
    val reason: String,
    val tracks: List<Track>,
    val note: String? = null
)

data class VibeProfile(
    val name: String,
    val aliases: Set<String>,
    val moods: Set<String>,
    val genres: Set<String>,
    val reason: String
)

data class DjMessage(val speaker: String, val text: String)

data class JellyfinPlaylist(val id: String, val name: String, val childCount: Int, val imageUrl: String?)

private data class JellyfinSession(val token: String, val userId: String)

private data class JellyfinLibraryLoad(val tracks: List<Track>, val playlists: List<JellyfinPlaylist>, val rawTrackCount: Int = tracks.sumOf { 1 + it.alternates.size })

data class JellyfinServerInfo(val serverName: String, val version: String)

internal val sampleTracks = listOf(
    Track("sample-1", "Night Drive Home", "Glass Harbor", "After Hours", "Synth", "Late", 238, 18, 0.96f, 1, true),
    Track("sample-2", "Cloudbreak", "Mara Vale", "Soft Focus", "Indie", "Calm", 204, 13, 0.92f, 0, true),
    Track("sample-3", "Static Bloom", "Northline", "Signal Path", "Alternative", "Focused", 248, 7, 0.84f, 2, false),
    Track("sample-4", "Warm Signal", "June Reactor", "Receiver", "Electronic", "Bright", 219, 20, 0.89f, 3, true),
    Track("sample-5", "Basement Sun", "The Low Keys", "Weekend Proof", "Rock", "Drive", 187, 11, 0.73f, 4, false),
    Track("sample-6", "Blue Room", "Cassette Atlas", "Room Tone", "Ambient", "Calm", 301, 9, 0.98f, 0, true),
    Track("sample-7", "Last Train Static", "Velvet Relay", "Platform Lights", "Synth", "Late", 266, 6, 0.81f, 1, false),
    Track("sample-8", "Good Weather Lie", "Harbor Kids", "Open Windows", "Indie", "Bright", 196, 15, 0.87f, 2, true)
)

private val internalGenreLabels = setOf("loud", "drive", "library", "crossover")
private val retainedVariantSuffixes = setOf("live", "remix", "acoustic", "instrumental", "demo")

internal fun cleanTrackMetadata(
    title: String,
    artist: String,
    album: String,
    genres: List<String>,
    fallbackPath: String? = null
): CleanTrackMetadata {
    val filename = fallbackPath?.substringAfterLast('/')?.substringAfterLast('\\')?.substringBeforeLast('.', missingDelimiterValue = fallbackPath)
        ?.takeIf { it.isNotBlank() }
    val cleanTitle = title.removePrefix("MID -").trim().ifBlank { filename ?: "Untitled" }
    val cleanArtist = artist.cleanUnknown("Unknown Artist")
    val cleanAlbum = album.cleanUnknown("Unknown Album")
    val splitGenres = genres.flatMap { it.split("/", ";", ",") }.map { it.trim() }.filter { it.isNotBlank() }
    val tags = splitGenres.filter { it.lowercase() in internalGenreLabels }.toSet()
    val genre = splitGenres.firstOrNull { it.lowercase() !in internalGenreLabels }.orEmpty()
    return CleanTrackMetadata(
        title = cleanTitle,
        artist = cleanArtist,
        album = cleanAlbum,
        genre = genre,
        tags = tags,
        filename = filename
    )
}

internal fun Track.subtitle(): TrackSubtitle =
    TrackSubtitle(
        listOf(
            artist.cleanUnknown("Unknown Artist").ifBlank { filename.orEmpty() },
            album.cleanUnknown("Unknown Album"),
            genre.takeIf { it.isNotBlank() && it.lowercase() !in internalGenreLabels }.orEmpty()
        ).filter { it.isNotBlank() }
    )

private fun String.cleanUnknown(unknownValue: String): String =
    trim().takeUnless { it.isBlank() || it.equals(unknownValue, ignoreCase = true) }.orEmpty()

internal fun normalizeDedupTitle(title: String): String {
    val retained = mutableListOf<String>()
    val withoutIgnoredSuffixes = title.replace(Regex("\\(([^)]*)\\)")) { match ->
        val suffix = match.groupValues[1].trim()
        if (suffix.lowercase() in retainedVariantSuffixes) {
            retained += suffix.lowercase()
        }
        " "
    }
    return (withoutIgnoredSuffixes + " " + retained.joinToString(" "))
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

internal fun normalizeDedupText(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

internal fun deduplicateTracks(tracks: List<Track>): List<Track> {
    if (tracks.size <= 1) return tracks
    val groups = mutableMapOf<TrackDedupKey, MutableList<Track>>()
    tracks.forEach { track ->
        val key = TrackDedupKey(normalizeDedupTitle(track.title), normalizeDedupText(track.artist))
        groups.getOrPut(key) { mutableListOf() } += track
    }
    return groups.values.flatMap { candidates ->
        val durationClusters = mutableListOf<MutableList<Track>>()
        candidates.sortedBy { it.durationSec }.forEach { track ->
            val cluster = durationClusters.firstOrNull { existing -> existing.any { kotlin.math.abs(it.durationSec - track.durationSec) <= 2 } }
            if (cluster == null) durationClusters += mutableListOf(track) else cluster += track
        }
        durationClusters.map { cluster ->
            val canonical = cluster.maxWithOrNull(
                compareBy<Track> { it.bitrate }
                    .thenBy { if (it.imageUrl.isNullOrBlank()) 0 else 1 }
                    .thenBy { it.plays }
                    .thenBy { it.durationSec }
            ) ?: cluster.first()
            val alternates = cluster
                .filterNot { it.id == canonical.id }
                .sortedByDescending { it.bitrate }
                .map {
                    TrackAlternate(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        album = it.album,
                        durationSec = it.durationSec,
                        bitrate = it.bitrate,
                        imageUrl = it.imageUrl
                    )
                }
            canonical.copy(alternates = (canonical.alternates + alternates).distinctBy { it.id })
        }
    }
}

internal fun recommendationScore(track: Track, liked: Boolean, longListens: Int, skips: Int, localPlays: Int = 0): Float {
    val likeBoost = if (liked) 35f else 0f
    val completionBoost = track.completion * 30f
    val playBoost = track.plays.coerceAtMost(30) * 1.2f
    val localPlayBoost = localPlays.coerceAtMost(40) * 1.6f
    val longListenBoost = longListens * 9f
    val skipPenalty = skips * 5f
    return likeBoost + completionBoost + playBoost + localPlayBoost + longListenBoost - skipPenalty
}

internal fun inferAudioFeatures(track: Track): TrackAudioFeatures {
    val text = "${track.title} ${track.artist} ${track.album} ${track.genre} ${track.mood} ${track.tags.joinToString(" ")}".lowercase()
    fun hasAny(vararg terms: String): Boolean = terms.any { it in text }
    val genre = track.genre.lowercase()
    val mood = track.mood.lowercase()
    val baseEnergy = when {
        hasAny("metal", "punk", "hardcore", "rage", "loud") || genre in setOf("rock", "metal", "punk") || mood == "loud" -> 0.84f
        hasAny("dance", "edm", "electronic", "synth", "workout", "drive") || genre in setOf("electronic", "synth", "dance") -> 0.72f
        hasAny("ambient", "calm", "sleep", "piano", "acoustic") || genre in setOf("ambient", "classical", "folk") -> 0.28f
        mood in setOf("calm", "warm", "late") -> 0.42f
        else -> 0.55f
    }
    val energy = (baseEnergy + (track.completion - 0.5f) * 0.1f - track.skipped.coerceAtMost(5) * 0.015f).coerceIn(0.05f, 1f)
    val bpm = when {
        hasAny("hype", "workout", "dance", "edm", "fast") -> 136f
        hasAny("metal", "punk", "rock", "drive", "loud") || genre in setOf("rock", "metal", "punk") -> 126f
        hasAny("ambient", "sad", "slow", "ballad", "sleep", "acoustic") -> 78f
        hasAny("late", "night", "chill", "calm") -> 92f
        genre in setOf("electronic", "synth", "dance") -> 122f
        else -> 106f
    } + ((track.id.hashCode().absoluteValue % 15) - 7)
    val centroid = when {
        hasAny("bright", "pop", "dance", "synth", "electronic") -> 0.74f
        hasAny("metal", "punk", "angry", "rage", "dark") -> 0.34f
        hasAny("sad", "late", "ambient", "calm", "folk", "acoustic") -> 0.32f
        genre == "rock" -> 0.48f
        else -> 0.56f
    }.coerceIn(0.05f, 1f)
    val dynamicRange = when {
        hasAny("angry", "rage", "metal", "punk", "live") -> 0.82f
        hasAny("classical", "jazz") -> 0.72f
        hasAny("focus", "ambient", "electronic", "synth") -> 0.28f
        hasAny("chill", "sad", "late") -> 0.38f
        else -> 0.54f
    }
    val valence = when {
        hasAny("sad", "blue", "heart", "alone", "hurt", "late", "dark") -> 0.24f
        hasAny("angry", "rage", "hate", "fight") -> 0.18f
        hasAny("happy", "sun", "good", "bright", "love") -> 0.78f
        else -> 0.5f
    }
    val vocalPresence = when {
        genre in setOf("ambient", "classical", "electronic", "synth") || hasAny("instrumental", "ost", "score", "ambient") -> 0.24f
        else -> 0.72f
    }
    val tempoStability = when {
        hasAny("live", "jazz", "acoustic") -> 0.42f
        hasAny("focus", "ambient", "electronic", "synth", "dance") -> 0.82f
        else -> 0.62f
    }
    return TrackAudioFeatures(
        bpm = bpm.coerceIn(50f, 180f),
        rmsEnergy = energy,
        spectralCentroid = centroid,
        dynamicRange = dynamicRange.coerceIn(0.05f, 1f),
        valence = valence.coerceIn(0.05f, 1f),
        vocalPresence = vocalPresence.coerceIn(0.05f, 1f),
        tempoStability = tempoStability.coerceIn(0.05f, 1f)
    )
}

private data class MixDefinition(
    val name: String,
    val reason: String,
    val targetSize: Int,
    val scorer: (Track) -> Float
)

private data class ScoredTrack(val track: Track, val score: Float)

internal fun buildMixes(
    rankedTracks: List<Track>,
    liked: Map<String, Boolean>,
    longListens: Map<String, Int>,
    skips: Map<String, Int> = emptyMap(),
    localPlays: Map<String, Int> = emptyMap(),
    recentTracks: List<Track> = emptyList(),
    features: Map<String, TrackAudioFeatures> = rankedTracks.associate { it.id to inferAudioFeatures(it) },
    daySeed: String = LocalDate.now().toString()
): List<Mix> {
    val library = deduplicateTracks(rankedTracks)
    val recentIds = deduplicateTracks(recentTracks).map { it.id }.toSet()
    val likedIds = library.filter { liked[it.id] == true || it.liked }.map { it.id }.toSet()
    val libraryCenter = features.featureCenter(library)
    val likedCenter = features.featureCenter(library.filter { it.id in likedIds }).takeIf { likedIds.isNotEmpty() } ?: libraryCenter
    val definitions = listOf(
        MixDefinition("Weekly Discovery", "Low-play tracks with strong completion, like, and low-skip affinity signals.", 36) { track ->
            val lowPlay = 1f / (1f + (localPlays[track.id] ?: track.plays).toFloat())
            val affinity = track.completion * 0.55f +
                (if ((skips[track.id] ?: track.skipped) == 0) 0.25f else 0f) +
                (if (track.id in likedIds) 0.15f else 0f)
            (lowPlay * 70f) + (affinity * 50f) + if (track.id in recentIds) -45f else 0f
        },
        MixDefinition("Heavy Rotation", "Tracks with the strongest recent local play count.", 30) { track ->
            ((localPlays[track.id] ?: 0) * 100f) + track.dailyJitter("$daySeed:heavy") * 0.01f
        },
        MixDefinition("Long Listen Mix", "Longer tracks with high completion and long-listen history.", 30) { track ->
            (track.completion * 100f) + (track.durationSec / 12f) + ((longListens[track.id] ?: 0) * 55f)
        },
        MixDefinition("Quick Shuffle", "Low-skip tracks rotated by a daily seed with a broad artist spread.", 40) { track ->
            val skipScore = 100f - ((skips[track.id] ?: track.skipped).coerceAtMost(10) * 10f)
            skipScore + track.dailyJitter("$daySeed:quick")
        },
        MixDefinition("Rediscover", "Historically played tracks you have not played recently.", 30) { track ->
            if (track.id in recentIds || (localPlays[track.id] ?: 0) > 0) -1000f else track.plays * 18f + track.dailyJitter("$daySeed:rediscover") * 0.05f
        },
        MixDefinition("Liked Radio", "Tracks closest to your explicit likes.", 30) { track ->
            val explicitLike = if (track.id in likedIds) 120f else 0f
            explicitLike + (1f - features.forTrack(track).distanceTo(likedCenter)) * 80f
        },
        MixDefinition("Library Radio", "A library-wide similarity station instead of a favorite or popularity sort.", 36) { track ->
            (1f - features.forTrack(track).distanceTo(libraryCenter)) * 100f + track.dailyJitter("$daySeed:library") * 0.04f
        },
        MixDefinition("Loud Flow", "Energy-ranked tracks using cached audio-feature energy and dynamics.", 30) { track ->
            val feature = features.forTrack(track)
            feature.rmsEnergy * 85f + feature.dynamicRange * 30f + feature.spectralCentroid * 12f
        }
    )
    val globalUsage = mutableMapOf<String, Int>()
    val selectedByName = mutableMapOf<String, Mix>()
    val selectionOrder = listOf(
        "Rediscover",
        "Liked Radio",
        "Heavy Rotation",
        "Long Listen Mix",
        "Loud Flow",
        "Weekly Discovery",
        "Library Radio",
        "Quick Shuffle"
    )
    definitions.sortedBy { definition -> selectionOrder.indexOf(definition.name).takeIf { it >= 0 } ?: selectionOrder.size }.forEach { definition ->
        val candidates = library
            .map { track -> ScoredTrack(track, definition.scorer(track)) }
            .filter { it.score > -999f }
            .sortedWith(compareByDescending<ScoredTrack> { it.score }.thenBy { it.track.dailyJitter("$daySeed:${definition.name}") })
        val selected = candidates.selectDiverseTracks(definition.targetSize, globalUsage)
        selected.tracks.forEach { track -> globalUsage[track.id] = (globalUsage[track.id] ?: 0) + 1 }
        selectedByName[definition.name] = Mix(definition.name, definition.reason, selected.tracks, selected.note)
    }
    return definitions.mapNotNull { selectedByName[it.name] }
}

private data class SelectionResult(val tracks: List<Track>, val note: String? = null)

private fun List<ScoredTrack>.selectDiverseTracks(
    targetSize: Int,
    globalUsage: Map<String, Int>
): SelectionResult {
    val desiredSize = minOf(targetSize, size)
    val strict = chooseTracks(targetSize, globalUsage, albumLimit = 2, artistLimit = 2, artistSpacing = 5, enforceGlobal = true)
    if (strict.size >= desiredSize) return SelectionResult(strict)
    val relaxAlbum = chooseTracks(targetSize, globalUsage, albumLimit = Int.MAX_VALUE, artistLimit = 2, artistSpacing = 5, enforceGlobal = true)
    if (relaxAlbum.size >= desiredSize) return SelectionResult(relaxAlbum, "Small library: relaxed album variety.")
    val relaxArtist = chooseTracks(targetSize, globalUsage, albumLimit = Int.MAX_VALUE, artistLimit = Int.MAX_VALUE, artistSpacing = 0, enforceGlobal = true)
    if (relaxArtist.size >= desiredSize) return SelectionResult(relaxArtist, "Small library: relaxed artist variety.")
    val relaxGlobal = chooseTracks(targetSize, globalUsage, albumLimit = Int.MAX_VALUE, artistLimit = Int.MAX_VALUE, artistSpacing = 0, enforceGlobal = false)
    return SelectionResult(
        tracks = listOf(strict, relaxAlbum, relaxArtist, relaxGlobal)
            .maxByOrNull { it.size }
            ?.ifEmpty { map { it.track }.distinctBy { it.id }.take(targetSize) }
            .orEmpty(),
        note = when (listOf(strict, relaxAlbum, relaxArtist, relaxGlobal).maxByOrNull { it.size }) {
            relaxGlobal -> "Small library: relaxed cross-mix overlap."
            relaxArtist -> "Small library: relaxed artist variety."
            relaxAlbum -> "Small library: relaxed album variety."
            else -> null
        }
    )
}

private fun List<ScoredTrack>.chooseTracks(
    targetSize: Int,
    globalUsage: Map<String, Int>,
    albumLimit: Int,
    artistLimit: Int,
    artistSpacing: Int,
    enforceGlobal: Boolean
): List<Track> {
    val result = mutableListOf<Track>()
    val artistCounts = mutableMapOf<String, Int>()
    val albumCounts = mutableMapOf<String, Int>()
    forEach { scored ->
        val track = scored.track
        if (result.any { it.id == track.id }) return@forEach
        if (enforceGlobal && (globalUsage[track.id] ?: 0) >= 2) return@forEach
        val artist = track.artist.ifBlank { "Unknown Artist" }
        val album = track.album.ifBlank { "Unknown Album" }
        if ((artistCounts[artist] ?: 0) >= artistLimit) return@forEach
        if ((albumCounts[album] ?: 0) >= albumLimit) return@forEach
        if (artistSpacing > 0 && result.takeLast(artistSpacing).any { it.artist == artist }) return@forEach
        result += track
        artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
        albumCounts[album] = (albumCounts[album] ?: 0) + 1
        if (result.size >= targetSize) return result
    }
    return result
}

private fun Map<String, TrackAudioFeatures>.forTrack(track: Track): TrackAudioFeatures =
    this[track.id] ?: inferAudioFeatures(track)

private fun Map<String, TrackAudioFeatures>.featureCenter(tracks: List<Track>): TrackAudioFeatures {
    if (tracks.isEmpty()) return TrackAudioFeatures(100f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
    val features = tracks.map { forTrack(it) }
    return TrackAudioFeatures(
        bpm = features.map { it.bpm }.average().toFloat(),
        rmsEnergy = features.map { it.rmsEnergy }.average().toFloat(),
        spectralCentroid = features.map { it.spectralCentroid }.average().toFloat(),
        dynamicRange = features.map { it.dynamicRange }.average().toFloat(),
        valence = features.map { it.valence }.average().toFloat(),
        vocalPresence = features.map { it.vocalPresence }.average().toFloat(),
        tempoStability = features.map { it.tempoStability }.average().toFloat()
    )
}

private fun TrackAudioFeatures.distanceTo(other: TrackAudioFeatures): Float {
    val bpmDistance = abs((bpm - other.bpm) / 130f)
    return ((bpmDistance +
        abs(rmsEnergy - other.rmsEnergy) +
        abs(spectralCentroid - other.spectralCentroid) +
        abs(dynamicRange - other.dynamicRange) +
        abs(valence - other.valence) +
        abs(vocalPresence - other.vocalPresence) +
        abs(tempoStability - other.tempoStability)) / 7f).coerceIn(0f, 1f)
}

private fun Track.dailyJitter(seed: String): Float =
    ("$seed:$id:$title:$artist").hashCode().absoluteValue.mod(10_000) / 100f

private val vibeProfiles = listOf(
    VibeProfile("Chill", setOf("chill", "calm", "relax", "soft", "easy"), setOf("Calm", "Warm", "Late"), setOf("Ambient", "Indie", "Folk", "Jazz"), "Soft edges, low pressure, and songs that settle in."),
    VibeProfile("Hype", setOf("hype", "party", "pump", "upbeat", "excited"), setOf("Bright", "Drive", "Loud"), setOf("Electronic", "Synth", "Rock", "Dance"), "High-energy tracks for momentum."),
    VibeProfile("Sad", setOf("sad", "down", "blue", "heartbreak", "melancholy"), setOf("Calm", "Late", "Warm"), setOf("Indie", "Ambient", "Folk"), "Slower, softer tracks for sitting with it."),
    VibeProfile("Angry", setOf("angry", "mad", "rage", "aggressive", "loud"), setOf("Loud", "Drive", "Focused"), setOf("Rock", "Metal", "Punk"), "Harder songs with bite and release."),
    VibeProfile("Focus", setOf("focus", "work", "study", "coding", "concentrate"), setOf("Focused", "Calm", "Drive"), setOf("Ambient", "Electronic", "Synth"), "Steady tracks that stay out of the way."),
    VibeProfile("Late Night", setOf("late", "night", "midnight", "drive", "neon"), setOf("Late", "Drive", "Calm"), setOf("Synth", "Ambient", "Electronic"), "Night-drive atmosphere and after-hours pacing."),
    VibeProfile("Happy", setOf("happy", "good", "bright", "sunny", "fun"), setOf("Bright", "Warm", "Drive"), setOf("Indie", "Pop", "Electronic"), "Warm, bright tracks with lift."),
    VibeProfile("Nostalgic", setOf("nostalgic", "throwback", "memory", "old", "comfort"), setOf("Warm", "Calm", "Late"), setOf("Indie", "Rock", "Folk"), "Comfort songs and familiar-feeling cuts."),
    VibeProfile("Workout", setOf("workout", "gym", "run", "training", "fast"), setOf("Drive", "Loud", "Focused"), setOf("Rock", "Electronic", "Synth", "Dance"), "Pace-forward songs built for movement."),
    VibeProfile("Rainy", setOf("rainy", "rain", "storm", "gray", "cozy"), setOf("Calm", "Warm", "Late"), setOf("Ambient", "Indie", "Folk", "Jazz"), "Moody, cozy songs for gray weather.")
)

internal fun buildVibeMixes(
    tracks: List<Track>,
    query: String,
    liked: Map<String, Boolean>,
    longListens: Map<String, Int>,
    localPlays: Map<String, Int>,
    features: Map<String, TrackAudioFeatures> = tracks.associate { it.id to inferAudioFeatures(it) },
    minQualifying: Int = 3,
    daySeed: String = LocalDate.now().toString()
): List<Mix> {
    val normalized = query.trim().lowercase()
    val profiles = if (normalized.isBlank()) {
        vibeProfiles.take(6)
    } else {
        vibeProfiles.filter { profile ->
            normalized in profile.name.lowercase() ||
                profile.aliases.any { normalized in it || it in normalized } ||
                profile.moods.any { normalized in it.lowercase() || it.lowercase() in normalized } ||
                profile.genres.any { normalized in it.lowercase() || it.lowercase() in normalized }
        }.ifEmpty {
            listOf(
                VibeProfile(
                    name = query.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    aliases = setOf(normalized),
                    moods = emptySet(),
                    genres = emptySet(),
                    reason = "A custom vibe search across song, artist, album, genre, and mood."
                )
            )
        }
    }
    return profiles.map { profile ->
        val ranked = rankTracksForVibe(
            tracks = deduplicateTracks(tracks),
            profile = profile,
            query = normalized,
            liked = liked,
            longListens = longListens,
            localPlays = localPlays,
            features = features,
            daySeed = daySeed
        ).artistDiverseTake(30)
        val requiredCount = if (profile.moods.isEmpty() && profile.genres.isEmpty()) 1 else minQualifying
        if (ranked.size < requiredCount && normalized.isBlank().not()) {
            Mix("${profile.name} Vibe", "Not enough tracks fit this vibe yet.", emptyList(), "Need at least $minQualifying qualifying tracks.")
        } else if (ranked.size < requiredCount) {
            Mix("${profile.name} Vibe", profile.reason, emptyList(), "Not enough tracks fit this vibe yet.")
        } else {
            Mix("${profile.name} Vibe", profile.reason, ranked)
        }
    }
}

internal fun rankTracksForVibe(
    tracks: List<Track>,
    profile: VibeProfile,
    query: String,
    liked: Map<String, Boolean>,
    longListens: Map<String, Int>,
    localPlays: Map<String, Int>,
    features: Map<String, TrackAudioFeatures> = tracks.associate { it.id to inferAudioFeatures(it) },
    daySeed: String = LocalDate.now().toString()
): List<Track> =
    tracks.filter { track ->
        val text = "${track.title} ${track.artist} ${track.album} ${track.genre} ${track.mood}".lowercase()
        (query.isNotBlank() && profile.aliases.size == 1 && profile.moods.isEmpty() && profile.genres.isEmpty() && query in text) ||
            qualifiesForVibe(profile.name, features.forTrack(track))
    }.sortedByDescending { track ->
        val text = "${track.title} ${track.artist} ${track.album} ${track.genre} ${track.mood}".lowercase()
        val feature = features.forTrack(track)
        val profileBoost = vibeFitScore(profile.name, feature) * 100f
        val queryBoost = if (query.isNotBlank() && query in text) 90f else 0f
        val lightPersonalTieBreak =
            (if (liked[track.id] == true || track.liked) 4f else 0f) +
                ((longListens[track.id] ?: 0) * 0.5f) +
                ((localPlays[track.id] ?: 0) * 0.25f)
        profileBoost + queryBoost + lightPersonalTieBreak + track.dailyJitter("$daySeed:${profile.name}") * 0.01f
    }

private fun qualifiesForVibe(name: String, feature: TrackAudioFeatures): Boolean =
    when (name.lowercase()) {
        "chill" -> feature.rmsEnergy <= 0.48f && feature.bpm in 70f..112f && feature.dynamicRange <= 0.48f
        "hype", "workout" -> feature.rmsEnergy >= 0.64f && feature.bpm >= 112f && feature.spectralCentroid >= 0.55f
        "angry" -> feature.rmsEnergy >= 0.68f && feature.dynamicRange >= 0.62f && feature.spectralCentroid <= 0.52f
        "sad", "rainy" -> feature.rmsEnergy <= 0.5f && feature.spectralCentroid <= 0.46f && feature.bpm <= 98f
        "focus" -> feature.dynamicRange <= 0.42f && feature.vocalPresence <= 0.48f && feature.tempoStability >= 0.68f
        "late night" -> feature.rmsEnergy in 0.34f..0.68f && feature.spectralCentroid <= 0.52f && feature.bpm <= 108f
        "happy" -> feature.valence >= 0.62f && feature.rmsEnergy >= 0.45f
        "nostalgic" -> feature.valence in 0.36f..0.72f && feature.rmsEnergy in 0.32f..0.68f
        else -> true
    }

private fun vibeFitScore(name: String, feature: TrackAudioFeatures): Float =
    when (name.lowercase()) {
        "chill" -> feature.closeness(92f, 0.3f, 0.36f, 0.32f, 0.52f)
        "hype", "workout" -> feature.closeness(134f, 0.84f, 0.76f, 0.52f, 0.7f)
        "angry" -> feature.closeness(126f, 0.86f, 0.34f, 0.82f, 0.18f)
        "sad", "rainy" -> feature.closeness(78f, 0.28f, 0.3f, 0.38f, 0.24f)
        "focus" -> feature.closeness(100f, 0.42f, 0.48f, 0.25f, 0.5f) + feature.tempoStability * 0.25f - feature.vocalPresence * 0.18f
        "late night" -> feature.closeness(92f, 0.5f, 0.34f, 0.42f, 0.42f)
        "happy" -> feature.closeness(112f, 0.62f, 0.68f, 0.46f, 0.78f)
        "nostalgic" -> feature.closeness(96f, 0.48f, 0.45f, 0.5f, 0.52f)
        else -> 0.5f
    }

private fun TrackAudioFeatures.closeness(targetBpm: Float, targetEnergy: Float, targetCentroid: Float, targetDynamicRange: Float, targetValence: Float): Float {
    val distance = (
        abs((bpm - targetBpm) / 130f) +
            abs(rmsEnergy - targetEnergy) +
            abs(spectralCentroid - targetCentroid) +
            abs(dynamicRange - targetDynamicRange) +
            abs(valence - targetValence)
        ) / 5f
    return (1f - distance).coerceIn(0f, 1f)
}

internal fun filterTracks(tracks: List<Track>, query: String): List<Track> {
    val normalized = query.trim().lowercase()
    val canonicalTracks = deduplicateTracks(tracks)
    if (normalized.isBlank()) return canonicalTracks
    return canonicalTracks.filter { track ->
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
            deduplicateTracks(tracks).sortedByDescending { it.durationSec + ((longListens[it.id] ?: 0) * 120) }
        DiscoveryFilter.Liked ->
            deduplicateTracks(tracks).filter { liked[it.id] == true || it.liked }
        DiscoveryFilter.LowSkips ->
            deduplicateTracks(tracks).sortedWith(compareBy<Track> { skips[it.id] ?: it.skipped }.thenByDescending { it.completion })
        DiscoveryFilter.SimilarMood ->
            deduplicateTracks(tracks).filter { it.mood == currentTrack.mood && it.id != currentTrack.id }
                .ifEmpty { deduplicateTracks(tracks).filter { it.genre == currentTrack.genre && it.id != currentTrack.id } }
        DiscoveryFilter.Rediscover ->
            deduplicateTracks(tracks).filter { (liked[it.id] != true) && (longListens[it.id] ?: 0) == 0 }
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
    val library = deduplicateTracks(tracks)
    val ranked = library
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
    return deduplicateTracks(listOf(seed) + ranked.artistDiverseTake(49)).take(50)
}

internal fun buildGuestDjQueue(
    mode: GuestDjMode,
    seed: Track,
    tracks: List<Track>,
    liked: Map<String, Boolean>,
    longListens: Map<String, Int>,
    skips: Map<String, Int>,
    localPlays: Map<String, Int>,
    recentlyPlayedIds: List<String>
): List<Track> {
    val recentIds = recentlyPlayedIds.take(12).toSet()
    val library = deduplicateTracks(tracks)
    val ranked = library.sortedByDescending { track ->
        val base = recommendationScore(
            track = track,
            liked = liked[track.id] == true,
            longListens = longListens[track.id] ?: 0,
            skips = skips[track.id] ?: 0,
            localPlays = localPlays[track.id] ?: 0
        )
        val continuity =
            (if (track.id == seed.id) 120f else 0f) +
                (if (track.mood == seed.mood) 28f else 0f) +
                (if (track.genre == seed.genre) 22f else 0f) +
                (if (track.artist == seed.artist) 18f else 0f) +
                (if (track.album == seed.album) 10f else 0f)
        val freshness = if (track.id in recentIds) -18f else 8f
        val modeBoost = when (mode) {
            GuestDjMode.Flow -> continuity + freshness
            GuestDjMode.Familiar -> base + if (liked[track.id] == true || (localPlays[track.id] ?: 0) > 1) 36f else 0f
            GuestDjMode.Discovery -> freshness + if ((localPlays[track.id] ?: 0) == 0 && track.plays <= 2) 40f else 0f
            GuestDjMode.DeepCuts -> {
                val lowPlayBoost = (30 - track.plays.coerceAtMost(30)) * 2.8f
                val skipGuard = if ((skips[track.id] ?: track.skipped) <= 2) 24f else -28f
                lowPlayBoost + skipGuard - if (liked[track.id] == true && track.plays > 12) 30f else 0f
            }
            GuestDjMode.ArtistFocus -> if (track.artist == seed.artist) 62f else if (track.album == seed.album) 34f else -10f
            GuestDjMode.HighEnergy -> if (track.mood in setOf("Drive", "Loud", "Bright", "Focused") || track.genre in setOf("Rock", "Synth", "Electronic")) 44f else -10f
            GuestDjMode.Chill -> if (track.mood in setOf("Calm", "Warm", "Late") || track.genre in setOf("Ambient", "Indie", "Folk", "Jazz")) 44f else -10f
        }
        base + continuity + modeBoost
    }
    val queue = ranked.distinctBy { it.id }
    return if (mode == GuestDjMode.ArtistFocus) {
        queue.take(50)
    } else {
        queue.artistDiverseTake(50)
    }
}

internal fun buildJarvisDjQueue(
    prompt: String,
    mode: GuestDjMode,
    seed: Track,
    tracks: List<Track>,
    liked: Map<String, Boolean>,
    longListens: Map<String, Int>,
    skips: Map<String, Int>,
    localPlays: Map<String, Int>,
    recentlyPlayedIds: List<String>
): List<Track> {
    val lowered = prompt.lowercase()
    val base = buildGuestDjQueue(mode, seed, tracks, liked, longListens, skips, localPlays, recentlyPlayedIds)
    val terms = prompt.split(" ", ",", ".", "!", "?")
        .map { it.trim().lowercase() }
        .filter { it.length >= 4 }
        .toSet()
    val promptedQueue = base.sortedByDescending { track ->
        val text = "${track.title} ${track.artist} ${track.album} ${track.genre} ${track.mood}".lowercase()
        val promptMatch = terms.count { it in text } * 18f
        val lessPenalty = if (lowered.contains("less") || lowered.contains("don't") || lowered.contains("dont")) {
            terms.count { it in text } * -34f
        } else {
            0f
        }
        promptMatch + lessPenalty + if (track.id == seed.id) 20f else 0f
    }.distinctBy { it.id }
    return if (mode == GuestDjMode.ArtistFocus) {
        promptedQueue.take(50)
    } else {
        promptedQueue.artistDiverseTake(50)
    }
}

internal fun inferGuestDjMode(prompt: String, fallback: GuestDjMode): GuestDjMode {
    val text = prompt.lowercase()
    return when {
        listOf("deep", "forgotten", "rare", "less played", "unknown").any { it in text } -> GuestDjMode.DeepCuts
        listOf("new", "fresh", "discover", "surprise", "different").any { it in text } -> GuestDjMode.Discovery
        listOf("familiar", "favorites", "liked", "comfort", "safe").any { it in text } -> GuestDjMode.Familiar
        listOf("artist", "same band", "same singer", "album").any { it in text } -> GuestDjMode.ArtistFocus
        listOf("loud", "energy", "workout", "drive", "heavy", "fast").any { it in text } -> GuestDjMode.HighEnergy
        listOf("chill", "calm", "soft", "relax", "sleep", "quiet").any { it in text } -> GuestDjMode.Chill
        else -> fallback
    }
}

internal fun findPromptSeed(prompt: String, tracks: List<Track>): Track? {
    val text = prompt.lowercase()
    return tracks.firstOrNull { track ->
        track.title.lowercase() in text ||
            track.artist.lowercase() in text ||
            track.album.lowercase() in text
    }
}

internal fun queueReason(track: Track, seed: Track, mode: GuestDjMode): String =
    when {
        track.id == seed.id -> "Current seed"
        track.artist == seed.artist -> "same artist"
        track.album == seed.album -> "same album"
        track.mood == seed.mood -> "same ${seed.mood.lowercase()} mood"
        track.genre == seed.genre -> "same ${seed.genre.lowercase()} lane"
        mode == GuestDjMode.DeepCuts -> "deep cut candidate"
        mode == GuestDjMode.Discovery -> "fresh discovery candidate"
        mode == GuestDjMode.Familiar -> "strong listening signal"
        else -> mode.label
    }

internal fun jarvisDjReply(prompt: String, mode: GuestDjMode, current: Track, queue: List<Track>): String {
    val next = queue.drop(1).firstOrNull()
    val nextText = next?.let { " Then I am lining up ${it.title} because it is ${queueReason(it, current, mode)}." }.orEmpty()
    return "I heard: \"$prompt\". I built a ${mode.label} queue around ${current.title}.${nextText}"
}

internal fun buildAutoplayQueue(
    seed: Track,
    tracks: List<Track>,
    liked: Map<String, Boolean>,
    longListens: Map<String, Int>,
    skips: Map<String, Int>,
    localPlays: Map<String, Int>,
    recentlyPlayedIds: List<String>
): List<Track> {
    val recentPenaltyIds = recentlyPlayedIds.take(8).toSet()
    val library = deduplicateTracks(tracks)
    val ranked = library
        .filterNot { it.id == seed.id }
        .sortedByDescending { track ->
            val continuity =
                (if (track.mood == seed.mood) 42f else 0f) +
                    (if (track.genre == seed.genre) 32f else 0f) +
                    (if (track.artist == seed.artist) 8f else 0f)
            val freshness = if (track.id in recentPenaltyIds) -24f else 10f
            continuity + freshness + recommendationScore(
                track = track,
                liked = liked[track.id] == true,
                longListens = longListens[track.id] ?: 0,
                skips = skips[track.id] ?: 0,
                localPlays = localPlays[track.id] ?: 0
            )
        }
    return ranked.ifEmpty { library.filterNot { it.id == seed.id } }.artistDiverseTake(50)
}

private fun List<Track>.artistDiverseTake(maxCount: Int): List<Track> {
    if (maxCount <= 0) return emptyList()
    val distinctTracks = distinctBy { it.id }
    if (distinctTracks.size <= 2) return distinctTracks.take(maxCount)
    val grouped = distinctTracks
        .groupBy { it.artist.ifBlank { "Unknown Artist" } }
        .mapValues { (_, tracks) -> ArrayDeque(tracks) }
        .toMutableMap()
    val artistOrder = grouped.keys.toMutableList()
    val result = mutableListOf<Track>()
    var lastArtist: String? = null
    while (result.size < maxCount && grouped.isNotEmpty()) {
        val nextArtist = artistOrder.firstOrNull { artist ->
            artist != lastArtist && grouped[artist]?.isNotEmpty() == true
        } ?: artistOrder.firstOrNull { artist -> grouped[artist]?.isNotEmpty() == true } ?: break
        val nextTrack = grouped[nextArtist]?.removeFirstOrNull() ?: break
        result += nextTrack
        lastArtist = nextArtist
        if (grouped[nextArtist]?.isEmpty() == true) {
            grouped.remove(nextArtist)
            artistOrder.remove(nextArtist)
        } else {
            artistOrder.remove(nextArtist)
            artistOrder.add(nextArtist)
        }
    }
    return result
}

private fun List<Track>.stableShuffleBy(seed: String): List<Track> =
    sortedWith(
        compareBy<Track> {
            "${seed}:${it.id}:${it.title}:${it.artist}".hashCode()
        }.thenBy { it.title }
    )

internal fun nextQueueIndex(currentIndex: Int, queueSize: Int, repeatEnabled: Boolean): Int {
    if (queueSize <= 1) return 0
    val next = currentIndex + 1
    return when {
        next < queueSize -> next
        repeatEnabled -> 0
        else -> queueSize - 1
    }
}

internal fun previousQueueIndex(currentIndex: Int, queueSize: Int, repeatEnabled: Boolean): Int {
    if (queueSize <= 1) return 0
    val previous = currentIndex - 1
    return when {
        previous >= 0 -> previous
        repeatEnabled -> queueSize - 1
        else -> 0
    }
}

internal fun isQueueEnd(currentIndex: Int, queueSize: Int, repeatEnabled: Boolean): Boolean =
    queueSize <= 1 && !repeatEnabled || currentIndex >= queueSize - 1 && !repeatEnabled

private fun moodFromGenre(genre: String, tags: Set<String> = emptySet()): String =
    when {
        tags.any { it.equals("Loud", ignoreCase = true) } -> "Loud"
        tags.any { it.equals("Drive", ignoreCase = true) } -> "Drive"
        genre.lowercase() in setOf("ambient", "classical", "jazz") -> "Calm"
        genre.lowercase() in setOf("electronic", "synth", "dance") -> "Drive"
        genre.lowercase() in setOf("rock", "metal", "punk") -> "Loud"
        genre.lowercase() in setOf("folk", "indie") -> "Warm"
        else -> "Library"
    }

internal fun formatDuration(durationSec: Int): String {
    val minutes = durationSec.coerceAtLeast(0) / 60
    val seconds = durationSec.coerceAtLeast(0) % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun Throwable.cleanMessage(): String =
    when (this) {
        is SocketTimeoutException -> "server timed out. Try Reload, or switch the URL between http and https."
        else -> message?.replace('\n', ' ')?.take(180) ?: javaClass.simpleName
    }

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

internal fun List<Track>.toTrackCacheString(): String {
    val items = JSONArray()
    forEach { track ->
        items.put(
            JSONObject()
                .put("id", track.id)
                .put("title", track.title)
                .put("artist", track.artist)
                .put("album", track.album)
                .put("genre", track.genre)
                .put("mood", track.mood)
                .put("durationSec", track.durationSec)
                .put("plays", track.plays)
                .put("completion", track.completion.toDouble())
                .put("skipped", track.skipped)
                .put("liked", track.liked)
                .put("imageUrl", track.imageUrl)
                .put("bitrate", track.bitrate)
                .put("filename", track.filename)
                .put("tags", JSONArray().also { tags -> track.tags.forEach(tags::put) })
                .put(
                    "alternates",
                    JSONArray().also { alternates ->
                        track.alternates.forEach { alternate ->
                            alternates.put(
                                JSONObject()
                                    .put("id", alternate.id)
                                    .put("title", alternate.title)
                                    .put("artist", alternate.artist)
                                    .put("album", alternate.album)
                                    .put("durationSec", alternate.durationSec)
                                    .put("bitrate", alternate.bitrate)
                                    .put("imageUrl", alternate.imageUrl)
                            )
                        }
                    }
                )
        )
    }
    return items.toString()
}

internal fun String.toTrackList(): List<Track> =
    runCatching {
        val items = JSONArray(this)
        (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            Track(
                id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                title = item.optString("title", "Untitled").removePrefix("MID -").trim().ifBlank { "Untitled" },
                artist = item.optString("artist").cleanUnknown("Unknown Artist"),
                album = item.optString("album").cleanUnknown("Unknown Album"),
                genre = item.optString("genre").takeUnless { it.lowercase() in internalGenreLabels }.orEmpty(),
                mood = item.optString("mood", "Library"),
                durationSec = item.optInt("durationSec", 1).coerceAtLeast(1),
                plays = item.optInt("plays", 0),
                completion = item.optDouble("completion", 0.0).toFloat().coerceIn(0f, 1f),
                skipped = item.optInt("skipped", 0),
                liked = item.optBoolean("liked", false),
                imageUrl = item.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
                bitrate = item.optInt("bitrate", 0),
                tags = item.optJSONArray("tags")?.let { tags ->
                    (0 until tags.length()).mapNotNull { tagIndex -> tags.optString(tagIndex).takeIf { it.isNotBlank() } }.toSet()
                }.orEmpty(),
                filename = item.optString("filename").takeIf { it.isNotBlank() && it != "null" },
                alternates = item.optJSONArray("alternates")?.let { alternates ->
                    (0 until alternates.length()).mapNotNull { alternateIndex ->
                        val alternate = alternates.optJSONObject(alternateIndex) ?: return@mapNotNull null
                        TrackAlternate(
                            id = alternate.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                            title = alternate.optString("title", "Untitled"),
                            artist = alternate.optString("artist"),
                            album = alternate.optString("album"),
                            durationSec = alternate.optInt("durationSec", 1).coerceAtLeast(1),
                            bitrate = alternate.optInt("bitrate", 0),
                            imageUrl = alternate.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" }
                        )
                    }
                }.orEmpty()
            )
        }
    }.getOrDefault(emptyList())

internal fun List<JellyfinPlaylist>.toPlaylistCacheString(): String {
    val items = JSONArray()
    forEach { playlist ->
        items.put(
            JSONObject()
                .put("id", playlist.id)
                .put("name", playlist.name)
                .put("childCount", playlist.childCount)
                .put("imageUrl", playlist.imageUrl)
        )
    }
    return items.toString()
}

internal fun String.toPlaylistList(): List<JellyfinPlaylist> =
    runCatching {
        val items = JSONArray(this)
        (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            JellyfinPlaylist(
                id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                name = item.optString("name", "Playlist"),
                childCount = item.optInt("childCount", 0),
                imageUrl = item.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }.getOrDefault(emptyList())

internal fun Map<String, TrackAudioFeatures>.toAudioFeatureCacheString(): String {
    val root = JSONObject()
    forEach { (trackId, feature) ->
        root.put(
            trackId,
            JSONObject()
                .put("bpm", feature.bpm.toDouble())
                .put("rmsEnergy", feature.rmsEnergy.toDouble())
                .put("spectralCentroid", feature.spectralCentroid.toDouble())
                .put("dynamicRange", feature.dynamicRange.toDouble())
                .put("valence", feature.valence.toDouble())
                .put("vocalPresence", feature.vocalPresence.toDouble())
                .put("tempoStability", feature.tempoStability.toDouble())
        )
    }
    return root.toString()
}

internal fun String.toAudioFeatureMap(): Map<String, TrackAudioFeatures> =
    runCatching {
        val root = JSONObject(this)
        root.keys().asSequence().mapNotNull { trackId ->
            val item = root.optJSONObject(trackId) ?: return@mapNotNull null
            trackId to TrackAudioFeatures(
                bpm = item.optDouble("bpm", 100.0).toFloat().coerceIn(50f, 180f),
                rmsEnergy = item.optDouble("rmsEnergy", 0.5).toFloat().coerceIn(0f, 1f),
                spectralCentroid = item.optDouble("spectralCentroid", 0.5).toFloat().coerceIn(0f, 1f),
                dynamicRange = item.optDouble("dynamicRange", 0.5).toFloat().coerceIn(0f, 1f),
                valence = item.optDouble("valence", 0.5).toFloat().coerceIn(0f, 1f),
                vocalPresence = item.optDouble("vocalPresence", 0.5).toFloat().coerceIn(0f, 1f),
                tempoStability = item.optDouble("tempoStability", 0.5).toFloat().coerceIn(0f, 1f)
            )
        }.toMap()
    }.getOrDefault(emptyMap())

internal fun JSONObject.hasPrimaryImage(): Boolean =
    optJSONObject("ImageTags")?.optString("Primary")?.isNotBlank() == true

internal inline fun <reified T : Enum<T>> String?.enumValueOrDefault(default: T): T =
    this?.let { value ->
        enumValues<T>().firstOrNull { it.name == value }
    } ?: default

internal fun String?.toTabOrDefault(default: Tab): Tab =
    when (this) {
        "Vibe", "Playlists", "Mixes" -> Tab.Mixes
        "Discover" -> Tab.Discover
        "Library" -> Tab.Library
        "Home" -> Tab.Home
        else -> default
    }

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
    val moodEnergy = when (track.mood.lowercase()) {
        "loud", "drive", "bright", "focused" -> 0.24f
        "late" -> 0.14f
        "calm", "warm" -> -0.08f
        else -> 0.04f
    }
    val genreShape = when (track.genre.lowercase()) {
        "rock", "metal", "punk" -> 3
        "electronic", "synth", "dance" -> 4
        "ambient", "classical", "jazz" -> 7
        "hip hop", "rap" -> 5
        else -> 6
    }
    val completionLift = track.completion.coerceIn(0f, 1f) * 0.12f
    return List(bandCount) { index ->
        val phrase = abs(sin((seed + index * 37).toFloat() * 0.011f))
        val beat = if ((index + seed).floorMod(genreShape) == 0) 0.26f else 0f
        val lowEnd = if (index < bandCount / 4 && track.genre.lowercase() in setOf("rock", "electronic", "synth", "hip hop", "rap")) 0.16f else 0f
        (0.16f + moodEnergy + completionLift + phrase * 0.42f + beat + lowEnd).coerceIn(0.08f, 1f)
    }
}

private fun Int.floorMod(other: Int): Int =
    ((this % other) + other) % other

internal fun Map<String, *>.toStorageString(): String =
    entries.joinToString(";") { "${it.key}=${it.value}" }
