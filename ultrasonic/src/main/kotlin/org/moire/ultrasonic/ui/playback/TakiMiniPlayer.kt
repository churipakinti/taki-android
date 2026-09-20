/*
 * TakiMiniPlayer.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playback

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiFloatingSurface
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests find the mini-player root and its 2dp progress line. */
const val MINI_PLAYER_TEST_TAG = "mini_player"
const val MINI_PLAYER_PROGRESS_TEST_TAG = "mini_player_progress"

/** How often the progress line re-reads the transport position while playing. Sub-pixel per
 *  step on a phone-width line even for a short track, so it reads as continuous. */
private const val PROGRESS_TICK_MS = 250L

/** The legacy `NowPlayingFragment.MIN_DISTANCE`: a horizontal move of more than this many raw
 *  pixels is a swipe (previous / next), anything smaller is a tap. */
const val MINI_PLAYER_MIN_SWIPE_PX = 30f

/** What a finished touch on the mini-player surface means. */
enum class MiniPlayerGesture { Previous, Next, OpenNowPlaying, None }

/**
 * The legacy `NowPlayingFragment.handleOnTouch` rule, unchanged: judged on the pointer-up
 * against the pointer-down. A horizontal move past [MINI_PLAYER_MIN_SWIPE_PX] is a swipe - to
 * the right (finger moves right) = previous, to the left = next; otherwise a vertical move past
 * the threshold does nothing (swiping up/down never dismissed the bar); otherwise it is a tap
 * that opens Now Playing. [dx]/[dy] are `down - up`, as the legacy code computed them.
 */
fun resolveMiniPlayerGesture(dx: Float, dy: Float): MiniPlayerGesture = when {
    abs(dx) > MINI_PLAYER_MIN_SWIPE_PX -> if (dx < 0) MiniPlayerGesture.Previous else MiniPlayerGesture.Next
    abs(dy) > MINI_PLAYER_MIN_SWIPE_PX -> MiniPlayerGesture.None
    else -> MiniPlayerGesture.OpenNowPlaying
}

/**
 * Commands the mini-player fires. Every one is implemented by the owning Activity against the
 * existing runtime (`PlaybackUiStateHolder` -> `MediaPlayerManager`, and the Activity's
 * `NavController`); the composable owns no playback, no navigation, no subscription.
 */
data class MiniPlayerActions(
    /** Tap on the bar (or the info column): open Now Playing (`playerFragment`). */
    val onOpenNowPlaying: () -> Unit,
    /** Tap on the cover: open the current track's album (legacy behaviour). */
    val onArtworkClick: () -> Unit,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
) {
    companion object {
        val Noop = MiniPlayerActions({}, {}, {}, {}, {})
    }
}

