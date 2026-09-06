# Taki Design System v2 — Technical Visual Specification

**Status:** Proposed canonical visual specification for the Compose migration  
**Product:** Taki Android  
**Reference viewport:** 412 × 915 dp (Pixel 7 class)  
**Purpose:** Turn Taki's existing visual identity into an implementation-grade system with explicit tokens, component geometry, screen composition rules, motion, and QA criteria.

> **Design intent:** A private music collection presented with the polish of a premium streaming app, without the noise of one.

---

## 0. Authority and relationship to existing resources

This document is the **composition and component specification** for the next visual phase of Taki. It does not replace working product behavior.

### Existing implementation remains authoritative for base tokens

The current Android resources remain the source of truth until Compose equivalents are introduced:

- `ultrasonic/src/main/res/values/colors.xml`
- `ultrasonic/src/main/res/values/themes.xml`
- `ultrasonic/src/main/res/values/dimens.xml`
- `ultrasonic/src/main/res/values/type.xml`
- `docs/technical/TAKI_GUIA_VISUAL_Y_LENGUAJE.md`

### V2 supersedes one prior visual rule

The previous guide prohibited green in bottom navigation. V2 allows **a very small green active-state signal** on the selected navigation icon/label if it improves identity and recognition.

Restrictions remain strict:

- no large bright-green navigation capsule;
- no green navigation background;
- only one destination may carry the active accent;
- the selected container stays neutral or uses the low-intensity `accentContainer`.

This change reflects the approved visual north star while preserving restrained accent usage.

---

# 1. Visual principles

## 1.1 Hierarchy

Every screen must have one obvious order of attention.

**Music/artwork → title/context → primary action → secondary metadata → utility chrome**

If utility chrome is noticed before the music, the composition is wrong.

## 1.2 Premium by reduction

Premium quality comes from:

- precise spacing;
- consistent alignment;
- restrained color;
- strong artwork;
- clear typography;
- deliberate component scale;
- predictable motion.

Do not create "premium" through:

- glassmorphism;
- heavy shadows;
- thick borders;
- decorative gradients on every card;
- many surface colors;
- oversized pills;
- animation for its own sake.

## 1.3 Artwork is the main source of color

Artwork may be visually rich. UI chrome should remain quiet.

- Do not tint every surface from artwork.
- Atmospheric artwork-derived color is allowed only on **featured** surfaces.
- Standard lists, shelves, detail rows, and navigation remain neutral.

## 1.4 Green budget

Taki green is a semantic signal, not a fill color.

**Target:** UI-generated green should normally occupy **< 5% of the visible screen area**, excluding album artwork.

Approved uses:

- liked/favorite active state;
- active shuffle/repeat;
- progress;
- selected navigation glyph/label;
- a single selected filter chip;
- small brand details.

Avoid simultaneous large green elements.

---

# 2. Color system

## 2.1 Canonical palette

| Token | Hex / ARGB | Role |
|---|---:|---|
| `taki_black` | `#090B09` | Main app background |
| `taki_surface_low` | `#111410` | Persistent low-emphasis surfaces |
| `taki_surface` | `#171A16` | Interactive cards, mini-player, panels |
| `taki_surface_high` | `#1D211B` | Pressed / temporarily elevated surface |
| `taki_ivory` | `#F1F2ED` | Primary text and high-emphasis icons |
| `taki_gray` | `#A7AAA2` | Secondary text and icons |
| `taki_accent` | `#B7D63C` | Brand / selected semantic state |
| `taki_accent_pressed` | `#91AD30` | Pressed accent |
| `taki_accent_secondary` | `#91AD30` | Secondary accent role |
| `taki_progress` | `#7C9229` | Quiet playback progress |
| `taki_selected_container` | `#293217` | Low-intensity green selection container |
| `taki_on_accent` | `#11130D` | Content on bright accent |
| `taki_selected_neutral` | `#FFFFFF14` | Neutral selected state (~8% white) |
| `taki_outline` | `#FFFFFF1F` | Rare outline (~12% white) |
| `taki_divider` | `#FFFFFF0D` | Divider (~5% white) |
| `taki_error` | `#FFB4AB` | Error |
| `taki_on_error_container` | `#FFDAD6` | Error-container content |

## 2.2 Surface hierarchy

Use no more than four tonal layers on a normal screen:

