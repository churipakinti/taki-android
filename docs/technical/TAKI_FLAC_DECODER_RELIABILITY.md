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

## 19. Downloaded file format integrity (#39)

A download whose bitrate setting is non-zero is requested with `stream.view?maxBitRate=N`, which Navidrome
transcodes (FLAC -> Ogg/Opus). Taki named the file from the track's metadata (`suffix`), so Opus bytes were
stored as `.flac`. Download identity in Taki is the file path itself (there is no download table), so every
lookup - download state, delete, the playback `CachedDataSource`, `CacheCleaner` - assumed the original suffix.

Fix: after a download finishes, `AudioContainerSniffer` reads the first 64 bytes of the partial file and the
file is renamed to the extension of the bytes actually received (`flac`, `opus` for Ogg+OpusHead, `ogg` for other
Ogg, `mp3`, `aac`, `m4a`, `wav`; unrecognised data keeps the requested name and is never labelled). All lookups
go through `FileUtil.resolveExistingFile` / `fileVariants`, which treat the stem as the identity and try the
transcode extensions (`opus`, `ogg`, `mp3`, `m4a`), so state, delete, offline playback and cache cleaning keep
working for a renamed file. Taki does not transcode anything itself.

Behaviour kept: an existing copy (original or transcoded) is reused, never overwritten; a changed bitrate
setting does not silently replace a valid original. Known limitation: a resumed `.partial` from a different
quality than the new request is still appended to (pre-existing behaviour).

Existing mislabeled files are NOT migrated automatically. On the test device `01-Mojo Pin.flac` and
`07-Lover, You Should've Come Over.flac` are Opus-in-Ogg stored as `.flac`; they still play (the player sniffs
content). Proposed, needing approval: rename each to `.opus` (non-destructive, same stem) or delete and
re-download.

Verified on a Pixel 7: bitrate 320 download of "Last Goodbye" was saved as `03-Last Goodbye.opus` (Ogg/Opus,
48 kHz stereo); it played offline with no stream request; delete removed the `.opus`; original-quality
`06-Hallelujah.flac` still plays offline.

## 20. Safe partial-download resume (#40)

Reproduced on a Pixel 7 before the fix: a 320 kbps download of a disposable track was interrupted at
2,848,302 bytes (an Ogg/Opus partial). After switching the pinning bitrate to 0 (original), Taki sent
`download.view` with `Range: bytes=2848302-`; Navidrome answered `206` with `Content-Type: audio/flac` and a
`Content-Range` for the FLAC, and Taki appended it. The result was a 215,611,102-byte file (the size of the
FLAC) with an Opus header, labelled `.opus`: `ffprobe` duration `N/A`, `ffmpeg` "CRC mismatch / Invalid data
found". The reverse direction was already safe because Navidrome answers a transcoded `stream.view` Range
request with a full `200` (`Accept-Ranges: none`), which Taki treats as a fresh transfer.

Root cause: `DownloadTask` resumed whenever a `.partial` file existed, using only its length, and treated any
`206` as safe to append. Nothing recorded which request produced the partial.

Resume contract (`DownloadResume`, `ResumeRecord`):
- A small `<partial>.resume` sidecar is written when a transfer starts from zero: track id, endpoint
  (`download`/`stream`), max bitrate, response Content-Type, total length, ETag.
- A Range is attempted only if the partial exists, the sidecar exists and matches the new request, and the
  request is for the original representation (bitrate 0). A transcoded response is never assumed to be
  byte-identical across requests, so transcoded transfers always restart from zero; a partial without a
  sidecar (pre-fix) is restarted.
- The response is appended only if it is `206`, its `Content-Range` starts exactly at the partial length, its
  total equals the recorded total, `Content-Length` equals total minus start, Content-Type (media type) matches,
  and the ETag matches when one was recorded. Anything else (including a `206` that does not continue our bytes,
  a `416`, or a `200`) discards the partial and restarts from zero. `416` is never treated as completion.
- After the copy, if the total is known and the file length differs, the download fails and stays a recoverable
  partial; it is never renamed to a finished file.
