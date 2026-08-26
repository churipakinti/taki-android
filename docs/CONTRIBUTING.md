# Contributing to Taki

Contributions are welcome. Before starting substantial work, search the [issue tracker](https://github.com/churipakinti/taki-android/issues) and open an issue to discuss the proposed change when appropriate.

Taki is intentionally a simple, music-only player for personal and shared Navidrome/OpenSubsonic-compatible libraries. Its value comes from **removing unnecessary product surface**, not from accumulating features.

That means contributions should preserve a focused listening experience. Chat, social feeds, sharing systems, podcasts, and day-to-day server administration are deliberately outside the core product direction unless the project's scope changes explicitly in the future.

## Contributing code

1. All contributions must be compatible with the [GNU GPLv3](../LICENSE).
2. Keep each pull request focused on one problem or feature.
3. Preserve existing behavior unless the change explicitly replaces it.
4. Follow the existing Kotlin and Android conventions.
5. Include tests when the change can be tested automatically.
6. Use `Taki` for all new user-facing names and copy.

Open pull requests against the `develop` branch from a dedicated feature branch.

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

The `ultrasonic` module name and `org.moire.ultrasonic` package are retained for compatibility with the upstream codebase. User-facing additions should use the Taki name.

## Project identity

- Describe the application as Taki, not Ultrasonic.
- Prefer simplification over adding another settings surface or mode.
- Keep the primary experience centered on browsing and listening to music.
- Do not publish links to Ultrasonic releases as Taki downloads.
- Keep internal legacy identifiers when renaming them would break compatibility.
- Preserve copyright, licensing, and upstream attribution notices.
- Keep current documentation aligned with the behavior users can actually access in Taki.

Taki is independently maintained as a personal open-source project rather than a commercial service or company-backed product.

## Upstream attribution

Taki is based on [Ultrasonic](https://gitlab.com/ultrasonic/ultrasonic). Keep applicable copyright notices and attribution intact when modifying upstream files. Ultrasonic contributors are not responsible for Taki-specific changes.