1. `taki_black` — canvas
2. `taki_surface_low` — persistent grouping/navigation
3. `taki_surface` — interactive card/panel/mini-player
4. `taki_surface_high` — pressed or temporary elevation

### Rule

A screen should not use a container merely to group content. First try:

- spacing;
- alignment;
- section headers;
- dividers.

## 2.3 Primary action color

For the main Play/Pause action:

- container: `taki_ivory`
- icon: `taki_on_accent` or `taki_black`
- do **not** use bright green as the default play-button fill.

This preserves green for state and identity rather than making playback controls look promotional.

## 2.4 Contrast

Minimum:

- normal text: **4.5:1**
- large text / essential icons: **3:1**
- interactive state must never rely on color alone.

---

# 3. Spacing system

Base rhythm: **4 dp**.

| Token | Value | Typical use |
|---|---:|---|
| `space_xxs` | 2 dp | title ↔ immediate metadata |
| `space_xs` | 4 dp | micro internal gap |
| `space_sm` | 8 dp | icon/text gap, compact items |
| `space_md` | 12 dp | artwork ↔ text, card internal gap |
| `space_lg` | 16 dp | standard screen gutter / card padding |
| `space_xl` | 24 dp | section separation |
| `space_2xl` | 32 dp | major composition separation |

Semantic aliases:

| Alias | Value |
|---|---:|
| `space_screen_horizontal` | 16 dp |
| `space_section_gap` | 24 dp |
| `space_text_tight` | 2 dp |

### Layout rules

- Normal page horizontal gutter: **16 dp**.
- Now Playing horizontal gutter: **24 dp** where screen width permits.
- Section-to-section gap: **24 dp**.
- Artwork-to-text gap: **12 dp**.
- Related text lines: **2–4 dp**.
- Never introduce arbitrary values such as `6`, `10`, `14`, `18`, `22`, or `28 dp` unless required by platform/inset mathematics and documented.

---

# 4. Corner radius and shape system

| Token | Value | Use |
|---|---:|---|
| `radius_xs` | 4 dp | very small inline elements |
| `radius_sm` | 8 dp | artwork, compact cards |
| `radius_md` | 12 dp | cards, bottom panels, hero artwork |
| `radius_lg` | 20 dp | large panels / featured surfaces |
| Circle | 50% | play button, round controls, avatars |

### Rules

- Standard album artwork: **8 dp**.
- Large Now Playing artwork: **12 dp**.
- Feature cards: **20 dp**.
- Do not mix multiple radii for the same component class.
- Avoid rounding entire screens or every list row.

---

# 5. Typography

Current XML roles are retained and become Compose typography roles.

| Role | Effective size / line height | Weight | Color | Use |
|---|---:|---:|---|---|
| `Taki.Hero` | 24 / 32 sp | 500 | `onSurface` | page greeting, Now Playing title, major detail title |
| `Taki.Title` | 16 / 24 sp | 500 | `onSurface` | row title, album/artist title |
| `Taki.TitleSmall` | 14 / 20 sp | 500 | `onSurface` | compact shelf/card title |
| `Taki.Body` | 14 / 20 sp | 400 | `onSurface` | body copy |
| `Taki.Caption` | 12 / 16 sp | 300 | `onSurfaceVariant` | artist, album, duration, counts |
| `Taki.SectionHeader` | 14 / 20 sp | 500 | `onSurfaceVariant` | shelf/group headers |

### Text behavior

- Row titles: max **1 line**, ellipsize end.
- Shelf titles: max **1 line**.
- Metadata: max **1 line** unless the screen exists specifically to inspect metadata.
- Now Playing track title: max **2 lines**.
- Artist line in Now Playing: max **1 line**.
- Page titles must not be truncated under normal phone widths.
- Minimum visible product text: **12 sp**.
- Sentence case only for product labels and section headers.

### Font scaling

Validate at:

- 1.0×
- 1.15×
- 1.30×

At larger accessibility scales, reflow rather than overlap. Do not shrink text to preserve a fixed card.

---

# 6. Icons and touch targets

| Token | Value | Use |
|---|---:|---|
| `icon_size_sm` | 18 dp | secondary inline action |
| `icon_size_md` | 24 dp | standard action/navigation |
| `icon_size_lg` | 32 dp | transport / strong action glyph |
| `touch_target_min` | 48 × 48 dp | every tappable control |

