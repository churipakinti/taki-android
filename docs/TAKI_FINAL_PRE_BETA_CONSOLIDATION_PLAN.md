# TAKI — Final Pre-Beta Consolidation Plan

## Purpose

This document consolidates the remaining work before the first real beta.

The objective is **not** to redesign Taki again.

The objective is to:

1. close the remaining confirmed functional issue;
2. finalize app identity before external distribution;
3. simplify the last confusing Settings/onboarding decisions;
4. produce a clean candidate;
5. review the whole result together on the Pixel 7;
6. stop product changes and move to beta testing.

## Product principle

> **Taki should let a non-technical person connect to a personal music library and listen with minimal friction, without needing to understand the infrastructure behind it.**

Supporting rule:

> **Technical complexity belongs to the server and inside Taki, not in the normal user experience.**

Do not remove useful functionality merely because it is advanced.

Do not add configuration merely because an internal capability exists.

---

# Working strategy

Do **not** make one giant unverified change.

Work in small, isolated blocks.

After each block:

- run the relevant unit/build checks;
- verify the code path enough to ensure the block is technically sound;
- commit the block separately.

Do **not** do a full Pixel UX pass after every small change.

Instead:

```text
small implementation block
→ targeted technical verification
→ commit
→ next block

when all blocks are complete
→ install one integrated candidate on Pixel 7
→ run one consolidated device review
```

Exception:

If a change affects downloads/retries, app identity/installability, first-run connection, or playback behavior, perform a short targeted device verification immediately if practical. The final Pixel pass is still mandatory.

---

# Phase 1 — Fix DownloadService retry lifecycle

## Priority

```text
P0 / PRE-BETA
```

## Confirmed problem

Current retry flow can stop `DownloadService` after the first failed attempt before the retry is processed.

Trace:

```text
download fails
→ RETRYING
→ active task removed
→ processNextTracks()
→ service sees empty active/queue state
→ stopSelf()
→ retry item is added afterward
```

## Goal

A failed user-requested download must either:

- retry correctly up to the configured limit;
- recover if connectivity returns;
- eventually reach a real `FAILED` state;
- remain retryable by the user.

## Constraints

Do not refactor `DownloadService` broadly.

Make the smallest reliable fix to ordering/lifecycle/state handling.

## Verify

Test:

```text
1. successful normal download
2. fail first attempt → retry occurs
3. multiple consecutive failures
4. configured retry limit is respected
5. connectivity restored during retry cycle
6. terminal FAILED state is reached
7. FAILED icon/message can now be exercised
8. manual retry after FAILED works
9. service stops normally after queue is truly finished
```

Add or update regression tests where practical.

---

# Phase 2 — Finalize Android app identity

## Priority

```text
P0 / BEFORE EXTERNAL DISTRIBUTION
```

Distinguish:

```text
namespace / Kotlin packages
vs.
applicationId
```

Do **not** perform a mass package rename.

## Goal

Decide and implement Taki's final installable `applicationId` before external testers receive the beta, unless there is a strong documented reason not to.

Example intent:

```text
applicationId = Taki-specific identity
namespace = may remain org.moire.ultrasonic for now
```

## Audit before changing

Locate dependencies on the old application id, including:

- manifest/provider authorities;
- notification channels if hard-coded;
- FileProvider/search provider authorities;
- app shortcuts;
- backup rules;
- deep links/intents;
- tests;
- build scripts;
- any persisted paths or assumptions;
- debug `applicationIdSuffix`.

Do not change namespace/packages unless required.

## Verify

- clean install works;
- debug and release variants build;
- providers/authorities do not collide;
- notifications work;
- Search suggestions work;
- Android Auto still recognizes the app;
- release signing still works.

If changing `applicationId` means old internal builds cannot update in place, document that explicitly.

---

# Phase 3 — First-run flow: connect directly

## Priority

```text
P1 / PRE-BETA UX
```

Target:

```text
Taki
Connect your music library

Library address
Username
Password

[ Connect ]
```

## Change

Remove the first-run Welcome / Not now decision if the user has no configured library.

On first run:

```text
launch
→ connection screen directly
```

Reuse the existing simplified new-library form.

Do not create a new onboarding system.

## Verify

- clean install opens connection screen;
- Connect validates, saves, activates library and reaches Home;
- back behavior is sensible;
- failed connection leaves user on the form with understandable feedback;
- normal subsequent launch does not reopen onboarding unnecessarily.

---

# Phase 4 — Finalize Settings information architecture

## Root

Keep:

```text
Settings

Playback
Downloads
Language
Advanced
About
```

Do not create more top-level sections.

## 4.1 Playback

### Product meaning

> **Playback = how I listen to music.**

Target:

```text
Playback
Sound and streaming quality

Equalizer
Adjust the sound

Mobile streaming quality
High · 256 Kbps

Wi-Fi streaming quality
Original · No limit
```

Move here:

- Equalizer
- Mobile streaming quality
- Wi-Fi streaming quality

Do not move here:

- Download quality
- ReplayGain
- hardware offload
- cache/storage
- ID3/library compatibility

ReplayGain and hardware offload stay in Advanced because they are technical concepts.

## 4.2 Resolve “Show Now Playing on Play”

Do not simply rename or move this setting.

Audit every real use of:

```text
shouldTransitionOnPlayback
```

For each call site determine:

```text
ALWAYS
NEVER
CONTEXT_DEPENDENT
```

Preferred product behavior:

- Album / Artist / Playlist / Search / Library:
  - start playback;
  - remain in browsing context;
  - mini-player provides access to full Player.

- launcher shortcut or another context with no meaningful music screen behind it:
  - opening full Player may be appropriate.

If normal cases can be handled coherently:

