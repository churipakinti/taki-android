# Taki Compose Migration Plan

**Status:** Architecture of record for the incremental Jetpack Compose migration
**Issue:** #8 (architecture). Consumed by #9 (Compose visual foundation) and #10 (screen migration).
**Scope of this document:** decide the architecture once, so #9 and #10 are execution, not re-design.

> This document does **not** migrate any screen, refactor playback, or add production
> Compose code. It defines boundaries, ownership, order and risks. Where a code snippet
> appears it is a contract sketch, not a finished implementation.

Companion documents:

- `docs/design/TAKI_DESIGN_SYSTEM_V2.md` — composition, geometry, component inventory, QA matrix. **Visual source of truth.**
- `docs/technical/TAKI_GUIA_VISUAL_Y_LENGUAJE.md` — voice, colour rationale, per-screen rules.
- `docs/assets/TAKI_VISUAL_NORTH_STAR.png` — directional visual reference (V2 §22 governs conflicts).
- The project's playback verification checklist (referenced in `HANDOFF.md`) — mandatory manual passes after touching `MediaPlayerManager` / `PlaybackService` / `PlayerFragment` / `NowPlayingFragment`.

---

## 0. Current architecture (starting point)

Facts established by reading the codebase on the `develop` branch:

| Area | Current state |
|---|---|
| UI toolkit | 100% Android Views + XML. `viewBinding` and `dataBinding` both enabled. **Zero Compose anywhere** (no dependency, no code). |
| Activity | One `NavigationActivity : ScopeActivity` (Koin). Hosts everything. `navigation_activity.xml` is a vertical `LinearLayout`: toolbar → content header → `NavHostFragment` → `now_playing_fragment` (`FragmentContainerView`) → `BottomNavigationView`. |
| Navigation | `androidx.navigation` (fragment) 2.9.8 + Safe Args. One flat `navigation_graph.xml` (~25 destinations, no per-tab subgraphs). Typed directions via `NavigationGraphDirections` / `*FragmentDirections`. |
| Screens | ~30 `Fragment`s in `org.moire.ultrasonic.fragment`. Lists use `BaseAdapter` + MultiType `ItemViewDelegate` / `*Binder` classes in `org.moire.ultrasonic.adapters`. |
| UI state holders | `AndroidViewModel`s in `org.moire.ultrasonic.model` (`HomeViewModel`, `AlbumListModel`, `ArtistDetailModel`, `GenericListModel` base, …). State exposed as `MutableLiveData<List<Domain>>`. Obtained with `by viewModels()` (default factory, **not** Koin). |
| Playback / runtime | `org.moire.ultrasonic.service`. `MediaPlayerManager` (Koin **singleton**, headless) wraps a Media3 `MediaController` bound to `PlaybackService` (`MediaLibrarySession`). Siblings: `MediaPlayerLifecycleSupport`, `PlaybackStateSerializer`, `DownloadService`, `RatingManager`, `SleepTimerController`, `PlaybackStallWatchdog`. |
| Event bus | `RxBus` (RxJava 3) — hot `Observable`s, most `replay(1).autoConnect(0)`, several with a 300 ms `throttleLatest` variant. Carries player state, playlist, download state, ratings, sleep timer, service commands. |
| Domain models | `core:domain` — `Track`, `Album`, `Artist`, … are Kotlin `data class`es **that are also Room `@Entity`s** and `Serializable`, with `var` fields. |
| Mini-player | `NowPlayingFragment` inside `now_playing_fragment` `FragmentContainerView`, a **sibling of the nav host**, not a destination. `NavigationActivity` shows/hides it from `RxBus.playerStateObservable` and owns its inset math (`applyBottomInset`, `getContentBottomInset`). |
| Chrome logic | `NavigationActivity.destinationChangedListener` switches toolbar / content-header / bottom-nav / mini-player visibility **by destination id and nav arguments**. Bottom-nav tab taps are custom (`switchToBottomNavTab` → `popBackStack(id,false)` or `navigate(id)`). |
| Tests | JVM only (`src/test`, JUnit Platform + Robolectric + Koin + Mockito). **No `androidTest` source set. No screenshot tooling.** Pixel 7 available over ADB for manual verification. |
| Toolchain | Kotlin 2.4.10 (bundled Compose compiler), AGP 9.2.1, `compileSdk`/`targetSdk` per `config`, single app module `:ultrasonic` + `:core:domain` + `:core:subsonic-api`. |
| Build fragility | Repo is inside OneDrive; generated files intermittently become cloud placeholders and break Gradle snapshotting. Fix is a `build/` wipe. Adding a compiler plugin (Compose) will hit this. |

### 0.1 Consequences that shape the whole plan

1. **The Activity, its XML shell, and the fragment nav graph are load-bearing and stay XML for the entire migration.** Every piece of chrome/back-stack/deep-link logic keys off *fragment destination ids*. Anything that forks the navigation host (i.e. `navigation-compose`) breaks it.
2. **`MediaPlayerManager` is already the single process-scoped playback facade.** The migration's job is to *not* add a second one, not to build one.
3. **Domain models are stable-ish `data class`es but are Room entities with `var` fields** — Compose can read them but must treat them as immutable snapshots, and the compiler may flag them as unstable.
4. **There is no UI test infrastructure at all.** Issue #9 must build it before issue #10 can declare anything "safe".

---

## 1. View ↔ Compose interoperability

### 1.1 Chosen model: **Compose-in-Fragment, one `ComposeView` per destination**

Each migrated screen stays a `Fragment` registered in `navigation_graph.xml` with the **same
`android:id`, the same `<argument>`s and the same `<action>`s**. Its entire view is a single
`ComposeView`:

```kotlin
class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels() // unchanged factory

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            TakiTheme {
                HomeScreen(
                    state = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onAlbumClick = { album -> findNavController().navigate(/* directions */) },
                    onRefresh = viewModel::refresh,
                )
            }
        }
    }
}
```

**Boundary rules**

- The `ComposeView` lives **at the Fragment root only**. No `ComposeView` embedded inside an
  XML layout for "partial" migration, and no Android `View` embedded inside a Compose screen
  via `AndroidView`. A screen is either fully XML or fully Compose. (The **single sanctioned
  exception** is Now Playing — see §8 step 7 — where the still-XML `PlayerFragment` shell may
  host sub-section `ComposeView`s during its own staged migration.)
- `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` on every `ComposeView`, so the
  composition's lifecycle is the Fragment **view** lifecycle — matching how the current
  fragments dispose view bindings and RxBus subscriptions in `onDestroyView`.
- The host Fragment obtains its `ViewModel` exactly as today (`by viewModels()`), and **passes
  it (or plain lambdas + an immutable state object) into the root composable**. Composables
  never call `viewModels()` / `koinViewModel()` themselves — keeps them preview-able and unit
  testable without a Fragment.
- The host Fragment is the **only** place allowed to touch `findNavController()`. Composables
  receive `onXxxClick` lambdas or emit typed nav events (see §3.2).

### 1.2 Why not the alternatives

| Option | Rejected because |
|---|---|
| `androidx.navigation:navigation-compose` + Compose `NavHost` | Forks the back stack from the fragment `NavController`. Breaks `NavigationActivity`'s destination-id chrome logic, `switchToBottomNavTab`, `appBarConfiguration`, `onNewIntent` deep links, and the mini-player show/hide. Would require re-implementing all of it. Massive risk for zero user benefit mid-migration. |
| `ComposeView` islands inside existing XML screens | Encourages long-lived hybrid screens with split state ownership and two scroll containers. Harder to reason about lifecycle. Only acceptable as a *temporary* internal step for Now Playing. |
| Activity becomes a `ComponentActivity` with `setContent` | Rewrites navigation, chrome, insets, mini-player hosting and lifecycle in one step. Explicitly out of scope for #8–#10. |
| New Gradle module for Compose UI | Not needed for a single-app-module project; adds build graph complexity and the OneDrive/Gradle fragility surface. Revisit only if compile times demand it. |

### 1.3 Coexistence during migration

- The nav graph is **mixed**: some destinations point at XML fragments, some at Compose-hosting
  fragments. Because both are `Fragment`s with unchanged ids/args/actions, `NavController`,
  Safe Args, the back stack and every `NavigationActivity` chrome branch keep working with **no
  changes**.
- `NavigationActivity`, `navigation_activity.xml`, `themes.xml`, `colors.xml` and the toolbar /
  content-header / bottom-nav / mini-player stay XML until the very end (see §7.3).
- Migrated and non-migrated screens can navigate to each other freely in both directions via
  the existing `NavigationGraphDirections`.

### 1.4 Avoiding duplicate state / lifecycle ownership

