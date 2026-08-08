# Handoff

This document exists so any AI agent (or human) picking up this project can understand the context, the vision, and how we work together — without re-deriving it from scratch by reading the whole chat history. Read this first. Then read `CHANGES.md` for the detailed, chronological log of what's actually been built and why (including bugs found and how they were diagnosed).

## What this is

A **private fork of Ultrasonic** (the open-source Android Subsonic/Navidrome/Airsonic client), cloned for the user's personal use. Not intended to be pushed to the public GitLab/GitHub upstream or shared. `origin` still points at `gitlab.com/ultrasonic/ultrasonic` — never push there.

## The vision

Turn Ultrasonic from a generic, feature-complete Subsonic client into a **music-only app built for intentional listening**, visually inspired by Spotify. Concretely, that means:

- **Music only.** Podcasts, Video, and Chat have been hidden from the UI (drawer + Home quick-access) because they don't belong in a "pure music player." Their code was deliberately *not* deleted — video support in particular is threaded through shared classes (`Track`, downloads) that music also depends on, so ripping it out is a much bigger, riskier job than hiding a menu entry. If asked to go further, that's a distinct, larger task — don't assume it's wanted.
- **Calm over algorithmic.** No fake personalization. Every "smart" feature (Mix, Discover, Recently Played) is built from data the Subsonic API already exposes (genre lists, play history, starred items) — never a recommendation engine. When in doubt, prefer showing the user their own listening history over inventing content.
- **Established visual language** (built up incrementally, screen by screen — see "Design system" below).

## How we work together

- **The user tests in Android Studio; this environment cannot.** There is no Android SDK here — no `gradlew`, no emulator, no way to see the app render or get a stack trace except by asking the user to paste one from Logcat. Every change is validated by (a) XML well-formedness checks (PowerShell `[xml]` parsing), (b) careful manual reading for Kotlin/type correctness, and (c) the user actually running the app and reporting back — often with a screenshot, sometimes with a crash log. **Never claim something "works" — say what you verified and what you couldn't.**
- **Iterative and screenshot-driven.** The user shares a screenshot, gives specific feedback ("this shadow looks like a floating circle", "the corners are too round"), and expects a concrete fix, not a discussion — unless the request is genuinely ambiguous or has a real design tradeoff, in which case ask first (see next point).
- **Ask before guessing on ambiguous or costly decisions.** Examples from this project: how to pick which artist/genre for the Mix, whether the Mix should regenerate every refresh or persist, how rounded corners should be, whether to hide or delete non-music features. Use targeted questions (2-4 options, not open-ended) rather than picking silently. For unambiguous, well-scoped asks, just implement — don't over-ask.
- **Reuse existing patterns aggressively.** This codebase has established idioms for almost everything: `RefreshableFragment` + `toastingExceptionHandler()` for loading/error state, `BaseAdapter`/MultiType `ItemViewDelegate` classes for RecyclerView rows, `SettingsDelegate` subclasses (`StringSetting`, `BooleanSetting`, etc.) for SharedPreferences-backed state, `NavigationGraphDirections`/`PlayerFragmentDirections` for Safe Args navigation. **Search for the existing pattern before inventing a new one** — several bugs in this project came from not doing that carefully enough (see the bug write-ups in `CHANGES.md`, e.g. the `navArgs()` crash from bypassing a Safe Args action).
- **No git commits or pushes without being explicitly asked.** For most of this project the user didn't want commits at all ("private app, probably won't commit"). Only commit when asked, as a single commit unless told otherwise, and never push anywhere without separate explicit instruction.
- **Keep `CHANGES.md` updated.** Every change gets logged there as it happens, grouped by topic, in Spanish (matching the conversation language), with file paths. Treat it as the project's real changelog — update it in the same turn you make the change, not as an afterthought.
- **Don't over-scope.** When a request implies a much bigger job than the rest of the conversation's pace (e.g. "apply the Home style to every screen" after just finishing Home itself), say so plainly and propose tackling it incrementally rather than silently doing a huge sweep.

## Design system (established incrementally — treat as the current baseline)

