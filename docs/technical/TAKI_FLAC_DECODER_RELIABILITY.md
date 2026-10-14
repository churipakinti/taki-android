# Hi-res FLAC decoder reliability (issue #17)

## 1. Original failure

Jeff Buckley - "Lover, You Should've Come Over" (FLAC 24-bit/192 kHz, ~5.7 Mbps, ~287 MB, 6:44)
freezes at about 2:34 (154.2 s) on a Pixel 7. No `onPlayerError` is ever raised. The same file plays
in Feishin, and a transcoded copy plays in Taki.

## 2. Existing mitigation (unchanged)

`PlaybackStallWatchdog` (FIX B, commit 3b281c2a) detects a frozen position with data buffered ahead and
nudges past it. It is a backstop, not a decoder fix. In the baseline run below it fired once at
154,232 ms and recovered, skipping about one second of audio.

## 3. Baseline reproduction (before this change, commit e66b15cd, Pixel 7, Android 17, Media3 1.10.1)

Wi-Fi quality unlimited (raw FLAC stream), offload off. Seek to ~2:20 and play on.

- Decoder created: `c2.android.flac.decoder` (logcat `CCodec: Created component [c2.android.flac.decoder]`).
- Position froze at 154,200 ms with ~30 s buffered ahead; the watchdog logged
  `Playback stall detected at 154232 ms ... nudging to 155232 ms and re-preparing`.
- Platform decoder CPU on this file: about 33-42 % of one core; process PSS ~338 MB.

## 4. Options compared

| | NextLib `nextlib-media3ext` (prebuilt FFmpeg) | Media3 FLAC extension from source (libFLAC) |
|---|---|---|
| Availability | Maven Central, `1.10.1-0.13.0` (prefix = exact Media3 1.10.1) | No prebuilt AAR from Google at any version; NDK + CMake build required |
| Size | AAR 10.4 MB; arm64 .so about 5.5 MB uncompressed | ~0.3 MB per ABI (estimate from the original investigation, not measured) |
| License | GPL-3.0 (POM) - compatible with Taki's GPLv3 | libFLAC BSD + Apache-2.0 |
| Reproducibility | Prebuilt binary, SHA-1 `ef0262e4d72efffa5cd45698636bc2c6cec97677`; source in upstream repo | Rebuildable from source, but needs a native toolchain this machine does not have |
| Maintenance | Bump together with Media3 (version prefix) | Rebuild and re-vendor the AAR on every Media3 bump |

**Chosen: NextLib.** It is the smallest change that was actually verified to fix the stall on the device,
needs no native toolchain or vendored binary, and its version scheme ties it to Media3. The cost is
binary size (section 10) and a third-party prebuilt FFmpeg. If size becomes a problem, the source-built
libFLAC extension is the documented alternative.

## 5. Implementation

- `gradle/libs.versions.toml`, `ultrasonic/build.gradle`: `io.github.anilbeesetti:nextlib-media3ext:1.10.1-0.13.0`.
- `service/HiResFlacRenderers.kt`:
  - `requiresSoftwareFlacDecoder()` - true only for `audio/flac` at **>= 88,200 Hz** (88.2/96/176.4/192 kHz).
  - `HiResFlacOnlyRenderer` - a `ForwardingRenderer` around NextLib's `FfmpegAudioRenderer` (a `final` class)
    whose capabilities claim only those files; everything else reports `FORMAT_UNSUPPORTED_TYPE`.
  - `TakiRenderersFactory` - `DefaultRenderersFactory` plus that renderer inserted **ahead of**
    `MediaCodecAudioRenderer` (renderer order is preference order). If the native library cannot load
    nothing is added and playback is exactly as before.
- `PlaybackService.getLocalPlayer()`: `ExoPlayer.Builder(this, TakiRenderersFactory(this))`. Load control,
  data sources, audio attributes, seek intervals and offload handling are untouched.

`EXTENSION_RENDERER_MODE_ON` was deliberately not used: it only swaps in an extension when the platform
decoder fails to initialize, and this failure is a silent stall. Routing by format keeps MP3, AAC, Opus and
ordinary FLAC on the platform decoders, so their hardware offload path is unchanged.

## 6. Audio offload

