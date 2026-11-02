# Taki 0.1.0-beta.2 release candidate

Handoff report. **Nothing was tagged, released, uploaded or announced.** The signed APK and its checksum are in a
private local staging folder outside the repository. Date: 2026-11-01.

## 1. Artifact

| Item | Value |
|---|---|
| Version | `versionName 0.1.0-beta.2`, `versionCode 132` (published beta.1 is 131) |
| Package | `io.github.churipakinti.taki` (release; debug stays `.debug`) |
| Type | single **universal** APK, `-PabiSplits` not used |
| File | `taki-0.1.0-beta.2.apk` in the private staging folder, plus `SHA256SUMS.txt` |
| Size | 30,340,341 bytes (28.93 MB) |
| SHA-256 | `2cb375a39f4d0b0649b4404212f07fb84366f7a6c3ec47e72c09b8fb215ce7a0` |
| Code SHA that produced it | **`e26349ed`** (`develop`); later commits only add documentation and screenshots (no file under `ultrasonic/src/main`, resources or Gradle changed), so they do not affect the APK |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| minSdk / targetSdk | 26 / 37; not debuggable |
| Signature | APK Signature Scheme v2, one signer, `CN=Taki Release, OU=Taki Android, O=Taki, C=US` |
| Certificate SHA-256 | `66b7c716f02273a087faf5614e56e11683441b5ae8616633ce364cab16f37125` (identical to `v0.1.0-beta.1`, compared by certificate digest, not by file name) |
| Contents check | 992 entries; no keystore, secret or test fixture; the `*.properties` entries are standard library/Gradle metadata |

The published APK must be **this exact file**; recompute the SHA-256 before uploading.

## 2. Automated gates (on the code commit `e26349ed`)

| Gate | Result |
|---|---|
| Unit tests | 1948, 0 failures, 0 errors |
| `lint` | no new issues (59 errors / 4 hints filtered by the baseline) |
| `assembleDebug`, `assembleRelease` | successful |
| Detekt (`-Pqc detekt`) | 43 findings = the documented baseline (42+1); informational, not a CI gate |
| Test fixed along the way | `ReleaseHygieneInvariantsTest` pinned the beta.1 `versionName`; it failed on the authorized bump and now pins `0.1.0-beta.2` and `versionCode 132` (test-only change, commit `e26349ed`) |
| GitHub Actions | see section 9 |

## 3. Emulator validation (isolated AVD, fake Subsonic server, no real server)

| Test | Result |
|---|---|
| **Upgrade 131 -> 132 from the published beta.1 APK** | `adb install -r` of the staging APK: success; first-install time unchanged; no onboarding; queue (5), item and position (4014 ms) identical; then play, Next, seek (to 72.6 s), pause all worked; 0 crashes |
| **Upgrade 131 -> 132 from the exact build installed on the owner's Pixel** (the local 30 MB `0.1.0-beta`/131 build, same certificate) | success; queue of 30 and position 3937 ms preserved; plays; 0 crashes. This is a rehearsal of the Pixel update without touching the Pixel |
| Clean install of the candidate | opens on "Add library", connects, plays; 0 crashes |
| Share Card | tapping Share generated the PNG and Android's "Sharing image" sheet showed the card preview; playback continued; 0 crashes |

Note: the emulator has no audio device, so audibility is not claimed; Instagram and WhatsApp are not installed on it.
A first beta.1 run crashed with `Permission denied` creating a cover file because a leftover external-storage folder
from my own earlier uninstall belonged to an old user ID. That is an emulator artifact, not an app defect (cleaned
and not reproduced); it does show that an unwritable external folder can crash cover-art loading, which is
rare and left as a `POST_BETA` note.

## 4. Pixel 7 release install: update NOT performed

| Item | Value |
|---|---|
| Device | Pixel 7, serial `2B191FDH200E36`, Android 17; every command used `-s` |
| Release package | `io.github.churipakinti.taki`: `versionCode 131`, `0.1.0-beta`, installed 2026-10-18, signature `CN=Taki Release`, certificate `66b7c716…7125` (APK pulled read-only and verified), not debuggable |
| Debug package | `io.github.churipakinti.taki.debug`: separate, untouched in this task |
| Update path | the candidate is signed with the same certificate and its `versionCode` rises 131 -> 132, so `install -r` would be accepted |

