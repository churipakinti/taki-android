# Taki Compose Migration — Coverage Audit (Issue #10)

**Status:** audit only, no implementation. Baseline: `develop` at `5f8bdc6a` (Artist Detail Compose,
phase 4C). Compose migration completed so far: Home, Library, Search, Album Detail (gated ID3
album mode of `TrackCollectionFragment`), Collection Detail, Box Sets shell/header continuity,
Artist Detail. Mini-player (`NowPlayingFragment`) and Now Playing (`PlayerFragment`) are still
legacy View/XML and are, by design, the final two migration phases.

This document exists to prevent a false "100% migrated" reading of issue #10 caused by hybrid
Fragments — most importantly `TrackCollectionFragment`, which is one nav destination but renders
twelve materially different screens depending on its nav args, only one of which (ID3 Album
Detail) is Compose today.

Sources used: `ultrasonic/src/main/res/navigation/navigation_graph.xml` (parsed directly, not
recalled from memory or docs), `NavigationGraphDirections`/`*FragmentDirections` call sites
(grepped across `ultrasonic/src/main/kotlin`), `NavigationActivity.kt` (chrome/toolbar rules),
every Fragment class reachable from the graph, `docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md`,
and the GitHub issue #10 body text (fetched live via `gh issue view 10`).

---

## A. Executive summary

| Metric | Count |
|---|---:|
| Total reachable UI surfaces/modes classified (matrix rows) | 38 |
| — Compose | 6 |
| — Hybrid (Compose + View in the same screen, by design) | 1 |
| — Legacy View/XML | 31 |
| — of which: recommended `MIGRATE_IN_#10` | 21 |
| — of which: `NEEDS_DECISION` (Videos mode) | 1 |
| — of which: `DEFER_OUTSIDE_#10` / `INTENTIONALLY_LEGACY` | 6 |
| — of which: `DEAD_CODE_CANDIDATE` (orphaned nav-graph nodes) | 3 |
| Unclassified | **0** |

Two additional **code-level** dead-code findings (not standalone UI surfaces, so not matrix rows)
are reported in §H: an unused nav argument (`albumListType` on `trackCollectionFragment`) and an
unused nav action (`albumListToTrackCollection`).

Two Fragment classes are **hybrid at the class level** (both host Compose and legacy View
rendering in the same screen, selected by a runtime condition): `TrackCollectionFragment`
(condition: `shouldUseComposeAlbumDetail(...)`) and `CollectionListFragment` (unconditional: an
XML shell with a Compose header `ComposeView` declared *in the XML layout* over an unchanged XML
`RecyclerView` body — this is the phase-4B "shell/header continuity" design, not a migration gap).

---

## B. Canonical matrix

Classification vocabulary: `DONE`, `MIGRATE_IN_#10`, `DEFER_OUTSIDE_#10`, `INTENTIONALLY_LEGACY`,
`DEAD_CODE_CANDIDATE`, `NEEDS_DECISION`.

### B.1 — Compose / hybrid-by-design (DONE)

| Surface | Destination / Mode | Current UI | Classification | Required action |
|---|---|---:|---:|---|
| Home | `homeFragment` | Compose | DONE | none |
| Library | `mainFragment` | Compose | DONE | none |
| Search | `searchFragment` | Compose | DONE | none |
| Box Sets shell | `collectionListFragment` | Hybrid (Compose header + XML `RecyclerView` body, by design) | DONE | none — this is the intended phase-4B end state, not a gap |
| Collection Detail | `collectionDetailFragment` | Compose | DONE | none |
| Artist Detail | `artistDetailFragment` | Compose | DONE | none |
| Album Detail | `trackCollectionFragment` / ID3 album, online, non-playlist (`shouldUseComposeAlbumDetail`=true) | Compose | DONE | none |

### B.2 — Legacy browsing surfaces recommended for #10

