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

---

# Release-readiness follow-up (second pass)

Done in this pass: licensing audit, ABI packaging, test hardening. **Not done: everything that needs the
Pixel 7** - it was not connected to the build machine during this pass (see section 18).

## 14. Dependency licensing assessment (verified from the shipped binaries)

| Component | Version | Evidence | License |
|---|---|---|---|
| NextLib wrapper (`media3ext`) | tag `v1.10.1-0.13.0` | POM + repo `LICENSE` | GPL-3.0 |
| FFmpeg (`libavcodec/avutil/swresample/swscale`) | **6.0** | `Lavc60.3.100` and "FFmpeg version 6.0" strings in the .so files; tag's `ffmpeg/setup.sh` has `FFMPEG_VERSION=6.0` | LGPL-3.0-or-later |
| libvpx | 1.13.0 | tag's `setup.sh` (`VPX_VERSION=1.13.0`), `--enable-libvpx` in the embedded configure line | BSD-3-Clause |
| mbed TLS | 3.4.1 | tag's `setup.sh`, `--enable-mbedtls` | Apache-2.0 OR GPL-2.0-or-later |

Why LGPL and not GPL: the configure line embedded in `libavcodec.so` (read directly) contains
`--enable-version3` and contains neither `--enable-gpl` nor `--enable-nonfree`; no GPL-only or nonfree
libraries (x264/x265/fdk-aac/lame) are linked (the string `x264` found in the binary is the H.264 decoder's
`x264_build` field). Built with NDK 25.2.9519653 on a GitHub Actions runner, shared libraries, 16 KB page
aligned, `--disable-asm`.

Obligations and how they are met:
- LGPL-3.0 notice + license reference + source pointer: added to `NOTICE.md` (FFmpeg 6.0 source URL,
  NextLib tag/build script, component versions).
- Replaceability of the LGPL library: the FFmpeg libraries are separate shared objects loaded dynamically,
  and Taki (GPL-3.0, full source published) can be rebuilt against a modified FFmpeg/NextLib.
- GPL-3.0 compatibility: LGPL-3.0, Apache-2.0 and BSD-3-Clause are all compatible with Taki's GPL-3.0.
- No in-app notice was added (no UI change); `NOTICE.md` is the documentation. Adding a line to the About
  screen is a possible follow-up.

Remaining uncertainties (not blockers I can prove, but not certified either):
- I verified the **binary and the tag's build script**, not NextLib's CI logs; the exact libvpx/mbedtls
  object code is inferred from the script and configure string.
- The AAR contains no license file; the notices live in `NOTICE.md` and upstream.
- FFmpeg 6.0 is an old release with published CVEs; Taki only feeds it FLAC from the user's own server
  (protocols are compiled in but unused). Updating means bumping NextLib (its newer tags use FFmpeg 9.x)
  together with Media3.
- This is an engineering assessment, not legal advice.

Conclusion: no distribution blocker found; `DEPENDENCY_LICENSING_REVIEW_COMPLETE` is YES on that basis.

## 15. Packaging and ABI measurements (measured)

Default build (unchanged): one universal APK. New opt-in `-PabiSplits` adds one APK per ABI and still
builds the universal one (`splits.abi` in `ultrasonic/build.gradle`; output names carry the ABI).

| Artifact (release, signed) | Size |
|---|---|
| Baseline universal APK (before the decoder, e66b15cd) | 6,462,728 B |
| Universal APK with FFmpeg | 30,340,265 B (+23.9 MB) |
| arm64-v8a split | 12,019,176 B (+5.6 MB vs baseline) |
| armeabi-v7a split | 11,589,044 B |
| x86 split | 12,974,808 B |
| x86_64 split | 12,988,262 B |

`CI` (`ci.yml`) runs `assembleRelease` without the flag and uploads only the debug APK, so it is unaffected.
The documented release flow (`RELEASE_SIGNING.md`) expects the APK(s) in
`ultrasonic/build/outputs/apk/release/` and uses globs; both layouts satisfy it. Recommendation: publish the
arm64-v8a split to modern phones and keep the universal APK as the compatibility download; an AAB would do
this automatically for a store, but Taki is distributed as direct APKs. Installed size on the device was
**not measured** (no device).

## 16. R8 / release package checks (done)

