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
- The app shell includes connection inputs, Home, Mixes, Discover, Library, player controls, seeded music data, and local recommendation ranking.
- Jellyfin username/password auth, music-library fetch, playlist metadata fetch, playlist drill-in track fetch, primary image URLs, stream URL generation, and framework `MediaPlayer` playback paths are implemented.
- Jellyfin library ingest now requests full audio metadata, deduplicates duplicate audio items by normalized title/artist/duration, keeps the highest-bitrate item canonical, and retains lower-bitrate copies as alternates for future track details.
- Current-track prebuffering is implemented for saved sessions and paused queue changes so Jellyfin playback can start faster after tapping Play.
- Server URL normalization and `/System/Info/Public` reachability check run before authentication.
- Like/unlike is optimistic locally and attempts Jellyfin favorite sync for connected non-demo tracks.
- Server URL, username, auth token, user id, likes, skips, and long-listen counts persist locally in SharedPreferences.
- Search filters tracks by song, artist, album, genre, and mood.
- Search, discovery, generated mixes, radio queues, vibe queues, Android Auto browse queues, and cached-library reads run through the same canonical deduplication layer so duplicate copies should not resurface downstream.
- Discovery filter chips are actionable: Long listens, Liked, Low skips, Similar mood, and Rediscover each alter the Discover track list.
- Generated mixes include Weekly Discovery, Heavy Rotation, Long Listen Mix, Quick Shuffle, Rediscover, Liked Radio, Library Radio, and Loud Flow. Each mix now uses a distinct primary signal, daily seeded tie-breaking, cross-mix overlap limits, max artist/album limits, and a defined small-library relaxation path.
- Mixes, generated playlist cards, and the full library expose explicit Play and Shuffle controls.
- Mixes and loaded playlists can start a queue. Skip is queue-aware. Player controls include shuffle and repeat queue toggles.
- Playback completion advances through the active queue, marks long listens, and starts an autoplay radio queue when the queue ends instead of stopping or looping the same song.
- Playback continuation is hardened across the phone app, widget service, and Android Auto service: when a one-song or exhausted queue finishes, JellyMix builds a non-empty continuation queue from the full cached library when available, excludes the just-finished song first, and only falls back to repeating the seed when the entire available library has no other track.
- Phone playback now has an automatic near-end crossfade: a lightweight playback monitor starts the next continuation track before completion, fades the old `MediaPlayer` down and the new one up over about 2.8 seconds, and guards old-player completion so the queue does not advance twice.
- Track radio builds a queue from the current song using mood, genre, artist, and local listening signals. Demo playback now pauses correctly.
- Up Next and clear-queue controls exist. Clear session removes the saved Jellyfin token and returns the app to demo mode.
- Home is now a music-first surface with Continue Listening, a max-six speed-dial shortcut grid, recently played, and heavy rotation. Connection/search/status panels no longer consume the first Home viewport.
- Mixes is the single owner for generated mixes, vibe playlists, and the user's Jellyfin playlists. It replaces the old separate Vibe and Playlists tabs with a Mixes/Vibes/Yours segmented control.
- Jellyfin track or album primary image artwork is used across Home rails, mix shelves, Now Playing, track rows, and the mini player. Generated gradient covers are always drawn underneath real images so missing/broken Jellyfin art does not render as an empty slab.
- Track subtitles use a single `artist • album • genre` model across phone rows, Android Auto browse, notifications, and widget foreground playback. Unknown artist/album placeholders and internal labels such as Loud, Drive, Library, and Crossover are not rendered as genres.
- The last loaded Jellyfin track and playlist lists are cached locally in SharedPreferences. Saved-session launches render the cached library immediately and refresh Jellyfin in the background, so startup is not blocked before playback is usable.
- Tapping the mini player opens a cleaner Now Playing page centered on a square album-art/visualizer stage, transport controls, DJ mode, queue context, autoplay status, and more mixes. Tapping the square stage toggles between album art and the visualizer.
- Jarvis DJ is implemented as a local conversational radio coordinator. It accepts free-form prompts and quick suggestions, infers Guest DJ modes, rebuilds the queue, persists the active mode/current queue/current track, and explains upcoming track reasons without sending listening data to a cloud service.
- Guest DJ modes include Flow, Familiar, Discovery, Deep cuts, Artist focus, High energy, and Chill. Now Playing includes DJ mode controls and an autoplay preview with queue reasons.
- Vibes live inside the Mixes tab for emotion/activity playlist search. Preset vibes now qualify tracks by cached audio-feature regions for energy, BPM, centroid/brightness, dynamics, vocal presence, tempo stability, and valence proxy instead of padding every vibe from one popularity sort. Undersized vibe regions show a subtle "not enough tracks yet" state.
- Discover is now a Jarvis DJ-only surface with prompt input, Familiar/Discovery/Deep mode chips, and the latest DJ queue/results instead of a large generic track wall.
- Library owns browsing and search with Artists/Albums/Tracks/Genres segmentation plus saved discovery filters.
- Up Next is no longer in a static tab; it is reachable from the mini player queue label and Now Playing as a bottom sheet with queue counter, Clear, drag-to-reorder, swipe-to-remove, and distinct Play next/Add to queue suggestion actions.
- Android Auto media integration is implemented with a foreground platform `MediaBrowserService`, `MediaSession`, and Car App Library media-template entry point for hosts that try to launch a car-safe activity. It exposes Curated, Vibes, Jarvis DJ, and Library browse roots, playable track queues, transport controls, Jellyfin stream playback, local play-history updates, autoplay continuation when a car-started queue ends, local media output attributes, content-style browse hints, and Android media audio-focus handling. The phone activity is intentionally not marked as drive-optimized; Android Auto should use the media browser/template surface rather than opening JellyMix's phone UI while driving.
- Apple CarPlay is documented as a separate iOS app requirement because this Android app cannot directly integrate with CarPlay or request Apple's CarPlay entitlement.
- Recently played and local play counts persist on-device and influence ranking.
- Custom theme options exist in the connection/settings card: System, Light, and Dark mode plus Jelly, Ember, Ocean, Grape, and Mono accent palettes. The selections persist locally.
- A real Settings entry point is available from the main tab header gear. It opens a bottom sheet with Jellyfin reload/sign-out actions, theme/accent controls, visualizer diagnostics, and live visualizer permission access so settings remain reachable after the onboarding connection card hides.
- JellyMix now defaults to dark theme with a teal Jelly accent, uses a defined 28/20/17/15/13 typography scale, stronger section/card hierarchy, low-emphasis secondary actions, generated gradient mix covers, initials-based missing-art fallbacks, denser adaptive track rows, and larger accessible row icon targets.
- JellyMix visual affordances now use one accent model: teal for primary actions and active nav, desaturated teal tint for selected nav/chips, and red reserved for actual errors. Mix/Vibe card Play buttons are filled primary actions while Shuffle stays outlined, small-library mix notes render as neutral info rows, row/home/player like affordances consistently use hearts, phone subtitles render as one-line artist-first rows, the static JellyMix app bar was removed, and generated initials only appear on missing-art gradients with collision-aware short forms.
- Library tab layout now orders search, browse segment, compact stats, Play/Shuffle actions, and list content so the actions apply to the selected browse segment. The stats panel renders Artists, Albums, and Tracks in one compact card without reserved blank space, and the segment row scrolls horizontally so Genres does not clip.
- Discover tab now treats Jarvis DJ as separate idle/results/error/skeleton states: the default placeholder prompt is hidden from results, redundant quick chips are removed/shortened, Tune Queue is an outlined secondary action with an empty-prompt tap message, and the Jarvis header uses an audio waveform icon.
- Home recently played now collapses consecutive plays by track ID with a play count and uses taller cards so two-line titles fit. The Home now-playing card includes previous and next controls.
- Expanded Now Playing now separates primary transport controls from modifiers: previous/play-pause/next are the large first row, while shuffle/like/repeat/queue/overflow are smaller secondary controls. The queue sheet is directly reachable from this view. Song radio moved out of the competing filled button and into the overflow menu with album/artist/playlist/info placeholders. The progress control is scrubbable with a 48dp touch target plus elapsed and remaining timestamps. The album-art/visualizer stage toggle persists, and fullscreen appears only when the visualizer is showing.
- Visualizer/player performance received a jank pass: Compose no longer resumes the `GLSurfaceView` on every audio-frame update, fullscreen feedback renders fluidly against a cheaper quarter-resolution offscreen buffer, audio visualizer state updates are throttled, expensive Home/Mixes/Vibes derived lists are memoized or deferred, Home uses a lightweight speed-dial mix builder, cached library hydration runs off the main thread, cached-session Jellyfin refresh is deferred until the app is usable, Jellyfin refresh merge/cache persistence runs on `Dispatchers.IO`, and local signal maps stay sparse instead of pushing thousands of default liked/skip/play entries through Compose state.
- The Plexamp-inspired performance pass moved live visualizer frames out of global Compose state into a lightweight `VisualizerFrameBus` that the GL renderer pulls from directly, so FFT/audio-rate updates no longer invalidate the whole UI. Library browsing now precomputes search/dedup/grouping with `remember`, all artwork requests use fixed thumbnail sizes through Coil with crossfade disabled, repeated artwork gradients/subtitles/visualizer palettes are remembered instead of rebuilt during scroll/player recomposition, and the large Library Tracks and saved discovery sections render as real keyed lazy rows instead of composing every track inside one giant item. Full-library ranking, mix generation, discovery filtering, and recent-history collapse are now gated to only run on the tabs/views that actually need them, reducing startup and tab-switch CPU churn. Cached-library startup and refresh no longer parse or store the full audio-feature map in Compose state; mix/vibe ranking falls back to deterministic feature inference until a proper non-UI PCM feature store is added.
- Saved-session startup now stages the cached library when landing on Home: the UI gets a compact current/queue/recent/top-track working set first while the full cached library stays in the ViewModel and is promoted only when Library or Mixes needs it. Background refreshes on Home also update the snapshot/cache without forcing the full library into Compose immediately. Core UI model classes are annotated immutable to help Compose skip row/card recomposition when stable track, mix, playlist, and visualizer data is reused.
- Lazy lists and rails now provide stable `contentType` values for track rows, mix cards, playlist cards, library groups, rails, controls, and empty/header sections. Home speed-dial/recent/heavy-rotation slices are memoized before rendering so playback state changes do not keep rebuilding small section inputs.
- A `profile` build type exists for performance testing without release signing. It keeps package id `com.smithware.jellymix`, uses debug signing for easy install, disables debug-only Compose tooling, and sets `isProfileable = true`; build it with `.\gradlew.bat :app:assembleProfile` and install `app/build/outputs/apk/profile/app-profile.apk`.
- The Now Playing visualizer has a decoupled audio-analysis layer and a GPU feedback-tunnel renderer. Live Jellyfin playback uses Android audio-session FFT capture after `RECORD_AUDIO` permission and exposes bands, bass, mid, treble, RMS, beat, and spectral-centroid signals. Demo mode or unavailable capture falls back to a calm track-shaped ambient frame instead of freezing. The square album-art slot toggles to the visualizer, and a fullscreen visualizer overlay is available from that stage.
- The feedback-tunnel visualizer now has saturation guardrails: black framebuffer initialization, explicit 0.92-0.97 decay, bounded additive energy, final luminance clamping, normalizer flooring for quiet input, a headless 300-frame luminance regression test, auto-reset logging for runaway feedback, and a persisted visualizer diagnostics overlay showing renderer FPS, mean luminance, reset count, live/fallback state, and band values.
- Fullscreen visualizer now includes fading track metadata, previous/play-pause/next transport controls, mode cycling by horizontal swipe with a mode toast, a scrubbable progress slider, tap-to-toggle overlay behavior, and keep-screen-awake behavior only while fullscreen is active.
- Now Playing hides the mini player while open, removes the low-value song-detail card, and uses larger driving-friendly transport controls: 60dp secondary buttons, a 76dp primary play/pause button, and clear active states for shuffle, like, and repeat.
- Android home-screen widget is included. It reads the local current-track cache, shows title/artist/Jellyfin context, keeps the widget background as an app-open target, and routes play/pause plus skip taps to private foreground-service playback commands so button taps do not open `MainActivity`.
- Lock-screen/media notification controls are included. The app owns a playback `MediaSession`, posts a public media-style notification with Previous, Play/Pause, Skip, and Stop actions, requests Android 13+ notification permission when playback starts, and routes notification action buttons through foreground-safe private playback commands instead of opening `MainActivity`.
- The Jellyfin connection card is onboarding-style: it hides after the library actually loads and reappears only when there is no saved session or the saved session fails to load the library.
- The mini player is a compact expandable playback bar with title marquee, artist subtitle, queue label, previous/play-next controls only, swipe up/down to expand/dismiss, swipe left/right to skip previous/next, and a thin bottom-edge progress line. Shuffle, repeat, and like live in expanded Now Playing.
- Recommendation, local play boost, generated mix differentiation/diversity, vibe feature-region classification, tab migration, Jarvis DJ prompt/mode handling, autoplay queue, search, discovery filter, track radio ordering, server URL normalization, queue advancement/end detection, local signal storage, cached library parsing, image tag detection, theme preference parsing, visualizer band tests, audio-analysis tests, and dedup/metadata cleanup tests exist in `app/src/test/java/com/smithware/jellymix/RecommendationTest.kt`.
- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleProfile` passed on July 26, 2026 after the Plexamp-inspired visualizer/state and Library virtualization pass. Emulator launch of the profile APK reported cold start `TotalTime: 1492ms` after the row/artwork allocation pass and reduced the startup Choreographer burst from the debug build's roughly 97 skipped frames to 35 skipped frames, so phone testing should use the profile APK for performance feedback.
- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` and `.\gradlew.bat :app:assembleProfile` passed on July 26, 2026 after the playback-continuation fix.
- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` passed on July 26, 2026 after the phone crossfade playback handoff.

Not done:

- Emulator install and launch verification passed on `emulator-5554` for the debug build after the visual hierarchy pass.
- No real Jellyfin server login/playback test has been run yet.
- Live visualizer capture has not been verified on a real device/server.
- Exact PCM library scanning for BPM/RMS/centroid/dynamic range is not implemented yet; the current recommendation feature cache uses deterministic metadata-derived feature inference. Migrating playback/library scanning to a PCM-accessible engine can replace the inference source later without changing the mix/vibe selectors.
- Lock-screen notification controls are implemented for the main app playback path. Real-device behavior still needs phone-side verification with a Jellyfin stream.

Published:

- Source repo: `https://github.com/BadBagger/jellymix`
- Debug test release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.0-visualizer-test`
- DevHub catalog release: `https://github.com/BadBagger/softsmith-devhub/releases/tag/v2.1.88-jellymix`
- Login/declutter release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.1-login-declutter`
- Home/Now Playing release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.2-home-now-playing`
- Artwork/startup release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.3-artwork-startup`
- Jarvis DJ release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.4-jarvis-dj`
- Vibe tab release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.5-vibe-tab`
- Android Auto release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.6-android-auto`
- Driving controls release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.7-driving-controls`
- Home/widget refresh release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.8-home-widget-refresh`
- Functional widget controls release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.9-widget-controls`
- Lock-screen controls release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.10-lockscreen-controls`
- Expanded media controls release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.11-expanded-media-controls`
- Widget control fix release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.12-widget-control-fix`
- Media control service release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.13-media-control-service`
- Android Auto audio-route release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.14-android-auto-audio-route`
- Android Auto foreground-media release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.15-android-auto-foreground-media`
- Car template entry release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.16-car-template-entry`
- Now Playing visualizer stage release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.17-now-playing-visualizer-stage`
- Visualizer/controls/discovery release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.18-visualizer-controls-discovery`
- Prebuffer waveform release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.19-prebuffer-waveform`
- Feedback tunnel visualizer release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.20-feedback-tunnel`
- Performance jank release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.23-performance-jank`
- Plexamp performance release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.24-plexamp-performance`
- Continuation autoplay release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.25-continuation-autoplay`
- Crossfade/settings release: `https://github.com/BadBagger/jellymix/releases/tag/v0.1.26-crossfade-settings`
- Server reachability from the Windows workspace was confirmed for `http://www.badgerflix.win/System/Info/Public` and `https://www.badgerflix.win/System/Info/Public`; both returned BadgerFlix `10.11.11`. Phone-side library loading still needs verification.

## Immediate Next Blocker

Verify and harden real Jellyfin behavior:

- Install the debug APK on a device/emulator.
- Connect to a real Jellyfin server.
- Confirm auth, library load, stream URL playback, and clear error messages.
- Verify lock-screen notification controls on a real phone with a Jellyfin stream.

## Recommended Architecture

- UI: Jetpack Compose + Material 3.
- State: ViewModel with immutable UI state.
- Storage: DataStore for server/settings, Room for listening history and cached library metadata.
- Network: Jellyfin REST API client, keeping credentials local.
- Playback: Android media playback surface with Android Auto service and main-app lock-screen notification controls.
- Car integration: Android Auto uses `CarPlaybackService`; Apple CarPlay requires a separate iOS implementation and entitlement.
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