| Surface | Destination / Mode | Current UI | Classification | Required action |
|---|---|---:|---:|---|
| Artist List | `artistListFragment` | View (`ArtistListFragment`, `list_layout_generic`/`_filterable`) | MIGRATE_IN_#10 | New Compose screen; precedes already-Compose Artist Detail |
| Album List | `albumListFragment` | View (`AlbumListFragment`, `list_layout_generic`/`_filterable`) | MIGRATE_IN_#10 | New Compose screen; precedes already-Compose Album Detail |
| Album Detail, folder/non-ID3 mode | `trackCollectionFragment` / `isAlbum=true`, `shouldUseId3Tags()=false` | View (`AlbumDetailHeaderBinder`+`TrackViewBinder`, `list_layout_track`) | MIGRATE_IN_#10 | Extend `AlbumDetailViewModel`'s data-loader seam to `getMusicDirectory`; drop the `usesId3` gate for this shape once verified |
| Album Detail, offline/downloaded | `downloadedAlbumFragment` → `TrackCollectionFragment` w/ `allowComposeAlbumDetail=false` | View (same binders as above) | MIGRATE_IN_#10 | Extend the same data-loader seam to `getDownloadedAlbumTracks`; drop the `allow` override |
| Liked Songs | `trackCollectionFragment` / `getStarred=true` | View (`LibraryTrackBinder`) | MIGRATE_IN_#10 | Part of proposed shared `TrackListScreen` (§E) |
| All Songs / library root (+ in-place filter bar) | `trackCollectionFragment` / `libraryRoot=true` | View (`LibraryTrackBinder` + `FilterButtonBar`) | MIGRATE_IN_#10 | Part of shared `TrackListScreen`; filter-bar sort switching becomes in-VM state |
| Random tracks | `trackCollectionFragment` / `sortOrder=RANDOM` or `getRandom=true` (sub-state of library-root filter bar; no direct live caller found) | View (`LibraryTrackBinder`) | MIGRATE_IN_#10 | Bundled with All Songs above |
| Daily Mix | `trackCollectionFragment` / `dailyMix=true` | View (`LibraryTrackBinder`) | MIGRATE_IN_#10 | Part of shared `TrackListScreen` |
| Genre tracks | `trackCollectionFragment` / `genreName != null` | View (`LibraryTrackBinder` or `TrackViewBinder` depending on entry point) | MIGRATE_IN_#10 | Part of shared `TrackListScreen` |
| Artist-scoped songs (filter-bar sub-state) | `trackCollectionFragment` / `selectedArtistId/Name` (sub-state of library-root filter bar) | View (`LibraryTrackBinder`) | MIGRATE_IN_#10 | Bundled with All Songs above |
| Folder / non-ID3 browsing (incl. index; mixes Album + Track rows) | `trackCollectionFragment` / fallthrough, `isAlbum=false` | View (`TrackViewBinder` + `AlbumRowDelegate`) | MIGRATE_IN_#10 | Part of shared `TrackListScreen`; needs a mixed-row-type list |
| Playlist detail | `trackCollectionFragment` / `playlistId != null` | View (`AlbumDetailHeaderBinder` w/ playlist actions + `TrackViewBinder`) | MIGRATE_IN_#10 | Thin sibling of shared `TrackListScreen` w/ its own header/action slot (rename/delete/download) |
| Genres list | `selectGenreFragment` (`legacy.SelectGenreFragment`) | View (`GenreAdapter`, `GridLayoutManager`) | MIGRATE_IN_#10 | New Compose screen, small |
| Playlists list | `playlistsFragment` (`legacy.PlaylistsFragment`) | View (`GridView` + `PlaylistAdapter`) | MIGRATE_IN_#10 | New Compose screen |
| Create/Edit Playlist | `createPlaylistFragment` | View (`RecyclerView` track editor) | MIGRATE_IN_#10 | Lowest-priority browsing item — management flow, not browsing |
| Downloads list | `downloadsFragment` | View (`MultiListFragment` base, `Album` rows) | MIGRATE_IN_#10 | New Compose screen |

### B.3 — Needs a product decision before scheduling

| Surface | Destination / Mode | Current UI | Classification | Required action |
|---|---|---:|---:|---|
| Videos | `trackCollectionFragment` / `getVideos=true` | View (`TrackViewBinder`); **no live caller found anywhere in `kotlin/`** | NEEDS_DECISION | Taki's product vision (per project memory) is music-only. Confirm whether video support is still intended; if not, delete the branch/arg instead of migrating it |

### B.4 — Playback-heavy (migrate in #10, but sequenced last per the issue text)