### Transport exception

Primary Play/Pause:

- visual button: **64 × 64 dp**
- icon: **32 dp**
- shape: circle
- fill: `taki_ivory`
- icon: dark

Secondary transport controls:

- touch target: **48 × 48 dp**
- icon: **24–32 dp**
- no container unless selected state requires it.

---

# 7. Artwork system

## 7.1 Sizes

| Context | Size |
|---|---:|
| compact row thumbnail | 56 × 56 dp |
| Home compact shelf artwork | 104 × 104 dp |
| standard larger card | 140 × 140 dp |
| featured card artwork | 124 × 124 dp inside 156 dp-high feature |
| Now Playing hero | responsive; see §11 |

## 7.2 Rules

- Aspect ratio: **1:1** unless source media explicitly differs.
- Crop: center crop for cover art.
- Radius: 8 dp standard; 12 dp hero.
- Default elevation: **0 dp**.
- Do not wrap artwork in a Material card solely to create a shadow.
- A stacked-artwork collection may use the existing small elevation ladder as a semantic collection cue.
- Missing artwork uses a neutral surface + simple icon. Do not generate a green placeholder field.

## 7.3 Atmospheric artwork color

Allowed only for featured cards or transitional player backgrounds.

Specification:

- source: palette extracted from artwork;
- saturation clamp: 20–55%;
- darken until text contrast passes;
- overlay opacity: **12–22%**;
- blur: **24–40 dp** equivalent;
- always combine with a dark neutral scrim behind text;
- never use this effect on every shelf card.

---

# 8. Elevation, borders, and dividers

## 8.1 Elevation

Default: **0 dp**.

Existing `elevation_raised = 3 dp` is reserved for a genuinely floating element such as:

- mini-player above navigation;
- transient sheet;
- semantic stacked-artwork cue.

Do not use elevation on normal album rows/cards.

## 8.2 Borders

Default: none.

When required:

- width: **1 dp**
- color: `taki_outline`

## 8.3 Dividers

- thickness: 1 physical px or up to 1 dp depending renderer
- color: `taki_divider`
- inset to text/content edge rather than spanning under leading artwork/icon when possible.

---

# 9. Reference viewport and responsive rules

Canonical design viewport:

**412 × 915 dp** — Pixel 7 class.

Do not hard-code screen positions. Use constraints derived from width/insets.

### Width classes

#### Compact
`width < 360 dp`

- screen gutter may reduce to 12 dp;
- feature cards remain single-column;
- secondary metadata truncates earlier.

#### Standard
`360–479 dp`

- use canonical 16 dp page gutter;
- two-column compact cards are permitted;
- Now Playing uses 24 dp gutter if hero art remains ≥ 280 dp.

#### Expanded phone / tablet
`≥ 480 dp`

- cap content column width where useful;
- do not simply stretch artwork/cards indefinitely;
- use max widths and additional whitespace.

---

# 10. Home — canonical composition

Home is **editorial and modular**, not a grid of repeated Material cards.

## 10.1 Geometry — standard 412 dp viewport

Content width:

`412 - 2(16) = 380 dp`

### Header

- top system inset: platform controlled
- content top gap after inset: 16–24 dp
- greeting: `Taki.Hero`
- optional subtitle: `Taki.Body` or `Taki.Caption`
- overflow/settings action: 48 dp target, 24 dp icon

### Filter row

Optional. Do not make it visually dominant.

- visual chip height: **36 dp**
- minimum touch envelope: **48 dp**
- horizontal padding: 12–16 dp
- item gap: 8 dp
- radius: 18 dp visual pill
- unselected: `surface_low` / neutral
- selected: one of:
  - `selectedNeutral` + ivory text; preferred neutral mode
  - `accent` + `onAccent`; allowed when only one bright accent control is present

### Featured mix card

- width: **380 dp**
- height: **156 dp**
- radius: **20 dp**
- internal padding: **16 dp**
- artwork: **124 × 124 dp**
- artwork right-aligned
- text column left
- title: `Taki.Title` or Hero only when copy is very short
- metadata: `Taki.Caption`
- primary play target: **48 dp**, visual circle 40–44 dp
- optional atmospheric artwork color per §7.3

### Section header

- top gap from prior block: 24 dp
- height driven by typography, not fixed
- title: `Taki.SectionHeader`
- optional `See all`: Caption/Body, 48 dp touch target

