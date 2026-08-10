# Taki for Android

**Your music, without distractions.**

Taki is a focused Android player for personal and shared music collections.
Listen online or download your music for offline playback, without letting the
technology get in the way.

Taki works with [Navidrome](https://www.navidrome.org/) and other servers
compatible with the [Subsonic](http://www.subsonic.org/pages/api.jsp) and
[OpenSubsonic](https://opensubsonic.netlify.app/) APIs.

> Taki is currently in beta. It is not yet published on Google Play or F-Droid;
> builds linked to the original Ultrasonic project are not Taki releases.

## Features

- Stream personal and shared music collections
- Download music for offline playback
- Connect to multiple compatible servers
- Browse playlists, albums, artists, songs, genres, and liked songs
- Search across your library
- Dark, distraction-free interface
- Android Auto and media-session integration

## Development

The project requires a recent Android Studio installation and the Android SDK.
To build the debug application from the command line:

```shell
./gradlew :ultrasonic:assembleDebug
```

The Gradle module and Java/Kotlin package still use the legacy `ultrasonic` and
`org.moire.ultrasonic` identifiers for compatibility. They do not represent the
public product name.

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) before proposing a change.

The source repository is hosted at
[github.com/churipakinti/taki-android](https://github.com/churipakinti/taki-android).

## Issues

Report bugs and request features in the
[Taki issue tracker](https://github.com/churipakinti/taki-android/issues).

## License and credits

Taki is free and open-source software distributed under the
[GNU General Public License v3](LICENSE). See [NOTICE](NOTICE.md) for the Taki
project notice and upstream attribution.

Taki is based on [Ultrasonic](https://gitlab.com/ultrasonic/ultrasonic).
Ultrasonic and its contributors are credited as the original project; they do
not maintain or endorse Taki.