- The sidecar is deleted on completion and on delete; `CacheCleaner` already treats `.partial.` names as cache.

Cost: one tiny text file (~100 bytes) per interrupted transfer, one extra write when a transfer starts, no
hashing or reading of the partial, and at most one extra HTTP request only when a resume is rejected.

Known limits: a partial is trusted by length plus the recorded identity and validators; its bytes are not
re-hashed. The sidecar of an abandoned partial is removed with the partial by the cache cleaner.

Pixel 7 validation of the fix (disposable tracks, Navidrome 0.64.2):
- Same-quality resume (original, in-process socket failure): the retry logged `Resuming at byte 50318356`,
  Navidrome answered `206` with `Content-Range: bytes 50318356-118459578/118459579`, 68,141,223 bytes were
  transferred, and the 118,459,579-byte result is a 24/192 FLAC whose CRC-checked decode is clean.
- Opus partial + `stream` 320 record, then an original-quality request: one request from byte 0, the planted
  partial was discarded, result `09-Eternal Life.flac`, 215,611,102 bytes, CRC-clean FLAC (the pre-fix build
  produced a corrupt 215,611,102-byte `.opus` from the same state).
- FLAC partial + `download` 0 record, then a 320 request: fresh `stream.view` (200, `audio/ogg`), result a
  11,420,144-byte Opus file (292.37 s) that decodes without errors.
- Offline (Wi-Fi and data off) playback of the resumed FLAC and the new Opus: 0 cache misses, 0 stream
  requests, state PLAYING.
Observations: the cross-quality states for the last two cases were planted on disk (a real Opus partial copied
from an existing file, plus a hand-written resume record), because Taki now discards a transcoded partial on its
own retry, so a real one cannot be switched to another quality in time. Also, `CacheCleaner.clean()` runs when
the app restarts and removes `.partial.` files, so an interrupted transfer only resumes within the running app
(existing behaviour, unchanged).

## 21. Streaming, background and format-transition validation (Pixel 7, 2026-10-18)

Source commit `c2532ba5`. Debug build `0.1.0-beta` (versionCode 131), package `...taki.debug`, Pixel 7,
Android 17, Media3 1.10.1, NextLib 1.10.1-0.13.0 (FFmpeg `Lavc60.3.100`), Navidrome 0.64.2 over Tailscale,
Wi-Fi, USB-powered. Not repeated: the offline 33-minute soak. All results are `PIXEL_VERIFIED` unless stated.
The phone was charging, so there is no battery or real Doze result (`deviceidle` stayed `ACTIVE`).

