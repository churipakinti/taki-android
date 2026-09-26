/*
 * NowPlayingScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.playback.PlaybackPhase
import org.moire.ultrasonic.ui.playback.PlaybackProgress
import org.moire.ultrasonic.ui.playback.PlayerUiState
import org.moire.ultrasonic.ui.playback.RepeatMode
import org.moire.ultrasonic.ui.theme.TakiAtmosphere
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.Util

/** Lets tests find the artwork/queue panel, the seek bar and the overflow menu trigger. */
const val NOW_PLAYING_ARTWORK_TEST_TAG = "now_playing_artwork"
const val NOW_PLAYING_QUEUE_PANEL_TEST_TAG = "now_playing_queue_panel"
const val NOW_PLAYING_SEEK_TEST_TAG = "now_playing_seek"

/** The legacy `PlayerFragment.PERCENTAGE_OF_SCREEN_FOR_SWIPE`: both the fling distance and
 *  velocity thresholds are this fraction of (screen width + screen height). */
private const val SWIPE_PERCENT_OF_SCREEN = 0.05f
private const val INITIAL_REPEAT_DELAY_MS = 1000L
private const val REPEAT_INTERVAL_MS = 300L
private const val MILLIS_PER_MINUTE = 60_000L
private const val PERCENT_MAX = 100f

/** A finished touch on the hero artwork panel (legacy `PlayerFragment.onFling`). */
enum class PlayerFlingGesture { NEXT, PREVIOUS, SEEK_FORWARD, SEEK_BACK, NONE }

/**
 * The legacy `onFling` rule, unchanged: a horizontal fling past both the distance and velocity
 * thresholds skips (right-to-left = next, left-to-right = previous); a vertical one seeks
 * (top-to-bottom = +30s, bottom-to-top = -8s); anything smaller is not a gesture at all (a plain
 * tap is handled separately, by the artwork not being clickable itself, matching the legacy
 * "swipe-only" artwork panel). `start`/`end` are the gesture's first and last pointer positions.
 */
@Suppress("LongParameterList")
fun resolvePlayerFlingGesture(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    velocityX: Float,
    velocityY: Float,
    swipeDistancePx: Float,
    swipeVelocityPx: Float,
): PlayerFlingGesture {
    val absVelocityX = abs(velocityX)
    val absVelocityY = abs(velocityY)
    return when {
        startX - endX > swipeDistancePx && absVelocityX > swipeVelocityPx -> PlayerFlingGesture.NEXT
        endX - startX > swipeDistancePx && absVelocityX > swipeVelocityPx -> PlayerFlingGesture.PREVIOUS
        endY - startY > swipeDistancePx && absVelocityY > swipeVelocityPx -> PlayerFlingGesture.SEEK_FORWARD
        startY - endY > swipeDistancePx && absVelocityY > swipeVelocityPx -> PlayerFlingGesture.SEEK_BACK
        else -> PlayerFlingGesture.NONE
    }
}

