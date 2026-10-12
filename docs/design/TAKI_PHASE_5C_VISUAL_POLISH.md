# Taki Phase 5C - Visual polish (#26)

`VISUAL_POLISH_COMPLETE = NO` - this document is updated per batch. 5C.1 is the first.

## 5C.1 - Late-migration audit + shared control/contrast fix

### Baseline
`7576dae0` on `develop`; Pixel 7 (1080x2400, 420dpi), debug build installed from the working tree.
Tests 1568 -> 1576, 0 failures, 0 skipped; ultrasonic detekt 42, core detekt 1; UI matrix 27/0/0.

### Shared root cause (HIGH): ivory label on bright-accent buttons
Every accent-filled Material `Button` declared `contentColor = onAccent`, but its label used a Taki
text role (`TakiTheme.type.titleSmall`, or stock `labelLarge` = `title`). Those roles hard-code
`color = ivory`, and an explicit `TextStyle.color` beats `LocalContentColor`. Result on device: ivory
text on `#B7D63C` (about 1.5:1) on Save / Connect / sheet confirms, despite the correct-looking source.
Source review alone could not find it; the Pixel screenshot did.

**Accent-filled controls found (10 sites, all affected):** TakiMessageSheet OK, TakiSheetActions
confirm, Rename/Save/Create playlist sheets, Discard-server-changes, Delete-server, Settings confirm,
Settings info, Edit Server Save/Connect (+ Create Playlist screen Save, stock `Button`).

**Fix:** new `components/TakiButtons.kt` - `TakiPrimaryButton` (accent + onAccent),
`TakiSecondaryButton` (neutral outlined, replaces the stock `OutlinedButton` "Test connection"),
`TakiDestructiveButton` (error tone + black, never green). Label colour is taken from
`LocalContentColor` explicitly so it can no longer be overridden. Disabled = neutral container +
gray 70% label (readable, clearly not active). `BRIGHT_ACCENT_STANDARD_CONTENT = onAccent`.
All 10 sites migrated; no hand-rolled accent `buttonColors` remain.

Guard: `TakiButtonContrastTest` (WCAG math over tokens: accent/onAccent pass, accent/ivory fails,
neutral pairs pass, palettes, disabled readability) + a source guard against hand-rolled
accent `buttonColors` outside `TakiButtons.kt`.

### Audit matrix (priority screens)
| Surface | Finding | Class | Pri | Action |
|---|---|---|---|---|
| Edit Server / Add library | Save/Connect ivory on accent; stock outlined Test button | CONTRAST / GLOBAL | HIGH | fixed (shared buttons) |
| All confirm/form sheets | same ivory-on-accent | CONTRAST / SHEET | HIGH | fixed |
| Delete library sheet | destructive action was accent green | COLOR_SURFACE | HIGH | fixed (destructive tone) |
| Server Selector | default swatch = bright accent disc on every row (green budget) | COLOR_SURFACE | MED | fixed: quiet selected-container + accent glyph. A server with an explicitly stored colour (the test server stores `#B7D63C`) still shows it - user data |
| Server Selector | row menu opened at row's left edge, not under the overflow button | SCREEN_SPECIFIC | MED | fixed (trailing anchor) |
| Settings | 12dp gutter vs 16dp V2 gutter | SPACING | MED | fixed (lg) |
| About | 24dp text gutter vs 16dp | SPACING | LOW | fixed (lg) |
| Settings choice sheet | looks correct | ACCEPTED_AS_IS | - | none |
| Equalizer, Box Sets, Load/Empty error, App Error sheet | not captured/changed this batch | - | - | deferred |

### Before/after evidence (scratchpad, not committed)
- Edit Server: Save/Test labels ivory -> `onAccent`/ivory-on-outline; Test button now a 48dp outlined pill.
- Delete sheet @ font 1.3: salmon destructive, label readable, no clipping.
- Server Selector: Offline swatch quiet; menu under the overflow button.
- 18 Roborazzi goldens re-recorded, each a reviewed intentional change (buttons, gutters, swatch).

### Responsive / Spanish
Font scale 1.3 checked on Edit Server and Delete sheet (no clipping). Narrow-width and Spanish passes: **not done** in 5C.1.

