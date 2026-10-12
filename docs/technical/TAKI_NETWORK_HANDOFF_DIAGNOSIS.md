# Taki network-handoff diagnosis (issue #35, Phase 2A)

Baseline: `abca8979` on `develop`. Diagnostic phase only: **no production code changed**. Temporary
instrumentation was used for measurements and removed before this commit.

Evidence labels: **OBSERVED** (measured on the Pixel 7 or read from the shipped Media3 1.10.1
bytecode), **INFERRED** (follows from observations, not directly measured), **NOT_REPRODUCED**,
**UNAVAILABLE** (could not be exercised with the available setup).

## 1. Objective

Explain why Taki can stay silent for a long time when the phone moves between Wi-Fi and mobile
data, and recommend the smallest next-phase change. Key question: once the route is usable again,
how much extra time does Taki need to produce audio, and why?

## 2. User-reported behavior

- A. Leaving home: Wi-Fi stays associated but stops carrying data; long silence until Android
  moves to mobile; turning Wi-Fi off by hand is faster.
- B. Arriving home: Wi-Fi joins before it is usable; long buffering; manual switching helps.

## 3. Current playback architecture (OBSERVED from source)

```
Navidrome <- Tailscale (VPN network on top of Wi-Fi/cell) <- OkHttp (default OkHttpClient.Builder())
  <- OkHttpDataSource <- ResolvingDataSource (resolver) <- CachedDataSource <- Media3 loader
  <- ExoPlayer (default DefaultLoadControl, default LoadErrorHandlingPolicy) <- audio output
```

| Concern | Owner |
|---|---|
| Connection establishment, read timeout, pooling | OkHttp: connect/read/write 10 s each, **no call timeout**, `retryOnConnectionFailure` default |
| Stream URL resolution | `PlaybackService.resolver`; `RESTMusicService.getStreamUrl` builds the request and short-circuits it with an interceptor, so **no network I/O** (re-resolving on each retry is cheap) |
| Load retries and backoff | Media3 `DefaultLoadErrorHandlingPolicy`: delay `min((errorCount-1)*1000, 5000)` ms (0, 1, 2, 3 s ...) |
| Buffer thresholds | Media3 `DefaultLoadControl`; **no custom LoadControl** is set |
| Terminal error, re-prepare | `MediaPlayerManager.onPlayerError` + #19 (`networkRecoveryDelayMs`: 2/4/8 s) |
| Stall (decoder) detection | `PlaybackStallWatchdog` (#17), suppression guard (#34) |

`CachedDataSource` only serves **fully downloaded local files** and forwards everything else
upstream: it is not a partial streaming cache. The streaming buffer is Media3's in-memory buffer
(plus whatever the OS holds in the TCP socket).

## 4. Existing #19 behavior

Fires only after `onPlayerError` with `IO_NETWORK_CONNECTION_FAILED/TIMEOUT`; re-`prepare()`s in place
after 2/4/8 s (3 attempts). It does not act during `STATE_BUFFERING` before an error exists.
**OBSERVED for the first time in this phase** (section 13): the path fires, re-prepares in place, and
playback resumed on the same queue item with no rebuild. #19 is not defective and stays closed.

## 5. Test environment

Pixel 7 (adb over USB, no root). Networks: Wi-Fi (default), LTE (Lucky Mobile), Tailscale VPN on top
of whichever is the default network. Streamed items were MP3 320 kbps. Test controls: `svc wifi`,
`cmd connectivity airplane-mode`. **No router/AP shaping or RF degradation was available.**

## 6. Instrumentation (temporary, not committed)

A debug-only logger with `elapsedRealtime` timestamps: Media3 `AnalyticsListener` (state, isPlaying,
playWhenReady, suppression, load started/completed/error/canceled, discontinuity, transition,
errors), an OkHttp `EventListener` (call/connect/response/failure, class names only), a
`ConnectivityManager` default + all-network callback (transport, INTERNET, VALIDATED), a 1 s player
sampler (position, buffered ahead, state), and two log lines around the #19 retry. No URLs, hosts,
headers or media ids were logged. The code was removed afterwards; raw logs are not committed.

## 7. Reproduction matrix

| Test | Status |
|---|---|
| A Stable Wi-Fi baseline | OBSERVED (startup of an already prepared item; next-track latency 0.17-0.47 s, n=3) |
| B Stable cellular baseline | OBSERVED (next-track latency 0.93-1.06 s, n=2) |
| C Clean Wi-Fi -> mobile switch | OBSERVED (x2) |
| D Degraded Wi-Fi, still associated | **UNAVAILABLE** (no way to shape RF/traffic on Wi-Fi without router/root access) |
| E Mobile -> associated-but-unusable Wi-Fi | **UNAVAILABLE** (clean mobile -> Wi-Fi join only; no playback effect) |
| F1 automatic degraded handoff / F2 forced | F1 **UNAVAILABLE**; F2 (manual Wi-Fi off) equals test C. No automatic-vs-manual comparison possible |
| G Short outage | 5 s OBSERVED; 2 s and 10 s not run; 60 s outage OBSERVED |
| H Prolonged BUFFERING without `onPlayerError` | OBSERVED for ~6 s (a window inside the 56 s retry sequence); not reproduced with a *returning* route in that window |
| High-bitrate FLAC | Not tested (library streams were MP3 320) |

## 8. Automatic vs manually forced handoff

**UNAVAILABLE.** The reported "manual Wi-Fi off is faster" cannot be confirmed or refuted without a
degraded-but-associated Wi-Fi. INFERRED: the difference is Android's own route-selection delay
(Wi-Fi remains the default network until it is validated as bad), which Taki cannot observe.

## 9. Associated vs usable

Only states A-C and F of the brief's ladder were observable (association, INTERNET/VALIDATED
callbacks, media bytes, audio). Nothing was observed where VALIDATED was true while the server was
unreachable. NOT_REPRODUCED.