### 21.1 Forcing a real network stream (the local `.opus` problem)
`07-Lover...opus` is a transcoded copy (#39) and `CachedDataSource` resolves it by stem, so asking for the
original would have played the local file. Method used (smallest, non-destructive, never committed):
- A temporary, uncommitted hook in `CachedDataSource.checkCache` returned "no cache hit" for stems listed in a
  file under the app's private `files/` dir (`val_bypass.txt`, read on every open). Only five Grace stems
  (`07-Lover`, `06-Hallelujah`, `11-Forget Her`, `10-Dream Brother`, `08-Corpus Christi`) were listed; the local
  files were not touched (sizes re-listed after the run: identical).
- A temporary OkHttp network interceptor logged method, path, non-credential query keys, status, content type and
  length/range headers (no host, no credentials). A second marker file (`val_diag`) enabled `EventLogger`, a
  one-off FLAC `STREAMINFO` bit-depth decode and a poll of `audioDecoderCounters`.
- All of it was removed afterwards (see 21.9). Production resolution policy was not changed.

Proof that the Lover stream is the original 24/192 FLAC:
1. Request: `GET /rest/stream.view` with only `id` (no `maxBitRate`, no `format`); the app's streaming bitrate
   for Wi-Fi was 0 (original).
2. Response: `200`, `Content-Type: audio/flac`, `Content-Length: 287632969`, equal to the library `size` for the
   track; no transcode (a transcode would be `audio/ogg`, as the `stream` 320 case showed in section 19).
3. `CachedDataSource` logged the bypass line and `No cache hit, forwarding call`; the local `.opus` was never opened.
4. Media3 input format: `audio/flac`, 192000 Hz, 2 ch, `pcmEnc=21` (24-bit), STREAMINFO bits per sample = 24.
5. Decoder: `audioDecoderInitialized ... ffmpegLavc60.3.100-flac`.
6. AudioFlinger track for the app's pid: 192000 Hz, PCM_16, frames served increasing at real time, underruns 0
   (the output is 16-bit: the FFmpeg path does not give bit-perfect 24-bit output).
Result: `ORIGINAL_24_192_STREAM_CONFIRMED` = yes.

### 21.2 154.2 s check and FFmpeg packet message
The Lover track was played from 0 four times, to its natural end each time (404 s):

| Run | Build | Result at the 154 s frame |
|---|---|---|
| A | bypass + network log only | played through; **no** `ffmpeg_jni` error in the full logcat covering the crossing |
| B | + diagnostics (started at 119 s, then seeked to 0) | 2 x `E ffmpeg_jni: Error in avcodec_send_packet: Invalid data found` at 13:31:30.594; `skippedOutputBufferCount` 0 -> 2 at position ~153.85 s (decoder ~0.5-0.8 s ahead of output); `rendered=6443` |
| C | + diagnostics, no seek (after another track) | same pair of errors, `skipped=2`, `rendered=6431` |
| Soak | bypass + network log only, screen locked | same pair at 14:18:07, during Lover |

- **No stall in any run**: position kept advancing past 154.2 s and to the end, `isPlaying` true, state READY,
  AudioFlinger underruns 0, no watchdog log, no recovery seek, no player error, no queue change.
- The message appears in 3 of 4 runs including a clean build. Run A is an unexplained outlier; it is not
  evidence that the anomaly is gone. Likely nondeterministic (timing or packetisation); unproven.
- No other track produced a packet message: the other Grace tracks, the 24/96 files and the Opus/MP3 tracks
  played with none (the full soak log has one pair only, at Lover).
- Characterisation: 1-2 packets rejected at frame ~#6425 (4608 samples = 24 ms at 192 kHz; the earlier measurement
  was a ~22 ms discontinuity). Whether `skipped=2` is two frames or one frame counted twice is unresolved. The
  decoder recovers on the next packet; `decoderInitCount` stayed 1.
- Not verified: sample-accurate integrity. The user's earlier A/B listening (no audible interruption) stands as
  `USER_LISTENING_VERIFIED`; it is not proof of bit-perfect decoding. No FFmpeg or NextLib change was made.
- Severity: low (one short dropout, no stall) but unexplained. It does not undo the stall fix. Recommendation:
  do not block the stall fix on it, and keep it recorded as an open integrity question for #17.

### 21.3 Long-duration streaming, screen locked (clean build, no diagnostics)
Grace album from track 1, 13:43:48-14:41:00 = **57 min 12 s**, 11 tracks, screen off the whole time (no wakelock),
app in background, one process (pid unchanged, never killed), `MediaSession` present throughout.
- Sources: 3 local Opus (48 kHz), 8 streamed original 24/192 FLAC (192 kHz; all `200 audio/flac`). Transitions
  Opus <-> 24/192 FLAC all advanced automatically and in order (queue index 0..10).
- 304 samples every ~10 s: 0 non-zero AudioFlinger underruns, 0 unexpected pauses, state PLAYING until the
  correct end of queue (STOPPED). No stall-watchdog log, no player error, no `ffmpeg_jni` message other than the
  Lover pair.
- Decoder identity in this run is inferred from the AudioFlinger sample rate class (192000 = FFmpeg, 48000 =
  Opus) plus the Lover message; exact names come from the diagnostic runs (21.5). Compressed-track coverage here
  is Opus; streamed MP3 and ordinary FLAC are covered in 21.5.