### Deferred to later 5C batches
- Equalizer, Box Sets/Collection shell, Load/Empty error + App Error sheet visual review (not captured).
- Settings row weight (`title` 16sp Medium for every row reads heavy beside caption), About density.
- Edit Server: 12dp field gaps / password affordance / library-colour swatch (still bright accent when unset).
- Stock `Switch`/`Slider`/`DropdownMenu` styling; Spanish + narrow-width sweep; typography/spacing literal sweep of late screens.
- Mature-screen comparison beyond a Home before/after sanity check.

---

## 5C.2 - Late-state, sheet and utility surfaces

### Baseline
`6e115742`. Tests 1576 -> 1586, 0 failures, 0 skipped; ultrasonic detekt 42, core 1.
**Device note:** the Pixel 7 disconnected mid-batch (after the BEFORE captures). AFTER verification,
and the Spanish / 1.3x / narrow sweeps, were therefore done with deterministic Roborazzi renders
(Robolectric `es` locale, 320dp, fontScale 1.3), not on the phone. The Equalizer was toggled for
capture and restored to disabled before the disconnect (verified by screenshot).

### 5C.1 goldens reviewed (all 18, before/after crops against `7576dae0`)
| Golden | Result |
|---|---|
| about_standard | intended - 16dp gutter (text reflow only) |
| settings_top_level / settings_advanced_group / settings_choice_sheet_golden | intended - 16dp gutter; one summary truncates 2 chars earlier |
| server_selector_standard | intended - quiet default swatch |
| server_selector_delete_confirmation | intended - destructive Delete, quiet swatch |
| edit_server_existing_mode / edit_server_advanced_expanded | buttons intended; **regression found**: unset library-colour swatch became a near-invisible dark disc (fixed in 5C.2, ring) |
| edit_server_new_mode | intended - Connect label onAccent |
| create_playlist_empty / _populated | intended - Save label + disabled treatment |
| overlay_confirm_delete_playlist | button intended; **finding**: still accent, should be destructive (fixed, but see below) |
| overlay_playlist_info, save_playlist_sheet_golden | intended - button only (diff confined to button bbox) |
| rename_playlist_sheet(_error)_golden, create_playlist_name_sheet(_error)_golden | intended - button only (diff bbox <=150x32px; checked by bounding box, not eyeballed individually) |