| Surface | Destination / Mode | Current UI | Classification | Required action |
|---|---|---:|---:|---|
| Mini-player | `NowPlayingFragment` (statically hosted `FragmentContainerView` in `navigation_activity.xml`, id `now_playing_fragment`) | View | MIGRATE_IN_#10 (Step 6) | Convert contents to `ComposeView` + `MiniPlayer` on `PlaybackUiStateHolder`, per plan §Step 6. Container/visibility/insets logic in `NavigationActivity` stays untouched |
| Now Playing | `playerFragment` (`PlayerFragment`) | View (ViewBinding, `current_playing.xml`) | MIGRATE_IN_#10 (Step 7, LAST) | Most complex screen in the app; plan explicitly sanctions a staged hybrid (nested `ComposeView`s) if a single-PR rewrite is too risky |
| Queue list | Embedded in `PlayerFragment` (`ViewFlipper` second child, not a separate destination) | View (`RecyclerView` + `ItemTouchHelper` drag/swipe) | MIGRATE_IN_#10 (bundled with Now Playing) | No separate screen exists; migrates with Now Playing |
| Sleep timer | `PlayerFragment.showSleepTimerDialog()` (inline `AlertDialog`, no separate screen) | View | MIGRATE_IN_#10 (bundled with Now Playing) | Decide dialog-vs-Compose-sheet at implementation time |
| Save Playlist dialog | `PlayerFragment.showSavePlaylistDialog()` (inline dialog, `save_playlist.xml`) | View | MIGRATE_IN_#10 (bundled with Now Playing) | Same as above |

### B.5 — Intentionally legacy / deferred outside #10

Per the migration plan's explicit "Out of scope for #10" list and issue #10's silence on
admin/support surfaces (see §C for the scope-authority reasoning):

| Surface | Destination / Mode | Current UI | Classification | Required action |
|---|---|---:|---:|---|
| Settings | `settingsFragment` | View (`PreferenceFragmentCompat` wrapper) | INTENTIONALLY_LEGACY | none for #10 |
| About | `aboutFragment` | View | INTENTIONALLY_LEGACY | none for #10 |
| Server Selector | `serverSelectorFragment` | View | INTENTIONALLY_LEGACY | none for #10 |
| Edit Server | `editServerFragment` | View | INTENTIONALLY_LEGACY | none for #10 |
| Equalizer | `equalizerFragment` | View | INTENTIONALLY_LEGACY | none for #10 |
| Lyrics | `lyricsFragment` (`legacy.LyricsFragment`) | View | INTENTIONALLY_LEGACY | none for #10 (explicitly named in the plan's out-of-scope list despite being reachable from Now Playing) |

### B.6 — Dead / orphaned nav-graph nodes

Confirmed by exhaustive grep across `ultrasonic/src/main/kotlin` and `res/` — each has **zero**
call sites targeting it (only the nav-graph XML declaration itself):

| Surface | Destination / Mode | Current UI | Classification | Required action |
|---|---|---:|---:|---|
| Media Library (dup.) | `mediaLibraryFragment` → `ArtistListFragment` (same class as `artistListFragment`, no args, `toMediaLibrary` action) | View, unreachable | DEAD_CODE_CANDIDATE | Remove the destination + `toMediaLibrary` action once confirmed unused; `artistListFragment` is the live equivalent |
| Entry List (dup.) | `entryListFragment` → `EntryListFragment` (abstract base class; `entryListToTrackCollection` action) | N/A, unreachable | DEAD_CODE_CANDIDATE | Remove the destination + action from the graph. The **class** `EntryListFragment` stays — it's a live base class for `ArtistListFragment`/`AlbumListFragment`, which both override its click handling and are attached to their own destination ids |
| Now Playing (graph node, dup.) | `nowPlayingFragment` (nav-graph node — **distinct** from the statically-hosted mini-player `FragmentContainerView` id `now_playing_fragment`, which is the real, live instance) | View, unreachable via Navigation Component | DEAD_CODE_CANDIDATE | Remove the graph node; it is never navigated to (verified: no action anywhere targets `@id/nowPlayingFragment`) |

---

## C. Issue #10's closure boundary

Issue #10 body (title: *"[Compose] Migrate core browsing screens before playback-heavy
surfaces"*), quoted:

