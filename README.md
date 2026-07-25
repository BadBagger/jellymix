# JellyMix

JellyMix is a native Android Jellyfin music client concept focused on discovery and personalized listening.

## Current status

This workspace now has a buildable JellyMix Android MVP shell.

What exists:

- Gradle wrapper and Android project configuration copied from a previously verified Compose app.
- App identity is `JellyMix` with package/application id `com.smithware.jellymix`.
- Launcher icon, theme name, internet permission, README, and ignore rules.
- A native Compose UI with onboarding connection inputs, music-first Home, Discover, Library, Playlists, Now Playing, and a persistent player bar.
- Seeded music data and local interaction signals for likes, skips, and long-listen playlist ranking.
- Jellyfin username/password authentication using the Jellyfin REST API.
- Jellyfin server URL normalization and pre-auth server reachability check.
- Jellyfin music-library track fetch after connection.
- Jellyfin playlist metadata fetch after connection.
- Tap a Jellyfin playlist to load and play tracks from that playlist.
- Jellyfin primary image URLs rendered with Coil for tracks and playlists.
- Like/unlike persists locally and attempts to sync Jellyfin favorite state for server tracks.
- Framework `MediaPlayer` streaming from Jellyfin audio stream URLs.
- Local persistence for server URL, username, auth token, user id, likes, skips, and long-listen counts.
- Search across songs, artists, albums, genres, and moods.
- Actionable discovery filters for long listens, liked tracks, low skips, similar mood, and rediscovery.
- Empty states for filtered track lists.
- Generated mixes now include Heavy Rotation, Long Listens, Rediscover, Liked Radio, genre radio, and mood flow.
- Curated playlist shelves now include Weekly Discovery and Quick Shuffle alongside personalized radio-style mixes.
- Mix and playlist queue start actions.
- Explicit Play and Shuffle controls exist on curated mixes, generated playlist cards, and the full library.
- Queue-aware skip, queue position display, shuffle toggle, and repeat-queue toggle.
- Playback completion advances through the active queue, then starts an autoplay radio queue instead of stopping or looping the same song.
- Home now leads with album art, current playback, curated mix shelves, recently played, and heavy rotation instead of connection/search/status panels.
- Album art is loaded from Jellyfin primary images across rows, mix shelves, Now Playing, and the mini player, with generated covers only as fallback.
- Tapping the mini player opens a Now Playing page with larger art, visualizer, controls, song details, queue context, and more mixes.
- Start radio from the current track using mood, genre, artist, and local listening signals.
- Demo playback can be paused before a Jellyfin server is connected.
- Up Next section with clear-queue action.
- Clear session action removes the saved Jellyfin token and returns to demo discovery mode.
- Recently played section backed by local play history.
- Local play counts persist and influence recommendation ranking.
- Custom theme options with System/Light/Dark mode and Jelly, Ember, Ocean, Grape, and Mono accent palettes persisted locally.
- In-app music visualizer in Now Playing and in the mini player, with live Jellyfin audio capture when permission is granted and animated preview bands for demo/unavailable capture.
- Login is onboarding-style: the Jellyfin connection panel hides after a real library load, and returns only when no session exists or the library did not load.
- The mini player is compact so library/discovery content remains usable while music is playing.
- JVM tests for recommendation ranking, local play boosts, generated mixes, autoplay queue behavior, search filtering, discovery filters, track radio ordering, server URL normalization, queue advancement/end detection, local signal storage, theme preference parsing, and visualizer band generation.

Known remaining blocker:

- Real Jellyfin server/device verification has not been run yet in this workspace.

## Product target

Build a native Android Jellyfin music client that feels closer to Plexamp and YouTube Music than a plain file browser:

- Server connection for Jellyfin base URL, username, and token/password flow.
- Music-first home screen with recently played, heavy rotation, similar artists, albums to rediscover, and quick mixes.
- Tailored playlists based on local listening signals: likes, skips, repeat plays, completion percentage, and longest-listened tracks.
- Library views for artists, albums, tracks, genres, favorites, and playlists.
- Now-playing screen with queue, like, skip, repeat, shuffle, song radio, autoplay context, visualizer, and Jellyfin stream metadata.
- Local-first preference/history/theme cache so personalization remains private on-device.

## Team lanes

Use this split for group work:

- Android shell: fix package/source layout, restore buildability, and keep Compose/Material 3 structure.
- Jellyfin integration: implement auth, library fetch, image URLs, stream URLs, and error states.
- Recommendation engine: rank tracks and artists from likes, skips, completion, replay count, recency, genre, and mood.
- UX/design: build the Home, Discover, Library, Playlists, and Player tabs with compact-phone checks.
- QA/release: run Gradle checks, install the APK, verify launch, then prepare a signed release only after the debug app works.

## Build verification

Use the local Android toolchain already configured on this machine:

```powershell
$env:JAVA_HOME="C:\Users\KyleB\Documents\Codex\2026-07-04\build-a-native-android-app-using\.local-jdk\jdk-17.0.19+10"
$env:ANDROID_HOME="C:\Users\KyleB\Documents\Codex\2026-07-04\build-a-native-android-app-using\.android-sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:PATH="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

Verified on July 24, 2026 with:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Debug APK:

- `JellyMix-debug.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

Latest copied debug APK size: 22,659,918 bytes.

## Test release

- Source repo: `https://github.com/BadBagger/jellymix`
- Debug test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.0-visualizer-test`
- Login/declutter test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.1-login-declutter`
- Home/Now Playing test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.2-home-now-playing`
- DevHub release that adds JellyMix to the catalog: `https://github.com/BadBagger/softsmith-devhub/releases/tag/v2.1.88-jellymix`