/**
 * Now Playing (issue #10 phase 4J, redesigned in phase 4J2): Taki's main emotional playback
 * surface. A pure projection of [PlayerUiState] plus a [PlaybackProgress] snapshot and the sleep
 * timer's own state - no playback, navigation or subscription lives here; every action is
 * dispatched to the host `PlayerFragment` through [NowPlayingActions].
 *
 * Phase 4J2 turned the screen into one continuous artwork-first composition instead of
 * "artwork + opaque control panel": a subtle top bar, a dominant hero artwork (or the legacy
 * embedded queue view, toggled by [showQueue]), and metadata/seek/transport sitting directly on
 * the full-bleed artwork-derived atmosphere - no card, no boxed groups. Every command and test
 * tag from phase 4J is unchanged; this is a visual pass only.
 *
 * [queueContent] hosts the legacy `current_playlist.xml` queue (drag-reorder, swipe-to-delete,
 * tap-to-play) unchanged, via an `AndroidView` the Fragment builds - deliberately not
 * reimplemented in Compose (issue #10 phase 4J scope: parity first, no new queue UI).
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    progress: PlaybackProgress,
    sleepTimerState: SleepTimerState,
    showQueue: Boolean,
    actions: NowPlayingActions,
    queueContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    TakiScaffold(modifier = modifier) {
        NowPlayingAtmosphere(model = state.artworkModelLarge)
        Column(Modifier.fillMaxSize()) {
            NowPlayingTopBar(state = state, actions = actions)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(targetState = showQueue, label = "now_playing_panel") { queueShown ->
                    if (queueShown) {
                        // Unlike the hero artwork, the queue is a dense scrollable text list (its
                        // own legacy header + rows, issue #10 phase 4J) rather than part of the
                        // artwork-first composition, so it keeps a quiet solid backing - the same
                        // canvas colour every other Compose list screen sits on - instead of
                        // floating directly on the atmosphere. That also gives it a clean seam
                        // against the transparent playback surface below rather than the two
                        // blending together with no visual boundary.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(TakiTheme.colors.black)
                                .testTag(NOW_PLAYING_QUEUE_PANEL_TEST_TAG),
                        ) {
                            queueContent()
                        }
                    } else {
                        NowPlayingHeroArtwork(state = state, actions = actions)
                    }
                }
            }
            NowPlayingPlaybackSurface(
                state = state,
                progress = progress,
                sleepTimerState = sleepTimerState,
                showQueue = showQueue,
                actions = actions,
            )
        }
    }
}

/**
 * The full-bleed artwork-derived backdrop (issue #10 phase 4J, re-tuned 4J2): the same technique
 * as [org.moire.ultrasonic.ui.components.TakiAtmosphericSurface] (a small cached Coil decode,
 * blurred and lightly saturated - no per-frame work) but a top-to-bottom scrim sized for one
 * vertical screen instead of a card. Recomposes only when [model] (the artwork request) changes,
 * never on a progress tick. A flat black canvas with no [model].
 */
@Composable
private fun NowPlayingAtmosphere(model: Any?) {
    if (model == null) return
    val colors = TakiTheme.colors
    val saturate = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(TakiAtmosphere.FEATURE_SATURATION) })
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(model)
            .size(TakiAtmosphere.ATMOSPHERE_SOURCE_PX)
            .crossfade(false)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = saturate,
        modifier = Modifier
            .fillMaxSize()
            .blur(TakiAtmosphere.featureBlurRadius)
            .alpha(TakiAtmosphere.NOW_PLAYING_ARTWORK_ALPHA),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to colors.black.copy(alpha = TakiAtmosphere.NOW_PLAYING_SCRIM_ALPHA_TOP),
                    1f to colors.black.copy(alpha = TakiAtmosphere.NOW_PLAYING_SCRIM_ALPHA_BOTTOM),
                ),
            ),
    )
}

/** A minimal back/overflow bar on the bare atmosphere (V2 section 6) - no title (it would
 *  duplicate the metadata below) and no Material app bar. Shares [TakiScreenHeader] with every
 *  other Compose sub-screen instead of hand-rolling its own row. */
@Composable
private fun NowPlayingTopBar(state: PlayerUiState, actions: NowPlayingActions) {
    var overflowExpanded by remember { mutableStateOf(false) }
    TakiScreenHeader(
        onBack = actions.onBack,
        backContentDescription = stringResource(R.string.player_back),
        actions = {
            Box {
                TakiIconButton(
                    onClick = { overflowExpanded = true },
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.player_options),
                    tint = TakiTheme.colors.ivory,
                )
                NowPlayingOverflowMenu(
                    expanded = overflowExpanded,
                    onDismiss = { overflowExpanded = false },
                    state = state,
                    actions = actions,
                )
            }
        },
    )
}

