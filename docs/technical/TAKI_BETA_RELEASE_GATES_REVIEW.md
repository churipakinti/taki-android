# Taki pre-beta release gates review (next beta after `v0.1.0-beta.1`)

This is a **release-readiness audit**, not a release. Nothing was tagged, published, uploaded or announced. No
production code and no version number were changed; only documentation (README, CHANGELOG, this file).
Date: 2026-11-01.

## 1. Technical baseline

| Item | Value |
|---|---|
| Branch / SHA | `develop` @ `c60fd493` (clean, in sync with origin); code last changed in `4c2c5b5c` (#50), everything after is documentation |
| Published release | `v0.1.0-beta.1` (pre-release, 2026-08-26), tag on `860a7144`, branch `release/0.1.0-beta.1` at the same commit |
| Delta since the tag | 155 commits (most are code; the rest are docs) |
| Gradle (HEAD and tag) | `applicationId io.github.churipakinti.taki`, `versionCode 131`, `versionName "0.1.0-beta"`, minSdk 26, targetSdk 37, compileSdk 37 |
| Tests | 1948 unit tests, 0 failures (last full run, #50) |
| Local release build | `./gradlew :ultrasonic:assembleRelease` green on `c60fd493` |

## 2. Published release inventory

| Item | Finding |
|---|---|
| Assets | `taki-0.1.0-beta.1.apk` (5,310,684 bytes) and `SHA256SUMS.txt` |
| Checksum | the SHA-256 in `SHA256SUMS.txt` and in the release body equals the one computed from the downloaded APK (`4b73eaeb…af03e`) |
| Manifest | package `io.github.churipakinti.taki`, `versionCode 131`, `versionName 0.1.0-beta`, minSdk 26, target 37, not debuggable, no native code |
| Signature | APK Signature Scheme v2 only, one signer, `CN=Taki Release, OU=Taki Android, O=Taki, C=US`, SHA-256 `66b7c716f02273a087faf5614e56e11683441b5ae8616633ce364cab16f37125` |
| Release notes | short; says the build is signed with the Taki release certificate and lists the portrait-only limitation |
| Note | the published `versionName` is `0.1.0-beta`, **not** `0.1.0-beta.1`; the tag and asset names carry the `.1` but the in-app version does not |

The published APK is **not** the current code: it predates the FFmpeg FLAC decoder, the Compose migration, #34,
#35, #47, #49, #50 and the Android Auto and security work.

## 3. Proposed next version

| | Current | Proposed | Why |
|---|---|---|---|
| Tag / release name | `v0.1.0-beta.1` | **`v0.1.0-beta.2`** | follows the existing `v0.1.0-beta.N` convention and the pre-release flag |
| `versionName` | `0.1.0-beta` | **`0.1.0-beta.2`** | makes the in-app About screen distinguish builds (beta.1 shows `0.1.0-beta`) |
| `versionCode` | `131` | **`132`** | **required**: beta.1 users have `131`; Android refuses a downgrade and treats an equal code as the same version, so a build that keeps `131` is not a normal upgrade. `132` is the smallest value that works; it also keeps the Fastlane file convention (`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, existing up to `131.txt`) |
| APK file name | Gradle: `Taki-<versionName>-release.apk` | publish as `taki-0.1.0-beta.2.apk` + `SHA256SUMS.txt` | matches the beta.1 asset names |
| Package ID | `io.github.churipakinti.taki` | unchanged | required for updates; the debug build keeps `.debug` |

**Not done (needs your authorization):** edit `ultrasonic/build.gradle` (`versionCode 132`, `versionName "0.1.0-beta.2"`),
add `fastlane/.../changelogs/132.txt`, and create the tag `v0.1.0-beta.2`.

## 4. CI and technical gates

| Gate | Status | Evidence |
|---|---|---|
| `testDebugUnitTest`, `lint`, `assembleDebug`, `assembleRelease` (the CI job) | PASS on the last code commit | GitHub Actions run on `4c2c5b5c`: success, all steps incl. artifact upload |
| CI on `c60fd493` | docs-only commit; run was still `in_progress` when written | re-check before tagging |
| Earlier red run (`2074e199`) | infrastructure flake, not code | `Test, lint, and build` failed with `IOException` fetching Robolectric artifacts from Maven (`MavenArtifactFetcher`); the same tests passed on the next code commit |
| `a2081cb8` run | cancelled by the newer push (`concurrency: cancel-in-progress`) | not a failure |
| `lintVitalRelease` | runs inside `assembleRelease`; green locally | local build |
| Detekt | not a CI gate (`ci.yml` does not run it); repository baseline 42+1 unchanged since #50 | #36 / section 13 of the audit |
| CI artifact vs reviewed commit | CI uploads the **debug** APK per SHA (`taki-debug-<sha>`); CI does **not** build the signed release APK | the release APK must be built locally with the release keystore (section 6) |

## 5. Android release hygiene (regressions since beta.1 only)

| Check | Result |
|---|---|
| Permissions | identical to beta.1 (`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH(_CONNECT)`, `FOREGROUND_SERVICE(_MEDIA_PLAYBACK/_DATA_SYNC)`, own signature permission) |
| Debuggable | no (release); no `.debug` suffix; the `Taki (Debug)` label is debug-only |
| Exported components | unchanged set: launcher activity, `PlaybackService` (Media3 library service, exported by design and hardened in #45/#46), Bluetooth and media-button receivers, one provider (album art, hardened earlier); the toggle receiver is **not** exported |
| Network | cleartext and user CAs allowed on purpose for self-hosted servers (same as beta.1) |
| Backup | `allowBackup=true`, but the credentials database is excluded from cloud backup and device transfer, and the saved queue and equalizer file are excluded from device transfer (#47) |
| Credentials in logs | release build log on the emulator after sign-in, playback and a queue change: no password, `p=`, `t=` or `s=` values |
| Crash handling | no crash in the emulator smoke runs (0 entries in the crash log) |
| Licensing | GPL-3.0 `LICENSE`, upstream attribution in `NOTICE.md` and the README credits; the legacy `ultrasonic-keystore.enc` is not used |
| APK size | beta.1 5.3 MB; candidate **28.9 MB** universal because the FFmpeg decoder adds native code for 4 ABIs (see 6.4) |

No new release-hygiene regression was found.

## 6. Signing and installability

| Check | Result |
|---|---|
| Release keystore available | yes: `keystore.properties` exists at the repo root (git-ignored, untracked) with all four keys, and its `storeFile` exists **outside** the repo |
| Official Taki identity | yes: the candidate is signed as `CN=Taki Release, OU=Taki Android, O=Taki, C=US` |
| Certificate matches beta.1 | **yes**: both APKs have SHA-256 `66b7c716f02273a087faf5614e56e11683441b5ae8616633ce364cab16f37125` |
| Signature verification | `apksigner verify` (build-tools 37.0.0): `Verifies`, v2 scheme, one signer, for both the candidate and the published APK |
| Not the debug identity | yes: the debug certificate is `03:A1:50:8C:…:25:8E:1E` (from `ci.yml`), different |
| Test data in the release | none found: no debug label, no `.debug` package, not debuggable |
| Secrets handling | no keystore path, alias or password was printed or copied; no new keystore was generated |

Signature scheme note: both APKs are v2-only (minSdk 26); that is consistent between beta.1 and the candidate, so
key continuity is preserved.

### 6.1 Upgrade compatibility (isolated emulator, never the Pixel)

| Step | Result |
|---|---|
| Install the **published** beta.1 APK | success |
| First run: add a server (fake Subsonic fixture), play a 5-track album, pause on track 2 | `PAUSED`, queue 5, position 1931 ms |
| `adb install -r` of the **candidate release APK** over it | success (same signature accepted) |
| After the update | no onboarding screen (server preserved), new Home loads, **queue of 5 restored with the same item and position (1931 ms)**, play/next/pause work, no crash |

**Limitation:** the candidate still has `versionCode 131`, so this proves signature compatibility, the Room schema
migration and data preservation, but not a `131 -> 132` upgrade. The code-bump variant is repeated on the final
release candidate (section 12). Downloads, accounts beyond one server, and the hi-res FLAC decoder were not part of
this emulator pass.

### 6.2 Clean install

Uninstalling and installing the candidate fresh on the emulator launches to the notification-permission prompt and
then onboarding with no crash.

### 6.3 Release smoke on the emulator (not a substitute for the final RC test)

Candidate release APK: play, next, pause, play with the screen off, foreground service (id 1001) and notification
present, no secrets in the log, 0 crashes. The emulator has no audio device, so audibility was not checked.

### 6.4 APK size and ABIs

Built locally with the opt-in `-PabiSplits` flag (same signing, same code):

| APK | Size |
|---|---|
| beta.1 (published, no native code) | 5.3 MB |
| universal (4 ABIs), default build | 28.9 MB |
| `arm64-v8a` (almost every phone since ~2017, incl. Pixel 7) | 11.5 MB |
| `armeabi-v7a` | 11.1 MB |
| `x86_64` / `x86` (emulators, few devices) | 12.4 MB each |

The per-ABI APK is signed with the same certificate (checked on `arm64-v8a`). **Decision for you:** (a) publish
only the universal APK (simplest for non-technical users, one file, 29 MB), or (b) publish universal plus an
`arm64-v8a` APK (11.5 MB) and say which to pick. Per-ABI files share one `versionCode`; when several ABIs are
installable Android needs distinct codes per ABI for in-place updates between ABI variants, so (b) needs a code
scheme before it is used. Recommendation: **(a) for beta.2**, revisit for beta.3.

## 7. Known-issue triage

Open issues (13) at `c60fd493`:

| # | Title (short) | Class | Reason |
|---|---|---|---|
| 35 | Wi-Fi connected but unusable | `ACCEPTED_LIMITATION` | residual risk accepted for the beta in #36; stays OPEN; **do not claim recovery is verified**; wording is in the notes below |
| 37 | stale Quick Settings card | `POST_BETA` / accepted | see 7.1 |
| 44 | Android Auto deferred P3 items | `POST_BETA` | no blocker found in the AA audit |
| 42 | hi-res FLAC 24-bit reaches Android as 16-bit PCM | `POST_BETA` | question, not a defect |
| 41 | FFmpeg rejects a packet near 154.2 s of one 24/192 file | `POST_BETA` | no stall, cause unknown |
| 22 | Album list does not reload after a folder change (folder mode) | `POST_BETA` | pre-existing and limited to folder mode; **needs a quick repro on the release candidate**; promote to `PRE_RELEASE_FIX` only if it reproduces on the common path |
| 20 | Share Now Playing as an Instagram-ready card | **recommend closing** | the Share Card is implemented (`7126c2a9`, `c22ed489`, `55fa4336`, `389723f0`); owner to close |
| 15 | Album star/unstar | **recommend closing** | implemented in `3156c8fd` |
| 24 | Fullscreen Visualizer Mode | `POST_BETA` | feature |
| 23 | Dead Artist/Album list legacy code cleanup | `POST_BETA` | cleanup |
| 14, 13, 12 | Smarter radio, mixes, cross-client resume | `POST_BETA` | features |

No `RELEASE_BLOCKER` and no `PRE_RELEASE_FIX` were found. Closed issues were not reopened; the number of open
issues was not used as a criterion.

### 7.1 #37 (Quick Settings media card)

Physical evidence from #36 (Pixel 7, Android 17):

- While Taki plays or is paused with a live service, the card shows Taki's title, artist, route and controls
  correctly: one card, one session.
- After the service stops, Android keeps a **dimmed media-resumption card** with a Play button. This is Android's
  own resumption UI and is legitimate; it is not a leaked Taki session (`dumpsys media_session` showed no session
  for Taki at that moment). It is not classified as a defect and no workaround to hide it is proposed.
- Play on that card works: it restarted the service and restored the same song and position in about 3.4 s.
- The **brief (~0.3 s) wrong-title flash** was seen in the *session state* (`dumpsys media_session`), not only in
  SystemUI. So it comes from the session (Taki's restore or Media3 publishing queue item 0 before the saved index and
  position are applied), not from the SystemUI card. The cause was not isolated further; it has no functional impact.

Classification: **`POST_BETA`** (cosmetic). Small recommended improvement: apply the saved queue, index and position in
one step on a cold resumption start so item 0 is never published. Keep #37 open for that; do not change it before the beta.

## 8. Documentation and first-run onboarding

| Question | Answer |
|---|---|
| Understands what Taki is | yes (README intro) |
| Knows a server is required | yes (Requirements); now also a short **Getting started** section |
| Finds the right APK | one APK on the release page; with 4 ABIs it is still one file (see 6.4) |
| How to connect | **fixed**: README now explains URL / username / password / Connect, `http://` vs `https://` |
| Limitations of the beta | the portrait-only line exists; the known limitations (Wi-Fi, #37) go into the release notes below |
| Where to report | README links the tracker; **fixed**: it now says what to include and how to scrub a log |
| Safe logs | **fixed**: never attach a log with the address, username or `u=`, `p=`, `t=`, `s=` values; logs are usually not needed |
| Screenshots match the app | **not verified**: the README screenshots under `fastlane/.../phoneScreenshots/` (1-5) predate the Compose redesign and should be re-captured from the release candidate; **`PRE_RELEASE_FIX` (low effort, no code)** |
| GPLv3 and attribution | consistent: GPL-3.0, upstream credit and `NOTICE.md` |

Fixed in this review (clearly outdated or incorrect text only):

- README said sharing is deliberately excluded although the Share Card ships. It now says "server-side share links"
  are excluded and lists the Share Card (generated locally, nothing uploaded).
- README had no first-run instructions and thin bug-report guidance: added.
- CHANGELOG `[Unreleased]` covered only the early sweep: now covers the delta since `v0.1.0-beta.1` (Share Card,
  hi-res FLAC, Android Auto, Compose rebuild, fixes) and a **Known limitations** section. It stays `[Unreleased]`:
  no version heading was written for a release that does not exist yet.

## 9. Draft release notes (GitHub) - not published

> **Taki v0.1.0-beta.2** (pre-release)
>
> Second public beta. Taki is a focused Android music player for Navidrome and OpenSubsonic servers. **You need your
> own server**; enter its address, username and password on first launch.
>
> **Added**
> - Share Card: turn Now Playing into an image to share (made on your phone, nothing uploaded).
> - Like or unlike a whole album; a Liked Albums entry in Library; tap an artist name to open the artist.
> - Hi-res FLAC (24-bit, high sample rate) now plays through a built-in decoder.
> - Android Auto: songs start inside their list, favorites play as a list, search works.
>
> **Changed**
> - The whole interface was rebuilt for a calmer, artwork-first look; behavior is the same.
> - Your saved queue and equalizer are kept more safely between launches.
> - The APK is larger (about 29 MB) because of the new decoder.
>
> **Fixed**
> - Swiping Taki out of Recents no longer stops the music.
> - Playback recovers from short network and server errors, and no longer stalls falsely after calls or assistant
>   interruptions.
> - Switching to Offline no longer cuts the current song; the Albums list no longer crashes while loading.
> - Downloads keep the right file type; server credentials are kept out of logs.
>
> **Known limitations**
> - If you stay connected to a Wi-Fi network that has stopped carrying data, playback may take a long time to
>   recover (#35).
> - After Taki stops, Android may keep a dimmed Taki card in Quick Settings; its Play button works (#37).
> - Portrait phone layout only.
>
> **Verify:** `SHA256SUMS.txt` lists the APK's SHA-256. Official APKs are signed with the Taki release certificate
> (SHA-256 `66b7c716f02273a087faf5614e56e11683441b5ae8616633ce364cab16f37125`), the same as beta.1, so beta.2 updates
> beta.1 in place.
>
> Report problems at the issue tracker; please include your Android version and server type, and **never paste
> credentials or private server addresses**.

## 10. Draft community message (Navidrome) - not published

> **Taki, a small Navidrome-first Android player: second public beta**
>
> I'm building Taki, a music-only Android player for Navidrome/OpenSubsonic. It's a fork of Ultrasonic, GPLv3, and
> a personal open-source project (no company, no accounts, nothing leaves your phone except the normal Subsonic
> calls to *your* server).
>
> What I'd love feedback on, in this order:
> 1. **Reliability:** does music keep going when you lock the phone, swipe it out of Recents, take calls or switch
>    between Wi-Fi and mobile data? Did it ever stop or skip by itself?
> 2. **Offline:** do your downloaded albums play with no connection?
> 3. **First run:** was connecting to your server clear? Where did you get stuck?
> 4. Hi-res FLAC and Android Auto if you use them.
>
> Known gap: on a Wi-Fi that's connected but stopped carrying data, recovery can be slow. I haven't verified a fix
> yet, so I'd like to hear if you hit it.
>
> Download and checksums are on the GitHub release page. It's a beta: please don't make it your only player yet.
> Please report issues on GitHub with your Android version and Navidrome version, and no credentials or server URLs.

## 11. Release gates

| Gate | Status | Evidence | Required action |
|---|---|---|---|
| Playback reliability | PASS | #36 audit: blocks A-E on a Pixel 7, 0 unexpected skips/silences/interruptions; #35 field case accepted | none |
| Security | PASS | #45, #46 closed; no new hygiene regression found here | none |
| State durability | PASS | #47 closed; queue survived swipe-away, process kill and the beta.1 -> candidate update | none |
| UI quality | PASS | #48 closed; Pixel runs in #36 | re-capture README screenshots |
| Crash fixes | PASS | #49, #50 closed; #49 on hardware, #50 on the final build | none |
| CI | PASS (code) / PENDING (docs commit) | green on `4c2c5b5c`; `c60fd493` run in progress | confirm green on the final RC commit |
| Versioning | **FAIL (action needed)** | `versionCode 131` equals the published beta; `versionName` has no `.2` | bump to `132` / `0.1.0-beta.2` with authorization |
| APK signing | PASS | same official certificate as beta.1, `apksigner verify` ok, not debug | repeat on the final RC APK and record its SHA-256 |
| APK upgrade | PARTIAL | beta.1 -> candidate on the emulator preserved server, queue, position; same `versionCode` | repeat with the bumped `132` build, then on a spare device; Pixel only with explicit authorization |
| Release smoke | PARTIAL | emulator smoke only; no audio device, no downloads/offline | run the checklist in section 12 on the final signed APK |
| Documentation | PASS after the fixes above | README, CHANGELOG, drafts | re-capture screenshots; add `fastlane/.../changelogs/132.txt` |
| Open issues | ACCEPTABLE | section 7: no blocker, no pre-release fix | quick repro of #22; owner closes #15 and #20 |

## 12. Release-candidate checklist (final signed APK)

1. Clean install on a clean device or emulator: onboarding, connect, play.
2. Update from the **published beta.1 APK** to the RC (with `versionCode 132`): server, queue and settings kept.
3. Connect to a compatible server; start playback from Home, an album and a playlist.
4. Play/Pause, Next/Previous, seek.
5. Background, screen off (4+ minutes), lock-screen controls, notification controls.
6. Swipe from Recents while playing (music continues) and while paused (stops cleanly, restores).
7. Offline: one downloaded album in airplane mode (Wi-Fi explicitly off), natural transition, seek.
8. Headset buttons and disconnect if a Bluetooth device is available.
9. Close and reopen; queue, item and position preserved.
10. No crash in the crash log; `apksigner verify`, certificate `66b7c716…7125`, SHA-256 recorded, APK not debuggable,
    package `io.github.churipakinti.taki`.
11. #22 quick repro (folder-mode album list).
12. The APK tested is **byte-identical** to the one published (`SHA256SUMS.txt` computed from it).

## 13. Recommendation

```text
READY_FOR_RELEASE_CANDIDATE = YES
READY_TO_PUBLISH            = NO
PUBLIC_BETA_READY           = NO
```

`READY_FOR_RELEASE_CANDIDATE`: no blocker; signing, certificate continuity and update-in-place are demonstrated; the
remaining work is mechanical. `READY_TO_PUBLISH = NO` until the version bump, the final signed APK, the bumped-version
upgrade test, the release smoke on that APK and a green CI on the final commit exist. `PUBLIC_BETA_READY = NO`: not
claimed; it needs the items below plus your decision.

## 14. Exact steps remaining

1. You authorize the version bump. Then: `versionCode 132`, `versionName "0.1.0-beta.2"` in `ultrasonic/build.gradle`,
   add `fastlane/metadata/android/en-US/changelogs/132.txt`, move `[Unreleased]` in `CHANGELOG.md` to `0.1.0-beta.2`.
2. Decide the APK shape (universal 4-ABI vs per-ABI, section 6.4).
3. Push to `develop`; wait for CI green on that SHA.
4. Build the RC locally: `./gradlew :ultrasonic:assembleRelease` with the release keystore; copy the APK to a staging
   folder outside the repo; `apksigner verify --print-certs`; compute SHA-256; write `SHA256SUMS.txt`.
5. Run the section 12 checklist on that exact file (upgrade from the published beta.1 on an emulator/spare device;
   your Pixel only if you authorize it).
6. Re-capture the README screenshots from the RC; recheck #22; close #15 and #20 if you agree.
7. Create the tag `v0.1.0-beta.2` on the tested commit and the pre-release with the section 9 notes, the APK and
   `SHA256SUMS.txt` (this is the first step that publishes anything).
8. Post the section 10 message after the release exists.
