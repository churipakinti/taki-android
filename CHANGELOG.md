# Changelog

Notable changes to Taki are documented here.

## [Unreleased]

## 0.1.0-beta.2

### Added

- Like or unlike a whole album from the album screen; the state syncs through Navidrome/OpenSubsonic to other clients.
- A **Liked Albums** entry in Library.
- Tap an artist name — on an album or in Now Playing — to open that artist.
- A **more actions** (⋮) menu on the album screen: go to artist, play next, add to queue, start radio.
- **Share card:** turn Now Playing into an image card to share. It is generated on your phone; nothing is uploaded.
- Software decoding for hi-res FLAC (24-bit, high sample rates), so those files play on phones that cannot decode them natively.
- Android Auto: tapping a song starts it inside its list, favorites play as one list, and search works from the car screen.
- Lyrics and Up Next screens redesigned for reading and for reordering the queue.

### Changed

- A consistent visual system across Home, Library, Search, the album/artist/collection screens and the player: calmer surfaces, artwork-first layouts, uniform spacing, and secondary actions standardized as compact icons.
- Library's header now matches Home's, with library switching moved into its menu.
- Playlist and album cards are flatter (no drop shadows, no tilted covers).
- The Now Playing artwork keeps its swipe gestures; the artist line beside it is now a link.
- Nearly every screen, including Home, Library, Search, the player, Settings and the Sleep Timer, was rebuilt in Jetpack Compose with the same look and behavior as before.
- The release APK is larger (about 30 MB) because it now carries the FLAC decoder for four CPU architectures.
- Some legacy Ultrasonic features that Taki does not use (chat, podcasts, bookmarks, server-side share links, the home-screen widget) were removed.
- Other apps and Android Auto can only start Taki's own library items; they can no longer hand it a playback source or star/rate tracks directly (security hardening).

### Fixed

- Playback now recovers on its own after a brief network change (Wi-Fi ↔ mobile, short drop) instead of stopping and needing a manual restart.
- Swipe gestures on the player (skip track, seek) now work.
- The like control in Now Playing is reachable with TalkBack.
- Opening Library no longer crashes in some configurations.
- Swiping Taki out of Recents no longer stops the music that is playing.
- The Albums list no longer crashes when your library changes while it is loading.
- Your saved queue and equalizer preset are kept safely between launches and when you switch servers.
- Playback recovers from short server and network errors, no longer stalls falsely during phone calls or assistant interruptions, and switching to Offline no longer cuts off the song that is playing.
- Downloads are no longer saved under the wrong file extension or resumed into a different audio format.
- Server credentials are no longer written to logs.

### Known limitations

- On a Wi-Fi network that stays connected but stops carrying data, playback may take a long time to recover (#35, not yet verified on real hardware).
- After Taki stops, Android may keep a dimmed Taki card in Quick Settings with a Play button. Play on it works (#37).
- Taki uses a portrait-only phone interface.

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
