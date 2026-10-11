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
