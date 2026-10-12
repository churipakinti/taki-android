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

---

## 5C.3 - Music surfaces and localization

### Baseline
`d79c51b6`. Tests 1586 -> 1588 (+2 Spanish completeness), 0 failures, 0 skipped; ultrasonic detekt 42, core 1.
Pixel 7 connected. A foreground guard (top activity must be Taki) was used for every tap/screenshot after
an earlier run touched another app. Live captures were taken after the first fixes landed, so BEFORE
evidence for changed screens is the pre-change golden (and the 5C.2 captures), not a live shot.

### Screens reviewed
| Surface | Pixel (en 1.0) | Pixel (es 1.0) | Pixel (es 1.3x) | Golden/render |
|---|---|---|---|---|
| Home (skeleton only; no data loaded in the run) | yes | yes | yes | home_* |
| Library | yes | yes | yes | library_* |
| Search (recents state; populated + no-results NOT on device) | yes | yes | yes | search_* |
| Albums / Artists / Songs / Genres / Playlists lists | yes | Genres | - | list goldens |
| Downloads | yes | yes | yes | downloads_* |
| Album Detail | yes | yes | yes | album_* |
| Artist Detail | yes | yes | yes | artist_standard |
| Playlist Detail | yes | - | - | playlist_standard |
| Now Playing (paused) | yes | yes | yes | now_playing_* |
| Up Next | yes | yes | yes | up_next_* |
| Lyrics (plain + loading on device; synced + error via goldens) | yes | loading | loading | lyrics_* |
| Sleep Timer sheet | yes | - | - | sleep_timer_* |
| Mini-player | yes (every screen) | yes | yes | mini_player_* |
| Settings / Equalizer (Spanish) | - | (5C.2) | (5C.2) | sweep_es_* |

### Audit matrix
| # | Surface | Finding | Class | Pri | Action |
|---|---|---|---|---|---|
| 1 | Spanish UI | 126 translatable strings + 6 plurals added with the Compose migration had no Spanish entry (nav label Home, Settings group titles, Equalizer Bands, Library hub, Album/Artist/Lyrics/Up Next copy, "2 albums") | l10n | HIGH | fixed: translated with existing terminology; `SpanishResourceCompletenessTest` (strings + plurals) |
| 2 | List family gutter | Albums, Artists, Genres, Downloads, Playlists, Songs, Box Sets, folder rows and `TakiLibraryTrackRow`/`TakiEntryRow` used 12dp while Home, Library, Search, all detail screens and `TakiTrackRow` use `screenHorizontal` 16dp (Songs rows 12 vs Album Detail rows 16) | SPACING | HIGH | fixed: list gutters/rows -> `screenHorizontal` (card inner padding stays 12) |
| 3 | `TakiLibraryTrackRow` heart | liked heart tinted `accentSecondary` (olive green); Album Detail / Now Playing / Library use the rose `liked` token | COLOR_SURFACE | MED | fixed: `liked` when liked, `gray` otherwise |
| 4 | Up Next | section labels were ALL-CAPS letter-spaced captions, contradicting the sentence-case `Taki.SectionHeader` rule | TYPOGRAPHY | MED | fixed: shared `TakiSectionHeader` |
| 5 | Library cards (es) | "Álbumes que me gu…" / "Listas de reproducci…" truncated at 1.0x | RESPONSIVE | MED | fixed: "Álbumes favoritos"; new `library_playlists` card label (Playlists / Listas) |
| 6 | Artist radio playlist name | base (English) string was Spanish: "Radio de %1$s" | l10n | LOW | fixed: "%1$s Radio" (es keeps "Radio de %1$s") |
| 7 | Library lists without a title | Albums/Artists/Genres/Songs/Playlists show only a back arrow (+ sort chip) while Downloads/Settings/Box Sets/Equalizer have a titled header. Intentional so far (shell-owned back bar, see `GenreListScreen` kdoc) but visibly inconsistent | NAV CHROME | MED | DEFERRED: needs a chrome decision in NavigationActivity |
| 8 | Genres | after the gutter edit, genre names broke mid-word ("Alternativ/e"): my sed also widened the card INNER padding; caught on the phone, reverted | RESPONSIVE | HIGH (self-inflicted) | fixed before commit |
| 9 | Now Playing | clean hierarchy, 304dp art, 80dp ivory Play, rose heart, quiet progress | ACCEPTED_AS_IS | - | none |
| 10 | Album / Artist / Playlist Detail | siblings share Play-left + icon-right action row and ivory primary; heroes differ by design | ACCEPTED_AS_IS | - | none |
| 11 | Mini-player | readable, floating over bottom nav, no collision with rows | ACCEPTED_AS_IS | - | none |
| 12 | Sleep Timer sheet | matches the shared sheet family | ACCEPTED_AS_IS | - | none |
| 13 | Lyrics | current-line container + gray context readable; plain lyrics left-aligned | ACCEPTED_AS_IS | - | none |
| 14 | Search | recents / Clear all / result hierarchy fine, rows already 16dp | ACCEPTED_AS_IS | - | none |
| 15 | Home chip row at es 1.3x | last chip partly visible: horizontal-scroll affordance, nothing unreachable | ACCEPTED_AS_IS | - | none |
| 16 | Library cards at es 1.3x | "Canciones favo…" / "Álbumes favorit…" ellipsize (fixed 80dp card) | ACCEPTED_AS_IS | LOW | truncation per design rules |
| 17 | Playlists smart-playlist tiles | large generic glyph squares | ACCEPTED_AS_IS | LOW | none |

