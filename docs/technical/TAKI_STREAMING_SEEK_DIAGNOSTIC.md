# Streaming seek delay: diagnostic (beta.2 hold)

Date: 2026-11-01. Code under test: `e26349ed` (the beta.2 release candidate). Diagnostic only: **no production code
was changed**, nothing was published, the release install on the Pixel 7 was not modified.

## 1. Symptom under investigation

During the physical validation of the signed beta.2 APK on the Pixel 7, seeking on some **streamed** songs was ignored for
the first seconds. Evidence from that session (release app, about four minutes after the in-place update):

- Media3 logged `MCImplBase: Controller isn't allowed to call command= 5` (`COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM`) for
  five seek attempts on one track over about 9 s.
- `dumpsys media_session` showed the session without the seek action for that track for 28 s or more, while audio was
  audibly playing, and with an abnormal legacy `PlaybackState` (`PLAYING`, `position=-1`, `buffered=-1`, `speed=0.0`).
- Several other tracks showed no seek action on the first sample after a quick skip, then gained it within about 3 s.
- Downloaded tracks always seeked immediately. The owner later saw a streamed track seek after about 30 s.

## 2. Static findings (code at `e26349ed`)

- `MediaLibrarySessionCallback.onConnect` passes `connectionResult.availablePlayerCommands` through unchanged;
  `ControllerPolicy` only removes **session** commands (rating). Taki does not remove command 5.
- The local player is a plain `ExoPlayer` with `DefaultMediaSourceFactory(CachedDataSource(ResolvingDataSource(OkHttpDataSource)))`.
  No custom extractor, no seek override, no `isCurrentMediaItemSeekable` override outside the Jukebox backend.
- Seekability is therefore decided by Media3's progressive source: a seek map from the extractor plus the length reported by the
  HTTP data source.
- Stream URLs come from `API.stream(id, maxBitRate, format)`; `estimateContentLength` is not sent. Observation (not shown to
  cause this symptom): `RESTMusicService.getStreamUrl` executes the stream request on `streamClient` to obtain the final URL
  and does not close that response, although its comment says it does not execute the call.
- Media3 1.10.1.

## 3. Method

Temporary, **debug-only, never committed** logging (removed again; `git status` clean; clean debug build reinstalled):

- Player side: `ExoPlayer` listener logging `isCommandAvailable(5)`, `isCurrentMediaItemSeekable`, window `isSeekable`, duration,
  position, state, audio MIME, on transition/state/timeline/commands/discontinuity events.
