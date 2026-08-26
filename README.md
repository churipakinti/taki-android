# Taki

**A small Android player for personal music libraries.**

Taki is a personal Android music player I built for my own Navidrome library. I wanted something simple for browsing my music, playing albums, and keeping a few things offline without bringing server administration into the player.

I’m sharing it because it may be useful to other people running their own music libraries. It is still a small personal project, and the first public beta may have rough edges.

Taki works with [Navidrome](https://www.navidrome.org/) and other OpenSubsonic/Subsonic-compatible servers.

> **Beta:** Taki is preparing its first public beta. There is no official public APK yet, and downloads from the original Ultrasonic project are not Taki releases.

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" alt="Taki Home" width="18%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" alt="Taki Library" width="18%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" alt="Taki Album" width="18%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png" alt="Taki Now Playing" width="18%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png" alt="Taki Search" width="18%" />
</p>

## Why I made it

Most of the music I listen to lives on my own server. I wanted the Android side to feel like a straightforward music player rather than a server-management tool: open the app, browse the library, press play, and download a few things for offline listening.

## What it does

- Stream albums, artists, playlists, songs, genres, and liked music
- Download music for offline playback
- Search across your library and connect multiple compatible servers
- Integrate with Android media controls and Android Auto
- Keep server administration out of the everyday listening interface

## Requirements

- Android 8.0 (API 26) or later
- A Navidrome or compatible OpenSubsonic/Subsonic server

Compatibility varies between server implementations and API versions. Navidrome is the primary compatibility target for the beta.

## Install / Download

The first public beta has not been published yet. When it is available, official builds will be attached to this repository's [GitHub Releases](https://github.com/churipakinti/taki-android/releases). Until then, build from source if you want to test Taki.

## Build from source

Install a recent Android Studio and Android SDK, clone the repository, then run:

```shell
./gradlew :ultrasonic:assembleDebug
```

The APK is generated under `ultrasonic/build/outputs/apk/debug/`. The module and Kotlin package retain the legacy `ultrasonic` and `org.moire.ultrasonic` names for compatibility.

## Reporting bugs

If you try Taki and run into a problem, feel free to open an issue using the [issue tracker](https://github.com/churipakinti/taki-android/issues). Do not post credentials, private server URLs, or sensitive logs. Security and privacy concerns should follow [SECURITY.md](SECURITY.md).

## Contributing

Issues and contributions are welcome. If you want to work on something larger than a small fix, opening an issue first is probably the easiest way to check whether it fits the project. See [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for the repository guidelines.

## Credits and License

Taki started as a fork of [Ultrasonic](https://gitlab.com/ultrasonic/ultrasonic) and is free and open-source software distributed under the [GNU General Public License v3](LICENSE). Copyright in upstream portions remains with the respective Ultrasonic contributors. They are not responsible for Taki-specific changes, releases, support, or defects. See [NOTICE.md](NOTICE.md) for full attribution.