> **Migration order:** 1. Home  2. Library  3. Search  4. album / collection detail
> 5. mini-player integration across migrated screens  6. Now Playing only after the earlier
> phases are stable
>
> **Playback boundary:** Now Playing and other playback-heavy UI must be the final phase of this
> issue. Do not move Media3/service ownership into Compose just to simplify screen code.
>
> **Acceptance criteria:** Home, Library, Search, and detail browsing surfaces can be migrated
> incrementally without a big-bang rewrite. The mini-player remains available and coherent while
> legacy and Compose screens coexist. Playback/runtime contracts remain unchanged during
> migration. Now Playing is migrated only after the lower-risk phases have proven stable.

The issue text never mentions Settings, About, server management, Equalizer, or Lyrics — its
scope is explicitly "core browsing screens" plus the playback surfaces named above. The migration
plan doc makes this explicit and names names:

> **Out of scope for #10.** Settings, Equalizer, `ServerSelectorFragment`, `EditServerFragment`,
> About, legacy `PlaylistsFragment` / `SelectGenreFragment` / `LyricsFragment`. Leave as XML.
> Migrate opportunistically much later or not at all — they are not music-playback surfaces and
> carry no architectural lessons.

**Verdict: scope B** — core browsing + mini-player + Now Playing, with settings/admin/support
screens explicitly and deliberately allowed to remain View-based indefinitely. Scope A ("all app
screens become Compose") is not supported by either the issue text or the migration plan and
would be scope creep if adopted now.

One note of tension worth surfacing rather than silently resolving: the migration plan's
out-of-scope list names `legacy.PlaylistsFragment` and `legacy.SelectGenreFragment` alongside
Settings/About/Equalizer — but both are reached directly from **Library**'s "Your music"/"Browse
your collection" rows (§B.2), which the issue's acceptance criteria explicitly calls "detail
browsing surfaces" that should migrate. This audit's own analysis (frequency + directness of
reach from a migrated Library screen) says Playlists and Genres read as core browsing, not
admin/support, and recommends `MIGRATE_IN_#10` for both (see §B.2, §D) — differing from the
plan doc's literal out-of-scope list for those two specifically. `LyricsFragment`, by contrast, is
reached only from deep inside Now Playing, not from Library, so this audit agrees it can stay
`INTENTIONALLY_LEGACY`. This is a recommendation for the user to confirm or override, not a
silent scope change — flagged explicitly per the task's "do not silently broaden scope"
instruction.

**Screens required before #10 closes:** every `MIGRATE_IN_#10` row in §B.2 and §B.4 (21 browsing
surfaces/modes + mini-player + Now Playing + its 3 bundled sub-screens), pending the
Playlists/Genres scope note above and the Videos decision in §B.3.

**Screens deferred to future issues / staying legacy for now:** every row in §B.5 (Settings,
About, Server Selector, Edit Server, Equalizer, Lyrics).

**Screens intentionally staying legacy permanently (not just "for now"):** none identified beyond
§B.5 — the plan frames even those as "migrate opportunistically much later or not at all," i.e.
not a hard permanent exclusion, just outside this issue.

---

## D. Remaining browsing work — recommended order

Ranked by frequency in normal listener flows, visual discontinuity, reuse potential,
implementation risk, and existing Compose-primitive availability:

1. **Extend Compose Album Detail to folder-mode + downloaded-album modes** (§B.2 rows 3–4).
   Highest leverage, lowest risk: reuses the already-proven `AlbumDetailScreen`/
   `AlbumDetailViewModel` and its `dataLoader` seam pattern; no new screen design. Removes the
   single biggest existing inconsistency — two nearly-identical-looking "album" screens (one
   Compose, one legacy) selected by a gate the user can't see (ID3 vs. folder tagging, online vs.
   offline).
2. **Artist List + Album List.** High frequency (primary Library/Home entry points into
   already-Compose Artist Detail / Album Detail); migrating them removes the most visible
   remaining "legacy toolbar flashes then a Compose detail screen appears" discontinuity in
   normal use. Grid/list primitives already exist from phases #9/#10.2 (`LibraryBrowseRow` family).
3. **Shared `TrackListScreen` for Liked Songs, All Songs (+ in-place sort filter bar), Random,
   Daily Mix, Genre tracks, Artist-scoped filter, and folder/non-ID3 browsing** (§B.2, §E). Single
   biggest remaining surface area, but also the highest consolidation leverage — one Compose
   screen can retire roughly seven legacy render paths at once instead of one bespoke screen per
   mode.
