# Contributing to Taki

Contributions are welcome. Before starting substantial work, search the [issue tracker](https://github.com/churipakinti/taki-android/issues) and open an issue to discuss the proposed change when appropriate.

Taki is intentionally a simple, music-only player for personal and shared Navidrome/OpenSubsonic-compatible libraries. Its value comes from **removing unnecessary product surface**, not from accumulating features.

That means contributions should preserve a focused listening experience. Chat, social feeds, sharing systems, podcasts, and day-to-day server administration are deliberately outside the core product direction unless the project's scope changes explicitly in the future.

## What to work on

Taki is a personal project in public beta. The immediate priority is making the existing experience dependable across more devices and Navidrome/OpenSubsonic servers, rather than adding lots of features.

Contributions are especially helpful for:

- Playback correctness and reliability, including interruptions and audio-device changes.
- Offline downloads, caching, and safe synchronization without losing user data.
- API compatibility with Navidrome/OpenSubsonic and different server versions.
- Battery use, performance, accessibility, and everyday usability.
- Focused tests, documentation, and fixes for reproducible bugs.

For larger features or structural refactors, please open an issue first to discuss the problem and scope. A working feature alone does not guarantee that it fits Taki's direction.

## Contributing code

1. All contributions must be compatible with the [GNU GPLv3](../LICENSE).
2. Keep each pull request focused on one problem or feature.
3. Preserve existing behavior unless the change explicitly replaces it.
4. Follow the existing Kotlin and Android conventions.
5. Include tests when the change can be tested automatically.
6. Use `Taki` for all new user-facing names and copy.

Open pull requests against the `develop` branch from a dedicated feature branch.

## AI-assisted contributions

AI-assisted and AI-generated contributions are welcome. The contributor remains responsible for the changes submitted, regardless of which tools were used.

- Review and understand the code you are proposing; do not submit unexamined generated output.
- Briefly disclose meaningful AI assistance in the PR description so reviewers have useful context.
- Explain what changed, why, and what verification you actually performed. Do not claim tests or manual checks that were not run.
- Check generated code for regressions, privacy or security risks, dependencies, and license compatibility. Never include private server URLs, tokens, passwords, or personal library data in issues, prompts, test fixtures, or PRs.
- Be prepared to answer questions and revise the change after review.

The review standard is the same for AI-assisted and manually written changes: clarity, correctness, maintainability, and fit with the project.

## Pull request review

Please include in the PR description:

1. The problem being addressed (and a linked issue, if there is one).
2. A concise summary of the changes and any user-visible behavior affected.
3. Tests or checks run and their results; explicitly note anything not tested.
4. For server-related changes, relevant server and Android versions, when known.
5. Risks or migration implications, especially for playback, offline data, and existing user settings.

Small, focused PRs are easier to review. CI results help, but passing CI does not guarantee correctness or automatic acceptance. Changes are reviewed for reliability, scope, and consistency with Taki's music-first design; maintainers may request revisions or decline changes that don't align with the project.


## Local checks

For code changes, the same test, lint, and build tasks used by GitHub Actions can be run locally:

```shell
./gradlew testDebugUnitTest lint assembleDebug assembleRelease --no-daemon
```

This is the project's CI baseline; it does not replace targeted tests or manual checks for changes involving playback, downloads, or specific server behavior. Documentation-only changes generally do not require a full Android build.

Code-quality plugins are enabled with `-Pqc` in `gradle_scripts/code_quality.gradle`. If you use those optional tools, the existing commands are:

```shell
./gradlew -Pqc ktlintFormat
./gradlew -Pqc detekt
```

`ktlintFormat` modifies source files, so review its diff before committing. These extra commands are not currently part of the default CI workflow. If a task is unavailable in your environment, report that in the PR rather than claiming it passed.

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