| Concern | Rule |
|---|---|
| Screen data | Lives in the `ViewModel` only. The composable renders it; it does **not** copy it into `remember`/`mutableStateOf`. |
| Ephemeral view state (scroll offset, expanded row, unsent text) | `rememberSaveable` inside the composable. Never in the Fragment, never in the ViewModel. |
| Composition lifecycle | Bound to the Fragment **view** lifecycle via `DisposeOnViewTreeLifecycleDestroyed`. One composition per `ComposeView`. |
| Collection of flows | `collectAsStateWithLifecycle()` (needs `lifecycle-runtime-compose`) so collection stops in `STOPPED`, matching current `observe(viewLifecycleOwner)` semantics. |
| Playback state | Read **only** through the shared mapper of §2.3. A screen never subscribes to `RxBus` directly and never holds a `MediaController`. |
| Koin-scoped objects | Still injected into the Fragment (or the ViewModel), passed inward as parameters. |

---

## 2. State ownership

Three tiers. A screen may read from tiers 2 and 3 and must never reach into tier 1.

### 2.1 Tier 1 — runtime / playback (unchanged, stays in `org.moire.ultrasonic.service`)

Owns all playback, session, queue, download and persistence state. **No Compose code, no
Compose dependency, ever.**

- `MediaPlayerManager` (Koin singleton) — the playback facade. Media3 `MediaController`
  lifecycle, rebuild-on-disconnect (issue #18), stall watchdog (#17), network-error re-prepare
  (#19), queue caps (`MAX_QUEUE_SIZE`), chunked `addMediaItems`.
- `MediaPlayerLifecycleSupport`, `PlaybackService` (`MediaLibrarySession`), `PlaybackStateSerializer`.
- `DownloadService` / `DownloadState`, `RatingManager`, `SleepTimerController`, `Scrobbler`.
- `RxBus` — the service→app event bus.

Media3 types (`MediaController`, `Player`, `MediaItem`, `SessionToken`, `Timeline`, ratings)
**must not appear in any `org.moire.ultrasonic.ui.*` file**. Enforced by a lint/detekt import
ban (§5.4).

### 2.2 Tier 2 — screen UI state (ViewModel / state holder)

The existing `model.*` `AndroidViewModel`s remain the screen state holders and keep being
created by `by viewModels()`. Their **internal** upgrade during migration:

- **Target:** expose one immutable `data class XxxUiState` as a `StateFlow<XxxUiState>`
  (`MutableStateFlow` internally), collected with `collectAsStateWithLifecycle()`.
  `XxxUiState` bundles content + `isLoading` + `error` + empty flags (today these are scattered
  across several `LiveData` + ad-hoc `updateEmptyState()` in the Fragment).
- **Bridge allowed:** a screen may be migrated *before* its ViewModel is refactored, consuming
  the existing `LiveData` via `observeAsState()`. This is a transition state, not an end state —
  the same PR that deletes the old Fragment should also land the `StateFlow` refactor for that
  ViewModel.
- Loading / refresh / cancellation stays in `viewModelScope` with the existing
  stale-response guards (`GenericListModel.loadJob`, `LatestRequestTracker`,
  `HomeShelvesFreshness`). Compose does not change any of this.
- Navigation intent is modelled as events, not state (§3.2).

### 2.3 Tier 3 — playback-facing UI state (shared read-only projection)

A new **`PlaybackUiStateHolder`** — the *only* adapter between `RxBus`/`MediaPlayerManager` and
Compose UI. One instance, app-scoped (Koin `single`), created lazily by the first consumer.

#### 2.3.1 Semantics — non-negotiable

`PlaybackUiStateHolder` is a **read projection of state that already exists** in
`MediaPlayerManager` / the playback services / `RxBus`. It is **never a second source of
truth**. It stores no playback decision of its own; every field it exposes is derived from an
upstream emission.

- **State flows one way:**
  `MediaPlayerManager / services  →  RxBus  →  PlaybackUiStateHolder  →  Compose`
  The holder only *observes upstream and maps*. It does not call `MediaPlayerManager` to change
  anything, and it does not hold a `MediaController`, a `Player`, a session, or a queue of its
  own.
- **Commands flow the other way, and skip the holder entirely:**
  `Compose  →  command callback (onPlayPause / onSeek / onToggleShuffle / onMoveQueueItem / onToggleStar / …)  →  ViewModel or host Fragment  →  MediaPlayerManager / RatingManager`
  The authoritative state change happens in tier 1; the new value then propagates back to
  Compose through the read path above.
- **No optimistic mutation.** A composable must **not** update a local copy of player/queue
  state on interaction and reconcile with the real state later. It issues the command and
  re-renders from the next `PlayerUiState` / `DownloadUiState` emission. The rendered list/seek
  position/toggle is always whatever tier 1 last reported — briefly stale is acceptable, a
  divergent local truth is not. (Queue reorder specifics: R3 in §9.)
- **Additive only.** Introducing the holder must not edit `MediaPlayerManager`,
  `PlaybackService`, `PlaybackStateSerializer` or `RxBus`. If a needed signal is not already on
  `RxBus`, add a projection *inside the holder* over what `RxBus`/`MediaPlayerManager` already
  expose — do not add state to tier 1.

```kotlin
// org.moire.ultrasonic.ui.playback
@Immutable
data class PlayerUiState(
    val currentTrack: Track? = null,          // domain snapshot, read-only
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playbackState: PlaybackPhase = PlaybackPhase.Idle, // enum, not Media3 Int
    val repeat: RepeatUiMode = RepeatUiMode.Off,
    val shuffle: Boolean = false,
    val elapsedMs: Long = 0,
    val durationMs: Long = 0,
    val sleepTimer: SleepTimerUi? = null,
    val queueLoading: Boolean = false,
)

@Immutable
data class DownloadUiState(val byTrackId: Map<String, DownloadBadge> = emptyMap())

class PlaybackUiStateHolder(/* MediaPlayerManager, RxBus */) {
    val player: StateFlow<PlayerUiState>       // from throttledPlayerStateObservable + throttledPlaylistObservable + sleepTimer/queueLoading
    val downloads: StateFlow<DownloadUiState>  // folds trackDownloadStateObservable single-track updates into a map
    // progress is derived in the composable (see §9 perf), not re-emitted per frame
}
```

- Subscribes **once** to the already-hot, `replay(1)` RxBus observables (prefer the
  `throttled*` variants — 300 ms — for anything the mini-player or a list renders). Converts
  each to a `StateFlow` via a small `Observable.asFlow()` extension + `stateIn(appScope)`.
- Maps Media3 `Int` states / rating types to **Taki UI enums** so no Media3 type crosses the
  boundary.
- Exposes **domain `Track`** objects directly for the current track and queue (they are already
  stable data classes). Consumers treat them as immutable.
- **Commands go the other way through the existing API**: composables invoke lambdas
  (`onPlayPause`, `onSeekToNext`, `onSeek`, `onToggleShuffle`, `onMoveQueueItem`,
  `onToggleStar`) that the ViewModel or host Fragment forwards to `MediaPlayerManager` /
  `RatingManager` **unchanged**. No new command path, no optimistic local mutation of queue as
  source of truth (§9).

### 2.4 How Compose consumes models

**Decision: no blanket stability configuration.** `org.moire.ultrasonic.domain.*` types are
Room `@Entity` `data class`es with `var` fields — they *are* potentially mutable, so a global
`stabilityConfiguration` entry marking the package stable would be a false promise to the
compiler and a latent correctness hazard. It is not used.

Instead:

- Each screen defines an **explicit immutable `XxxUiState`** (and, where a list row needs it,
  explicit `@Immutable` row models — `AlbumCardUi`, `TrackRowUi`, …) built in the ViewModel /
  `PlaybackUiStateHolder` from the domain objects. These carry only the fields the screen
  renders, as `val`s of stable types (`String`, `Int`, `Boolean`, enums, `ImmutableList`).
- Composables receive those UI models, **not** raw `Track` / `Album` / `Artist`. A domain
  object may be passed *through* as an opaque payload for a click callback
  (`onAlbumClick(album)`), but is not read field-by-field inside `@Composable` scope.
- Use `kotlinx.collections.immutable` (`ImmutableList` / `persistentListOf`) for list fields on
  UI-state models so `LazyColumn` content is skippable.
- `LazyColumn` / `LazyRow` items keyed by stable domain id (`track.id`, `album.id`) for
  skippability and correct scroll / animation identity.
- Mapping cost (domain → UI model) is trivial relative to network/DB fetch and happens once per
  emission in the ViewModel, off the composition.

---

## 3. Navigation

### 3.1 Strategy: keep fragment Navigation + Safe Args for the whole migration (and after)

- `navigation_graph.xml` stays the single source of navigation truth. Each Compose screen is a
  `Fragment` node with an **unchanged id, unchanged `<argument>` set, unchanged `<action>`s**.
  If the host class name changes (e.g. `HomeFragment` → a thin `HomeComposeFragment`), update
  only `android:name` and delete the old class in the same PR.
- Navigation calls remain `findNavController().navigate(NavigationGraphDirections.toX(...))`.
- No `navigation-compose`. No Compose `NavHost`. No Voyager/Decompose.

Rationale: every consequence in §0.1.1. The `NavigationActivity` chrome/back-stack/deep-link
logic is entirely destination-id driven and keeps working untouched only if destinations stay
fragments with stable ids.

### 3.2 How a composable triggers navigation

Two patterns, chosen per screen:

| Pattern | Use when | Shape |
|---|---|---|
| **Callback props** | Screen has ≤ 2 navigation targets and no conditional nav logic. | `HomeScreen(onAlbumClick: (Album) -> Unit, onSeeAllNewest: () -> Unit)`; Fragment supplies lambdas that call `findNavController()`. |
| **Typed nav events** (default for detail screens) | Screen has many targets / overflow menus / conditional routing (album detail, artist detail, search). | ViewModel exposes `val navEvents: SharedFlow<NavTarget>` (or `Channel`); Fragment `collect`s it in `repeatOnLifecycle` and does the single `findNavController().navigate(...)`. Composable calls `viewModel.onAlbumClicked(album)`. Keeps NavController out of both composable and ViewModel-unit-tests. |

`NavTarget` is a sealed interface of Taki-level destinations (`AlbumDetail(id, name, parentId)`,
`ArtistDetail(...)`, `Player`, …); the Fragment maps it to `NavigationGraphDirections`. This is
also the seam that would later make a host swap possible without touching screens.

### 3.3 Back-stack behavior

- Unchanged. Fragment back stack via `NavController`.
- `NavigationActivity.switchToBottomNavTab` (`popBackStack(item.itemId,false)` else
  `navigate(item.itemId)`) and `onSupportNavigateUp()` / `appBarConfiguration` keep working
  because tab roots (`homeFragment`, `mainFragment`, `searchFragment`) stay the same fragment
  ids.
- A tab re-tap must still land on that tab's root, never a stale sub-screen — covered by an
  existing behavior comment in `NavigationActivity`; add a regression test (§6.4).

### 3.4 Contextual navigation album ↔ artist ↔ player

- Already implemented (issue #16): tappable artist name on album detail / Now Playing,
  album-detail `⋮` overflow (go to artist / play next / add to queue / start radio) via
  `ContextMenuUtil`, artwork intentionally non-clickable to preserve swipe gestures.
- Compose screens reproduce this by calling the **same `NavigationGraphDirections` actions**
  and reusing `ContextMenuUtil` from the host Fragment (invoked via a callback / nav event).
  The `⋮` menu itself can stay a platform `PopupMenu` raised by the Fragment initially; a
  Compose `DropdownMenu` is a later polish, not required for parity.
- Mini-player tap and `INTENT_SHOW_PLAYER` still `navigate(R.id.playerFragment)`.

### 3.5 Scroll / state restoration

- **Config change & back-stack pop-return:** because compositions are disposed with the
  Fragment view, list state is **not** retained automatically. Required pattern:
  `val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }` (same for
  grid / scroll). The `ViewModel` holds the data, so recomposition on return is cheap and the
  saved index/offset restores the visual position.
- **Match, don't regress, current behavior:** today scroll position survives only within a
  live Fragment instance and is lost when the fragment view is destroyed on back navigation
  (RecyclerView state is not persisted across `NavController` pop for most of these screens).
  `rememberSaveable` for list state is therefore a small *improvement*, acceptable as long as
  it is consistent.
- Endless-scroll / pagination (`EndlessScrollListener`, `GenericListModel` offset paging) moves
  into the ViewModel: expose `canLoadMore` + `onLoadMore()`; the composable calls `onLoadMore()`
  when `listState.layoutInfo` nears the end. Paged data still accumulates in the ViewModel.

### 3.6 Deep links / external entry

- `NavigationActivity.onNewIntent` handles `INTENT_PLAY_RANDOM_SONGS`, `ACTION_MAIN` +
  `INTENT_SHOW_PLAYER`, `ACTION_SEARCH`, `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`, and app
  shortcuts — all destination-id / `NavigationGraphDirections` based.
- Constraint on #10: a migrated screen **must keep its existing argument names and types** so
  `NavigationGraphDirections` and these intent handlers stay valid. New Compose screens read
  args via Safe Args `navArgs()` in the host Fragment (or `SavedStateHandle` in the ViewModel)
  and pass them inward — never parse the `Bundle` in a composable.
- No new `<deepLink>` entries are required by this migration; if one is added later it attaches
  to the fragment node like any other.

---

## 4. Persistent mini-player

### 4.1 It does not move

The mini-player stays exactly where it is architecturally:

- `now_playing_fragment` is a `FragmentContainerView` **sibling of the `NavHostFragment`** in
  `navigation_activity.xml`, owned by `NavigationActivity`, **not a navigation destination**.
- Visibility is driven by `NavigationActivity` from `RxBus.playerStateObservable`
  (`showNowPlaying()` / `hideNowPlaying()`), gated by `Settings.SHOW_NOW_PLAYING`, current
  destination (`playerFragment`, search+IME) and the user's dismiss gesture.
- Inset math (`applyBottomInset`, `getContentBottomInset`, edge-to-edge nav-bar handling)
  operates on the **container view**, independent of its contents.

Because it is a separate view tree with its own lifecycle, layered over the nav host, it
already survives **every** destination swap — XML→XML, XML→Compose, Compose→Compose — with zero
changes. Nothing about mixed hosting affects it.

### 4.2 What "migrating the mini-player" means (§8 step 6)

Only `NowPlayingFragment`'s **contents** change:

- `onCreateView` returns a `ComposeView` rendering a `MiniPlayer` composable.
- `MiniPlayer` reads `PlaybackUiStateHolder.player` (throttled) + `.downloads`, renders per
  V2 §13 (64 dp, `surface`, 2 dp top progress line, 48 dp play/next targets), and calls
  `MediaPlayerManager` via callbacks (`togglePlayPause`, `seekToNext`) and
  `findNavController().navigate(R.id.playerFragment)` on body tap.
- Swipe/drag gesture handling (currently in `handleOnTouch`) reproduced with Compose
  `pointerInput` / `draggable`, preserving "swipe up/down no longer dismisses" behavior.
- **`NavigationActivity` is not touched.** The container id, the show/hide calls, the inset
  code and `getContentBottomInset()` all keep referring to the `FragmentContainerView`.

### 4.3 It survives screen replacement without duplicating playback ownership

- The mini-player reads a **process-scoped singleton** (`MediaPlayerManager` via
  `PlaybackUiStateHolder`). It holds no `MediaController`, no queue, no session. Replacing the
  screen behind it changes nothing it depends on.
- **Explicitly rejected:** hoisting the mini-player into a per-screen Compose `Scaffold`
  `bottomBar`. That would instantiate it once per destination, rebuild it on every navigation,
  force every screen to depend on playback state, and re-introduce the inset problem the
  Activity already solves. V2 §13 is explicit that "playback/service ownership must remain
  outside UI composition".
- A shared mini-player ↔ Now Playing **element transition** (V2 §13, §17) is a **post-#10**
  candidate, only worth doing once both are Compose; it still would not move ownership — it
  would animate two views reading the same singleton.

---

## 5. Theme / design-system migration

### 5.1 `TakiTheme` — the Compose token layer (issue #9)

A single `TakiTheme { }` wrapper that:

1. Builds a Material 3 `ColorScheme` from the **exact** `themes.xml` attribute mapping and
   passes it to `MaterialTheme` (so stock M3 components inside Taki are themed correctly).
2. Provides Taki-specific tokens through `CompositionLocal`s, mirroring
   `docs/design/TAKI_DESIGN_SYSTEM_V2.md §16`:
   `LocalTakiColors`, `LocalTakiSpacing`, `LocalTakiShapes`, `LocalTakiTypography`,
   `LocalTakiIcons`, `LocalTakiMotion`, `LocalTakiDimensions`.
3. Exposes them via a `TakiTheme` accessor object (`TakiTheme.colors.accent`,
   `TakiTheme.spacing.lg`, `TakiTheme.type.sectionHeader`).

All token **values are transcribed from the current XML**, not re-derived from Compose M3
defaults (which differ subtly from the View M3 defaults already shipping).

### 5.2 1:1 mapping tables (authoritative for #9)

**Colours — from `res/values/colors.xml`** (`TakiColors`, same hex):

| XML | Compose |
|---|---|
| `taki_black #090B09` | `TakiColors.black` |
| `taki_surface_low #111410` | `TakiColors.surfaceLow` |
| `taki_surface #171A16` | `TakiColors.surface` |
| `taki_surface_high #1D211B` | `TakiColors.surfaceHigh` |
| `taki_ivory #F1F2ED` | `TakiColors.ivory` |
| `taki_gray #A7AAA2` | `TakiColors.gray` |
| `taki_accent #B7D63C` | `TakiColors.accent` |
| `taki_accent_pressed #91AD30` | `TakiColors.accentPressed` |
| `taki_accent_secondary #91AD30` | `TakiColors.accentSecondary` |
| `taki_progress #7C9229` | `TakiColors.progress` |
| `taki_selected_container #293217` | `TakiColors.selectedContainer` |
| `taki_on_accent #11130D` | `TakiColors.onAccent` |
| `taki_selected_neutral #14FFFFFF` | `TakiColors.selectedNeutral` |
| `taki_outline #1FFFFFFF` | `TakiColors.outline` |
| `taki_divider #0DFFFFFF` | `TakiColors.divider` |
| `taki_error #FFB4AB` | `TakiColors.error` |
| `taki_on_error_container #FFDAD6` | `TakiColors.onErrorContainer` |

**Material `ColorScheme` — from `themes.xml` (`UltrasonicTheme.Dark`)**, transcribe every
mapping: `primary=accent`, `onPrimary=onAccent`, `primaryContainer=selectedContainer`,
`onPrimaryContainer=ivory`, `secondary=accentSecondary`, `secondaryContainer=selectedNeutral`,
`background=black`, `onBackground=ivory`, `surface=black`, `surfaceVariant=surface`,
`surfaceContainerLow=surfaceLow`, `surfaceContainer=surfaceLow`,
`surfaceContainerHigh=surface`, `surfaceContainerHighest=surfaceHigh`, `onSurface=ivory`,
`onSurfaceVariant=gray`, `outline=outline`, `outlineVariant=divider`, `error=error`,
`onErrorContainer=onErrorContainer`. Dark scheme only (Taki ships one theme, no day/night).

**Dimensions — from `res/values/dimens.xml`** (`TakiSpacing` / `TakiShapes` / `TakiDimensions`):

| XML | Compose | Value |
|---|---|---|
| `space_xxs … space_2xl` | `TakiSpacing.xxs … xxl` | 2 / 4 / 8 / 12 / 16 / 24 / 32 dp |
| `space_screen_horizontal` | `TakiSpacing.screenHorizontal` | 16 dp |
| `space_section_gap` | `TakiSpacing.sectionGap` | 24 dp |
| `space_text_tight` | `TakiSpacing.textTight` | 2 dp |
| `radius_xs/sm/md/lg` | `TakiShapes.xs/sm/md/lg` (`RoundedCornerShape`) | 4 / 8 / 12 / 20 dp |
| `icon_size_sm/md/lg` | `TakiDimensions.iconSm/Md/Lg` | 18 / 24 / 32 dp |
| `touch_target_min` | `TakiDimensions.touchTargetMin` | 48 dp |
| `row_height_sm/md/lg` | `TakiDimensions.rowSm/Md/Lg` | 56 / 64 / 72 dp |
| `artwork_thumb/card` | `TakiDimensions.artworkThumb/Card` | 56 / 140 dp |
| `elevation_raised` / `border_thin` | `TakiDimensions.elevationRaised` / `borderThin` | 3 / 1 dp |

V2 §7 artwork sizes not yet in `dimens.xml` (104, 124, 156, 184, 96, 364…) are added to
**both** `dimens.xml` and `TakiDimensions` in the same #9 change, keeping the two in lockstep
per the `dimens.xml` header rule.

**Typography — from `res/values/type.xml`** (`TakiTypography`, `TextStyle`s). Pin the resolved
size / line-height / weight / colour rather than inheriting Compose M3 defaults:

| Role (V2 §5) | size / line | weight | colour |
|---|---|---|---|
| `Taki.Hero` | 24 / 32 sp | Medium (500) | onSurface (ivory) |
| `Taki.Title` | 16 / 24 sp | Medium | onSurface |
| `Taki.TitleSmall` | 14 / 20 sp | Medium | onSurface |
| `Taki.Body` | 14 / 20 sp | Normal (400) | onSurface |
| `Taki.Caption` | 12 / 16 sp | Light (300) | onSurfaceVariant (gray) |
| `Taki.SectionHeader` | 14 / 20 sp | Medium, **allCaps = false** | onSurfaceVariant |

**Motion — from V2 §17** (`TakiMotion`): durations 120 / 180 / 180–240 / 240 / 280–320 / 180 ms
with the three named cubic-bezier easings. New in Compose; no XML equivalent to mirror.

### 5.3 `TAKI_DESIGN_SYSTEM_V2.md` as the visual source of truth

- During migration: **XML resource values are the token source of truth** (V2 §0); the Compose
  tokens are generated from them and must produce identical values. **V2 governs which token a
  component uses and the screen composition/geometry.**
- After the last XML layout is deleted (§7.3): the XML token files are removed and **V2 +
  `TakiTheme` become jointly authoritative** — V2 for geometry/composition, `TakiTheme` for the
  concrete values.
- V2 §15 component inventory is the required Compose primitive set (§8 step 1). Screens compose
  those primitives; they do not re-implement a primitive's visual rules inline.

### 5.4 Preventing a separate "Compose look"

Guards, all added in issue #9:

1. **Import ban (detekt / lint / Konsist test):**
   - `import androidx.compose.material3.MaterialTheme` — allowed **only** in
     `org.moire.ultrasonic.ui.theme`.
   - `import androidx.media3.*` and `androidx.media3.session.MediaController` — banned in
     `org.moire.ultrasonic.ui.*` entirely.
2. **No raw design values in UI code:** a Konsist / ktlint rule failing on `Color(0x…`,
   numeric `.dp` / `.sp` literals, and `TextStyle(` / `Shape` literals outside
   `org.moire.ultrasonic.ui.theme`. Padding must be `TakiTheme.spacing.*`, never
   `Modifier.padding(16.dp)`.
3. **Primitive-only rule:** a component that exists in V2 §15 must be used, not re-built with
   raw `Row`/`Column`/`Text`.
4. **Screenshot parity (§6.3):** during a screen's transition window, a Roborazzi baseline of
   the Compose screen is eyeballed against the equivalent XML screen at the same viewport.
5. **PR checklist (§6.5) item:** "no unexplained raw dp/sp/hex" (mirrors V2 §20).

### 5.5 Adding a value that has no token

Add the token to **both** `dimens.xml`/`type.xml`/`colors.xml` **and** the matching Compose
object in the same change. Never a bare literal in a composable. If V2 and the current XML
disagree on a value, V2 wins (V2 §22) and the XML token is corrected in the same PR.

---

## 6. Testing strategy

Issue #9 **builds the harness**; issue #10 **fills it in per screen**. Nothing is declared
"safe" until §6.5 passes.

### 6.1 Unit tests (JVM — existing `src/test`, JUnit Platform + Robolectric + Koin)

- Existing pure-logic tests stay as they are.
- **ViewModel state-reduction tests:** feed a fake `MusicService` / fake RxBus emissions;
  assert `StateFlow<XxxUiState>` transitions (loading → content → empty → error, stale-response
  guard, pagination accumulation). `kotlinx-coroutines-test` + **`app.cash.turbine`** for
  `Flow` / `StateFlow` assertions — **approved as a `testImplementation`-only dependency**
  (added in #9, `libs.versions.toml` + `testImplementation` in `ultrasonic/build.gradle`; not
  in any other configuration).
- **`PlaybackUiStateHolder` mapper test:** push `RxBus.StateWithTrack` / playlist / download /
  sleep-timer emissions; assert the mapped `PlayerUiState` / `DownloadUiState` (isPlaying,
  currentTrack identity, repeat/shuffle enum mapping, `byTrackId` map folding,
  throttle/replay behavior). No Compose runtime.

### 6.2 Compose UI tests (JVM via Robolectric — no device)

`createComposeRule()` + `androidx.compose.ui:ui-test-junit4` + `ui-test-manifest`, run under
the existing Robolectric setup (`@RunWith(AndroidJUnit4::class)` in `src/test`).

Per migrated screen:

- Assert nodes for **each state**: loading, empty, error, content.
- Assert **each interactive callback** fires: `onNodeWithContentDescription("Play").performClick()`
  → verify the passed lambda / fake ViewModel method was called.
- Assert **accessibility**: content descriptions on icon-only actions; `stateDescription` /
  `Role` for liked / shuffle / repeat; custom `progressSemantics` / seek actions expose a
  meaningful action (V2 §19).
- Run key assertions at `fontScale` 1.0 / 1.15 / 1.30 (V2 §5, §20) — no overlap, reflow.

### 6.3 Screenshot tests (Roborazzi — JVM, no device)

**Roborazzi is the chosen screenshot-test tool** (not Paparazzi, not an emulator-based tool):
it runs on the JVM through the Robolectric infrastructure the project already has, needs no
emulator, and diffs in CI without a device job.

Add `io.github.takahirom.roborazzi` (`roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`)
— fits the existing Robolectric infra, no emulator, CI-diffable.

- Baseline PNG per screen at the **V2 §20 QA matrix** cells: 360 × 800 and 412 × 915;
  `fontScale` 1.0 and 1.30; long title / long artist; missing artwork; loading state; empty
  state where applicable.
- CI fails on pixel diff beyond threshold; baselines committed under
  `ultrasonic/src/test/screenshots/` (or Roborazzi default).
- **This workflow is a concrete deliverable of issue #9**, proven on the first leaf component
  (§8 step 1) so #10 screens only add cases.

### 6.4 Navigation / back-stack checks

- **JVM (this is the #9 deliverable):** `TestNavHostController` inflated from
  `navigation_graph.xml`; per Compose Fragment, assert its nav callbacks / `NavTarget` mapping
  resolve to the correct destination id + args. Assert tab re-tap pops to the tab root (mirror
  `switchToBottomNavTab`). Robolectric can also drive a `FragmentScenario` over the mixed graph
  for a shallow back-stack unwind check without an emulator.
- **No CI emulator / `androidTest` job in #9.** Issue #9 verification is: JVM unit tests,
  Compose UI tests under Robolectric, Roborazzi screenshot tests, and **manual Pixel 7
  validation**. There is deliberately no `androidTest` source set and no emulator CI job yet.
- **Deferred instrumented smoke test.** A full mixed-stack instrumented test —
  `ActivityScenario<NavigationActivity>` + Espresso, launch → start playback → mini-player
  appears → Home → Library → album detail → Now Playing → unwind → tab switches, with a real
  `MediaController` — is worthwhile, but **revisit adding the `androidTest` source set + an
  emulator CI job when the first real screen migration (issue #10, step 2 — Home) lands**, not
  before. Until then this path is covered by manual Pixel 7 runs against the §6.5 checklist.

### 6.5 "Screen is safe to ship" checklist

A migrated screen's PR must satisfy **all** of:

1. **Behavior contract unchanged** — every nav path, argument, refresh, pagination, empty and
   error path verified against the screen it replaces.
2. **V2 §20 QA matrix** passes (2 viewports × 2 font scales × long text × missing art ×
   loading × empty).
3. **Roborazzi baseline** committed and green in CI.
4. **Compose UI test** covers each state + each interactive callback + a11y semantics.
5. **No new raw color/dp/sp/hex** (lint/Konsist rule green).
6. **No `androidx.media3` / `MediaController` import** anywhere under `ui.*` for this screen.
7. **ViewModel unit test** for state reduction (or a documented reason it is trivial).
8. **Pixel 7 pass.** If the screen touches `MediaPlayerManager` call sites (detail screens,
   mini-player, Now Playing): also the project playback verification checklist —
   playback / large-queue restore / Media3 threading / rotation / "don't keep activities"
   process death.
9. **TalkBack traversal** follows visual order; liked / shuffle / repeat announce state; long
   Spanish strings checked (V2 §19).
10. **Old Fragment + layout + screen-only adapters/menus/drawables deleted in the same PR**
    (§7) — no live duplicate left behind.

### 6.6 Unit-test environment changes (landed in #9)

Enabling the Compose / Roborazzi / navigation-graph tests forced two module-wide test-env
changes. Both are additive and were validated by **re-running the entire pre-existing unit
suite (271 tests, 0 failures) after the change** — no existing test regressed.

**`ultrasonic/build.gradle` — `testOptions.unitTests.includeAndroidResources = true`.**
The Compose UI tests (`createComposeRule`), Roborazzi (`captureRoboImage`), and the
`TestNavHostController` graph inflation all need the merged Android resources on the JVM test
classpath. Before #9 the module ran resource-less (AGP's default), which is why
`RobolectricUAppContext` exists.

**`ultrasonic/src/test/resources/robolectric.properties` (new).** Turning resources on made
Robolectric read the real merged manifest, which broke two prior assumptions:

- `sdk=35` — the app compiles/targets SDK 37; Robolectric 4.16.1 rejects that as unsupported
  the moment it can read `targetSdkVersion` from the merged manifest. Pinning to a supported,
  deterministic platform (35) applies to **every** Robolectric test in the module, old and new.
- `application=android.app.Application` — with the manifest now visible, Robolectric would
  instantiate the real `UApp`, whose constructor starts Koin and calls a `StrictMode` method
  Robolectric does not implement; across a multi-test run this throws
  `KoinApplicationAlreadyStartedException`. Forcing a plain test `Application` restores the
  pre-#9 behaviour (`UApp` was simply never the test app). Tests that need
  `UApp.applicationContext()` still install a stand-in via `RobolectricUAppContext`.

**Roborazzi record/verify.** `ultrasonic/build.gradle` forwards `-Proborazzi.test.record` /
`-Proborazzi.test.verify` / `-Proborazzi.test.compare` to the test JVM and **defaults to
verify**. Goldens live in `ultrasonic/src/test/screenshots/` and are committed. Regenerate
intentionally with `./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true`; see
that folder's `README.md`.

---

## 7. Legacy-code deletion criteria

### 7.1 When XML / layout / Fragment code is removed

**In the same PR that lands the Compose screen**, once §6.5 passes. No "migrate now, delete
later" — a parallel XML copy rots and doubles maintenance (HANDOFF: "don't keep duplicate
implementations indefinitely").

Removed together with a migrated screen:

- the old `*Fragment` class (or repurpose it as the thin Compose host, deleting the old body);
- its `res/layout/*.xml` (and any `_land` / `-sw600dp` variants);
- its screen-specific `res/menu/*.xml`;
- its screen-specific `adapters/*Binder` / `*Delegate` / `*Adapter` **if not shared**;
- now-unreferenced `drawable` / `string` / `dimen` / `id` resources.

### 7.2 Parity to verify before deleting

1. `navigation_graph.xml` still resolves — the node id and `<argument>`s are unchanged; only
   `android:name` updated; the old class has no other referrers.
2. `grep` confirms **nothing else** references the deleted layout ids, `R.layout.*`,
   `R.menu.*`, or adapter classes (`ContextMenuUtil`, other fragments, `NavigationActivity`).
3. Shared adapters (`BaseAdapter`, `TrackViewBinder`, `AlbumRowDelegate`,
   `AlbumDetailHeaderBinder`, `DividerBinder`, `HeaderViewBinder`, `Utils.kt`, …) are removed
   **only when their last consumer is migrated**. Maintain a consumer list per shared adapter
   in the migration PR description; delete on the PR that removes the final consumer.
4. §6.5 items 1–2 (behavior + QA matrix) are the functional parity gate.

### 7.3 Shared foundation — removal order

| Artifact | Removable when |
|---|---|
| A screen's own layout / menu / adapters | that screen's migration PR (§7.1) |
| `type.xml`, `dimens.xml` (as layout tokens) | no `res/layout/*.xml` references them any more |
| `styles.xml`, `player_dimensions.xml`, `arrays.xml` (UI-only entries) | last consuming layout gone |
| `colors.xml`, `themes.xml` (`UltrasonicTheme.*`) | **last** — `navigation_activity.xml` + the toolbar/content-header/bottom-nav stay XML for all of #8–#10, so these survive to the end |
| `NavigationActivity` XML shell → Compose | **out of scope for #8–#10.** A separate, later decision. |
| `viewBinding` / `dataBinding` build flags | only after the last XML layout in the module is gone |

### 7.4 Anti-duplication rule

No screen may exist as both a live XML fragment and a live Compose fragment past its migration
PR. If a migration genuinely must be split across PRs, gate the old path behind **one**
temporary flag (a `BuildConfig` boolean, not a user setting) and remove it within the same
milestone — never leave two implementations reachable indefinitely.

---

## 8. Exact migration order

### Step 0 — Issue #9: Compose foundation + tooling (no screen swapped)

**Status: DONE (issue #9, on `develop`).** What landed:

- Compose enabled: `org.jetbrains.kotlin.plugin.compose` (`version.ref = kotlin`, 2.4.10),
  `buildFeatures.compose = true`; **Compose BOM `2026.08.00`** + `ui`, `ui-graphics`,
  `foundation`, `material3`, `ui-tooling-preview` (+ `ui-tooling` debug),
  `lifecycle-runtime-compose` / `lifecycle-viewmodel-compose` (`2.11.0`), `coil-compose`
  (`version.ref = coil`), `kotlinx-collections-immutable` (`0.4.0`). Test-only:
  `ui-test-junit4`, `ui-test-manifest` (`debugImplementation`), `turbine` (`1.2.1`),
  `navigation-testing` (`version.ref = navigation`), `roborazzi` / `roborazzi-compose` /
  `roborazzi-junit-rule` (`1.73.0`).
- **No blanket Compose `stabilityConfiguration` file** (§2.4). Stability is per screen.
- `TakiTheme` + `TakiColors` / `TakiSpacing` / `TakiShapes` / `TakiDimensions` /
  `TakiTypography` / `TakiIcons` / `TakiMotion` under `org.moire.ultrasonic.ui.theme`,
  transcribed 1:1 per §5.2. `TakiTokensTest` reads `colors.xml` / `dimens.xml` and fails on
  drift; `TakiTypographyTest` locks the six role values.
- `PlaybackUiStateHolder` + `PlayerUiState` / `PlaybackPhase` / `PlaybackProgress` under
  `org.moire.ultrasonic.ui.playback`, read projection only (§2.3.1), registered
  `single { PlaybackUiStateHolder(get()) }` in `mediaPlayerModule`. No edits to
  `MediaPlayerManager` / `PlaybackService` / `RxBus`. Turbine tests cover mapping + command
  forwarding + "no interaction on construction". Consumes the non-throttled
  `RxBus.playerStateObservable` for now (deliberate — synchronous, testable).
- Leaf primitives (§8 step 1 first set): `TakiArtwork`, `TakiIconButton`,
  `TakiSectionHeader`, `LibraryBrowseRow` under `org.moire.ultrasonic.ui.components`.
- Roborazzi + Compose-UI-test harness proven on the primitives; 4 goldens committed under
  `ultrasonic/src/test/screenshots/`. `TakiIconButtonComposeTest` proves `createComposeRule`.
- JVM `TestNavHostController` nav-check: `ComposeNavHostHarnessTest` inflates the real
  `navigation_graph.xml`. **No `androidTest` source set, no emulator CI.**
- Architecture guards: `ArchitectureGuardTest` (source-text scan) — no `androidx.media3.*`
  under `ui`, no `MaterialTheme` import outside `ui/theme`, no raw `Color(0x…)` / `.dp` / `.sp`
  outside `ui/theme` (escape hatch: line ends `// taki-raw-ok`). Simple by design; no Konsist.
- Test-environment changes (§6.6): `includeAndroidResources = true` +
  `robolectric.properties` (`sdk=35`, `application=android.app.Application`). Full pre-existing
  suite (271 tests) re-run green.
- Verified: `:ultrasonic:compileDebugKotlin`, `:ultrasonic:testDebugUnitTest`,
  `:ultrasonic:assembleDebug`, `:ultrasonic:lintDebug` ("no new issues"), Roborazzi
  record + verify, `-Pqc` ktlint (main clean) / detekt (`ui/**` clean). No Pixel run — no
  user-visible screen.

Justification: every downstream step depends on the theme, the playback-state seam and the test
harness. De-risking here is free because nothing ships.

### Step 1 — Leaf primitives (V2 §15), built in isolation

`TakiArtwork`, `TakiSectionHeader`, `TakiIconButton`, `TakiFilterChip`, `EmptyState`,
`ErrorState`, `AlbumShelfItem` (104 / 140 variants), `LibraryBrowseRow` (56 dp),
`LibraryPrimaryCard` (2-col 184 × 80), `FeaturedMixCard` (156 dp), `TakiScaffold` (content
background + insets; **not** the mini-player/nav — those stay Activity-owned).

Each ships with previews + a Roborazzi baseline + a11y semantics + a Compose-UI test. **No
screen swapped.** Low risk: pure rendering, no state, no navigation, no playback.

### Step 2 — Home (first full screen)

**Status: DONE (issue #10 phase 1, committed to `develop`).** Home is migrated and the
floating playback/navigation shell (mini-player + bottom nav as translucent overlays with
dynamic bottom insets) landed with it. Pixel 7 validated end to end; automated checks green.
What landed:

- `HomeFragment` is now a thin `ComposeView` host (`DisposeOnViewTreeLifecycleDestroyed`,
  `TakiTheme`), owning only the `NavController` and `MediaPlayerManager` command callbacks
  (`HomeActions`). The `homeFragment` id / args / actions / back stack / Activity chrome are
  unchanged.
- `HomeViewModel` refactored from 6 `MutableLiveData` to one
  `StateFlow<HomeUiState>` (`@Immutable`, `ImmutableList` fields, `HomeAlbumUi` /
  `FeaturedMixUi` UI models mapped off-main). Freshness window and per-shelf error swallowing
  kept verbatim. Raw mix tracks stay a `@Volatile var` for the Fragment's playback command,
  not in UI state.
- New primitives: `TakiScaffold`, `TakiFilterChip`, `AlbumShelfItem`, `FeaturedMixCard`,
  `EmptyState`. New: `imageloader/ComposeArtwork.kt` (`Album`/`Track` → `CoverArtRequest` for
  Coil `AsyncImage`). Tokens `artwork_shelf_compact` (104), `featured_card_height` (156),
  `featured_card_artwork` (124) added to `dimens.xml` + `TakiDimensions` in lockstep.
- Canonical V2 §10 composition: header → featured daily-mix card → quiet quick-access chips →
  Recently Played (104 shelf) → Liked / Recently Added / Discover / Most Played (140 shelves).
  Pull-to-refresh preserved via `PullToRefreshBox`; cold-load skeleton; centred `EmptyState`.
- 34 new tests (state / mapping / VM / Compose-UI / 5 Roborazzi goldens / nav). Deleted
  `home_fragment.xml`, `home_shortcut_item.xml`, `HomeShortcutDelegate`,
  `bg_home_shortcut_item.xml`, `Widget.Taki.NeutralChip`. `HomeAlbumDelegate` +
  `home_carousel_item.xml` kept - still used by `ArtistDetailFragment` (deletable at step 5).
- Pixel 7 on-device review passed (real library, real artwork via Coil, scroll / album nav +
  scroll restore / mix play / overflow / tab switch / pull-to-refresh; no jank). One fix
  during review: the `showLibraryHub` popup anchor (`HomeFragment` FrameLayout + top-end
  anchor view; `PopupMenu` gravity END).
- **Polish pass (V2 sections 7.3 / 13 / 14):** artwork-derived atmospheric wash behind the
  Daily-mix card (`TakiAtmosphericSurface`: ~96px Coil decode + 1.15x saturation + 28dp blur +
  a continuous 0.78 -> 0.50 -> 0.24 horizontal dark scrim, foreground artwork stays sharp);
  mini-player and bottom nav given translucent floating-surface tone + a 1dp tonal edge
  (`TakiFloatingSurface` in Compose for step 6; baked `taki_surface_floating` /
  `taki_surface_low_floating` / `taki_edge_highlight` for the current View chrome). New
  `TakiAtmosphere` token object. Shelves, cards and rows stay flat.
- **Floating mini-player (shell change):** the mini-player moved from a reserved LinearLayout
  row into an overlay inside `nav_host_container` (bottom-gravity, `mini_player_edge_margin`
  inset, `NavigationActivity` manages its bottom margin / nav-bar inset). The nav host now
  fills the space above the (still-persistent) bottom nav, so content scrolls *behind* the
  mini-player. It rests optically centred in the band above the nav: `mini_player_edge_margin`
  = 16dp is the screen-edge inset *and* the gap above the bottom nav, and
  `content_inset_floating_chrome` (96dp = 16 + 64 + 16) is the bottom padding a scrollable
  screen adds so its last item clears the overlay with the same 16dp gap (Compose
  `HomeScreen` contentPadding; the shared
  `list_parts_recycler` + `primary`/`artist_detail`/`search`/`select_genre` layouts; View
  fragments that ask get it from `getContentBottomInset()`). Transport icons recede to
  neutral (prev/next gray, play ivory); green stays only on the 2dp progress line.
  Transport row uses `baselineAligned="false"` + `paddingHorizontal=space_md` (12dp) so
  artwork, the title/artist block and all three controls share the pill's centre line.
  `navigation-compose` not added; nav ids / back stack / Fragment-Compose coexistence
  unchanged. Pixel 7 verified (uiautomator bounds): 16dp L/R margin symmetric, 16dp above
  the bottom nav, 16dp below content, every internal element centred on y-mid; content
  visibly scrolls behind the translucent surface, last item clears the overlay on every
  list checked, tab/back transitions clean, overflow popup still anchors top-end, controls
  + tap-to-open-Now-Playing intact. Frame stats unchanged by the re-centre: realistic Home
  scroll ~3.4% janky (95th 26ms). GPU 95th 5ms even under aggressive flinging, so the
  floating surface adds no compositing cost (no backdrop blur). One art-less-track path
  not reproducible on this library (size-only edit, unchanged behaviour).
- **Floating bottom nav (shell change):** the bottom nav moved from a LinearLayout row into
  the same `nav_host_container` overlay (bottom-gravity, below the mini-player in z-order),
  so the nav host fills the whole screen and content scrolls *behind the nav too*. Its
  translucency now actually reads. Surface bumped to ~0.93 (`taki_surface_low_floating`
  `0xED`, `FLOATING_SURFACE_ALPHA`) so its small labels stay legible over artwork - the
  lower, more grounded floating layer vs the mini-player's 0.87; 1px `taki_divider` (~5%)
  top hairline; no shadow/border/blur. M3 sizes the nav itself (80dp labelled here), so the
  insets read its *measured* height (`bottomNavFootprintPx`, re-applied on an
  `OnLayoutChangeListener`) rather than a constant. `getContentBottomInset()` /
  `computeContentBottomInset()` now return the live stack below the content -
  `bottomNavFootprint + 96` (nav + mini), `+ 16` (nav only), `navBar + 96` (mini only),
  `navBar` (neither) - exposed as `contentBottomInset: StateFlow<Int>`. Scrollable screens
  self-inset off it: `bindFloatingChromeInset(owner, scrollView)` (a lifecycle-scoped
  `updatePadding` collector) in `MultiListFragment` (covers album/artist/track/downloads/
  collection-detail/search), `MainFragment`, `ArtistDetailFragment`, `SelectGenreFragment`,
  `CollectionListFragment`, `CreatePlaylistFragment` (whole root - it has a fixed bottom
  bar); Compose `HomeScreen` takes a `bottomContentInset` param fed from the same flow;
  `PlaylistsFragment` already consumed `getContentBottomInset()`. The `content_inset_
  floating_chrome` dimen stays 96 as the XML pre-layout fallback. Pixel 7 verified
  (uiautomator bounds): nav [0,2063]-[1080,2400] = 80dp content + 48dp nav-bar, mini-player
  bottom at 2021 = exactly 16dp above the nav (no overlap), nav host fills to 2400; Home/
  Library/Search artwork visibly scrolls behind the nav with labels still legible, last item
  clears in both nav-only and nav+mini states, inset shrinks when playback stops, secondary
  (nav-hidden) + Settings-while-playing paths unaffected, popup anchoring / tab / back
  intact. Realistic Home scroll 3.0% janky (95th 23ms, 4 missed vsync / 972), tab switching
  0.9%; translucent nav over content adds no measurable cost.

### Step 3 — Library (`MainFragment` + browse rows)

Static navigation rows + a few `*ListModel`-backed lists. Pure navigation, **no playback
state**. Reuses `LibraryBrowseRow` / `LibraryPrimaryCard` from step 1. Exercises the
`libraryOnlyDestination` chrome branch in `NavigationActivity` (unchanged — still id-based).

### Step 4 — Search

One text input + result lists. No `RxBus`, no queue. Slightly trickier: IME handling and the
Activity's "hide bottom-nav + mini-player while search IME is up" logic — that logic is
**Activity-side and destination-id + `imeVisible` based**, so it keeps working; the Compose
screen just needs a normal `TextField` + `WindowInsets.ime` awareness. Recent-search
suggestions and `ACTION_SEARCH` intent routing unchanged.

### Step 5 — Album / collection / artist detail

`TrackCollectionFragment` (album mode), `CollectionDetailFragment`, `ArtistDetailFragment`.
**First screens with real playback *actions***: play, play next, add to queue, shuffle, star
(via `RatingManager`), plus per-row download state from `RxBus.trackDownloadStateObservable`.
Exercises `PlaybackUiStateHolder` reads + `MediaPlayerManager` command callbacks +
`ContextMenuUtil` overflow parity (issue #16) + the `DownloadUiState` map pattern (§9).
Deliberately **before** the mini-player / Now Playing so the playback-facing plumbing is proven
on screens that degrade gracefully if something is wrong.

### Step 6 — Mini-player integration

Convert `NowPlayingFragment` **contents** to a `ComposeView` + `MiniPlayer` composable on
`PlaybackUiStateHolder` (§4.2). `NavigationActivity` container / visibility / insets untouched.
Small surface, high exposure (visible on most destinations) — do it once detail screens have
shaken out the state mapper.

### Step 7 — Now Playing (`PlayerFragment`, ~1419 lines) — LAST

Most complex surface in the app: queue list with drag-reorder (`moveItemInPlaylist`), seek bar
with progress animation, repeat / shuffle, sleep timer, Up Next / Lyrics / About panel, swipe
gestures, artwork crossfade, and essentially every `MediaPlayerManager` method. Migrate only
after state-read, command, list-mutation, a11y and screenshot patterns are all proven
elsewhere.

**Sanctioned exception to §1.1:** if a single-PR rewrite is too risky, keep the XML
`PlayerFragment` shell and land sub-sections as nested `ComposeView`s in sequence — transport
row → progress → secondary actions → Up Next/Lyrics/About panel → queue list — collapsing to a
single root `ComposeView` at the end. This is the only screen where in-place hybrid hosting is
allowed, and only transiently within its own milestone.

### Out of scope for #10

Settings, Equalizer, `ServerSelectorFragment`, `EditServerFragment`, About, legacy
`PlaylistsFragment` / `SelectGenreFragment` / `LyricsFragment`. Leave as XML. Migrate
opportunistically much later or not at all — they are not music-playback surfaces and carry no
architectural lessons.

### Order summary

```
#9  0. theme/tokens + PlaybackUiStateHolder + test harness   (no UI change)
#10 1. leaf primitives (isolation)
    2. Home
    3. Library
    4. Search
    5. album / collection / artist detail
    6. mini-player
    7. Now Playing              (last; may be internally staged)
```

---

## 9. Risk register

| # | Risk | Why it exists | Mitigation |
|---|---|---|---|
| R1 | **MediaController / session lifecycle** — a screen tries to hold or subscribe a `MediaController`; recomposition / new coroutine scopes over-subscribe or leak it. | Compose makes it easy to spin up scopes and effects per composition. | Hard rule: no Media3 in `ui.*` (import ban, §5.4). Only `MediaPlayerManager` (process singleton) + one app-scoped `PlaybackUiStateHolder` subscriber. The existing rebuild-on-disconnect logic (#18) is service-side and unaffected. |
| R2 | **Playback-state propagation** — the RxBus→Compose bridge drops the replayed last value, or double-emits on recomposition, or leaks the subscription. | Rx `replay(1).autoConnect` semantics + Compose collection lifecycle mismatch. | Bridge once, app-scoped, `stateIn(SharingStarted.WhileSubscribed)`. Consume the `throttled*` (300 ms) variants for mini-player / lists. `collectAsStateWithLifecycle`. Mapper unit test asserts replay + throttle behavior. |
| R3 | **Queue mutation** — Compose `LazyColumn` drag-reorder desyncs from the authoritative Media3 timeline / shuffle order / `MAX_QUEUE_SIZE` cap. | Optimistic local list vs `MediaPlayerManager.moveItemInPlaylist` / `removeFromPlaylist` as the real source of truth. | Now Playing is **last**. Queue UI renders **from** `PlayerUiState.queue` (authoritative), sends move/remove as **commands**, never treats a local list as truth; reconciles on the next emission. Reuse `IncompleteTrackRemovalTest`-style coverage; add a reorder round-trip test. |
| R4 | **Offline / download state** — per-row badges come from a stream of single-track updates; a naive Compose list re-renders the whole list per update or shows stale badges. | `RxBus.trackDownloadStateObservable` emits one `TrackDownloadState` at a time; `DownloadService.getDownloadState` is a point lookup. | `PlaybackUiStateHolder.downloads` folds updates into `DownloadUiState.byTrackId: Map<String, _>`; composable reads `map[track.id]`; `LazyColumn` keyed by `track.id` → only the changed row recomposes. Online→Offline queue-trim (issue #2) is service-side, unchanged. |
| R5 | **Process death / restoration** — Compose screen state lost, or a screen assumes ViewModel data survived when only `SavedStateHandle` did; interaction with `PlaybackStateSerializer` restore + `NavigationActivity` re-create. | `ComposeView` compositions die with the fragment view; only `rememberSaveable` + `SavedStateHandle` persist. | ViewModels already reload from `MusicService` / cache on re-create — keep that. `rememberSaveable` only for view state (scroll, expanded). Screen args via Safe Args / `SavedStateHandle`. Add "rotate + don't-keep-activities" to the per-screen checklist (§6.5.8). |
| R6 | **Mixed navigation stacks** — half the graph Compose-hosted, half XML, one `NavController`; back-stack / tab-reselect / up-navigation edge cases (already a documented past-breakage area in `NavigationActivity`). | Two UI toolkits behind one nav host. | **No `navigation-compose`.** Every destination stays a `Fragment` with unchanged id / args / actions, so `switchToBottomNavTab`, `popBackStack`, `appBarConfiguration`, `destinationChangedListener` are untouched. Instrumented smoke test over a mixed stack (§6.4) + the tab-root regression test. |
| R7 | **Performance / jank** — unskippable recompositions from unstable `Track` / `Album` (Room `@Entity`, `var` fields); whole-list re-emit on a single change; artwork decode on the main thread. | Domain models double as mutable Room entities; RxBus emits whole lists. | **No blanket `stabilityConfiguration`** (§2.4) — potentially mutable domain types are not declared globally stable. Instead, per-screen explicit `@Immutable` `XxxUiState` / row models (`val`s of stable types, `ImmutableList` fields) mapped in the ViewModel; composables never read domain objects field-by-field. `LazyColumn` keys; `derivedStateOf` for progress; hoisted state. Coil `AsyncImage` (off-main by default). Roborazzi + a manual Pixel 7 scroll-jank pass. Keep throttled observables. Consider a Baseline Profile after Now Playing. |
| R8 | **Accessibility regression** — Compose semantics differ from View `contentDescription`; icon-only transport, liked / shuffle / repeat state, custom seek actions, long Spanish strings, `fontScale ≥ 1.30` (V2 §19). | Semantics are opt-in and easy to under-specify. | Every V2 §15 primitive ships with `contentDescription` / `stateDescription` / `Role` / seek `semantics`. Compose-UI test asserts semantics per screen at 3 font scales. Manual TalkBack pass + long-string pass in §6.5. |
| R9 | **Build toolchain fragility** — adding the Compose compiler plugin worsens the known OneDrive ↔ Gradle snapshot failures. | Repo lives in a OneDrive-synced folder; generated files become cloud placeholders. | Document the `build/`-wipe recovery in `HANDOFF.md`; recommend excluding `build/` from OneDrive sync (or moving the repo out). Pin exact Compose BOM + compiler versions in `libs.versions.toml`. |
| R10 | **Scope creep into playback refactor** — the temptation to "clean up" `MediaPlayerManager` or `RxBus` while adding the Compose seam. | The seam touches the boundary of a large, fragile class. | Explicit non-goal (this doc's header, issue #8 constraints). `PlaybackUiStateHolder` is **additive** — a read projection + command pass-through. Zero edits to `MediaPlayerManager` / `PlaybackService` / `PlaybackStateSerializer` in #9–#10. |

---

## 10. Summary

### Architecture chosen

- **Compose-in-Fragment.** Every screen stays a `Fragment` node in the existing flat
  `navigation_graph.xml` (unchanged ids / arguments / actions); a migrated screen's whole view
  is one root `ComposeView` with `DisposeOnViewTreeLifecycleDestroyed`. No `navigation-compose`, no
  Compose `NavHost`, no Activity rewrite, no new Gradle module.
- **Three-tier state.** Tier 1 runtime/playback (`MediaPlayerManager` + `service.*` +
  `RxBus`) is untouched and Media3 stays out of UI code. Tier 2 screen state is the existing
  `model.*` ViewModels, upgraded internally from `LiveData` lists to one immutable
  `StateFlow<XxxUiState>`. Tier 3 is a **new additive `PlaybackUiStateHolder`** — the single
  app-scoped read projection of player/queue/download state for Compose, with commands passing
  straight back through `MediaPlayerManager`.
- **`TakiTheme`** mirrors `colors.xml` / `themes.xml` / `dimens.xml` / `type.xml` 1:1;
  `TAKI_DESIGN_SYSTEM_V2.md` governs composition/geometry. Lint/Konsist guards ban raw
  color/dp/sp and stray `MaterialTheme` / Media3 imports in `ui.*`. **No blanket Compose
  stability configuration** — stability is per-screen explicit `@Immutable` UI models.
- **Test harness** (Roborazzi screenshots + Compose-UI tests under Robolectric + ViewModel
  state tests with `turbine` + a JVM `TestNavHostController` nav-check) is built in #9 and is
  the gate for #10. **No emulator / `androidTest` CI job in #9** — deferred to issue #10 step 2.

### Key boundaries

| Boundary | Rule |
|---|---|
| Composable ↔ navigation | Only the host Fragment calls `findNavController()`; composables get lambdas or emit typed `NavTarget` events. |
| Composable ↔ playback | Only via `PlaybackUiStateHolder` (read projection, one-way) + command lambdas → `MediaPlayerManager` (write). No Media3 types in `ui.*`. No optimistic mutation of the holder. |
| Composable ↔ domain data | Composables render per-screen `@Immutable` UI models, not raw `Track` / `Album`; no blanket stability config. |
| Fragment ↔ Compose | Screen is fully XML or fully Compose; one root `ComposeView`. Now Playing is the only sanctioned transient hybrid. |
| Mini-player | Stays an Activity-owned `FragmentContainerView` sibling of the nav host; only its contents become Compose. Never a per-screen `Scaffold` bottom bar. |
| Design tokens | XML values are the source of truth during migration; added values land in XML **and** Compose together. |
| Legacy code | Old Fragment + layout + screen-only adapters deleted in the **same PR** as the Compose screen, after the §6.5 checklist. |

### Migration order

theme/tokens + playback seam + test harness (#9) → leaf primitives → Home → Library → Search →
album/collection/artist detail → mini-player → **Now Playing last**. Settings and the
Activity shell are out of scope.

### Major risks

MediaController lifecycle leaks (R1), RxBus→Compose bridge correctness (R2), queue-mutation
desync (R3), per-row download-badge fan-out (R4), process-death state loss (R5), mixed
Fragment/Compose back stack (R6), recomposition jank from unstable Room-entity models (R7),
accessibility regressions (R8), OneDrive/Gradle build fragility from the new compiler plugin
(R9), and scope creep into a playback refactor (R10). Full table in §9.

### Files changed by issue #8

- **Added:** `docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md` (this file) — architecture-of-record.
- **Edited:** `docs/README.md` — indexed this document under "Technical references" and added a
  paragraph clarifying that long-lived architecture/design records are the deliberate exception
  to "no plans kept here".
- **Edited:** `docs/design/TAKI_DESIGN_SYSTEM_V2.md` §22 — corrected the north-star image
  reference to its actual location `docs/assets/TAKI_VISUAL_NORTH_STAR.png` (file not moved).
- No source, build, or resource files touched. Documentation-only; repo-consistency checks
  only (link/reference integrity).

### Architectural decisions (finalized for #8 — do not re-open in #9 / #10)

These were the open questions at first draft; all are now decided:

1. **This document stays in `docs/technical/` as architecture-of-record.** It is a living
   record of how the Compose UI is built, updated in place, not retired when #10 closes —
   the same status as `TAKI_DESIGN_SYSTEM_V2.md` and `TAKI_GUIA_VISUAL_Y_LENGUAJE.md`.
   `docs/README.md` now states this exception explicitly.
2. **`app.cash.turbine` is approved — `testImplementation` only.** Added in #9 for
   `Flow` / `StateFlow` assertions; not permitted in any other Gradle configuration.
3. **No CI emulator / `androidTest` job in #9.** #9 verification = JVM unit tests + Compose UI
   tests under Robolectric + Roborazzi screenshot tests + manual Pixel 7 validation. Adding an
   `androidTest` source set and an emulator CI job is **revisited when issue #10 step 2 (Home)
   lands**, i.e. at the first real screen migration.
4. **Roborazzi is the screenshot-test tool.** JVM/Robolectric, no device, CI-diffable. Not
   Paparazzi, not an emulator-based tool.
5. **No blanket Compose stability configuration.** `org.moire.ultrasonic.domain.*` types are
   mutable Room entities and are **not** declared globally stable. Each screen defines explicit
   immutable `XxxUiState` / `@Immutable` row models mapped in its ViewModel; composables render
   those, never raw domain objects field-by-field (§2.4, R7).
6. **`PlaybackUiStateHolder` is a one-way read projection, never a second source of truth**
   (§2.3.1). State: `MediaPlayerManager / services → RxBus → PlaybackUiStateHolder → Compose`.
   Commands: `Compose → command callback → MediaPlayerManager` (bypassing the holder).
   Composables must not mutate the holder optimistically and reconcile later. The holder is
   additive — zero edits to tier-1 playback code.
7. **North-star reference path corrected** in `TAKI_DESIGN_SYSTEM_V2.md` to
   `docs/assets/TAKI_VISUAL_NORTH_STAR.png`. The file was not moved.

### Resolved during #9

- **Compose BOM = `2026.08.00`** (Compose UI / foundation 1.12.0, material3 1.4.0), pinned in
  `gradle/libs.versions.toml` alongside `lifecycleCompose = 2.11.0`,
  `kotlinxCollectionsImmutable = 0.4.0`, `turbine = 1.2.1`, `roborazzi = 1.73.0`, and the
  `composeCompiler` plugin (`version.ref = kotlin`, 2.4.10). Compatible with the Kotlin-bundled
  Compose compiler.
- **The JVM mixed-graph back-stack check uses `TestNavHostController` alone** (no
  `FragmentScenario` needed) — `ComposeNavHostHarnessTest` inflates the real
  `navigation_graph.xml` and drives navigate / `popBackStack` / tab-root pop. It relies on
  `androidx.navigation:navigation-testing` (test-only, same version as `navigation`), which #8
  had not listed but the harness requires.
- **Test-environment changes** landed with #9 — see §6.6 (`includeAndroidResources`,
  `robolectric.properties` with `sdk=35` + `application=android.app.Application`). Full existing
  suite re-run green.

### Still genuinely open

- Nothing blocking #10. Throttling policy for `PlaybackUiStateHolder` (it currently consumes
  the non-throttled `RxBus.playerStateObservable`) is revisited when the mini-player is
  migrated (§8 step 6) — a per-consumer decision, not an architecture one.
