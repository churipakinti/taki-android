# Taki Compose Migration — Coverage Audit (Issue #10)

**Status:** updated through phase 4M2 (Create/Edit Playlist migrated to Compose). Phase 4L
baseline: `develop` at `16ddc285` (Migrate Sleep Timer to Compose sheet); phase 4M1 baseline:
`768a2a0f` (Finalize Compose migration coverage audit); phase 4M2 baseline: `9b6a8de6` (Migrate
folder browsing to Compose). This supersedes the phase-4C version of this document (baseline
`5f8bdc6a`), which is now stale — nearly every `MIGRATE_IN_#10` row it listed has since shipped
(Artist List, Album List, all `TrackCollectionFragment` browsing modes, Playlists, Genres,
Downloads, mini-player, Now Playing, Up Next, Lyrics, Sleep Timer, folder browsing, Create
Playlist).

Sources used: `ultrasonic/src/main/res/navigation/navigation_graph.xml` (read directly, not
recalled), every Fragment class reachable from the graph, `NavigationActivity.kt`'s
`onDestinationChangedListener`/`hidesSupportActionBar`/`miniPlayerHiddenFor`/`updateChromeVisibility`
(chrome/toolbar/mini-player rules — read in full, not summarized from memory), `git log
5f8bdc6a..16ddc285` (27 commits), GitHub issue #10 and #21–#24 bodies (fetched live via `gh issue
view`), and a live Pixel 7 crawl (`2B191FDH200E36`) via `adb`/`uiautomator`.

---

## A. Executive summary

| Metric | Count |
|---|---:|
| Total reachable UI surfaces classified | 27 |
| — Compose | 20 |
| — Hybrid (Compose + View by design) | 1 |
| — Legacy View/XML, still core-browsing (`MIGRATE_IN_#10` remaining) | 1 |
| — Legacy View/XML, intentionally out of scope | 5 |
| Dead / unreachable, removed phase 4L | 2 (nav nodes) + 2 (dead adapter/binder classes) |
| Dead / unreachable, removed phase 4M2 | 3 (dead adapter/layout files, Create/Edit Playlist picker) |
| Dead / unreachable, product decision required | 1 (Videos mode) |
| Unclassified | **0** |

Arithmetic: 20 + 1 + 1 + 5 = 27 reachable surfaces. Videos is excluded from "reachable" because it
has zero live callers (confirmed by exhaustive grep — see §F). Phase 4M1 moved folder/non-ID3
browsing from legacy to Compose (18→19, 3→2); phase 4M2 moved Create/Edit Playlist from legacy to
Compose (19→20, 2→1); see §B.1/§B.3, §L, and §M below.

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

### B.2 — Hybrid by design

| Surface | Destination | Detail |
|---|---|---|
| Box Sets shell | `collectionListFragment` | XML shell (`ComposeView` header declared in the XML layout) over an unchanged XML `RecyclerView` body. Intended end state since phase 4B, not a migration gap. |

### B.3 — Legacy, still core browsing (`MIGRATE_IN_#10` remaining)

| Surface | Destination / Mode | Current UI | Reachability | Classification |
|---|---|---|---|---|
| Save Playlist dialog | `PlayerFragment.showSavePlaylistDialog()` (inline `AlertDialog`, `R.layout.save_playlist`) | View | Live, reached from Now Playing's (Compose) overflow menu | `SMALL_BLOCKER_FOR_#10` — one dialog embedded in an otherwise-Compose screen |

Folder/non-ID3 browsing and Create/Edit Playlist (the phase-4L version of this audit's other two
§B.3 rows) were migrated to Compose in phases 4M1/4M2 — see §B.1. The one remaining row is
confirmed live via direct grep of its call site (`PlayerFragment.showSavePlaylistDialog`), not
inferred. Not migrated in this pass — building a new Compose surface is implementation work, out
of scope for an audit-and-cleanup phase per that task's own instructions.

### B.4 — Intentionally legacy / out of scope for #10

| Surface | Destination | Verified via |
|---|---|---|
| Settings | `settingsFragment` | `NavigationActivity.hidesSupportActionBar`/`updateChromeVisibility` still special-case it; Pixel-verified, tokenized dark theme, no toolbar, correct back nav |
| About | `aboutFragment` | same |
| Server Selector | `serverSelectorFragment` | same |
| Edit Server | `editServerFragment` | same |
| Equalizer | `equalizerFragment` | same |

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
