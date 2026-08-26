# Changelog

Notable changes to Taki are documented here. Detailed historical development notes remain in [`CHANGES.md`](CHANGES.md).

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
