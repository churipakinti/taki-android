/*
 * LyricsScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.theme.TakiAtmosphere
import org.moire.ultrasonic.ui.theme.TakiTheme

const val LYRICS_LIST_TEST_TAG = "lyrics_list"
const val LYRICS_ACTIVE_LINE_TEST_TAG = "lyrics_active_line"
const val LYRICS_RESUME_TEST_TAG = "lyrics_resume"

private const val POSITION_POLL_MS = 250L
private const val READING_ZONE_FRACTION = 0.3f
private const val TAIL_FRACTION = 0.5f
private const val SYNCED_NEAR_ALPHA = 0.65f
private const val SYNCED_FAR_ALPHA = 0.35f
private const val SYNCED_NEAR_DISTANCE = 2
private const val SYNCED_ACTIVE_WASH_ALPHA = 0.10f
private const val ACCENT_RULE_ALPHA = 0.4f
private const val PLAIN_FONT_SP = 19 // taki-raw-ok: lyrics body reading size
private const val PLAIN_LINE_SP = 32 // taki-raw-ok: lyrics body reading size
private const val SYNCED_FONT_SP = 24 // taki-raw-ok: lyrics body reading size
private const val SYNCED_LINE_SP = 34 // taki-raw-ok: lyrics body reading size
private const val SYNCED_ACTIVE_FONT_SP = 27 // taki-raw-ok: current line is dominant, one step up
private const val SYNCED_ACTIVE_LINE_SP = 37 // taki-raw-ok: current line is dominant, one step up
private val SyncedMaxWidth = 480.dp // taki-raw-ok: comfortable centered reading measure

/**
 * Lyrics (issue #10 phase 4K3, given the Taki atmosphere in phase 4K4): a calm reading surface.
 * A minimal Taki header, the track's title/artist, then the lyrics - [LyricsContent.Plain] as
 * generously spaced static text, [LyricsContent.Synced] with the current line emphasised and
 * followed as playback moves. It is a pure projection of [state]; the only things it reads
 * outside it are [positionMs] (polled at a relaxed cadence and only while synced lyrics are
 * shown, so playback ticks never recompose the whole list - only the active index changes, once
 * per line) and [artworkModel] (the current track's cover, for [LyricsAtmosphere] only - never
 * refetched or recomposed on a tick, only when the artwork itself changes).
 *
 * Unlike the legacy screen, there is no static synced/unsynced badge: the emphasised, following
 * active line already tells synced and plain lyrics apart, so the badge would be redundant chrome.
 */
@Composable
fun LyricsScreen(
    state: LyricsUiState,
    positionMs: () -> Long,
    actions: LyricsActions,
    modifier: Modifier = Modifier,
    artworkModel: Any? = null,
    bottomContentInset: Dp = TakiTheme.dimensions.contentInsetFloatingChrome,
) {
    TakiScaffold(modifier = modifier) {
        LyricsAtmosphere(model = artworkModel)
        Column(Modifier.fillMaxSize()) {
            TakiScreenHeader(onBack = actions.onBack, title = stringResource(R.string.download_menu_lyrics))
            TrackIdentity(state)
            when (val content = state.content) {
                LyricsContent.Loading -> LoadingBody()
                LyricsContent.Empty -> EmptyState(
                    icon = painterResource(R.drawable.ic_np_lyrics),
                    title = stringResource(R.string.lyrics_nomatch),
                    modifier = Modifier.fillMaxSize(),
                )
                LyricsContent.Error -> EmptyState(
                    icon = painterResource(R.drawable.ic_np_lyrics),
                    title = stringResource(R.string.lyrics_error),
                    actionLabel = stringResource(R.string.lyrics_retry),
                    onAction = actions.onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
                is LyricsContent.Plain -> PlainBody(state.trackId, content, bottomContentInset)
                is LyricsContent.Synced ->
                    SyncedBody(state.trackId, content, positionMs, actions.onSeek, bottomContentInset)
            }
        }
    }
}

/**
 * Lyrics' backdrop (issue #10 phase 4K4): the same small-blurred-wash technique as
 * [org.moire.ultrasonic.ui.player.NowPlayingScreen]'s atmosphere, but pushed far more towards
 * flat black - lyrics is a reading surface, so the artwork is a hint of mood glimpsed behind the
 * text, not a picture. Recomposes only when [model] (the artwork request) changes, never on a
 * position tick. A flat black canvas (from [TakiScaffold]) with no [model].
 */
@Composable
private fun LyricsAtmosphere(model: Any?) {
    if (model == null) return
    val colors = TakiTheme.colors
    val desaturate = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(TakiAtmosphere.LYRICS_SATURATION) })
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(model)
            .size(TakiAtmosphere.ATMOSPHERE_SOURCE_PX)
            .crossfade(false)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = desaturate,
        modifier = Modifier
            .fillMaxSize()
            .blur(TakiAtmosphere.featureBlurRadius)
            .alpha(TakiAtmosphere.LYRICS_ARTWORK_ALPHA),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to colors.black.copy(alpha = TakiAtmosphere.LYRICS_SCRIM_ALPHA_TOP),
                    TakiAtmosphere.LYRICS_SCRIM_STOP_MID to
                        colors.black.copy(alpha = TakiAtmosphere.LYRICS_SCRIM_ALPHA_MID),
                    1f to colors.black.copy(alpha = TakiAtmosphere.LYRICS_SCRIM_ALPHA_BOTTOM),
                ),
            ),
    )
}

