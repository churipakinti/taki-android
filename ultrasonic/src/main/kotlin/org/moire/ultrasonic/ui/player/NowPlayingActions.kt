/*
 * NowPlayingActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

/** One item of the Now Playing overflow menu (legacy `R.menu.nowplaying`), issue #10 phase 4J.
 *  `JUKEBOX` is deliberately absent: the legacy menu item was unconditionally
 *  `isVisible = false` / `isEnabled = false` (dead code - see the phase 4J report), so it is
 *  not ported. */
enum class NowPlayingOverflowItem {
    GO_TO_ARTIST,
    GO_TO_ALBUM,
    SAVE_PLAYLIST,
    CLEAR_PLAYLIST,
    LYRICS,
    EQUALIZER,
    TOGGLE_SCREEN_ON,
}

/**
 * Commands the Now Playing screen fires (issue #10 phase 4J). Every one is implemented by the
 * host `PlayerFragment` against the existing runtime (`PlaybackUiStateHolder` ->
 * `MediaPlayerManager`, `RxBus.ratingSubmitter` for favourite, and the Fragment's own
 * `NavController` / dialogs / window flags); the screen itself owns no playback, no
 * navigation, no subscription - the same boundary [org.moire.ultrasonic.ui.playback
 * .MiniPlayerActions] and the Album/Artist Detail actions already establish.
 */
data class NowPlayingActions(
    val onBack: () -> Unit,
    val onPlayPause: () -> Unit,
    /** The legacy buffering-only "Stop" button, shown in place of Play/Pause. */
    val onStop: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    /** The previous/next buttons' press-and-hold repeat: rewind/fast-forward within the
     *  current track, rather than skip. */
    val onSeekBackRepeat: () -> Unit,
    val onSeekForwardRepeat: () -> Unit,
    /** The seek bar was dropped at this position (ms). */
    val onSeekTo: (positionMs: Int) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onToggleFavorite: () -> Unit,
    /** The title tap ("go to album") and the artist-line tap ("go to artist") - issue #16,
     *  carried over unchanged. */
    val onTitleClick: () -> Unit,
    val onArtistClick: () -> Unit,
    val onSavePlaylist: () -> Unit,
    val onLyrics: () -> Unit,
    /** Toggles the artwork/queue panel - the legacy `ViewFlipper` flip. */
    val onToggleQueue: () -> Unit,
    val onSleepTimer: () -> Unit,
    val onOverflowItem: (NowPlayingOverflowItem) -> Unit,
    /** A point read of whether the equalizer is currently available on this device
     *  (`EqualizerController.get()` LiveData), resolved once when the overflow menu opens -
     *  ephemeral, never part of [org.moire.ultrasonic.ui.playback.PlayerUiState]. */
    val equalizerAvailable: () -> Boolean,
    /** A point read of whether "keep screen on" is currently active, for the overflow menu's
     *  toggle label. */
    val keepScreenOnActive: () -> Boolean,
    val onArtworkSwipeNext: () -> Unit,
    val onArtworkSwipePrevious: () -> Unit,
    val onArtworkSwipeSeekForward: () -> Unit,
    val onArtworkSwipeSeekBack: () -> Unit,
) {
    companion object {
        val Noop = NowPlayingActions(
            onBack = {},
            onPlayPause = {},
            onStop = {},
            onPrevious = {},
            onNext = {},
            onSeekBackRepeat = {},
            onSeekForwardRepeat = {},
            onSeekTo = {},
            onToggleShuffle = {},
            onCycleRepeat = {},
            onToggleFavorite = {},
            onTitleClick = {},
            onArtistClick = {},
            onSavePlaylist = {},
            onLyrics = {},
            onToggleQueue = {},
            onSleepTimer = {},
            onOverflowItem = {},
            equalizerAvailable = { false },
            keepScreenOnActive = { false },
            onArtworkSwipeNext = {},
            onArtworkSwipePrevious = {},
            onArtworkSwipeSeekForward = {},
            onArtworkSwipeSeekBack = {},
        )
    }
}