**Backup verdict: no verifiable backup of the release app's private data exists, so I did not update it.**

- `run-as` is refused (`package not debuggable`), so the private directory cannot be copied.
- `adb backup` does not give a reliable result on modern Android for an app that targets API 37.
- System backup (`bmgr`, Google transport) is by design incomplete: Taki **excludes its server/credentials database
  from backup and device transfer**, and the saved queue/equalizer from transfer. A system backup could therefore never
  restore the configured server, and it would be unverifiable from a computer anyway.
- The app's external folder (about 7 MB) holds covers and cache only; no large downloads for the release package.
- Switching the global backup transport to the local transport would change a system setting and still could not be extracted without root.

I did not try root, exploits or credential extraction. Because no recoverable, verifiable backup exists, the stop
condition applies and the release package on the Pixel is unchanged (`PIXEL_RELEASE_UPDATE_PERFORMED = NO`).

What is at stake if you choose to update anyway: the configured server, preferences, saved queue and equalizer of
the **release** package (re-creating them means re-entering the server address, user and password). Evidence that
the update itself is safe is in section 3 (the same build, upgraded on an emulator with its queue intact), and the
same code ran physically as a debug build in #36. **Decision for you:** (a) accept updating the Pixel release
package in place without a backup, knowing the worst case is re-adding the server; or (b) leave the Pixel as is and
run the physical release smoke on a spare device. I will not choose for you.

## 5. Physical release smoke (section "Phase 7")

Not run on the signed release APK, because the Pixel was not updated. Status per area:

| Area | Release APK on a phone | Related evidence |
|---|---|---|
| Playback, screen off, lock-screen controls | NOT_TESTED | #36 Pixel 7 on the same code as a debug build; emulator smoke on the release APK |
| Real swipe-away | NOT_TESTED | #36 Pixel 7 (debug); emulator Recents behavior |
| Bluetooth | NOT_TESTED | #36 Pixel 7 (debug) |
| Offline with downloads | NOT_TESTED | #36 Pixel 7 (debug) |
| Share Card to Instagram / WhatsApp | NOT_TESTED here | owner confirmed both work; emulator shows the share sheet and preview |
| Stability | emulator: 0 crashes | #36: 0 interruptions |

The one thing the debug runs cannot prove is behavior that depends on R8 minification and release signing. The
emulator smoke of the minified release APK covers startup, sign-in, playback, notification, the foreground
service, update and the share sheet; a physical release pass remains the open check.

## 6. Issues

| Issue | Status |
|---|---|
| #15 album star | **closed** with evidence (implementation, tests, Feishin interop verified earlier) |
| #20 Share Card | **closed** with evidence (implementation, FileProvider and share sheet verified on the RC, owner confirmed Instagram and WhatsApp) |
| #22 album list does not reload after a folder change | **open**, `POST_BETA`: the code documents it as a known gap; the selector changes, the page does not reload; only matters with several music folders; no crash, no wrong data; pull-to-refresh is the workaround. Not exercised live because the fake server has one folder |
| #35 degraded Wi-Fi | open, residual risk accepted for the beta |
| #37 resumption card | open, `POST_BETA` (cosmetic) |
| #41, #42, #44, #23, #24, #12, #13, #14 | open, post-beta |

No `RELEASE_BLOCKER` found.

## 7. Documentation and screenshots

- `ultrasonic/build.gradle`, `CHANGELOG.md` (`0.1.0-beta.2` section plus an empty `[Unreleased]`),
  `fastlane/.../changelogs/132.txt` in **en-US and es-ES**.
- README screenshots are **new real captures from the release candidate** on an emulator with a fictional library
  (artists such as "Lumen Harbor", generated cover art, no real server data), 1080x2400, same order as before:
  Home, Library, Album, Now Playing, Search. The Spanish set (`es-ES`) was refreshed the same way. The emulator
  status bar was fixed to 10:00 with full battery. No code was changed for visuals.

## 8. Accepted limitations and publication risks

