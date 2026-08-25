# Taki

**Your music, without distractions.**

Taki is a focused Android music player that makes a personal music library feel simple to use. Stream from [Navidrome](https://www.navidrome.org/) or another OpenSubsonic/Subsonic-compatible server, keep music available offline, and stay close to your collection without server-management clutter.

> **Beta:** Taki is preparing its first public beta. There is no official public APK yet, and downloads from the original Ultrasonic project are not Taki releases.

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" alt="Taki Home" width="30%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" alt="Taki Library" width="30%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" alt="Taki Now Playing" width="30%" />
</p>

## Why Taki?

Taki keeps everyday listening direct: connect a library, find music, press play, and download what you want offline. Its interface is intentionally dark, calm, and music-first.

## Features

- Stream albums, artists, playlists, songs, genres, and liked music
- Download music for offline playback
- Search across your library and connect multiple compatible servers
- Integrate with Android media controls and Android Auto
- Use a focused interface without server administration features

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

Use the [issue tracker](https://github.com/churipakinti/taki-android/issues) and choose the bug report form. Do not post credentials, private server URLs, or sensitive logs. Security and privacy concerns should follow [SECURITY.md](SECURITY.md).

## Contributing

Contributions are welcome when they fit Taki's focused direction. Read the [contribution guide](docs/CONTRIBUTING.md) before opening a pull request.

## Credits and License

Taki is free and open-source software distributed under the [GNU General Public License v3](LICENSE). It is based on [Ultrasonic](https://gitlab.com/ultrasonic/ultrasonic); Ultrasonic contributors are credited as the original project and are not responsible for Taki-specific changes, releases, support, or defects. See [NOTICE.md](NOTICE.md) for full attribution.