- Resources (clean build; `dumpsys meminfo` TOTAL PSS and one `top` CPU sample per ~10 s, so noisy): PSS
  226-272 MB, mean 238 MB; CPU samples 0-78 %, mean 29 %. No growth over 57 min. For comparison, Phase 1B
  offline reported 277-289 MB. PSS drops to ~160-175 MB while the UI is backgrounded and the app is trimmed.

### 21.4 Lifecycle and controls (diagnostic build, Hallelujah and Grace queues, 24/192 streams)
- Lock, unlock, home, foreground: position advanced in real time through each, no decoder reinit, no state change.
- Notification (quick-settings media card): Pause, Play (resumed at the same position, 110.55 s), Previous. Paused
  for 83 s with no automatic resume. Media keys and `cmd media_session dispatch`: pause, play, play-pause toggle,
  fast-forward (+5 s), rewind (-5 s), next (no-op at the end of the queue) all correct.
- Lock-screen media card (the device has a secure lock): Next, Pause, Play, Previous worked. Earlier attempts
  failed only because the screen had timed out before the tap.
- Seek forward 122.9 -> 253.0 s and back 264.3 -> 121.2 s via the seek bar: correct, no watchdog log.
- Previous: the first press after the start restarts the track (Media3 behaviour), a second press within 3 s goes
  to the previous item; Next followed by a quick Previous returns to the earlier item.
- Pause near a transition (Eternal Life, 287.7 s of 292 s): paused 6 s, resumed at 287.70 s, then advanced by
  AUTO_TRANSITION into the next 24/192 track with the decoder swap Opus -> FFmpeg.
- Seek to 97 % then natural end: 24/192 FLAC -> Opus on its own, correct next item.
- Seek to 50 % then Next: correct next item.
- Locked playback for 3 min 40 s, then return to the app: position in sync (4:17 shown), no restart.
- Not exercised: Bluetooth or second-device controls, and #34 audio-focus interplay with another app (no
  AudioManager change was made).

### 21.5 Mixed-format transitions (diagnostic build; exact decoder from `audioDecoderInitialized`)
Sources identified from stream/format logs, not file names. In this library the downloaded `Stromae/Cheese/*.flac`
files are Opus (pre-#39 mislabel), and no 44.1 kHz FLAC was found; the ordinary FLAC used is Geography (FLAC
16-bit 48 kHz, streamed, `c2.android.flac.decoder`).

| Transition | Result | Decoders |
|---|---|---|
| MP3 -> ordinary FLAC (16/48) | pass | `c2.android.mp3` -> `c2.android.flac` |
| ordinary FLAC -> MP3 | pass | `c2.android.flac` -> `c2.android.mp3` |
| MP3 -> 24/192 FLAC | pass, ~0.28 s | mp3 -> `ffmpegLavc60.3.100-flac` |
| 24/192 FLAC -> MP3 | pass | ffmpeg -> mp3 |
| Opus -> 24/192 FLAC | pass, 0.24-0.33 s | `c2.android.opus` -> ffmpeg |
| 24/192 FLAC -> Opus | pass | ffmpeg -> `c2.android.opus` |
| 24/192 -> another 24/192 FLAC | pass | ffmpeg re-created per track |
| 24/192 -> 24/96 FLAC | pass | ffmpeg (96000 Hz) |
| 24/96 -> ordinary FLAC | pass | ffmpeg -> `c2.android.flac` |
| ordinary FLAC <-> 24/192 FLAC (both directions) | pass | `c2.android.flac` <-> ffmpeg |
| Opus -> MP3, MP3 -> Opus, ordinary FLAC -> Opus | pass | platform decoders |
| Manual Next (~100 presses over two queues), Previous, seek then Next, pause near a transition, natural completion | pass | see 21.4 |

Each transition selected the requested queue item (index +1 per press, correct title/album/suffix), began audio
(`audioTrackInit` 0.19-0.43 s after the key press, `adb` latency included, preloaded items), kept the queue intact,
and showed no stall watchdog and no persistent buffering. Opus items were local, the rest streamed; the 24/192
items were also played offline in earlier phases. Not claimed: gapless playback (no suitable material, no
measurement). One expected result: streaming a `Stromae` track with its local copy bypassed returned a 182-byte
JSON error (the server no longer has that file) and the app showed its existing "Couldn't play this track" dialog
(`ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE`). That came from forcing a stream of a missing file, not a regression.