/**
 * The persistent floating mini-player (issue #10 phase 4I): a 64dp translucent surface with the
 * cover, a marquee title over the artist, previous / play-pause / next, and a 2dp progress line
 * along the top. A pure projection of [PlayerUiState] - it renders nothing while
 * [PlayerUiState.hasCurrentTrack] is false. The owning Activity decides *whether the bar is
 * shown at all* (destination, search IME, player state) and its screen-edge margins; this
 * composable never does.
 *
 * [progressProvider] is a point-in-time read of transport position/duration
 * (`PlaybackUiStateHolder.snapshotProgress`), polled while playing - never part of state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TakiMiniPlayer(
    state: PlayerUiState,
    actions: MiniPlayerActions,
    progressProvider: () -> PlaybackProgress,
    modifier: Modifier = Modifier,
) {
    if (!state.hasCurrentTrack) return

    val currentActions by rememberUpdatedState(actions)
    TakiFloatingSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.miniPlayerHeight)
            .testTag(MINI_PLAYER_TEST_TAG)
            .pointerInput(Unit) {
                // Children (buttons, cover) consume their own presses first, so this only sees
                // touches on the bare surface / info column - exactly the legacy root listener.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    val delta = down.position - up.position
                    when (resolveMiniPlayerGesture(delta.x, delta.y)) {
                        MiniPlayerGesture.Previous -> currentActions.onPrevious()
                        MiniPlayerGesture.Next -> currentActions.onNext()
                        MiniPlayerGesture.OpenNowPlaying -> currentActions.onOpenNowPlaying()
                        MiniPlayerGesture.None -> Unit
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TakiTheme.dimensions.miniPlayerHeight)
                .padding(horizontal = TakiTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Decorative for accessibility (legacy importantForAccessibility=no); still tappable.
            TakiArtwork(
                model = state.artworkModel,
                contentDescription = null,
                size = TakiTheme.dimensions.artworkMini,
                shape = TakiTheme.shapes.sm,
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .clickable(onClick = actions.onArtworkClick),
            )
            Spacer(Modifier.width(TakiTheme.spacing.md))
            MiniPlayerInfo(state, actions, Modifier.weight(1f))
            MiniPlayerTransport(state, actions)
        }
        MiniPlayerProgress(state, progressProvider, Modifier.align(Alignment.TopStart))
        // The 1dp tonal edge all round (legacy bg_now_playing stroke), above the content.
        Box(
            Modifier
                .matchParentSize()
                .border(TakiTheme.dimensions.borderThin, TakiTheme.colors.edgeHighlight, TakiTheme.shapes.md),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniPlayerInfo(state: PlayerUiState, actions: MiniPlayerActions, modifier: Modifier) {
    val openLabel = stringResource(R.string.button_bar_now_playing)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            // One announced control: "<title>, <artist>" + a "Now Playing" open action.
            onClick(label = openLabel) {
                actions.onOpenNowPlaying()
                true
            }
        },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.title.orEmpty(),
            style = TakiTheme.type.title,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
        )
        if (!state.artist.isNullOrEmpty()) {
            Text(
                text = state.artist,
                style = TakiTheme.type.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MiniPlayerTransport(state: PlayerUiState, actions: MiniPlayerActions) {
    TakiIconButton(
        onClick = actions.onPrevious,
        painter = painterResource(R.drawable.media_backward),
        contentDescription = stringResource(R.string.buttons_previous),
        iconSize = TakiTheme.dimensions.iconMd,
    )
    TakiIconButton(
        onClick = actions.onPlayPause,
        painter = painterResource(if (state.isPlaying) R.drawable.media_pause else R.drawable.media_start),
        // The legacy button kept a static "Play" description; announce the real action.
        contentDescription = stringResource(if (state.isPlaying) R.string.buttons_pause else R.string.buttons_play),
        iconSize = TakiTheme.dimensions.iconLg,
        tint = TakiTheme.colors.ivory,
    )
    TakiIconButton(
        onClick = actions.onNext,
        painter = painterResource(R.drawable.media_forward),
        contentDescription = stringResource(R.string.buttons_next),
        iconSize = TakiTheme.dimensions.iconMd,
    )
}

/**
 * The 2dp progress line (legacy `now_playing_progress`): visible only once a duration is known,
 * filled with the muted progress green over the faint divider track, re-read while playing and
 * frozen while paused. The fraction lives in a `State` read only in the draw phase, so a tick
 * redraws the line without recomposing anything.
 */
@Composable
private fun MiniPlayerProgress(
    state: PlayerUiState,
    progressProvider: () -> PlaybackProgress,
    modifier: Modifier,
) {
    val fraction = remember { mutableFloatStateOf(0f) }
    var hasDuration by remember { mutableStateOf(false) }
    val provider by rememberUpdatedState(progressProvider)

    LaunchedEffect(state.trackId, state.isPlaying, state.phase) {
        while (true) {
            val progress = provider()
            hasDuration = progress.durationMs > 0
            fraction.floatValue = if (progress.durationMs > 0) {
                (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
            } else {
                0f
            }
            if (!state.isPlaying || progress.durationMs <= 0) break
            delay(PROGRESS_TICK_MS)
        }
    }

    if (!hasDuration) return
    val track = TakiTheme.colors.divider
    val fill = TakiTheme.colors.progress
    Box(
        modifier
            .fillMaxWidth()
            .height(TakiTheme.spacing.xxs)
            .testTag(MINI_PLAYER_PROGRESS_TEST_TAG)
            .clearAndSetSemantics {}
            .drawBehind {
                drawRect(track, size = size)
                drawRect(fill, topLeft = Offset.Zero, size = Size(size.width * fraction.floatValue, size.height))
            },
    )
}
