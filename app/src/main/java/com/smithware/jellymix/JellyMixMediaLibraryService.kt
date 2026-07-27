package com.smithware.jellymix

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.security.MessageDigest
import java.io.File
import java.util.concurrent.Executors

private const val MEDIA3_TAG = "JellyMixAutoMedia3"
private const val AUTO_PACKAGE_GEARHEAD = "com.google.android.projection.gearhead"
private const val MEDIA_ROOT = "media3:root"
private const val MEDIA_PLAYLISTS = "media3:playlists"
private const val MEDIA_ALBUMS = "media3:albums"
private const val MEDIA_ARTISTS = "media3:artists"
private const val MEDIA_RECENT = "media3:recent"
private const val MEDIA_FAVORITES = "media3:favorites"
private const val MEDIA_PLAYLIST_PREFIX = "media3:playlist:"
private const val MEDIA_ALBUM_PREFIX = "media3:album:"
private const val MEDIA_ARTIST_PREFIX = "media3:artist:"
private const val MEDIA_TRACK_PREFIX = "media3:track:"

class JellyMixMediaLibraryService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private val client = JellyfinClient()
    private val mediaExecutor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "JellyMixAutoMedia").apply { isDaemon = true }
        })

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        prefs().edit().putBoolean("isPlaying", isPlaying).apply()
                        JellyMixWidgetProvider.updateAll(this@JellyMixMediaLibraryService)
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                            Log.w(MEDIA3_TAG, "Android Auto playback changed by audio focus loss; playWhenReady=$playWhenReady")
                        }
                    }

                    override fun onVolumeChanged(volume: Float) {
                        if (volume < 1f) {
                            Log.w(MEDIA3_TAG, "Android Auto playback volume changed, possible focus duck; volume=$volume")
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val trackId = mediaItem?.mediaId?.removePrefix(MEDIA_TRACK_PREFIX)?.takeIf { it != mediaItem.mediaId } ?: return
                        val tracks = readCachedTracks()
                        val index = tracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                        prefs().edit()
                            .putString("currentTrackId", trackId)
                            .putString("queueIds", tracks.joinToString(",") { it.id })
                            .putInt("queueIndex", index)
                            .putString("queueTitle", "Android Auto")
                            .apply()
                        JellyMixWidgetProvider.updateAll(this@JellyMixMediaLibraryService)
                    }
                }
            )
        }
        player = exoPlayer
        session = MediaLibrarySession.Builder(this, exoPlayer, callback)
            .setId("JellyMixMediaLibrarySession")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        player?.release()
        player = null
        mediaExecutor.shutdownNow()
        super.onDestroy()
    }

    private val callback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val packageName = controller.packageName
            val allowed = packageName == this@JellyMixMediaLibraryService.packageName ||
                packageName == AUTO_PACKAGE_GEARHEAD
            if (!allowed) {
                Log.w(MEDIA3_TAG, "Rejected Android Auto caller package=$packageName signatures=${callerSignatures(packageName)}")
                return MediaSession.ConnectionResult.reject()
            }
            return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                Player.Commands.Builder().addAllCommands().build()
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browsableItem(MEDIA_ROOT, "JellyMix", "Jellyfin music"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            mediaExecutor.submit<LibraryResult<ImmutableList<MediaItem>>> {
                LibraryResult.ofItemList(children(parentId, page, pageSize), params)
            }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(itemForMediaId(mediaId), null))

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> =
            Futures.immediateFuture(LibraryResult.ofVoid(params))

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return mediaExecutor.submit<LibraryResult<ImmutableList<MediaItem>>> {
                val normalized = query.trim().lowercase()
                val matches = readCachedTracks()
                    .filter {
                        it.title.lowercase().contains(normalized) ||
                            it.artist.lowercase().contains(normalized) ||
                            it.album.lowercase().contains(normalized)
                    }
                    .toPlayableItems()
                LibraryResult.ofItemList(paginate(matches, page, pageSize), params)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return mediaExecutor.submit<MutableList<MediaItem>> {
                mediaItems.flatMap { mediaItem -> queueForMediaId(mediaItem.mediaId) }
                    .ifEmpty { readCachedTracks().take(25) }
                    .map { it.toPlayableMediaItem() }
                    .toMutableList()
            }
        }
    }

    private fun children(parentId: String, page: Int, pageSize: Int): MutableList<MediaItem> {
        val tracks = readLiveOrCachedTracks()
        val prefs = prefs()
        val playlists = readLiveOrCachedPlaylists()
        val items = when {
            !isAuthenticated() -> listOf(browsableItem("media3:error:not-authenticated", "Connect JellyMix", "Open JellyMix on your phone and sign in to Jellyfin."))
            parentId == MEDIA_ROOT -> listOf(
                browsableItem(MEDIA_PLAYLISTS, "Playlists", "${playlists.size} Jellyfin playlists"),
                browsableItem(MEDIA_ALBUMS, "Albums", "${tracks.map { it.album }.filter { it.isNotBlank() }.distinct().size} albums"),
                browsableItem(MEDIA_ARTISTS, "Artists", "${tracks.map { it.artist }.filter { it.isNotBlank() }.distinct().size} artists"),
                browsableItem(MEDIA_RECENT, "Recently Added", "Latest Jellyfin tracks"),
                browsableItem(MEDIA_FAVORITES, "Favorites", "Liked Jellyfin tracks")
            )
            parentId == MEDIA_PLAYLISTS -> playlists.map { playlist ->
                browsableItem("$MEDIA_PLAYLIST_PREFIX${playlist.id}", playlist.name, "${playlist.childCount} items", playlist.imageUrl)
            }
            parentId.startsWith(MEDIA_PLAYLIST_PREFIX) -> {
                val playlistId = parentId.removePrefix(MEDIA_PLAYLIST_PREFIX)
                fetchPlaylistTracks(playlistId).toPlayableItems()
            }
            parentId == MEDIA_ALBUMS -> tracks
                .filter { it.album.isNotBlank() }
                .groupBy { it.album }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .map { (album, albumTracks) -> browsableItem("$MEDIA_ALBUM_PREFIX${Uri.encode(album)}", album, albumTracks.first().artist, albumTracks.first().imageUrl) }
            parentId.startsWith(MEDIA_ALBUM_PREFIX) -> {
                val album = Uri.decode(parentId.removePrefix(MEDIA_ALBUM_PREFIX))
                tracks.filter { it.album.equals(album, ignoreCase = true) }.toPlayableItems()
            }
            parentId == MEDIA_ARTISTS -> tracks
                .filter { it.artist.isNotBlank() }
                .groupBy { it.artist }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .map { (artist, artistTracks) -> browsableItem("$MEDIA_ARTIST_PREFIX${Uri.encode(artist)}", artist, "${artistTracks.size} tracks", artistTracks.first().imageUrl) }
            parentId.startsWith(MEDIA_ARTIST_PREFIX) -> {
                val artist = Uri.decode(parentId.removePrefix(MEDIA_ARTIST_PREFIX))
                tracks.filter { it.artist.equals(artist, ignoreCase = true) }.toPlayableItems()
            }
            parentId == MEDIA_RECENT -> tracks.sortedByDescending { it.plays }.take(100).toPlayableItems()
            parentId == MEDIA_FAVORITES -> tracks.filter { prefs.getString("liked", "").orEmpty().toBooleanMap()[it.id] == true || it.liked }.toPlayableItems()
            else -> emptyList()
        }
        return paginate(items, page, pageSize).toMutableList()
    }

    private fun itemForMediaId(mediaId: String): MediaItem =
        mediaId.removePrefix(MEDIA_TRACK_PREFIX).takeIf { it != mediaId }
            ?.let { trackId -> readCachedTracks().firstOrNull { it.id == trackId }?.toPlayableMediaItem() }
            ?: browsableItem(mediaId, "JellyMix", "Open JellyMix on your phone.")

    private fun queueForMediaId(mediaId: String): List<Track> {
        val tracks = readLiveOrCachedTracks()
        return when {
            mediaId.startsWith(MEDIA_TRACK_PREFIX) -> {
                val id = mediaId.removePrefix(MEDIA_TRACK_PREFIX)
                tracks.firstOrNull { it.id == id }?.let { track -> listOf(track) + tracks.filterNot { it.id == track.id }.take(99) }.orEmpty()
            }
            mediaId.startsWith(MEDIA_PLAYLIST_PREFIX) -> fetchPlaylistTracks(mediaId.removePrefix(MEDIA_PLAYLIST_PREFIX))
            mediaId.startsWith(MEDIA_ALBUM_PREFIX) -> tracks.filter { it.album.equals(Uri.decode(mediaId.removePrefix(MEDIA_ALBUM_PREFIX)), ignoreCase = true) }
            mediaId.startsWith(MEDIA_ARTIST_PREFIX) -> tracks.filter { it.artist.equals(Uri.decode(mediaId.removePrefix(MEDIA_ARTIST_PREFIX)), ignoreCase = true) }
            mediaId == MEDIA_FAVORITES -> tracks.filter { it.liked }
            mediaId == MEDIA_RECENT -> tracks.sortedByDescending { it.plays }.take(100)
            else -> tracks.take(100)
        }
    }

    private fun readLiveOrCachedTracks(): List<Track> {
        if (!isAuthenticated()) return readCachedTracks()
        return runCatching {
            val prefs = prefs()
            client.fetchAudioTracksWithCount(
                prefs.getString("serverUrl", "").orEmpty(),
                prefs.getString("userId", "").orEmpty(),
                prefs.getString("token", "").orEmpty()
            ).tracks
        }.getOrElse {
            Log.w(MEDIA3_TAG, "Jellyfin track fetch failed for Android Auto; using cache: ${it.message}")
            readCachedTracks()
        }
    }

    private fun readLiveOrCachedPlaylists(): List<JellyfinPlaylist> {
        if (!isAuthenticated()) return readCachedPlaylists()
        return runCatching {
            val prefs = prefs()
            client.fetchPlaylists(
                prefs.getString("serverUrl", "").orEmpty(),
                prefs.getString("userId", "").orEmpty(),
                prefs.getString("token", "").orEmpty()
            )
        }.getOrElse {
            Log.w(MEDIA3_TAG, "Jellyfin playlist fetch failed for Android Auto; using cache: ${it.message}")
            readCachedPlaylists()
        }
    }

    private fun fetchPlaylistTracks(playlistId: String): List<Track> {
        if (!isAuthenticated()) return emptyList()
        val prefs = prefs()
        return runCatching {
            client.fetchPlaylistTracks(
                prefs.getString("serverUrl", "").orEmpty(),
                prefs.getString("userId", "").orEmpty(),
                prefs.getString("token", "").orEmpty(),
                playlistId
            )
        }.getOrElse {
            Log.w(MEDIA3_TAG, "Jellyfin playlist track fetch failed for Android Auto playlist=$playlistId: ${it.message}")
            emptyList()
        }
    }

    private fun readCachedTracks(): List<Track> =
        prefs().getString("cachedTracks", null)?.toTrackList().orEmpty()

    private fun readCachedPlaylists(): List<JellyfinPlaylist> =
        prefs().getString("cachedPlaylists", null)?.toPlaylistList().orEmpty()

    private fun isAuthenticated(): Boolean =
        prefs().getString("serverUrl", "").orEmpty().isNotBlank() &&
            prefs().getString("userId", "").orEmpty().isNotBlank() &&
            prefs().getString("token", "").orEmpty().isNotBlank()

    private fun prefs() = getSharedPreferences("jellymix", Context.MODE_PRIVATE)

    private fun Track.toPlayableMediaItem(): MediaItem {
        val playbackUri = offlineAudioFile(this).takeIf { it.exists() && it.length() > 0L }?.let(Uri::fromFile)
            ?: if (isAuthenticated() && !id.startsWith("sample-")) {
                Uri.parse(
                    client.streamUrl(
                        prefs().getString("serverUrl", "").orEmpty(),
                        id,
                        prefs().getString("token", "").orEmpty()
                    )
                )
        } else {
            Uri.EMPTY
        }
        return MediaItem.Builder()
            .setMediaId("$MEDIA_TRACK_PREFIX$id")
            .setUri(playbackUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(imageUrl?.let(Uri::parse))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    private fun offlineAudioFile(track: Track): File =
        File(File(filesDir, "offline-audio"), "${track.id.safeOfflineFileName()}.audio")

    private fun List<Track>.toPlayableItems(): List<MediaItem> = map { it.toPlayableMediaItem() }

    private fun browsableItem(mediaId: String, title: String, subtitle: String, artworkUrl: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artworkUrl?.let(Uri::parse))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun paginate(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        if (page < 0 || pageSize <= 0) return items
        val from = (page * pageSize).coerceAtMost(items.size)
        val to = (from + pageSize).coerceAtMost(items.size)
        return items.subList(from, to)
    }

    private fun callerSignatures(packageName: String): String =
        runCatching {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val info = packageManager.getPackageInfo(packageName, flags)
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                info.signatures.orEmpty()
            }
            signatures.joinToString { signature -> signature.sha256Digest() }
        }.getOrElse { "unavailable:${it.message}" }

    private fun android.content.pm.Signature.sha256Digest(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