## 10. Tailscale / VPN

- OBSERVED: with a clean Wi-Fi->cell switch the default network stayed the *same* VPN network
  (`WIFI+VPN` -> `CELL+VPN`, ~0.6 s). **No new HTTP connection was opened** (no `connectStart`), and the
  in-flight request kept running. The handoff is invisible to OkHttp.
- OBSERVED: with airplane mode the VPN network remained as `VPN` only (no underlying transport).
  New TCP connects were **black-holed**, not refused: each failed only after the 10 s OkHttp
  timeout (`SocketTimeoutException`), never with a fast `UnknownHost`/`ConnectException`.
- OBSERVED: after airplane mode was turned off, the default network was available 3.4 s later and a
  pending connect attempt completed 2.9 s after that (Tailscale path re-establishment / SYN
  retransmission; INFERRED mechanism).
- Tailscale was never disabled, no direct endpoint was created.

## 11. Media3 buffering (OBSERVED)

Effective config: `DefaultLoadControl` defaults in 1.10.1. Constants: streaming min/max buffer
50 000/50 000 ms, playback start 1 000, after rebuffer 2 000; **local-playback** variants: min 1 000,
max 50 000, start 1 000, after rebuffer 1 000, prioritize-time true; audio byte cap 13 107 200 B.

**Finding.** `DefaultLoadControl.isLocalPlayback()` treats a media item as local when its URI scheme
is *empty* or in {file, content, data, android.resource, rawresource, asset}. Taki's media-item URIs
are `"$id|$bitrate|$filePath"` (`MediaItemConverter.kt:69`), which have no scheme, so **every
streamed track is classified as local playback.** Measured result, repeated across tracks: the
buffer is topped up only when it falls to ~1 s, then refilled in a burst to ~50 s:

```
bufAhead:   50 s ------ 40 ------ 30 ------ 20 ------ 10 ------ ~1 s   -> refill burst (isLoading=true at 0.98 s) -> 50 s
```

So the playback reserve is a sawtooth between ~1 s and ~50 s (mean ~25 s), not a steady ~50 s. A
handoff that coincides with the low part of the saw has almost no reserve. Rebuffer resume
threshold is 1 s. The hi-res byte cap (13.1 MB) would limit 24/96 FLAC (~4.6 Mb/s) to ~22 s of
audio unless prioritize-time is enabled (INFERRED from constants; FLAC not measured).

