# JellyMix

JellyMix is a native Android Jellyfin music client concept focused on discovery and personalized listening.

## Current status

This workspace now has a buildable JellyMix Android MVP shell.

What exists:

- Gradle wrapper and Android project configuration copied from a previously verified Compose app.
- App identity is `JellyMix` with package/application id `com.smithware.jellymix`.
- Launcher icon, theme name, internet permission, README, and ignore rules.
- A native Compose UI with onboarding connection inputs, music-first Home, Vibe, Discover, Library, Playlists, Now Playing, and a persistent player bar.
- Seeded music data and local interaction signals for likes, skips, and long-listen playlist ranking.
- Jellyfin username/password authentication using the Jellyfin REST API.
- Jellyfin server URL normalization and pre-auth server reachability check.
- Jellyfin music-library track fetch after connection.
- Jellyfin playlist metadata fetch after connection.
- Tap a Jellyfin playlist to load and play tracks from that playlist.
- Jellyfin primary image URLs rendered with Coil for tracks and playlists.
- Like/unlike persists locally and attempts to sync Jellyfin favorite state for server tracks.
- Framework `MediaPlayer` streaming from Jellyfin audio stream URLs.
- Current-track prebuffering for saved sessions and paused queue changes so playback can start faster after tapping Play.
- Local persistence for server URL, username, auth token, user id, likes, skips, and long-listen counts.
- Search across songs, artists, albums, genres, and moods.
- Actionable discovery filters for long listens, liked tracks, low skips, similar mood, and rediscovery.
- Empty states for filtered track lists.
- Generated mixes now include longer Heavy Rotation, Long Listens, Rediscover, Liked Radio, genre radio, mood flow, and Quick Shuffle queues with artist rotation to avoid long one-artist blocks.
- Curated playlist shelves now include Weekly Discovery and Quick Shuffle alongside personalized radio-style mixes.
- Mix and playlist queue start actions.
- Explicit Play and Shuffle controls exist on curated mixes, generated playlist cards, and the full library.
- Queue-aware skip, queue position display, shuffle toggle, and repeat-queue toggle.
- Playback completion advances through the active queue, then starts an autoplay radio queue instead of stopping or looping the same song.
- Home now leads with album art, current playback, curated mix shelves, recently played, and heavy rotation instead of connection/search/status panels.
- Home now takes stronger inspiration from modern music apps with mood chips, a speed-dial mix grid, full-cover discovery tiles, and mixed-for-you rails.
- Album art is loaded from Jellyfin track or album primary images across rows, mix shelves, Now Playing, and the mini player. Generated covers are always painted underneath so broken/missing Jellyfin art never leaves blank slabs.
- The last loaded Jellyfin library and playlist list are cached locally so saved-session launches can show and play music immediately while Jellyfin refreshes in the background.
- Tapping the mini player opens a cleaner Now Playing page centered on a square album-art/visualizer stage, driving-friendly controls, DJ mode, queue context, autoplay preview, and more mixes. Tapping the square stage toggles between album art and the visualizer.
- Jarvis DJ adds a local conversational radio coordinator in Discover. It accepts prompts like "give me deep cuts", "chill it out", or "more from this artist", changes DJ mode, rebuilds the queue, and explains why tracks are coming next without sending listening data to a cloud service.
- Guest DJ modes include Flow, Familiar, Discovery, Deep cuts, Artist focus, High energy, and Chill. Now Playing shows the active mode plus an autoplay preview with reasons for upcoming tracks.
- Vibe tab adds emotion/activity playlist search for moods like Chill, Hype, Sad, Angry, Focus, Late Night, Happy, Nostalgic, Workout, and Rainy, plus custom metadata-based vibe searches.
- Android Auto media integration is registered with a foreground `MediaBrowserService`, `MediaSession`, and a Car App Library media-template entry point for hosts that try to launch a car-safe activity. Car surfaces can browse Curated, Vibes, Jarvis DJ, and Library queues, then control play, pause, previous, next, and autoplay continuation. The car playback service claims local music output with Android media audio-focus handling so projection devices have a real media audio route to control. The phone activity is not marked as drive-optimized; Android Auto should use the media browser or template surface rather than opening JellyMix's phone UI while driving.
- Apple CarPlay is not implemented in this Android project. It requires a separate iOS app and Apple's CarPlay entitlement.
- Start radio from the current track using mood, genre, artist, and local listening signals.
- Demo playback can be paused before a Jellyfin server is connected.
- Up Next section with clear-queue action.
- Clear session action removes the saved Jellyfin token and returns to demo discovery mode.
- Recently played section backed by local play history.
- Local play counts persist and influence recommendation ranking.
- Custom theme options with System/Light/Dark mode and Jelly, Ember, Ocean, Grape, and Mono accent palettes persisted locally.
- In-app music visualizer in Now Playing and in the mini player, with live Jellyfin audio capture when permission is granted and a richer mirrored waveform/ribbon renderer. Preview waveforms are shaped by track, genre, mood, and completion when live capture is unavailable.
- Now Playing hides the mini player while open, removes low-value song-detail clutter, and uses larger driving-friendly transport controls with a prominent play/pause button plus clearer active states for shuffle, like, and repeat.
- Android home-screen widget is included. It shows the current track, artist, Jellyfin context, and play/pause plus skip controls. The widget background opens JellyMix, but the playback buttons dispatch foreground-service commands instead of launching the app.
- Lock-screen/media notification controls are included with a media-style notification, playback `MediaSession`, public lock-screen visibility, and Previous, Play/Pause, Skip, and Stop actions routed through foreground-safe private playback commands instead of activity launches.
- Login is onboarding-style: the Jellyfin connection panel hides after a real library load, and returns only when no session exists or the library did not load.
- The mini player is compact so library/discovery content remains usable while music is playing.
- JVM tests for recommendation ranking, local play boosts, generated mixes, vibe playlist search/ranking, Jarvis DJ prompt/mode handling, Android Auto browse catalog entries and car queue selection, autoplay queue behavior, search filtering, discovery filters, track radio ordering, server URL normalization, queue advancement/end detection, local signal storage, cached library parsing, image tag detection, theme preference parsing, and visualizer band generation.