```text
REMOVE “Show Now Playing on Play” from Settings
```

Keep internal implementation only if still needed.

Do not remove the setting until call sites are fully traced.

## 4.3 Downloads

### Product meaning

> **Downloads = how music is stored intentionally on this device.**

Target:

```text
Downloads

Download quality
Original · No limit

Download over Wi-Fi only

Remove all downloads
```

Move `Download quality` from Advanced / Network into Downloads.

Keep streaming quality out of Downloads.

## 4.4 Advanced

Recommended grouping:

```text
Advanced

Audio
  ReplayGain
  Hardware audio offload

Storage & cache
  Music cache size
  Custom cache location
  Image cache

Library compatibility
  ID3 tags
  Offline metadata / ID3 behavior

Diagnostics
  Debug logging

Other
  Confirmation dialogs
  Clear search history
```

Do not add settings just to fill categories.

---

# Phase 5 — Do not expand scope

Do not touch unless a confirmed regression appears:

- Home redesign;
- context-menu reduction;
- Box Sets redesign;
- Playlist visual rewrite;
- Compose migration;
- Android Auto visual redesign;
- hidden chat/podcast/share/bookmark code;
- broad architecture refactor;
- mass `org.moire.ultrasonic` package rename;
- new features;
- new personalization options;
- new Player redesign.

Feature freeze remains active.

---

# Phase 6 — Integrated build before Pixel review

After Phases 1–4:

1. ensure working tree is clean;
2. review the commits together;
3. run relevant unit tests;
4. run lint/build tasks used by this project;
5. build one integrated candidate;
6. install that exact candidate on the Pixel 7.

Do not generate final release notes/checksum yet.

---

# Phase 7 — Consolidated Pixel 7 review

Run one integrated review after all remaining changes are together.

## A. First run

```text
clean install
→ Taki opens connection screen directly
→ enter library address / username / password
→ Connect
→ Home
```

Check:

- no redundant Welcome/Not now step;
- no server-management terminology;
- failure feedback understandable;
- successful connection feels direct.

## B. Settings

Expected root:

```text
Playback
Downloads
Language
Advanced
About
```

### Playback

Verify:

```text
Equalizer
Mobile streaming quality
Wi-Fi streaming quality
```

Confirm there is no confusing “Now Playing” configuration left unless a verified product reason remains.

### Downloads

Verify:

```text
Download quality
Download over Wi-Fi only
Remove all downloads
```

### Advanced

Confirm technical controls remain available without dominating normal Settings.

Check summaries, hierarchy, back navigation and system insets.

## C. Playback navigation behavior

From:

```text
Home
Search
Album
Artist
Playlist
Library / Songs
launcher shortcut if supported
```

start playback and verify final context rules.

Starting a song should not unexpectedly throw the user out of what they were browsing.

Mini-player must remain a reliable way to open full Player.

## D. Downloads

Test:

```text
normal download
download album/track
network failure during download
retry
network recovery
terminal FAILED
tap FAILED icon
manual retry
remove download
Offline playback
```

Confirm:

- retry lifecycle survives service behavior;
- `FAILED` is reachable when appropriate;
- explanation is visible;
- manual retry works;
- successfully downloaded music remains available Offline.

## E. Core regression

Quick pass:

```text
Home
Search
Library
Downloads
Album
Artist
Playlist
Box Sets
Player
Queue
Sleep Timer
Daily Mix
Radio
Like/Unlike
Offline mode
network loss/recovery
notification controls
Bluetooth/headphones
Android Auto basic playback
```

Record only:

```text
BUG
CONFUSION
COSMETIC
POST_BETA
```

Do not redesign during this pass.

---

# Phase 8 — Beta decision

Choose:

```text
READY_FOR_BETA
READY_AFTER_SMALL_FIXES
NOT_READY_FOR_BETA
```

Only bugs or severe confusion affecting normal connection, playback, downloads, offline behavior, identity/installability, or recovery should block beta.

Cosmetic findings and subjective design preferences should normally move to post-beta.

---

# Phase 9 — Final release preparation

Only after the integrated Pixel review passes:

1. freeze code;
2. confirm final `applicationId`;
3. confirm `versionCode` / `versionName`;
4. clean checkout;
5. run final tests/lint/build;
6. generate signed release APK;
7. install exactly that APK;
8. run a short smoke test;
9. verify signing;
10. calculate SHA-256;
11. update release notes with the actual final commit;
12. remove outdated statements such as missing Sleep Timer;
13. update the beta completion document;
14. prepare GitHub Issues feedback path/template if useful;
15. create tag/release only with explicit owner approval.

Do not reuse an old APK checksum or release note commit after code changes.

---

# Commit strategy

Recommended commits:

```text
1. Fix DownloadService retry lifecycle
2. Finalize Taki applicationId
3. Route first run directly to Connect
4. Simplify Playback behavior/settings
5. Reorganize Playback and Downloads settings
6. Final pre-beta cleanup/regression fixes (only if necessary)
```

Do not mix unrelated changes into one commit.

Do not push, tag or publish a release without explicit approval.

---

# Agent reporting format

After each phase, report only:

```text
Result
Files changed
Verification
Remaining risk
```

Keep it short.

At the end of the Pixel review return:

## Confirmed ready

## Bugs found

## Deferred post-beta

## Beta decision

---

# Stop condition

Once:

- DownloadService retries are reliable;
- Taki has intentional installable identity;
- first run goes directly to Connect;
- Playback / Downloads / Advanced settings are coherent;
- playback navigation is context-appropriate;
- integrated Pixel review passes without a real P0/P1 blocker;

**stop changing the product and distribute the beta to real users.**

Do not start another design audit before beta feedback exists.
