# Changelog

Notable changes to Taki are documented here.

## [Unreleased]

### Added

- Like or unlike a whole album from the album screen; the state syncs through Navidrome/OpenSubsonic to other clients.
- A **Liked Albums** entry in Library.
- Tap an artist name — on an album or in Now Playing — to open that artist.
- A **more actions** (⋮) menu on the album screen: go to artist, play next, add to queue, start radio.

### Changed

- A consistent visual system across Home, Library, Search, the album/artist/collection screens and the player: calmer surfaces, artwork-first layouts, uniform spacing, and secondary actions standardized as compact icons.
- Library's header now matches Home's, with library switching moved into its menu.
- Playlist and album cards are flatter (no drop shadows, no tilted covers).
- The Now Playing artwork keeps its swipe gestures; the artist line beside it is now a link.

### Fixed

- Playback now recovers on its own after a brief network change (Wi-Fi ↔ mobile, short drop) instead of stopping and needing a manual restart.
- Swipe gestures on the player (skip track, seek) now work.
- The like control in Now Playing is reachable with TalkBack.
- Opening Library no longer crashes in some configurations.

## 0.1.0-beta.1

### Added

- Taki application identity and package ID.
- Home, Library, and Search navigation.
- Offline downloads managed from Library.
- Song radio, artist radio, and Daily Mix.
- Sleep Timer with timed presets and End of Track.
- Android media controls and Android Auto integration.

### Changed

- Simplified first-run connection flow.
- Simplified Settings around playback, downloads, language, advanced options, and About.
- Search playback keeps the user in Search instead of forcing Now Playing.
- Download behavior now represents persistent local availability rather than cache/pin concepts.

### Fixed

- Download retry lifecycle and terminal failure handling.
- False downloaded-state indicators after missing or removed files.
- First-connection library naming after an invalid host attempt.
- Release lint configuration for runtime locale handling on clean CI runners.

### Known limitations

- Some localized strings are incomplete and may fall back to English.
- Compatibility may vary across OpenSubsonic/Subsonic server implementations; Navidrome is the primary compatibility target for this beta.
- Taki currently uses a portrait-only phone interface.
