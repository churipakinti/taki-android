# Taki v0.1.0-beta.1 — release notes draft

Taki is a focused Android music player for Navidrome and OpenSubsonic/Subsonic-compatible servers, built around simple browsing, playback, and offline listening from a personal music library.

## Build identification

- **App version:** `0.1.0-beta`
- **versionCode:** `131`
- **Final release commit:** added after the signed candidate is frozen
- **Signing certificate SHA-256:** added after final signing
- **APK SHA-256:** added after final signing

Do not publish this document with placeholder build-identification fields unresolved.

## What this beta includes

- Home, Library, and Search navigation focused on everyday listening.
- Streaming from Navidrome and compatible OpenSubsonic/Subsonic servers.
- Persistent offline downloads managed from Library.
- Search results that can start playback directly without leaving Search.
- Song radio, artist radio, and Daily Mix.
- Playlist browsing, playback, creation, editing, downloading, and removal.
- Sleep Timer with 15, 30, 45, and 60 minute presets plus End of Track.
- Android media controls and Android Auto integration.
- Clearer connection, playback, and download failure feedback.

## Known limitations

- Some localized strings are still incomplete and may fall back to English.
- Compatibility can vary between OpenSubsonic/Subsonic server implementations; Navidrome is the primary compatibility target for this beta.
- Android Auto presents a simpler browsing experience than the phone interface.

## Reporting problems

Use [GitHub Issues](https://github.com/churipakinti/taki-android/issues). Include the expected behavior, what happened instead, steps to reproduce, Android/device information, and logs or screenshots when useful. Remove credentials, private server URLs, tokens, and personal library details before posting.

## Project status

Taki is independently maintained as a personal open-source project. It is not offered as a commercial service and is not a company-backed product.

## License and origin

Taki started as a fork of [Ultrasonic](https://gitlab.com/ultrasonic/ultrasonic) and is distributed under GNU GPLv3. Copyright in upstream portions remains with the respective Ultrasonic contributors.
