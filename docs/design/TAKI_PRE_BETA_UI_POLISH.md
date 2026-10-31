# Pre-beta UI polish sprint (#48)

Visual-only. No playback, networking, persistence, auth or server code touched.

## What changed

| Area | Change |
|---|---|
| Menus | New `TakiDropdownMenu` / `TakiMenuItem` / `TakiMenuDivider` (`ui/components/TakiMenu.kt`): flat `surfaceHigh`, `radius_md`, hairline edge instead of shadow, 220-296dp width, body-weight labels (2 lines before ellipsis), 48dp rows, groups separated by a divider, destructive label in the error colour. Used by Now Playing overflow, Album overflow, track context menu. Material's anchoring/dismiss/a11y untouched. |
| Typography | No token changed. Title-to-subtitle gap raised 2dp -> 4dp (`spacing.xs`) in Settings rows, Library Hub, album hero (artist/metadata), `TakiEntryRow`, `TakiTrackRow`. `caption` stays 12sp Light: legible at the measured contrast (gray on near-black); changing it would affect every screen. |
| Sleep Timer | `FilterChip` -> soft `radius_lg` neutral tiles (`selectable`, radio role). Selected = accent outline + accent label, no green fill. Status line uses body size; cancel is full width. `SleepTimerController` untouched. |
| About | "Report a problem" is a plain Settings-style row: hairline above, 48dp target, no container. |
| Share Card | Signature (existing "t" symbol, 48px, 50% ivory) centred **above** the cover, 44px clear of it; block (symbol, cover, centred title/artist/album) centred in the 250..1470 safe zone. Cover 780 -> 740px so worst-case two-line title + two-line artist + album still fit. Output stays 1080x1920. |

## Decisions / alternatives
- Menu surface: `surfaceHigh` + hairline rather than `surface` (indistinguishable from screens behind) or a shadow (banned).
- Sleep Timer: selected state uses an outline, not `selectedContainer`, to avoid green fills.
- Share text centred (not left) to share one axis with the centred signature.

## Verification
- 1915 unit tests, 0 failures (was 1912 + 30 intended golden diffs before re-recording; +3 menu, +1 sleep-font, +3 share card goldens, share/menu behavioural tests).
- Goldens re-recorded only after viewing: `menu_standard`, `sleep_timer_*` (+`_font_1_50_small`), `about_standard`, `share_card_*` (new), and spacing-only diffs in album/artist/settings/hub/playlist/downloads/folder goldens.
- lintDebug, lintVitalRelease, assembleDebug, assembleRelease green. Detekt 42 issues before and after.
- Before images: git history of `ultrasonic/src/test/screenshots` (parent of the sprint commits).
- Not run: physical Pixel 7 (not authorised); emulator pass is left for the beta QA checklist.

## Residual
- Library Hub / Settings subtitles still use 12sp Light caption; revisit only if device QA shows legibility problems.
- Other `DropdownMenu` users (album list, artist list, sort, folder selector) still use Material defaults; migrating them is a one-line swap each.

## Emulator QA (2026-10-31, baseline `ac7eaa29`)

Rig: `Pixel_7` AVD (API 37, 1080x2400 @ 420 dpi), debug build installed over `adb`, a throw-away **fake Subsonic server** on the host (synthetic artists/albums/WAV tone/generated PNG covers, reached through `adb reverse`; app data on the emulator wiped first). The physical Pixel 7, the user's Navidrome and Tailscale were not touched. Second configuration: 360x640 dp (`wm size 1080x1920`, density 480) with `font_scale 1.5`. Server, `adb reverse`, display and font overrides were reverted afterwards. Screenshots: `docs/technical/evidence/48-emulator/` (real device captures, no generated images).

| Area | Result |
|---|---|
| Now Playing / album / track menus | Surface, `radius_md` corners, dividers, 48dp rows, destructive tint (unit golden), anchoring, Back and outside-tap dismissal, and actions (Go to Album, Play Next) all correct. 1.5x font: no clipping. |
| Typography | Settings, Playback settings, Library Hub, Album Detail hero, track rows: title/subtitle gap reads cleanly; `caption` token left unchanged (legible). |
| Sleep Timer | Four presets, end-of-song, cancel, off/timed/end-of-song states, 360x640 + 1.5x font: no clipping; tiles measured 48dp on the device; playback stayed `PLAYING` through every selection. |
| About | Hierarchy and spacing good; "Report a problem" only opens the project URL in the browser (nothing sent); still reachable by scroll at 1.5x. |
| Share Card | Preview and exported PNG (1080x1920, pulled from `cache/share`) match; signature centred above the cover; long title/artist/album ellipsise inside the card; no-cover + no-album card renders the fallback tile; Share button visible at 360x640 / 1.5x. |

### Defect found and fixed
- **Track context menu touched the left screen edge** (anchored to the full-width row, long-press). Fix: `TakiDropdownMenu(offset = ...)`, track menu passes a 16dp gutter (`AlbumDetailScreen`). Before/after: `menu_track_emulator_before_fix.png` / `menu_track_emulator.png`. No unit test: Robolectric does not report a popup window's screen offset (a bounds test read 16dp with and without the offset), so the emulator evidence is the proof.

### Not covered
- **TalkBack: NOT_TESTED** (semantics covered by Compose tests: selected state, radio role, headings, 48dp). Android "Display size" larger than default: not exercised (360x640 dp at density 480 and 1.5x font were).
- Fallback share tile plus the signature both show the "t" symbol when there is no cover (by design; noted, not changed).