### Recently Played shelf

Each item:

- width: **104 dp**
- artwork: **104 × 104 dp**
- radius: 8 dp
- title gap: 8 dp
- title: TitleSmall, 1 line
- artist: Caption, 1 line
- horizontal gap: 12 dp

### Made for You compact cards

On 412 dp:

`(380 - 12) / 2 = 184 dp`

- width: **184 dp**
- height: **96 dp**
- gap: **12 dp**
- radius: **12 dp**
- internal padding: **12 dp**
- maximum 2 text levels
- no more than one small semantic icon

### Liked Albums

- artwork-forward horizontal shelf
- default artwork: **140 × 140 dp**
- card background: none
- text below art when shown
- item gap: 12 dp

## 10.2 Home anti-patterns

Do not:

- show four equally weighted utility chips before music unless testing proves they are needed;
- use the same card geometry for every section;
- put all sections inside rounded containers;
- show protocol/server state on Home;
- display more than two lines of metadata per music item.

---

# 11. Library — canonical composition

Library should feel like **an organized personal collection**, not a settings page.

## 11.1 Header

- horizontal gutter: 16 dp
- title: `Taki.Hero`
- search/overflow: 48 dp targets
- optional collection summary below title

## 11.2 Optional collection summary

- width: 380 dp
- height: **72 dp**
- radius: 12 dp
- surface: `surface_low`
- leading semantic icon or small stacked art: 40–48 dp
- title: Title
- metadata: Caption
- trailing chevron: 18–24 dp within 48 dp target

Do not expose server URL/protocol here.

## 11.3 “Your music” primary destinations

Two-column compact cards:

- width: **184 dp**
- min height: **80 dp**
- gap: 12 dp
- radius: 12 dp
- padding: 12 dp
- icon: 24 dp
- title: TitleSmall/Title
- optional count: Caption

Suitable for:

- Liked Songs
- Playlists
- Downloads
- optional Recently Added

## 11.4 Browse collection rows

For Albums / Artists / Songs / Genres / Box Sets:

- width: fill
- height: **56 dp**
- background: transparent
- leading icon: 24 dp
- icon-to-title gap: 12 dp
- title: Title or Body depending density
- optional count: Caption
- trailing chevron: 18 dp
- divider: optional, subtle
- entire row tappable

### Rule

Do not convert these browse rows into large pills/cards. Their quietness makes the personal destinations above them feel more important.

---

# 12. Now Playing — canonical composition

Now Playing is Taki's emotional center.

## 12.1 Priority order

1. artwork
2. track title
3. artist / album context
4. progress
5. transport
6. secondary playback actions
7. Up Next / Lyrics / About

## 12.2 Top bar

- horizontal inset: 16–24 dp
- back/down: 48 dp target, 24 dp icon
- overflow: 48 dp target, 24 dp icon
- no visible title in app bar
- background: transparent over app background

## 12.3 Hero artwork

Responsive formula:

`artworkSize = min(screenWidth - 48dp, availableContentHeight × 0.43)`

Constraints:

- preferred at 412 dp width: **364 × 364 dp**
- minimum: **280 dp**
- maximum phone size: **380 dp**
- radius: **12 dp**
- centered horizontally
- no shadow by default

On short screens, artwork shrinks before typography or touch targets shrink.

## 12.4 Track metadata

- top gap after art: 24 dp
- title: `Taki.Hero`, max 2 lines
- artist/album: `Taki.Caption` or Body with secondary color, max 1 line
- favorite:
  - 48 dp target
  - 24–32 dp icon
  - inactive: outline/gray
  - active: `taki_accent`
- text and favorite must not overlap at 1.30× font scale

## 12.5 Progress

- top gap: 16 dp
- active track height: **3 dp**
- inactive track: low-contrast neutral
- thumb: **10 dp** diameter or platform equivalent
- elapsed / duration:
  - Caption
  - aligned to track edges
  - top gap 8 dp
- green may be `taki_progress` or `taki_accent` depending contrast validation.

## 12.6 Main transport row

Recommended geometry:

- row height: **72 dp**
- shuffle: 48 dp target / 24 dp icon
- previous: 48 dp target / 32 dp icon
- play/pause: **64 dp button / 32 dp icon**
- next: 48 dp target / 32 dp icon
- repeat: 48 dp target / 24 dp icon
- distribute with equal visual rhythm, not necessarily equal raw spacing