/**
 * Title, artist and a small accent rule (issue #10 phase 4K4, strengthened in 4K5) - compact on
 * its own, but with generous room below before the first lyric ([TakiTheme.spacing.xl]), so it
 * reads as a distinct identity block rather than crowding the reading surface underneath. The
 * title is semibold (stronger than a body row's usual medium weight) and the artist drops the
 * caption role's default light weight for a plain one, so both stay clearly legible against any
 * album art - the header scrim's own top stop does the rest (see [LyricsAtmosphere]).
 */
@Composable
private fun TrackIdentity(state: LyricsUiState) {
    if (state.title.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TakiTheme.spacing.xl)
            .padding(bottom = TakiTheme.spacing.xl)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = state.title,
            style = TakiTheme.type.title.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!state.artist.isNullOrEmpty()) {
            Text(
                text = state.artist,
                style = TakiTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(TakiTheme.spacing.sm))
        Box(
            Modifier
                .width(TakiTheme.spacing.xxl)
                .height(TakiTheme.dimensions.borderThin)
                .background(TakiTheme.colors.accent.copy(alpha = ACCENT_RULE_ALPHA)),
        )
    }
}

@Composable
private fun LoadingBody() {
    val label = stringResource(R.string.lyrics_loading)
    Box(
        Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(TakiTheme.dimensions.iconSm),
                strokeWidth = TakiTheme.spacing.xxs,
                color = TakiTheme.colors.gray,
            )
            Spacer(Modifier.width(TakiTheme.spacing.sm))
            Text(text = label, style = TakiTheme.type.caption)
        }
    }
}

/**
 * Unsynced lyrics: static, source line breaks preserved, no active line, no playback coupling.
 * [bottomContentInset] (issue #10 phase 4K4) reserves room for the floating mini-player so the
 * last line can scroll clear of it, exactly like every other detail screen's scrollable body.
 */
@Composable
private fun PlainBody(trackId: String?, content: LyricsContent.Plain, bottomContentInset: Dp) {
    val listState = rememberLazyListState()
    // A new track starts at the top.
    LaunchedEffect(trackId) { listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(LYRICS_LIST_TEST_TAG),
        contentPadding = PaddingValues(
            start = TakiTheme.spacing.xl,
            end = TakiTheme.spacing.xl,
            top = TakiTheme.spacing.sm,
            bottom = bottomContentInset,
        ),
    ) {
        itemsIndexed(content.lines) { _, line ->
            if (line.isBlank()) {
                Spacer(Modifier.height(TakiTheme.spacing.xl))
            } else {
                Text(
                    text = line,
                    style = TakiTheme.type.body.copy(fontSize = PLAIN_FONT_SP.sp, lineHeight = PLAIN_LINE_SP.sp),
                )
            }
        }
    }
}

/**
 * Synced lyrics. [active] is the only frequently-changing value, recomputed by a binary search on
 * a 250ms poll and written only when the line actually changes. Auto-follow scrolls the active
 * line into a reading zone; a manual scroll suspends it (a small "back to current line" chip
 * resumes it), and tapping a line seeks - which also resumes following.
 */
@Composable
private fun SyncedBody(
    trackId: String?,
    content: LyricsContent.Synced,
    positionMs: () -> Long,
    onSeek: (Long) -> Unit,
    bottomContentInset: Dp,
) {
    val lines = content.lines
    val listState = rememberLazyListState()
    val readPosition by rememberUpdatedState(positionMs)
    var active by remember(trackId, lines) { mutableIntStateOf(-1) }
    var following by remember(trackId, lines) { mutableStateOf(true) }
    val autoScrolling = remember { BooleanHolder() }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lines, lifecycle) {
        trackActiveLine(lifecycle, lines, readPosition) { active = it }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !autoScrolling.value) following = false
        }
    }
    LaunchedEffect(active, following) {
        if (following && active >= 0) {
            autoScrolling.value = true
            try {
                listState.followLine(active)
            } finally {
                autoScrolling.value = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        SyncedLines(lines, active, listState, bottomContentInset) { index, line ->
            onSeek(line.startMs)
            active = index
            following = true
        }
        ResumeFollowChip(visible = !following && active >= 0, onResume = { following = true })
    }
}

/**
 * Polls [positionMs] at [POSITION_POLL_MS] cadence and reports the active line whenever it
 * changes, only while the lifecycle is at least STARTED - like the legacy screen's
 * onResume/onPause tracking, no polling while backgrounded.
 */
private suspend fun trackActiveLine(
    lifecycle: Lifecycle,
    lines: List<LyricsLineUi>,
    positionMs: () -> Long,
    onActiveChanged: (Int) -> Unit,
) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        var active = -1
        while (true) {
            val index = activeLineIndex(lines, positionMs())
            if (index != active) {
                active = index
                onActiveChanged(index)
            }
            delay(POSITION_POLL_MS)
        }
    }
}