Reserve extended by the OS: in the 5 s outage, a refill burst completed (50 s buffered) *after*
airplane mode was already on, i.e. from data already in the socket (INFERRED). Short outages can
therefore be masked even when the app buffer is low.

## 12. HTTP timeouts and retries (OBSERVED)

- Dead path with an established stale connection (5 s outage; buffer was masked): the stalled read
  was only failed by the OkHttp **read timeout, 10.1 s** after it began, and **4.5 s after the route
  had returned**; the next request used a fresh connection and refilled in ~0.3 s. STALE_HTTP_CONNECTION
  is real: an established stalled read does not recover on route return, it waits for the timeout.
- Dead path, new connections (60 s outage): five attempts, each failing at the **10 s connect
  timeout**, with Media3 backoff 0/1/2/3 s between them. First stall to `onPlayerError`: **56.2 s**.
  Final error code `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`.
- `prepare()` re-opens the item from the saved position via the resolver (no network cost to
  resolve). Position preservation mid-track was **not observed** (the failure happened at a track
  boundary, position 84 ms).

## 13. Playback recovery timeline (60 s outage, buffer ~1.6 s at start)

Times in seconds relative to the first stalled request (T+0 = ~0.5 s after airplane mode on).

```
T-0.5   airplane mode ON; default network becomes VPN-only
T+0.0   new refill request on the existing flow; no bytes arrive
T+10.0  read timeout (attempt 1) -> Media3 retry immediately
T+10-20 connect timeout (attempt 2) ; +1 s backoff
T+21-31 connect timeout (attempt 3) ; +2 s backoff
T+33-43 connect timeout (attempt 4) ; +3 s backoff
T+46-56 connect timeout (attempt 5)
T+50.2  buffer exhausted at the track boundary -> STATE_BUFFERING (silence begins)
T+56.2  onPlayerError CONNECTION_FAILED, state IDLE; #19 schedules attempt 1/3 in 2000 ms
T+58.3  #19 prepare() fires -> new request starts connecting
T+59.4  airplane mode OFF (route can return)
T+62.8  default network CELL+VPN available (+3.4 s)
T+65.7  connect completes (+2.9 s)       <- the in-flight connect succeeded, within its 10 s window
T+65.9  HTTP 200 first headers
T+66.5  STATE_READY, isPlaying=true (+0.8 s)
```

- Network unavailable (airplane): 59.4 s; remaining reserve at first stall: ~1.6 s refill threshold
  (buffer filled to 50 s just before; it covered ~50 s of the outage).
- Audible silence: 16.3 s (T+50.2 -> T+66.5); 9.2 s while the network was truly down, **7.1 s after
  the route returned**: 3.4 s Android/VPN, 2.9 s TCP connect, 0.8 s response + buffering.
- Media item changed only by natural track end; position preserved at the boundary; #19 retries: 1.
- #19's 2 s delay overlapped network-down time here, so it added no avoidable delay in this run.

The 5 s outage (buffer ~3.7 s at start): no audible interruption; stale read failed at the read
timeout; reconnect and refill in ~0.3 s. The clean Wi-Fi->cell switch (buffer ~4.3 s): no
interruption, no new HTTP connection, refill at 1 s unaffected.

## 14. Root-cause findings

