/*
 * UpNextScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.upnext

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

const val UP_NEXT_LIST_TEST_TAG = "up_next_list"
const val UP_NEXT_CURRENT_TEST_TAG = "up_next_current"

private const val SECTION_LETTER_SPACING_EM = 0.08f
private const val RESYNC_DELAY_MS = 600L
private const val EDGE_SCROLL_STEP_PX = 24f
private const val DRAG_SCALE = 1.02f
private const val DRAG_SHADOW_DP = 6f
private const val FOOTER_KEY = "footer"
private const val CURRENT_KEY = "current"
private const val NOW_HEADER_KEY = "now_header"
private const val NEXT_HEADER_KEY = "next_header"

/**
 * Up Next (issue #10 phase 4K1): the queue as a dedicated, calm playback sub-surface - a Taki
 * header, the currently playing track, then the upcoming tracks in play order with long-press
 * drag (reorder), swipe-to-remove and a menu. Replaces the legacy `current_playlist.xml`
 * RecyclerView; the runtime queue (`MediaPlayerManager`) stays the only source of truth - this is
 * a pure projection of [UpNextUiState], and the local order kept while a drag is in flight is
 * reconciled with the runtime right after the drop.
 *
 * [progressFraction] is read only while drawing the current track's thin progress line, so the
 * 500ms playback ticks never recompose the list. [artworkFor] resolves a row's cover lazily, so
 * only visible rows pay for it.
 */
@Composable
fun UpNextScreen(
    state: UpNextUiState,
    progressFraction: () -> Float,
    artworkFor: (UpNextTrackUi) -> Any?,
    actions: UpNextActions,
    modifier: Modifier = Modifier,
) {
    TakiScaffold(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            TakiScreenHeader(onBack = actions.onBack, title = stringResource(R.string.up_next_title))
            UpNextList(
                state = state,
                progressFraction = progressFraction,
                artworkFor = artworkFor,
                actions = actions,
            )
        }
    }
}

/** Local drag bookkeeping - the row being dragged and its pixel offset. Never the queue itself. */
@Stable
private class UpNextDragState {
    var draggedKey by mutableStateOf<String?>(null)
    var offsetY by mutableFloatStateOf(0f)
    var startIndex = -1
    var resyncTick by mutableIntStateOf(0)
    val active: Boolean get() = draggedKey != null
}

/**
 * Owns the list's local order while a drag is in flight and turns row gestures into runtime
 * commands. The order here is only ever a mirror of the runtime queue: it is re-synced from the
 * state whenever no drag is active and shortly after every drop, and never dispatched as a whole -
 * only single `move(from, to)` / `remove(position)` commands, in play-order positions.
 */