- **#35:** playback on a Wi-Fi that stays connected but carries no data may recover slowly; not verified on hardware.
- **#37:** a dimmed Android resumption card after Taki stops; Play on it works; a ~0.3 s wrong-title flash on resume.
- Release APK is 28.9 MB (universal). A per-ABI APK would be 11.5 MB (arm64) but needs a versionCode scheme; deferred.
- The physical **release** pass is pending (section 5); the emulator and debug evidence cover everything except R8/signing-specific behavior on a real phone.
- Cleartext HTTP and user CAs are allowed on purpose for self-hosted servers (same as beta.1).
- Risk on publication: if the owner uploads a rebuilt APK, its SHA-256 will differ; upload the staged file.

## 9. CI

The GitHub Actions run on the code commit `e26349ed` finished green. Commits that follow are documentation and
screenshots only and re-run the same job.

| Run | Result |
|---|---|
| `e26349ed` | **success** (run 41369053865): tests, lint, debug and release builds, debug-APK upload |

## 10. Recommendation

```text
READY_FOR_RELEASE_CANDIDATE = YES   (signed, versioned, verified APK; 131 -> 132 update proven on emulator)
READY_TO_PUBLISH            = NO    (needs: CI green on the final commit, and your decision on the Pixel
                                     physical release pass: update without backup, or use a spare device)
PUBLIC_BETA_READY           = NO
```

## 11. Steps to publish (all need your approval)

1. Decide the Pixel question in section 4 and, if you choose to, run the physical checklist on the staged APK.
2. Confirm CI is green on the latest `develop`.
3. Re-verify the staged APK: `apksigner verify --print-certs` (certificate `66b7c716…7125`) and its SHA-256 equals the value in section 1.
4. Create the tag `v0.1.0-beta.2` on the tested commit and the pre-release, with the release notes from
   `docs/technical/TAKI_BETA_RELEASE_GATES_REVIEW.md` section 9 (update the size to ~29 MB), the staged APK renamed
   `taki-0.1.0-beta.2.apk`, and `SHA256SUMS.txt`.
5. Post the community message after the release exists.

## 12. Physical validation of the release APK (2026-11-01)

The owner accepted the missing-backup risk for an in-place update of the official signed APK. The staged APK
(SHA-256 `2cb375a3…15ce7a0`, certificate `66b7c716…7125`) was installed on the Pixel 7 with `adb -s <serial> install -r`.

| Check | Result |
|---|---|
| Update 131 -> 132 | `Success`; `versionCode 132`, `0.1.0-beta.2`; `firstInstallTime` unchanged (2026-10-18) |
| Data preserved | opened on Home with the library, no onboarding; saved queue and position restored; external data folder unchanged (6.8 MB); downloads still available offline |
| Debug package | untouched (`.debug`, 131, last update 2026-10-31) |
| Playback, pause/resume, next/previous, screen off, lock-screen controls | PASS (owner heard audio) |
| Seek on streamed tracks | **PARTIAL**, see below |
| Lifecycle (real swipe from Recents) | PASS: same process kept playing, track/queue/position preserved |
| Bluetooth (route, play/pause, disconnect, reconnect) | PASS, 4 of 4 steps |
| Offline (Wi-Fi and data off, verified no active network) | PASS: next, seek, pause/resume on downloaded tracks, no errors |
| Share Card (Instagram, WhatsApp) | PASS (owner-confirmed, nothing published or sent) |
| Crashes / ANRs | 0 (logcat checked) |
| Device configuration | Wi-Fi/data restored, airplane off, no adb reverse/forward left |

### Seek on streamed tracks

On streamed (not downloaded) tracks the seek bar can be ignored for the first seconds: the session lacks the seek action
and Media3 logs `Controller isn't allowed to call command= 5`. Observed delay before seeking becomes available
ranged from about 3 s to 30 s or more (one track stayed unseekable for 28 s or more), after which it works; downloaded
tracks seek immediately. Streaming data-source code is unchanged since beta.1 apart from #39, so this is probably not
introduced by beta.2, but beta.1 could not be run against the same server to prove it. Cause not isolated (likely the
server stream's length/range behaviour); needs an issue and a decision.