### 21.6 Other high-resolution rates
- 24-bit/96 kHz FLAC: **verified** with two albums (Random Access Memories, Discovery). `One More Time` played
  start to end (320 s): AudioFlinger 96000 Hz, underruns 0, no packet message, FFmpeg selected.
- 24-bit/192 kHz: verified (Grace, 8 tracks).
- 24-bit/88.2 kHz: **UNTESTED** (no such file found in a limited probe of about 12 albums). 176.4 kHz: not found.

### 21.7 Findings and remaining limitations
- The stall fix holds for streaming, locked, 57-minute and mixed-queue playback on this build.
- The FFmpeg packet message at ~154.2 s of the Lover file recurs (3 of 4 runs). Cause unknown; not audible in
  the earlier A/B; integrity is not claimed.
- The FFmpeg path outputs 16-bit PCM for 24-bit sources, so playback is not bit-perfect.
- Charging device: no battery or real Doze data; PSS and CPU are coarse samples.
- Debug build only at the time of this section; the release package was validated afterwards (section 22).

### 21.8 Not touched
No source change is committed by this validation. Offload and watchdog policy, #19/#34/#39/#40 behaviour and the
NextLib dependency are unchanged. Automated gates were not re-run because there is no application code change.

### 21.9 Restoration
The diagnostics (cache-bypass hook, network log, event logger, counters poll) and one older uncommitted
`EventLogger` line that was already in the working tree were reverted with `git checkout`; the marker files were
deleted from the app's private dir; a clean `assembleDebug` of `c2532ba5` was reinstalled (versionCode 131; the
APK contains none of the diagnostic strings). `git status` was clean before this document was added.

## 22. Release-build validation and #17 closure (Pixel 7, 2026-10-18)

Source: develop `6364d415` (application code unchanged since `c2532ba5`; `6364d415` only adds documentation).
Device: Pixel 7, Android 17, Wi-Fi, USB-powered, Navidrome 0.64.2 over Tailscale. Media3 1.10.1,
NextLib 1.10.1-0.13.0 (FFmpeg `Lavc60.3.100`).

### 22.1 Artifact and installation
- The pre-existing `Taki-0.1.0-beta-release.apk` (built 2026-10-17 20:38) pre-dated the last code commit
  (`af880413`, #40 at 20:39), so it was **not** used. A fresh `:ultrasonic:assembleRelease` from `6364d415` was
  built (30,340,265 bytes, SHA-256 `431f8c45...d793e`), signed with the project's own release key (cert CN
  "Taki Release"; no key material was read), not debuggable, `libavcodec.so`/`libmedia3ext.so` (arm64) present,
  and none of the temporary diagnostic strings (`VALNET`, `VALDEC`, `VALLOG`, `val_bypass`) in any dex.
- Release application ID `io.github.churipakinti.taki`, `versionName 0.1.0-beta`, `versionCode 131`. The debug
  build is `io.github.churipakinti.taki.debug`; they are separate packages, so installing release did not touch
  debug. After installation the 8 downloaded Grace files in the debug profile had the same sizes as before.
  Nothing was uninstalled, cleared or overwritten. The release app launched without a crash (empty crash buffer).
- The release profile starts empty (no library, no downloads): the user logged in with their own credentials on
  the Add-library screen, and unlocked the phone once, for the lock-screen return step. No credential was
  requested, read or copied by the validation.

### 22.2 Original 24/192 FLAC, release (Lover, You Should've Come Over)
No instrumentation was added to the release build; evidence is non-intrusive.
- Source: the release profile has no local copy (0 files), so the track streamed. AudioFlinger showed the app's
  track at **192000 Hz** (a 320 kbps Opus transcode would be 48000 Hz). Network receive on the VPN interface
  during the last ~324 s was ~215 MB (the whole original is 287.6 MB; a 320 kbps Opus is ~16 MB).