4. **Playlists list + Playlist Detail**, reusing the new `TrackListScreen`'s row/list internals
   with a playlist-specific header/action slot for rename/delete/download and "remove from
   playlist."
5. **Genres list** — small, thin wrapper; natural pairing with the genre-tracks mode already
   covered by item 3.
6. **Downloads list** — smaller audience (offline-only), lower frequency than the above.
7. **Create/Edit Playlist** — a management flow, not a browsing surface; lowest priority among the
   `MIGRATE_IN_#10` items.
8. **Resolve the Videos `NEEDS_DECISION`** — confirm with the user whether the branch is dead
   product surface (Taki is music-only per project vision) before spending any migration effort
   on it either way.

Then, only after the above are stable and shipped (per the issue's explicit ordering):

9. **Mini-player → Compose** (issue Step 5 / plan Step 6).
10. **Now Playing → Compose, LAST** (issue Step 6 / plan Step 7), including the bundled Queue,
    sleep-timer dialog, and Save-Playlist dialog.

---

## E. TrackCollection strategy

Evaluated dimensions for the eight remaining non-album `TrackCollectionFragment` modes (Liked
Songs, All Songs + filter bar, Random, Daily Mix, Genre, Artist-scoped, folder/non-ID3 browsing,
Playlist detail):

- **Common row model:** yes — legacy already collapses these into two binder families
  (`TrackViewBinder` plain rows vs. `LibraryTrackBinder` "library-styled" rows with a heart). Both
  map cleanly onto a single Compose row (the existing `TakiTrackRow`, already built for Album
  Detail) with a `showHeart`/`rank` variant flag — no new visual design needed.
- **Title/header differences:** real, but bounded — no header (Daily Mix, Videos), plain header
  (folder browsing, artist-scoped), and a hero header **with CRUD actions** (Playlist detail:
  rename/delete/download). The CRUD case is the one genuine outlier.
- **Sorting:** the "All Songs" mode's in-place `FilterButtonBar` (switch to Random / Liked /
  Genre / Artist without leaving the screen) is really a mode-switcher hiding inside one nav
  destination — this maps naturally onto one ViewModel holding a `sortOrder`/mode enum rather than
  five separate screens.
- **Loading source:** every mode already funnels through one `TrackCollectionModel` method
  (`getStarred`/`getAllSongs`/`getRandom`/`getDailyMix`/`getSongsForGenre`/`getSongsForArtist`/
  `getMusicDirectory`/`getPlaylist`) — this is a natural fit for the `dataLoader`-seam pattern
  already used in `AlbumDetailViewModel`/`ArtistDetailViewModel`: one sealed `TrackListSource`
  with one implementation per legacy method.
- **Playback / queue insertion:** identical pattern already proven in Compose Album Detail
  (`TakiTrackRow` tap → play-from-index via `MediaPlayerManager`) — directly reusable.
- **Context menus:** almost identical (`context_menu_track_collection`) except Playlist detail
  adds one item ("remove from playlist") — modelable as an optional extra action, not a reason to
  fork the screen.
- **Download state, pagination, pull-to-refresh, current-track indicator:** all either already
  solved by Album Detail's Compose implementation (pull-to-refresh, current-track marker via
  `PlaybackUiStateHolder`) or a small additive concern (pagination is just "load more" on scroll,
  already present as `append`/`offset` in several `TrackCollectionModel` methods).
- **The one real outlier:** folder/non-ID3 browsing mixes **Album rows and Track rows in the same
  list** (`AlbumRowDelegate` alongside `TrackViewBinder`) — a shared screen needs a
  discriminated-union row type, not a pure `List<Track>`.

**Recommendation: B — a small number of distinct Compose surfaces**, specifically:

- One shared `TrackListScreen` (state-driven by a `TrackListMode` sealed type wrapping the
  `TrackListSource` seam) covering Liked Songs, All Songs (+ its filter-bar sort switching),
  Random, Daily Mix, Genre, Artist-scoped, and folder/non-ID3 browsing (with a mixed-row list
  type for the last one).
- Playlist Detail as a **thin sibling** reusing the same row/list/pagination internals but with
  its own header composable (rename/delete/download actions) — not forced into the exact same
  screen, mirroring the project's own precedent from phase 4C: `DetailPrimaryPlayButton` was
  extracted and shared, but `DetailHero`/`DetailActionRow` were deliberately **not** forced into
  one shape between Album and Artist Detail because their action sets genuinely diverged. The
  same judgment applies here to Playlist Detail's header.

