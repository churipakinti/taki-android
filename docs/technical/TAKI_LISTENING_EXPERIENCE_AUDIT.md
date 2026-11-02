# Taki listening-experience audit (issue #36) — Phase 1 + Phase 2

Phase 1 = reconnaissance, automated audit, emulator validation and a prepared Pixel 7 protocol. Phase 2
(section 14) = the physical validation on the owner's Pixel 7, run on 2026-11-01. **No production code was
changed in either phase. No production server was contacted by the emulator work; the Pixel used the owner's
own library (normal scrobbling).**

## 0. Status

```text
LISTENING_EXPERIENCE_AUDIT_COMPLETE = YES  (A-E verified; block F and selected focus cases explicitly
                                            NOT_TESTED / UNAVAILABLE; residual risk accepted in 14.9)
BETA_PLAYBACK_READY                 = YES  (no PRE_BETA_BLOCKER / PRE_BETA_FIX; #35 risk accepted for beta)
PUBLIC_BETA_READY                   = NO   (belongs to the full release checklist)
```

Nothing found in Phase 1 or Phase 2 is a `PRE_BETA_BLOCKER` or `PRE_BETA_FIX`. The album-list crash (#50) and
the swipe-away stop (#49) are fixed, and #49 is now confirmed with a real finger swipe on the phone (14.5).
Unverified scenarios remain explicitly identified; audit closure reflects an owner-delegated beta-risk acceptance,
not a PASS for those scenarios. See section 14.9.

## 1. Baseline

| Item | Value |
|---|---|
| Branch / SHA | `develop` @ `063e8ef8` at the start (working tree clean, in sync with origin) |
| Media3 | 1.10.1 (`media3-exoplayer`, `-session`, `-datasource-okhttp`, `-inspector`); FFmpeg audio renderer via NextLib (same prefix) for hi-res FLAC |
| Automated | 1915 tests, 0 failures at `063e8ef8`; +5 new (section 4) |
| Emulator | `Pixel_7` AVD, Android 37, 1080x2400 @ 420 dpi, software GPU, no audio device (`-no-audio`) |
| Server | throw-away fake Subsonic server on the host (synthetic fixtures; a 90 s 330 Hz WAV, generated covers), reached over `adb reverse`; fault switch for HTTP status / black-hole / a permanently 404 track |
| Date | 2026-10-31 |

Evidence classes used below: `VERIFIED_AUTOMATED`, `VERIFIED_EMULATOR`, `VERIFIED_PHYSICAL`,
`PARTIALLY_VERIFIED`, `NOT_TESTED`, `KNOWN_DEFECT`. Dates and commits are those of the cited source.

## 2. Relevant architecture (from source, not assumptions)

| Concern | Where | Notes |
|---|---|---|
| Player / session | `service/PlaybackService.kt` (`MediaLibraryService`, one `MediaLibrarySession`, one ExoPlayer) | Declared `foregroundServiceType="mediaPlayback"` |
| Audio focus | **Media3**: `setAudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC, handleAudioFocus = true)` | Taki has no focus code of its own. Transient loss = *suppressed* playback with `playWhenReady` kept |
| Noisy output | **Media3**: `setHandleAudioBecomingNoisy(true)` | Headset unplug / A2DP drop pauses; no Taki code |
| Wake | `setWakeMode(LOCAL)` offline, `NETWORK` online | One partial wake lock (`ExoPlayer:WakeLockManager`) while playing |
| Stall watchdog | `PlaybackStallWatchdog` driven every 5 s by `MediaPlayerManager.checkForPlaybackStall` | Frozen position with data buffered ahead -> one +1 s recover, then escalate (skip/stop). **Ignores suppressed playback** (#34) |
| Network recovery | `PlaybackLoadControl` (30 s min buffer), `PlaybackLoadErrorPolicy` (429/502/503/504 retried in place 1/2/4/8 s, `Retry-After` capped 10 s), #19 re-prepare 2/4/8 s for `IO_NETWORK_*` | #35 |
| Error contract | `PlaybackErrorRecovery`, `ErrorMessageChannel` | One actionable sheet per failure; Next after an error prepares the next item (#30) |
| Queue / persistence | `PlaybackStateSerializer` -> durable `files/` + `.bak` (#47) | Saved queue, index, position; restored on resumption/cold start |
| Offline | `CachedDataSource` + local files; `ActiveServerProvider.isOffline()` | Downloaded tracks play from disk with no HTTP |
| MediaSession / controls | `MediaLibrarySessionCallback` (controller authorization #45/#46), `BluetoothIntentReceiver` (A2DP connect resumes, disconnect pauses) | Media3 handles media buttons |
| Hi-res FLAC | `HiResFlacRenderers` (NextLib FFmpeg) | #17 |
| Sleep timer | `SleepTimerController` | Not touched by #48 |
| Removed from Recents | `PlaybackService.onTaskRemoved` + `shouldContinuePlaybackAfterTaskRemoved` (#49) | Continues while the user wants playback; finishes cleanly otherwise (section 12). Before #49 it always stopped playback |

## 3. Existing evidence (what is already verified, not repeated here)

| Area | Evidence | Class | Source / commit / date |
|---|---|---|---|
| Audio focus false stall | Watchdog ignores suppression; Pixel clock-timer 60 s hold: same item, no watchdog, resumed; #34 closed | VERIFIED_PHYSICAL + AUTOMATED | `abca8979` (2026-10-12); `PlaybackStallWatchdogTest` (18) |
| Buffering policy | Min ahead 1 s -> 30 s; zero time below 10 s ahead | VERIFIED_PHYSICAL | `b67f9cb9` (2026-10-13), diagnosis doc "Phase 2B" |
| Transient HTTP statuses | Reproduced (4 requests then permanent IDLE) and fixed; 503 20 s resumes | VERIFIED_EMULATOR + AUTOMATED | `cc7837af` (2026-10-26) |
| Network handoff, clean | Wi-Fi<->mobile clean switches; total outages 47/123/180 s recover 0-1 s after network returns; D plays, C never starts | VERIFIED_PHYSICAL | `4a220f29` (2026-10-31), diagnosis doc "Phase 4" |
| Degraded-but-associated Wi-Fi | Cannot be produced from USB | NOT_TESTED (field) | #35 open |
| Hi-res FLAC | 24/192 streamed past 154 s in 4 runs; 57 min locked-screen soak (11 tracks), no pauses/underruns/watchdog; format transitions; release build 13 min locked | VERIFIED_PHYSICAL | `6364d415` (2026-10-18), FLAC doc §21-22; #17 closed |
| Hi-res open items | 154.2 s FFmpeg packet message (no audible effect), 24-bit->16-bit output | KNOWN_DEFECT (low) | #41, #42 |
| Offline | Downloaded album: zero HTTP online and in airplane mode; hi-res offline verified | VERIFIED_PHYSICAL | `b67f9cb9`; FLAC doc §18 (2026-10-17) |
| Download integrity | Transcoded Opus no longer saved as `.flac`; safe partial resume | VERIFIED_PHYSICAL | #39 `65de5da6`, #40 `af880413` |
| Error contract | One error sheet; Next re-prepares; mini-player after error (#32) | VERIFIED_PHYSICAL + AUTOMATED | beta audit §V (2026-10-11); `PlaybackErrorRecoveryTest` (14) |
| Persistence | Queue survives cache reclaim, atomic write, migration; equalizer too | VERIFIED_AUTOMATED + EMULATOR | `e19f1579` (#47, 2026-10-25), post-#46 audit §7 |
| Controller security | Untrusted controllers cannot rate/star; only own UID supplies a source URI | VERIFIED_PHYSICAL + EMULATOR | #45 `f8417b5f`, #46 `447148d8` |
| Android Auto | 16 of 20 scenarios pass, 4 partial, no failures (Pixel + DHU) | VERIFIED_PHYSICAL | `bb63ae60` (2026-10-25); P3 items in #44 |
| Bluetooth A2DP | Receiver logic and emulator relay; **no hardware connect/disconnect** | PARTIALLY_VERIFIED | `BluetoothIntentReceiverTest` (3); #44 |
| Notification / lock-screen / media-key / seek | Behave correctly, user pauses not auto-resumed | VERIFIED_PHYSICAL | FLAC doc §21.4 (2026-10-18) |
| Stale Quick Settings card | Diagnostic issue, not reproduced as a Taki bug | NOT_TESTED / OPEN | #37 |

## 4. Automated audit

Full suite at `063e8ef8`: **1915 tests, 0 failures, 0 skipped** (211 classes). The playback-relevant classes
total about 405 tests: Android Auto (7 classes, ~97), controller authorization (14), stall watchdog (18),
error recovery (14), load control (8) and load-error policy (8), network recovery delay (6), resumption (4),
restore position (6), infrastructure start (11), queue round trip (5), state write gate (8), durable state
file (17), equalizer persistence (6), download resume (17) and deletion consistency (8), hi-res renderers
(11), transition routing (9), sleep timer controller (15), Bluetooth receiver (3), source policy (9), replay
gain (25), shuffle order (6).

**Gap found and closed (small, deterministic, no production change):** audio focus and "audio becoming
noisy" are implemented by two Media3 builder flags that nothing else in the suite would notice losing (every
other test starts from an already-running player). New `PlaybackPolicyInvariantsTest` (5 tests) pins, from
the checked-in sources: `setAudioAttributes(..., true)`, `setHandleAudioBecomingNoisy(true)`, the wake mode,
music audio attributes, and the `mediaPlayback` foreground-service type. It does not claim to test Media3.

**Gaps that stay open (not testable without a player or hardware):** real focus loss/regain; headset-unplug
pause; A2DP connect/disconnect on hardware; Doze/battery; degraded Wi-Fi; `onTaskRemoved` behaviour.

## 5. Emulator validation (2026-10-31)

Method: Taki's debug build on the `Pixel_7` AVD against the fake server. A host probe polled
`dumpsys media_session` (Media3 session state) and, in some runs, `dumpsys media.audio_flinger` ("Frames
written" of the output thread). Every action is stamped with the host clock; the server logs each request in
epoch milliseconds. All delays below are **loopback on an emulator without an audio device**.

**Latency definitions.** Server first response = first `stream` request seen by the server. Session
`PLAYING` = Media3 `isPlaying` (`READY` and play intent), i.e. an approximation of `STATE_READY`. Output frames
advance = the AudioFlinger output counter moved, i.e. data reached the output thread. **None of these is
"first audible audio"**: the emulator has no audio device and the session position only refreshes every few
seconds. First audible audio is therefore `NOT_VERIFIED` here and stays a Pixel 7 measurement.

| # | Scenario | Result (observed) | Class |
|---|---|---|---|
| E1 | Pause / resume | Pause -> session `PAUSED` +0.09 s; Play -> `PLAYING` +0.16 s, output frames +0.29 s | VERIFIED_EMULATOR |
| E2 | Tap a track while paused | first stream request +0.18 s, `PLAYING` +0.23 s | VERIFIED_EMULATOR (loopback) |
| E3 | Next / Previous / seek | Next and seek keep `PLAYING`; Previous to the prior track shows `BUFFERING` +0.36 s then `PLAYING`; no error, no skipped track | VERIFIED_EMULATOR |
| E4 | Permanent HTTP 404 on the first track | 4 stream requests in ~3.1 s, then `ERROR`, **one** error sheet ("Couldn't play this track..."), queue kept (#30) | VERIFIED_EMULATOR |
| E5 | Next after E4 with play intent | next track `PLAYING` +0.22 s (1 stream request) | VERIFIED_EMULATOR |
| E6 | HTTP 503 for 8 s at track start (not cached) | requests +0.25 / +2.32 / +4.35 / +8.38 s (the 2 s `Retry-After` honoured), `PLAYING` +8.5 s, no error | VERIFIED_EMULATOR |
| E7 | Process killed (`kill -9`), then media key Play | session recreated, queue restored (index 1) at **exactly** the saved 17090 ms; `BUFFERING` +2.2 s, first stream +2.4 s, `PLAYING` +3.0 s (process start + restore ~1.3 s) | VERIFIED_EMULATOR |
| E8 | Incoming call (emulator GSM) while playing, 40 s hold | `PAUSED` +1.3 s after ringing; call ended -> `PLAYING` +0.49 s; same item, position 62390 -> 62424 ms; **0 stream requests, no watchdog, no skip, no error line** (8 watchdog ticks passed) | VERIFIED_EMULATOR |
| E9 | Downloaded album, server **stopped**, play | `BUFFERING` -> `PLAYING` in 0.9 s, no error | VERIFIED_EMULATOR |
| E10 | Offline natural transition (87 s -> next) and Next/seek | next downloaded track playing within the probe's ~3 s resolution, no `BUFFERING`/`ERROR` | VERIFIED_EMULATOR |
| E11 | 5 min playing with the screen off | `PLAYING` throughout; one partial wake lock (`ExoPlayer:WakeLockManager`); Java heap 38.3 -> 37.1 MB, native heap 27.6 -> 26.3 MB (PSS); server saw 3 stream + 6 scrobble requests (3 track changes), no retry storm | VERIFIED_EMULATOR (not a battery measurement) |
| E12 | Cold start with a restored queue (`am start`) | queue and position restored, paused until the user acts | VERIFIED_EMULATOR |

Observations that are **not** conclusions:
- After `am force-stop`, a media key did nothing (the package is in Android's *stopped* state, which blocks
  the broadcast). A process kill (E7) restores normally. An earlier audit saw a force-stop cold start work in
  a different setup, so this is recorded as platform behaviour, `ACCEPTED_AS_IS`, with a physical re-check
  (low priority).
- The swipe-from-recents check could not be driven in Phase 1 (the task switcher did not open), so it was
  `NOT_TESTED` there; it is now covered by section 12 (#49).

**Emulator limitations (explicit).** No real Wi-Fi/mobile handoff, no Bluetooth or wired route, no Doze or
battery accounting, no audible-output measurement, no real call/assistant audio stack beyond the telephony
simulator, loopback timings. The emulator does not validate any of the physical blocks in section 8.

## 6. Scenario matrix

Coverage: A = automated, E = emulator, P = physical. Priority is the classification if the scenario failed
(`Blocker` / `Fix` / `Post-beta` / `Accepted`), not a result. "Required" = which validation still has to run.

| Scenario | Expected | Existing evidence | Coverage | Risk | Required | If it failed |
|---|---|---|---|---|---|---|
| Start from Home/Library/Search/Album/Playlist/Downloads | Plays the tapped item with the right queue | `AlbumPlayDeterministicTest`, AA contextual tests; Pixel journeys (beta audit §F); E2 | A+P+E | Med | P: one pass per entry point inside block A | Fix |
| Play/Pause, Next/Prev, seek, queue edit | Immediate, no skip | Pixel (FLAC §21.4); E1/E3 | A+P+E | Low | none beyond A | Fix |
| Natural transition | Next track, scrobble, sleep-timer rules | `MediaItemTransitionRoutingTest`; Pixel (§21.5); E10 | A+P+E | Low | none | Fix |
| Compressed stream, cached/uncached start | Starts within a second on a good link | Pixel A/B (`b67f9cb9`); E2/E6 | P+E | Low | first-audible latency on Pixel (block A, optional metric) | Post-beta |
| FLAC / hi-res | No stall, right decoder | Pixel soak (`6364d415`); #41/#42 open | P | Med | A regression run on the final build (block A) | Fix |
| Buffering / temporary HTTP error | Retries in place, no error within budget | `cc7837af`; E6 | A+E | Low | none | Fix |
| Permanent error | One message, queue kept, Next recovers | beta audit §V; E4/E5 | A+P+E | Low | none | Fix |
| Recovery after an outage | Resumes without manual action | Pixel 47/123/180 s (`4a220f29`) | P | Med | #35 gaps only (block F) | Blocker if reproducible |
| Transient focus loss (call, other app, notification) | Suppressed, same item/position, resumes by itself | `PlaybackStallWatchdogTest`; Pixel clock timer; E8 | A+E+P(partial) | **Med** | P: real call, assistant, other media app (block B) | Blocker if it skips or loses position |
| Pause during an interruption / permanent loss | No surprise resume | Watchdog tests (intent dropped) | A | Med | P: block B | Blocker if it auto-resumes |
| Screen off / background / return | Keeps playing, state intact | Pixel 57 min soak, 13 min release (§21.3, §22); E11 | P+E | Low | 10 min locked in block A on the final build | Fix |
| Process death / restore | Queue and position restored, no unwanted play | #47 suites; E7/E12 | A+E | Med | P: one kill + resume in block C | Blocker if the queue is lost |
| Removed from Recents | Playing/buffering continue; paused/idle finish cleanly | #49 fix: A/B on the emulator (section 12), policy unit tests | A+E | Med | P: confirm on the Pixel 7 with a real swipe (block C) | Fix |
| Notification / lock-screen controls | Work, stale card not harmful | Pixel §21.4; #37 open | P | Low | observe #37 in block C | Post-beta |
| Bluetooth connect/disconnect, media buttons | Connect resumes, disconnect pauses | Receiver unit tests; emulator relay; **no hardware** | A+E | **Med** | P: block D | Fix |
| Wired headset unplug / output change | Pauses (Media3 noisy) | flag pinned by new test | A(config) | Med | P: block D if a headset exists | Fix |
| Android Auto | Browse, play, queue, reconnect | `bb63ae60` (16/20, 4 partial) | P | Low | Post-beta items in #44; one DHU run on the final build | Post-beta |
| Offline: play, seek, transition | Local files, no HTTP | Pixel (`b67f9cb9`, FLAC §18); E9/E10 | A+P+E | Low | 5 min airplane-mode regression in block E | Fix |
| Offline->online, unavailable item | Keeps queue, bounded error | Pixel (resolution timeout 20 s keeps queue); unit tests | P+A | Low | none | Post-beta |
| CPU / memory | Flat | Pixel 226-272 MB PSS over 57 min; E11 | P+E | Low | none | Post-beta |
| Wake locks | One partial lock while playing | E11 (`dumpsys power`) | E | Low | confirm released after pause (block A) | Post-beta |
| Battery | Reasonable | not measured; USB-powered device | NOT_TESTED | Low | optional unplugged run, not a gate | Post-beta |
| Network retries / writes | No storm; bounded | `PlaybackLoadErrorPolicyTest`; Pixel 180 s; E11 (3+6 requests / 5 min) | A+P+E | Low | none | Fix |
| Long disconnection | Bounded retries then one error | Phase 3 (70 s outage, 16 requests) | E | Low | none | Fix |
| Degraded-but-associated Wi-Fi | Recovers without long silence | **none** | NOT_TESTED | **Med-High** | P field walk (block F) | Blocker if a long silence is reproduced |

## 7. Findings and classification

**No `PRE_BETA_BLOCKER`.** Nothing reproducible loses data, blocks playback, skips unexpectedly or fails to
recover. F-1 (#50, album-list crash) and F-3 (#49, swipe-away) are fixed.

| ID | Finding | Evidence | Class |
|---|---|---|---|
| F-1 | **#50, fixed (section 13).** The Albums list crashed with `Key "<id>" was already used` when two pages of the paged load contained the same album; the UI and the playback service share one process, so the music stopped too. Reproduced with valid responses, then fixed by de-duplicating by album id in `AlbumListViewModel` | logcat before/after on the emulator, 2026-10-31 | `PRE_BETA_FIX`, **done**; physical confirmation is not needed for the list logic |
| F-2 | `#37` stale Quick Settings media card: Android-managed history is plausible; not examined here | #37 | `POST_BETA` unless it proves to be a live duplicate session |
| F-3 | Swiping Taki from recents **stopped playback and removed the notification** (inherited `onTaskRemoved`) | source + emulator A/B (section 12) | **Fixed in #49** (policy decided by the owner); **confirmed on the Pixel 7 with a real finger swipe (section 14.5)** |
| F-4 | #41 / #42 hi-res FLAC follow-ups (packet message at ~154.2 s, 24->16 bit output) | FLAC doc §21-22 | `POST_BETA` |
| F-5 | Force-stop then media key does not restart the app (emulator) | E-notes | `ACCEPTED_AS_IS` (Android stopped state) |
| F-6 | Time to first audible audio is not measured anywhere | this audit | metric gap, not a defect |

Related issues (none modified or closed): #17 (closed, FLAC), #19 (closed, retry), #30/#32 (closed, error
contract), #34 (closed, focus), #35 (open, field case), #37 (open), #45/#46/#47 (closed), #44 (open P3).

## 8. Pixel 7 protocol (executed in Phase 2, results in section 14)

### 8.1 Principles
- **Duration is justified by risk, not a fixed number.** The watchdog ticks every 5 s, the buffer holds 30-50 s,
  a typical track is 3-5 min, and a natural transition, an interruption and a reconnect each need a few
  minutes. The earlier 57 min locked soak (2026-10-18) already covers sustained streaming on a previous build,
  so Phase 2 is a *regression pass on the final build plus the gaps*, not a repeat soak. The USB cable keeps
  the phone out of Doze and hides battery effects; this protocol does not claim to measure either.
- **Do not repeat** the 47/123/180 s outage runs, the clean Wi-Fi<->mobile switches or the 57 min soak.
- Agent measures; the person performs radio, call and Bluetooth actions and says "done" for each step.
- **Data safety (per #47, post-#46 audit §7.4):** before anything, archive and verify the debug app's
  `databases/`, `shared_prefs/`, `files/` **and `cache/`** (compare file sizes); never `pm clear`, never
  uninstall, never delete downloads; side-by-side debug build only. After the run, compare sizes with the
  archive. Prefer the user's own library with **read-only listening** (no stars, ratings, playlist edits);
  scrobbling follows normal use and is the one unavoidable write, so a short session on a real library is
  acceptable only if the user accepts it, otherwise use a disposable fixture server.
- **Logs:** capture only Media3/session state, positions, timestamps and the app's own tags; strip hostnames,
  IPs, URLs, ids and credentials with a redaction pass before anything is written to the repo; no
  permanent telemetry.

### 8.2 Blocks

| Block | Required? | What | Evidence | PASS | FAIL |
|---|---|---|---|---|---|
| **A Normal listening** (~30 min incl. 10 min screen off) | **Mandatory** | Start from each entry (Home, Library, Search, Album, Playlist, Downloads); mixed Opus/MP3 + FLAC + one 24/96 or 24/192 track; Next/Prev/seek; 6+ natural transitions; 10 min locked; confirm wake lock released after pause | session states + positions, `dumpsys power` before/after pause, logcat error count | no unrequested skip, no pause, no watchdog line, queue intact, controls respond | any skip/pause not requested, silence > ~2 s on a good link, crash |
| **B Audio focus** (~10 min) | **Mandatory** | (1) real incoming call, answered and rejected; (2) voice assistant; (3) another media app starts then is stopped; (4) a notification sound; (5) user pauses during an interruption, then it ends; (6) permanent loss (another music app takes focus for good) | state timeline, item/position before and after, watchdog/recovery lines | transient: same item and position, resumes by itself; pause or permanent loss: **no** resume; no error sheet | skip, position loss, surprise resume, error sheet |
| **C Background / lifecycle** (~10 min) | **Mandatory** | lock/unlock, notification and lock-screen controls, app to background and back, **one process kill** (not force-stop) with media-button resume, **swipe from recents (decision F-3)**, observe the Quick Settings card (#37) | session state, queue/position after restore, screenshot of the QS card | state restored, no unwanted autoplay, controls correct | queue/position lost |
| **D Bluetooth / media controls** (~10 min) | **Mandatory** if an A2DP device exists, else defer with `NOT_TESTED` | connect resumes, disconnect pauses, headset buttons, route switch to speaker and back; unplug wired headset if available | session state per event | connect resumes, disconnect pauses, no loop | spurious play, no pause on disconnect |
| **E Offline** (~5 min) | **Mandatory, short** | downloaded album in airplane mode: play, seek, natural transition, Next; go online mid-playback | no HTTP, states | plays and transitions with no error | error, skip |
| **F Network gaps only** (~30 min field walk) | Attempt once; **deferrable only with a justification** | degraded-but-associated Wi-Fi: leave/arrive home x3 with the debug logger, plus the manual Wi-Fi-off comparison (#35 plan); optionally a < 10 s blip and a handoff while the buffer is nearly empty | sanitized timeline: usable-route restored -> `READY` -> audio | recovers within the existing budget without manual action | silence that does not self-recover |

The normal gate is to attempt F and document the outcome before deferring it. If the field walk cannot be run,
F remains `NOT_TESTED` and the audit is incomplete **unless an explicit, documented project-owner risk exception
is accepted**. The owner-delegated exception for the beta is recorded in section 14.9; it does not satisfy #35's
technical acceptance criteria or establish that degraded-Wi-Fi handoff works.

## 9. Completion criteria

`LISTENING_EXPERIENCE_AUDIT_COMPLETE = YES` when: every row of the matrix has evidence or a written platform
limitation; blocks A-E are run (or D is `NOT_TESTED` for lack of a device, stated); F is run or its
limitation is accepted in writing; every finding is classified; Phase 2 results and sanitized timelines are in
this document.

`BETA_PLAYBACK_READY = YES` when additionally: no `PRE_BETA_BLOCKER`; no `PRE_BETA_FIX` affecting everyday
listening is open; F-3 has an owner decision; #35's field case is either validated or its residual risk is
accepted with the evidence above; the final build passes the gates (tests, lint, release build). A passing unit
suite alone never sets it. A scenario that was not tested is **not** automatically a blocker: it blocks only
if its failure would be a blocker (matrix column "If it failed") **and** no other evidence covers it.

Severity rules: `PRE_BETA_BLOCKER` = data loss, reproducible blocked playback, unrequested skips, serious
failed recovery. `PRE_BETA_FIX` = significant, reproducible everyday friction. `POST_BETA` = non-critical or
acceptable workaround. `ACCEPTED_AS_IS` = Android behaviour or documented limitation.

## 10. Minimum next steps
1. ~~Owner decision on F-3~~ done: policy approved and implemented in #49; block C now confirms it on hardware.
2. ~~Run Phase 2 blocks A-E on the Pixel 7~~ done (section 14). Still open: F as a field walk, or the owner's written acceptance of its limitation.
3. ~~#50~~ done (section 13); optionally open one issue for first-audible-audio measurement.
4. Update this document with the Phase 2 results and set the two statuses.

## 11. Reproducing the emulator work
Throw-away host scripts (not committed): a fake Subsonic server with `/_ctl?mode=<status|black>&for=<s>`
and one always-404 track, a `dumpsys media_session` / `media.audio_flinger` poller, and an action log. They
were removed with the emulator and the server at the end of the run; `adb reverse` was cleared.

## 12. Playback continuity after removing the task (#49)

**Previous behaviour.** `onTaskRemoved()` always called `releasePlayerAndSession()`: music stopped and the
notification disappeared. Reproduced on the emulator with the old build: log "Stopping the playback because we
were swiped away", media session and foreground notification gone, output frames stop.

**Approved policy.**

| State when the task is removed | Result |
|---|---|
| PLAYING | music continues |
| BUFFERING with play intent (including a pending #19 network recovery and a transient focus suppression) | session continues and keeps recovering |
| PAUSED | service finishes cleanly; nothing plays on reopen |
| IDLE with no recovery pending / ENDED / empty queue | service finishes |
| Force Stop | Android stops the app (not prevented) |

**What Media3 1.10.1 does by default (disassembled, not assumed).** `MediaSessionService.onTaskRemoved`
calls `pauseAllPlayersAndStopSelf()` unless `isPlaybackOngoing()` (foreground notification started) **and** some
session's `Player.isPlaying()`. `pauseAllPlayersAndStopSelf()` first runs `setPlayWhenReady(false)` on every
player. `isPlaying()` is false while BUFFERING and while focus-suppressed, so the default would stop those
sessions and also discard the user's play intent. Deleting the override would therefore not satisfy the policy.

**Implementation (small, no new manager or service).** `PlaybackTaskRemovalPolicy.kt`:
`shouldContinuePlaybackAfterTaskRemoved(playWhenReady, playbackState, mediaItemCount, networkRecoveryPending)`.
`PlaybackService.onTaskRemoved` returns without releasing (and without calling `super`, which would pause)
when it is true; otherwise it runs the existing `releasePlayerAndSession()` path unchanged.
`MediaPlayerManager.isNetworkRecoveryPending` exposes the existing #19 retry counter (cleared when playback
resumes or the budget is spent, so a dead network cannot pin the service). Queue/position persistence (#47),
the media session, controllers, the sleep timer and audio focus are untouched.

**Tests.** `PlaybackTaskRemovalPolicyTest` (14): the whole policy table, and the pending-recovery flag driven
through the real player listener (scheduled by a transient error, cleared when playback returns, not pending
after the budget is spent, not started when paused). A wiring guard in `PlaybackPolicyInvariantsTest` checks
that the policy is consulted before the release and that the continue branch returns before `super`. Mutation
check: dropping `BUFFERING` from the policy fails the buffering test. **Limitation:** a Robolectric test of the
real service is not possible here (`object Settings` and the player/session stack do not class-load in the unit
environment), so the service lifecycle is validated on the emulator.

**Emulator validation (2026-10-31, `Pixel_7` AVD, fake server, synthetic data).** The Recents card could not be
dismissed with a synthetic fling, so the task was removed with `cmd activity stack remove <taskId>` (the
activity-manager path that removes a task and notifies its services; not Force Stop).

| Scenario | Observed | Result |
|---|---|---|
| A. Playing, task removed | log "Task removed, but playback is wanted"; same process; session `PLAYING`; foreground notification (id 1001) and media session kept; output frames kept advancing (+372096 in ~7 s); reopening showed the next queued track playing at 1:21 | PASS |
| B. Paused, task removed | "Task removed and nothing is playing: finishing the session"; session and notification gone; reopening restored `PAUSED` at 4940 ms with **no** audio frames | PASS |
| C. BUFFERING (server 503 for 22 s), task removed | session kept; loader retries at +0.1/2.2/4.2/8.2/16.2/24.2 s; `PLAYING` at +24.4 s without any UI; audio frames flowing | PASS |
| D1. Queue cleared (stopped), task removed | "nothing is playing: finishing"; session and notification gone | PASS |
| D2. Force Stop while playing | process gone, no session, no notification, no audio, not restarted in 8 s | PASS (Android policy) |
| Before/after | old build, scenario A: stops, notification gone, silent | confirms the fix |

Audio output: verified as frames reaching the AudioFlinger output thread plus the session state. The emulator
has no audio device, so audibility is **not** verified here. A process kill (not Force Stop) restoring the queue
at the saved position was validated in Phase 1 (E7); that path is unchanged.

**Pending on hardware (#36 Phase 2, block C):** a real finger swipe from Recents, the Quick Settings media
card after removal (#37), whether a paused session leaves a stale notification, and behaviour under real Doze.

## 13. Album list duplicate-key crash (#50)

**Root cause.** `AlbumListViewModel.loadPaged` appended each page with `allAlbums + fetched`, and the grid and
list are keyed by album id. Offset paging over a collection that changes between two requests (a library scan
adds an album that sorts first, or a recent/frequent order moves while listening) returns the last album of page
1 again at the start of page 2. Compose throws `IllegalArgumentException: Key "<id>" was already used`, the
process dies and the playback service dies with it.

**Fix (one view model, no change to the paging system).**
- `mergeAlbumPage(existing, page)` appends only ids not yet shown; the first occurrence wins, so a row already
  on screen is never replaced or moved, and newer metadata for an album already shown is picked up by a refresh,
  not mid-scroll. Identity is the album id alone: two different albums with the same title/artist are both kept.
- A first page (and the unpaged "by artist" result) is de-duplicated by id too.
- The offset is unchanged: each successful page advances it by one page size, **independent of how many rows
  were new**, so de-duplication can never make the next request repeat a page (20 received, 19 new -> next
  offset 40, not 39). The end of the list is still a page the server sent empty, never "nothing new".
- A page made only of albums already shown adds no row, so the screen has nothing new to trigger the next load
  on; the view model then asks for the next page itself, at most 3 times in a row, then waits for the next scroll.
  Bounded: it cannot loop.
- Limitation that cannot be removed: offset paging over a mutable collection can still **miss** an album (one
  that moved from page 2 to page 1 after page 1 was read). It is never shown twice; a refresh shows the current
  library. The existing page-size-based offset (rather than the received count) is kept because an existing
  test pins it; the two differ only when a server returns short pages mid-collection, which is a separate,
  unchanged behaviour.

**Tests.** `AlbumListPaginationTest` (13) drives the real view model against an in-memory server that honours
`size`/`offset`: ordinary pages; a duplicate across pages; inside one page; same title with different ids;
the documented scenario (page size 20, 28 albums, an album inserted before page 2, `gen17` once, offsets 0/20/40,
refresh shows the inserted album); offset after de-duplication; a duplicates-only page is bounded (0/20/40/60/80)
and is not the end of the list; an empty page is; refresh after several pages; first-wins merge; rows already
on screen are not replaced; a response for the previous sort order never reaches the new list. Against the old
merge 5 of the 13 fail. The existing `AlbumListViewModelTest` tests are unchanged and green.

**Emulator (2026-10-31, `Pixel_7` AVD, fake server honouring `type`/`size`/`offset`, music playing).**

| Build | Result |
|---|---|
| Baseline `c219a95c` | page 1, the server inserts an album sorting first on the page-2 request, scroll to the boundary: `Key "gen16" was already used`, process 12048 died, the music stopped (session `NONE`) |
| Fixed | same procedure: no crash, same process (12878) the whole time, session `PLAYING` throughout, 29 distinct titles across the whole scroll (the 29 original albums; the inserted one appears after a refresh), requests 0/20/40 and then no more, open an album and back, pull to refresh: the inserted album is now first and paging restarts at offset 0 |

**Other lists.** No other screen has the pattern (append without de-duplication + id keys): the create-playlist
song list already de-duplicates by id (and advances its offset by the received count); the legacy
`AlbumListModel` is not used by any screen. Lists built from a single response are not paged. Not changed.

**Gates (final code).** 1948 tests, 0 failures (1935 + 13); lintDebug and lintVitalRelease with no new issues;
assembleDebug and assembleRelease green; detekt 42 + 1, the same as before. The emulator run above used the
build made before a behaviour-preserving refactor (the merge bookkeeping and the skip-ahead rule were moved into
small helpers to keep `loadPaged` under the detekt complexity limit); the 13 tests and the existing view-model
tests pass on the final code, but the emulator was not re-run on the very last build. One earlier full run had a
single failure in an unrelated test (`SheetInteractionAuditTest.createPlaylistName`: "uncaught exceptions before
the test started", caused by a leaked `Scrobbler` coroutine from an earlier test class with no Koin); it did not
recur on the next full run and does not involve the album list, so it is recorded as a pre-existing timing flake,
not investigated further here.


## 14. Phase 2: physical validation on the Pixel 7 (2026-11-01)

### 14.1 Setup and data safety
| Item | Value |
|---|---|
| Device | Pixel 7 (`panther`), Android 17 / SDK 37, build `CP3A.261005.002.A1`, 1080x2400; identified by ADB serial and used only through `adb -s <serial>` |
| Build under test | debug app `io.github.churipakinti.taki.debug`, `0.1.0-beta` (131) built from `develop` @ `4c2c5b5c`, installed with `adb install -r` (same signature); the release app was not modified (last update 2026-10-18) |
| Audio route | JBL Live 770NC over A2DP (also the built-in speaker in block D) |
| Network | home Wi-Fi + Tailscale VPN, mobile data on; library on the owner's Navidrome (normal scrobbling accepted) |
| Backup | `run-as ... tar` of the whole private dir (`databases/`, `shared_prefs/`, `files/`, `cache/` incl. `downloadstate.ser`, 6.4 MB) stored outside the repo; verified by extracting it, matching SHA-256 of the key files against the live ones, and `PRAGMA integrity_check` = ok on all four databases. The tar's own SHA-256 was recorded next to it |
| Data preserved | yes: preferences file byte-identical after the update; downloads (23 files, ~970 MB) untouched; no `pm clear`, no uninstall. The saved queue was replaced by test queues (it was empty before), which is normal use |
| Emulator #50 recheck | final build `4c2c5b5c` on the isolated AVD with the fake server: page 2 returned shifted after an album insert, no crash, no duplicate row, playback stayed `PLAYING` |

Method: a small host script sampled `dumpsys media_session` (restricted to Taki's own session), `dumpsys audio`
(started `AudioTrack`, focus requests/abandons with timestamps) and `dumpsys connectivity` every ~0.15-30 s.
Only package names, positions and titles were recorded; no URLs, tokens or hostnames. The person performed every
physical action and confirmed audibility by ear: **Media3 `READY`/`PLAYING` and a started `AudioTrack` were never
taken as proof of sound.** Times measured from an ADB command include about 0.2-0.3 s of ADB overhead.

### 14.2 Results matrix
| Block | Scenario | Result | Evidence |
|---|---|---|---|
| A | Start from Home (Daily Mix), Album, Playlist | PASS | tap to `PLAYING` + `AudioTrack` started in 0.6-1.0 s (3 entries); heard |
| A | Play/Pause, Next, Previous, seek | PASS | resume 0.55 s at the same position; next 0.6-1.0 s; previous > 3 s restarts the song (standard); seek buffers ~0.4-1.0 s and resumes; all heard |
| A | Natural transitions, queue edit (Play Last 5 to 6), screen-off, app switch, reopen | PASS | 5+ automatic transitions with no skip; queue grew without a glitch; 60 s locked, then 4 min locked: `PLAYING`, position advancing in real time; Taki to Settings and back: same item, continuous position |
| B1 | Permanent focus loss (YouTube, `req=1` GAIN) | ACCEPTED_AS_IS | Taki paused and abandoned focus, did **not** resume: correct for a permanent loss |
| B1 | Temporary focus loss (Clock alarm, `req=4`) | PASS | paused at the request, resumed ~0.1 s after the alarm released focus, same item, 173.1 to 173.3 s |
| B2 | User pause during an interruption | PASS | paused while the alarm rang, stayed paused after dismiss (no audio track) |
| B3 | Google Assistant | PASS | paused on the assistant request, resumed ~0.3 s after it released focus, same position |
| B3 | Real incoming call, notification sound | UNAVAILABLE | no real call placed; no notification-sound source exercised |
| C1 | Real finger swipe from Recents while playing | PASS | `removeGroupTaskInternal` + `onTaskRemoved` for the Taki task logged; music continued and moved to the next song with no pause; process, foreground service (id 1001) and notification stayed; session `active`; reopen: same 100-item queue, same item, continuous position |
| C2 | Swipe while paused | PASS | no sound; the session, service and notification were removed (not stale); reopen restored the same item at the identical position (73366 ms) |
| C3 | Quick Settings card | PASS with a cosmetic note | normal playing/paused states correct; after the service stops Android keeps a dimmed resumption card (Play only); tapping Play restarted the service and restored the song at 73.4 s in 3.4 s; the session briefly (~0.3 s) published queue item 0's metadata before the restored item. Related to #37, not modified |
| C4 | Screen off / idle | PASS | `PLAYING` for 4 min with the screen off; `PAUSED` for 2.5 min with the screen off: no spontaneous resume. No Doze forcing, USB attached (so Doze/battery effects are not claimed) |
| C | Process kill (`run-as kill -9`, not Force Stop) | PASS | the system restarted the process; a media-button Play restored the same item at 120.3 s (120.5 s before), queue of 12, `PLAYING` in 2.2 s |
| D | Headset buttons (Play/Pause, Next, Previous) | PASS | each press matched the log |
| D | Disconnect (headphones off) | PASS | paused 0.37 s after the route dropped; nothing on the speaker |
| D | Speaker, then reconnect | PASS | played on the speaker; on reconnect the same `AudioTrack` moved to the Bluetooth device while `PLAYING` with no pause or reset |
| D | Lock-screen controls (Pause, Play, Next) | PASS | all worked without unlocking |
| D | Wired headset, reconnect while paused | NOT_TESTED | no device / not exercised |
| D | Android Auto | REUSED | AA-05/07/09/10 and the final AA audit (`bb63ae60`); not repeated |
| E | Offline (Wi-Fi and mobile off, `Network is unreachable`, no default network) | PASS | downloaded album: kept playing, natural transition, seek, pause, resume; no errors; back online: queue intact (12), playback continued; connectivity restored to the original configuration |
| F | Degraded Wi-Fi field walk (#35) | NOT_TESTED | the owner could not do the walk; not attempted |

Note found while running E: with airplane mode on, Android keeps Wi-Fi up (`wifi_on=2`), so the phone stayed
online until Wi-Fi was turned off explicitly (confirmed by ping before counting the test).

### 14.3 Metrics
- Tap to first `PLAYING` with a started `AudioTrack`: 0.6-1.0 s over three entry points (includes ADB overhead).
  First *audible* audio was confirmed by ear but not timed to sub-second precision (a person cannot time it).
- Unrequested skips: 0. Unexpected silences: 0 (only the expected 0.4-1.0 s seek rebuffer). Unrequested
  pauses or resumes: 0 (every pause/resume followed a focus event or an action). Unexpected interruptions: 0.
- Errors in the session (`error=`): none in any block.

### 14.4 Findings
| ID | Finding | Class |
|---|---|---|
| P2-1 | After the service stops, Android shows a dimmed media-resumption card; its Play works. Brief (~0.3 s) wrong-title flash on resume | `POST_BETA` (#37; platform-managed card) |
| P2-2 | A permanent focus loss (another media app) leaves Taki paused with no auto-resume | `ACCEPTED_AS_IS` (Android policy) |
| P2-3 | First-audible time is not logged by the app | `POST_BETA` metric gap (F-6) |
| P2-4 | #35 degraded-Wi-Fi case unverified on hardware | residual risk, see 14.9 |

No defect reproduced that interrupts music, changes the queue or needs an app restart.

### 14.5 #49 on hardware
The earlier A/B was an emulator `stack remove`. On the Pixel a real finger swipe removed the task and playback
continued (C1); a paused swipe stops cleanly and restores exactly (C2). The first C1 attempt swiped the
Assistant's floating card by mistake (the logcat showed the removed task id was not Taki's), so it was discarded
and repeated; the evidence above is from the repeat.

### 14.6 Reused evidence
Automated: 1948 tests, 0 failures, debug and release builds, lint and detekt unchanged (section 13). Streaming:
the 57 min soak (2026-10-18) and #35 Phases 3-4. Android Auto: AA-05/07/09/10 and `bb63ae60`. New in this phase:
the manual Pixel runs above.

### 14.7 Not tested
Block F field walk; real call; notification sound; wired headset; reconnect while paused; hi-res FLAC mix in
block A; sustained 10 min locked playback beyond the 4 min sampled here (covered by the 57 min soak).

### 14.8 Restoration
The Pixel's configuration was restored (airplane off, Wi-Fi on, mobile data on, Tailscale connected, Bluetooth
connected). Test queues remain in the debug app (the original saved queue was empty and is in the backup).

### 14.9 Decision
**Risk acceptance — 2026-11-01, product decision delegated by the project owner.** Accept the residual #35 risk
**for a beta release**, not as a claim that the degraded-but-associated Wi-Fi handoff is fixed or verified. A real
field walk was **NOT_TESTED**; prolonged silence at Wi-Fi range boundaries remains possible. Existing Pixel
results cover clean handoffs, prolonged outages and A-E everyday listening, with no newly reproduced blocker.
Leave #35 **OPEN** for targeted field validation and beta feedback. If a reproducible, significant silence
recurs, reclassify and prioritize it as a pre-beta/severity-appropriate playback defect before broader rollout.
The real incoming-call and notification-sound focus cases remain **UNAVAILABLE**, not PASS; temporary focus,
Assistant, permanent focus loss and user-pause behavior did pass physically.

This explicit, scoped exception closes the audit's evidence/decision gate without rewriting untested scenarios
as passing: `LISTENING_EXPERIENCE_AUDIT_COMPLETE = YES`, `BETA_PLAYBACK_READY = YES`, and
`PUBLIC_BETA_READY = NO` (release gates still pending). Next: `BETA_RELEASE_GATES_REVIEW`.
