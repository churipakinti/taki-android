# Taki post-#46 audit: queue persistence, local-path containment and public-beta inventory

Date: 2026-10-25. Baseline `198cdce2` (develop). Audit only: **no production code was changed.** Reproductions ran on a throw-away `Pixel_7` emulator with a fake Subsonic server and fixture files; the Pixel 7 was not touched (it was not even attached). No production server, Navidrome or Tailscale peer was contacted. Related: #47 (queue durability, already open and owning the fix), #46 (closed, not reopened), #36, #44, #45.

## 1. Queue persistence (Phase 1)

### 1.1 How the state is stored and restored (code trace)

| Step | Where | Behaviour |
|---|---|---|
| Write | `PlaybackStateSerializer.serializeNow()` -> `FileUtil.serialize()` | `ObjectOutputStream(FileOutputStream(File(context.cacheDir, "downloadstate.ser")))`: Java serialization of `PlaybackState(songs, index, position, shuffle, repeat)`. The stream is opened (and the file truncated) **before** the object is written; there is no temp file, rename, fsync or previous generation. Any exception is swallowed (returns `false`, a partial file may remain). |
| Triggers | `MediaPlayerManager` (queue/state changes), position checkpoints (rate-limited by `PlaybackStateWriteGate`: >= 3 s and >= 1 s of change), synchronous flush on shutdown (`serializeCurrentSessionSync`) | queue is capped by `MAX_QUEUE_SIZE = 100` around the current item, so the file is small (about 1.2 KB for 3 tracks) |
| Read | `PlaybackStateSerializer.deserialize()` <- `MediaPlayerLifecycleSupport.restoreLastSession()` | `FileUtil.deserialize()` returns `null` on a missing file or any `Throwable`; `restoreLastSession` then simply continues with an empty queue |
| Overwrite by design | `MediaPlayerLifecycleSupport` -> `startFresh()` (service-only Android Auto start) and any new playback | the next queue replaces the saved one; a single global slot, **not keyed by server** (tracks carry `serverId`, switching library keeps the live queue) |
| Class evolution | `serialVersionUID` fixed on `PlaybackState` and `Track` | adding a field is tolerated; removing or retyping one or changing a supertype would make old files fail to load silently |
| Same pattern elsewhere | `EqualizerController` -> `equalizer.dat` in `cacheDir` | user equalizer preset is in the same disposable location |
| Backup | `backup_rules.xml` includes only shared prefs and databases | the queue file is not backed up or device-transferred (cache is excluded by Android anyway) |

### 1.2 Failure modes (emulator, fixture data)

| ID | Scenario | Result | Evidence class |
|---|---|---|---|
| E1 | OS cache reclamation (`adb shell pm trim-caches`) after force-stop | `downloadstate.ser` **deleted**, cold start by media button: `onPlaybackResumption` fails with "Nothing to resume" | **Reproduced** (forced trim; spontaneous frequency not measured) |
| E1b | "Clear cache" in system settings | same mechanism as E1 (cache dir emptied) | Reasoned from E1 (not separately run) |
| E2a | file truncated to 900 of 1226 bytes | `Failed to deserialize` warning, no crash, no restore, queue silently empty | **Reproduced**, safe failure but unrecoverable |
| E2b | garbage file | same as E2a | **Reproduced** |
| E2c | valid file | `Restoring 3 songs`, index and position restored | **Reproduced** (round trip also covered by `QueueRestoreRoundTripTest`) |
| E3 | `SIGKILL` (`am force-stop`) 0-80 ms after a skip, 25 trials | 0 truncated or zero-length files | **Not reproduced**: the open-truncate-write window exists by construction but is tiny for a <= 100-track queue |
| E4 | app update with `adb install -r` | the cache file survived updates | **Observed** on the Pixel 7 earlier in the session (the queue file survived two `install -r` updates); not re-run today |
| E5 | process death / device reboot | file is on disk and reloads (E2c, cold start) | **Observed** |
| E6 | writing a blank/foreign queue over a valid one | happens by design when the user plays something else (fixture playback on 2026-10-25 did exactly this), no confirmation needed | **Observed**; this is the replace-queue contract, not a defect |

### 1.3 Was the 2026-10-25 loss a real persistence vulnerability?

**Mostly a test-procedure failure, plus a latent durability weakness.** The Stromae queue was overwritten by fixture playback (E6: normal behaviour) and the backup did not include `cache/`, so it could not be restored. That is not evidence that Android evicted anything or that the #46 patch deleted state. The audit does show a separate, real weakness: the resumable queue lives in a location the OS may reclaim (E1), is written non-atomically (E3 unproven in practice), and a corrupt or truncated file leaves no earlier valid generation (E2a).