| Class | Finding | Confidence |
|---|---|---|
| Buffer policy | Streams are misclassified as local playback, so reserve is a 1-50 s sawtooth with a 1 s refill threshold | **Confirmed** (bytecode + repeated measurements) |
| STALE_HTTP_CONNECTION | An established stalled read waits for the 10 s read timeout even after the route returns | Observed once (masked by buffer) |
| PROLONGED_MEDIA3_BUFFERING | On a black-holed path Media3 + OkHttp need ~56 s (5 x 10 s timeouts + 6 s backoff) to produce an error; Taki cannot act earlier | Observed |
| NETWORK_HANDOFF with Tailscale | A clean transport switch is invisible to OkHttp; no Taki-side delay | Observed |
| TRANSIENT_NETWORK_ERROR (#19) | Works; adds up to 2/4/8 s only if the route returns inside a backoff gap | Observed once; worst case INFERRED |
| Android/Tailscale | 3.4 s default-network recovery + 2.9 s path re-establishment after the route returns | Observed (one sample) |
| Degraded Wi-Fi remaining associated | Not exercised | **UNAVAILABLE** |

Honest summary: the Taki-controllable contributors observed are (1) the thin, sawtooth buffer
reserve and (2) up-to-10 s OkHttp timeouts on a dead or stale path. The dominant delay in the
reproducible outage (about 6.3 s of the 7.1 s after route return) was OS/VPN route recovery, which
Taki cannot shorten. Whether the specific *leaving home* case is dominated by Android's refusal to
leave a failing Wi-Fi remains unmeasured.

## 15. Candidate solutions

| Option | Evidence | Improvement | Cost / risk | Battery / data | Tailscale | Testable |
|---|---|---|---|---|---|---|
| A. Explicit `DefaultLoadControl` so streams use real buffer thresholds (e.g. `setBufferDurationsMsForLocalPlayback`/streaming, or a scheme-correct URI) | Strong | Raises minimum reserve from ~1 s to a steady target (tens of seconds); more masking of handoffs | Low; one builder in `getLocalPlayer`; memory roughly 2 MB per 50 s of MP3, ~6 MB FLAC 16/44, ~29 MB hi-res if time-prioritized | Slightly more steady traffic; same total bytes | Neutral | Unit-test config; device A/B with same sampler |
| B. Shorter OkHttp read/connect timeouts | Observed 10 s waits | Faster failover on dead paths | Medium: hurts slow networks; evidence does not isolate which timeout matters | Neutral | Neutral | Needs degraded Wi-Fi |
| C. Custom `LoadErrorHandlingPolicy` | 56 s of retries | Faster error surfacing | Medium; interacts with #19 | Neutral | Neutral | Possible |
| D. Bounded in-place recovery on a validated route change while a load is stuck | No evidence yet: in-flight connects recovered within the timeout | Unknown | Higher; second recovery path | Low | Needs care | Needs degraded Wi-Fi repro |
| E. Recovery for buffering without a player error | Windows of ~6 s observed only inside Media3's own retries | Unknown | Higher | Low | Neutral | Needs repro |
| F. Optimize URL resolution | Resolver does no network I/O | None expected | n/a | n/a | n/a | n/a |
| G. No app change | Part of delay is OS/VPN | None | None | None | Neutral | n/a |

## 16. Recommended implementation (Phase 2B)

**Option A only**: give `ExoPlayer.Builder` an explicit `DefaultLoadControl` so streamed tracks get
a steady forward reserve and a sensible rebuffer resume threshold, keeping offline/local files on
the fast local path. Re-run the identical sampler to demonstrate that `bufAhead` no longer
decays to ~1 s before a refill, then repeat the 5 s and 60 s outages. Do not change timeouts,
retry policy, #19, #17 or #34. Options B-E remain deferred until a degraded-Wi-Fi reproduction
exists (see 17).

## 17. Unverified scenarios and limitations

- The primary reported case (Wi-Fi associated but unusable; test D, E, F1) was not reproduced.
  Recommended method: run the diagnostic build while walking away from and toward the home Wi-Fi,
  or use an authorised access point with controllable throttling/blackholing, and log the same
  timeline. Capture the Android route-change timestamp versus the first failed read.
- One 60 s outage and a handful of short samples; no statistical variability analysis.
- Hi-res FLAC and non-Tailscale paths were not tested.
- The airplane-mode outage black-holes TCP; real degraded Wi-Fi may behave differently (partial loss).
- 2 s and 10 s outages were not run.

## 18. Next-phase acceptance criteria

1. Streamed items no longer use the local-playback thresholds; a measured `bufAhead` stays above a
   documented floor during steady playback on Wi-Fi and cellular.
2. Same-track, same-position resume after the 5 s and 60 s outages; no queue changes.
3. No regression of #34 suppression, #17 watchdog, #19 recovery, offline/downloaded playback, seek.
4. Memory impact measured for MP3 320 and FLAC streams and recorded.
5. Test D/E evidence collected, or the gap explicitly carried forward.