@Stable
private class UpNextController(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val edgePx: Float,
    initialRows: List<UpNextTrackUi>,
) {
    var rows by mutableStateOf(initialRows)
    val drag = UpNextDragState()
    var firstPosition = initialRows.firstOrNull()?.playOrderIndex ?: 0
    lateinit var actions: UpNextActions

    fun positionOf(key: String): Int = firstPosition + rows.indexOfFirst { it.key == key }

    fun remove(key: String) {
        val position = positionOf(key)
        rows = rows.filterNot { it.key == key }
        actions.onRemove(position)
    }

    fun moveBy(key: String, delta: Int) {
        val from = rows.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in rows.indices) return
        rows = moved(rows, from, to)
        actions.onMove(firstPosition + from, firstPosition + to)
        drag.resyncTick++
    }

    fun startDrag(key: String) {
        drag.draggedKey = key
        drag.offsetY = 0f
        drag.startIndex = rows.indexOfFirst { it.key == key }
    }

    fun dragBy(dy: Float) {
        drag.offsetY += dy
        rows = dragStep(listState, drag, rows)
        autoScroll(scope, listState, drag, edgePx)
    }

    fun endDrag() {
        val from = drag.startIndex
        val to = rows.indexOfFirst { it.key == drag.draggedKey }
        drag.draggedKey = null
        drag.offsetY = 0f
        if (from >= 0 && to >= 0 && from != to) actions.onMove(firstPosition + from, firstPosition + to)
        drag.resyncTick++
    }

    fun cancelDrag(latest: List<UpNextTrackUi>) {
        drag.draggedKey = null
        drag.offsetY = 0f
        rows = latest
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpNextList(
    state: UpNextUiState,
    progressFraction: () -> Float,
    artworkFor: (UpNextTrackUi) -> Any?,
    actions: UpNextActions,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val edgePx = with(LocalDensity.current) { TakiTheme.dimensions.touchTargetMin.toPx() }
    val controller = remember { UpNextController(listState, scope, edgePx, state.upcoming) }
    controller.actions = actions
    controller.firstPosition = state.upcoming.firstOrNull()?.playOrderIndex ?: controller.firstPosition
    val latestUpcoming by rememberUpdatedState(state.upcoming)
    val drag = controller.drag

    LaunchedEffect(state.upcoming) { if (!drag.active) controller.rows = state.upcoming }
    LaunchedEffect(drag.resyncTick) {
        if (drag.resyncTick > 0) {
            delay(RESYNC_DELAY_MS)
            if (!drag.active) controller.rows = latestUpcoming
        }
    }

    val rows = controller.rows
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(UP_NEXT_LIST_TEST_TAG),
        contentPadding = PaddingValues(bottom = TakiTheme.spacing.xxl),
    ) {
        state.current?.let { current ->
            item(key = NOW_HEADER_KEY) { SectionLabel(stringResource(R.string.up_next_now_playing)) }
            item(key = CURRENT_KEY) { CurrentTrackSection(current, progressFraction) }
        }
        if (rows.isNotEmpty()) item(key = NEXT_HEADER_KEY) { SectionLabel(stringResource(R.string.up_next_next)) }
        items(rows, key = { it.key }) { row ->
            val index = rows.indexOfFirst { it.key == row.key }
            val dragging = drag.draggedKey == row.key
            UpNextRow(
                row = row,
                artwork = artworkFor(row),
                dragging = dragging,
                canMoveUp = index > 0,
                canMoveDown = index in 0 until rows.lastIndex,
                onPlay = { controller.actions.onPlay(controller.positionOf(row.key)) },
                onRemove = { controller.remove(row.key) },
                onMenuItem = { item -> controller.actions.onMenuItem(controller.positionOf(row.key), item) },
                onMoveBy = { delta -> controller.moveBy(row.key, delta) },
                onDragStart = { controller.startDrag(row.key) },
                onDrag = controller::dragBy,
                onDragEnd = controller::endDrag,
                onDragCancel = { controller.cancelDrag(latestUpcoming) },
                modifier = if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer {
                        translationY = drag.offsetY
                        scaleX = DRAG_SCALE
                        scaleY = DRAG_SCALE
                        shadowElevation = DRAG_SHADOW_DP.dp.toPx()
                    }
                } else {
                    Modifier.animateItem()
                },
            )
        }
        item(key = FOOTER_KEY) { Footer(remaining = rows.size) }
    }
}

/** One drag step: if the dragged row's centre has crossed a neighbour, swap them in the local
 *  order and shift the offset so the row stays under the finger. Returns the (possibly new) order. */
private fun dragStep(
    listState: LazyListState,
    drag: UpNextDragState,
    rows: List<UpNextTrackUi>,
): List<UpNextTrackUi> {
    val visible = listState.layoutInfo.visibleItemsInfo
    val info = visible.firstOrNull { it.key == drag.draggedKey } ?: return rows
    val rowKeys = rows.mapTo(HashSet()) { it.key }
    val center = info.offset + info.size / 2f + drag.offsetY
    val target = visible.firstOrNull {
        it.key != drag.draggedKey && it.key in rowKeys && center >= it.offset && center <= it.offset + it.size
    } ?: return rows
    val from = rows.indexOfFirst { it.key == drag.draggedKey }
    val to = rows.indexOfFirst { it.key == target.key }
    if (from < 0 || to < 0 || from == to) return rows
    val newOffset = if (to > from) target.offset + target.size - info.size else target.offset
    drag.offsetY -= (newOffset - info.offset)
    return moved(rows, from, to)
}

