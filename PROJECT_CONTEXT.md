# JellyMix Project Context

## Goal

Create a native Android app for Jellyfin music playback with strong music discovery and tailored playlists based on what the listener likes and listens to the longest.

## Current State

The workspace was shifted back from handoff into implementation and now has a buildable Compose MVP shell.

Done:

- Gradle wrapper and Android configuration are present.
- App id/package in Gradle is `com.smithware.jellymix`.
- Manifest label is `JellyMix`.
- Internet permission is present.
- A JellyMix Compose theme file exists under `app/src/main/java/com/smithware/jellymix/ui/theme/Theme.kt`.
- `MainActivity` is now under `com.smithware.jellymix`.
- Old ManagerMeet starter Kotlin files were removed.
- The first app shell includes connection inputs, Home, Discover, Library, Playlists, player controls, seeded music data, and local recommendation ranking.
- Jellyfin username/password auth, music-library fetch, playlist metadata fetch, playlist drill-in track fetch, primary image URLs, stream URL generation, and framework `MediaPlayer` playback paths are implemented.
- Server URL normalization and `/System/Info/Public` reachability check run before authentication.
- Like/unlike is optimistic locally and attempts Jellyfin favorite sync for connected non-demo tracks.
- Server URL, username, auth token, user id, likes, skips, and long-listen counts persist locally in SharedPreferences.
- Search filters tracks by song, artist, album, genre, and mood.
- Discovery filter chips are actionable: Long listens, Liked, Low skips, Similar mood, and Rediscover each alter the Discover track list.
- Generated mixes include Weekly Discovery, Heavy Rotation, Long Listen Mix, Quick Shuffle, Rediscover, Liked Radio, genre radio, and mood flow.
- Mixes, generated playlist cards, and the full library expose explicit Play and Shuffle controls.
- Mixes and loaded playlists can start a queue. Skip is queue-aware. Player controls include shuffle and repeat queue toggles.
- Playback completion advances through the active queue, marks long listens, and starts an autoplay radio queue when the queue ends instead of stopping or looping the same song.
- Track radio builds a queue from the current song using mood, genre, artist, and local listening signals. Demo playback now pauses correctly.
- Up Next and clear-queue controls exist. Clear session removes the saved Jellyfin token and returns the app to demo mode.
- Home is now a music-first surface with current album art, curated mix shelves, stations/discovery, recently played, and heavy rotation. Connection/search/status panels no longer consume the first Home viewport.
- Jellyfin primary image artwork is used across Home rails, mix shelves, Now Playing, track rows, and the mini player with generated gradient covers only as fallback.
- Tapping the mini player opens a Now Playing page with larger album art, visualizer, transport controls, song details, queue context, autoplay status, and more mixes.
- Recently played and local play counts persist on-device and influence ranking.
- Custom theme options exist in the connection/settings card: System, Light, and Dark mode plus Jelly, Ember, Ocean, Grape, and Mono accent palettes. The selections persist locally.
- An in-app visualizer exists in Now Playing and in the mini player. It uses Android audio-session waveform capture for real Jellyfin playback after `RECORD_AUDIO` permission, and falls back to animated preview bands for demo mode or unavailable capture.
- The Jellyfin connection card is onboarding-style: it hides after the library actually loads and reappears only when there is no saved session or the saved session fails to load the library.
- The mini player was reduced to a compact control surface after phone screenshots showed the previous player consumed too much screen space.
- Recommendation, local play boost, generated mix, autoplay queue, search, discovery filter, track radio ordering, server URL normalization, queue advancement/end detection, local signal storage, theme preference parsing, and visualizer band tests exist in `app/src/test/java/com/smithware/jellymix/RecommendationTest.kt`.
- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` passed on July 25, 2026 after the Home album-art/Now Playing tap-target pass.

Not done:

- No emulator/device install verification has been run yet.
- No real Jellyfin server login/playback test has been run yet.
- Live visualizer capture has not been verified on a real device because no device/emulator is attached.
- Background media session controls and lock-screen controls are not implemented yet.

Published:

- Source repo: `https://github.com/BadBagger/jellymix`
- Debug test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.0-visualizer-test`
- DevHub catalog release: `https://github.com/BadBagger/softsmith-devhub/releases/tag/v2.1.88-jellymix`
- Login/declutter release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.1-login-declutter`
- Server reachability from the Windows workspace was confirmed for `http://www.badgerflix.win/System/Info/Public` and `https://www.badgerflix.win/System/Info/Public`; both returned BadgerFlix `10.11.11`. Phone-side library loading still needs verification.

## Immediate Next Blocker

Verify and harden real Jellyfin behavior:

- Install the debug APK on a device/emulator.
- Connect to a real Jellyfin server.
- Confirm auth, library load, stream URL playback, and clear error messages.
- Add Android media session/background controls after foreground playback is reliable.

## Recommended Architecture

- UI: Jetpack Compose + Material 3.
- State: ViewModel with immutable UI state.
- Storage: DataStore for server/settings, Room for listening history and cached library metadata.
- Network: Jellyfin REST API client, keeping credentials local.
- Playback: Android media playback surface after API/library proof works.
- Personalization: local ranking engine using listening events.

## MVP Definition

The first useful MVP should:

- Connect to a Jellyfin server.
- Fetch music libraries, artists, albums, and tracks.
- Show Home, Discover, Library, Playlists, and Player tabs.
- Play a selected track through a Jellyfin stream URL.
- Record local listening events.
- Generate at least three tailored playlists:
  - Heavy Rotation
  - Long Listens
  - Rediscover
  - Liked Radio
  - Genre Radio
  - Mood Flow

## Verification Standard

Before calling the MVP complete:

- `.\gradlew.bat :app:assembleDebug` passes. Completed July 24, 2026.
- APK installs on an Android device or emulator.
- App launches without crashing.
- User can connect to Jellyfin or see a useful connection error.
- A sample or real Jellyfin music library renders in the UI.
- At least one Jellyfin track can be played or the exact playback blocker is documented.