This explicitly rejects pure option A (one universal screen for every mode including Playlist
Detail) as overreach, and rejects C (keep specific modes legacy) as leaving real duplication and
discontinuity on the table when the reuse case is this strong.

---

## F. Playback phases — confirmed order

1. Remaining browsing migrations (§D items 1–8).
2. Mini-player → Compose (`NowPlayingFragment` contents only; `NavigationActivity`'s container,
   visibility, and inset logic untouched).
3. Now Playing → Compose, **LAST** (`PlayerFragment`, including the bundled Queue list,
   sleep-timer dialog, and Save-Playlist dialog). The migration plan explicitly sanctions a
   staged/hybrid nested-`ComposeView` approach for this one screen if a single-PR rewrite proves
   too risky — this is the only screen where that in-place hybrid hosting is pre-approved.

No changes recommended to this order — it matches both issue #10's own text and the existing
architecture doc.

---

## G. Deferred surfaces (exact list, staying View outside #10)

- `settingsFragment` (Settings)
- `aboutFragment` (About)
- `serverSelectorFragment` (Server Selector)
- `editServerFragment` (Edit Server)
- `equalizerFragment` (Equalizer)
- `lyricsFragment` / `legacy.LyricsFragment` (Lyrics)

(Playlists and Genres are *not* included here — see the scope note in §C recommending they move
to `MIGRATE_IN_#10` despite being named in the plan doc's original out-of-scope list. Confirm or
override before scheduling.)

---

## H. Dead-code candidates

**Nav-graph nodes** (remove only after confirming with a broader grep / instrumentation that
truly nothing depends on them — see §B.6 for full evidence):

- `mediaLibraryFragment` destination + `toMediaLibrary` action — dead, `artistListFragment` is the
  live equivalent.
- `entryListFragment` destination + `entryListToTrackCollection` action — dead as a destination;
  the `EntryListFragment` **class** stays (live base class for `ArtistListFragment`/
  `AlbumListFragment`).
- `nowPlayingFragment` nav-graph node — dead; do not confuse with the statically-hosted mini-player
  `FragmentContainerView` (id `now_playing_fragment`), which is the real, live instance and must
  not be touched.

**Code-level (not standalone UI surfaces):**

- `trackCollectionFragment`'s `albumListType` nav argument — read only to suppress a "Play All"
  menu item; no call site anywhere sets it when navigating to `trackCollectionFragment` (the only
  `albumListType` writers belong to the unrelated `AlbumListFragmentArgs`). `isAlbumList` is
  effectively always `false` today.
- `albumListFragment`'s `albumListToTrackCollection` action — declared in the nav graph but
  `AlbumListFragment.onItemClick()` calls `NavigationGraphDirections.toTrackCollection(...)`
  directly instead, so the fragment-scoped action is unused.

None of these were removed as part of this audit (audit-only, no implementation) — they're listed
so they aren't accidentally preserved by a future migration that carries them forward as "needed
compatibility."

---

## I. Coverage guard: `ComposeMigrationCoverageTest` (design only, not implemented)

**Goal:** fail loudly when a new nav-graph destination — or a new `trackCollectionFragment` mode
— is added without an explicit migration classification, so this audit's matrix can't silently
go stale.

**Design:**

1. **A registry**, e.g. `ultrasonic/src/test/kotlin/org/moire/ultrasonic/ui/ComposeMigrationRegistry.kt`:
   a simple `Map<String, MigrationStatus>` keyed by destination id (for ordinary destinations) or
   by `"trackCollectionFragment:<argName>"` (for each of the boolean/string args that select a
   `TrackCollectionFragment` mode — `isAlbum`, `getStarred`, `getRandom`, `getVideos`, `dailyMix`,
   `genreName`, `playlistId`, `libraryRoot`), with `MigrationStatus` mirroring this audit's
   vocabulary (`COMPOSE`, `HYBRID_BY_DESIGN`, `MIGRATE_PENDING`, `DEFERRED`, `DEAD`).
