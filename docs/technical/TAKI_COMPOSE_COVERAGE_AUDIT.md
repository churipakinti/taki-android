# Taki Compose Migration — Coverage Audit (Issue #10)

**Status:** issue #10 is CLOSED as completed (phase 4M4, `READY_TO_CLOSE_#10 = YES` — see §P).
Everything below §P describes **post-#10 residual UI migration** (the two remaining non-Compose
surfaces #10 itself classified as out of scope: the intentionally-hybrid Box Sets shell and the
intentionally-legacy About screen), not a reopening of #10. Phase 5A1 baseline: `589f24f0`
(Migrate rename playlist dialog to Compose). Phase 4L baseline: `develop` at `16ddc285` (Migrate
Sleep Timer to Compose sheet); phase 4M1 baseline: `768a2a0f` (Finalize Compose migration coverage
audit); phase 4M2 baseline: `9b6a8de6` (Migrate folder browsing to Compose); phase 4M3 baseline:
`5bdb486b` (Migrate playlist editor to Compose); phase 4M4 baseline: `73920322` (Migrate final
playlist dialogs to Compose). This supersedes the phase-4C version of this document (baseline
`5f8bdc6a`), which is now stale — every `MIGRATE_IN_#10` row it listed has since shipped (Artist
List, Album List, all `TrackCollectionFragment` browsing modes, Playlists, Genres, Downloads,
mini-player, Now Playing, Up Next, Lyrics, Sleep Timer, folder browsing, Create Playlist, Save
Playlist, Create Playlist naming, Rename Playlist).

Sources used: `ultrasonic/src/main/res/navigation/navigation_graph.xml` (read directly, not
recalled), every Fragment class reachable from the graph, `NavigationActivity.kt`'s
`onDestinationChangedListener`/`hidesSupportActionBar`/`miniPlayerHiddenFor`/`updateChromeVisibility`
(chrome/toolbar/mini-player rules — read in full, not summarized from memory), `git log
5f8bdc6a..16ddc285` (27 commits), GitHub issue #10 and #21–#24 bodies (fetched live via `gh issue
view`), and a live Pixel 7 crawl (`2B191FDH200E36`) via `adb`/`uiautomator`.

---

## A. Executive summary

**Current (post-phase 5A4):**

| Metric | Count |
|---|---:|
| Total reachable UI surfaces classified | 27 |
| — Compose | 26 |
| — Hybrid (Compose + View by design) | 0 |
| — Legacy View/XML, still core-browsing (`MIGRATE_IN_#10` remaining) | 0 |
| — Legacy View/XML, intentionally out of scope | 1 |
| Unclassified | **0** |

Arithmetic: 26 + 0 + 0 + 1 = 27. Phase 5A1 (§Q) moved Box Sets (hybrid) and About (legacy) to full
Compose, 21/1/5 → 23/0/4. Phase 5A2 (§R) moved Server Selector to full Compose, 23/0/4 → 24/0/3.
Phase 5A3 (§S) moved Edit Server to full Compose, 24/0/3 → 25/0/2. Phase 5A4 (§T) moved Settings to
full Compose, 25/0/2 → 26/0/1. Remaining intentionally-legacy surface: Equalizer (§B.4). See
§Q/§R/§S/§T for the full before/after detail of each phase. Everything below this line up to §P is
the historical record as of issue #10's closure and is preserved unedited; §Q, §R, §S and §T append
the post-#10 updates.

**As of #10's closure (phase 4M4, historical):**

| Metric | Count |
|---|---:|
| Total reachable UI surfaces classified | 27 |
| — Compose | 21 |
| — Hybrid (Compose + View by design) | 1 |
| — Legacy View/XML, still core-browsing (`MIGRATE_IN_#10` remaining) | 0 |
| — Legacy View/XML, intentionally out of scope | 5 |
| Dead / unreachable, removed phase 4L | 2 (nav nodes) + 2 (dead adapter/binder classes) |
| Dead / unreachable, removed phase 4M2 | 3 (dead adapter/layout files, Create/Edit Playlist picker) |
| Dead / unreachable, removed phase 4M3 | 1 (`save_playlist.xml`) |
| Dead / unreachable, removed phase 4M4 | 1 (`create_playlist.xml`, `playlist.create_name`) |
| Dead / unreachable, product decision required | 1 (Videos mode) |
| Unclassified | **0** |

Arithmetic: 21 + 1 + 0 + 5 = 27 reachable surfaces. Videos is excluded from "reachable" because it
has zero live callers (confirmed by exhaustive grep — see §F). Phase 4M1 moved folder/non-ID3
browsing from legacy to Compose (18→19, 3→2); phase 4M2 moved Create/Edit Playlist from legacy to
Compose (19→20, 2→1); phase 4M3 moved the last matrix-listed blocker, Save Playlist, from legacy to
Compose (20→21, 1→0) and additionally migrated the Create Playlist naming `AlertDialog` — a live
transient dialog discovered during 4M2 that was never counted as its own matrix row (see §M's own
note) and is not counted as one here either, to keep this arithmetic reconciling with the
historical row-based count; it is documented as a migrated transient sub-surface in §N instead.
Phase 4M4 migrated the third such transient, Rename Playlist (discovered during 4M3's own audit and
deliberately deferred — see §N), also not counted as a standalone matrix row for the same reason;
see §P. The matrix totals (21/1/0/5/27) are unchanged since 4M3. See §B.1/§B.3, §L, §M, §N, and §P
below.

---

## B. Canonical matrix

### B.1 — Compose (DONE)

