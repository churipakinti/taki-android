# Contributing to Taki

Thank you for helping improve Taki. Before starting substantial work, search the
[issue tracker](https://github.com/churipakinti/taki-android/issues) and open an
issue to discuss the proposed change when appropriate.

Taki is a focused music player for people using personal or shared
Navidrome/Subsonic-compatible libraries. Contributions should keep the product
calm, approachable, music-only, and useful without requiring technical server
knowledge beyond connecting a library.

## Contributing code

1. All contributions must be compatible with the [GNU GPLv3](../LICENSE).
2. Keep each pull request focused on one problem or feature.
3. Preserve existing behavior unless the change explicitly replaces it.
4. Follow the existing Kotlin and Android conventions.
5. Include tests when the change can be tested automatically.
6. Use `Taki` for all new user-facing names and copy.

Open pull requests against the `develop` branch from a dedicated feature
branch. Do not use your own `develop` branch for feature work.

## Local checks

Format Kotlin code:

```shell
./gradlew -Pqc ktlintFormat
```

Run static analysis:

```shell
./gradlew -Pqc detekt
```

Run Android lint:

```shell
./gradlew -Pqc :ultrasonic:lintRelease
```

Build the debug application:

```shell
./gradlew :ultrasonic:assembleDebug
```

The `ultrasonic` module name and `org.moire.ultrasonic` package are retained for
compatibility with the upstream codebase. User-facing additions should use the
Taki name.

## Documentation and product identity

- Describe the application as Taki, not Ultrasonic.
- Do not publish links to Ultrasonic releases as Taki downloads.
- Keep internal legacy identifiers when renaming them would break compatibility.
- Preserve copyright, licensing, and upstream attribution notices.
- Keep supporting documents focused on the current Taki behavior rather than
  inherited features that are no longer exposed.

## Upstream attribution

Taki is based on [Ultrasonic](https://gitlab.com/ultrasonic/ultrasonic). Keep
applicable copyright notices and attribution intact when modifying upstream
files. Ultrasonic contributors are not responsible for Taki-specific changes.