private fun autoScroll(
    scope: CoroutineScope,
    listState: LazyListState,
    drag: UpNextDragState,
    edgePx: Float,
) {
    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == drag.draggedKey } ?: return
    val viewportHeight = listState.layoutInfo.viewportSize.height
    val top = info.offset + drag.offsetY
    val bottom = top + info.size
    val step = when {
        bottom > viewportHeight - edgePx -> EDGE_SCROLL_STEP_PX
        top < edgePx -> -EDGE_SCROLL_STEP_PX
        else -> return
    }
    scope.launch { drag.offsetY += listState.scrollBy(step) }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = TakiTheme.type.caption.copy(letterSpacing = SECTION_LETTER_SPACING_EM.em),
        modifier = Modifier
            .padding(horizontal = TakiTheme.spacing.lg)
            .padding(top = TakiTheme.spacing.lg, bottom = TakiTheme.spacing.xs)
            .semantics { heading() },
    )
}

@Composable
private fun CurrentTrackSection(current: UpNextCurrentUi, progressFraction: () -> Float) {
    val nowPlaying = stringResource(R.string.up_next_now_playing)
    val activeColor = TakiTheme.colors.progress
    val trackColor = TakiTheme.colors.surface
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(UP_NEXT_CURRENT_TEST_TAG)
            .semantics(mergeDescendants = true) { stateDescription = nowPlaying },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TakiTheme.spacing.lg, vertical = TakiTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TakiArtwork(
                model = current.artworkModel,
                contentDescription = null,
                size = TakiTheme.dimensions.artworkThumb,
                shape = TakiTheme.shapes.sm,
            )
            Spacer(Modifier.width(TakiTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(text = current.title, style = TakiTheme.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!current.artist.isNullOrEmpty()) {
                    Text(text = current.artist, style = TakiTheme.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(TakiTheme.spacing.sm))
            Icon(
                painter = painterResource(R.drawable.ic_queue_playing),
                contentDescription = null,
                tint = TakiTheme.colors.accent,
                modifier = Modifier.size(TakiTheme.dimensions.iconSm),
            )
        }
        Box(
            Modifier
                .padding(horizontal = TakiTheme.spacing.lg)
                .fillMaxWidth()
                .height(TakiTheme.spacing.xxs)
                .drawBehind {
                    drawRect(trackColor, size = size)
                    drawRect(activeColor, size = Size(size.width * progressFraction().coerceIn(0f, 1f), size.height))
                },
        )
    }
}

@Composable
private fun Footer(remaining: Int) {
    Box(
        Modifier.fillMaxWidth().padding(top = TakiTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (remaining == 0) {
                stringResource(R.string.up_next_nothing)
            } else {
                pluralStringResource(R.plurals.up_next_remaining, remaining, remaining)
            },
            style = TakiTheme.type.caption,
        )
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpNextRow(
    row: UpNextTrackUi,
    artwork: Any?,
    dragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMenuItem: (UpNextMenuItem) -> Unit,
    onMoveBy: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentRemove by rememberUpdatedState(onRemove)
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) currentRemove()
    }
    val removeLabel = stringResource(R.string.up_next_remove)
    val moveUpLabel = stringResource(R.string.up_next_move_up)
    val moveDownLabel = stringResource(R.string.up_next_move_down)
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        gesturesEnabled = !dragging,
        backgroundContent = { RemoveReveal(dismissState.dismissDirection) },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (dragging) TakiTheme.colors.surface else TakiTheme.colors.black)
                .clickable(role = Role.Button, onClick = onPlay)
                .reorderOnLongPress(
                    key = row.key,
                    onArm = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onLongPressRelease = { menuOpen = true },
                )
                .semantics {
                    onLongClick { menuOpen = true; true }
                    customActions = buildList {
                        add(CustomAccessibilityAction(removeLabel) { onRemove(); true })
                        if (canMoveUp) add(CustomAccessibilityAction(moveUpLabel) { onMoveBy(-1); true })
                        if (canMoveDown) add(CustomAccessibilityAction(moveDownLabel) { onMoveBy(1); true })
                    }
                }
                .heightIn(min = TakiTheme.dimensions.rowMd)
                .padding(horizontal = TakiTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TakiArtwork(
                model = artwork,
                contentDescription = null,
                size = TakiTheme.dimensions.artworkMini,
                shape = TakiTheme.shapes.sm,
            )
            Spacer(Modifier.width(TakiTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(text = row.title, style = TakiTheme.type.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!row.artist.isNullOrEmpty()) {
                    Text(text = row.artist, style = TakiTheme.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            RowMenu(expanded = menuOpen, items = row.menuItems, onDismiss = { menuOpen = false }, onItem = onMenuItem)
        }
    }
}

/**
 * Long-press-then-drag on the whole row. Arbitration: a tap plays (plain `clickable`), a horizontal
 * swipe before the long-press timeout belongs to swipe-to-remove (this detector gives up without
 * consuming), and once the long press fires the row is "armed": vertical travel beyond touch slop
 * reorders, while releasing without that travel opens the row menu. The armed gesture consumes its
 * events, including the release, so it never also counts as a tap.
 */
@Suppress("LongParameterList")
private fun Modifier.reorderOnLongPress(
    key: String,
    onArm: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onLongPressRelease: () -> Unit,
): Modifier = composed {
    val arm by rememberUpdatedState(onArm)
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    val release by rememberUpdatedState(onLongPressRelease)
    pointerInput(key) {
        detectLongPressReorder(
            onArm = { arm() },
            onDragStart = { start() },
            onDrag = { move(it) },
            onFinish = { dragged -> if (dragged) end() else release() },
            onCancel = { dragged -> if (dragged) cancel() },
        )
    }
}

private suspend fun PointerInputScope.detectLongPressReorder(
    onArm: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onFinish: (dragged: Boolean) -> Unit,
    onCancel: (dragged: Boolean) -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val armed = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        onArm()
        var travelled = 0f
        var dragging = false
        val finished = drag(armed.id) { change ->
            val dy = change.positionChange().y
            change.consume()
            travelled += dy
            if (dragging) {
                onDrag(dy)
            } else if (abs(travelled) > slop) {
                dragging = true
                onDragStart()
                onDrag(travelled)
            }
        }
        currentEvent.changes.forEach { it.consume() }
        if (finished) onFinish(dragging) else onCancel(dragging)
    }
}

@Composable
private fun RemoveReveal(direction: SwipeToDismissBoxValue) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    Box(
        Modifier
            .fillMaxSize()
            .background(TakiTheme.colors.error.copy(alpha = 0.14f))
            .padding(horizontal = TakiTheme.spacing.xl),
        contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_np_remove),
            contentDescription = null,
            tint = TakiTheme.colors.error,
            modifier = Modifier.size(TakiTheme.dimensions.iconMd),
        )
    }
}

@Composable
private fun RowMenu(
    expanded: Boolean,
    items: List<UpNextMenuItem>,
    onDismiss: () -> Unit,
    onItem: (UpNextMenuItem) -> Unit,
) {
    if (items.isEmpty()) return
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        items.forEach { item ->
            val label = when (item) {
                UpNextMenuItem.GO_TO_ARTIST -> R.string.download_menu_show_artist
                UpNextMenuItem.GO_TO_ALBUM -> R.string.download_menu_show_album
                UpNextMenuItem.LYRICS -> R.string.download_menu_lyrics
                UpNextMenuItem.SHUFFLE -> R.string.download_menu_shuffle
                UpNextMenuItem.FAVORITE -> R.string.download_menu_favorite
            }
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    onDismiss()
                    onItem(item)
                },
            )
        }
    }
}