| Surface | Destination / Mode | Owner | Notes |
|---|---|---|---|
| Home | `homeFragment` | Fragment | |
| Library | `mainFragment` | Fragment | |
| Search | `searchFragment` | Fragment | |
| Artist List | `artistListFragment` | Fragment | migrated since phase-4C baseline |
| Artist Detail | `artistDetailFragment` | Fragment | phase 4C |
| Album List | `albumListFragment` | Fragment | migrated since phase-4C baseline |
| Album Detail (id3 + folder + offline/downloaded) | `trackCollectionFragment` (`isAlbum=true`) and `downloadedAlbumFragment` (routes through the same Fragment/ViewModel) | Fragment | phases 4A + 4D + "converge album detail modes" (`400fe1f8`) unified every album shape into one Compose screen |
| Playlist Detail | `trackCollectionFragment` (`playlistId != null`) | Fragment | phase 4F3, specialized sibling of Album Detail |
| Shared Track List (Liked Songs, All Songs, Random, Artist-scoped filter, Genre tracks, Daily Mix) | `trackCollectionFragment` (`useLibraryTrackRows` = `libraryRoot`\|`getStarred`\|`genreName!=null`\|`dailyMix`) | Fragment | phases 4F1/4F2 collapsed 6 legacy modes into one Compose screen + `TrackListViewModel` |
| Playlists list | `playlistsFragment` (`PlaylistListFragment`) | Fragment | phase 4G1 |
| Genres list | `selectGenreFragment` (`GenreListFragment`) | Fragment | phase 4G2 |
| Downloads list | `downloadsFragment` | Fragment | |
| Collection Detail | `collectionDetailFragment` | Fragment | phase 4B |
| Mini-player | statically-hosted `now_playing_fragment` container | Activity-hosted Fragment | |
| Now Playing | `playerFragment` | Fragment | |
| Up Next | embedded in `playerFragment` (not a separate destination) | Fragment | |
| Lyrics | `lyricsFragment` | Fragment | |
| Sleep Timer | bottom sheet launched from `playerFragment` | Fragment/dialog | baseline commit `16ddc285` |
| Folder/non-ID3 browsing (mixed directory + track rows) | `trackCollectionFragment` (`isAlbum=false`, no `playlistId`, not a library-track-rows mode) | Fragment | phase 4M1 — reached only from Compose Artist List's "Index" row tap; every deeper directory tap routes to the already-Compose Album Detail (phase 4H1 handles further nesting) |
| Create Playlist (track picker) | `createPlaylistFragment` | Fragment | phase 4M2 — always *creates* (no edit mode exists in the legacy screen or this port); draws no header of its own, relies on the shared Material toolbar exactly like the legacy screen did |
| Save Playlist (transient sheet over Now Playing) | `PlayerFragment.offerSavePlaylist()` (not a nav destination) | Fragment-hosted Compose sheet | phase 4M3 — replaces the legacy `AlertDialog`; saves the current playback queue, unchanged server call |
| Box Sets list | `collectionListFragment` | Fragment | **phase 5A1** (post-#10) — was the hybrid XML-shell-over-`RecyclerView` row in §B.2 below; now full Compose (`CollectionListScreen`) |
| About | `aboutFragment` | Fragment | **phase 5A1** (post-#10) — was the intentionally-legacy row in §B.4 below; now full Compose (`AboutScreen`) |
| Server Selector | `serverSelectorFragment` | Fragment | **phase 5A2** (post-#10) — was the intentionally-legacy row in §B.4 below; now full Compose (`ServerSelectorScreen`), including the delete confirmation (`DeleteServerSheet`, replacing the legacy `ErrorDialog`) |
| Edit Server | `editServerFragment` | Fragment | **phase 5A3** (post-#10) — was the intentionally-legacy row in §B.4 below; now full Compose (`EditServerScreen`), both New (onboarding) and Existing (full editor) modes, plus the discard-changes confirmation (`DiscardServerChangesSheet`, replacing the legacy `AlertDialog`) |
| Settings | `settingsFragment` | Fragment | **phase 5A4** (post-#10) — was the intentionally-legacy row in §B.4 below; now full Compose (`SettingsScreen`), top level + all 6 nested groups rendered from one static `SettingsDefinitions` tree, plus three new transient sheets (`SettingsChoiceSheet`/`SettingsConfirmSheet`/`SettingsInfoSheet`) replacing the legacy `ListPreference` dialogs and `ConfirmationDialog`/`InfoDialog`/`ErrorDialog` usages |

### B.2 — Hybrid by design

**None as of phase 5A1.** The one row this section ever listed, Box Sets, moved to full Compose
(§B.1) — see §Q. Kept as a heading for historical navigability; the original row text is preserved
in §Q's before/after table rather than deleted here.

### B.3 — Legacy, still core browsing (`MIGRATE_IN_#10` remaining)

**None.** Every row this section ever listed (folder/non-ID3 browsing, Create/Edit Playlist, Save
Playlist dialog) has been migrated to Compose — see §B.1 and §N.

### B.4 — Intentionally legacy / out of scope for #10

**Updated in phase 5A1 (post-#10):** About moved to full Compose — see §Q. **Updated in phase 5A2
(post-#10):** Server Selector moved to full Compose — see §R. **Updated in phase 5A3 (post-#10):**
Edit Server moved to full Compose — see §S. **Updated in phase 5A4 (post-#10):** Settings moved to
full Compose — see §T. The remaining row is unchanged.

| Surface | Destination | Verified via |
|---|---|---|
| Equalizer | `equalizerFragment` | `NavigationActivity.hidesSupportActionBar`/`updateChromeVisibility` still special-case it; Pixel-verified, tokenized dark theme, no toolbar, correct back nav |

Confirmed against issue #10's acceptance criteria (§H below): none of these are "core browsing" or
"playback-heavy" surfaces the issue names. `docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md`'s "Out
of scope for #10" section has been corrected in this pass — see §E.

### B.5 — Product decision required

| Surface | Destination / Mode | Evidence | Recommendation |
|---|---|---|---|
| Videos | `trackCollectionFragment` (`getVideos=true`) | Zero live callers: `grep -r "getVideos.*=.*true"` across `ultrasonic/src/main/kotlin` returns nothing. The arg exists only as a declared nav argument and a dead `if (getVideos)` branch inside `TrackCollectionFragment.kt` (~line 1159) | `REMOVE_FROM_TAKI` — see §F |

---

## C. Dead code removed this pass (`REMOVE_NOW`)

Each confirmed dead by exhaustive grep across `ultrasonic/src/main` (kotlin + res + test) before
removal — zero references outside the artifact itself:

1. **`mediaLibraryFragment`** nav destination + **`toMediaLibrary`** action — an unreachable
   duplicate of `artistListFragment` (same class, no args). Removed from `navigation_graph.xml`.
2. **`entryListFragment`** nav destination + **`entryListToTrackCollection`** action, and the
   **`EntryListFragment.kt`** class itself — the class had **zero subclasses** left (both
   `ArtistListFragment` and `AlbumListFragment`, its only former subclasses, moved to Compose and
   stopped extending it; they only reference it in explanatory comments now). Removed the nav
   nodes and deleted the file.
3. **`albumListToTrackCollection`** action (scoped under `albumListFragment`) — unused;
   `AlbumListFragment` calls the global `NavigationGraphDirections.toTrackCollection(...)` action
   directly instead. Removed.
4. **`FolderSelectorBinder.kt`** (adapter) + its **`list_header_folder.xml`** layout — its only
   caller was `EntryListFragment.onViewCreated()` (removed in step 2 above); fully superseded by
   the Compose `TakiFolderSelectorHeader`. Zero remaining instantiations. Deleted both files.
5. **`ic_menu_select_folder.xml`** (drawable) + **`select_artist.folder`** string (all 17 locale
   files) — transitively orphaned by step 4; `lintDebug` itself caught both as new
   `UnusedResources` errors on the first post-cleanup gate run (its only caller was
   `FolderSelectorBinder`'s popup-menu icon/label). Deleted the drawable and removed the string
   from every locale file (`values/`, `values-cs/`, `values-de/`, `values-es/`, `values-fr/`,
   `values-gl/`, `values-hu/`, `values-it/`, `values-ja/`, `values-nb-rNO/`, `values-nl/`,
   `values-pl/`, `values-pt/`, `values-pt-rBR/`, `values-ru/`, `values-zh-rCN/`, `values-zh-rTW/`).

**Known remaining orphan, deliberately left alone:** `music_library.label` /
`music_library.label_offline` (all 17 locale files) became unreferenced when step 1 removed
`mediaLibraryFragment`'s `android:label` attribute, but `lintDebug` did **not** flag either as
`UnusedResources` (Android Lint's resource-usage graph does not treat a nav-graph `android:label`
attribute the same way it treats a layout resource reference, so this was already an
untracked/invisible-to-lint orphan pattern, not something this pass newly broke lint's coverage
of). Since the lint gate is green without touching it and it costs 17 more file edits to remove
for a purely cosmetic-orphan string with no build-visible consequence, it was **left in place** —
noted here so a future pass doesn't need to rediscover it.

Verified safe: recompiled (`compileDebugKotlin`, `compileDebugUnitTestKotlin`) and reran the full
unit suite after every removal — still **1094 tests, 0 failures**. `lintDebug` initially failed on
the two orphans in item 5 (caught by the gate itself, exactly as intended); fixed and reran green.
Zero new detekt findings throughout.

Nothing else was removed. `ArtistListModel.kt`/`AlbumListModel.kt`/`isStandalone` branches/the
`AlbumListFragment` `refresh` arg are **not** touched here — they are issue #23's explicit scope
(confirmed by reading #23's body live) and #23 remains open, unduplicated.

---

## D. Navigation graph audit

Every remaining destination and action in `navigation_graph.xml` was checked for a live caller.
Findings beyond §C: none. No duplicate destinations, no dead actions, no unused arguments were
found beyond the ones already removed. `trackCollectionFragment`'s `albumListType` argument is
still read-only/effectively-always-`false` dead weight (documented, not removed — it's load-bearing
enough to need its own small PR, not a one-line delete) as previously noted; left as-is per the
"only remove if clearly dead **and safe**" instruction — a partial removal here risks missing a
subtler caller given the argument feeds a "Play All" menu-item suppression check, not just
navigation.

---

## E. Migration-plan doc correction

`docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md`'s "Out of scope for #10" section originally named
`PlaylistsFragment`, `SelectGenreFragment`, and `LyricsFragment` as staying legacy indefinitely.
All three were in fact migrated (Playlists/Genres in phases 4G1/4G2, confirmed by the phase-4C
audit's own recommendation to reclassify them as core browsing; Lyrics in the baseline commit
range). A status-update note was appended in place (not a rewrite) recording this without erasing
the original decision text — see that file's "Out of scope for #10" section.

---

## F. Videos — product decision

**Recommendation: `REMOVE_FROM_TAKI`.**

Evidence:
- `getVideos=true` has **zero live callers** anywhere in `ultrasonic/src/main/kotlin` (confirmed
  by grep, not by absence-of-memory).
- The nav argument and its handling branch in `TrackCollectionFragment.kt` are the only surviving
  code; no menu item, deep link, server-capability check, or UI entry point sets it.
- Per project memory, Taki's product vision is an explicitly **music-only** fork of Ultrasonic —
  video support is an inherited Subsonic-API feature Taki never exposed a way to reach.
- Removing it is **not** a tiny one-line change: `getVideos` threads through
  `TrackCollectionFragment.kt` (~line 1123, 1159–1161), `TrackCollectionModel`/`GenericListModel`,
  and the nav-graph argument declaration. That's more than the "small and obvious" bar this pass
  allows itself, so it was **not removed in this pass** — only decided and documented, per the
  task's explicit instruction to make the decision without doing a large removal inline.
- Recommend a small, distinct follow-up issue scoped exactly to deleting the dead `getVideos` path
  (arg, branch, any related `TrackCollectionModel.getVideos()`/menu resources) once filed.

---

## G. Known debt reconciliation

| Debt | Current status | Blocker for #10? |
|---|---|---|
| #21 Artist Radio StrictMode main-thread Room access | Still open, unaddressed (verified body live) | No — pre-existing, explicitly decoupled from Compose migration by its own text |
| #22 Album List not reloading after folder change | Still open, unaddressed | No — same, explicitly pre-existing/out of scope for its migration phase |
| #23 Dead Artist/Album list legacy cleanup (`ArtistListModel`/`AlbumListModel`/`isStandalone`/dead `refresh` arg) | Still open; `ArtistListModel.kt`/`AlbumListModel.kt` confirmed still present on disk, unused by the migrated screens | No — explicitly scoped as its own cleanup issue, correctly left alone here |
| #24 Visualizer Mode | Still open, explicitly out of scope for this phase per the task brief | No |
| Online ID3 Album Detail missing per-row "already downloaded" indicators | **Confirmed still real** — `AlbumDetailUiState.showDownloadStatus` is hardcoded `true` only for the offline/downloaded album mode (`comment: "True only for the offline/downloaded album (issue #10 phase 4H1)"`); an online album with some tracks already downloaded shows no per-row indicator | No — cosmetic parity gap, not a regression of any acceptance criterion |
| Up Next reorder-under-shuffle limitation | **Not reproduced.** `UpNextUiState`/`UpNextScreen` now thread `playOrderIndex` through `moveItemInPlaylist`/`getUnshuffledIndexOf` consistently; no code or filed issue evidences a remaining limitation. Not creating a speculative issue for it. | No |
| Lyrics limitations | None found beyond expected "no data for this track" empty state (verified live: Lyrics screen correctly showed an empty state for a track with no synced/plain lyrics, not a bug) | No |
| Landscape behavior | Not audited in this pass (no landscape-specific complaint or regression surfaced by the migration commits' own messages) | No |
| StrictMode findings from Pixel testing | None observed in this pass's crawl (`adb logcat -b crash` / `*:E` both empty across the full crawl) | No |
| Stale "legacy"/"TODO Compose" comments | None found — grepped for "legacy Now Playing", "legacy Lyrics", "legacy Sleep Timer", "queue AndroidView", "TODO Compose", "not yet Compose" across `ultrasonic/src/main`: zero matches | No |

No new GitHub issue was filed for the online-download-indicator gap or the Videos removal in this
pass — see §J for why, and the recommendation to open them as small, distinct follow-ups.

---

## H. Issue #10 acceptance criteria — assessed against the issue's live body

> Home, Library, Search, and detail browsing surfaces can be migrated incrementally without a
> big-bang rewrite.

**Met.** 18 Compose surfaces shipped across 20+ independently-committed phases, each with its own
green test/gate run, exactly as required.

> The mini-player remains available and coherent while legacy and Compose screens coexist.

**Met.** Verified live on Pixel 7: mini-player shows correctly on Compose screens (Home, Library,
Album Detail) and on legacy screens (Settings), hides correctly on Now Playing/Lyrics per
`miniPlayerHiddenFor`, no visual discontinuity observed.

> Playback/runtime contracts remain unchanged during migration.

**Met.** `MediaPlayerManager`/`PlaybackService` ownership untouched throughout every phase in this
range (verified: no commit in `5f8bdc6a..16ddc285` touches `service/MediaPlayerManager.kt` or
`service/PlaybackService.kt` structurally — only UI-layer and `PlaybackUiStateHolder` projection
code).

> Now Playing is migrated only after the lower-risk phases have proven stable.

**Met.** Now Playing (`8d8ce040`) landed after Home/Library/Search/all detail screens/mini-player,
exactly the issue's specified order.

**All four acceptance criteria are met.** However, the issue's own body (not just this audit) also
lists "detail browsing surfaces" as in scope, and one detail-browsing mode
(folder/non-ID3 mixed browsing) plus two adjacent management surfaces (Create/Edit Playlist, Save
Playlist dialog) remain legacy — see §B.3.

`READY_TO_CLOSE_#10 = NO`

**Exact blockers:**
1. Folder/non-ID3 browsing mode of `TrackCollectionFragment` — still legacy, still reachable, still core browsing for any non-ID3-tagged server.
2. `CreatePlaylistFragment` — still legacy, reachable from the now-Compose Playlists list.
3. `PlayerFragment.showSavePlaylistDialog()` — still a legacy `AlertDialog` embedded in the now-Compose Now Playing screen.

None of these are large — each is a single, bounded screen/dialog with an existing Compose sibling
pattern to follow (`TrackListScreen`'s row model extends naturally to a mixed-row variant; the
Save Playlist dialog can become a small Compose dialog analogous to the Sleep Timer sheet). This
audit recommends a **phase 4M** to close out exactly these three before closing #10, rather than
closing #10 now with known gaps against its own acceptance text.

---

## I. Pixel 7 final crawl (device `2B191FDH200E36`)

Performed live via `adb`/`uiautomator` against a build of the current tree — after the §C
deletions, with `assembleDebug` verified to compile and install.

| Screen | Toolbar nodes | Notes |
|---|---:|---|
| Home | 0 | mini-player visible and playing (`Tick Tock` / Tom Misch) |
| Library | 0 | overflow menu (Switch collection / Add collection / Settings / About) works |
| Genres list | 0 | 2-column grid, no toolbar |
| **Genre tracks** (Acid Jazz, entered directly from Genres) | 0 | **confirms the phase-4F2/shell-continuity fix holds** — this was the exact screen flagged with a stray olive toolbar in the phase-4C audit; now clean |
| Album List | 0 | "By Name" sort visible, Compose grid |
| Album Detail (Oasis / "Wonderwall") | 0 | Compose hero, metadata line, track row — matches phase 4A/4D/converged design |
| Settings (intentionally legacy) | expected legacy chrome | tokenized dark theme, no toolbar, no bottom nav (correctly hidden per `updateChromeVisibility`), mini-player still visible (correct — Settings isn't in `miniPlayerHiddenFor`) |

Back-stack navigation verified correct at every step (`Home → Library → Genres → Genre tracks →
back → back → Library`, landing exactly on Library with no double-pop or stuck state).

`adb logcat -d -b crash` and `adb logcat -d *:E | grep churipakinti` were both **empty** across the
entire crawl — no crashes, no app-attributable errors.

This is a representative, not exhaustive, crawl per the task's own instruction — it deliberately
re-checked the two screens the phase-4C audit flagged as broken (Genre tracks, Daily Mix pattern)
plus one fresh Compose surface (Album Detail) plus one intentionally-legacy surface (Settings),
rather than re-walking all 27 surfaces already covered by Roborazzi goldens and the phase-by-phase
Pixel verification recorded in project memory for each of the 20+ commits in this range.

---

## J. Cleanup policy compliance

Everything removed in §C was: (1) proven dead by exhaustive grep before deletion, (2) small (a
handful of XML lines + two short, single-purpose Kotlin files with zero remaining callers), and
(3) verified safe by a full recompile + unit-test rerun afterward. Nothing in §B.3 (folder browsing,
Create/Edit Playlist, Save Playlist dialog) or §F (Videos) was implemented or removed in this pass
— each requires either new Compose implementation work or a multi-file removal, both explicitly
outside this audit's cleanup allowance. New issues were considered for the online-download-indicator
gap and the Videos removal but **not filed**, because filing them is better done alongside the
audit comment on #10 (§K) where the maintainer can triage priority against the three `phase 4M`
blockers directly, rather than adding standalone issues that fragment the same "final #10 push"
conversation.

---

## K. Next action (superseded by §L for folder browsing — kept for history)

`READY_TO_CLOSE_#10 = NO` as of phase 4L. Recommended next phase: **4M — close out the three
remaining `TrackCollectionFragment`-adjacent legacy surfaces** (folder/non-ID3 browsing,
Create/Edit Playlist, Save Playlist dialog), each independently shippable, after which #10's
acceptance criteria are met with zero remaining core-browsing gaps and the issue can close.

---

## L. Phase 4M1 — Folder/non-ID3 browsing migrated

Baseline `768a2a0f`. Migrated the last surviving legacy `TrackCollectionFragment` mode
(`isAlbum=false`, no `playlistId`, not a library-track-rows mode) to Compose.

**Reachability, precisely traced (not assumed):** this mode is reached from exactly one live
entry point — Compose Artist List's "Index" row tap (`ArtistListFragment.onEntryClick`,
`row.isIndex` — folder-grouping entries returned by the non-ID3 `getIndexes()` call, mixed with
real leaf `Artist` rows in the same list). Every directory tapped *from inside* this screen
navigates with `isAlbum=true` (the legacy `onItemClick`'s directory branch, ported unchanged),
which routes straight to the already-Compose Album Detail — proven by phase 4H1's own kdoc
("a folder-mode directory that contains sub-folders... now shows them as tappable folder rows").
So this screen only ever renders one level; it never navigates to itself.

**Architecture:** a new, dedicated `FolderBrowserViewModel`/`FolderBrowserUiState`/
`FolderBrowserActions`/`FolderBrowserScreen` (`ui/folderbrowser/`), not a `TrackListScreen` mode
(mixed row types) and not folded into `AlbumDetailViewModel` (no hero/disc/notes/star apply to a
bare directory). Reuses existing primitives rather than inventing new ones: `TakiEntryRow` for
directory rows (already supported title+subtitle+artwork+tap-only — no dedicated `TakiFolderRow`
needed), `TakiLibraryTrackRow` for track rows (heart + context menu, same as the shared Track
List), and the exact `TrackContextAction`/`TrackContextMenuState` shared type + `R.menu
.context_menu_track_collection` eight-action menu Album Detail/Track List already dispatch through
`ContextMenuUtil`. The directory loader (`service.getMusicDirectory`) is a direct port of
`AlbumDetailViewModel`'s own folder-mode `albumLoader` seam. `MediaPlayerManager`/queue/playback
ownership untouched — the ViewModel only reads; the Fragment still dispatches every playback call.

**A real chrome bug found and fixed:** the first live Pixel pass showed a duplicated header — the
new screen's own `TakiScreenHeader` *and* the shared Material toolbar both rendering "01" at once.
`NavigationActivity`'s `hidesSupportActionBar` didn't recognize this destination shape (it fell
through to the shown-toolbar default, the same as the old legacy screen, which never drew a
separate header of its own and only ever showed the one shared toolbar). Fixed with a new
`isFolderBrowser` flag, added to `hidesSupportActionBar`'s signature exactly like the existing
`isLightweightHeaderTrackCollection` (Genre/Daily Mix) precedent — same treatment, same reasoning,
same deliberate exclusion from `libraryOnlyDestination`/`showsContentBackButton`. Locked by two new
`NavigationChromeSelectionTest` cases. Re-verified live after the fix: single header, correct back
stack, correct mini-player/bottom-nav visibility.

**Legacy cleanup — found, not removed (deferred, substantial):** with this mode migrated,
`AlbumRowDelegate`, `TrackViewBinder`, `HeaderViewBinder`, and `DiscHeaderBinder` (confirmed by
grep: each is instantiated *only* inside `TrackCollectionFragment.kt`, nowhere else in the app) —
plus the ~200-line `super.onViewCreated()` legacy View-setup branch itself — are now unreachable
for every *live* purpose. The only remaining paths into that branch are the dead `getVideos` mode
and a defensive "id==null" ViewPager-bug fallback, neither with a live caller (confirmed phase 4L
and re-confirmed here). This is a real, substantial cleanup opportunity, but removing four adapter
classes plus a large Fragment branch is more than "small and obvious" for an implementation phase
whose own brief said "if cleanup becomes substantial, defer it." **Not removed in this pass** —
flagged for a follow-up (naturally adjacent to #23's existing `TrackCollectionFragment`-family
cleanup scope, or a new dedicated issue).

**Remaining #10 blockers after this phase:** 2 (Create/Edit Playlist, Save Playlist dialog) — down
from 3. `READY_TO_CLOSE_#10` is still `NO`.

---

## M. Phase 4M2 — Create/Edit Playlist migrated

Baseline `9b6a8de6`. Migrated the legacy `CreatePlaylistFragment` track picker to Compose.

**Audit finding that reframed the task: there is no edit mode.** The legacy screen (and this port)
always *creates* — `getMusicService().createPlaylist(id = null, ...)` is the only call it ever
makes. It never loads an existing playlist's membership, never updates or deletes one, and has no
reorder. The playlist's *name* is chosen one screen earlier, in a small legacy `AlertDialog`
(`PlaylistListFragment.showCreatePlaylistDialog()`, `R.layout.create_playlist`) that validates
non-blank/trimmed input and then navigates here with the name fixed as a nav arg — that dialog is
a separate, still-legacy artifact, explicitly out of this phase's scope (not `CreatePlaylistFragment`
itself), noted here so it isn't mistaken for forgotten work.

**Architecture:** a new `CreatePlaylistViewModel`/`CreatePlaylistUiState`/`CreatePlaylistActions`/
`CreatePlaylistScreen` (`ui/createplaylist/`). The paged All Songs/By Artist/By Genre loaders (with
their exact offset/`canLoadMore` bookkeeping) are adapted from `TrackListViewModel`'s own port of
the same `TrackCollectionModel` methods — not shared directly, since this screen additionally needs
per-track selection state and a free-text search mode neither Track List nor its ViewModel have.
Selection is a `LinkedHashMap`, matching the legacy `selectedTracks` field exactly: re-selecting a
deselected track moves it to the *end* of the order, because that order becomes the created
playlist's own track order. Reuses `TakiSearchField`/`TakiSortMenu`/`TakiArtwork` rather than
inventing new components; a small dedicated checkable row replaces the legacy `PlaylistTrackPickerBinder`
(tap-to-toggle, no playback, no context menu — deliberately not `TakiLibraryTrackRow`, whose
tap-to-play/heart/menu semantics don't fit a selection picker). Draws no Compose header — this
destination relies on the shared Material toolbar exactly like the legacy screen did, so no new
`NavigationActivity` chrome flag was needed (confirmed live: single header, no duplication).

**A real crash found and fixed live:** the first Pixel pass crashed the app (`FATAL EXCEPTION`,
uncaught `SocketTimeoutException`) while submitting a search. Root cause: `onSearchSubmit()`
launched its own child coroutine inside `viewModelScope` and *rethrew* a caught exception from
inside it, intending the Fragment's `toastingExceptionHandler`-wrapped launch to catch it — but
that launch is a different coroutine scope entirely and can never observe it, so the rethrow had
no handler and crashed the process. Fixed by swallowing the exception there instead (`return@launch`),
matching every other load path in the class (`dispatchLoad`), at the cost of a failed search going
silently empty rather than surfacing a toast — documented in the ViewModel's own kdoc as a
deliberate, necessary correction, not a shortcut. Re-verified live after the fix: the same search
against the same (network-flaky) test server now fails to an empty state with zero crashes.

**Legacy cleanup — removed (small, directly-owned, proven dead):** `PlaylistTrackPickerBinder.kt`,
`create_playlist_editor.xml`, and `list_item_playlist_track_picker.xml` — each confirmed by grep to
have zero references outside their own definitions (and one now-dead `tools:listitem` design-time
hint) once the Fragment stopped inflating them. Deleted. `PLAYLIST_CREATED_RESULT`'s contract with
`PlaylistListFragment` (the `savedStateHandle` refresh signal) is unchanged.

**Remaining #10 blockers after this phase:** 1 (Save Playlist dialog) — down from 2.
`READY_TO_CLOSE_#10` is still `NO`.

---

## N. Phase 4M3 — Final playlist dialogs migrated; #10 acceptance audit

Baseline `5bdb486b`. Migrated the last two live legacy playlist dialogs to Compose.

### Scope: two dialogs, one newly discovered

**Save Playlist** (`PlayerFragment.showSavePlaylistDialog()`) — the matrix-listed §B.3 blocker.
Audited fully before touching it: it saves the *entire current playback queue*
(`mediaPlayerManager.playlist`, in queue order, unfiltered) as a new server playlist via
`createPlaylist(id = null, ...)` — this is not the Compose track-picker flow from phase 4M2, a
genuinely different feature that happens to share a similarly-named legacy string. No blank-name
validation exists in the legacy dialog (it would happily submit an empty name), so none was
invented here. The dialog closes immediately on Save, before the network call resolves — preserved
exactly (the sheet does the same).

**Create Playlist naming** (`PlaylistListFragment.showCreatePlaylistDialog()`) — discovered during
phase 4M2's own audit, never a standalone §B.3 matrix row. Collects and validates (non-blank,
trimmed) the name for a new playlist, then navigates to the already-Compose `createPlaylistFragment`
picker — the two-step flow (name first, then track picker) is unchanged; this phase only replaces
the naming dialog's implementation, not the flow shape.

A third, related legacy dialog was found and *deliberately not touched*:
`TrackCollectionFragment.showRenamePlaylistDialog()` reuses the same `R.layout.create_playlist`
XML to rename an *existing* playlist from Playlist Detail — a different feature, at a different
call site, not named in this task's scope. `create_playlist.xml` therefore stays (still live via
that path); only `save_playlist.xml` (confirmed zero remaining references) was deleted.

### Architecture

Two new transient Compose overlays, both following the exact pattern
[`SleepTimerSheet`](../../../ultrasonic/src/main/kotlin/org/moire/ultrasonic/ui/player/SleepTimerSheet.kt)
established in phase 4K6 (scrim + sliding panel, composed as the last child of the host screen's
own root `Box`, not a system dialog or a new nav destination): `SavePlaylistSheet`/
`SavePlaylistActions` (`ui/player/`) and `CreatePlaylistNameSheet`/`CreatePlaylistNameActions`
(`ui/playlistlist/`). Both are presentation-only — no ViewModel of their own; the host Fragment
(`PlayerFragment`/`PlaylistListFragment`) owns the name/error `mutableStateOf` and every server/
navigation call, unchanged from the legacy dialogs' own division of responsibility. A new shared
`TakiTextField` (`ui/components/`) backs both sheets' input — a labelled, Taki-colored
`OutlinedTextField` with an inline error slot, since neither existing text input
(`TakiSearchField`, hint-only) fit a field that needs a label and a validation error.

### A real ArchitectureGuardTest violation caught before it shipped

`TakiTextField`'s first draft imported `MaterialTheme` directly for its text style — the project's
own `ArchitectureGuardTest` (`no raw dp/sp literals outside ui theme` / `MaterialTheme is only
imported inside ui theme`) failed immediately, exactly as designed. Fixed by using
`TakiTheme.type.body` instead, matching every other Compose file in the app.

### Legacy cleanup

Deleted `save_playlist.xml` — confirmed by grep to have zero references (`R.layout.save_playlist`,
`R.id.save_playlist_name`) outside its own definition once `PlayerFragment` stopped inflating it.
`create_playlist.xml` was **not** deleted (still live via `showRenamePlaylistDialog`, out of this
phase's scope — see above). It was later deleted in phase 4M4 once Rename migrated too — see §P.

### Tests / gates

- Tests: 1126 → 1142 (+16: 6 `SavePlaylistSheetComposeTest`, 7 `CreatePlaylistNameSheetComposeTest`,
  1 `SavePlaylistSheetScreenshotTest`, 2 `CreatePlaylistNameSheetScreenshotTest`), 0 failures
- `assembleDebug`/`assembleRelease`/`lintDebug`: green
- `detekt -Pqc`: 42 in `:ultrasonic` (baseline, zero new) + 1 pre-existing unrelated in
  `:core:subsonic-api`
- Roborazzi verify: green (3 new goldens — the populated Save Playlist sheet, the blank and
  validation-error states of the Create Playlist naming sheet)

### Pixel 7 validation — not completed this session

The Pixel 7 (`2B191FDH200E36`) that every prior phase in this range validated against disconnected
partway through this phase, before live device validation of these two new sheets could run
(`adb devices` returned empty after repeated retries; not a code issue - the device simply dropped
off USB). This matters concretely: phase 4M2's own live Pixel pass on this exact device caught a
real crash (an uncaught `SocketTimeoutException` from a coroutine-scope bug) that all 16 of that
phase's unit tests had missed. Save Playlist's flow touches the same
`createPlaylist`/network-call shape Create Playlist's picker does, so the same *class* of bug is
structurally possible here even though the code was written with that exact failure mode already
front of mind and no equivalent scope-crossing rethrow exists in either new sheet (verified by
reading, not just testing — see §N's own architecture description above: both sheets are
presentation-only and dispatch every network call through the Fragment's own already-toasting-safe
`launchWithToast`/`toastingExceptionHandler` paths, never through a sheet-owned coroutine). Unit
tests, Compose interaction tests, and Roborazzi goldens all pass, but per this project's own
demonstrated risk pattern, live validation is treated as a precondition for closing #10, not an
optional nice-to-have - see §O's verdict.

### Remaining #10 blockers after this phase

**Zero** matrix-listed (§B.3) surfaces remain. The one open item is verification, not
implementation: live Pixel validation of these two sheets.

---

## O. Issue #10 final acceptance audit (phase 4M3)

Every criterion from issue #10's own body, re-read directly (not recalled), assessed against the
current tree:

| Criterion | Status | Evidence |
|---|---|---|
| Home, Library, Search migrated | ✅ | §B.1 |
| Detail browsing (Album/Artist/Collection/Playlist Detail) migrated | ✅ | §B.1 |
| Lists (Album/Artist/Playlists/Genres/Downloads/folder browsing) migrated | ✅ | §B.1, phases 4E–4M1 |
| Mini-player coherent throughout | ✅ | verified every phase through 4M1's live crawl |
| Playback/runtime contracts unchanged | ✅ | `MediaPlayerManager`/`PlaybackService` untouched this entire range (verified by diff, not assumption, each phase) |
| Now Playing migrated after lower-risk surfaces | ✅ | phase 4K, after all browsing phases |
| Up Next Compose | ✅ | §B.1 |
| Lyrics Compose | ✅ | §B.1 |
| Sleep Timer Compose | ✅ | §B.1 |
| Folder/non-ID3 browser Compose | ✅ | phase 4M1 |
| Create Playlist picker Compose | ✅ | phase 4M2 |
| Playlist naming transient Compose | ✅ | phase 4M3 |
| Save Playlist transient Compose | ✅ | phase 4M3 |
| No remaining reachable legacy core-browsing/playback surface inside #10 scope | ✅ (implementation) / ⚠️ (verification) | §B.3 is empty; live device confirmation of the two newest surfaces is outstanding |

**Intentionally legacy (allowed, verified against #10's own acceptance text, unchanged):**
Settings, About, Server Selector, Edit Server, Equalizer — §B.4.

**Hybrid Box Sets** remains intended by design (§B.2), not a gap.

**Videos** remains dead/unreachable, classified `REMOVE_FROM_TAKI` (§F), not implemented (removal
spans multiple files, out of this task's explicit scope), not a #10 blocker either way since it was
never reachable during the migration.

**Known debt, all non-blocking, reconciled:**

| Item | Status | Blocker for #10? |
|---|---|---|
| #21 Artist Radio StrictMode | Open, unaddressed, explicitly decoupled from this migration | No |
| #22 Album List folder-change reload | Open, unaddressed, pre-existing | No |
| #23 Dead Artist/Album list legacy cleanup | Open, correctly untouched this entire range | No |
| #24 Visualizer | Explicitly out of scope for #10 | No |
| Online ID3 Album Detail missing per-row download indicator | Confirmed real (phase 4L), cosmetic | No |
| Up Next reorder-under-shuffle | Not reproduced (phase 4L), no issue filed | No |
| Dead TrackCollection/binder cleanup (`AlbumRowDelegate`/`TrackViewBinder`/`HeaderViewBinder`/`DiscHeaderBinder`) | Confirmed dead (phase 4M1), deliberately deferred - substantial, not small | No - #10's acceptance criteria are about reachable UI, not internal dead code |
| Videos removal | Recommended, not implemented (scope) | No - already unreachable |

### `READY_TO_CLOSE_#10 = NO` (superseded by §P)

**Exact blocker:** live Pixel 7 validation of the two surfaces built in this phase (`SavePlaylistSheet`,
`CreatePlaylistNameSheet`) has not been performed - the validation device disconnected mid-session.
Every other #10 acceptance criterion is met in the code as written and verified by unit/Compose/
Roborazzi tests, but this project's own track record in this exact range (a real crash phase 4M2's
tests missed and only a live device pass caught) is the reason this audit does not treat
test-green as sufficient by itself for the *final* phase closing out the issue. This is a narrow,
concrete, one-session gap - not an architectural or implementation gap - and is expected to close
as soon as a Pixel (or equivalent physical device) is available again.

---

## P. Phase 4M4 — Rename Playlist migrated; live validation completed; #10 closed

Baseline `73920322`. Migrated the third and last reachable legacy playlist dialog
(`TrackCollectionFragment.showRenamePlaylistDialog()`, discovered during 4M3's own audit and
deliberately deferred - see §N), then performed the live Pixel 7 validation 4M3 could not complete,
across all three playlist transient sheets.

### Rename Playlist: full audit before implementation

`TrackCollectionFragment.showRenamePlaylistDialog()` reused `R.layout.create_playlist` to rename an
*existing* playlist from Playlist Detail - a different feature from both Create Playlist naming
(4M3) and Save Playlist (4M3) despite sharing that layout with the former. Traced in full:
playlist ID from `navArgs.playlistId` (returns early if null); name prefilled from
`navArgs.playlistName`; validation is trim + non-blank only (no dedup), matching Create's own
rules; the dialog dismisses *before* the network result (matching Save Playlist's own timing, not
Create's, which waits); the server call is `updatePlaylist(id, name, null, null)` - `null` track
list/order means membership and ordering are never touched, matching "rename only"; success calls
`FragmentTitle.setTitle(name)` (the legacy Activity toolbar) and toasts
`playlist.updated_info`; failure toasts `playlist.updated_info_error` via the existing
`toastingExceptionHandler`, the same non-throwing, Fragment-owned path every other playlist action
in this class already uses; cancel/Back/outside-tap all dismiss without saving
(`ConfirmationDialog.Builder`'s `setCancelable(true)`, confirmed by reading `Dialogs.kt`). Playlist
Detail (Compose since phase 4F3) never refreshes its track list on rename - by design, matching the
legacy screen's own scope (rename only touches the name).

### What migrated

New `RenamePlaylistSheet`/`RenamePlaylistActions` (`ui/playlist/`), following the exact
scrim + sliding-panel pattern `CreatePlaylistNameSheet`/`SavePlaylistSheet` established in 4M3 -
presentation-only, host-owned `mutableStateOf` name/error state in `TrackCollectionFragment`
(`showRenamePlaylistSheet`/`renamePlaylistName`/`renamePlaylistError`), every server/toast call
unchanged in `renamePlaylist()`. `PlaylistDetailViewModel` gained one new method, `applyRename`, an
in-memory-only title update (no re-fetch) that is the Compose equivalent of the legacy
`FragmentTitle.setTitle(name)` call - the Compose screen owns the visible title now that the
Activity toolbar is hidden in this mode, so *something* had to carry that one line of legacy
behavior across; everything else about `renamePlaylist()` is untouched.

**Shared-primitive audit (task requirement):** `RenamePlaylistSheet` and `CreatePlaylistNameSheet`
are structurally close (title + labelled field + Cancel/primary row), but 4M3 already chose not to
share `SavePlaylistSheet`/`CreatePlaylistNameSheet` despite the same closeness, to keep each sheet's
validation and copy independently readable. `RenamePlaylistSheet` follows that same precedent
(duplicated, not extracted) rather than introducing a shared `PlaylistNameSheetContent` this phase
would be the first to actually need - reconsider only if a fourth sheet makes the duplication cost
clearly outweigh the extra indirection.

### A real bug found during required live validation, not before

Live Pixel 7 validation of Create Playlist naming (§14.A) surfaced a genuine defect neither 4M3's
nor this phase's own unit/Compose/Roborazzi tests could have caught: `NavigationActivity`'s bottom
nav and mini-player are Activity-owned overlay views, drawn **above** every Fragment's own content
in the view hierarchy (`mini-player stays Activity-owned`, per the phase 4A/4I architecture).
Neither `CreatePlaylistNameSheet` nor the new `RenamePlaylistSheet` accounted for that live chrome
band - both anchored their Cancel/primary row to `Alignment.BottomCenter` of a plain
`fillMaxSize()` Box with only `.navigationBarsPadding()` (the system gesture bar), not the app's own
mini-player + bottom-nav footprint stacked above it. On a real device, with a track loaded (the
ordinary case), the row rendered *underneath* that chrome and was confirmed untappable - a tap on
the visible "Create"/"Rename" button's own screen coordinates was swallowed by the mini-player,
opening Now Playing instead. `SavePlaylistSheet` never showed the symptom because it only opens
from `playerFragment` (Now Playing), where `NavigationActivity.miniPlayerHiddenFor` already hides
both bars - the two sheets that do open over ordinary browsing screens (Playlists list, Playlist
Detail) were the ones exposed.

**Fix:** both sheets now take a required `bottomContentInset: Dp` parameter - the exact same value
their host screen (`PlaylistListScreen`/`PlaylistDetailScreen`) already threads from
`NavigationActivity.contentBottomInset` to clear this chrome for its own scrollable content -
applied as the sheet's own bottom padding in place of `.navigationBarsPadding()` (the inset already
includes the system bar; see `contentBottomInsetFor`'s own kdoc). `PlaylistListFragment` and
`TrackCollectionFragment` now pass their already-computed `bottomInset` through to the sheet calls.
Locked with a new Compose test per sheet (`RenamePlaylistSheetComposeTest`/
`CreatePlaylistNameSheetComposeTest`) asserting the primary button's `boundsInRoot` clears a
non-zero synthetic inset - a real cross-view-hierarchy touch conflict like this can't be
reproduced in a Robolectric-only Compose tree (there is no Activity chrome to collide with), so the
test locks the *contract* (the sheet actually reserves the space it's given), not the live-device
symptom itself; only the Pixel crawl in §14 below can confirm the symptom is actually gone.

This was not part of this phase's planned implementation scope - it was a live-validation finding
in code this task explicitly required exercising end to end before closing #10. Both affected
sheets (`CreatePlaylistNameSheet`, `SavePlaylistSheet`-adjacent pattern, `RenamePlaylistSheet`) are
fixed; retested live below.

### Legacy cleanup

Deleted `create_playlist.xml` - confirmed by grep to have zero remaining references
(`R.layout.create_playlist`, `R.id.create_playlist_name`) once `showRenamePlaylistDialog` stopped
inflating it (the only remaining hits were historical kdoc mentions in `CreatePlaylistNameSheet.kt`
and this document, left as-is). Removing it made `R.string.playlist.create_name` ("Playlist name",
the layout's own `hint`) newly unused - lint's `UnusedResources` caught this immediately (not part
of the 42-issue baseline), confirming it had no other caller (not even Compose, which reuses
`download.playlist_name` for all three sheets' field labels); deleted from `values/` and the one
translated `values-es/` entry. No other resource depended on either file.

### Tests / gates

- Tests: 1142 → 1155 (+13: 1 `PlaylistDetailViewModelTest` (`applyRename`), 9
  `RenamePlaylistSheetComposeTest` (8 behavioral + 1 inset-contract regression test), 2
  `RenamePlaylistSheetScreenshotTest`, 1 `CreatePlaylistNameSheetComposeTest` (matching
  inset-contract regression test)), 0 failures
- `assembleDebug`/`assembleRelease`/`lintDebug`: green, zero new findings (baseline
  `lint-baseline.xml` actually shrank - several previously-baselined findings are no longer
  reproduced, unrelated to this phase)
- `detekt -Pqc`: 42 in `:ultrasonic` (baseline, zero new, confirmed byte-identical finding set) + 1
  pre-existing unrelated finding in `:core:subsonic-api`
- Roborazzi verify: green (2 new goldens - the populated Rename sheet and its validation-error
  state)
- `ArchitectureGuardTest`/`TakiTokensTest`/`NavigationChromeSelectionTest`: green (the guard test's
  raw-`.dp`-literal rule initially flagged this phase's own draft `= 0.dp` default parameter value,
  exactly as designed - fixed by making `bottomContentInset` a required parameter instead, matching
  `PlaylistListScreen`/`PlaylistDetailScreen`'s own convention of never defaulting it)

### Pixel 7 validation - completed this session

The device reconnected (required a physical unlock - it had locked behind the screen-lock code,
which this session correctly did not attempt to bypass) and was used for the full crawl §14
requires:

**Create Playlist naming:** confirmed the touch-conflict bug live (tapping the visible "Create"
button's own coordinates opened Now Playing instead, with a track loaded and the Playlists list
showing) before the inset fix; confirmed fixed after (button `boundsInRoot` now ends well above the
mini-player's own top edge, tap lands correctly). End-to-end: named and created a disposable
playlist ("ZZDisposable", 3 songs) via the IME "Done" submit path, landed on the Compose track
picker, selected songs, saved - "Playlist created." toast, zero crashes, no legacy `AlertDialog`,
no duplicate toolbar.

**Save Playlist:** opened from Now Playing's overflow menu; sheet pre-filled with today's date
exactly like the legacy dialog; no mini-player/bottom-nav present in this context (`playerFragment`
is in `NavigationActivity`'s `hideForDestination` set), so the inset fix is a no-op here and the
sheet rendered correctly with `.navigationBarsPadding()`'s prior behavior unchanged. No legacy
`AlertDialog`, no crash.

**Rename Playlist:** opened via Playlist Detail's overflow ("Rename playlist"); sheet correctly
prefilled with the playlist's *current* name (read from `navArgs.playlistName`, the same nav
argument the legacy dialog itself read - confirmed this is not a regression but a faithful port,
including its one pre-existing quirk: the prefill reflects the nav argument at entry, not any
server-verified live name); Cancel/Rename row correctly clears the mini-player after the fix;
renamed a pre-existing disposable test playlist ("Test_2" → "ZZRenamed"), confirmed the success
toast (`Updated playlist information for ZZRenamed`), confirmed the Compose header updated
immediately via `applyRename` (no reload), confirmed server-side persistence (a second toast-
confirmed rename call, back to "Test_2", would fail identically if the first had not actually
persisted), then renamed it back to restore original state. Playlists list does **not** pick up the
rename without a manual refresh - confirmed as pre-existing, unchanged behavior (no such hook
exists for Create Playlist's own creation-refresh path to reuse; not invented here either).

**Cleanup / incidental findings:** the disposable playlist and its downloaded copy were removed
after testing. One incidental operator error during manual testing (a mistaken tap hit "Download"
instead of "Delete" once) left three pre-existing library playlists locally partially-downloaded
(cached audio only, no server-side or naming change); left as-is - reversible, non-destructive, and
clearing it is a normal Downloads-screen action available to the user at any time. A second
"ZZDisposable, 0 songs" list row was observed after a Save Playlist attempt whose typed name did
not survive an automation artifact (likely on-device IME autocomplete substituting a previously-
typed suggestion) - a delete attempt against it failed with "Requested data was not found",
confirming the row was already-stale client-side list cache rather than a real server-side
playlist; a full app restart confirmed the live server state matches the pre-session state exactly
(8 playlists, `Test_2` unchanged). No crash, no ANR, and no data-integrity issue at any point -
confirmed via `adb logcat` across the entire session (zero `FATAL`/`AndroidRuntime` entries).

**Final representative crawl (§15):** Home, Library, Search, Playlists, Playlist Detail, Now
Playing, Up Next, Sleep Timer all checked live post-fix - correct chrome (no duplicate headers, no
stray toolbars), correct mini-player visibility rules, playback uninterrupted throughout, no
crashes or ANRs.

### Corrected playlist transient UI accounting (task-required correction to §N)

§N's own text already disclosed Rename as a deliberately deferred third transient (not silently
omitted), but the historical `2 Compose legacy dialogs migrated this phase` framing in 4M3's own
summary reads, in isolation, as if playlist transient UI migration were complete after 4M3. It was
not - explicitly, in full:

| Surface | Before 4M3 | After 4M3 | After 4M4 |
|---|---|---|---|
| Create Playlist naming | Legacy `AlertDialog` | **Compose** (`CreatePlaylistNameSheet`) | Compose |
| Save Playlist | Legacy `AlertDialog` | **Compose** (`SavePlaylistSheet`) | Compose |
| Rename Playlist | Legacy `AlertDialog` | Legacy `AlertDialog` (deferred, documented in §N) | **Compose** (`RenamePlaylistSheet`) |

Before 4M4: 2 Compose / 1 legacy. After 4M4: **3 Compose / 0 legacy** - all reachable
playlist-management transient UI in #10's scope is now Compose. None of these three were ever a
standalone row in the canonical 21/1/0/5/27 matrix (§A/§B.1); that arithmetic is unchanged by either
phase.

### Remaining #10 blockers after this phase

**Zero.** Every matrix-listed (§B.3) surface has been Compose since 4M1; every playlist transient
dialog is Compose as of this phase; the live-validation gap 4M3 left open (§N) is closed by the
Pixel crawl above, which additionally caught and fixed a real defect neither prior phase's tests
could have - the last in-scope category of risk this migration's own precedent (phase 4M2's caught
crash) said to specifically watch for.

### Final #10 acceptance audit

Every criterion from §O re-checked against the tree as it stands after this phase's fixes - all
unchanged from §O's own table except the two rows §O itself marked provisional:

| Criterion | Status | Evidence |
|---|---|---|
| Playlist naming transient Compose | ✅ | phase 4M3, live-validated this phase |
| Save Playlist transient Compose | ✅ | phase 4M3, live-validated this phase |
| Rename Playlist transient Compose | ✅ | phase 4M4 |
| No remaining reachable legacy core-browsing/playback surface inside #10 scope | ✅ | §B.3 is empty; all three playlist transients live-validated |
| Every other §O criterion | ✅ | unchanged, re-confirmed by the §15 representative crawl this phase |

**Known debt** is unchanged from §O's own table (#21-#24, the online-download-indicator gap, Up
Next's unreproduced shuffle-reorder question, the deferred `TrackCollectionFragment`/binder
cleanup, Videos) - all still open, all still confirmed non-blocking for #10 specifically, because
#10's acceptance criteria are about reachable UI migration, not this project's full backlog.

### `READY_TO_CLOSE_#10 = YES`

Every implementation, test, gate, and live-validation requirement in #10's own acceptance text and
in this task's explicit closure checklist is met: all reachable browsing and playlist-management UI
is Compose, playback/runtime ownership is unchanged throughout, the mini-player remains coherent
(and is now verified compatible with every transient sheet that can open over it), and live Pixel 7
validation - including a real defect this exact validation step was designed to catch - passed
after the fix. Issue #10 is closed as completed.

---

## Q. Phase 5A1 — About + Box Sets residual migration (post-#10)

Baseline `589f24f0` (Migrate rename playlist dialog to Compose). This is **not** a reopening of
issue #10 — #10 closed at phase 4M4 (§P) with these two surfaces already correctly classified as
out of scope (Box Sets as intentionally hybrid, §B.2; About as intentionally legacy, §B.4). This
phase is the first step of a post-#10 residual UI migration toward removing every non-essential
non-Compose surface before beta.

### What migrated

**About** (`aboutFragment`): `AboutFragment` is now a thin Compose host (`ComposeView`, no XML) →
new `AboutScreen`/`AboutActions` (`ui/about/`). Content is unchanged from the legacy `help.xml`:
app name, version (`Util.getVersionName`), tagline, and the free-text blurb (`about.text`'s
embedded `<b>Taki</b>` tag stripped via `HtmlCompat.fromHtml`, matching the existing app-wide
convention in `ArtistDetailViewModel`/`AlbumDetailViewModel`/`TrackCollectionFragment` rather than
rendering styled text). One visible action, "Report a problem" (`about.report` →
`Intent.ACTION_VIEW` on `about.report.url`). The legacy "Visit website" button was found to be
**permanently `android:visibility="gone"` in production** (confirmed: no code path ever set it
visible) — preserved exactly as still-off (`SHOW_WEBSITE_ACTION = false` in `AboutScreen.kt`,
hide-don't-delete), with `AboutActions.onWebsite`/the Fragment's Intent-launch still wired so a
future flip is a one-line change, not a re-implementation. No credits, licenses, social links, or
update checker were added — none existed in the legacy screen. `NavigationActivity`'s
`showsContentBackButton` inline destination set was extracted into a pure, testable companion
function of the same name and `aboutFragment` removed from it (About now draws its own
`TakiScreenHeader` back arrow, so the shared `content_navigation_header` bar would otherwise
double it up); `hidesSupportActionBar` already included `aboutFragment` and needed no change.
Bottom-nav/mini-player visibility rules for `aboutFragment` in `updateChromeVisibility` were not
touched (bottom nav already hidden, mini-player already not hidden, unchanged from before).

**Box Sets** (`collectionListFragment`): fully Compose, ending the hybrid shell. New
`CollectionListViewModel` (`StateFlow<CollectionListUiState>`, replacing the legacy `LiveData`-based
`CollectionListModel` 1:1 in loading semantics — same `AlbumDao.withGrouping()` +
`CollectionResolver.resolve` read, same "don't reload if already loaded once" guard, now also with
`loadFailed` bookkeeping for parity with every other migrated list ViewModel, a gap the legacy
class had) + `CollectionListScreen`/`CollectionListUiState`/`CollectionListActions`
(`ui/collectionlist/`). The screen keeps its own `TakiScreenHeader` ("Box Sets" + back), a 2-column
`TakiEntryGrid`, `PullToRefreshBox`, and the `EmptyState`/`collection_empty` empty state — all
already established patterns from Genres/Playlists List. The grid card's diagonal 3-cover stack is
a direct geometric port of the legacy `view_stacked_artwork.xml`'s percent-bias `ConstraintLayout`
(back top-start, middle centre, front bottom-end, each 82% of the square cell,
`TakiTheme.shapes.sm` matching the legacy `radius_sm`) — a new private `CollectionStackedArtwork`
composable in `CollectionListScreen.kt`, **not** a reuse of `CollectionDetailScreen`'s
`CollectionIdentityMark` (that composable fans covers **horizontally** for the Detail header, a
different, already-established visual for a different context; this audit found
`StackedArtworkBinder`'s own kdoc claim that the two screens shared it was already stale before
this phase — Collection Detail stopped using it when it migrated in phase 4B). Layer visibility
(`back` only if >2 albums, `middle` only if >1) is preserved exactly. Navigation to Collection
Detail is unchanged: `CollectionListFragmentDirections.toCollectionDetail(row.title)`, `row.title`
being the exact `MusicCollection.title` `CollectionResolver` resolved, never a display-only copy —
locked by a new `CollectionListNavigationTest`. `CollectionListFragment` now threads
`NavigationActivity.contentBottomInset` into `bottomContentInset: Dp` exactly like
`CollectionDetailFragment`, replacing the legacy `bindFloatingChromeInset` RecyclerView-padding
call — `collectionListFragment` was never in `updateChromeVisibility`'s `hideForDestination` set,
so bottom nav/mini-player visibility rules are unchanged. No context menu, no long-press — the
legacy screen never had either.

### Legacy cleanup

Deleted (confirmed by grep to have zero remaining references before deletion): `help.xml`,
`collection_list_layout.xml`, `list_item_collection.xml`, `view_stacked_artwork.xml`,
`CollectionRowAdapter.kt`, `StackedArtworkBinder.kt`, `CollectionListModel.kt`. `R.id.stack_*`,
`R.layout.list_item_collection`, and `R.layout.view_stacked_artwork` are now fully unreferenced.
Kept: `list_layout_generic.xml`/`list_parts_empty_view.xml`/`list_parts_recycler.xml` (shared with
other list screens), `R.drawable.ic_empty`/`R.drawable.unknown_album` (shared broadly),
`R.plurals.n_discs` (still used by both List and Detail), `R.string.library_box_sets`/
`R.string.collection_empty` (still the screen's title/empty-state strings, now read from Compose
instead of XML), `about.webpage`/`about.webpage.url` (kept per hide-don't-delete, see above).

### Tests

Baseline 1155 → **1190** (+35), 0 failures:
- 10 `CollectionListViewModelTest`, 8 `CollectionListScreenComposeTest`, 2
  `CollectionListScreenScreenshotTest`, 3 `CollectionListNavigationTest`
- 7 `AboutScreenComposeTest`, 1 `AboutScreenScreenshotTest`
- 4 `NavigationChromeSelectionTest` (locking the `showsContentBackButton` extraction and the
  `aboutFragment` removal from it)

### Gates

- `compileDebugKotlin`/`compileDebugUnitTestKotlin`: green
- `testDebugUnitTest` (includes Roborazzi verify): green, 1190/1190 (4 new goldens recorded:
  `about_standard`, `collection_list_standard`, `collection_list_empty`, plus the existing
  `CollectionListScreenScreenshotTest` empty-state golden)
- `assembleDebug`/`assembleRelease`: green
- `lintDebug`: green, zero new findings (baseline `lint-baseline.xml` unchanged); `lintVitalRelease`:
  no errors or warnings
- `detekt -Pqc`: **42** in `:ultrasonic` — byte-identical finding set to the pre-phase baseline,
  zero new issues from any file this phase touched or added
- `ArchitectureGuardTest`/`TakiTokensTest`/`NavigationChromeSelectionTest`: green — no raw
  dp/sp/Color literals, no `MaterialTheme` import, and no Media3 import in either new `ui/about` or
  `ui/collectionlist` package

### Pixel 7 validation (device `2B191FDH200E36`)

Live crawl via `adb`/`uiautomator` against the freshly assembled debug build, against a real
Navidrome server with real Box Sets data (Bach 333 / 222 discs, Chanson française / 2 discs, The
Complete Mozart Edition / 2 discs).

**Box Sets:** Library → Box Sets shows exactly one header (0 Toolbar nodes, one "Box Sets" title,
one "Go back" affordance), the 2-column grid with correct diagonal stacked covers (3 layers for
Bach 333, 2 for the others, real artwork loaded), correct disc counts ("222 discs" / "2 discs"),
mini-player and bottom nav both visible throughout. Tapping "Bach 333" opened Collection Detail
with the correct grouping/title and disc count; a pull-down gesture completed without error or
data loss; the header's own back arrow returned to Library (not just system back, which was also
separately verified to return to the Box Sets list with all three collections still shown — no
reload/scroll-reset regression). No crashes, no ANRs, no clipping.

**About:** Library overflow (⋮) → About shows exactly one header (0 Toolbar nodes), correct live
version ("Version 0.1.0-beta"), the tagline and HTML-stripped blurb, and exactly one visible action
("Report a problem" — "Visit website" correctly absent). Tapping "Report a problem" launched an
external handler for the GitHub issues URL (resolved to the installed GitHub app rather than a
browser, standard Android link-verification behavior for a github.com URL — no issue was actually
filed). The header's own back arrow returned to Library. Bottom nav confirmed hidden (0
`bottom_navigation` nodes), mini-player confirmed still visible — both unchanged from the
pre-phase behavior. `adb logcat -b crash`/`*:E` filtered to the app's own package showed zero
Taki-attributable entries across the entire session.

### Coverage accounting

| | Before 5A1 | After 5A1 |
|---|---:|---:|
| Compose | 21 | 23 |
| Hybrid | 1 | 0 |
| Intentionally legacy | 5 | 4 |
| Total reachable | 27 | 27 |

Arithmetic: 21 + 1 + 5 = 27 → 23 + 0 + 4 = 27. Remaining intentionally-legacy surfaces: Settings,
Server Selector, Edit Server, Equalizer (§B.4, updated). Videos remains separately tracked as
unreachable/dead product cleanup (§F/§B.5), not part of the reachable-surface count either before
or after this phase.

### Known debt (unchanged, non-blocking)

Everything §P's own table listed (#21–#24, the online-download-indicator gap, the deferred
`TrackCollectionFragment`/binder cleanup, Videos removal) remains open and unaffected by this
phase. No new debt was found or introduced.

### Next phase (superseded by §R)

**Phase 5A2 — migrate Server Selector to Compose**, continuing the post-#10 residual migration in
the same low-risk-first order this phase established. Not started as part of this phase.

---

## R. Phase 5A2 — Server Selector residual migration (post-#10)

Baseline `a6e4f743` (Migrate About and Box Sets to Compose). Not a reopening of issue #10 — Server
Selector was already correctly classified as intentionally legacy at #10's closure (§B.4).

### Audit findings

Full source of `ServerSelectorFragment`/`ServerRowAdapter`/`ServerSettingsModel`/
`ActiveServerProvider`/`ServerSetting` read directly (not recalled). Key findings:

- **List**: `ServerSettingsModel.getServerList()` (`repository.loadAllServerSettings()`, no
  `ORDER BY`), loaded only from the Fragment's `onResume` (never `onViewCreated`) - preserved
  exactly, including the timing, so returning from Edit Server always shows fresh data.
- **Offline row**: never a DB row - `ActiveServerProvider.OFFLINE_DB` is synthesized as row 0 on
  every load, non-editable, non-deletable, its `url` ("http://localhost") never shown.
- **Active indication**: `setting.id == ActiveServerProvider.getActiveServerId()`, a plain 2dp
  stroke in the legacy View - re-created as an "Active" accent-colored label in Compose (no
  content description existed for it before; still none needed, since the label text itself is
  now in the accessibility tree).
- **A real, verified, pre-existing bug in the Edit contract**: the legacy `ServerRowAdapter` feeds
  the on-screen RecyclerView **position** into `EditServerFragment`'s `index` nav argument, which
  is looked up against the `ServerSetting.index` **DB column** - not `ServerSetting.id`, and not
  reliably the same value once a reorder has happened. **Preserved exactly, not fixed**: the new
  `ServerSelectorRow.position` field carries the identical on-screen-position value into the same
  `index` nav argument, since fixing `EditServerFragment`'s own contract is explicitly out of this
  phase's scope (Edit Server stays legacy).
- **A real, verified, pre-existing bug in the Delete sequence**: `deleteMetaDatabase` is called
  with the *pre-delete active server's id*, not the deleted server's own id. When deleting a
  non-active server, this clears the *surviving* active server's metadata cache for no reason and
  leaves the deleted server's own cache file orphaned. **Preserved exactly, not fixed** -
  documented in `ServerSelectorViewModel.confirmDelete`'s own kdoc and locked by a regression test
  (`CollectionListViewModelTest`-style: `deleteMetaDatabase receives the pre-delete active id`).
- **Reorder ("Move up"/"Move down") was found to be already non-functional**: the underlying query
  has no `ORDER BY`, so swapping the `index` column value never changes the on-screen order - its
  only real effect was silently desyncing the index-based value `EditServerFragment` reads. **Not
  carried forward** into the Compose row's menu (Edit/Delete only) - this is a product-direction
  simplification consistent with the task's own "simple libraries screen" framing, not a silent
  feature drop, and it also removes the only mechanism that could desync the Edit contract above.
  `ServerSettingsModel.moveItemUp`/`moveItemDown` are left in place, unused - `ServerSettingsModel`
  itself is explicitly retained per scope, and these two methods are harmless dead code, not
  removed in this pass.
- **Delete restrictions**: none exist in the legacy screen (no guard against deleting the last or
  the active server) - none were added.
- **A real, pre-existing accessibility mismatch found and fixed**: the legacy row's "⋮" button
  reused `server_editor.advanced` ("Advanced settings", the *Edit Server* screen's own section
  label) as its content description, despite opening an Edit/Delete/reorder menu, not advanced
  settings. Fixed with a new, correctly-scoped string (`server_selector.row_menu`, "More options
  for %1$s").

### Compose implementation

`ServerSelectorFragment` -> thin Compose host -> new `ServerSelectorScreen`/`ServerSelectorUiState`/
`ServerSelectorActions` (`ui/serverselector/`), backed by a new `ServerSelectorViewModel`
(`model/`) - a StateFlow port of the legacy Fragment's own logic, reusing `ServerSettingsModel`/
`ActiveServerProvider` as the sole data source (no second repository). Test seams
(`serverListLoader`/`activeServerIdReader`/`setActiveServer`/`deleteServerById`/
`deleteMetaDatabase`) let `ServerSelectorViewModelTest` exercise every path deterministically
without touching Koin or Room. The screen draws its own `TakiScreenHeader` ("Configured
libraries" - the exact existing, if previously invisible, title string - plus back), a
`LazyColumn` of server rows (color swatch, name, url, "Active" label, an overflow "⋮" ->
Edit/Delete), and an inline "Add library" row (no FAB - Compose migrated screens in this codebase
don't use one, and an inline row sidesteps the phase-4M4 floating-chrome-collision class of bug
entirely). Delete confirmation is a new `DeleteServerSheet` (`ui/serverselector/`), the same
scrim + sliding-panel pattern every other migrated transient overlay uses, replacing the legacy
`ErrorDialog`-based `AlertDialog` with the exact same title/message copy.
`ServerSelectorFragment.onResume` threads `NavigationActivity.contentBottomInset` into
`bottomContentInset` exactly like `CollectionListFragment`/`CollectionDetailFragment`, and calls
`viewModel.reload()` - preserving the legacy's own onResume-only refresh timing.

**A real bug found and fixed during testing (not a legacy behavior change)**: the row's color
swatch initially reused the legacy `ServerColor.getBackgroundColor`/`getForegroundColor` helpers,
which call `MaterialColors.getColor`/`harmonizeWithPrimary` - both resolve the XML theme attribute
`?attr/colorPrimary`, which only exists on a real themed Activity. This crashed every Compose test
that rendered a row (`IllegalArgumentException`), since a plain Compose test host has no such
theme. Fixed by reimplementing the same swatch logic natively in Compose
(`TakiTheme.colors.accent` as the neutral fallback - textually the exact color `ServerColor`'s own
fallback comment already named as its intent - plus a pure luminance calculation for the glyph
tint), removing the dependency on `ServerColor`/Activity theming entirely from the new code.
`ServerColor.kt` itself is untouched and still used by the still-legacy `EditServerFragment`.

### Legacy cleanup

Deleted (confirmed by grep to have zero remaining references before deletion): `ServerRowAdapter
.kt`, `server_selector.xml`, `server_row.xml`, `circle.xml` (the round swatch background drawable,
referenced only by the two deleted files). Removed the now-genuinely-dead `server_menu.move_up`/
`server_menu.move_down` strings from all 15 locale files that had them (their only caller was the
deleted adapter's reorder menu). Retained (confirmed still needed): `ServerSettingsModel`,
`ActiveServerProvider`, `ServerColor.kt`, `ic_menu_server`/`ic_menu_screen_on_off` (now used from
Compose instead), `server_editor.new_label`/`server_editor.advanced` (still used by the
still-legacy Edit Server screen), `ErrorDialog`/`Dialogs.kt` (used by Settings, `EditServerFragment`,
and `CommunicationError` - not migrated globally just because this screen stopped using it).

### Tests

Baseline 1190 -> **1232** (+42), 0 failures: 16 `ServerSelectorViewModelTest`, 17
`ServerSelectorScreenComposeTest`, 2 `ServerSelectorScreenScreenshotTest`, 4
`ServerSelectorNavigationTest`, 3 `NavigationChromeSelectionTest` (locking the
`showsContentBackButton` update and the `aboutFragment`/`serverSelectorFragment` split).

### Gates

- `compileDebugKotlin`/`compileDebugUnitTestKotlin`: green
- `testDebugUnitTest` (includes Roborazzi verify): green, 1232/1232 (2 new goldens:
  `server_selector_standard`, `server_selector_delete_confirmation`)
- `assembleDebug`/`assembleRelease`: green
- `lintDebug`: one real new finding caught and fixed (`EmptySuperCall` - a redundant
  `super.onCleared()` call in `ServerSelectorViewModel`, since `AndroidViewModel.onCleared()` is
  `@EmptySuper`); green after the fix, zero new findings remaining, baseline `lint-baseline.xml`
  unchanged; `lintVitalRelease`: no errors or warnings
- `detekt -Pqc`: **42** in `:ultrasonic` - byte-identical to the pre-phase baseline, zero new
  issues from any file this phase touched or added
- `ArchitectureGuardTest`/`TakiTokensTest`/`NavigationChromeSelectionTest`: green - no raw
  dp/sp/Color literals, no `MaterialTheme` import, no Media3 import, and (per the swatch fix
  above) no XML-theme-attribute dependency in the new `ui/serverselector` package

### Pixel 7 validation

Completed in a follow-up pass once the device (`2B191FDH200E36`) reconnected (it had merely
disconnected mid-session, as noted above - not a lasting problem). Installed the already-built
debug APK and validated live via `adb`/`uiautomator` against the real configured server, recording
the active server first (`100.80.152.121`, confirmed via the Library Hub's disabled "current
collection" menu item) so it could be confirmed unchanged afterward.

**Main selector:** Library -> Switch collection shows exactly one header (0 Toolbar nodes),
"Configured libraries" title, both rows (Offline, the one real server with its name/URL/"Active"
label and accent-colored swatch), mini-player visible, bottom nav correctly absent, no clipping.
Only one real server is configured, so the server-switch branch was not exercised live (per the
task's own instruction not to manufacture a second server merely to test it) - it is covered by
`ServerSelectorViewModelTest`'s `selecting a server calls setActiveServer with that server's id`.

**Add:** tapped "Add library" -> opened the legacy Edit Server screen in new-server mode (empty
URL/username/password fields, "Connect" button) - confirmed correct. Backed out via system back,
which surfaced the legacy screen's own pre-existing "leave and lose your changes?" confirmation
(unrelated to this migration); confirmed "OK", landed back on Server Selector with its list intact.

**Edit:** opened the real server's "⋮" overflow (content description correctly read "More options
for 100.80.152.121", confirming the accessibility-label fix) -> Edit -> confirmed the legacy Edit
Server screen opened pre-filled with that exact server's own real name/URL/username/password, not
a different or blank entry - the position-based nav-argument contract (documented above as a
preserved, not fixed, legacy fragility) resolved correctly for this single-server case. Backed out
without changing or saving anything.

**Delete:** opened the overflow -> Delete -> confirmed the new `DeleteServerSheet` renders with the
exact legacy copy ("Delete" / "Are you sure you want to remove this library?") and the exact
correct target server name ("100.80.152.121"), scrim and sliding-panel matching every other
migrated transient sheet. Tapped **Cancel** (the real production server was not deleted, per the
task's explicit instruction); confirmed the server remained in the list afterward, still marked
Active. Full confirm-and-delete behavior was not live-exercised (no disposable second server
existed) - it is covered by `ServerSelectorViewModelTest`'s delete-sequence tests, including the
regression test locking the pre-delete-active-id quirk.

**Stability:** zero crashes, zero ANRs; `adb logcat -b crash`/`*:E` filtered to the app's own
package showed no Taki-attributable entries across the entire session (the one `E`-level hit was
the same benign `WindowOrganizerController` task-reparenting warning seen during phase 5A1's own
validation, not an app error). The active server was never actually changed during this
validation, so no restoration step was needed.

### Coverage accounting

| | Before 5A2 | After 5A2 |
|---|---:|---:|
| Compose | 23 | 24 |
| Hybrid | 0 | 0 |
| Intentionally legacy | 4 | 3 |
| Total reachable | 27 | 27 |

Arithmetic: 23 + 0 + 4 = 27 -> 24 + 0 + 3 = 27. Remaining intentionally-legacy surfaces: Settings,
Edit Server, Equalizer (§B.4, updated).

### Known debt

No new debt beyond what was already documented and preserved-not-fixed above (the Edit
`index`-vs-`id` contract and the `deleteMetaDatabase` pre-delete-id quirk, both pre-existing and
now explicitly recorded rather than silently carried forward unnoticed). Live Pixel 7 validation
is complete (see above) - no outstanding items.

### Next phase

**Phase 5A3 — migrate Edit Server to Compose.** This would also be the natural point to revisit
the Edit `index`-vs-`id` contract this phase's audit flagged, since fixing it properly requires
changing `EditServerFragment`'s own nav-argument semantics. Not started as part of this phase.

---

## S. Phase 5A3 — Edit Server residual migration (post-#10)

Baseline `5b632a26` (Record phase 5A2 Pixel 7 live validation results). Not a reopening of issue
#10 — Edit Server was already correctly classified as intentionally legacy at #10's closure (§B.4).
The highest-risk surface of the post-#10 residual migration: first-connection onboarding and the
only way to edit the real, already-configured production server.

### Audit findings

Full source of `EditServerFragment`/`EditServerModel`/`ServerSettingsModel`/`ServerSetting`/
`ActiveServerProvider`/`MusicServiceFactory`/`RxBus`, the nav graph, `ServerSelectorFragment`'s
caller, `server_edit.xml`, and the third-party `ColorPickerDialog` read directly. Key findings:

- **A real, pre-existing contract fragility, confirmed and fixed (authorized in advance)**: the
  legacy `editServerFragment` destination's `index` nav argument was looked up against
  `ServerSetting.index`, a screen-position-derived DB column with no stable meaning once rows are
  added/removed/reordered — the exact fragility §R's audit flagged and explicitly deferred.
  Grepped every caller of this destination: **exactly 3 call sites** — two in
  `NavigationActivity.kt` (first-run auto-nav, Library Hub's "Add" menu item), both always passing
  `-1` (new-server mode), and `ServerSelectorFragment`'s own edit action, the **only** call site
  that ever passes a real value, and that value was the on-screen row **position**, not an id.
  Fixed at the root: the nav argument is renamed `index` → `serverId`, `ServerSettingDao`'s query
  changed from `WHERE [index] = :index` to `WHERE [id] = :id`
  (`getLiveServerSettingByIndex`→`getLiveServerSettingById`), `ServerSettingsModel` updated to
  match, and `ServerSelectorFragment.editServer` now passes `row.id` instead of `row.position`
  (`ServerSelectorRow.position` removed from the data class entirely, since nothing reads it
  anymore). Proven, not just asserted: `ServerSelectorNavigationTest` has a regression test opening
  the *second* displayed row and asserting the destination receives *that row's own id*,
  independent of its on-screen position, plus a test confirming the old `index` argument no longer
  exists on the destination at all.
- **Product direction confirmed via the legacy screen itself**: `server_edit.xml`'s own
  `View.GONE`/`setVisibility` logic already hides name/color/advanced/self-signed/plaintext/jukebox
  for new-server mode — the task's "simple onboarding" direction for New mode is not a new product
  decision, it is what the legacy screen already does, now made structural (two distinct
  composables) instead of runtime visibility toggles on one shared layout.
- **Validation/URL-normalization rules ported rule-for-rule**: `getFields()`'s exact sequence
  (address required → must parse as a URL with a non-blank host → name auto-fills from the URL host
  when hidden or blank → username required, password unconstrained) and `correctServerAddress()`'s
  exact `trim(' ', '/')` normalization, called on address-field focus loss.
- **`minimumApiVersion` reset preserved exactly**: loading an existing server whose
  `minimumApiVersion` is already cached clears it and re-persists, so the next connection re-probes
  the server's real API level — unchanged from legacy, now living in
  `EditServerViewModel.onExistingServerLoaded`.
- **Connect/Save sequencing preserved exactly**, including the active-server-reset-then-RxBus-
  publish ordering: Save → `updateServer` → if the edited server is the active one,
  `resetMusicService()` then `RxBus.activeServerChangedPublisher` → navigate up. Connect (new mode)
  → validate → test → `saveNewServer` → `setActiveServerById` → navigate home.
- **The phase 4M2 "scope-crossing rethrow" bug class, explicitly avoided**: navigation-completion
  signaling was first attempted with the standard `Channel`+`receiveAsFlow()` pattern, but proved
  untestable in this project's Robolectric+coroutines-test harness (events silently vanished in 4
  separate test cases regardless of dispatcher choice). Replaced with a direct synchronous callback
  field (`EditServerViewModel.onNavigate: (EditServerNavigationEvent) -> Unit`), assigned by the
  Fragment in `onViewCreated` and explicitly cleared (`= {}`) in a new `onDestroyView` override —
  deterministic regardless of which coroutine scope calls it, and a late callback after the view is
  destroyed is a safe no-op instead of a crash on a stale `findNavController()`.
- **`deleteMetaDatabase`'s pre-delete-active-id quirk (§R) is unrelated to this screen and was not
  touched**, per the task's explicit scope boundary — it lives entirely in
  `ServerSelectorViewModel.confirmDelete`.

### Compose implementation

`EditServerFragment` → thin Compose host (the same shape as `ServerSelectorFragment`) → new
`EditServerScreen`/`EditServerUiState`/`EditServerActions` (`ui/serverselector/`), backed by a new
`EditServerViewModel` (`model/`) — a `StateFlow` port of the legacy Fragment's own form/validation/
connection/save logic, reusing `ServerSettingsModel`/`ActiveServerProvider`/`EditServerModel` as the
sole data sources (no duplicate persistence logic). Test seams
(`serverSettingLoader`/`connectionTester`/`saveNewServer`/`updateServer`/`setActiveServer`/
`resetMusicService`/`publishActiveServerChanged`) let `EditServerViewModelTest` exercise every path
deterministically without touching Koin or Room. New mode renders only address/username/password
and a "Connect" button (`OnboardingFields`); Existing mode renders the full editor — a Library
section (name, address), a Sign-in section (username, password), an Appearance row (color swatch,
opens the retained third-party `ColorPickerDialog`), a collapsible Advanced section (self-signed,
plain-password, jukebox-by-default toggles, auto-expanded if any is already on — matching the
legacy `selfSignedSwitch`/`jukeboxSwitch`-only check), and Test Connection/Save actions. Back (both
the header's own back action and the system Back gesture) now routes through the same
`requestBack()`/`hasUnsavedChanges()` dirty-check, fixing a documented legacy gap where only system
Back triggered the leave-confirmation. The discard confirmation is a new `DiscardServerChangesSheet`
(`ui/serverselector/`), the same scrim + sliding-panel pattern every other migrated transient
overlay in this codebase uses, replacing the legacy `AlertDialog` with the exact same copy.

**A real bug found and fixed during the required live Pixel 7 validation, not before**: Compose's
`onFocusChanged` reports a field's *initial* (unfocused) state once on composition — this is not a
real focus-loss transition, but the address field's `onFocusChanged` callback didn't distinguish the
two, so `onAddressFocusLost()` (which trims `' '`/`'/'` from both ends) fired the instant the New
Server screen opened, before the user touched anything, turning the virgin `"http://"` seed into
`"http:"` and simultaneously marking the untouched form as dirty — pressing Back on a screen nobody
had edited incorrectly surfaced the discard-changes confirmation. Not caught by
`EditServerScreenScreenshotTest`'s or any Compose test's prior goldens/assertions because
Robolectric's Compose test harness does not reproduce this initial-callback behavior the same way
a real device does (verified: the existing golden already rendered `"http://"` correctly, and only
disappeared on the real device). Fixed in `EditServerScreen.kt`'s `AddressField` with a
`wasFocused` guard that only forwards a *true* focused→unfocused transition. A new regression test,
`EditServerScreenComposeTest`'s "opening the screen does not fire onAddressFocusLost", was verified
to fail without the guard and pass with it before being kept.

### Legacy cleanup

Deleted (confirmed by grep to have zero remaining references before deletion): `server_edit.xml`,
`rounded_swatch_fill.xml`, `rounded_border.xml` (both drawables referenced only by the deleted
layout), and — a direct, incidental consequence of deleting `server_edit.xml` (the only remaining
user of these four resources) — `ic_lyrics_synced.xml`, `ic_lyrics_unsynced.xml`, and the
`Ultrasonic.AllCapsLabel`/`Ultrasonic.AllCapsLabel.Inset` styles, all caught by `lintDebug`'s
`UnusedResources` check after the rest of the phase was otherwise gate-clean, re-confirmed orphaned
by grep before removal. Retained (confirmed still needed): `ServerSettingsModel`,
`ActiveServerProvider`, `EditServerModel`, `ServerColor.kt` (the color-picker's initial-color
helper), the third-party `ColorPickerDialog`/`BubbleFlag` dependency (kept by explicit task
direction, option A — not worth a subproject to replace).

### Tests

Baseline 1232 → **1291** (+59), 0 failures: 29 `EditServerViewModelTest` (new/existing mode,
validation, URL normalization, `minimumApiVersion` reset, Connect/Save sequencing, discard/dirty-
check), 24 `EditServerScreenComposeTest` (including the `onAddressFocusLost` regression test above),
3 `EditServerScreenScreenshotTest` (New mode, Existing mode, Advanced expanded), plus updates to
`ServerSelectorNavigationTest` (the second-row-opens-its-own-id regression test and the obsolete-
`index`-argument regression test) and `NavigationChromeSelectionTest` (asserting `editServerFragment`
now hides chrome exactly like every other Compose-migrated destination).

### Gates

- `compileDebugKotlin`/`compileDebugUnitTestKotlin`: green
- `testDebugUnitTest` (includes Roborazzi verify): green, 1291/1291, all 3 new goldens recorded and
  visually verified
- `assembleDebug`/`assembleRelease`: green
- `lintDebug`: 4 real new `UnusedResources` findings caught (the two lyrics-icon drawables and the
  two `AllCapsLabel` styles, orphaned by this phase's own `server_edit.xml` deletion — see Legacy
  cleanup above); green after removing all four, zero new findings remaining,
  `lint-baseline.xml` unchanged; `lintVitalRelease`: no errors or warnings
- `detekt -Pqc`: **42** in `:ultrasonic` — byte-identical to the pre-phase baseline, zero new issues
  from any file this phase touched or added
- `ArchitectureGuardTest`/`TakiTokensTest`/`NavigationChromeSelectionTest`: green — no raw dp/sp/
  Color literals outside an explicit `// taki-raw-ok` escape hatch, no `MaterialTheme` import, no
  Media3 import in the new `ui/serverselector` additions

### Pixel 7 validation

Completed live against device `2B191FDH200E36` with the real configured production server
(`100.80.152.121`, `http://100.80.152.121:4533`, username `Joseph`), recorded first so it could be
confirmed unchanged afterward.

**New mode:** Library → "Switch collection" → "Add library" showed exactly the onboarding form
(Library Address pre-filled `http://`, Username, Password, "Connect") with no name/color/advanced
fields — confirmed correct. **This is where the `onAddressFocusLost` bug above was actually found**:
pressing Back on this completely untouched form surfaced the discard-changes confirmation, which it
should not have. Fixed in-session (see Compose implementation above), APK rebuilt and reinstalled,
and re-validated: Back on an untouched New mode form now returns directly to Server Selector with no
confirmation, and the address field correctly displays `http://` (not `http:`) on open.

**Existing mode:** opened the real server's "⋮" → Edit → confirmed every field showed the exact real
data (name `100.80.152.121`, address `http://100.80.152.121:4533`, username `Joseph`, password
masked, Library color swatch, Advanced collapsed) — not a different or blank entry, proving the
`serverId`-based navigation fix resolved correctly against the live database.

**Safe save round-trip:** appended a harmless suffix to the display name only (address/username/
password untouched), Save → confirmed Server Selector showed the new name as the Active server →
reopened Edit, cleared the name field, retyped the exact original value `100.80.152.121`, Save again
→ confirmed Server Selector shows the original name restored exactly, still Active.

**Connection Test:** ran against the real server from Existing mode → "Connection successful" and
"This server doesn't support jukebox mode" (live-probed feature detection) — both correct for this
server's real capabilities.

**Back/discard flow:** modified the username field (`Joseph`→`JosephX`, harmless, never saved) →
Back → discard-changes confirmation appeared correctly → Cancel → confirmed the editor stayed open
with the modification still present (unsaved) → Back again → OK (discard) → confirmed Server
Selector → reopened Edit → confirmed the username reverted to exactly `Joseph`, proving the discard
path never persisted the change.

**Stability:** the app process (`pidof`) stayed the same single PID across the entire validation
session — no crash-triggered restart; `adb logcat -d *:E` filtered for
`org.moire.ultrasonic|io.github.churipakinti|AndroidRuntime|FATAL|ANR` across the whole session
returned nothing.

**Confirmed afterward**: the production server's name, address, username, and password are
unchanged from before this validation session began.

### Coverage accounting

| | Before 5A3 | After 5A3 |
|---|---:|---:|
| Compose | 24 | 25 |
| Hybrid | 0 | 0 |
| Intentionally legacy | 3 | 2 |
| Total reachable | 27 | 27 |

Arithmetic: 24 + 0 + 3 = 27 → 25 + 0 + 2 = 27. Remaining intentionally-legacy surfaces: Settings,
Equalizer (§B.4, updated).

### Known debt

The `index`-vs-`id` navigation contract fragility §R flagged is now **resolved**, not merely
preserved — proven by regression tests, not just asserted. The `deleteMetaDatabase` pre-delete-
active-id quirk (§R) is explicitly **unchanged and still open** — out of this phase's scope by the
task's own instruction. `ServerSettingsModel.moveItemUp`/`moveItemDown` remain dead code (§R,
unaffected by this phase — `ServerSettingsModel` itself is retained per scope). The Videos-mode
product decision (§B.5) remains open and unrelated to this surface. No new debt was introduced by
this phase; the one real bug found during live validation (the `onFocusChanged` initial-state issue
above) was fixed and regression-tested within this same session, not deferred.

### Next phase

**Phase 5A4 — migrate Settings to Compose.** Not started as part of this phase.

---

## T. Phase 5A4 — Settings residual migration (post-#10)

Baseline `55c49f75` (Migrate Edit Server to Compose). Not a reopening of issue #10 — Settings was
already correctly classified as intentionally legacy at #10's closure (§B.4).

### Audit findings

Full source of `SettingsFragment`, `R.xml.settings`, `Settings.kt`, `setting_keys.xml`, `arrays.xml`,
`SelectCacheActivityContract`, `FileLoggerTree`, `NavigationActivity`'s chrome logic, and the nav
graph read directly. Key findings:

- **Full tree inventory**: top level (5 rows: Playback nav, Downloads nav, Override-language choice,
  Advanced nav, About nav) → 3 levels of nesting deep at the extreme (`advanced_group` →
  `music_cache_group`/`library_group`/`debug_group`). 6 nested `PreferenceScreen` groups, ~20 leaf
  rows total: 2 navigation-to-Equalizer/About rows, 6 `ListPreference` choices, 9 `SwitchPreferenceCompat`
  toggles, 4 non-persisted action rows (`clear_downloads`/`clear_search_history`/`clear_image_cache`/
  `cache_location`), 2 `PreferenceCategory` visual-only labels. **Zero `EditTextPreference` rows
  exist** in `settings.xml` — the task's suggested "Value" item type has no live representative; the
  legacy Fragment's own `is EditTextPreference` branch in `updatePreferenceSummaries` was already
  dead code, confirmed by full-file read, not ported.
- **Exactly one external entry point and one internal self-navigation**: `R.id.library_hub_settings`
  (always top-level, no args) and the self-referencing `settingsToGroup` action (only ever called
  from `onPreferenceTreeClick`). No hidden deep-link callers.
- **`groupTitle` nav argument dropped as a legitimate simplification**: the legacy nav graph threaded
  both `rootKey` and `groupTitle` (the clicked row's own title text, captured at click time) through
  navigation. Since [SettingsDefinitions] now derives a screen's title purely from `rootKey` (a pure,
  static lookup, not a runtime-captured string), `groupTitle` has nothing left to carry — removed from
  the `settingsFragment` destination entirely, a real but narrow contract simplification, not a
  behavior change (the displayed title text is identical).
- **A real, verified, pre-existing dead setting**: `showConfirmationDialog`'s own summary string
  claims it "Displays a confirmation dialog before deleting downloaded songs" - but grepped and
  confirmed **zero reads** of `Settings.showConfirmationDialog` anywhere in the app. The actual
  "Clear All Downloads" confirmation is unconditional, hard-coded, gated by nothing. **Preserved
  exactly, not fixed** - the toggle still persists its value; classified `DEFER` /
  `PRODUCT_DECISION_REQUIRED`, not silently removed or silently wired up, per the task's explicit
  instruction not to make product decisions unilaterally.
- **The ID3 dependency**: `useId3TagsOffline`'s enabled-state is driven by `id3_tags` (online)'s
  current value, re-derived on every preference change - ported declaratively into
  `SettingsRowState.ToggleRow.enabled`, Pixel-verified live (toggling online off/on correctly
  disables/re-enables the offline row while preserving its own stored value).
- **Cache location**: `cacheLocationUri` and the `cache_location` row's own persisted key are the
  *same* string ("cacheLocation") by design in the legacy screen - preserved exactly; the row is
  visible only while `customCacheLocation` is on, matching `setupCacheLocationPreference()`'s
  `isVisible` guard exactly.
- **Debug logging**: toggling on plants the `FileLoggerTree` immediately; toggling off uproots it
  *immediately* (logging stops right away) and *separately* asks whether to delete the
  already-stopped log files - these are two independent effects, not one gated behind the other,
  ported exactly as two separate steps in `onDebugLogToggled`.
- **`FileLoggerTree.plantToTimberForest()` is also called unconditionally at app startup**
  (`UApp.onCreate`) when the preference is already on - confirmed the Settings screen itself never
  re-plants on open, only on an explicit user toggle, matching the task's "do not activate logging
  simply by opening Settings" requirement without needing any new guard.

### Compose architecture

`SettingsFragment` → thin Compose host (same shape as `ServerSelectorFragment`/`EditServerFragment`)
→ `SettingsScreen`/`SettingsUiState`/`SettingsActions` (`ui/settings/`), backed by a new
`SettingsViewModel` (`model/`) - a pure projection over the real `Settings`/`SharedPreferences`
storage, never a second persistence layer. A small, closed item model
(`SettingsItem`: `Toggle`/`Choice`/`Navigation`/`Action`/`Category`) replaces the inflated XML
`PreferenceScreen` tree; a new `SettingsDefinitions` object is a 1:1 static transcription of
`R.xml.settings`, resolved by `rootKey` exactly like
`PreferenceFragmentCompat.setPreferencesFromResource(R.xml.settings, rootKey)`'s own null-means-root
convention - one `SettingsViewModel` instance per screen (top level or nested group), matching the
legacy self-navigating destination's one-Fragment-instance-per-level behavior. The
`OnSharedPreferenceChangeListener` is registered in `init`/unregistered in `onCleared` (not the
legacy Fragment's `onResume`/`onPause`) - this ViewModel's own lifecycle is already the correct scope
and stays live reactively across external preference changes (another screen, a picker result,
side-effect code), confirmed by a dedicated test and live on-device (the ID3 dependency toggle test).
Three new transient sheets replace every legacy dialog this screen owned: `SettingsChoiceSheet` (a
scrollable radio list, not a `TakiSortMenu`-style anchored dropdown - cache size has 18 entries,
language has 15, too long for a small menu), and two generic reusable sheets, `SettingsConfirmSheet`
(two-button, stands in for the clear-downloads/clear-image-cache/debug-log-delete confirmations) and
`SettingsInfoSheet` (one-button, stands in for the debug-log-deleted and cache-location-error
messages) - `ConfirmAction`/the overlay's own resource ids select the copy and the gated
`SettingsViewModel.onConfirm` branch. `SettingsFragment` keeps exactly the one piece of platform
plumbing that must stay Fragment-side: the `SelectCacheActivityContract` `ActivityResultLauncher`
(unchanged); the ViewModel emits a `SettingsEffect.LaunchCacheLocationPicker` one-shot callback
(`onEffect`, assigned in `onViewCreated`/cleared in `onDestroyView`) rather than touching
`ActivityResultLauncher` itself - the same precedent `EditServerViewModel.onNavigate` established in
phase 5A3.

### Behavior parity

Every toggle/choice/action's exact persisted key, default value, and side effect preserved:
validation-free (Settings has no field validation), URL-free. Choice rows now display the
human-readable current-value label (resolved by zipping `entries`/`values` and matching the stored
raw string) instead of the legacy's own dynamic-summary-overwrite mechanism - a deliberate, requested
UI improvement (Taki rows show "current value," not "static description + raw key"), not a behavior
regression; a stale/invalid stored value (proven by a dedicated test writing a bogus string directly)
is carried through without crashing, simply showing no resolved label. Destructive actions
(clear-downloads, clear-image-cache) keep their exact confirmation copy and gated side effects; the
debug-log keep/delete prompt keeps its exact file-count/size message and both outcomes. Cache
location keeps the exact system-picker contract, the exact cancel/failure fallback (force
`customCacheLocation` off only if the URI is still unset), and the exact visibility rule. The ID3
online/offline dependency is preserved exactly as a reactive enabled-state, not a one-time check.

### Dead/stale settings

- `showConfirmationDialog` - **DEFER / PRODUCT_DECISION_REQUIRED**: toggle persists, is displayed,
  but gates nothing; its own summary string overpromises. Not silently removed or silently wired up.
- The `EditTextPreference`-handling branch in the legacy `updatePreferenceSummaries` - **REMOVE_NOW**
  (already not ported): zero `EditTextPreference` rows exist in `settings.xml`, confirmed by a full
  read of the file: this was dead code in the legacy Fragment itself, not a setting.
- Every other key: **KEEP**, live and read somewhere in the app (`Settings.kt`/`ActiveServerProvider`/
  `PlaybackService`/`NavigationActivity`/`Util`), confirmed by grep per-key during the audit.

### Legacy cleanup

Deleted (confirmed by grep to have zero remaining references before deletion): `R.xml.settings`,
`settings_fragment.xml`. The live Settings path no longer imports or uses
`PreferenceFragmentCompat`/`SwitchPreferenceCompat`/`ListPreference`/`EditTextPreference`/
`PreferenceScreen` - confirmed by grep, only kdoc-comment mentions of these class names remain, zero
real code references. The `androidx.preference` Gradle dependency is **retained** - `Settings.kt` and
`SettingsDelegate.kt` both still call `PreferenceManager.getDefaultSharedPreferences`, confirmed by
grep before keeping it. `R.array.*`/`setting_keys.xml` resources all retained (still referenced by
the new Compose code). `ConfirmationDialog`/`ErrorDialog`/`InfoDialog`(`Dialogs.kt`) retained -
`PlaylistListFragment`/`TrackCollectionFragment`/`CoroutinePatterns.kt` still use them, confirmed by
grep before keeping.

### Tests

Baseline 1291 → **1341** (+50), 0 failures: 23 `SettingsViewModelTest` (screen resolution per
`rootKey`, a dedicated test locking every hardcoded persisted-key literal against its real
`R.string.setting_key_*` resource value, toggle writes, the ID3 enabled-state dependency, external
preference-change reactivity, choice writes + stale-value handling, cache-location visibility/toggle/
picker-result/cancel, clear-search/clear-image-cache/clear-downloads action seams, debug-log
plant/uproot/confirm/delete/keep), 21 `SettingsScreenComposeTest` (top level, one representative
nested group per interaction type, toggle single-fire with no double-toggle, disabled-toggle
no-op, choice sheet open/select/dismiss, confirm sheet, info sheet, hidden/visible action rows,
category rows, navigation dispatch by target, back), 3 `SettingsScreenScreenshotTest` (top level,
Advanced - the one screen exercising every row type, the choice sheet), plus 3 new
`NavigationChromeSelectionTest` cases (Settings toolbar-hidden unchanged, Settings no longer shows
the shared back bar, Equalizer explicitly confirmed unaffected).

A real cross-test state-leak bug was found and fixed *in the test suite itself* during this phase:
`Settings`'s delegate-backed properties (`cacheLocationUri`, `isWifiRequiredForDownload`, ...) each
lazily cache their own `SharedPreferences` reference once and never re-resolve it, while
`Settings.preferences` is a fresh computed property on every access - clearing state only via
`Settings.preferences.edit().clear()` in `@Before` left the delegate-cached properties unreset,
causing values written in one test to leak into later tests. Fixed by also resetting every
delegate-backed property this suite touches directly in `@Before`. A second, real production-code bug
was also found and fixed: `clearAllDownloads()` wrapped its track-loading call in
`withContext(Dispatchers.IO)` directly rather than inside the `offlineTracksLoader` seam, so a test
override never actually dispatched onto a real thread `advanceUntilIdle()` could see, making that one
test flaky - fixed by moving the dispatcher hop into the seam's own default implementation, matching
`EditServerViewModel.connectionTester`'s established precedent.

### Gates

- `compileDebugKotlin`/`compileDebugUnitTestKotlin`: green
- `testDebugUnitTest` (includes Roborazzi verify): green, 1341/1341, all 3 new goldens recorded and
  visually verified
- `assembleDebug`/`assembleRelease`: green
- `lintDebug`: two real new findings caught and fixed during development - `EmptySuperCall`
  (`SettingsViewModel.onCleared()`'s redundant `super.onCleared()` call, the same finding phase 5A2
  hit) and `UseKtx` (three raw `SharedPreferences.edit().put...().apply()` calls, converted to the
  KTX `edit { }` form); green after both fixes, zero new findings remaining, `lint-baseline.xml`
  unchanged; `lintVitalRelease`: no errors or warnings
- `detekt -Pqc`: **42** in `:ultrasonic` - byte-identical to the pre-phase baseline (one `LongMethod`
  finding was introduced and then eliminated by extracting `ConfirmSheetActions` out of
  `SettingsConfirmSheet`, not suppressed)
- `ArchitectureGuardTest`/`TakiTokensTest`/`NavigationChromeSelectionTest`: green - one raw `.dp`
  literal caught and fixed with an explicit `// taki-raw-ok` escape hatch
  (`SettingsChoiceSheet`'s sheet-height cap), no `MaterialTheme` import, no Media3 import in the new
  `ui/settings` package
- Grep assertions (required by the task): zero live `PreferenceFragmentCompat`/
  `SwitchPreferenceCompat`/`ListPreference`/`EditTextPreference`/`PreferenceScreen` usage in
  `ultrasonic/src/main/kotlin` (only kdoc-comment mentions remain); `R.xml.settings`/
  `settings_fragment.xml` confirmed deleted from disk

### Pixel 7 validation

Completed live against device `2B191FDH200E36`. The device was in active personal use when first
checked (Instagram/ChatGPT in the foreground) - confirmed with the user before proceeding rather than
forcing it.

**Top level:** single Taki header ("Settings", 0 Toolbar nodes), all 5 rows, no clipping, correct
mini-player-absent layout (Settings hides bottom chrome, matching legacy).

**Nested groups:** opened all 6 - Playback, Downloads, Advanced, Storage & cache, Library
compatibility, Diagnostics - each showing its own title, the exact expected rows (choices showing
live-resolved current-value summaries, e.g. "Maximum · 320 Kbps", "Original · No limit"), and correct
back behavior (back from a sub-group returns to its *parent* group, not the top level, proving the
back stack nests correctly through all 3 levels) with no duplicate header at any level.

**Safe toggle:** "Download on Wi-Fi only" - recorded off, tapped once (fired exactly once, no
double-toggle from the whole-row + switch both being hit targets), confirmed on, tapped again,
confirmed restored to off.

**Choice:** ReplayGain Mode - opened the sheet (all 6 options shown), selected "Track Only", sheet
closed and the row's summary updated live, reopened and restored to "Disabled" (the original value).

**Cache location:** confirmed the `Cache Location` row is hidden by default (`customCacheLocation`
off); toggling it on launched the real Android system folder picker; backed out without selecting a
folder (system Back) - the app correctly showed "Invalid cache location. Using default." and silently
reset `customCacheLocation` back off, confirmed via the row's visibility and the switch state
afterward. The real on-disk cache location was never touched. `Clear Image Cache` showed a real,
correctly-formatted dynamic summary ("Delete 17.17 MB of cached images") against the device's actual
image cache.

**ID3 dependency, live:** toggled "Browse Using ID3 Tags" off - "Use ID3 method also when offline"
immediately became visually disabled while keeping its own checked state; toggled back on - instantly
re-enabled. Both restored to their original (on) values.

**Debug logging, live:** toggled on - the row immediately showed the real log file path
(`/storage/emulated/0/Android/data/io.github.churipakinti.taki.debug/files/ultrasonic.*.log`);
toggled off - the keep/delete confirmation appeared with the real file count and size ("There are 1
log files taking up ~1.0 MB space in the ... directory."); tapped **Keep files** (no deletion),
restored to the original off state with no path shown.

**Destructive actions:** `Clear Image Cache` and `Clear All Downloads` both opened their exact
confirmation copy; **Cancel** tapped both times - the real image cache size and the real downloaded
songs were never touched. `Clear Search History` has no confirmation in the legacy design (an
unconditional action) - per the task's "do not clear real user data" instruction, it was **not**
actually triggered live; its logic is covered by a dedicated seam-based unit test instead.

**Navigation:** Settings → Equalizer → Back (single back arrow, Equalizer's own unaffected legacy
chrome, correct return to Playback) and Settings → About → Back (single back arrow, no duplicate
header, correct return to the Settings top level) both verified.

**Stability:** the app process (`pidof`) stayed the same single PID across the entire validation
session - no crash-triggered restart; `adb logcat -d *:E` filtered for
`org.moire.ultrasonic|io.github.churipakinti|AndroidRuntime|FATAL|ANR` across the whole session
returned nothing.

**Confirmed afterward**: every intentionally-changed setting was restored to its exact original value
- `wifiRequiredForDownload`=off, `replayGain`="Disabled", `customCacheLocation`=off (and the real
on-disk cache location untouched), `useId3Tags`/`useId3TagsOffline`=on/on, `debugLogToFile`=off,
`showConfirmationDialog`=off (never touched), `overrideLanguage`="System default" (never touched),
`cacheSize`="500 MB" (never touched). No downloaded songs or image-cache entries were deleted.

### Coverage

| | Before 5A4 | After 5A4 |
|---|---:|---:|
| Compose | 25 | 26 |
| Hybrid | 0 | 0 |
| Intentionally legacy | 2 | 1 |
| Total reachable | 27 | 27 |

Arithmetic: 25 + 0 + 2 = 27 → 26 + 0 + 1 = 27. Remaining intentionally-legacy surface: Equalizer
(§B.4, updated).

### Known debt

`showConfirmationDialog` remains dead (preserved, not fixed - a product decision, not this phase's
to make). The `deleteMetaDatabase` pre-delete-active-id quirk (§R) and the `index`-vs-`id` contract
(resolved in §S) are both unrelated to this surface and untouched. `ServerSettingsModel.moveItemUp`/
`moveItemDown` remain dead code (§R, unrelated). The Videos-mode product decision (§B.5) remains open
and unrelated. No new debt was introduced by this phase; both real bugs found during development (the
cross-test SharedPreferences leak, the dispatcher-hop test flakiness) were fixed and verified stable
across repeated runs within this same session, not deferred.

### Next phase

**Phase 5A5 — migrate Equalizer to Compose.** Not started as part of this phase.