@Composable
private fun NowPlayingOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    state: PlayerUiState,
    actions: NowPlayingActions,
) {
    fun fire(item: NowPlayingOverflowItem) {
        onDismiss()
        actions.onOverflowItem(item)
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (state.hasCurrentTrack) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_menu_show_artist)) },
                onClick = { fire(NowPlayingOverflowItem.GO_TO_ARTIST) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_menu_show_album)) },
                onClick = { fire(NowPlayingOverflowItem.GO_TO_ALBUM) },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.download_menu_save)) },
            onClick = { fire(NowPlayingOverflowItem.SAVE_PLAYLIST) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.download_menu_clear_playlist)) },
            onClick = { fire(NowPlayingOverflowItem.CLEAR_PLAYLIST) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.download_menu_lyrics)) },
            onClick = { fire(NowPlayingOverflowItem.LYRICS) },
        )
        if (actions.equalizerAvailable()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_menu_equalizer)) },
                onClick = { fire(NowPlayingOverflowItem.EQUALIZER) },
            )
        }
        val screenOnLabel = if (actions.keepScreenOnActive()) {
            R.string.download_menu_screen_off
        } else {
            R.string.download_menu_screen_on
        }
        DropdownMenuItem(
            text = { Text(stringResource(screenOnLabel)) },
            onClick = { fire(NowPlayingOverflowItem.TOGGLE_SCREEN_ON) },
        )
    }
}

/**
 * The hero artwork (issue #10 phase 4J2): the screen's dominant visual element, centred with
 * generous negative space rather than reading as a header thumbnail. Responsive between
 * [org.moire.ultrasonic.ui.theme.TakiDimensions.albumHeroArtworkMin] and the Now Playing-specific
 * [org.moire.ultrasonic.ui.theme.TakiDimensions.nowPlayingHeroArtworkMax] - larger than the
 * shared detail-screen hero cap, since this is the app's one full-screen playback surface.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingHeroArtwork(state: PlayerUiState, actions: NowPlayingActions) {
    val currentActions by rememberUpdatedState(actions)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag(NOW_PLAYING_ARTWORK_TEST_TAG)
            .pointerInput(Unit) {
                val swipeThresholdPx = (size.width + size.height) * SWIPE_PERCENT_OF_SCREEN
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val stillDown = event.changes.any { it.pressed }
                        event.changes.forEach { change ->
                            tracker.addPointerInputChange(change)
                            last = change.position
                        }
                        if (!stillDown) break
                    }
                    val velocity = tracker.calculateVelocity()
                    val gesture = resolvePlayerFlingGesture(
                        startX = down.position.x,
                        startY = down.position.y,
                        endX = last.x,
                        endY = last.y,
                        velocityX = velocity.x,
                        velocityY = velocity.y,
                        swipeDistancePx = swipeThresholdPx,
                        swipeVelocityPx = swipeThresholdPx,
                    )
                    when (gesture) {
                        PlayerFlingGesture.NEXT -> currentActions.onArtworkSwipeNext()
                        PlayerFlingGesture.PREVIOUS -> currentActions.onArtworkSwipePrevious()
                        PlayerFlingGesture.SEEK_FORWARD -> currentActions.onArtworkSwipeSeekForward()
                        PlayerFlingGesture.SEEK_BACK -> currentActions.onArtworkSwipeSeekBack()
                        PlayerFlingGesture.NONE -> Unit
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val horizontalInset = TakiTheme.spacing.lg
        val side = minOf(maxWidth - horizontalInset * 2, maxHeight - TakiTheme.spacing.lg)
            .coerceIn(TakiTheme.dimensions.albumHeroArtworkMin, TakiTheme.dimensions.nowPlayingHeroArtworkMax)
        TakiArtwork(
            model = state.artworkModelLarge,
            contentDescription = stringResource(R.string.albumArt),
            size = side,
            shape = TakiTheme.shapes.md,
        )
    }
}

/**
 * Metadata, seek, transport and quick actions (issue #10 phase 4J2): sits directly on the
 * atmosphere with no enclosing card - spacing and typography carry the hierarchy instead of a
 * container. Always present (both with and without the queue shown), matching phase 4J.
 */
@Composable
private fun NowPlayingPlaybackSurface(
    state: PlayerUiState,
    progress: PlaybackProgress,
    sleepTimerState: SleepTimerState,
    showQueue: Boolean,
    actions: NowPlayingActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TakiTheme.spacing.xl)
            .padding(bottom = TakiTheme.spacing.xl),
    ) {
        NowPlayingMediaInfo(state = state, actions = actions)
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        NowPlayingSeekSection(state = state, progress = progress, actions = actions)
        Spacer(Modifier.height(TakiTheme.spacing.xl))
        NowPlayingTransportRow(state = state, actions = actions)
        Spacer(Modifier.height(TakiTheme.spacing.md))
        NowPlayingShuffleRepeatRow(state = state, actions = actions)
        Spacer(Modifier.height(TakiTheme.spacing.xl))
        NowPlayingQuickActionsRow(sleepTimerState = sleepTimerState, showQueue = showQueue, actions = actions)
    }
}