`Settings.useHwOffload` enables Media3 offload (mode ENABLED, not REQUIRED). Offload only applies to
compressed output from the platform renderer; the FFmpeg renderer outputs PCM. Verified on the device
with offload **on**: hi-res FLAC still used FFmpeg and passed 2:34; ordinary tracks still reported
`setOffload: offload = true`. Enabling offload therefore cannot bring the stall back.

## 7. Pixel 7 results (final build)

| Check | Result |
|---|---|
| Decoder for the 24/192 file | FFmpeg (NextLib). No `c2.android.flac.decoder` was created for it; `ffmpeg_jni` logs appear; platform FLAC decoder is only created for ordinary FLAC tracks. Name stated from log evidence, not from `getAudioDecoderCounters`. |
| Stream, play through 2:34 | Position checkpoints continuous 143 s -> 263 s, **0 watchdog events** |
| Offload on | Continuous 142 s -> 182 s, 0 watchdog events |
| Seeks 2:15, back to 2:20, 2:34, 5:00 | All continuous, 0 watchdog events, 0 player errors; reached 5:10 |
| Ordinary tracks (offload on) | MP3/FLAC tracks play, seek, pause/resume via media keys, advance with Next; offload active; 0 stall/errors |
| One FFmpeg message per crossing | `avcodec_send_packet: Invalid data found` once each time the 154 s frame is decoded (3 crossings in the seek test). FFmpeg rejects that single packet and continues; the platform decoder hangs on it. This suggests one damaged frame in the file. A brief dropout of that frame is possible; it was not evaluated by ear. |

## 8. Not verified

- **Offline original:** the original 24/192 file could not be downloaded here: the server answered the
  download with API error 70 (data not found) for that track, a pre-existing download-path issue outside
  this change. Offline playback of the hi-res original is therefore **unverified**. The renderer is chosen
  from the stream's `Format`, not from the data source, so the same path is expected, but that is inference.
- 24/96 FLAC, AAC/Opus-specific checks, background/locked-screen playback of the hi-res file, track-boundary
  transitions and battery were not exercised on the device.
- Release APK was built and inspected but not run (its package is not installed and needs its own login).

## 9. Resources (Pixel 7)

- CPU while playing 24/192: FFmpeg 59-68 % of one core vs platform decoder 33-42 %: about +25-30 points,
  only while such a file plays.
- PSS: ~312-318 MB (FFmpeg) vs ~338 MB (platform) - no increase; process-level noise dominates.
- No new buffers; `PlaybackLoadControl` unchanged.

## 10. Package size

Universal release APK: 6,462,728 B -> 30,340,265 B (+23.9 MB, about 4.7x). The FFmpeg `.so` files for all four
ABIs are packaged uncompressed: arm64-v8a 5.54 MB, armeabi-v7a 5.11 MB, x86 6.34 MB, x86_64 6.52 MB
(libavcodec/avutil/swresample/swscale/media3ext). A device installs only its own ABI when delivered as an
AAB/ABI split (about +5.5 MB on arm64). Taki has no ABI filters/splits today; adding splits for the GitHub
APK is the obvious follow-up. No ABI was dropped.

## 11. Licensing and provenance

NextLib is GPL-3.0 (declared in its POM), same as Taki. It bundles a build of FFmpeg (LGPL/GPL depending on
configuration) as prebuilt binaries; the wrapper's POM does not itemise FFmpeg's terms. **Distribution
obligations for the bundled FFmpeg were not independently audited** and should be reviewed (notices, and
source availability via the NextLib repository) before a public release. Artifact: Maven Central,
`io.github.anilbeesetti:nextlib-media3ext:1.10.1-0.13.0`, SHA-1 as above.

## 12. Future Media3 upgrades

Keep `nextlib` in `libs.versions.toml` on the version whose prefix equals Media3 (e.g. `1.11.1-0.16.0` for
Media3 1.11.1). `HiResFlacOnlyRenderer` depends on `ForwardingRenderer`, `RendererCapabilities` and
`DefaultRenderersFactory.buildAudioRenderers`, all `@UnstableApi`.

## 13. Tests

`HiResFlacRenderersTest` (8): routing rule by MIME/sample rate, gated renderer claims only hi-res FLAC and
never asks the delegate otherwise, name/track type preserved, and the JVM "library unavailable" case still
has the platform audio renderer and no software one. Real decoding correctness needs the device (section 7).