### 1.4 Recommendation (no migration is justified beyond this minimal one)

Fix under the existing **#47**; do not open a new issue. Smallest change that closes the demonstrated gap:

1. Move the file to `filesDir` (app-private, not reclaimable). No database and no new dependency.
2. Write atomically: `androidx.core.util.AtomicFile` (already a transitive AndroidX dependency) or write `*.tmp` + `fsync` + `renameTo`, and keep the previous valid generation as `*.bak`.
3. Read order: current -> `.bak` -> legacy `cache/downloadstate.ser` (**migration**: write the new file first, verify it deserializes, then delete the legacy file; never delete before success).
4. Keep Java serialization and the fixed `serialVersionUID`s (changing the format is not required and would widen risk). Move `equalizer.dat` with the same helper.
5. Document the single-slot, not-per-server policy (queue persists across library switches; tracks carry `serverId`); no key change needed unless a defect is shown.
6. Test-automation hygiene: the backup/restore script must include `cache/` (specifically `downloadstate.ser`) and verify by restoring into a throw-away profile before any destructive session; matching database sizes is not proof.

Regression tests: deletion of the legacy and new locations (cache wipe survives), truncated current file falls back to `.bak`, leftover `*.tmp` is ignored, interrupted write leaves the previous generation intact (fake `AtomicFile`/failing stream), migration success and migration failure (legacy file kept), concurrent `serializeNow` + `serializeCurrentSessionSync`, equalizer round trip, `QueueRestoreRoundTripTest` unchanged.