- **Surfaces:** neutral dark tones from the Material3 role system (`colorSurfaceContainerHigh` / `colorSurfaceContainerHighest`), not saturated/tonal colors like `colorSecondaryContainer`.
- **Corners:** deliberately more squared than stock Material3 — **4dp** on cards (carousel cards, shortcut grid cards), 16-20dp only on large floating panels (mini player, player control panel, drawer edge). This was an explicit user preference ("mi estilo, apenas suavizado el borde"), not a Material3 default.
- **Text hierarchy:** primary text (song/album/mix title) is **bold**; secondary text (artist, subtitles) is regular weight and muted (`colorOnSurfaceVariant`). Applied to Home's cards. The mini player's title is deliberately *not* bold — it was tuned down from bold earlier for being "too bold" relative to its small cover art, so don't naively re-apply the bold-title rule there without checking with the user.
- **Depth without native elevation on transparent icon buttons.** Android `elevation` draws a shadow from the view's *outline*, not its visible pixels — on a transparent-background `Widget.Material3.Button.IconButton`, that produces a floating disconnected circle (a real bug found and fixed here). Where a subtle shadow is wanted on an icon, it's hand-drawn: the icon is rendered twice in a `<layer-list>`, a dark low-alpha copy offset 1dp behind the normal one (see `media_*_shadow.xml`). Where a shadow is wanted on a whole card/panel (mini player, player control panel), use real `elevation` on a view that actually has an opaque/rounded background — that works fine.
- **Floating card pattern** (mini player): inset from screen edges via `layout_margin`, rounded corners via a dedicated background drawable (not a `MaterialCardView`, just a `<shape>`), slight transparency via `android:alpha` on the whole view (not a true blur — that's a bigger, unimplemented ask if ever wanted).
- **Minimal metadata on the Player screen.** Only title, artist, and playback position/duration are shown. Everything else (album, genre, bitrate, track-in-queue counter, total queue duration) was deliberately removed as clutter; tapping the title navigates to the album where full details live. If asked to add more info back, prefer a tap-through/secondary view over cramming it into the main screen.

## Current state (high level — `CHANGES.md` has the full detail, including every bug found)

1. **New Home screen**, now the app's start destination (`homeFragment` in the nav graph). Greeting, quick-access buttons, "Recently Played" shortcuts grid, a **daily-persisted genre Mix** (one playlist-style row, regenerates once per calendar day via `Settings.homeMix*` SharedPreferences keys — not a carousel, and not regenerated on every refresh), and album carousels: Favorites, Recently Added, Discover (renamed from "Random" — but that string rename is Home-only, the shared `main.albums_random` string used elsewhere, e.g. sort-order dropdowns, was deliberately left alone).
2. **Drawer & toolbar** modernized.
3. **Mini player** redesigned as a floating card with prev/play/next (was play/pause only).
4. **Full Player screen** trimmed to title/artist/progress; 5-star rating removed in favor of a single-line heart.
5. **Podcasts/Video/Chat hidden** from UI only.
6. Debug builds are labeled "ultrasonic-test".

## Pending / next steps

- **Apply the Home visual language to the rest of the app, one screen at a time** (explicitly agreed approach — not a big-bang rewrite). Not yet touched: Media Library (artist list), track/song lists (`TrackCollectionFragment`), Playlists, Search, Settings, Downloads, Bookmarks, Shares.
- `Settings.showNowPlayingDetails` (a Settings screen toggle for genre/year/bitrate in the Player) is now a no-op since those fields were removed from that screen. Left in place, not deleted — flagged for the user to decide whether to remove it or resurrect the info elsewhere.
- No commits had been made prior to this handoff being written; check `git log` for what's landed since.

## Key files

- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment/HomeFragment.kt` + `model/HomeViewModel.kt` — Home screen and its data/Mix logic.
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/adapters/Home*.kt` — MultiType delegates for Home's cards.
- `ultrasonic/src/main/res/layout/home_fragment.xml`, `home_*_item.xml` — Home's layouts.
- `ultrasonic/src/main/res/layout/now_playing.xml` + `fragment/NowPlayingFragment.kt` — mini player bar.
- `ultrasonic/src/main/res/layout/current_playing.xml`, `player_media_info.xml`, `player_slider.xml`, `media_buttons.xml` + `fragment/PlayerFragment.kt` — full player screen.
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/util/Settings.kt` / `SettingsDelegate.kt` — SharedPreferences-backed state pattern to reuse for any new persisted setting.
- `CHANGES.md` — the real changelog. Read it before assuming something hasn't been tried.
