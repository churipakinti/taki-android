<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" alt="Taki logo" width="128" />
</p>

# Taki

**Your music, without distractions.**

Taki is a focused Android music player for [Navidrome](https://www.navidrome.org/) and other OpenSubsonic/Subsonic-compatible servers. Its goal is deliberately simple: open your library, find music, press play, and keep a few things available offline.

Taki is intentionally **music-only**. It leaves out chat, social feeds, server-side share links, podcasts, and day-to-day server administration so the player can stay centered on focused listening.

> **Beta:** Taki is currently in public beta. Official builds are available through this repository's [GitHub Releases](https://github.com/churipakinti/taki-android/releases).

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" alt="Taki Home" width="18%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" alt="Taki Library" width="18%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" alt="Taki Album" width="18%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png" alt="Taki Now Playing" width="18%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png" alt="Taki Search" width="18%" />
</p>

## What it does

- Stream albums, artists, playlists, songs, genres, and liked music
- Download music for offline playback
- Search across your library and connect multiple compatible servers
- Integrate with Android media controls and Android Auto
- Share what you are listening to as an image card (generated on your phone; nothing is uploaded)
- Keep everyday listening separate from server administration

During normal online playback, Taki reports Now Playing and qualifying completed plays to the connected server using the Subsonic/OpenSubsonic scrobble API. Server-side forwarding to services such as Last.fm, when configured, is controlled by the server. See [Default behavior](docs/DEFAULT_BEHAVIOR.md) for Taki's other automatic behaviors and defaults.

## What it deliberately leaves out

Taki is built around simplification rather than feature accumulation. Chat, social feeds, server-side share links, podcasts, and similar extras are intentionally outside the listening experience. The aim is a small, direct player for people who mainly want to listen to their own music.

## Requirements

- Android 8.0 (API 26) or later
- A Navidrome or compatible OpenSubsonic/Subsonic server
- Taki currently uses a portrait-only phone interface

Compatibility varies between server implementations and API versions. Navidrome is the primary compatibility target for the beta.

## Getting started

Taki is a player, not a music store: it needs a server that already holds your music. On first launch, enter your server's address (for example `https://music.example.com` or `http://192.168.1.10:4533`), your username and your password, then tap **Connect**. Plain `http://` addresses work, which is common on a home network; use `https://` whenever the server is reachable from the internet.

## Install / Download

Official beta builds are available from [GitHub Releases](https://github.com/churipakinti/taki-android/releases). Download the APK from the most recent Taki prerelease, or build from source using the instructions below.

## Build from source

Install a recent Android Studio and Android SDK, clone the repository, then run:

```shell
./gradlew :ultrasonic:assembleDebug
```

The APK is generated under `ultrasonic/build/outputs/apk/debug/`. The module and Kotlin package retain the legacy `ultrasonic` and `org.moire.ultrasonic` names for compatibility.

## Reporting bugs

Use the [issue tracker](https://github.com/churipakinti/taki-android/issues) for reproducible problems. Please include the Taki version (the "Your library" menu, then About), your Android version, the server type and version (for example Navidrome 0.5x), and the steps that trigger it. You usually do not need to attach logs; if a log is requested, remove your server address, username and any token or `u=`, `p=`, `t=`, `s=` query values first. Do not post credentials, private server URLs, or sensitive logs. Security and privacy concerns should follow [SECURITY.md](SECURITY.md).

## Contributing

Issues and contributions are welcome. For larger changes, opening an issue first helps keep the scope aligned with Taki's deliberately simple, music-only direction. See [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for repository guidelines.

## Project status

Taki is independently maintained as a personal open-source project. It is not offered as a commercial service and is not a company-backed product. This describes the project itself and does not add restrictions beyond the GPLv3 license.

## Credits and License

Taki started as a fork of [Ultrasonic](https://gitlab.com/ultrasonic/ultrasonic) and is free and open-source software distributed under the [GNU General Public License v3](LICENSE). Copyright in upstream portions remains with the respective Ultrasonic contributors. They are not responsible for Taki-specific changes, releases, support, or defects. See [NOTICE.md](NOTICE.md) for full attribution.
