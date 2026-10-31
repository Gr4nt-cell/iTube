# iTube

A native Android YouTube client with a Liquid Glass interface — no ads, no account required.

## Features

- **Everything you watch**: trending (music, gaming, live, movies, podcasts), search with suggestions and filters, channels (videos, shorts, live, playlists), playlists, comments with replies, chapters, captions, related videos and autoplay
- **Ad-free**: streams come straight from YouTube's servers, so there are no video or banner ads
- **SponsorBlock**: auto-skips sponsors, intros, outros, self-promo and more — choose categories in Settings; segments show on the seek bar
- **Return YouTube Dislike** counts
- **Background play**: keeps audio playing with the screen off or in other apps, with notification and lock-screen controls (play/pause, next, previous)
- **Pop-up player**: floating, draggable, pinch-to-resize video window over other apps
- **Picture-in-picture**
- **Downloads**: video (up to 1080p MP4, higher in WebM on Android 10+) and audio-only (M4A/Opus), saved to `Downloads/iTube`, playable offline
- **Library**: subscriptions feed (local, no login), watch history with resume, Watch later, custom playlists
- **Shorts section**: Shorts stay out of regular discovery feeds, search results and autoplay queues; explicit Shorts links open the dedicated Shorts tab
- **Liquid Glass UI**: refractive glass bars, buttons, sheets and player controls with depth shadows, in light and dark mode

## Build with GitHub

Every push to `main` runs **Actions → Build APK**, which:

1. builds a signed release APK,
2. uploads it as the `iTube-apk` artifact, and
3. publishes it on the repository's **Releases** page (pushing a tag like `v1.0.0` makes a full release).

Download the APK from Releases on your phone and install it.

### Signing

The repo ships with a signing key in `keystore/` so every build installs over the previous one. If the repository is public, replace it with your own key stored in GitHub secrets: `ITUBE_KEYSTORE_BASE64` (base64 of the .jks), `ITUBE_KEYSTORE_PASSWORD`, `ITUBE_KEY_ALIAS`, `ITUBE_KEY_PASSWORD`.

## Tech

Kotlin, Jetpack Compose, Media3 ExoPlayer, NewPipe Extractor, Haze (Liquid Glass), Coil, WorkManager.

## License

GPL-3.0 (includes code adapted from NewPipe).