/** Title, artist and favourite as one identity group (V2 direction: favourite belongs to track
 *  identity, not a generic toolbar) - `title / artist ... heart`, unchanged from phase 4J. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingMediaInfo(state: PlayerUiState, actions: NowPlayingActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                text = state.title.orEmpty(),
                style = TakiTheme.type.hero,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .basicMarquee(iterations = Int.MAX_VALUE)
                    .clickable(role = Role.Button, onClick = actions.onTitleClick),
            )
            if (!state.artist.isNullOrEmpty()) {
                Spacer(Modifier.height(TakiTheme.spacing.xs))
                Text(
                    text = state.artist,
                    style = TakiTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .clickable(role = Role.Button, onClick = actions.onArtistClick),
                )
            }
        }
        TakiIconButton(
            onClick = actions.onToggleFavorite,
            painter = painterResource(
                if (state.isCurrentTrackLiked) R.drawable.rating_heart_full else R.drawable.rating_heart_hollow,
            ),
            contentDescription = stringResource(
                if (state.isCurrentTrackLiked) {
                    R.string.now_playing_unlike_description
                } else {
                    R.string.now_playing_like_description
                },
            ),
            selected = state.isCurrentTrackLiked,
            tint = if (state.isCurrentTrackLiked) TakiTheme.colors.liked else TakiTheme.colors.gray,
        )
    }
}

/** Hoists the drag-in-progress position and formats the elapsed/total labels; the actual seek
 *  surface is [NowPlayingSeekBar] (issue #10 phase 4J2 - replaces the Material3 `Slider` with a
 *  thin custom track so the screen never reads as an old-style Android SeekBar). */
@Composable
private fun NowPlayingSeekSection(state: PlayerUiState, progress: PlaybackProgress, actions: NowPlayingActions) {
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }
    val durationMs = progress.durationMs.coerceAtLeast(0L)
    val maxMs = durationMs.coerceAtLeast(1L).toFloat()
    val positionMs = (dragPositionMs ?: progress.positionMs.toFloat()).coerceIn(0f, maxMs)
    // Matches the legacy `progressBar.isEnabled = isPlaying || isJukeboxEnabled`: dragging a
    // paused, non-jukebox stream is not meaningful (nothing is buffering to seek within).
    val enabled = state.hasCurrentTrack && (state.isPlaying || state.isJukeboxEnabled)

    Column {
        NowPlayingSeekBar(
            positionMs = positionMs,
            maxMs = if (durationMs > 0) maxMs else 0f,
            bufferedPercent = if (state.hasCurrentTrack) progress.bufferedPercent else 0,
            enabled = enabled,
            onValueChange = { dragPositionMs = it },
            onValueChangeFinished = {
                actions.onSeekTo((dragPositionMs ?: positionMs).toInt())
                dragPositionMs = null
            },
        )
        Spacer(Modifier.height(TakiTheme.spacing.xxs))
        Row(Modifier.fillMaxWidth()) {
            val elapsed = if (state.hasCurrentTrack) {
                Util.formatTotalDuration((dragPositionMs?.toLong() ?: progress.positionMs), true)
            } else {
                stringResource(R.string.util_zero_time)
            }
            val total = if (state.hasCurrentTrack) {
                Util.formatTotalDuration(durationMs, true)
            } else {
                stringResource(R.string.util_no_time)
            }
            Text(text = elapsed, style = TakiTheme.type.caption)
            Spacer(Modifier.weight(1f))
            Text(text = total, style = TakiTheme.type.caption)
        }
    }
}

