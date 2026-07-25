# Agent Handoff

Read `PROJECT_CONTEXT.md` before making changes.

## Current Rule

This repo has a buildable Compose MVP shell. Keep it buildable after each change.

## First Task

Verify and harden real Jellyfin behavior:

- Keep `com.smithware.jellymix` as the package/application id.
- Test auth, library fetch, and playback against a real Jellyfin server.
- Add background media controls after core playback is proven on a real device/server.
- Preserve optimistic Jellyfin favorite sync, queue, completion advance, endless autoplay, shuffle, repeat, track radio, search, actionable discovery filters, curated playlist shelves, Now Playing page, clear-session, recent-history, local play count, custom theme options, in-app visualizer behavior, and generated mix behavior when changing playback.
- Preserve server URL normalization and pre-auth reachability checks when changing connection code.
- Preserve persistent local listening history for recommendation ranking.
- Keep `:app:testDebugUnitTest :app:assembleDebug` passing after changes.
- Keep secrets out of the repo.
- Use the known local JDK/SDK paths from `README.md` for Gradle verification.

## Boundaries

- Do not publish or sign a release until a debug build installs and launches.
- Do not add cloud services or tracking.
- Jellyfin credentials and listening history should stay local to the device.
- Preserve unrelated user changes if this folder becomes a git repo later.