**Not reproduced:** the "Home loading skeleton" seen on the phone after `adb install -r` in the aborted run (#47 item 8). On the emulator the same skeleton is the normal first-frame load state and completes in a few seconds (requests were served). No UI issue is warranted; the phone case used the real server and could not be investigated without touching it.

## 2. `CachedDataSource` path containment (Phase 2)

### 2.1 How a local path is built and opened

- `Track.toMediaItem()` sets the item URI to `"$id|$bitrate|$filePath"`; `filePath = FileUtil.getSongFile(track)`.
- `getSongFile` builds `musicDirectory.path + "/" + sanitized parts`. `fileSystemSafe` / `fileSystemSafeDir` neutralise `..`, `../` and listed unsafe characters, so server-supplied *names and paths* cannot climb. **One shortcut bypasses this**: `if (track.id.startsWith(dir)) return track.id` ("offline files have their path as their id") returns the id **unsanitised**.
- `CachedDataSource.open()` splits the URI on `|`, takes `components[2]` as `path`, and `FileUtil.resolveExistingFile` -> `Storage.isPathExists` -> `JavaFile.isPathExists = File(path).exists()` (default storage). There is **no canonical-path or root-containment check** and symlinks are followed. With a user-chosen storage location (`StorageFile`, SAF) lookups are by document tree, so the root is inherent.
- Foreign controllers can no longer supply this string (#46, `ControllerPolicy.maySupplyPlaybackSource`); the only remaining sources are Taki's own process: server-derived tracks, the offline database and the saved queue file (app-private).

### 2.2 Evidence

- Reproduced during #46 (own-controller path semantics): an absolute path was opened with Taki's identity.
- **Hypothesis tested here:** a forged server response with a track `id` of `<musicDir>/Fixture/../../…/data/data/<pkg>/files/private-fixture.wav` (to use the `id.startsWith(dir)` shortcut). **Not reproduced** on the emulator: the stream request shows the file check failed, because Android's per-app storage mounts stop `..` crossing from `/storage/emulated/0/Android/data/<pkg>` into `/data/data`. It cannot be ruled out where the music directory is on the same filesystem as sensitive files, and it does not require a malicious server in the sense of this audit: a forged response over the cleartext HTTP that Taki permits would be enough.
- Symlinks: not tested; app-specific directories can only contain links created by the app itself on current Android.

### 2.3 Recommendation

P2 hardening, **no new issue** (the need is not demonstrated). When convenient, add a single check at the one place that opens local files (or in `JavaFile.getFromPath`): resolve `File(path).canonicalFile` and require it to lie under `Storage.mediaRoot` (canonical); reject otherwise and fall through to the server stream. Also drop the `id.startsWith(dir)` shortcut's unsanitised return or sanitise it. Compatible with offline playback because every legitimate path is built under `musicDirectory`. Tests: `..` traversal, absolute outside path, symlink inside root pointing outside, legitimate nested path with spaces/unicode, `.complete.` and alternate-extension variants, custom-location (SAF) mode unchanged, offline album playback.

## 3. Risk matrix

| ID | Risk | Evidence | Probability | Impact | Priority |
|---|---|---|---|---|---|
| R1 | Saved queue lost when the OS/user clears cache | **Reproduced** (E1) | Medium over a long beta (storage pressure, "clear cache") | Medium: resumable queue and position lost, library untouched | **P1** (recommended before public beta; owned by #47) |
| R2 | Corrupt/truncated queue file leaves no valid fallback | **Reproduced** (E2a, safe failure) | Low (E3 not reproduced) | Low-medium | P1 as part of R1's atomic write |
| R3 | Non-atomic write interrupted by process kill | Reasoned, not reproduced (0/25) | Low | Low-medium | P1 (same change) |
| R4 | Equalizer preset in cache | Code + E1 mechanism | Medium | Low (preset reset) | P2 (same change) |
| R5 | Test workflows overwrite saved state without a complete backup | **Reproduced** (2026-10-25) | n/a (process) | Test-device data only | P1 process item in #47 |
| R6 | No root containment in `CachedDataSource` / `getSongFile` shortcut | Code; forged-id case **not reproduced** | Very low | Low (read/metadata oracle, no write) | P2 hardening |
| R7 | Network handoff stalls (#35) | User report, mitigations exist | Medium | Medium (audible gaps) | P1 (existing issue) |
| R8 | Stale media card in Quick Settings (#37) | Screenshots; Android-managed history suspected | Medium | Low (cosmetic/confusing) | P1 to classify, may become ACCEPTED |
| R9 | Real Bluetooth connect/disconnect after AA-05 not exercised | Gap documented | Low | Low | P1 release-checklist item (#44) |
| R10 | Android Auto after #46 on hardware | Judgement only (AA sends ids) | Very low | Medium | P1 release-checklist smoke test |
| R11 | Stale `CHANGELOG`, known POST_BETA debt (PB-1..13), AA-11/12/13/15 | Documented | n/a | Low | P2 |
| R12 | Hi-res FLAC follow-ups #41, #42 | Open, no stall reproduced | Low | Low-medium | P2 |

## 4. Beta blocker inventory (public beta)

Security gates #45 and #46 are closed with evidence. `PUBLIC_BETA_READY` remains NO.

- **P0 (blocks beta):** none demonstrated as a *defect*. The only gate that is still formally unmet is **#36** (listening audit: `LISTENING_EXPERIENCE_AUDIT_COMPLETE` / `BETA_PLAYBACK_READY` not yet produced). It is a process gate, and it is where #35 and #37 get classified.
- **P1 (resolve before public beta):** #47 (queue durability: R1-R3, R5; the small change in 1.4); #35 (Wi-Fi/mobile handoff stalls); #37 (classify the stale media card: fix or document as Android behaviour); release-checklist smoke tests: real Bluetooth connect/disconnect (R9) and Pixel 7 + DHU after #46 (R10); a current `CHANGELOG` for the first public build.
- **P2 (after launch or when convenient):** R4 equalizer location (can ride with #47), R6 path containment, #41/#42 FLAC follow-ups, AA-11/12/13/15 (#44), PB-1..PB-13 debt, #20 share card, #24, #23.

## 5. Issues

No new issue is warranted: the persistence work is already **#47** (this audit supplies its reproduction, scope reduction and tests), the containment hardening is hypothetical (documented here and offered as a P2 follow-up), and everything else maps to an existing issue. Comments were added to #47 and #36.

## 6. Minimum set of changes to reach a stable, safe, reliable public beta

1. #47: `filesDir` + atomic write + `.bak` + verified legacy migration (+ equalizer file), with the regression tests above, and a verified-restore backup step in the test workflow.
2. #36: finish the listening audit; fix or consciously classify #35 and #37.
3. Release checklist: real Bluetooth connect/disconnect smoke test, one short Pixel 7 + DHU run on the final candidate build, current `CHANGELOG`.
4. Optional, same release: root-containment check (section 2.3).

`POST46_AUDIT_COMPLETE = YES`

`PUBLIC_BETA_READY = NO` (blocked by #36 and the P1 items above; no security blocker remains known)


## 7. #47 implementation: durable playback state (2026-10-25)

Baseline `9189d686`. The saved queue (`downloadstate.ser`) and the equalizer preset (`equalizer.dat`) now live in `filesDir`, are written through `androidx.core.util.AtomicFile`, and are migrated once from the old `cacheDir` copy. The change is confined to `FileUtil.serialize/deserialize` (the only two callers are `PlaybackStateSerializer` and `EqualizerController`, so neither was edited), two backup XML files and tests. Serialization format, `serialVersionUID`s, the 100-track queue cap, file names and the Media3/Android Auto code are untouched.

### 7.1 Behaviour

- **Write:** `AtomicFile.startWrite` -> `ObjectOutputStream` -> `finishWrite` (fsync, then replace). An exception calls `failWrite`, which restores the previous generation; the call returns `false` and logs (file name only, never contents). After a successful write any leftover `cacheDir` copy is deleted, because the newer state supersedes it.
- **Read order:** current file (`AtomicFile.openRead` also repairs an interrupted write by restoring the backup generation) -> if absent or unreadable, the legacy `cacheDir` copy -> otherwise nothing. A missing, truncated or garbage file returns `null` with a warning; nothing crashes and nothing blocks.
- **Migration:** legacy file read -> new file written -> new file read back successfully -> only then the legacy file is deleted. Repeating it is harmless. If the new write fails, the legacy copy is kept (and still used) for the next start. If the new file is already valid, the legacy file is never read (no stale resurrection) and is removed. A corrupt legacy file is neither migrated nor deleted. Reads and writes share one lock.
- **Backup:** `filesDir` would otherwise be picked up by device-to-device transfer (it includes everything not excluded), so `backup_rules.xml` excludes both files for device transfer; cloud backup and the legacy descriptor only include shared prefs and databases and are guarded by a test. The queue still is not carried to another device (it refers to server ids that are not transferred either).
- **Single global slot, not per server (unchanged policy):** tracks carry `serverId`; switching library keeps the live queue; playing something replaces the saved queue by design.

### 7.2 Tests (16 new, 0 failures; 12 of the 15 `DurableStateFileTest` fail on the old code)

`DurableStateFileTest`: round trip of songs/index/position/shuffle/repeat; missing file; clearing the cache directory keeps the queue; valid legacy migrated and removed, repeatable; new state wins over a stale legacy copy; writing discards an unread legacy copy; corrupt legacy neither migrated nor deleted; failed migration keeps the legacy copy and succeeds on the next run; interruption after the new file was written; interrupted write (`startWrite` + partial bytes, no finish) keeps the last good state; a write failure keeps the last good state; truncated or garbage current file fails safely; an empty queue never brings back an older one; the real `PlaybackStateSerializer` through the durable file; the equalizer preset (same helper, migration and cache wipe). `ReleaseHygieneInvariantsTest`: backup rules keep the state files out of device transfer and never add the `file` domain to cloud/legacy includes.

### 7.3 Validation

- Automated: **1889 tests, 0 failures** (1873 before + 16); `assembleDebug`, `assembleRelease`; `lintDebug` and `lintVitalRelease` report no new issues; `detekt` 42 (`ultrasonic`) + 1 (`subsonic-api`), identical to the baseline; `git diff --check` clean. Unchanged and passing: AA-05 `ControllerAuthorizationTest`, #46 `PlaybackSourcePolicyTest`, `QueueRestoreRoundTripTest`, the AA-01..AA-10 suites.
- Emulator (`Pixel_7` AVD, fake Subsonic server, fixtures; the Pixel 7 was not touched): the pre-#47 build wrote `cache/downloadstate.ser` (1634 bytes); upgrading with `adb install -r` left it in place until first launch, then `FileUtil: Deserialized object` and an identical 1634-byte `files/downloadstate.ser` appeared with the cache copy removed; `pm trim-caches` (the OS reclaim reproduced in section 1) emptied `cache/` but the queue survived and a cold start by media button logged `Restoring 3 songs` at the saved position and played; a second `install -r` kept the file. The queue of the same fixture session therefore reproduces E1 as fixed.
- Not exercised on hardware: Android Auto/DHU and offline playback were not re-run on a device; they are covered by the unchanged automated suites (the change does not touch playback, browse or session code).

### 7.4 Test-device backup procedure (for any session that switches servers or plays fixtures)

Back up **all** app state, not only databases: `databases/`, `shared_prefs/`, `files/` (now includes `downloadstate.ser` and `equalizer.dat`) **and, for installs older than this change, `cache/downloadstate.ser`**. Verify the archive by listing it and, when possible, by restoring into a throw-away profile before the destructive session. Matching database sizes is not proof of a full restore. Never test on the user's phone data without this.

### 7.5 Residual risks

- Two simultaneous process instances writing the same file are not coordinated beyond `AtomicFile` (not a supported configuration).
- A completed write that is later corrupted by the storage layer leaves no older generation (AtomicFile keeps one only during a write).
- Queue stays a single slot shared by all servers (policy above).

`ISSUE_47_ACCEPTANCE = MET` for the automated and emulator criteria; hardware smoke tests stay with the beta release checklist.

`PUBLIC_BETA_READY = NO` (unchanged: #35, #36 and the release checklist remain).