Inactive shuffle/repeat: gray.  
Active shuffle/repeat: green.

## 12.7 Secondary controls

- top gap: 16 dp
- each action: 48 dp target
- icon: 18–24 dp
- default color: gray
- no permanent text labels unless ambiguity requires them

Examples:

- add / queue
- lyrics
- queue/up-next
- sleep timer

## 12.8 Lower content panel

For `Up Next / Lyrics / About`:

- top gap: 16–24 dp
- background: `surface_low`
- top radius: 20 dp
- no strong elevation
- tab labels: Caption or SectionHeader
- selected tab: accent text or a **2 dp** accent indicator, not both if visually heavy
- row artwork in Up Next: 48–56 dp
- panel may expand/scroll independently when appropriate

---

# 13. Mini-player

The mini-player is a continuation of Now Playing, not a second control panel.

## Geometry

- external horizontal margin: **8 dp**
- container height: **64 dp**
- radius: **12 dp**
- background: `surface`
- optional elevation: up to 3 dp
- progress line: **2 dp** at top edge
- artwork: **48 × 48 dp**
- internal left/right padding: 8–12 dp
- artwork-to-text gap: 12 dp

Text:

- title: TitleSmall or Title, 1 line
- artist: Caption, 1 line

Controls:

- Play/Pause: 48 dp target / 24 dp icon
- Next: 48 dp target / 24 dp icon
- Previous is optional; avoid three visible controls if it compresses metadata.

### Interaction

Tapping non-control area opens Now Playing.

During Compose migration, mini-player expansion into Now Playing should become a candidate for shared visual continuity, but playback/service ownership must remain outside UI composition.

---

# 14. Bottom navigation

Destinations:

- Home
- Library
- Search

## Geometry

- height: **72 dp** excluding system navigation inset
- icon: 24 dp
- label: 12 sp / Caption metrics
- target per destination: ≥ 48 dp
- background: `surface_low`

## Selected state — V2

Preferred:

- small neutral/low-green tonal indicator: approx **48 × 32 dp**
- radius: 16 dp
- active icon: `taki_accent` **or** ivory
- active label: ivory, medium weight
- inactive icon/label: gray

Avoid:

- 64+ dp wide bright-green pills;
- green navigation background;
- multiple accent-colored destinations.

---

# 15. Component inventory for Compose

The Compose layer should expose reusable primitives instead of recreating visual rules per screen.

| Component | Required contract |
|---|---|
| `TakiScaffold` | background, system insets, mini-player + nav placement |
| `TakiSectionHeader` | title + optional trailing action |
| `TakiArtwork` | size, shape, placeholder, loading |
| `AlbumShelfItem` | 104/140 artwork variants + title/caption |
| `FeaturedMixCard` | 156 dp hero card + optional atmospheric artwork treatment |
| `LibraryPrimaryCard` | 2-column 184×80 standard-phone geometry |
| `LibraryBrowseRow` | 56 dp transparent row |
| `TakiIconButton` | 48 dp target; 18/24 dp visual variants |
| `TakiFilterChip` | 36 dp visual / 48 dp target |
| `MiniPlayer` | 64 dp player continuation |
| `PlaybackTransport` | 64 dp primary play + secondary controls |
| `NowPlayingProgress` | track + time layout |
| `NowPlayingLowerPanel` | Up Next / Lyrics / About container |
| `EmptyState` | icon + title + concise copy + optional action |
| `ErrorState` | human message + action + optional diagnostics |

### Hard rule

No component may introduce a raw color/dp/sp value that already has a semantic token.

---

# 16. Proposed Compose token objects

The Compose theme should mirror—not reinterpret—the XML resources.

Recommended conceptual structure:

- `TakiColors`
- `TakiSpacing`
- `TakiShapes`
- `TakiTypography`
- `TakiIcons`
- `TakiMotion`
- `TakiDimensions`

During mixed View/Compose migration, XML and Compose must produce the same visual values.

Do not create a second Compose-only palette.

---

# 17. Motion specification

Motion is functional and quiet.

