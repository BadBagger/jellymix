package com.smithware.jellymix

import com.smithware.jellymix.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import kotlin.math.absoluteValue

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
            listOf("Weekly Discovery", "Heavy Rotation", "Long Listen Mix", "Quick Shuffle", "Rediscover", "Liked Radio", "Library Radio", "Loud Flow"),
            mixes.map { it.name }
        )
        assertEquals("2", mixes.first { it.name == "Long Listen Mix" }.tracks.first().id)
    }

    @Test
    fun generatedMixesAreLongerAndArtistDiverse() {
        val tracks = (0 until 48).map { index ->
            sampleTrack("mix-$index", liked = index % 4 == 0, plays = 20 - (index % 10), completion = 0.9f - (index % 5) * 0.04f)
                .copy(
                    artist = "Artist ${index % 8}",
                    album = "Album ${index % 12}",
                    genre = if (index % 2 == 0) "Rock" else "Indie",
                    mood = if (index % 3 == 0) "Drive" else "Warm"
                )
        }

        val mixes = buildMixes(
            rankedTracks = tracks,
            liked = tracks.associate { it.id to it.liked },
            longListens = emptyMap(),
            skips = emptyMap(),
            localPlays = emptyMap(),
            daySeed = "2026-07-25"
        )
        val quickShuffle = mixes.first { it.name == "Quick Shuffle" }
        val heavyRotation = mixes.first { it.name == "Heavy Rotation" }

        assertTrue(quickShuffle.tracks.size >= 30)
        assertTrue(heavyRotation.tracks.size >= 30)
        assertTrue(quickShuffle.tracks.take(8).zipWithNext().none { (left, right) -> left.artist == right.artist })
    }

    @Test
    fun generatedMixesHaveDistinctOpenersWhenLibraryAllowsIt() {
        val tracks = (0 until 96).map { index ->
            sampleTrack("distinct-$index", liked = index % 5 == 0, plays = 30 - (index % 15), completion = 0.62f + (index % 7) * 0.04f)
                .copy(
                    artist = "Artist ${index % 16}",
                    album = "Album ${index % 24}",
                    genre = listOf("Rock", "Indie", "Synth", "Dance")[index % 4],
                    mood = listOf("Warm", "Drive", "Late", "Bright")[index % 4],
                    durationSec = 160 + index
                )
        }

        val mixes = buildMixes(
            rankedTracks = tracks,
            liked = tracks.associate { it.id to it.liked },
            longListens = tracks.filterIndexed { index, _ -> index % 9 == 0 }.associate { it.id to 2 },
            skips = emptyMap(),
            localPlays = tracks.associate { it.id to (it.plays % 4) },
            daySeed = "2026-07-31"
        )

        val openerIds = mixes.mapNotNull { it.tracks.firstOrNull()?.id }
        assertTrue(openerIds.toSet().size >= 6)
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
    fun continuationQueueFallsBackToFullLibraryWhenStagedTracksOnlyContainSeed() {
        val seed = sampleTrack("seed", liked = true, plays = 10, completion = 0.9f).copy(mood = "Late", genre = "Synth")
        val next = sampleTrack("next", liked = false, plays = 1, completion = 0.5f).copy(mood = "Warm", genre = "Indie")

        val continuation = buildContinuationQueue(
            seed = seed,
            tracks = listOf(seed),
            fallbackTracks = listOf(seed, next),
            liked = emptyMap(),
            longListens = emptyMap(),
            skips = emptyMap(),
            localPlays = emptyMap(),
            recentlyPlayedIds = listOf(seed.id)
        )

        assertEquals(listOf("next"), continuation.map { it.id })
    }

    @Test
    fun continuationQueueNeverReturnsEmptyEvenForSingleTrackLibraries() {
        val seed = sampleTrack("seed", liked = true, plays = 10, completion = 0.9f)

        val continuation = buildContinuationQueue(
            seed = seed,
            tracks = listOf(seed),
            fallbackTracks = listOf(seed),
            liked = emptyMap(),
            longListens = emptyMap(),
            skips = emptyMap(),
            localPlays = emptyMap(),
            recentlyPlayedIds = listOf(seed.id)
        )

        assertEquals(listOf("seed"), continuation.map { it.id })
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
    fun guestDjDiscoveryRotatesArtists() {
        val tracks = (0 until 30).map { index ->
            sampleTrack("dj-$index", liked = index % 5 == 0, plays = index % 6, completion = 0.8f)
                .copy(
                    artist = if (index < 10) "Stacked Artist" else "Artist ${index % 7}",
                    mood = "Drive",
                    genre = "Rock"
                )
        }
        val queue = buildGuestDjQueue(
            mode = GuestDjMode.Discovery,
            seed = tracks.first(),
            tracks = tracks,
            liked = emptyMap(),
            longListens = emptyMap(),
            skips = emptyMap(),
            localPlays = emptyMap(),
            recentlyPlayedIds = emptyList()
        )

        assertTrue(queue.size >= 25)
        assertTrue(queue.take(8).zipWithNext().none { (left, right) -> left.artist == right.artist })
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
            localPlays = emptyMap(),
            minQualifying = 1
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
            localPlays = emptyMap(),
            minQualifying = 1
        )

        assertEquals("Workout Vibe", mixes.first().name)
        assertEquals("workout", mixes.first().tracks.first().id)
    }

    @Test
    fun carBrowseRootExposesDiscoverySurfaces() {
        val root = buildCarBrowseEntries(
            parentId = CAR_ROOT_ID,
            tracks = listOf(sampleTrack("one", liked = true, plays = 4, completion = 0.8f))
        )

        assertEquals(listOf(CAR_CURATED_ID, CAR_VIBES_ID, CAR_JARVIS_ID, CAR_LIBRARY_ID), root.map { it.id })
        assertTrue(root.none { it.playable })
    }

    @Test
    fun carBrowseVibesAndJarvisReturnPlayableQueues() {
        val calm = sampleTrack("calm", liked = false, plays = 1, completion = 0.6f).copy(mood = "Calm", genre = "Ambient")
        val calmTwo = sampleTrack("calm-2", liked = false, plays = 1, completion = 0.6f).copy(artist = "Quiet Two", mood = "Calm", genre = "Ambient")
        val calmThree = sampleTrack("calm-3", liked = false, plays = 1, completion = 0.6f).copy(artist = "Quiet Three", mood = "Calm", genre = "Ambient")
        val loud = sampleTrack("loud", liked = true, plays = 20, completion = 0.95f).copy(mood = "Loud", genre = "Rock")
        val library = listOf(loud, calm, calmTwo, calmThree)

        val vibes = buildCarBrowseEntries(
            parentId = CAR_VIBES_ID,
            tracks = library,
            liked = mapOf("loud" to true)
        )
        val chillTracks = buildCarBrowseEntries(
            parentId = vibes.first { it.title == "Chill Vibe" }.id,
            tracks = library,
            liked = mapOf("loud" to true)
        )
        val jarvisTracks = buildCarBrowseEntries(
            parentId = CAR_JARVIS_ID,
            tracks = library,
            seed = calm,
            djMode = GuestDjMode.Chill
        )

        assertTrue(chillTracks.isNotEmpty())
        assertTrue(chillTracks.all { it.playable })
        assertTrue(jarvisTracks.isNotEmpty())
        assertTrue(jarvisTracks.all { it.id.startsWith(CAR_TRACK_PREFIX) })
    }

    @Test
    fun carQueueForTrackStartsRadioFromSelectedSong() {
        val seed = sampleTrack("seed", liked = true, plays = 10, completion = 0.9f).copy(mood = "Late", genre = "Synth")
        val similar = sampleTrack("similar", liked = false, plays = 1, completion = 0.5f).copy(mood = "Late", genre = "Synth")

        val queue = queueForCarMediaId(
            mediaId = carTrackId(seed.id),
            tracks = listOf(similar, seed)
        )

        assertEquals(listOf("seed", "similar"), queue.map { it.id })
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
    fun vibeRegionsUseAudioFeaturesInsteadOfPopularityWinner() {
        val popular = sampleTrack("popular", liked = true, plays = 100, completion = 0.99f).copy(artist = "Popular", genre = "Rock", mood = "Loud")
        val chill = sampleTrack("chill", liked = false, plays = 1, completion = 0.5f).copy(artist = "Chill", genre = "Ambient", mood = "Calm")
        val hype = sampleTrack("hype", liked = false, plays = 1, completion = 0.5f).copy(artist = "Hype", genre = "Electronic", mood = "Drive")
        val angry = sampleTrack("angry", liked = false, plays = 1, completion = 0.5f).copy(artist = "Angry", genre = "Metal", mood = "Loud")
        val features = mapOf(
            popular.id to TrackAudioFeatures(110f, 0.55f, 0.55f, 0.55f, 0.8f, 0.8f, 0.5f),
            chill.id to TrackAudioFeatures(86f, 0.25f, 0.34f, 0.3f, 0.52f, 0.3f, 0.7f),
            hype.id to TrackAudioFeatures(136f, 0.86f, 0.82f, 0.52f, 0.72f, 0.7f, 0.8f),
            angry.id to TrackAudioFeatures(128f, 0.88f, 0.32f, 0.84f, 0.18f, 0.8f, 0.55f)
        )

        val mixes = buildVibeMixes(
            tracks = listOf(popular, chill, hype, angry),
            query = "",
            liked = mapOf(popular.id to true),
            longListens = emptyMap(),
            localPlays = emptyMap(),
            features = features,
            minQualifying = 1,
            daySeed = "2026-07-25"
        )

        assertEquals("chill", mixes.first { it.name == "Chill Vibe" }.tracks.first().id)
        assertEquals("hype", mixes.first { it.name == "Hype Vibe" }.tracks.first().id)
        assertEquals("angry", mixes.first { it.name == "Angry Vibe" }.tracks.first().id)
        assertNotEquals(
            mixes.first { it.name == "Chill Vibe" }.tracks.first().id,
            mixes.first { it.name == "Hype Vibe" }.tracks.first().id
        )
    }

    @Test
    fun vibeWithTooSmallRegionSurfacesNotEnoughTracks() {
        val onlyHype = sampleTrack("hype", liked = true, plays = 30, completion = 0.95f).copy(genre = "Electronic", mood = "Drive")
        val features = mapOf(onlyHype.id to TrackAudioFeatures(136f, 0.9f, 0.8f, 0.52f, 0.7f, 0.6f, 0.8f))

        val mixes = buildVibeMixes(
            tracks = listOf(onlyHype),
            query = "hype",
            liked = mapOf(onlyHype.id to true),
            longListens = emptyMap(),
            localPlays = emptyMap(),
            features = features,
            minQualifying = 3
        )

        assertEquals("Hype Vibe", mixes.first().name)
        assertTrue(mixes.first().tracks.isEmpty())
        assertTrue(mixes.first().note!!.contains("Need at least 3"))
    }

    @Test
    fun generatedMixesRespectDiversityAndDailySeed() {
        val tracks = (0 until 160).map { index ->
            sampleTrack("diverse-$index", liked = index % 7 == 0, plays = 30 - (index % 20), completion = 0.65f + (index % 5) * 0.06f)
                .copy(
                    artist = "Artist ${index % 80}",
                    album = "Album ${index % 90}",
                    genre = if (index % 3 == 0) "Electronic" else if (index % 3 == 1) "Rock" else "Indie",
                    mood = if (index % 4 == 0) "Drive" else if (index % 4 == 1) "Loud" else "Warm",
                    durationSec = 180 + index
                )
        }
        val features = tracks.associate { it.id to inferAudioFeatures(it) }

        val mixes = buildMixes(
            rankedTracks = tracks,
            liked = tracks.associate { it.id to it.liked },
            longListens = tracks.associate { it.id to if (it.id.endsWith("4")) 2 else 0 },
            skips = emptyMap(),
            localPlays = tracks.associate { it.id to (it.id.hashCode().absoluteValue % 4) },
            features = features,
            daySeed = "2026-07-25"
        )
        val usage = mixes.flatMap { it.tracks }.groupingBy { it.id }.eachCount()
        val quickToday = mixes.first { it.name == "Quick Shuffle" }.tracks.map { it.id }
        val quickTomorrow = buildMixes(
            rankedTracks = tracks,
            liked = tracks.associate { it.id to it.liked },
            longListens = emptyMap(),
            skips = emptyMap(),
            localPlays = emptyMap(),
            features = features,
            daySeed = "2026-07-26"
        ).first { it.name == "Quick Shuffle" }.tracks.map { it.id }

        assertTrue(usage.values.all { it <= 2 })
        mixes.forEach { mix ->
            assertTrue(mix.tracks.groupingBy { it.artist }.eachCount().values.all { it <= 2 })
            assertTrue(mix.tracks.groupingBy { it.album }.eachCount().values.all { it <= 2 })
            assertTrue(mix.tracks.windowed(6, 1, partialWindows = true).all { window -> window.map { it.artist }.size == window.map { it.artist }.toSet().size })
        }
        assertNotEquals(quickToday.take(10), quickTomorrow.take(10))
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
    fun oldBottomTabsMigrateToMixesTab() {
        assertEquals(Tab.Mixes, "Vibe".toTabOrDefault(Tab.Home))
        assertEquals(Tab.Mixes, "Playlists".toTabOrDefault(Tab.Home))
        assertEquals(Tab.Discover, "Discover".toTabOrDefault(Tab.Home))
        assertEquals(Tab.Home, "Unknown".toTabOrDefault(Tab.Home))
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
    fun dedupNormalizerStripsPunctuationAndNonVariantParentheticals() {
        assertEquals("slipped away", normalizeDedupTitle("Slipped Away (Album Version)!"))
        assertEquals("who knows", normalizeDedupTitle("Who Knows -"))
    }

    @Test
    fun dedupNormalizerKeepsRealVariantSuffixesDistinct() {
        assertEquals("slipped away live", normalizeDedupTitle("Slipped Away (Live)"))
        assertEquals("slipped away remix", normalizeDedupTitle("Slipped Away (Remix)"))
        assertEquals("slipped away acoustic", normalizeDedupTitle("Slipped Away (Acoustic)"))
        assertEquals("slipped away instrumental", normalizeDedupTitle("Slipped Away (Instrumental)"))
        assertEquals("slipped away demo", normalizeDedupTitle("Slipped Away (Demo)"))
    }

    @Test
    fun dedupKeepsHighestBitrateAndRetainsAlternates() {
        val low = sampleTrack("low", liked = false, plays = 1, completion = 0.4f)
            .copy(title = "Slipped Away (Album Version)", artist = "Avril Lavigne", durationSec = 214, bitrate = 128000)
        val high = low.copy(id = "high", title = "slipped away", durationSec = 216, bitrate = 320000)
        val live = low.copy(id = "live", title = "Slipped Away (Live)", durationSec = 214, bitrate = 128000)

        val deduped = deduplicateTracks(listOf(low, high, live))

        assertEquals(2, deduped.size)
        val canonical = deduped.first { it.title == "slipped away" }
        assertEquals("high", canonical.id)
        assertEquals(listOf("low"), canonical.alternates.map { it.id })
        assertTrue(deduped.any { it.id == "live" })
    }

    @Test
    fun metadataCleanupSeparatesInternalTagsAndSubtitleOmitsUnknowns() {
        val metadata = cleanTrackMetadata(
            title = "MID - Nobody's Home",
            artist = "Unknown Artist",
            album = "Under My Skin",
            genres = listOf("Loud", "Rock", "Drive"),
            fallbackPath = "/music/nobodys-home.flac"
        )
        val track = sampleTrack("metadata", liked = false, plays = 1, completion = 0.1f)
            .copy(title = metadata.title, artist = metadata.artist, album = metadata.album, genre = metadata.genre, tags = metadata.tags, filename = metadata.filename)

        assertEquals("Nobody's Home", metadata.title)
        assertEquals("Rock", metadata.genre)
        assertEquals(setOf("Loud", "Drive"), metadata.tags)
        assertEquals("nobodys-home • Under My Skin • Rock", track.subtitle().text)
    }

    @Test
    fun fftAnalysisProducesSmoothedSignals() {
        val engine = VisualizerAnalysisEngine(bandCount = 32)
        val fft = ByteArray(128) { index ->
            when {
                index % 8 == 2 -> 95
                index % 11 == 3 -> (-80).toByte()
                else -> 0
            }
        }

        val frame = engine.analyzeVisualizerFft(fft, samplingRateMilliHz = 44_100_000, nowMs = 1_000)

        assertEquals(32, frame.bands.size)
        assertTrue(frame.live)
        assertTrue(frame.bands.all { it in 0.04f..1f })
        assertTrue(frame.rms > 0f)
        assertTrue(frame.spectralCentroid in 0f..1f)
    }

    @Test
    fun ambientAnalysisIsNonLiveAndBounded() {
        val frame = VisualizerAnalysisEngine(bandCount = 48).ambient(sampleTrack("ambient", liked = false, plays = 1, completion = 0.5f), nowMs = 2_000)

        assertEquals(48, frame.bands.size)
        assertEquals(false, frame.live)
        assertTrue(frame.bands.all { it in 0.06f..0.42f })
        assertTrue(frame.bass >= 0f)
    }

    @Test
    fun visualizerFrameBusPublishesLatestFrameWithoutUiStateCoupling() {
        val bus = VisualizerFrameBus()
        val frame = VisualizerAnalysisEngine(bandCount = 16).ambient(sampleTrack("bus", liked = true, plays = 2, completion = 0.7f), nowMs = 3_000)

        bus.publish(frame)

        assertEquals(frame, bus.latest())
        assertEquals(16, bus.latest().bands.size)
    }

    @Test
    fun feedbackSafetyKeepsHeadlessRenderLuminanceBounded() {
        val safety = FeedbackSafetyMonitor()
        val engine = VisualizerAnalysisEngine(bandCount = 48)
        repeat(300) { index ->
            val fft = ByteArray(256) { bin ->
                when {
                    bin % 17 == 0 -> 96
                    bin % 29 == 0 -> (-74).toByte()
                    else -> 0
                }
            }
            val frame = engine.analyzeVisualizerFft(fft, samplingRateMilliHz = 44_100_000, nowMs = 1_000L + index * 16L)
            val decay = (0.965f - frame.treble * 0.035f - frame.rms * 0.012f).coerceIn(0.92f, 0.97f)
            val injected = (frame.rms * 0.018f + if (frame.beat) 0.015f else 0f).coerceIn(0f, 0.055f)
            safety.advance(decay, injected)
        }

        assertTrue(safety.meanLuminance in 0.05f..0.75f)
    }

    @Test
    fun feedbackSafetyResetsRunawayLuminance() {
        val safety = FeedbackSafetyMonitor()
        safety.forceMeanForTest(0.9f)

        repeat(31) {
            safety.advance(decay = 0.97f, injectedEnergy = 0.08f)
        }

        assertTrue(safety.resetCount > 0)
        assertTrue(safety.meanLuminance < 0.1f)
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
    fun previousQueueIndexStopsAtStartUnlessRepeatIsEnabled() {
        assertEquals(0, previousQueueIndex(currentIndex = 0, queueSize = 3, repeatEnabled = false))
        assertEquals(1, previousQueueIndex(currentIndex = 2, queueSize = 3, repeatEnabled = false))
        assertEquals(2, previousQueueIndex(currentIndex = 0, queueSize = 3, repeatEnabled = true))
        assertEquals(0, previousQueueIndex(currentIndex = 0, queueSize = 1, repeatEnabled = true))
    }

    @Test
    fun isQueueEndStopsSingleTrackAndLastTrackWithoutRepeat() {
        assertTrue(isQueueEnd(currentIndex = 0, queueSize = 1, repeatEnabled = false))
        assertTrue(isQueueEnd(currentIndex = 2, queueSize = 3, repeatEnabled = false))
        assertEquals(false, isQueueEnd(currentIndex = 1, queueSize = 3, repeatEnabled = false))
        assertEquals(false, isQueueEnd(currentIndex = 2, queueSize = 3, repeatEnabled = true))
    }

    @Test
    fun derivedDataCacheReusesHomeDataForPlaybackOnlyChanges() {
        val tracks = (0 until 40).map { index ->
            sampleTrack("cache-$index", liked = index % 7 == 0, plays = index % 5, completion = 0.7f)
                .copy(artist = "Artist ${index % 8}", album = "Album ${index % 12}")
        }
        val state = JellyMixState(
            serverUrl = "https://music.example",
            username = "user",
            tracks = tracks,
            currentTrack = tracks.first(),
            jellyfinPlaylists = emptyList(),
            selectedPlaylistTracks = emptyList(),
            liked = tracks.associate { it.id to it.liked },
            skips = emptyMap(),
            longListens = emptyMap(),
            localPlays = emptyMap(),
            recentTrackIds = tracks.take(8).map { it.id }
        )
        val cache = JellyMixDerivedDataCache()

        val first = cache.home(state)
        val second = cache.home(state.copy(isPlaying = true, status = "Playing"))

        assertTrue(first === second)
    }

    @Test
    fun topArtistsLineSummarizesActualPlaylistSpread() {
        val tracks = listOf(
            sampleTrack("a", liked = false, plays = 1, completion = 0.7f).copy(artist = "Gold Panda"),
            sampleTrack("b", liked = false, plays = 1, completion = 0.7f).copy(artist = "Gold Panda"),
            sampleTrack("c", liked = false, plays = 1, completion = 0.7f).copy(artist = "Linkin Park"),
            sampleTrack("d", liked = false, plays = 1, completion = 0.7f).copy(artist = "The Cure")
        )

        assertEquals("Gold Panda • Linkin Park • The Cure", topArtistsLine(tracks))
        assertEquals("Mixed from your library", topArtistsLine(emptyList()))
    }

    @Test
    fun trendPlaylistsCreatePersonalizedDiscoveryWithoutPrompt() {
        val tracks = (0 until 72).map { index ->
            sampleTrack("trend-$index", liked = index % 8 == 0, plays = index % 12, completion = 0.45f + (index % 10) * 0.05f)
                .copy(
                    artist = "Artist ${index % 12}",
                    album = "Album ${index % 18}",
                    genre = listOf("Rock", "Indie", "Synth", "Ambient")[index % 4],
                    mood = listOf("Drive", "Warm", "Late", "Calm")[index % 4],
                    durationSec = 150 + index
                )
        }

        val playlists = buildTrendPlaylists(
            seed = tracks.first(),
            tracks = tracks,
            liked = tracks.associate { it.id to it.liked },
            longListens = tracks.filterIndexed { index, _ -> index % 9 == 0 }.associate { it.id to 2 },
            skips = mapOf("trend-4" to 3),
            localPlays = tracks.associate { it.id to (it.plays % 5) },
            recentlyPlayedIds = tracks.take(10).map { it.id }
        )

        assertEquals(
            listOf("Your Flow", "Fresh For You", "Deep Cuts", "Comfort Zone", "Energy Lift", "After Hours"),
            playlists.map { it.name }
        )
        assertTrue(playlists.all { it.tracks.isNotEmpty() })
        assertTrue(playlists.flatMap { it.tracks.take(3) }.map { it.id }.toSet().size > 8)
    }

    @Test
    fun cachedLibraryPayloadFailureReturnsEmptyLoadInsteadOfCrashing() {
        val cached = cachedLibraryFromPayloads(
            trackPayload = "{not valid jellymix cache",
            playlistPayload = "{also bad",
            rawTrackCount = 29_000
        )

        assertTrue(cached.tracks.isEmpty())
        assertTrue(cached.playlists.isEmpty())
        assertEquals(29_000, cached.rawTrackCount)
    }

    @Test
    fun cachedLibraryPayloadDedupsTracksForStartup() {
        val lowBitrate = sampleTrack("low", liked = false, plays = 0, completion = 0.4f)
            .copy(title = "Same Song", artist = "Same Artist", durationSec = 200, bitrate = 128_000)
        val highBitrate = lowBitrate.copy(id = "high", bitrate = 320_000)

        val cached = cachedLibraryFromPayloads(
            trackPayload = listOf(lowBitrate, highBitrate).toTrackCacheString(),
            playlistPayload = null,
            rawTrackCount = 2
        )

        assertEquals(listOf("high"), cached.tracks.map { it.id })
        assertEquals(listOf("low"), cached.tracks.first().alternates.map { it.id })
    }

    @Test
    fun compactStartupTracksUsesCurrentQueueRecentAndTopWindow() {
        val tracks = (0 until 500).map { index -> sampleTrack("startup-$index", liked = false, plays = 0, completion = 0.1f) }
        val compact = compactStartupTracksForSavedLibrary(
            tracks = tracks,
            current = tracks[400],
            queue = listOf(tracks[401], tracks[402], tracks[0]),
            recentIds = listOf("startup-450", "startup-451", "missing"),
            limit = 8
        )

        assertEquals(
            listOf("startup-400", "startup-401", "startup-402", "startup-0", "startup-450", "startup-451", "startup-1", "startup-2"),
            compact.map { it.id }
        )
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
