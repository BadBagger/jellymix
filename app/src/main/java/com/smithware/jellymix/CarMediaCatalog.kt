package com.smithware.jellymix

internal const val CAR_ROOT_ID = "car:root"
internal const val CAR_CURATED_ID = "car:curated"
internal const val CAR_VIBES_ID = "car:vibes"
internal const val CAR_LIBRARY_ID = "car:library"
internal const val CAR_JARVIS_ID = "car:jarvis"
internal const val CAR_TRACK_PREFIX = "car:track:"
internal const val CAR_MIX_PREFIX = "car:mix:"
internal const val CAR_VIBE_PREFIX = "car:vibe:"

internal data class CarBrowseEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val playable: Boolean
)

internal fun carTrackId(trackId: String): String = "$CAR_TRACK_PREFIX$trackId"

internal fun carMixId(name: String): String = "$CAR_MIX_PREFIX${name.toCarSlug()}"

internal fun carVibeId(name: String): String = "$CAR_VIBE_PREFIX${name.toCarSlug()}"

internal fun String.fromCarTrackId(): String? =
    takeIf { startsWith(CAR_TRACK_PREFIX) }?.removePrefix(CAR_TRACK_PREFIX)

internal fun buildCarBrowseEntries(
    parentId: String,
    tracks: List<Track>,
    liked: Map<String, Boolean> = emptyMap(),
    longListens: Map<String, Int> = emptyMap(),
    skips: Map<String, Int> = emptyMap(),
    localPlays: Map<String, Int> = emptyMap(),
    recentlyPlayedIds: List<String> = emptyList(),
    djMode: GuestDjMode = GuestDjMode.Flow,
    seed: Track = tracks.firstOrNull() ?: sampleTracks.first()
): List<CarBrowseEntry> {
    val library = tracks.ifEmpty { sampleTracks }
    val ranked = library.rankedForCar(liked, longListens, skips, localPlays)
    val recentTracks = recentlyPlayedIds.mapNotNull { id -> library.firstOrNull { it.id == id } }
    val mixes = buildMixes(ranked, liked, longListens, skips, localPlays, recentTracks)
    val vibes = buildVibeMixes(library, "", liked, longListens, localPlays)
    val jarvisQueue = buildGuestDjQueue(djMode, seed, library, liked, longListens, skips, localPlays, recentlyPlayedIds)

    return when {
        parentId == CAR_ROOT_ID -> listOf(
            CarBrowseEntry(CAR_CURATED_ID, "Curated for you", "Weekly Discovery, Heavy Rotation, and radio mixes", playable = false),
            CarBrowseEntry(CAR_VIBES_ID, "Vibes", "Emotion and activity queues", playable = false),
            CarBrowseEntry(CAR_JARVIS_ID, "Jarvis DJ", "A hands-free queue shaped by your listening signals", playable = false),
            CarBrowseEntry(CAR_LIBRARY_ID, "Library tracks", "${library.size} songs from Jellyfin", playable = false)
        )
        parentId == CAR_CURATED_ID -> mixes.map { mix ->
            CarBrowseEntry(carMixId(mix.name), mix.name, "${mix.tracks.size} tracks - ${mix.reason}", playable = false)
        }
        parentId == CAR_VIBES_ID -> vibes.map { vibe ->
            CarBrowseEntry(carVibeId(vibe.name), vibe.name, "${vibe.tracks.size} tracks - ${vibe.reason}", playable = false)
        }
        parentId == CAR_JARVIS_ID -> jarvisQueue.toCarTrackEntries()
        parentId == CAR_LIBRARY_ID -> ranked.take(100).toCarTrackEntries()
        parentId.startsWith(CAR_MIX_PREFIX) -> mixes.firstOrNull { carMixId(it.name) == parentId }?.tracks.orEmpty().toCarTrackEntries()
        parentId.startsWith(CAR_VIBE_PREFIX) -> vibes.firstOrNull { carVibeId(it.name) == parentId }?.tracks.orEmpty().toCarTrackEntries()
        else -> emptyList()
    }
}

internal fun queueForCarMediaId(
    mediaId: String,
    tracks: List<Track>,
    liked: Map<String, Boolean> = emptyMap(),
    longListens: Map<String, Int> = emptyMap(),
    skips: Map<String, Int> = emptyMap(),
    localPlays: Map<String, Int> = emptyMap(),
    recentlyPlayedIds: List<String> = emptyList(),
    djMode: GuestDjMode = GuestDjMode.Flow,
    seed: Track = tracks.firstOrNull() ?: sampleTracks.first()
): List<Track> {
    val library = tracks.ifEmpty { sampleTracks }
    val directTrack = mediaId.fromCarTrackId()?.let { id -> library.firstOrNull { it.id == id } }
    if (directTrack != null) return buildTrackRadio(directTrack, library, liked, longListens, skips, localPlays)

    val ranked = library.rankedForCar(liked, longListens, skips, localPlays)
    val recentTracks = recentlyPlayedIds.mapNotNull { id -> library.firstOrNull { it.id == id } }
    val mixes = buildMixes(ranked, liked, longListens, skips, localPlays, recentTracks)
    val vibes = buildVibeMixes(library, "", liked, longListens, localPlays)
    return mixes.firstOrNull { carMixId(it.name) == mediaId }?.tracks
        ?: vibes.firstOrNull { carVibeId(it.name) == mediaId }?.tracks
        ?: buildGuestDjQueue(djMode, seed, library, liked, longListens, skips, localPlays, recentlyPlayedIds)
}

private fun List<Track>.rankedForCar(
    liked: Map<String, Boolean>,
    longListens: Map<String, Int>,
    skips: Map<String, Int>,
    localPlays: Map<String, Int>
): List<Track> =
    sortedByDescending { track ->
        recommendationScore(
            track = track,
            liked = liked[track.id] == true || track.liked,
            longListens = longListens[track.id] ?: 0,
            skips = skips[track.id] ?: track.skipped,
            localPlays = localPlays[track.id] ?: 0
        )
    }

private fun List<Track>.toCarTrackEntries(): List<CarBrowseEntry> =
    map { track ->
        CarBrowseEntry(
            id = carTrackId(track.id),
            title = track.title,
            subtitle = "${track.artist} - ${track.album}",
            playable = true
        )
    }

private fun String.toCarSlug(): String =
    lowercase().filter { it.isLetterOrDigit() }