- Decoder: not directly observable in release (no Timber/decoder log). Non-intrusive evidence: 192 kHz and the
  `E ffmpeg_jni` native log tag, which only the FFmpeg path produces. Stated as inferred, not observed by name.
- Playback from 0:00 with no seeking: audio position advanced continuously to 400.6 s, session `STOPPED` at
  404,218 ms (natural end), 0 AudioFlinger underruns, no unexpected seek, no queue change (single-item queue).
- 154.2 s: no stall. One pair of `avcodec_send_packet: Invalid data found` at ~154 s (17:45:40.26) - the known
  packet message (#41); playback continued. Sole `ffmpeg_jni` message of the whole release session (about 35 min of
  other 24/192, 24/96, Opus, MP3 and FLAC playback in that session produced none).
- PASS: original high-res track played through the historical failure point to its natural end without a stall.

### 22.3 Release smoke test (sample rate via AudioFlinger, title via the app's own MediaSession)
| Check | Result |
|---|---|
| Ordinary FLAC (Geography, 16/48 -> 48000 Hz) | plays, Next x13, Previous (restart, then previous item), pause/resume at same position |
| Seek forward/back (Now Playing bar) | 93.2 s forward, 32.9 s back, correct |
| ordinary FLAC -> 24/192 | pass (48000 -> 192000 Hz, Mojo Pin) |
| 24/192 -> MP3 | pass (192000 -> 44100 Hz) |
| MP3 -> Opus | pass (44100 -> 48000 Hz) |
| Opus -> 24/96 | pass (48000 -> 96000 Hz) |
| MP3 -> 24/192 | pass (44100 -> 192000 Hz) |
| 24/96 natural advance (seek to 97 %) | advanced to the next track, position kept |
| Automatic advance 24/192 -> 24/192 | Hallelujah -> Lover on its own (locked) |
| Screen lock / 13 min locked background | queue Hallelujah, Lover, Corpus Christi Carol, Eternal Life (24/192 streams), same process, `MediaSession` present, 0 underruns; the app was unlocked by the user afterwards and was still playing |
| Lock-screen card | Pause, Play, Next worked (taps must follow wake within seconds) |
| Notification shade card | Pause, Play (same position), Next worked; Previous restarted the track (>3 s) |
| Return to foreground | app resumed with playback uninterrupted |
- 220 samples over the smoke run: 0 non-zero underruns. Two samples caught a transient `BUFFERING`/no-audio state
  while the test skipped streamed tracks every ~3.4 s; no persistent buffering, no crash, no unexpected restart.
- Resources (coarse, one `dumpsys meminfo`/`top` sample per ~5 s; release): Lover alone PSS 147-179 MB
  (mean 149), CPU samples 4-73 % (mean 45 %); smoke run (all formats, background included) PSS 112-228 MB
  (mean 144), CPU mean ~20 %. Debug soak had PSS 226-272 MB; the release app is lighter. No decoder
  initialisation failure and no crash (`FATAL`, ANR, process death: none). The phone was charging: no battery
  or Doze result.

### 22.4 Result and limits
- Release validation **passed** for the original 24/192 FLAC and the mixed-format/background/control checks.
- The original silent stall at ~154.2 s no longer reproduces: 4 debug passes (one inside the 57 min soak) and 1 release pass all crossed it.
- Limits: decoder identity in release is inferred; ordinary-FLAC/MP3/Opus decoder names rely on the debug
  diagnostic runs (section 21) plus sample rates; 24/88.2 untested; 16-bit output (not bit-perfect); gapless not
  claimed; Bluetooth and other-app audio focus not exercised.

### 22.5 Separate follow-ups (not part of the stall)
- #41 - FFmpeg rejects a packet near 154.2 s of one file (cause unknown, integrity not claimed).
- #42 - 24-bit FLAC reaches Android as 16-bit PCM: where the conversion happens is inferred (decoder, from
  `shouldOutputFloat` in the shipped NextLib jar) but not measured; no bit-perfect claim.