### Audit matrix
| Surface | Finding | Class | Pri | Action |
|---|---|---|---|---|
| Unset server-colour swatch (Edit Server) | 5C.1 regression: dark disc, no control affordance | COLOR_SURFACE | HIGH | fixed: 1dp gray ring when unset |
| Destructive confirms (Settings clear downloads / image cache / delete logs; playlist Delete; remove download) | green primary on irreversible actions | COLOR_SURFACE / SHEET | HIGH | fixed: `destructive` flag on `TakiSheetActions`/`TakiConfirmSheet`; Settings confirm uses `TakiDestructiveButton` |
| Equalizer sliders | 5 full-width accent-lime active tracks = the largest green area in the app | COLOR_SURFACE | HIGH | fixed: active track `progress` (#7C9229), thumb stays accent, disabled unchanged |
| Equalizer | 12dp gutter vs 16dp Settings it is reached from | SPACING | MED | fixed (lg) |
| LoadErrorState | Retry was a plain ivory text button (read as a caption); used the *empty* playlist glyph (error looked like empty) | ICON_CONTROL / COLOR_SURFACE | MED | fixed: `EmptyState` action is now `TakiSecondaryButton`; error icon `ic_info_outline`. Error-vs-empty logic untouched |
| App Error sheet | short + long + 1.3x render cleanly | ACCEPTED_AS_IS | - | new goldens only |
| Settings row typography | `title` 16sp Medium; Library browse rows (mature) and the Equalizer/choice sheets use the same role | TYPOGRAPHY | - | ACCEPTED_AS_IS (consistent with the quality bar; no evidence it is heavy) |
| Settings sections/rows/dividers | rhythm fine, no cards needed | ACCEPTED_AS_IS | - | none |
| Equalizer preset sheet | visually identical to Settings choice sheet / TakiSheet (radius, handle, rows) | SHEET | - | ACCEPTED_AS_IS; Back ownership untouched |
| Box Sets list | 12dp gutter matches Album List grid (cells 162dp at 360dp); stack legible; no evidence for change | ACCEPTED_AS_IS | - | none |
| Collection Detail | fan hero + member grid coherent with Album Detail; list-toggle glyph heavier than refresh | ACCEPTED_AS_IS / LOW | LOW | none |
| Cancel TextButtons | ivory Medium beside filled primary; clearly secondary (no fill), 48dp target | ACCEPTED_AS_IS | - | no new primitive (would have one-off consumers) |
| Empty state placement | Equalizer mid-screen vs Box Sets near header; each fits its host | ACCEPTED_AS_IS / LOW | LOW | deferred |

### Spanish / 1.3x / narrow (320dp) result
Combined worst case rendered for Settings (Advanced), Equalizer, Edit Server (expanded), Box Sets, plus a
destructive confirm sheet with "Eliminar descargas", load error and app error (short/long): no clipping,
no overlap, sheet actions fit, titles wrap/ellipsize sensibly. The Settings *confirm overlay* golden
renders below the 1200dp capture crop and was not visually inspected (the shared confirm sheet golden
covers the same component). Pre-existing gap: several Settings strings are still English in `values-es`
(e.g. "Advanced", "Bands", "Music cache size...") - a translation task, not changed here.

### Shared fixes
`TakiSheetActions` / `TakiConfirmSheet` (`destructive`), `EmptyState` (secondary action),
`LoadErrorState` (error icon). Screen-specific: Equalizer (gutter + track colour), Edit Server (swatch ring),
Settings confirm sheet (destructive), 3 call sites flagged destructive (PlaylistListFragment x2, TrackCollectionFragment).

### Goldens
Changed: equalizer_screen_{enabled,disabled,preset_sheet} (track colour + gutter), lyrics_error (Retry button),
edit_server_{existing_mode,advanced_expanded} (swatch ring). New: shared_* (5) and sweep_es_* (5).
All reviewed from before/after crops; `taki_floating_surface.png` churns on every record and was reverted.

### Mature screens
Home/Library were inspected on the phone before the disconnect (Library captured; no shared 5C.2 component
touches them). Album/Artist Detail, Now Playing, Up Next were **not** re-inspected after this batch; the
only shared components changed are `EmptyState` (action slot), `LoadErrorState` and sheet confirm actions.

### Deferred
Untranslated Spanish Settings strings; on-device AFTER verification of 5C.2 changes; Equalizer band
density (4 labels per band) untouched; empty-state placement unification; remaining screens not yet
visually reviewed (Library/Search/detail lists, Now Playing, Up Next, Lyrics, Downloads, Genres, playlists).

### 5C.2 addendum - on-device verification (Pixel 7 reconnected)
Foreground-guarded run (every tap checked Taki was the top activity; Taki only). Verified at 1.0x on the
phone: Equalizer enabled/disabled (desaturated tracks, 16dp gutter; EQ restored to disabled), Settings >
Downloads > "Clear All Downloads" confirm (error-toned OK; dismissed with Back, nothing deleted), Edit Server
(dark `onAccent` Save, neutral Test Connection), Home (unchanged). Spanish + 1.3x checked on Edit Server,
Settings and Equalizer: no clipping, but two defects were found and fixed:
- Edit Server action row: "Comprobar conexion" wrapped to two lines and the two buttons had unequal
  heights -> `IntrinsicSize.Min` + `fillMaxHeight` (Pixel-verified).
- Same row at 320dp / 1.3x (Roborazzi): label broke mid-word -> the buttons stack (Save above Test)
  below 280 font-neutral dp. `sweep_es_edit_server` re-recorded. Not re-run on the phone (the phone is
  not narrow); the 320dp case is covered by the golden only.
Observed, not fixed (deferred): `values-es` still has English for Settings "Playback"/"Downloads"/"Advanced"/
"Equalizer > Bands", bottom-nav "Home"/"Library"; Home chip row and "Liked Albums" -> "Favoritos"
overflow horizontally at 1.3x (expected scroll). Restored after testing: app locale (system default), font scale 1.0.