/**
 * A thin, restrained seek surface (V2 direction: no old-style Android SeekBar) drawn directly
 * rather than a Material3 `Slider`, so the visual track can stay a couple of dp thick regardless
 * of the platform's slider styling. The touch target is still a full [TakiTheme.dimensions
 * .touchTargetMin]-tall row - only the drawn line is thin. A single unified gesture ([Modifier
 * .nowPlayingSeekGesture], matching this file's existing hero-artwork / skip-button pointer
 * handling) drives both "tap to seek" and "drag to seek": the position follows the finger from
 * first contact to release, then [onValueChangeFinished] commits it. [Modifier
 * .nowPlayingSeekSemantics] wires `SemanticsActions.SetProgress` for accessibility services and
 * instrumented tests; [drawNowPlayingSeekTrack] paints the track/buffered/progress/thumb.
 */
@Suppress("LongParameterList")
@Composable
private fun NowPlayingSeekBar(
    positionMs: Float,
    maxMs: Float,
    bufferedPercent: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val trackColor = TakiTheme.colors.surface
    val bufferedColor = TakiTheme.colors.gray
    val activeColor = TakiTheme.colors.progress
    val trackHeight = TakiTheme.spacing.xxs
    val thumbDiameter = TakiTheme.dimensions.nowPlayingSeekThumb
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.touchTargetMin)
            .testTag(NOW_PLAYING_SEEK_TEST_TAG)
            .nowPlayingSeekSemantics(
                positionMs = positionMs,
                maxMs = maxMs,
                enabled = enabled,
                onValueChange = currentOnValueChange,
                onValueChangeFinished = currentOnValueChangeFinished,
            )
            .nowPlayingSeekGesture(
                enabled = enabled,
                maxMs = maxMs,
                onValueChange = currentOnValueChange,
                onValueChangeFinished = currentOnValueChangeFinished,
            )
            .drawBehind {
                drawNowPlayingSeekTrack(
                    positionMs = positionMs,
                    maxMs = maxMs,
                    bufferedPercent = bufferedPercent,
                    trackColor = trackColor,
                    bufferedColor = bufferedColor,
                    activeColor = activeColor,
                    trackHeightPx = trackHeight.toPx(),
                    thumbRadiusPx = thumbDiameter.toPx() / 2f,
                )
            },
    )
}

/** Wires `SemanticsActions.SetProgress` (accessibility services, instrumented tests) and the
 *  standard progress-bar range info onto the seek [Box][NowPlayingSeekBar]. */
@Suppress("LongParameterList")
private fun Modifier.nowPlayingSeekSemantics(
    positionMs: Float,
    maxMs: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
): Modifier = semantics(mergeDescendants = true) {
    if (!enabled) disabled()
    progressBarRangeInfo = ProgressBarRangeInfo(current = positionMs, range = 0f..maxMs.coerceAtLeast(1f))
    setProgress { targetValue ->
        if (!enabled || maxMs <= 0f) return@setProgress false
        onValueChange(targetValue.coerceIn(0f, maxMs))
        onValueChangeFinished()
        true
    }
}

/** A single gesture drives both "tap to seek" and "drag to seek": the position follows the
 *  finger from first contact to release, then [onValueChangeFinished] commits it. */
private fun Modifier.nowPlayingSeekGesture(
    enabled: Boolean,
    maxMs: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
): Modifier = pointerInput(enabled, maxMs) {
    if (!enabled || maxMs <= 0f) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown()
        val trackWidthPx = size.width.toFloat()
        fun valueAt(x: Float) = (x / trackWidthPx).coerceIn(0f, 1f) * maxMs
        onValueChange(valueAt(down.position.x))
        var pointerId = down.id
        var dragging = true
        while (dragging) {
            val eventChange = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
            if (eventChange == null || !eventChange.pressed) {
                dragging = false
            } else {
                eventChange.consume()
                onValueChange(valueAt(eventChange.position.x))
                pointerId = eventChange.id
            }
        }
        onValueChangeFinished()
    }
}

/** Background track, buffered-ahead segment, active progress and the restrained thumb - in that
 *  paint order, all as thin round-capped lines rather than a filled bar. */