- Controller side (the app's `MediaController`): the same fields.
- OkHttp network interceptor logging only HTTP status, protocol, whether `Range` was sent, `format`/`maxBitRate`/`estimateContentLength`
  parameter values, and the response headers `Content-Type`, `Content-Length`, `Accept-Ranges`, `Content-Range`,
  `Transfer-Encoding`, `X-Content-Duration`. No URL, host, credential or query parameter other than those three was logged.

Runs on the Pixel 7 against the owner's real library over the normal app path (read-only playback):

| Run | App | Protocol |
|---|---|---|
| 1 | debug, player-side logging | five named streamed tracks, 40 s each, seek every ~10 s |
| 2 | debug, player + controller logging | cold start after reinstall, seek within 3 s of play, 7 quick Next presses with an immediate seek, then 30 s of seeking |
| 3 | **release** (the exact signed APK), no instrumentation | same protocol as 2, observed only from outside (`dumpsys media_session` once per second and `MCImplBase` warnings) |

## 4. Results

**Server / HTTP (runs 1 and 2, 140 responses, all via the normal player path):**

| Question | Result |
|---|---|
| `Content-Length` present | every response (0 of 140 without) |
| `Accept-Ranges: bytes` | every response |
| `Range` requests (seeks) | answered `206` with a correct `Content-Range` every time |
| `Transfer-Encoding: chunked` | never |
| `format` / `maxBitRate` / `estimateContentLength` in the request | always null: no transcoding in these runs |
| `X-Content-Duration` | present |
| Formats seen | FLAC (41 full responses), MP3 (22), Ogg/Opus (2), WMA (6) |

**Media3 timing (time from item transition to the seek command appearing):**

| Run | Tracks | Median | p90 | Max |
|---|---|---|---|---|
| 1 | 32 | 0.14 s | 0.18 s | 1.15 s (restored item after reinstall) |
| 2 | 37 | 0.11 s | 0.24 s | 4.75 s (a WMA track that cannot be played, see below) |

The command appears 60–180 ms after the HTTP response, which matches Media3 building the seek map from the first bytes.
In run 2 the controller and the player never disagreed for more than 20 ms. **No track was ever READY or playing while
unseekable, on either the player or the controller.** FLAC and MP3 seeked immediately; Opus too.

**Release re-run (run 3):** 79 of 82 session samples had the seek action; the 3 exceptions were taken during `BUFFERING`
at the start of a new track. `Controller isn't allowed to call command= 5` appeared **0 times**.

**Local vs streaming:** downloaded tracks seek immediately (owner, and `cmd5` true at READY). The same track streamed also
seeks within ~0.15 s, so transport is not the difference in these runs.

**Incidental finding, unrelated to seeking:** six responses had `Content-Type: audio/x-ms-wma`. Media3 1.10.1 has no WMA
extractor, so those tracks raise `UnrecognizedInputFormatException` (`Source error`); Taki's existing recovery (#30) retries
once and skips. The one "song that would not play" the owner reported in run 2 was this. It is not a seek defect.

## 5. Hypotheses

| Hypothesis | Status | Evidence |
|---|---|---|
| Server lacks Range / Content-Length | **Refuted** | 140/140 responses have both; all seek reads returned 206 |
| Server transcodes (no length, chunked) | **Refuted** | no `format`/`maxBitRate` parameters; no chunked responses |
| MP3 CBR/VBR or FLAC seek map is slow to build | **Refuted** for FLAC, MP3, Opus | seek available ~0.1–0.25 s after the response |
| `onConnect` / `ControllerPolicy` strips command 5 | **Refuted** | static: command set passed through; dynamic: controller `cmd5` tracks the player |
| Release-only (R8 / signing) | **Not supported** | run 3 on the release APK did not reproduce |
| Cold start / fast skipping | **Not supported** | run 2 |
| Rapid-skip samples being a sampling artifact | **Plausible** for the short cases | `seek=0` snapshots coincide with `BUFFERING` at track start |
| Slow first byte from the server or tunnel at that moment | **Open, weak** | would show `BUFFERING`, but the session reported `PLAYING` with audible sound |
| Stale session / controller command state on the first play after an in-place update | **Open** | matches the abnormal `PlaybackState` and controller-only rejection; cannot be tested without updating the release app again, which this task forbids |

## 6. Root cause assessment

**Class D: undetermined.** The long delay (28 s or more on one track, rejections from the app's controller, abnormal session
state) was observed once, on the release app shortly after the first run following the update, and **was not reproduced in
three controlled attempts**, including one on the exact release APK. What the evidence does exclude is the externally plausible
causes: HTTP range support, transcoding, extractor seek maps and Taki's `onConnect` policy.

What remains is a state-synchronisation or transient condition between the service's session and the app's `MediaController`,
or a transient server/tunnel condition. Neither can be confirmed or refuted from the data.

What would distinguish them if it recurs: record a bug report immediately (`adb bugreport`), the `dumpsys media_session`
block for the Taki package, and note whether it follows an in-place update, an app restart, or a restored queue.

## 7. Remediation assessment

- No defective code was located, so there is no fix that can be justified. Forcing the seek command or adding a second
  player/extractor is out of bounds and would not be correct if the media is not really seekable.
- Impact if it recurs: temporary loss of the seek bar on a streamed track. It does not stop playback, lose data or crash,
  and recovered by itself in every observation. Downloaded tracks are never affected.

**Recommendation: `RECOMMEND_DOCUMENT_AND_RELEASE`.** Document it as a known limitation (see the issue), keep collecting
evidence in the beta, and treat a reproducible case as a post-beta fix. Benefit to users: beta.2 ships with the many fixes
already validated; risk: a rare, self-correcting seek-bar glitch on streamed tracks.

## 8. Acceptance criteria for closing the issue

1. A reproducible case, or a bug report showing the session/controller state, identifying the cause.
2. After a fix, a seek on a streamed track works within 1 s of READY on a cold start and after an in-place update.
3. A unit test that fails if the controller loses `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` while the player has it.

## 9. Cleanup record

- Temporary instrumentation reverted (`git checkout`); no `SEEKDIAG` text left in `ultrasonic/src`.
- Clean debug APK rebuilt and reinstalled over the **debug** package only (`io.github.churipakinti.taki.debug`).
- Release package `io.github.churipakinti.taki`: untouched (version 132, `lastUpdateTime` unchanged during this diagnostic).
- No logs, URLs or credentials are stored in the repository.