### Mature-screen coherence matrix (after fixes)
| Dimension | Home | Library | Album Detail | Artist Detail | Now Playing | Up Next |
|---|---|---|---|---|---|---|
| gutter | 16 | 16 | 16 | 16 | 24 (V2) | 16 |
| title hierarchy | Hero greeting | Hero | Hero, centred | Hero, centred | Hero, left | header + Taki.Title rows |
| secondary text | gray caption | gray | gray | gray | gray | gray caption |
| artwork | shelf 8dp / hero 20dp | icons only | 12dp hero | 12dp hero 220 | 12dp 304 | 8dp rows |
| primary action | ivory Play | cards | ivory Play 64 | ivory Play 64 | ivory Play 80 | row tap |
| green use | none | none (rose liked) | current-row marker | current-row marker | progress + active toggles | marker + progress |
| surface use | glass hero card | cards | flat | flat | atmosphere | flat |
Intentional differences: Now Playing 24dp gutter and 80dp Play; hero sizes. Visual debt: #7 (deferred); fixed here: #2, #3, #4.

### Spanish completeness
- Method: base vs `values-es`, keys normalised (dot to underscore), referenced from Kotlin/XML; plus plurals.
- Missing before: 126 strings (+ `button_bar.home`, `song.add_to_playlist_description`) and 6 plurals
  (`artist_album_count`, `n_discs`, `n_playlists`, `up_next_remaining`, `sleep_timer_remaining_full`,
  `collection_discover_result`). Now 0 / 0, locked by `SpanishResourceCompletenessTest`.
- Terminology kept: "lista de reproducción", "colección", "Aleatorio", "Cola", "Favoritas".
  New: Biblioteca, Inicio, Buscar, Descargas, Reproducción, Avanzado, "Box sets" (loanword), "A continuación", "Reproduciendo".
- Hard-coded user-visible English in Compose source: none (only `@Preview` fixtures and an animation label).
- Not translated: the other 11 locales (out of scope); `error_label` and `song_details_kbps` are identical in Spanish.