@Suppress("LongParameterList")
private fun DrawScope.drawNowPlayingSeekTrack(
    positionMs: Float,
    maxMs: Float,
    bufferedPercent: Int,
    trackColor: Color,
    bufferedColor: Color,
    activeColor: Color,
    trackHeightPx: Float,
    thumbRadiusPx: Float,
) {
    val centerY = size.height / 2f
    val fraction = if (maxMs > 0f) (positionMs / maxMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = (bufferedPercent / PERCENT_MAX).coerceIn(0f, 1f)
    drawLine(
        color = trackColor,
        start = Offset(0f, centerY),
        end = Offset(size.width, centerY),
        strokeWidth = trackHeightPx,
        cap = StrokeCap.Round,
    )
    if (bufferedFraction > fraction) {
        drawLine(
            color = bufferedColor,
            start = Offset(0f, centerY),
            end = Offset(size.width * bufferedFraction, centerY),
            strokeWidth = trackHeightPx,
            cap = StrokeCap.Round,
        )
    }
    if (fraction > 0f) {
        drawLine(
            color = activeColor,
            start = Offset(0f, centerY),
            end = Offset(size.width * fraction, centerY),
            strokeWidth = trackHeightPx,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(color = activeColor, radius = thumbRadiusPx, center = Offset(size.width * fraction, centerY))
}

/** Previous / Play-Pause / Next (issue #10 phase 4J2): the most obvious interaction after the
 *  artwork, so it gets the widest spacing and the largest primary button on the screen - no
 *  enclosing card, just the three controls on the atmosphere. */
@Composable
private fun NowPlayingTransportRow(state: PlayerUiState, actions: NowPlayingActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowPlayingSkipButton(
            painter = painterResource(R.drawable.media_backward),
            contentDescription = stringResource(R.string.buttons_previous),
            enabled = state.canSeekToPrevious,
            onClick = actions.onPrevious,
            onRepeat = actions.onSeekBackRepeat,
        )
        NowPlayingPrimaryButton(state = state, actions = actions)
        NowPlayingSkipButton(
            painter = painterResource(R.drawable.media_forward),
            contentDescription = stringResource(R.string.buttons_next),
            enabled = state.canSeekToNext,
            onClick = actions.onNext,
            onRepeat = actions.onSeekForwardRepeat,
        )
    }
}

/** Shuffle and repeat (issue #10 phase 4J2): subordinate to the primary transport, so they move
 *  to their own quiet row below it instead of flanking Play/Pause. Selected state still reads
 *  clearly via [TakiIconButton]'s accent tint. */
@Composable
private fun NowPlayingShuffleRepeatRow(state: PlayerUiState, actions: NowPlayingActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TakiIconButton(
            onClick = actions.onToggleShuffle,
            painter = painterResource(R.drawable.media_shuffle),
            contentDescription = stringResource(R.string.buttons_shuffle),
            iconSize = TakiTheme.dimensions.iconSm,
            selected = state.isShuffleEnabled,
        )
        Spacer(Modifier.width(TakiTheme.spacing.xxl))
        val (icon, description) = repeatButtonIcon(state.repeatMode)
        TakiIconButton(
            onClick = actions.onCycleRepeat,
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            iconSize = TakiTheme.dimensions.iconSm,
            selected = state.repeatMode != RepeatMode.OFF,
        )
    }
}

private fun repeatButtonIcon(mode: RepeatMode): Pair<Int, Int> = when (mode) {
    RepeatMode.OFF -> R.drawable.media_repeat_off to R.string.download_repeat_off
    RepeatMode.ONE -> R.drawable.media_repeat_one to R.string.download_repeat_single
    RepeatMode.ALL -> R.drawable.media_repeat_all to R.string.download_repeat_all
}

@Composable
private fun NowPlayingPrimaryButton(state: PlayerUiState, actions: NowPlayingActions) {
    val icon: Int
    val description: String
    val onClick: () -> Unit
    when {
        state.phase == PlaybackPhase.Buffering -> {
            icon = R.drawable.media_stop
            description = stringResource(R.string.buttons_stop)
            onClick = actions.onStop
        }

        state.isPlaying -> {
            icon = R.drawable.media_pause
            description = stringResource(R.string.buttons_pause)
            onClick = actions.onPlayPause
        }

        else -> {
            icon = R.drawable.media_start
            description = stringResource(R.string.buttons_play)
            onClick = actions.onPlayPause
        }
    }
    Box(
        modifier = Modifier
            .size(TakiTheme.dimensions.nowPlayingPrimaryAction)
            .background(TakiTheme.colors.ivory, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = TakiTheme.colors.black,
            modifier = Modifier.size(TakiTheme.dimensions.iconLg),
        )
    }
}

/**
 * The legacy `AutoRepeatButton`: a plain tap fires [onClick] (skip); pressing and holding past
 * [INITIAL_REPEAT_DELAY_MS] instead fires [onRepeat] (rewind/fast-forward) every
 * [REPEAT_INTERVAL_MS] until release, and suppresses the click. A single `pointerInput` owns
 * the whole gesture - no competing `clickable` on the same node - with the tap action exposed
 * to accessibility services via semantics instead, so a screen reader's "activate" still skips
 * without needing to hold.
 */
@Composable
private fun NowPlayingSkipButton(
    painter: Painter,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onRepeat: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnRepeat by rememberUpdatedState(onRepeat)
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(TakiTheme.dimensions.touchTargetMin)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
                onClick(label = contentDescription) {
                    if (enabled) currentOnClick()
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    var repeated = false
                    val job = scope.launch {
                        delay(INITIAL_REPEAT_DELAY_MS)
                        while (true) {
                            repeated = true
                            currentOnRepeat()
                            delay(REPEAT_INTERVAL_MS)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                    if (!repeated) currentOnClick()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = if (enabled) TakiTheme.colors.ivory else TakiTheme.colors.gray,
            modifier = Modifier.size(TakiTheme.dimensions.iconLg),
        )
    }
}

/** Save / Lyrics / Queue / Sleep timer (issue #10 phase 4J2): reachable but visually quiet -
 *  small icons, centred, no labels, sitting last in the composition. */
@Composable
private fun NowPlayingQuickActionsRow(
    sleepTimerState: SleepTimerState,
    showQueue: Boolean,
    actions: NowPlayingActions,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TakiIconButton(
            onClick = actions.onSavePlaylist,
            painter = painterResource(R.drawable.ic_add_white),
            contentDescription = stringResource(R.string.download_menu_save),
            iconSize = TakiTheme.dimensions.iconSm,
        )
        Spacer(Modifier.width(TakiTheme.spacing.xl))
        TakiIconButton(
            onClick = actions.onLyrics,
            painter = painterResource(R.drawable.ic_library),
            contentDescription = stringResource(R.string.download_menu_lyrics),
            iconSize = TakiTheme.dimensions.iconSm,
        )
        Spacer(Modifier.width(TakiTheme.spacing.xl))
        TakiIconButton(
            onClick = actions.onToggleQueue,
            painter = painterResource(R.drawable.media_toggle_list),
            contentDescription = stringResource(R.string.buttons_queue),
            iconSize = TakiTheme.dimensions.iconSm,
            selected = showQueue,
        )
        Spacer(Modifier.width(TakiTheme.spacing.xl))
        TakiIconButton(
            onClick = actions.onSleepTimer,
            painter = painterResource(R.drawable.ic_sleep_timer),
            contentDescription = sleepTimerContentDescription(sleepTimerState),
            iconSize = TakiTheme.dimensions.iconSm,
            selected = sleepTimerState !is SleepTimerState.Off,
        )
    }
}

/** Ported verbatim from the legacy `PlayerFragment.sleepTimerContentDescription`. */
@Composable
private fun sleepTimerContentDescription(state: SleepTimerState): String = when (state) {
    SleepTimerState.Off -> stringResource(R.string.sleep_timer_title)
    is SleepTimerState.EndOfTrack -> stringResource(R.string.sleep_timer_title_end_of_song)
    is SleepTimerState.Duration -> {
        val remainingMinutes = ceilMinutes(state.remainingMs(SystemClock.elapsedRealtime()))
        val remaining = pluralStringResource(
            R.plurals.sleep_timer_remaining_short,
            remainingMinutes,
            remainingMinutes,
        )
        stringResource(R.string.sleep_timer_title_active, remaining)
    }
}

private fun ceilMinutes(remainingMs: Long): Int =
    ((remainingMs + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(1)