2. **The test**, `ultrasonic/src/test/kotlin/org/moire/ultrasonic/ui/ComposeMigrationCoverageTest.kt`,
   living alongside the existing `ArchitectureGuardTest.kt` (same resource-less-Robolectric-safe
   style already used in this repo — read the raw XML file from disk rather than inflating Android
   resources, the same technique `TakiTokensTest`/`NavigationChromeSelectionTest` rely on):
   - Read `ultrasonic/src/main/res/navigation/navigation_graph.xml` as raw text.
   - Extract every `<fragment android:id="@+id/(\w+)"` via a simple regex (no full XML parsing
     needed — this only needs to catch *new* ids, not model the graph's structure).
   - Assert every extracted id has a registry entry; fail with the specific missing id(s) if not.
   - Separately, extract every `<argument android:name="(\w+)"` nested under the
     `trackCollectionFragment` node specifically, and assert each is either a known
     already-classified mode-selecting arg or an explicitly allow-listed "not mode-selecting" arg
     (e.g. `id`, `name`, `size`, `offset`, `refresh`, `autoPlay`, `shuffle`, `parentId`,
     `playlistName` — args that parameterize a mode rather than select one). A new boolean/string
     arg landing on that node without being classified either way fails the test.
3. **Why this shape and not something heavier:** the repo already has a working precedent for
   "read the resource file as text, assert on it" in `ArchitectureGuardTest`/`TakiTokensTest`, and
   this project's test environment is deliberately resource-less Robolectric (per prior phases'
   documented test-infra constraints), so a real `NavGraph`/`NavInflater` load isn't available
   without extra `includeAndroidResources` wiring that isn't otherwise needed here. A regex
   extraction is brittle only to the extent that someone reformats the XML unusually — reasonable
   for a small, hand-maintained navigation graph.

Not implemented in this audit pass — this is a "design it, don't build it yet" deliverable per
the task's own instructions; building it now would be scope creep beyond an audit.

---

## J. Pixel navigation crawl

**Not performed.** This audit session has no `adb`/device-bridge tool available in its
environment (`adb devices` — command not found; no Android device automation tool is present in
this session's toolset), unlike prior phases where Pixel 7 validation was performed with direct
hardware access. Fabricating a crawl result would violate the audit's own read-only, no-guessing
standard, so this section is left as an explicit gap rather than invented data.

**Recommendation:** run the crawl listed in the task (Home → Library → Search → Albums → Album
Detail → Artists → Artist Detail → Songs → Liked Songs → Liked Albums → Playlists → Downloads →
Genres → Box Sets → Collection Detail → Daily Mix → mini-player → Now Playing → Queue → Lyrics)
in a session/environment with device access, recording for each screen: Compose vs. legacy visual
signature, toolbar ownership, and any visual discontinuity — this audit's matrix (§B) predicts
exactly where the legacy/Compose boundary should be visible (every `MIGRATE_IN_#10` row in §B.2
is a place where a visible "legacy chrome" moment should still be reproducible today).

---

## K. Recommended next implementation phase

**Phase 4D: extend Compose Album Detail to folder-mode + offline/downloaded albums.**

Rationale: highest reuse leverage (no new screen, extends `AlbumDetailViewModel`'s existing
`dataLoader` seam to two more data sources: `TrackCollectionModel.getMusicDirectory` and
`getDownloadedAlbumTracks`), lowest risk (the screen shell, row rendering, header, and actions are
already proven and shipped), and it directly removes the most confusing existing inconsistency —
two visually-similar "album" screens whose Compose/legacy split depends on a gate
(`shouldUseComposeAlbumDetail`) the user has no way to observe. This is a tightly-scoped,
single-PR-sized phase, consistent with every phase so far.

Scope for that phase, precisely:
- Extend `AlbumDetailViewModel`'s data-loader seam to accept a folder-mode source
  (`getMusicDirectory`) and an offline source (`getDownloadedAlbumTracks`).
- Loosen `shouldUseComposeAlbumDetail`'s `usesId3` term and `DownloadedAlbumFragment`'s
  `allowComposeAlbumDetail=false` override once the new sources are verified against real data
  (folder-tagged server + a downloaded album on-device).
- Do **not** touch any other `TrackCollectionFragment` mode in this phase — Liked Songs, All
  Songs, Random, Daily Mix, Genre, Playlist detail, Artist-scoped, and plain folder/index browsing
  all stay legacy until the separate `TrackListScreen` phase (§D item 3) that follows.