The release dex keeps `FfmpegAudioRenderer`, `FfmpegLibrary`, `FfmpegAudioDecoder` and
`HiResFlacOnlyRenderer`; `lib/<abi>/libavcodec.so`, `libavutil.so`, `libswresample.so`, `libswscale.so`,
`libmedia3ext.so` are packaged for all four ABIs; the signing certificate verifies on the universal and
the arm64 split. Running the release build on the phone was not possible (see 18).

## 17. CPU note

`--disable-asm` in NextLib's FFmpeg build means no NEON assembly; this is the likely reason FFmpeg used
~59-68 % of a core vs ~33-42 % for the platform decoder on 24/192 FLAC. Not optimised (out of scope); the
cost is paid only while a file at >= 88.2 kHz plays.

## 18. Device validation (Pixel 7) and remaining gaps

### Verified on the device
- Decoder identity: `onAudioDecoderInitialized` reports `ffmpegLavc60.3.100-flac` for audio/flac 192 kHz,
  2 ch, 24-bit; ordinary tracks still log `setOffload: offload = true` with offload enabled.
- Streaming playback of a 24/192 FLAC (404.2 s, 287.6 MB) runs through 154 s with no stall and no watchdog
  nudge (the platform decoder needed one). Seek, pause/resume via media key, and Next work.
- A media-key resume with the screen off played the tail of the hi-res track to its natural end.
  This is a short run, not a long locked-screen soak.
- Peak memory is no higher than with the platform decoder (PSS ~312-318 MB vs ~338 MB).

### Packet near 154 s (investigated, cause NOT resolved)
Confirmed:
- The source file (captured byte-for-byte from the live stream) is a valid FLAC: 16,843 frames of 4608
  samples, a CRC-checked desktop FFmpeg decode reports no errors, no false sync candidates, and the declared
  maximum frame size equals the real maximum.
- The frame at 154.200 s (#6425, 17,502 bytes, right/side stereo) is where the in-app FFmpeg 6.0 decoder
  rejects one packet. Measured effect: a ~22 ms discontinuity.

Strong inference: the file is not damaged; the rejection is specific to the in-app decoder/extractor
path on that frame (right/side frames are common in the file, 386 of them, so the channel mode alone is not
the explanation).

Unverified: whether the ~22 ms gap is audible. No human listening comparison was possible in this session.
**Audio integrity is therefore not claimed.**

### Offline hi-res playback (verified, Pixel 7, 2026-10-17)
Error 70 was **not reproduced** through Taki's own download path (issue #38), so its original cause is
still unknown; the earlier error-70 responses came from ad hoc requests made outside the app.
- Taki's pinned download uses `download.view` when the pinning bitrate is 0 (the default, "original"), and
  `stream.view` with `maxBitRate=N` otherwise. This device had the pinning bitrate at 320.
- With 0: `download.view` returned `206`, `audio/flac`, 268,748,292 bytes; the file on the device is a
  24-bit/192 kHz FLAC (ffprobe), size equals `Content-Length`, and a CRC-checked decode reports no errors.
- With the network disabled, the downloaded file was opened from the local path (no stream request),
  decoded by `ffmpegLavc60.3.100-flac`, seeked to 2:20, played continuously across 154 s with no stall,
  and paused/resumed. No playback errors in logcat.
- Not verified: a byte-for-byte hash against the server original (no second trusted copy available).
- Side finding (separate defect): with a non-zero pinning bitrate the server transcodes (Opus in Ogg for
  320) and Taki saves it under the original `.flac` name. Playback still works, but the extension is
  misleading. Two such files (`01-Mojo Pin.flac`, `07-Lover...flac`) were created by this test and remain
  on the test device.

### Not tested
Long locked-screen soak, Next/Previous across mixed formats, 24/96 and 24/88.2 files, AAC/Opus, sustained
battery, a release-build run on the device (release uses a separate package and needs a login).

### Gates
`testDebugUnitTest`, `:core:subsonic-api:test`, `assembleDebug`, `assembleRelease`, `lintDebug` and
`lintVitalRelease` pass. detekt (`-Pqc`) reports 43 issues, below the earlier 47-49 count.

### Status
Decoder implemented and active on the Pixel; streaming and offline playback verified. Listening comparison
(user, around 2:30-2:40: Taki and Feishin both without audible interruption) is a passed subjective check,
not proof of bit-perfect decoding. The wider device matrix is open, so the #17 fix is **not** declared complete.