Known remaining blocker:

- Real Jellyfin server/device verification has not been run yet in this workspace.

## Product target

Build a native Android Jellyfin music client that feels closer to Plexamp and YouTube Music than a plain file browser:

- Server connection for Jellyfin base URL, username, and token/password flow.
- Music-first home screen with recently played, heavy rotation, similar artists, albums to rediscover, and quick mixes.
- Tailored playlists based on local listening signals: likes, skips, repeat plays, completion percentage, and longest-listened tracks.
- Library views for artists, albums, tracks, genres, favorites, and playlists.
- Now-playing screen with queue, like, skip, repeat, shuffle, song radio, autoplay context, visualizer, and Jellyfin stream metadata.
- Local-first preference/history/theme/library/Jarvis-DJ cache so personalization remains private on-device and startup is not blocked by a full Jellyfin refresh.
- Android Auto support through Android's media app APIs; CarPlay would be a separate iOS lane.

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

Verified on July 25, 2026 with:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Debug APK:

- `JellyMix-debug.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

Latest copied debug APK size: 25,635,481 bytes.

## Test release

- Source repo: `https://github.com/BadBagger/jellymix`
- Debug test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.0-visualizer-test`
- Login/declutter test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.1-login-declutter`
- Home/Now Playing test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.2-home-now-playing`
- Artwork/startup test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.3-artwork-startup`
- Jarvis DJ test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.4-jarvis-dj`
- Vibe tab test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.5-vibe-tab`
- Android Auto test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.6-android-auto`
- Driving controls test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.7-driving-controls`
- Home/widget refresh test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.8-home-widget-refresh`
- Functional widget controls test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.9-widget-controls`
- Lock-screen controls test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.10-lockscreen-controls`
- Expanded media controls test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.11-expanded-media-controls`
- Widget control fix test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.12-widget-control-fix`
- Media control service test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.13-media-control-service`
- Android Auto audio-route test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.14-android-auto-audio-route`
- Android Auto foreground-media test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.15-android-auto-foreground-media`
- Car template entry test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.16-car-template-entry`
- Now Playing visualizer stage test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.17-now-playing-visualizer-stage`
- Visualizer/controls/discovery test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.18-visualizer-controls-discovery`
- Prebuffer waveform test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.19-prebuffer-waveform`
- DevHub release that adds JellyMix to the catalog: `https://github.com/BadBagger/softsmith-devhub/releases/tag/v2.1.88-jellymix`
