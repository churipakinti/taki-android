/*
 * LyricsScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.lyrics

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

const val LYRICS_LIST_TEST_TAG = "lyrics_list"
const val LYRICS_ACTIVE_LINE_TEST_TAG = "lyrics_active_line"
const val LYRICS_RESUME_TEST_TAG = "lyrics_resume"

private const val POSITION_POLL_MS = 250L
private const val READING_ZONE_FRACTION = 0.3f
private const val TAIL_FRACTION = 0.5f
private const val PAST_ALPHA = 0.5f
private const val FAR_ALPHA = 0.55f
private const val NEAR_ALPHA = 0.8f
private const val PLAIN_FONT_SP = 18 // taki-raw-ok: lyrics body reading size
private const val PLAIN_LINE_SP = 28 // taki-raw-ok: lyrics body reading size
private const val SYNCED_FONT_SP = 24 // taki-raw-ok: lyrics body reading size
private const val SYNCED_LINE_SP = 34 // taki-raw-ok: lyrics body reading size

/**
 * Lyrics (issue #10 phase 4K3): a calm reading surface. A minimal Taki header, the track's
 * title/artist, then the lyrics - [LyricsContent.Plain] as generously spaced static text,
 * [LyricsContent.Synced] with the current line emphasised and followed as playback moves. It is a
 * pure projection of [state]; the only thing it reads outside it is [positionMs], polled at a
 * relaxed cadence and only while synced lyrics are shown, so playback ticks never recompose the
 * whole list - only the active index changes, once per line.
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
) {
    TakiScaffold(modifier = modifier) {
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
                is LyricsContent.Plain -> PlainBody(state.trackId, content)
                is LyricsContent.Synced -> SyncedBody(state.trackId, content, positionMs, actions.onSeek)
            }
        }
    }
}

@Composable
private fun TrackIdentity(state: LyricsUiState) {
    if (state.title.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TakiTheme.spacing.lg)
            .padding(bottom = TakiTheme.spacing.md)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(text = state.title, style = TakiTheme.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!state.artist.isNullOrEmpty()) {
            Text(text = state.artist, style = TakiTheme.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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

/** Unsynced lyrics: static, source line breaks preserved, no active line, no playback coupling. */
@Composable
private fun PlainBody(trackId: String?, content: LyricsContent.Plain) {
    val listState = rememberLazyListState()
    // A new track starts at the top.
    LaunchedEffect(trackId) { listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(LYRICS_LIST_TEST_TAG),
        contentPadding = PaddingValues(horizontal = TakiTheme.spacing.lg, vertical = TakiTheme.spacing.md),
    ) {
        itemsIndexed(content.lines) { _, line ->
            if (line.isBlank()) {
                Spacer(Modifier.height(TakiTheme.spacing.lg))
            } else {
                Text(
                    text = line,
                    style = TakiTheme.type.body.copy(fontSize = PLAIN_FONT_SP.sp, lineHeight = PLAIN_LINE_SP.sp),
                )
            }
        }
        item { Spacer(Modifier.height(TakiTheme.spacing.xxl)) }
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
        SyncedLines(lines, active, listState) { index, line ->
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
    onLineClick: (index: Int, line: LyricsLineUi) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(LYRICS_LIST_TEST_TAG),
        contentPadding = PaddingValues(horizontal = TakiTheme.spacing.lg, vertical = TakiTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md),
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

@Composable
private fun BoxScope.ResumeFollowChip(visible: Boolean, onResume: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TakiTheme.spacing.xl),
    ) {
        TextButton(onClick = onResume, modifier = Modifier.testTag(LYRICS_RESUME_TEST_TAG)) {
            Text(text = stringResource(R.string.lyrics_resume_follow), style = TakiTheme.type.titleSmall)
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
 * One synced line. Emphasis follows its distance from the active line: the active line is
 * semibold ivory, the lines around it recede in steps. Only lines whose distance changed
 * recompose their style.
 */
@Composable
private fun SyncedLine(line: LyricsLineUi, distance: Int, onClick: () -> Unit) {
    val isActive = distance == 0
    val currentLine = stringResource(R.string.lyrics_current_line)
    val seekLabel = stringResource(R.string.lyrics_seek_to_line)
    val base = TakiTheme.type.body.copy(fontSize = SYNCED_FONT_SP.sp, lineHeight = SYNCED_LINE_SP.sp)
    val style = when {
        isActive -> base.copy(fontWeight = FontWeight.SemiBold)
        distance in -1..1 -> base.copy(color = base.color.copy(alpha = NEAR_ALPHA))
        distance < 0 -> base.copy(color = base.color.copy(alpha = PAST_ALPHA))
        else -> base.copy(color = base.color.copy(alpha = FAR_ALPHA))
    }
    Text(
        text = line.text.ifBlank { " " },
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isActive) it.testTag(LYRICS_ACTIVE_LINE_TEST_TAG) else it }
            .clickable(onClickLabel = seekLabel, role = Role.Button, onClick = onClick)
            .semantics { if (isActive) stateDescription = currentLine },
    )
}
