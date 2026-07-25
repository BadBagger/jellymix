package com.smithware.jellymix

import com.smithware.jellymix.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
            listOf("Heavy Rotation", "Long Listens", "Rediscover", "Liked Radio", "Indie Radio", "Warm Flow"),
            mixes.map { it.name }
        )
        assertEquals("2", mixes.first { it.name == "Long Listens" }.tracks.first().id)
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
    fun storageRoundTripKeepsSignals() {
        val ints = mapOf("a" to 1, "b" to 4)
        val booleans = mapOf("a" to true, "b" to false)

        assertEquals(ints, ints.toStorageString().toIntMap())
        assertEquals(booleans, booleans.toStorageString().toBooleanMap())
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