| Interaction | Duration | Easing |
|---|---:|---|
| press/selection feedback | 120 ms | standard |
| icon/state crossfade | 180 ms | standard |
| content insertion/removal | 180–240 ms | standard |
| navigation transition | 240 ms | emphasized |
| mini-player ↔ Now Playing continuity | 280–320 ms | emphasized |
| artwork crossfade | 180 ms | standard |

Recommended curves:

- Standard: `cubic-bezier(0.2, 0.0, 0.0, 1.0)`
- Exit: `cubic-bezier(0.3, 0.0, 1.0, 1.0)`
- Emphasized/decelerate: `cubic-bezier(0.05, 0.7, 0.1, 1.0)`

Rules:

- no bounce;
- no repeated pulse on normal content;
- no scale animation larger than ~1.02 for press feedback;
- respect Android animation scale / reduced-motion expectations;
- never delay playback response to wait for animation.

---

# 18. Loading, empty, error, and pressed states

## Loading

Prefer stable geometry.

- reserve final artwork/card footprint;
- use `surface_low` / `surface` placeholders;
- avoid aggressive shimmer;
- if animated, use subtle opacity movement only.

## Empty

- centered or context-aligned, depending screen;
- icon 32 dp;
- title: Title;
- copy: Body/Caption;
- one action maximum.

## Error

Human message first. Technical detail remains secondary/diagnostic.

## Pressed

Use one or more:

- `surface_high`;
- small alpha change;
- restrained scale ≤1.02.

Do not flash bright green for every press.

---

# 19. Accessibility

Mandatory:

- 48 × 48 dp minimum touch target;
- 12 sp minimum product text;
- content descriptions for icon-only actions;
- semantic state announcements for liked/shuffle/repeat;
- no state communicated only by green;
- test long Spanish strings;
- test font scale ≥1.30×;
- TalkBack traversal follows visual hierarchy;
- progress/seek controls expose meaningful accessibility actions.

---

# 20. Visual QA matrix

Every migrated canonical screen must be checked at minimum at:

| Case | Required |
|---|---|
| 360 × 800 dp | yes |
| 412 × 915 dp | yes |
| font scale 1.0× | yes |
| font scale 1.30× | yes |
| long title / long artist | yes |
| missing artwork | yes |
| loading state | yes |
| empty state where applicable | yes |
| Pixel 7 physical device | yes |

## Screenshot review checklist

Before declaring a screen complete:

- [ ] Artwork is the strongest visual element where music content exists.
- [ ] One clear primary action exists.
- [ ] Green occupies only a small semantic portion of chrome.
- [ ] No unexplained raw dp/sp/hex values were introduced.
- [ ] Surface hierarchy uses tone before elevation.
- [ ] No normal album art has a drop shadow.
- [ ] Every tappable icon has a 48 dp target.
- [ ] Title/metadata contrast is obvious.
- [ ] Text does not collide at 1.30× font scale.
- [ ] The screen still works with no artwork.
- [ ] Mini-player and bottom nav do not dominate the content.
- [ ] The result feels related to the approved north star without copying another commercial app.

---

# 21. Implementation sequencing

This document should be referenced by the Compose migration issues.

## Phase A — architecture

Issue #8:

- define View/Compose interoperability;
- state ownership;
- navigation;
- mini-player coexistence;
- testing boundaries.

No visual reinterpretation.

## Phase B — Compose visual foundation

Issue #9:

- mirror current colors/type/spacing/shapes;
- add motion/dimension tokens from this spec;
- build low-risk primitives;
- prove screenshot/UI regression workflow.

## Phase C — screen migration

Issue #10:

Recommended implementation order:

1. Home
2. Library
3. Search
4. album / collection detail
5. mini-player integration
6. Now Playing last

### Important

Do **not** migrate XML 1:1 merely for parity.

Preserve behavior and data contracts, but Compose layouts should follow the canonical composition rules in this document.

---

# 22. Canonical visual references

The image `TAKI_VISUAL_NORTH_STAR.png` is a **directional reference**, not a pixel-perfect implementation contract.

Extract from it:

- composition;
- hierarchy;
- visual quietness;
- artwork prominence;
- component scale;
- surface restraint;
- relative emphasis.

Do not copy:

- fabricated data or unavailable features;
- exact mockup-only content;
- accidental image-generation artifacts;
- unsupported tabs/actions;
- exact pixels where they contradict this technical specification.

**When the north-star image and this specification disagree, this specification wins.**