@Composable
private fun BoxScope.SyncedLines(
    lines: List<LyricsLineUi>,
    active: Int,
    listState: LazyListState,
    bottomContentInset: Dp,
    onLineClick: (index: Int, line: LyricsLineUi) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(LYRICS_LIST_TEST_TAG),
        contentPadding = PaddingValues(
            start = TakiTheme.spacing.xl,
            end = TakiTheme.spacing.xl,
            top = TakiTheme.spacing.sm,
            bottom = bottomContentInset,
        ),
        verticalArrangement = Arrangement.spacedBy(TakiTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.startMs}" }) { index, line ->
            SyncedLine(
                line = line,
                distance = if (active < 0) Int.MAX_VALUE else index - active,
                onClick = { onLineClick(index, line) },
            )
        }
        item { Spacer(Modifier.fillParentMaxHeight(TAIL_FRACTION)) }
    }
}

/**
 * A small floating chip (issue #10 phase 4K5) - a plain icon glyph on a translucent circle, the
 * same visual weight as the app's other floating chrome ([TakiTheme.colors.surfaceFloating]),
 * not a labelled button: it should read as a quiet affordance in the corner of the reading
 * surface, not a control competing with the lyrics for attention.
 */
@Composable
private fun BoxScope.ResumeFollowChip(visible: Boolean, onResume: () -> Unit) {
    val label = stringResource(R.string.lyrics_resume_follow)
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomEnd).padding(TakiTheme.spacing.lg),
    ) {
        Box(
            modifier = Modifier
                .size(TakiTheme.dimensions.touchTargetMin)
                .clip(TakiTheme.shapes.circle)
                .background(TakiTheme.colors.surfaceFloating)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onResume)
                .testTag(LYRICS_RESUME_TEST_TAG)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = TakiTheme.colors.ivory,
                modifier = Modifier.size(TakiTheme.dimensions.iconSm),
            )
        }
    }
}

private class BooleanHolder {
    var value: Boolean = false
}

private suspend fun LazyListState.followLine(index: Int) {
    val viewport = layoutInfo.viewportSize.height
    animateScrollToItem(index, scrollOffset = -(viewport * READING_ZONE_FRACTION).toInt())
}

/**
 * One synced, centred line (issue #10 phase 4K4, centred and re-tiered in 4K5): the active line
 * is ivory, semibold and a size step larger, on a faint accent wash - the one deliberately
 * sparing use of the accent colour on this screen, never the reading colour itself. Every other
 * line is muted gray in one of two tiers by its distance from the active line - near lines stay
 * readable context, far lines fade further - so the eye reads the current line first and finds
 * its way to nearby lines at a glance. Capped at [SyncedMaxWidth] and center-aligned so the block
 * reads as a centred, immersive surface rather than a left-aligned list. Only lines whose
 * distance changed recompose their style.
 */
@Composable
private fun SyncedLine(line: LyricsLineUi, distance: Int, onClick: () -> Unit) {
    val isActive = distance == 0
    val currentLine = stringResource(R.string.lyrics_current_line)
    val seekLabel = stringResource(R.string.lyrics_seek_to_line)
    val style = when {
        isActive -> TakiTheme.type.body.copy(
            fontSize = SYNCED_ACTIVE_FONT_SP.sp,
            lineHeight = SYNCED_ACTIVE_LINE_SP.sp,
            color = TakiTheme.colors.ivory,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        else -> {
            val alpha = if (abs(distance) <= SYNCED_NEAR_DISTANCE) {
                SYNCED_NEAR_ALPHA
            } else {
                SYNCED_FAR_ALPHA
            }
            TakiTheme.type.body.copy(
                fontSize = SYNCED_FONT_SP.sp,
                lineHeight = SYNCED_LINE_SP.sp,
                color = TakiTheme.colors.gray.copy(alpha = alpha),
                textAlign = TextAlign.Center,
            )
        }
    }
    Text(
        text = line.text.ifBlank { " " },
        style = style,
        modifier = Modifier
            .widthIn(max = SyncedMaxWidth)
            .let { if (isActive) it.testTag(LYRICS_ACTIVE_LINE_TEST_TAG) else it }
            .then(
                if (isActive) {
                    Modifier.background(
                        TakiTheme.colors.accent.copy(alpha = SYNCED_ACTIVE_WASH_ALPHA),
                        TakiTheme.shapes.xs,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = TakiTheme.spacing.sm, vertical = TakiTheme.spacing.xxs)
            .clickable(onClickLabel = seekLabel, role = Role.Button, onClick = onClick)
            .semantics { if (isActive) stateDescription = currentLine },
    )
}
