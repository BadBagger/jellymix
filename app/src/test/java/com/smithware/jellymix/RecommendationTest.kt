package com.smithware.jellymix

import com.smithware.jellymix.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class RecommendationTest {
    @Test
    fun likedLongCompletedTrackBeatsSkippedTrack() {
        val strongSignalTrack = Track(
            id = "a",
            title = "Signal",
            artist = "Artist",
            album = "Album",
            genre = "Synth",
            mood = "Late",
            durationSec = 240,
            plays = 8,
            completion = 0.95f,
            skipped = 0,
            liked = true
        )
        val skippedTrack = strongSignalTrack.copy(id = "b", liked = false, completion = 0.45f, plays = 2)

        val strongScore = recommendationScore(strongSignalTrack, liked = true, longListens = 2, skips = 0)
        val skippedScore = recommendationScore(skippedTrack, liked = false, longListens = 0, skips = 4)

        assertTrue(strongScore > skippedScore)
    }

    @Test
    fun localPlaysIncreaseRecommendationScore() {
        val track = sampleTrack("local", liked = false, plays = 0, completion = 0.5f)

        val firstScore = recommendationScore(track, liked = false, longListens = 0, skips = 0, localPlays = 0)
        val replayScore = recommendationScore(track, liked = false, longListens = 0, skips = 0, localPlays = 5)

        assertTrue(replayScore > firstScore)
    }

    @Test
    fun mixesAlwaysIncludeCoreDiscoveryBuckets() {
        val tracks = listOf(
            sampleTrack("1", liked = true, plays = 10, completion = 0.9f),
            sampleTrack("2", liked = false, plays = 1, completion = 0.8f),
            sampleTrack("3", liked = true, plays = 4, completion = 0.7f)
        )

        val mixes = buildMixes(
            rankedTracks = tracks,
            liked = tracks.associate { it.id to it.liked },
            longListens = mapOf("2" to 3)
        )

        assertEquals(
            listOf("Weekly Discovery", "Heavy Rotation", "Long Listen Mix", "Quick Shuffle", "Rediscover", "Liked Radio", "Indie Radio", "Warm Flow"),
            mixes.map { it.name }
        )
        assertEquals("2", mixes.first { it.name == "Long Listen Mix" }.tracks.first().id)
    }

    @Test
    fun filterTracksMatchesArtistAlbumGenreAndMood() {
        val tracks = listOf(
            sampleTrack("1", liked = true, plays = 10, completion = 0.9f),
            sampleTrack("2", liked = false, plays = 1, completion = 0.8f).copy(artist = "Glass Harbor", genre = "Synth", mood = "Late")
        )

        assertEquals(listOf("2"), filterTracks(tracks, "glass").map { it.id })
        assertEquals(listOf("2"), filterTracks(tracks, "synth").map { it.id })
        assertEquals(listOf("1", "2"), filterTracks(tracks, "").map { it.id })
    }

    @Test
    fun discoveryFilterReturnsLikedAndSimilarMoodTracks() {
        val current = sampleTrack("1", liked = true, plays = 10, completion = 0.9f).copy(mood = "Warm", genre = "Indie")
        val liked = sampleTrack("2", liked = false, plays = 1, completion = 0.8f).copy(mood = "Late", genre = "Synth")
        val similar = sampleTrack("3", liked = false, plays = 4, completion = 0.7f).copy(mood = "Warm", genre = "Rock")
        val tracks = listOf(current, liked, similar)

        assertEquals(
            listOf("1", "2"),
            discoveryTracks(
                tracks = tracks,
                filter = DiscoveryFilter.Liked,
                liked = mapOf("2" to true),
                skips = emptyMap(),
                longListens = emptyMap(),
                currentTrack = current
            ).map { it.id }
        )
        assertEquals(
            listOf("3"),
            discoveryTracks(
                tracks = tracks,
                filter = DiscoveryFilter.SimilarMood,
                liked = emptyMap(),
                skips = emptyMap(),
                longListens = emptyMap(),
                currentTrack = current
            ).map { it.id }
        )
    }

    @Test
    fun trackRadioStartsWithSeedAndPrefersSimilarMoodGenre() {
        val seed = sampleTrack("seed", liked = true, plays = 10, completion = 0.9f).copy(mood = "Late", genre = "Synth")
        val similar = sampleTrack("similar", liked = false, plays = 1, completion = 0.5f).copy(mood = "Late", genre = "Synth")
        val unrelated = sampleTrack("other", liked = true, plays = 20, completion = 0.9f).copy(mood = "Warm", genre = "Indie")

        val radio = buildTrackRadio(
            seed = seed,
            tracks = listOf(unrelated, similar, seed),
            liked = mapOf("other" to true),
            longListens = emptyMap(),
            skips = emptyMap(),
            localPlays = emptyMap()
        )

        assertEquals(listOf("seed", "similar", "other"), radio.map { it.id })
    }

    @Test
    fun autoplayQueueDoesNotRepeatSeedAndPrefersRelatedMusic() {
        val seed = sampleTrack("seed", liked = true, plays = 10, completion = 0.9f).copy(mood = "Late", genre = "Synth")
        val similar = sampleTrack("similar", liked = false, plays = 1, completion = 0.5f).copy(mood = "Late", genre = "Synth")
        val unrelated = sampleTrack("other", liked = true, plays = 20, completion = 0.9f).copy(mood = "Warm", genre = "Indie")

        val autoplay = buildAutoplayQueue(
            seed = seed,
            tracks = listOf(seed, unrelated, similar),
            liked = mapOf("other" to true),
            longListens = emptyMap(),
            skips = emptyMap(),
            localPlays = emptyMap(),
            recentlyPlayedIds = listOf("seed")
        )

        assertEquals("similar", autoplay.first().id)
        assertTrue(autoplay.none { it.id == "seed" })
    }

    @Test
    fun jarvisPromptInfersDjMode() {
        assertEquals(GuestDjMode.DeepCuts, inferGuestDjMode("give me deep cuts tonight", GuestDjMode.Flow))
        assertEquals(GuestDjMode.Chill, inferGuestDjMode("chill it out", GuestDjMode.Flow))
        assertEquals(GuestDjMode.HighEnergy, inferGuestDjMode("make it loud and high energy", GuestDjMode.Flow))
        assertEquals(GuestDjMode.Flow, inferGuestDjMode("keep going", GuestDjMode.Flow))
    }

    @Test
    fun guestDjDeepCutsPreferLowPlayRelatedTracks() {
        val seed = sampleTrack("seed", liked = true, plays = 12, completion = 0.9f).copy(mood = "Warm", genre = "Indie")
        val deepCut = sampleTrack("deep", liked = false, plays = 1, completion = 0.75f).copy(mood = "Warm", genre = "Indie")
        val obvious = sampleTrack("obvious", liked = true, plays = 30, completion = 0.95f).copy(mood = "Warm", genre = "Indie")

        val queue = buildGuestDjQueue(
            mode = GuestDjMode.DeepCuts,
            seed = seed,
            tracks = listOf(obvious, deepCut, seed),
            liked = mapOf("obvious" to true),
            longListens = emptyMap(),
            skips = emptyMap(),
            localPlays = emptyMap(),
            recentlyPlayedIds = emptyList()
        )

        assertEquals("seed", queue.first().id)
        assertTrue(queue.indexOf(deepCut) < queue.indexOf(obvious))
    }

    @Test
    fun jarvisPromptCanBuildAroundNamedArtist() {
        val seed = sampleTrack("seed", liked = true, plays = 12, completion = 0.9f).copy(artist = "Glass Harbor", mood = "Late", genre = "Synth")
        val artistTrack = sampleTrack("artist", liked = false, plays = 1, completion = 0.65f).copy(artist = "Glass Harbor", mood = "Late", genre = "Synth")
        val other = sampleTrack("other", liked = true, plays = 30, completion = 0.95f).copy(artist = "Other Artist", mood = "Warm", genre = "Indie")

        val queue = buildJarvisDjQueue(
            prompt = "more Glass Harbor and keep it late",
            mode = GuestDjMode.ArtistFocus,
            seed = seed,
            tracks = listOf(other, artistTrack, seed),
            liked = mapOf("other" to true),
            longListens = emptyMap(),
            skips = emptyMap(),
            localPlays = emptyMap(),
            recentlyPlayedIds = emptyList()
        )

        assertEquals("seed", queue.first().id)
        assertTrue(queue.indexOf(artistTrack) < queue.indexOf(other))
    }

    @Test
    fun vibeSearchBuildsEmotionPlaylists() {
        val calm = sampleTrack("calm", liked = false, plays = 1, completion = 0.6f).copy(mood = "Calm", genre = "Ambient")
        val loud = sampleTrack("loud", liked = true, plays = 20, completion = 0.95f).copy(mood = "Loud", genre = "Rock")
        val mixes = buildVibeMixes(
            tracks = listOf(loud, calm),
            query = "sad",
            liked = mapOf("loud" to true),
            longListens = emptyMap(),
            localPlays = emptyMap()
        )

        assertEquals("Sad Vibe", mixes.first().name)
        assertEquals("calm", mixes.first().tracks.first().id)
    }

    @Test
    fun vibeSearchMatchesActivityAliases() {
        val workout = sampleTrack("workout", liked = false, plays = 1, completion = 0.6f).copy(mood = "Drive", genre = "Electronic")
        val quiet = sampleTrack("quiet", liked = true, plays = 20, completion = 0.95f).copy(mood = "Calm", genre = "Ambient")
        val mixes = buildVibeMixes(
            tracks = listOf(quiet, workout),
            query = "gym",
            liked = mapOf("quiet" to true),
            longListens = emptyMap(),
            localPlays = emptyMap()
        )

        assertEquals("Workout Vibe", mixes.first().name)
        assertEquals("workout", mixes.first().tracks.first().id)
    }

    @Test
    fun customVibeSearchFallsBackToMetadata() {
        val rainy = sampleTrack("rain", liked = false, plays = 1, completion = 0.6f).copy(title = "Rain Window", mood = "Warm", genre = "Indie")
        val other = sampleTrack("other", liked = true, plays = 20, completion = 0.95f).copy(title = "Sun Run", mood = "Drive", genre = "Rock")
        val mixes = buildVibeMixes(
            tracks = listOf(other, rainy),
            query = "window",
            liked = mapOf("other" to true),
            longListens = emptyMap(),
            localPlays = emptyMap()
        )

        assertEquals("Window Vibe", mixes.first().name)
        assertEquals("rain", mixes.first().tracks.first().id)
    }

    @Test
    fun storageRoundTripKeepsSignals() {
        val ints = mapOf("a" to 1, "b" to 4)
        val booleans = mapOf("a" to true, "b" to false)

        assertEquals(ints, ints.toStorageString().toIntMap())
        assertEquals(booleans, booleans.toStorageString().toBooleanMap())
    }

    @Test
    fun cachedLibraryRoundTripKeepsArtworkUrls() {
        val track = sampleTrack("cached", liked = true, plays = 4, completion = 0.9f)
            .copy(imageUrl = "https://music.example/Items/album/Images/Primary")
        val playlist = JellyfinPlaylist(
            id = "playlist",
            name = "Road Mix",
            childCount = 12,
            imageUrl = "https://music.example/Items/playlist/Images/Primary"
        )

        assertEquals(listOf(track), listOf(track).toTrackCacheString().toTrackList())
        assertEquals(listOf(playlist), listOf(playlist).toPlaylistCacheString().toPlaylistList())
    }

    @Test
    fun imageTagDetectionRequiresPrimaryImage() {
        val withPrimary = JSONObject("""{"ImageTags":{"Primary":"abc"}}""")
        val withoutPrimary = JSONObject("""{"ImageTags":{"Backdrop":"abc"}}""")

        assertTrue(withPrimary.hasPrimaryImage())
        assertEquals(false, withoutPrimary.hasPrimaryImage())
    }

    @Test
    fun enumPreferenceParserFallsBackOnMissingOrInvalidValues() {
        assertEquals(ThemeMode.Dark, "Dark".enumValueOrDefault(ThemeMode.System))
        assertEquals(ThemeMode.System, "Missing".enumValueOrDefault(ThemeMode.System))
        assertEquals(ThemeMode.System, null.enumValueOrDefault(ThemeMode.System))
    }

    @Test
    fun waveformBytesMapToBoundedVisualizerBands() {
        val waveform = byteArrayOf(128.toByte(), 255.toByte(), 128.toByte(), 0, 128.toByte(), 220.toByte())
        val bands = waveform.toVisualizerBands(bandCount = 3)

        assertEquals(3, bands.size)
        assertTrue(bands.all { it in 0.06f..1f })
        assertTrue(bands.any { it > 0.06f })
    }

    @Test
    fun syntheticVisualizerBandsAreStableForTrack() {
        val track = sampleTrack("visual", liked = true, plays = 8, completion = 0.8f)

        assertEquals(syntheticVisualizerBands(track), syntheticVisualizerBands(track))
        assertEquals(28, syntheticVisualizerBands(track).size)
    }

    @Test
    fun connectionCardHidesAfterLibraryLoads() {
        val track = sampleTrack("connected", liked = false, plays = 1, completion = 0.8f)
        val base = JellyMixState(
            serverUrl = "https://www.badgerflix.win",
            username = "user",
            token = "token",
            userId = "user-id",
            tracks = listOf(track),
            currentTrack = track,
            jellyfinPlaylists = emptyList(),
            selectedPlaylistTracks = emptyList(),
            liked = emptyMap(),
            skips = emptyMap(),
            longListens = emptyMap(),
            localPlays = emptyMap(),
            recentTrackIds = emptyList()
        )

        assertTrue(base.shouldShowConnectionCard)
        assertEquals(false, base.isConnected)
        assertEquals(false, base.copy(libraryLoaded = true).shouldShowConnectionCard)
        assertTrue(base.copy(libraryLoaded = true).isConnected)
    }

    @Test
    fun normalizeServerUrlAddsSchemeAndRejectsInvalidSchemes() {
        assertEquals("http://192.168.1.25:8096", normalizeServerUrl("192.168.1.25:8096/"))
        assertEquals("https://music.local/jellyfin", normalizeServerUrl("https://music.local/jellyfin/"))
        assertEquals(null, normalizeServerUrl("ftp://music.local"))
        assertEquals(null, normalizeServerUrl(""))
    }

    @Test
    fun nextQueueIndexStopsAtEndUnlessRepeatIsEnabled() {
        assertEquals(1, nextQueueIndex(currentIndex = 0, queueSize = 3, repeatEnabled = false))
        assertEquals(2, nextQueueIndex(currentIndex = 2, queueSize = 3, repeatEnabled = false))
        assertEquals(0, nextQueueIndex(currentIndex = 2, queueSize = 3, repeatEnabled = true))
        assertEquals(0, nextQueueIndex(currentIndex = 0, queueSize = 1, repeatEnabled = true))
    }

    @Test
    fun isQueueEndStopsSingleTrackAndLastTrackWithoutRepeat() {
        assertTrue(isQueueEnd(currentIndex = 0, queueSize = 1, repeatEnabled = false))
        assertTrue(isQueueEnd(currentIndex = 2, queueSize = 3, repeatEnabled = false))
        assertEquals(false, isQueueEnd(currentIndex = 1, queueSize = 3, repeatEnabled = false))
        assertEquals(false, isQueueEnd(currentIndex = 2, queueSize = 3, repeatEnabled = true))
    }

    private fun sampleTrack(id: String, liked: Boolean, plays: Int, completion: Float): Track =
        Track(
            id = id,
            title = "Track $id",
            artist = "Artist",
            album = "Album",
            genre = "Indie",
            mood = "Warm",
            durationSec = 200,
            plays = plays,
            completion = completion,
            skipped = 0,
            liked = liked
        )
}
