# Taki Beta Readiness Audit (Phase 5B, issue #25)

Audit date: 2026-10-10. Auditor: Claude (Sonnet 5.5) with the project owner's Pixel 7
(`2B191FDH200E36`, Android 17 / API 37, 1080×2400 @ 420 dpi, portrait-locked app).

This is an **audit**, not a remediation. No product code was changed. The only repository changes
are tests and this document (see §P).

---

## A. Executive verdict

**`BETA_READY = NO`**

There is no crash, data-loss or release-build blocker, the core journeys work, and playback is
stable. But one privacy defect should not ship to external testers (**BB-1**, a plaintext server
password reaching the log file the beta is going to ask testers to attach), and five reproducible
interaction defects (**BF-1…BF-6**) are cheap to fix and visible to anyone using the app for an
hour. The strict reading of the decision rule ("a reproducible issue that should reasonably be
fixed before external beta users → NO") applies. The minimal remediation set is in §R.

| Class | Count |
|---|---:|
| BETA_BLOCKER | 1 |
| BETA_FIX | 6 |
| POST_BETA | 12 |
| ACCEPTED | 11 |
| NOT_REPRODUCED / not exercised | 8 |

---

## B. Baseline

| Item | Value |
|---|---|
| Commit audited | `0424dc4f` "Fix sheet tap-through and Back handling; record 5A6 Pixel validation" (`origin/develop` == local, clean) |
| Canonical UI matrix | 27 Compose / 0 Hybrid / 0 Legacy (unchanged) |
| Reachable app-owned View overlays | 0 (unchanged; `ResidualViewOverlayGuardTest` green) |
| Tests at start | 1470 / 0 failures / 0 skipped |
| detekt (ultrasonic) | 42 (= baseline) |
| Installed build on device | debug APK built from this commit (`io.github.churipakinti.taki.debug`) |
| Release APK | `io.github.churipakinti.taki`, versionName `0.1.0-beta`, versionCode 131, signed (v2), not debuggable |

---

## C. Methodology

1. **Source review** of manifest, Gradle, backup rules, network security config, logging, the
   exported `ContentProvider`, and every known-debt item.
2. **Automated probes** added as tests (§P): a per-sheet interaction matrix, release-hygiene
   invariants, and an executable reproduction of the credential-logging defect.
3. **Live Pixel 7 crawl** with `adb` + `uiautomator` + screenshots + `dumpsys` + `logcat`:
   sheets, onboarding, Home/Library/Search/detail screens, playback, Now Playing, Up Next, Lyrics,
   Sleep Timer, Equalizer, downloads/offline, a real network outage, font scale 1.5, a lifecycle
   stress loop, scroll-jank and memory samples.
4. Production state was recorded first and restored afterwards (§Q).

Limits, stated up front: the release build was not driven live (the device carries the debug
build; release was audited statically and built/signed by Gradle); TalkBack was not run (semantics
were checked structurally); the app's own error sheet could not be provoked safely (§H).

---

## D. Findings summary

| ID | Area | Finding | Severity | Reproduced | Evidence | Recommended action |
|---|---|---|---|---|---|---|
| **BB-1** ([#27](https://github.com/churipakinti/taki-android/issues/27)) | Privacy | `ServerSetting.toString()` contains the plaintext password and `ServerSettingsModel` logs the whole object (add/update/re-index). In **release** it reaches the file log when "debug log to file" is on — the feature a tester is asked to use to attach logs. | **BETA_BLOCKER** | Yes (test + source) | `ServerSettingCredentialExposureAuditTest`; `ServerSettingsModel.kt:124,138,168` | Stop logging the object / redact `toString`; re-run the audit tests (they flip). |
| BF-1 ([#28](https://github.com/churipakinti/taki-android/issues/28)) | Sheets | Tap on a non-interactive part of a panel (title, message, blank area, gap between fields) falls through to the scrim and **dismisses** the sheet. 11 sheets built before 5A6. | BETA_FIX | Yes — live on 7, Robolectric on all 11 | §E; `SheetInteractionAuditTest` | Give the old sheets the 5A6 `TakiSheet` panel behaviour. |
| BF-2 ([#28](https://github.com/churipakinti/taki-android/issues/28)) | Sheets / Back | The composable does not handle Back for 7 sheets; with no host callback, **Back leaves the screen** (sheet lost) or does nothing. | BETA_FIX | Yes — live on 5, source on 2 | §E | `TakiBackHandler` in those sheets (or adopt `TakiSheet`). |
| BF-3 (#21) | Radio | Artist Radio builds its queue on the main thread; `CachedMusicService.getAlbumsOfArtist` throws Room's `IllegalStateException` (main-thread DB), `fetch()` swallows it, and the radio silently loses the artist's albums (3 seed candidates, rest random filler). Radio still plays. | BETA_FIX | Yes (live, logcat) | §N | Move the build to IO (the issue's own suggestion). |
| BF-4 ([#29](https://github.com/churipakinti/taki-android/issues/29)) | Up Next | Reordering with **shuffle on** moves the wrong tracks (`moveItemInPlaylist` ignores shuffle position — it says so in a TODO). | BETA_FIX | Yes (live) | §G | At minimum, disable the drag handle while shuffled; or map play-order → playlist indices. |
| BF-5 ([#30](https://github.com/churipakinti/taki-android/issues/30)) | Playback errors | A stream the server cannot serve (`UnrecognizedInputFormatException`) leaves the player in `STATE_ERROR` with **no message**; **Next** from that state does not start the next track (needs a second Play press). | BETA_FIX | Yes (live) | §G | Surface the error (toast/sheet) and auto-advance or let Next prepare. |
| BF-6 ([#31](https://github.com/churipakinti/taki-android/issues/31)) | Network | With the network down, list screens (Albums) show **"No media found"** — indistinguishable from an empty library; the failure is swallowed, no retry hint. | BETA_FIX | Yes (live outage) | §H | Distinguish error from empty; offer retry. |
| PB-1 | Downloads | Online Album Detail shows no per-row ✓ and no "downloaded" state on the download button, although 12/12 tracks are local (Downloaded-mode view does show ✓). | POST_BETA | Yes | §H | Known "online per-row indicator gap". |
| PB-2 | Now Playing | Seek bar is disabled while paused (legacy parity) — you cannot scrub a paused track. | POST_BETA | Yes | §G | Product decision. |
| PB-3 | Server selector | Deleting a **non-active** server clears the *active* server's metadata cache and orphans the deleted one's. Cache only, re-fetchable. | POST_BETA | Source + existing unit test | §N | Known `deleteMetaDatabase` quirk. |
| PB-4 | StrictMode | 7 main-thread `DiskReadViolation`s (`JavaFile`, `FileUtil.getOrCreateDirectory`, `DownloadService.getDownloadState`), 138–169 ms. Debug-only policy. | POST_BETA | Yes | §I | Known; share the download-state fix with PB-1. |
| PB-5 | Accessibility | The Shuffle toggle's label is constant ("Shuffle") — on/off state is not exposed (Repeat does reflect state). | POST_BETA | Yes | §J | Add state semantics. |
| PB-6 | Playback | During an outage the next (uncached) track shows an indefinite buffering state with no message for 35 s+; it **recovers by itself** when the network returns. | POST_BETA | Yes | §H | Same fix family as BF-5/BF-6. |
| PB-7 | Docs | `CHANGELOG.md` `[Unreleased]` does not mention the Compose migration, downloads/offline UX, Equalizer, sheets. | POST_BETA | Yes | §L | Write before the beta tag. |
| PB-8 | Settings | `showConfirmationDialog` is a dead toggle (nothing reads it). | POST_BETA | Source | §N | Known. |
| PB-9 | Code health | #23 dead Artist/Album/legacy TrackCollection code; Videos unreachable; R5 legacy binders. | POST_BETA | — | §N | Known; pinned by `ResidualViewOverlayGuardTest`. |
| PB-10 | Performance | Home scroll on the **debug** build: 7 % janky frames, p95 32 ms (the main-thread disk reads in PB-4 contribute). | POST_BETA | Yes | §K | Re-measure on the release build. |
| PB-11 | Memory | PSS 249 → 298 MB over 6 navigation cycles (image caches). No crash/ANR/restart. | POST_BETA | Yes | §K | Watch on release; not a leak proof. |
| PB-12 | Privacy | `allowBackup` includes preferences and the per-server metadata caches (credentials DB is excluded). | POST_BETA | Source | §M | Accepted trade-off; revisit if prefs ever hold secrets. |
| ACC-* | — | See §D.1. | ACCEPTED | — | — | — |
| NR-* | — | See §D.2. | NOT_REPRODUCED | — | — | — |

### D.1 Accepted

- Cleartext HTTP + user CAs trusted: deliberate, for self-hosted servers (pinned by a test).
- Six exported components: each is intentional (launcher, media browser service, media button,
  two broadcast receivers, widget artwork provider whose path handling was hardened in 2026-08).
- Portrait-only: rotation is unsupported by design, so no recreation path exists to test.
- Cold start 1.2–2.4 s and warm 89 ms (debug, unoptimised).
- No touch target under 48 dp on 10 screens (the one 46 dp node is a half-visible shelf item).
- Font scale 1.5 on Home, Now Playing, Library hub, Add library: no unusable clipping.
- Playback survives screen-off, background/foreground, notification shows prev/seek/next/like/shuffle.
- Network outage: playback of downloaded content continues offline; streaming resumes on its own
  after the network returns.
- Lyrics: synced highlight, "No lyrics available" and next-track switch while open, Back.
- Sleep Timer sheet / Equalizer: opened, cancelled, nothing armed, EQ file byte-identical.
- Dumped `StrictMode` entries are all the known category (PB-4).
- Release APK: signed, not debuggable, minified, version `0.1.0-beta` (131).

### D.2 Not reproduced / not exercised

- **#22** Album List folder reload — the server exposes a single music folder, so no folder
  selector exists to reproduce with.
- **Crash / ANR** — none in the whole session (PID stable across lifecycle loops).
- **App-wide error sheet** — needs a real transport failure; unit-tested
  (`ErrorMessageChannelTest`), not provoked live (would mean changing device connectivity beyond
  the temporary outage; the outage surfaced no transport error, only BF-6).
- **Remove-download confirmation** — no fully-downloaded playlist exists on the device.
- **Recent-search "Clear all"** — would destroy the user's history; not pressed.
- **Successful server connection** during onboarding — would create configuration.
- **`deleteMetaDatabase` live** — destructive; source reasoning + the existing unit test only.
- **Create Playlist "By Genre"** — same picker code as the artist/genre pickers verified live.

---

## E. Interaction / Back / sheets

`SheetInteractionAuditTest` runs the same two probes against **every** reachable sheet:
**tap-through** (a tap on the panel's non-interactive top edge) and **composable-level Back**.
The result is pinned as *current behaviour*, defects included, so the build stays green and the
table is executable evidence (when a sheet is fixed its row flips and the test must be updated).

| Sheet | Panel tap dismisses? | Back handled by composable? | Live confirmation |
|---|---|---|---|
| Add to playlist, Album info, Playlist info, Update playlist info, Library hub, App error (all `TakiSheet`) | no | yes | 5A6 |
| Rename playlist, Create playlist name | **yes (BF-1)** | yes (fixed 5A6) | 5A6 / test |
| Settings choice | **yes** | **no (BF-2)** | tap title, tap blank, Back → leaves Playback |
| Settings confirm (Clear all downloads) | **yes** | **no** | tap message, Back → leaves Downloads; **OK never pressed** |
| Settings info | **yes** | **no** | test |
| Server delete | **yes** | **no** | tap message, Back → leaves selector; **server intact** |
| Discard server changes | **yes** | **no** (host `requestBack` re-raises it: Back cannot cancel, only the Cancel button) | tap message dismisses; Back no-op |
| Save playlist | **yes** | **no** | tap title, Back → leaves Now Playing; **Save never pressed** |
| Sleep timer | **yes** | **no** | tap subtitle, Back → leaves Now Playing; **no timer armed** |
| Equalizer preset | **yes** | no (the fragment's own callback handles Back — verified) | tap title dismisses; Back closes only the sheet |

None of these can cause a destructive action (a stray tap or a Back always *cancels*); the harm is
inconsistency and lost input (a name typed into the Save-playlist or Rename sheet is lost on a stray
tap on the panel; the 5A6 sheets, including Update information, are not affected). Remaining overlay properties:

- **Informational/disabled rows inert** — Library hub's current-library row verified inert (live).
- **Actionable controls fire once** — covered by 5A6 tests for the new sheets.
- **Stale target** — verified live for the playlist delete/info sheets (second playlist shows its
  own name).
- **Obscured by chrome** — Settings, server and hub sheets clear the mini-player/bottom nav;
  Now Playing sheets have no chrome.
- **Long content scrolls** — Genre picker (hundreds of rows) scrolls inside the 75 % cap.
- **Anchored menus** (sort menu, track/overflow menus): open, select, Back dismisses (5A6 and this
  crawl).

---

## F. Core user journeys

| Journey | Result |
|---|---|
| Cold/warm launch | OK. 1.2–2.4 s cold, 89 ms warm (debug). Existing server → Home directly, no onboarding, no blank frame seen. |
| Onboarding (Add library) | Form renders at 1.0× and 1.5×; untouched Back leaves directly; dirty Back raises the discard sheet; "leave" discards; **no junk server created** (verified). URL field keeps/normalises `http://`. Failed-connection and success paths not run (would create configuration). |
| Server selector | Lists Offline + the server (Active); Edit/Delete menu; delete sheet names the server; **never confirmed**; server still present afterwards. |
| Home | Loads, pull-to-refresh, Daily Mix, shelves, hub. |
| Library | Liked Songs, Liked Albums, Playlists, Downloads, Albums, Artists, Songs, Genres, Box Sets: every one opens, loads and backs out, 0 crashes. |
| Search | Empty (recents), query, results (artists/albums/songs), no-results state, very long query, Back. Tapping a *song* result plays it (by design). |
| Album Detail | Online, downloaded; hero, metadata, ✓ (downloaded mode only, PB-1), Info sheet, track menu, Add to playlist, Back. |
| Artist Detail | Hero, Play, Radio, Download, Popular, Albums; Radio plays (BF-3). |
| Playlist Detail | Header menu, rename/delete confirm (cancel), add to playlist, Back. |

---

## G. Playback, mini-player, Now Playing, Up Next, Lyrics, Sleep Timer

- Pause/resume, next/previous, repeat (Off → Song → All), shuffle, like/unlike (server `star` /
  `unstar` observed, net zero), drag-seek and tap-seek while playing, background/foreground,
  screen off/on (playback continued; the PID never changed), media notification controls.
- **PB-2** Seek is ignored while paused (position unchanged; by design in `NowPlayingSeekBar`).
- **BF-5** Reproduced with a server-side-unplayable radio filler track: `ExoPlaybackException:
  Source error` → state `ERROR`, UI shows 0:00 and a Play icon, no message. **Next** moves the UI
  selection but the player stays in `ERROR` (no `stream.view` request) until Play is pressed; Play
  then retries and plays the next track.
- **BF-4** Up Next: reorder works with shuffle off (verified). With shuffle on, dragging row 1
  down two positions left row 1 in place and swapped in a *different* track — the wrong items move.
  The source confirms it (`MediaPlayerManager.moveItemInPlaylist`: "currently does not care about
  shuffle position").
- Lyrics: synced lines + highlight; switches track and shows "No lyrics available" for an
  unsynced classical track; Back works; mini-player is hidden there by design.
- Sleep timer: sheet opened/cancelled; state "No active timer" confirmed before and after.

---

## H. Offline / downloads / network

- **Downloads screen**: lists Cheese (12), Electric Messengers (3); delete (trash) was **not**
  pressed.
- **Offline playback**: with Wi-Fi and mobile data both disabled (`svc`), "Play this album" on a
  downloaded album starts and keeps playing from the local cache (no `stream.view` request).
- **Real outage, streaming**: the already-buffered track kept playing; pull-to-refresh and the
  Albums list failed after the 10 s connect timeout. **BF-6**: Albums showed "No media found" with
  no error; no sheet/toast anywhere. **PB-6**: skipping to an uncached track buffered
  indefinitely (35 s+). **Recovery**: within 12 s of re-enabling the network the track started
  playing by itself; no stale error overlay; no crash.
- **PB-1**: online Cheese (all 12 downloaded) shows no ✓ and the plain download icon.
- The error *sheet* path (`CommunicationError` → `ErrorMessageChannel`) did not trigger: the
  failures above are caught by the Compose ViewModels/ExoPlayer and never reach it.
- Network restored exactly (Wi-Fi on, mobile data on) — see §Q.

---

## I. Lifecycle / stability

- 6 iterations of: bottom-nav cycling, forward/back through Albums and Playlists,
  Home→foreground, Now Playing open/close → **same PID, 0 `FATAL`/ANR**.
- `NavController` logs only the benign "Ignoring popBackStack … not found on the back stack" from
  tab switching. No `IllegalStateException`, `ConcurrentModificationException`,
  `FragmentManager`, `Room`/`NetworkOnMainThread`, `AudioEffect` or uncaught-coroutine entries
  (the Room `IllegalStateException` in Artist Radio is BF-3, caught and logged by the builder).
- Rotation/recreation: the activity is `screenOrientation=portrait`; not applicable.
- The only `StrictMode` entries are 7 × `DiskReadViolation` (PB-4), debug-policy only.

---

## J. Accessibility

- **Touch targets**: ≥ 48 dp on Home, Library (all 9 destinations), Search, Now Playing, Up
  Next, Lyrics, Settings, Equalizer (scan of every clickable node). The only sub-48 dp node is a
  half-visible shelf card at the screen edge. Sheet rows/actions are ≥ 48 dp (tests).
- **Semantics**: sheets expose a heading and labelled scrim; the Library hub's current-library
  row is exposed disabled; destructive actions are labelled "Delete".
- **PB-5**: Shuffle's content description never changes (state not exposed); the heart's label does
  change (Like/Unlike).
- TalkBack traversal order was not exercised.

---

## K. Performance

Debug build, Pixel 7, `dumpsys gfxinfo`:

| Scenario | Frames | Janky | p50 / p90 / p95 |
|---|---:|---:|---|
| Library scroll ×4 | 79 | 5.1 % | 5 / 7 / 23 ms |
| Home (artwork-heavy shelves) ×12 | 631 | 7.1 % | 6 / 18 / 32 ms |

Memory (PSS): 249 MB → 298 MB after the stress loop (Java 63 MB, native 37 MB, graphics 102 MB at
sampling). No process restart, ANR or jank loop. These are debug-build numbers with the known main-thread
disk reads (PB-4) — **re-measure on the release APK**; no speculative optimisation was done.

---

## L. Release hygiene

| Check | Result |
|---|---|
| applicationId / version | `io.github.churipakinti.taki` / `0.1.0-beta` (131); debug adds `.debug` |
| Build types | release: `minifyEnabled`, `shrinkResources`, R8 rules present; not debuggable |
| Signing | release APK signed (v2); `keystore.properties` is git-ignored and not tracked; absent keystore → unsigned APK with a build warning (documented in `docs/RELEASE_SIGNING.md`) |
| Permissions | exactly the ten audited (BT, internet, network state, notifications, wake lock, audio settings, foreground service ×3) — pinned by test |
| Exported components | launcher activity, `PlaybackService`, `UltrasonicIntentReceiver`, `BluetoothIntentReceiver`, `MediaButtonReceiver`, `AlbumArtContentProvider` — pinned by test; `DownloadService`, `SearchSuggestionProvider` are not exported |
| Cleartext / network config | cleartext allowed globally, user CAs trusted — deliberate for self-hosting (ACCEPTED) |
| HTTP/Timber logging | OkHttp request logging and `DebugTree` only when `BuildConfig.DEBUG` (pinned by test) |
| Backup | credentials DB excluded from cloud and device transfer; prefs + metadata caches included (PB-12) |
| Changelog / About | About shows the version; **PB-7**: `CHANGELOG.md` `[Unreleased]` is stale |
| ProGuard | rule files for okhttp/retrofit/jackson/kotlin/coil present; release builds and `lintVitalRelease` pass |

---

## M. Privacy / security

- **BB-1** (see §D): plaintext password in `ServerSetting.toString()`, logged at three sites.
  Release exposure requires "debug log to file" (opt-in) — which is exactly the setting a beta
  tester is asked to turn on to attach a log. Also visible in `logcat` on debug builds.
- OkHttp request logging includes the auth query parameters (`u`, `t`/`s` token+salt, or `p=enc:`
  hex of the password for legacy-auth servers) — **debug builds only**, impossible in release
  (`debug = BuildConfig.DEBUG`). Not a release defect; do not paste debug logcat in public issues.
- `Util.dumpSettingsToLog` writes `SharedPreferences` to the log; no credential is stored in prefs.
- `AlbumArtContentProvider` (exported): path restricted to a 32-hex key + `SUFFIX_LARGE`, known
  (id, key) pairs only, canonical-containment check — the 2026-08 hardening is intact.
- Self-signed-certificate setting and cleartext are explicit user/server choices (ACCEPTED).

---

## N. Known-debt review

| Item | Status now | Classification |
|---|---|---|
| #21 Artist Radio StrictMode | **Reproduced** from Artist Detail: `IllegalStateException: Cannot access database on the main thread` in `CachedMusicService.getAlbumsOfArtist`, swallowed; radio plays but is degraded (3 seed candidates, 60 random filler). Not a crash, not silent-total-failure. | BETA_FIX (**BF-3**) |
| #22 Album List folder reload | Not reproducible: single music folder, no selector. | NOT_REPRODUCED |
| Online per-row download indicator | Reproduced (PB-1). | POST_BETA |
| Up Next shuffle/reorder | Reproduced (BF-4). | BETA_FIX |
| `deleteMetaDatabase` quirk | Source + unit test: non-active delete wipes the *active* cache and orphans the deleted one's; cache only. | POST_BETA (PB-3) |
| Settings `showConfirmationDialog` | Still dead; the destructive "Clear All Downloads" confirm is unconditional (so the toggle never weakens it). | POST_BETA (PB-8) |
| #23 dead cleanup | No runtime risk. | POST_BETA |
| Videos | Unreachable; unchanged. | POST_BETA |
| #17 Hi-res FLAC stall | Not exercised (no hi-res file queued); open issue. | out of audit scope |
| #24 Visualizer, #20 Share | Out of scope. | — |

---

## O. Visual findings deferred to #26 (not beta-relevant)

- Library hub / sheets: generous empty space below short sheets (panel extends behind the
  mini-player by design).
- Home: horizontally scrolling chip row clips "Songs" at 1.5× font (still scrollable).
- Now Playing: seek thumb/track alignment while buffering (thumb at 0:00 without a buffered bar).
- Playlist "Partially downloaded" caption density; Settings group titles.
- Update-information form uses plain outlined fields (no capitalisation hints).

---

## P. Automated gates and audit tests

New tests (no product code touched):

| Test | What it proves |
|---|---|
| `SheetInteractionAuditTest` (16) | the per-sheet tap-through / Back matrix of §E |
| `ReleaseHygieneInvariantsTest` (9) | identity, signing path, minify, exported set, permission set, backup exclusions, cleartext pin, debug-only logging |
| `ServerSettingCredentialExposureAuditTest` (2) | BB-1, as executable evidence (flips when fixed) |

**Gates** (run on the audit commit): `testDebugUnitTest` **1497 tests, 0 failures, 0 skipped** (1470 + 27 new), including Roborazzi verify, `ArchitectureGuardTest`, `TakiTokensTest`, `NavigationChromeSelectionTest` and `ResidualViewOverlayGuardTest`; `lintDebug` and `lintVitalRelease` no new issues (baseline untouched); `assembleDebug` and `assembleRelease` green; detekt **42** (= baseline). Canonical matrix 27/0/0 and 0 reachable View overlays unchanged.

---

## Q. Pixel 7 results

**Before**: one server (`100.80.152.121`, plus the built-in Offline library); font scale 1.0;
Wi-Fi on / mobile data on; EQ file byte-identical to the 5A5 original; no sleep timer; paused
queue; Playlists list = 3 (see below).

**Exercised (non-destructive)**: all of §E–§K. Destructive confirmations were opened and
**cancelled only** (clear downloads, delete server, delete playlist, save playlist, update info);
no playlist or server was created, renamed, changed or deleted by the audit.

**After**: playback **paused**; EQ file **byte-identical** to the original; font scale restored to
1.0; Wi-Fi and mobile data restored (verified); **no sleep timer active**; one server, unchanged;
no extra server (the add-library form was discarded); no downloads queued or removed (Cheese and
Electric Messengers still listed); liked state of the two tracks I liked was toggled back (net
zero). The previously paused queue could not be restored exactly — the Artist Radio test replaced
it (the current queue is a radio mix, paused).

**One observation outside the audit's control**: the server now lists **3** playlists (Never
Played, Random Mix, Recently Added); at the end of phase 5A6 (2026-10-04) it listed 8 (including
two `2026-08-10` playlists, `Tesr`, `Test_2`, `Top Played`). Nothing in this audit confirmed any
deletion (every destructive sheet was cancelled, and the 5A6 post-check matched at the time), so
this is a server-side change that happened between sessions — most likely the owner tidying test
playlists on the server. Flagged here so it is not mistaken for audit damage.

---

## R. Remediation order (`BETA_READY = NO`)

1. ~~**BB-1**~~ — **RESOLVED** (#27, see §S).
2. ~~**BF-1 + BF-2**~~ — **RESOLVED** (#28, see §T).
3. **BF-3 (#21)** — build Artist Radio on an IO dispatcher.
4. **BF-5** — surface a playback-source error and let Next prepare the next item from `ERROR`.
5. **BF-6** — make list screens distinguish a load failure from an empty result and offer retry.
6. **BF-4** — guard or fix Up Next reordering while shuffled.

Then rerun the affected 5B checks (the tests above plus the live items) and re-issue this verdict.
Everything in §D marked POST_BETA is acceptable for a first beta.

---

## S. BB-1 remediation (#27) — RESOLVED

**Root cause.** `ServerSetting` is a Kotlin `data class`, so its generated `toString()` printed the
plaintext `password` (and `userName`). `ServerSettingsModel` interpolated whole objects into Timber
at three sites (`updateItem`, `saveNewItem`, `reindexSettings`), and `ActiveServerProvider.getActiveServer`
logged the whole cached `ServerSetting` on every cache miss (a fourth site the original audit missed).
Timber reaches logcat in debug builds and `FileLoggerTree` (the shareable "debug log to file") in release.

**Fix (defence in depth).**
- Layer A: the four sites now log only ids (`updateItem ... id: N`, `reindexSettings ... id: N index: M`,
  `getActiveServer ... found: true|false`).
- Layer B: `ServerSetting.toString()` is overridden to `ServerSetting(id, index, name, password=<redacted>)`.
  No length, hash or fragment. Equality, `hashCode`, `copy`, `componentN`, Room mapping and persistence unchanged.
- Related credential path (same logging family, narrow): the debug-build OkHttp `HttpLoggingInterceptor`
  logs the request line, which carries `t`/`s` (or legacy `p`). It now uses `redactQueryParams("p","t","s")`.
  Reachable only with `debug = BuildConfig.DEBUG` (never release), but debug logcat could be pasted into reports.
  `SubsonicClientConfiguration` (data class holding the password) also got a redacting `toString()`; it is not logged anywhere today.
- Field classification: `password`, `t`, `s`, `p` = SECRET; `userName`, `url` = SENSITIVE_CONTEXT (no longer logged by these paths);
  `id`, `index`, `name` = SAFE_IDENTIFIER.
- Exceptions: connection-test failures are logged via `Timber.w(exception)` in `EditServerViewModel`; OkHttp/Retrofit
  exception messages carry host/status only, never the query string, so no concrete exposure was found.
- Not changed: Room schema, storage format, authentication, onboarding, debug-log-to-file feature.

**Tests.** `ServerSettingCredentialExposureAuditTest` rewritten (2 → 7) as a permanent regression using a
unique sentinel password and a capturing Timber tree (the same messages `FileLoggerTree` receives):
`toString` redaction, equality/copy preserved, interpolation, `updateItem`, `saveNewItem`, `reindexSettings`
(via `getServerList`), `ActiveServerProvider`. Verified against the old code: 6 of 7 fail without the fix.
`HttpLoggingCredentialRedactionTest` (2, core) proves `p`, `t`, `s` are redacted in HTTP logging.
`testDebugUnitTest`: 1497 → 1502 (0 failures, 0 skipped); core adds 2.

**Source audit.** Remaining `Timber` matches near server objects/credentials: `ServerSelectorViewModel` and
`ActiveServerProvider` log ids only; `EditServerModel`/`RESTMusicService` log API version; `PerfMetricsInterceptor`
logs method + path only. No `$serverSetting` / `$setting` interpolation remains.

**Live (Pixel 7).** Debug APK installed over the existing install; cold start logged
`getActiveServer retrieved from DataBase, id: 1 found: true`; 94 request lines showed `t=██&s=██`; zero
`password=` / `ServerSetting(` lines. No server was edited, added or deleted and no preference was changed
(so the production server, its password and the debug-log preference are untouched). The real password was never
read or echoed; the sentinel tests are the authoritative proof.

**Status: BB-1 = RESOLVED. `BETA_READY` remains NO** pending #28, #21, #30, #31, #29 and the 5B revalidation.

---

## T. BF-1 + BF-2 remediation (#28) — RESOLVED

**Exact matrix** (`SheetInteractionAuditTest`, 16 rows + a hidden-state probe): 6 `TakiSheet` sheets already correct (Add to playlist,
Album info, Playlist info, Update playlist info, Library hub, App error) and **10 older sheets fixed**:

| Sheet | Host | Back owner before → after | Panel tap before → after |
|---|---|---|---|
| Settings choice / confirm / info | Settings | none → sheet (`TakiBackHandler`) | dismissed → inert |
| Server delete | Server Selector | none → sheet | dismissed → inert |
| Discard server changes | Edit Server | host `requestBack` re-raised it → sheet (registered later, so it wins) | dismissed → inert |
| Save playlist, Sleep timer | Now Playing | none → sheet | dismissed → inert |
| Rename playlist, Create playlist name | Playlist detail / list | sheet (5A6) → sheet | dismissed → inert |
| Equalizer preset | Equalizer | **host** (`EqualizerFragment` callback) → host (intentionally retained) | dismissed → inert |

**Root cause.** These sheets were hand-built (full-screen box, manual scrim, manual panel) before 5A6 introduced `TakiSheet`; the
panel had no pointer handler, so a tap on a non-interactive area reached the scrim `clickable`, and only some had a Back handler.

**Implementation.** Shared: new `Modifier.takiSheetPanelTapSwallow()` in `TakiSheet.kt` (now also used by `TakiSheet` itself).
Per sheet: apply it to the panel and add `TakiBackHandler` (`enabled = visible` for the always-composed animated sheets, so a hidden
sheet never intercepts Back). No visual, inset, content or callback change. Not migrated onto `TakiSheet` itself: titles, sizing and
bottom insets differ and a rewrite would be visual churn outside this issue. Equalizer keeps its host-owned Back (correct, verified
live) to avoid a second, competing handler.

**Tests.** `SheetInteractionAuditTest` is now the permanent matrix: every row asserts panel tap = no dismiss, scrim tap = exactly one
dismiss, Back = exactly one dismiss (Equalizer: 0 at composable level by design), plus hidden animated sheets do not intercept Back.
New `EditServerDiscardBackTest`: dirty form → discard sheet → Back closes only the sheet, stays on Edit Server, form intact, host
re-asks on the next Back, panel/scrim/Discard behave. `EqualizerScreenComposeTest` scrim test now taps the exposed scrim (it had relied
on the tap-through). 1502 → 1505; 12 of 19 new/changed assertions fail on the pre-fix code.

**Source audit.** All 16 sheets are in the matrix; `REACHABLE_SHEET_TAP_THROUGH = 0`, `REACHABLE_SHEET_BACK_ESCAPE = 0`.

**Pixel 7.** Settings choice (Language), Settings confirm (Clear all downloads), Server delete, Edit Server discard, Sleep timer,
Save playlist, Equalizer preset, Create playlist name and Rename playlist: panel/title/message/gap taps keep the sheet open; Back
closes only the sheet (screen retained); scrim dismisses; no setting, server, EQ preset or timer changed; Settings info not reachable
safely (covered by the matrix). Edit Server: the dirty form survived Back/scrim; Discard left without saving (name unchanged).
**Incident:** a probe tap between the Save-playlist buttons landed on the Save button's padding and created a server playlist named
`2026-10-10` (3 songs). I identified it by creation time (1:39 pm, during validation) and deleted it; the server is back to its 3
playlists. Nothing else changed (playback paused, no timer, EQ = Normal, one server).

**Status: BF-1 = RESOLVED, BF-2 = RESOLVED. BB-1 remains RESOLVED. `BETA_READY` remains NO** pending #21, #30, #31, #29.