### Responsive
- Spanish 1.0x (Pixel): Home, Library, Search, Downloads, Genres, Album/Artist Detail, Now Playing, Up Next, Lyrics - fixed #5, otherwise clean.
- Spanish 1.3x (Pixel): same screens - no overlap, no mid-word breaks, controls reachable (see #15/#16).
- 320dp: the 5C.2 `sweep_es_*` renders were re-recorded with translated strings. New 320dp renders for
  Search/Genres/Downloads/Album/Artist/Now Playing/Up Next/Lyrics were NOT added in this batch (existing
  `*_compact_360` goldens only).

### Shared fixes
`TakiLibraryTrackRow` (gutter + heart tint), `TakiEntryRow` (gutter), `UpNextScreen.SectionLabel` (-> `TakiSectionHeader`),
list screens' gutters, `values` / `values-es` strings, `LibraryScreen` playlist card label.

### Goldens changed (every before/after inspected)
Gutter 12 to 16: album_list_{grid,list,empty,folder_header}, artist_list_{grid,list,empty,folder_header},
collection_list_standard, downloads_{standard,empty}, genre_list_standard, playlist_list_{standard,grid,empty},
folder_browser_mixed, server_selector_{standard,delete_confirmation}, track_list_{songs,with_header,empty},
album_detail_folder (folder rows), track_list_liked_songs (also rose hearts).
Up Next sentence-case headers: up_next_{populated,empty}.
Spanish now translated: sweep_es_{settings_advanced,settings_confirm,equalizer,box_sets}.
The pure-gutter empties (album/artist/downloads/playlist/track-list/up-next empty) were checked by diff region, not viewed one by one.

### Architecture (explicit)
`CANONICAL_UI_MATRIX = 27 Compose / 0 Hybrid / 0 Legacy` - source `TAKI_COMPOSE_COVERAGE_AUDIT.md` (27/0/0/0), re-checked
against the tree: 0 changes to `res/layout`, `res/navigation`, `res/menu` or the manifest since the 5C baseline `7576dae0`
(the only change since the audit baseline is the #33 error-host elevation).
`REACHABLE_VIEW_OVERLAYS = 0` - `ResidualViewOverlayGuardTest` green.

### Device restoration
App locale: system default. Font scale 1.0. Playback left paused on "Bien alto" at about 0:14 (never started).
Shuffle/Repeat untouched, no sleep timer armed, EQ unchanged (disabled), no data changed.

### Deferred (-> 5C.4)
- #7 title-less library list screens (chrome decision).
- Not seen on device: Search populated/no-results, synced lyrics, lyrics error, Home with loaded data (skeleton only),
  Folder browser, Collection detail, music-list error/empty states, Playlist Detail in Spanish.
- 320dp renders for the music surfaces; other locales.
- Known product debt unchanged: online Album Detail has no per-row downloaded indicator.

---

## 5C.4 - Header convergence and final visual sign-off

### Baseline
`112f8012`. Tests 1588 -> 1612 (+9 header, +13 s320 renders, +2 chrome); ultrasonic detekt 42, core 1. Pixel 7 connected, foreground-guarded
(top activity must be Taki before every tap or screenshot).

### Header decision
Every standard Library destination now owns one Compose `TakiScreenHeader` (back + localized title):
Albums, Artists, Genres, Songs, Liked Songs, Playlists - and Downloads (decision B below). No new header primitive;
`onBack` is a plain callback on each screen's Actions (default no-op) wired in the host Fragment to
`findNavController().navigateUp()`. Navigation graph and NavController ownership are unchanged.

Titles (existing keys, no duplicates): `main_albums_title` / `main_artists_title` / `main_genres_title` /
`playlist_label` / `main_songs_title` / `library_liked_songs` / `menu_downloads`. Albums and Artists keep the
contextual `navArgs.title` when one is passed (e.g. an artist's albums); Songs/Liked Songs reuse
`TrackListUiState.headerTitle` (Genre tracks / Daily Mix already did).
Downloads: B - its hand-drawn hero `Text` was replaced by the shared header; it was the one list with
shell back-arrow + its own headline, now identical to its siblings.

### Chrome selection matrix (after)
| Destination | Support toolbar | Shell back-only bar | Compose header | Hero title | Back owner | Class |
|---|---|---|---|---|---|---|
| Albums / Artists / Genres / Playlists / Downloads | hidden | hidden (was shown) | back + title | - | Fragment navigateUp | HEADER_WITH_TITLE |
| Songs / Liked Songs | hidden | hidden (was shown) | back + title | - | Fragment navigateUp | HEADER_WITH_TITLE |
| Genre tracks / Daily Mix / Folder browser | hidden | hidden | back + title | - | Fragment | HEADER_WITH_TITLE (unchanged) |
| Box Sets / Settings / Equalizer / About / Lyrics / Up Next | hidden | hidden | back + title | - | Fragment | HEADER_WITH_TITLE (unchanged) |
| Album Detail / downloaded Album Detail / Playlist Detail | hidden | shown | - | in hero | shell arrow | HERO_WITH_BACK_ONLY (unchanged) |
| Artist Detail / Collection Detail | hidden | hidden | back only (+actions) | in hero | Fragment | HERO_WITH_BACK_ONLY (unchanged) |
| Now Playing | hidden | hidden | back + overflow | in body | Fragment | IMMERSIVE |
| Home / Library / Search | hidden | hidden | none | greeting/section | - | TOP_LEVEL |
`showsContentBackButton` shrank to `isAlbumDetail || isPlaylistDetail`. `NavigationChromeSelectionTest` replaced the
old "every list shows the bar" assertions with: only hero-led detail screens keep the shared bar; every Library list
hides toolbar AND bar; and each `trackCollectionFragment` mode (Songs, Liked, Genre/Daily Mix, Folder, Album Detail,
downloaded Album Detail, Playlist Detail) gets exactly one back affordance.
New `LibraryHeaderConvergenceComposeTest` (9): exactly one back arrow + one title per screen, callback fired once,
contextual title preserved, no header when no title.

### Navigation validated on the Pixel (Compose arrow and system Back)
Library -> Albums -> back; Artists; Genres -> Genre tracks -> arrow -> Genres -> arrow -> Library; Songs; Liked Songs;
Playlists -> Playlist Detail -> back -> Playlists; Albums -> Album Detail -> arrow -> Albums -> system Back -> Library;
Artists -> Artist Detail -> arrow -> Artists -> system Back -> Library; Downloads -> downloaded Album Detail -> back ->
Downloads. Every arrow tap was checked by UI dump (landed on the expected list or on the Library hub) and the bottom nav
stayed on Library; Home/Library/Search tab switching behaved. No double pop.
Genre list also fixed (found on device, see below).

### Defects found and fixed in this batch
1. Genre cards at 320dp/1.3x broke names to one letter per line ("Alt/er..."): the grid now falls to one column when two
   cards would be narrower than 168dp x font scale. Other widths unchanged.
2. Songs controls at 320dp/1.3x: the sort chip ellipsized to "Todas las" with no ellipsis glyph; `TakiFilterChip` label now
   ellipsizes properly.

### Before/after for the new headers
Before: Library lists showed a bare back arrow over an unlabeled screen (`h4/` captures vs the 5C.3 `c3/` shots, and the
downloads golden); after: arrow + Hero title, controls row 56dp below, content unchanged. Verified on the Pixel for
Albums, Artists, Songs, Genres, Playlists, Liked Songs, Downloads (en).

### Remaining on-device coverage
| Item | Outcome |
|---|---|
| Populated Home (Daily Mix, Recently Played, Liked Albums, Recently Added, Discover, Most Played) | PIXEL_VERIFIED |
| Search results (artists/albums/songs, scrolled) and no-results | PIXEL_VERIFIED |
| Search clear/recents (removed the throw-away test queries I typed; the user's earlier recents are untouched) | PIXEL_VERIFIED |
| Collection Detail + Box Sets list | PIXEL_VERIFIED |
| Synced lyrics | UNAVAILABLE live (the server returns plain lyrics for some tracks and none for the current one; switching tracks would change playback). Rendering validated through `lyrics_synced_standard` / `lyrics_synced_font_1_30` goldens and the new 320dp es synced render - GOLDEN_VERIFIED |
| Folder Browser | NOT_REPRODUCED (server is id3; folder mode needs a settings change). `folder_browser_mixed` golden (re-recorded for the 16dp gutter) - GOLDEN_VERIFIED |
| Music-list EMPTY states | GOLDEN_VERIFIED (album/artist/genre/playlist/downloads/track-list/search empties) |
| Music-list ERROR/Retry | GOLDEN_VERIFIED (`shared_load_error*`, `LoadErrorStateComposeTest`); NOT_REPRODUCED live (no safe way to force a failure) |

### Responsive
- Spanish 1.0x / 1.3x on the Pixel: reviewed in 5C.3 for all main screens; the new header destinations were validated
  at en 1.0x on the Pixel, and at es 1.3x / 320dp through the new renders (below). The final build was then checked on the Pixel at es 1.3x: Albums, Artists, Songs, Genres (one column, by design), Playlists, Liked Songs, Downloads - titles localized, one header each, no overlap (PIXEL_VERIFIED).
- 320dp / es / 1.3x renders (13 new goldens, all viewed): `s320_albums, artists, genres, songs, liked_songs, playlists,
  downloads, search, album_detail, artist_detail, now_playing, up_next, lyrics`. No title/back collision (long "Listas de
  reproducción" and "Canciones favoritas" ellipsize or fit), no mid-word breaks after the two fixes, controls reachable.
  Observed and accepted: grid titles ellipsize ("OK Com..."), Now Playing's long title uses its marquee (static render clips).

### Goldens
New: s320_* (13). Changed: downloads_standard, downloads_empty (shared header replaces the Hero text). Earlier-phase
list goldens were unaffected by the header (they render the screens without a title). Every changed/new golden was opened
and viewed (before/after for downloads).

### Final coherence assessment
Library destinations now read as the same family as Box Sets/Settings/Genre tracks: arrow + Hero title, restrained
accent, 16dp gutters, one rose liked color, sentence-case section headers. Intentional differences retained: Now Playing
immersive, Album/Playlist Detail hero-led with shell arrow, Artist/Collection hero with in-screen back.

### Accepted exceptions
Home chip row scroll at 1.3x; Library card truncation at 1.3x; Album/Playlist Detail still use the shell back arrow
(hero-led); Settings/Equalizer rows use `Taki.Title`.

### Remaining findings (none HIGH)
- Known product debt: online Album Detail shows no per-row downloaded indicator.
- Not translated: 11 other locales (out of #26 scope).
- Live-only gaps listed above (synced lyrics, folder mode, forced error states).

### Final coverage matrix for #26
| Surface | Inspected | Problem found | Disposition | Final validation |
|---|---|---|---|---|
| Home | yes (loaded, en) | none | ACCEPTED_AS_IS | PIXEL_VERIFIED |
| Library hub | yes | es card truncation (5C.3) | FIXED | PIXEL_VERIFIED |
| Search (recents/results/none) | yes | none | ACCEPTED_AS_IS | PIXEL_VERIFIED |
| Albums / Artists / Genres / Songs / Liked / Playlists | yes | no title; 12dp gutter; genre cards narrow | FIXED (5C.3/5C.4) | PIXEL_VERIFIED (en 1.0, es 1.3) + 320dp render |
| Downloads | yes | own hero title + shell arrow | FIXED | PIXEL_VERIFIED + 320dp render |
| Album / Artist / Playlist Detail | yes | none | ACCEPTED_AS_IS | PIXEL_VERIFIED + 320dp (album, artist) |
| Collection Detail / Box Sets | yes | none | ACCEPTED_AS_IS | PIXEL_VERIFIED |
| Now Playing / Up Next / Sleep Timer | yes | Up Next caps (5C.3) | FIXED / ACCEPTED | PIXEL_VERIFIED + 320dp |
| Lyrics (plain, loading) | yes | none | ACCEPTED_AS_IS | PIXEL_VERIFIED; synced GOLDEN_VERIFIED |
| Folder Browser | golden | gutter (5C.3) | FIXED | GOLDEN_VERIFIED (UNAVAILABLE live) |
| Settings / Equalizer / About / Server Selector / Edit Server | yes (5C.1-5C.2) | contrast, destructive, gutters | FIXED | PIXEL_VERIFIED (5C.2) |
| Sheets / Empty / Error / App error | yes (5C.2) | button hierarchy | FIXED | GOLDEN_VERIFIED + PIXEL (destructive confirm) |

### Final decision
All 16 closure conditions hold: single localized header on every required destination; no double arrow or toolbar
(chrome tests + device); back/navigation tests and device transitions pass; populated Home and Search results are
Pixel-verified; synced Lyrics, Folder Browser and forced error states are golden-verified because the live server cannot
produce them without changing data; Spanish 1.3x and 320dp reviewed with no known high-impact clipping; no HIGH defect
open; gates green; architecture 27/0/0 and 0 View overlays; 0 FATAL/ANR in logcat; locale, font scale and data restored.


