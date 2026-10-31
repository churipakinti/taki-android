# Taki listening-experience audit (issue #36) — Phase 1

Phase 1 = reconnaissance, automated audit, emulator validation and a prepared (not executed) Pixel 7
protocol. **No production code was changed. The Pixel 7 was not used. No production server was contacted.**
Phase 2 (physical validation) is not started.

## 0. Status

```text
LISTENING_EXPERIENCE_AUDIT_COMPLETE = NO   (physical blocks in section 8 not run)
BETA_PLAYBACK_READY                 = NO   (no blocker found, but the gate needs the Phase 2 evidence)
PUBLIC_BETA_READY                   = NO
```

Nothing found in Phase 1 is a `PRE_BETA_BLOCKER`. After the swipe-away work (#49, section 12) the open
finding that could be a `PRE_BETA_FIX` is F-1 / #50 (album-list duplicate-key crash), proposed, owner to
confirm. "Not ready" here means *unverified on hardware in the listed areas*, not *known broken*.

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
recover. F-1 (#50) is proposed as `PRE_BETA_FIX`; F-3 (swipe-away) is fixed (#49).

| ID | Finding | Evidence | Class |
|---|---|---|---|
| F-1 | **#50.** The Albums list crashes with `IllegalArgumentException: Key "<id>" was already used` (Compose `LazyGrid`, key = album id) when two pages of the paged load contain the same album. The Phase 1 crash came from a fake server that ignored `offset` (invalid). It was then **reproduced with fully valid responses**: a server honouring `type`/`size`/`offset` whose library gains one album between page 1 and page 2 (page 2 repeats the last album of page 1; `Key "gen17"`). The UI and the playback service share one process, so the crash also stops playback (the queue is restored from #47 state). `AlbumListViewModel` appends with no de-duplication | logcat, 2026-10-31; real Navidrome not tried | Proposed `PRE_BETA_FIX` (deterministic crash from valid responses, small fix); owner confirms |
| F-2 | `#37` stale Quick Settings media card: Android-managed history is plausible; not examined here | #37 | `POST_BETA` unless it proves to be a live duplicate session |
| F-3 | Swiping Taki from recents **stopped playback and removed the notification** (inherited `onTaskRemoved`) | source + emulator A/B (section 12) | **Fixed in #49** (policy decided by the owner); physical confirmation pending in Phase 2 block C |
| F-4 | #41 / #42 hi-res FLAC follow-ups (packet message at ~154.2 s, 24->16 bit output) | FLAC doc §21-22 | `POST_BETA` |
| F-5 | Force-stop then media key does not restart the app (emulator) | E-notes | `ACCEPTED_AS_IS` (Android stopped state) |
| F-6 | Time to first audible audio is not measured anywhere | this audit | metric gap, not a defect |

Related issues (none modified or closed): #17 (closed, FLAC), #19 (closed, retry), #30/#32 (closed, error
contract), #34 (closed, focus), #35 (open, field case), #37 (open), #45/#46/#47 (closed), #44 (open P3).

## 8. Pixel 7 protocol (prepared, not executed)

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

Deferring F beyond the beta is acceptable **only if** F was attempted, did not reproduce a long silence, and
the limitation is documented (see 9). A walk that cannot be done is `NOT_TESTED` and keeps
`LISTENING_EXPERIENCE_AUDIT_COMPLETE = NO`.

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
2. Run Phase 2 blocks A-E on the Pixel 7 with a verified backup, then F as a field walk.
3. Owner to confirm the classification of #50 (album list duplicate keys) and decide whether it is fixed before the beta; optionally open one issue for first-audible-audio measurement.
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
