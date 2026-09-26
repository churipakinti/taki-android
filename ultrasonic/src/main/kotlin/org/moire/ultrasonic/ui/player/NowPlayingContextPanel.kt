/*
 * NowPlayingContextPanel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.ui.components.TakiEntryRow
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.theme.TakiTheme

/** One queued item shown in the Up Next preview (issue #10 phase 4J3). [playOrderIndex] is the
 *  item's position in the shuffle-aware play order - the same index space the full queue list
 *  uses for tap-to-play. */
@Immutable
data class UpNextItem(
    val title: String,
    val artist: String?,
    val artworkModel: Any?,
    val playOrderIndex: Int,
)

/** The bottom context panel's tabs (issue #10 phase 4J3). */
enum class NowPlayingTab { UP_NEXT, LYRICS, ABOUT }

private const val TAB_LETTER_SPACING_EM = 0.08f

/**
 * The compact, labeled utility row under the transport (issue #10 phase 4J3): Up Next, Lyrics and
 * Sleep Timer as icon-over-label items, evenly spaced and quieter than the transport. The Up Next
 * item toggles the full queue exactly like the old queue button; Lyrics and Sleep Timer fire the
 * same actions. Accent only marks an active state.
 */
@Composable
internal fun NowPlayingUtilityRow(
    sleepTimerState: SleepTimerState,
    sleepTimerDescription: String,
    showQueue: Boolean,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        NowPlayingUtilityItem(
            iconRes = R.drawable.ic_np_up_next,
            label = stringResource(R.string.now_playing_up_next_label),
            contentDescription = stringResource(R.string.buttons_queue),
            selected = showQueue,
            onClick = actions.onToggleQueue,
            modifier = Modifier.weight(1f),
        )
        NowPlayingUtilityItem(
            iconRes = R.drawable.ic_np_lyrics,
            label = stringResource(R.string.download_menu_lyrics),
            contentDescription = stringResource(R.string.download_menu_lyrics),
            selected = false,
            onClick = actions.onLyrics,
            modifier = Modifier.weight(1f),
        )
        NowPlayingUtilityItem(
            iconRes = R.drawable.ic_np_sleep,
            label = stringResource(R.string.now_playing_sleep_timer_label),
            contentDescription = sleepTimerDescription,
            selected = sleepTimerState !is SleepTimerState.Off,
            onClick = actions.onSleepTimer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NowPlayingUtilityItem(
    iconRes: Int,
    label: String,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) TakiTheme.colors.accent else TakiTheme.colors.gray
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(TakiTheme.dimensions.iconMd),
        )
        Spacer(Modifier.height(TakiTheme.spacing.xs))
        Text(
            text = label,
            style = TakiTheme.type.caption.copy(color = tint),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The bottom context panel (issue #10 phase 4J3): UP NEXT / LYRICS / ABOUT tabs over a compact
 * body, on a quiet rounded surface - part of the player, not a modal card. Every body reuses
 * existing behaviour: the Up Next row plays via the same tap-to-play path as the queue list, the
 * queue affordance opens the full (legacy, unchanged) queue, Lyrics opens the existing lyrics
 * destination, and About only offers the existing
 * go-to-album / go-to-artist actions.
 */
@Composable
internal fun NowPlayingContextPanel(
    upNext: List<UpNextItem>,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(NowPlayingTab.UP_NEXT) }
    val shape = TakiTheme.shapes.lg
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TakiTheme.colors.surfaceLowFloating)
            .border(TakiTheme.dimensions.borderThin, TakiTheme.colors.edgeHighlight, shape),
    ) {
        NowPlayingTabs(selected = selectedTab, onSelect = { selectedTab = it })
        Box(Modifier.fillMaxWidth().height(TakiTheme.dimensions.rowLg), contentAlignment = Alignment.CenterStart) {
            when (selectedTab) {
                NowPlayingTab.UP_NEXT -> UpNextBody(upNext = upNext, actions = actions)
                NowPlayingTab.LYRICS -> PanelActionRow(
                    iconRes = R.drawable.ic_np_lyrics,
                    text = stringResource(R.string.now_playing_lyrics_open),
                    onClick = actions.onLyrics,
                    modifier = Modifier.fillMaxWidth(),
                )

                NowPlayingTab.ABOUT -> AboutBody(actions = actions)
            }
        }
    }
}

@Composable
private fun NowPlayingTabs(selected: NowPlayingTab, onSelect: (NowPlayingTab) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = TakiTheme.spacing.md)) {
            NowPlayingTab.entries.forEach { tab ->
                val label = when (tab) {
                    NowPlayingTab.UP_NEXT -> R.string.now_playing_tab_up_next
                    NowPlayingTab.LYRICS -> R.string.now_playing_tab_lyrics
                    NowPlayingTab.ABOUT -> R.string.now_playing_tab_about
                }
                NowPlayingTabItem(
                    text = stringResource(label).uppercase(),
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(TakiTheme.dimensions.borderThin).background(TakiTheme.colors.divider))
    }
}

@Composable
private fun NowPlayingTabItem(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(TakiTheme.dimensions.touchTargetMin), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = TakiTheme.type.caption.copy(
                    color = if (selected) TakiTheme.colors.accent else TakiTheme.colors.gray,
                    letterSpacing = TAB_LETTER_SPACING_EM.em,
                ),
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(TakiTheme.spacing.xxs)
                .background(if (selected) TakiTheme.colors.accent else Color.Transparent),
        )
    }
}

@Composable
private fun UpNextBody(upNext: List<UpNextItem>, actions: NowPlayingActions) {
    if (upNext.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(TakiTheme.dimensions.rowLg), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.now_playing_up_next_empty), style = TakiTheme.type.caption)
        }
        return
    }
    Column {
        upNext.forEach { item ->
            TakiEntryRow(
                title = item.title,
                artworkModel = item.artworkModel,
                subtitle = item.artist,
                onClick = { actions.onPlayUpNext(item.playOrderIndex) },
                trailing = {
                    TakiIconButton(
                        onClick = actions.onToggleQueue,
                        painter = painterResource(R.drawable.ic_np_up_next),
                        contentDescription = stringResource(R.string.now_playing_open_queue),
                    )
                },
            )
        }
    }
}

@Composable
private fun AboutBody(actions: NowPlayingActions) {
    Row(Modifier.fillMaxWidth()) {
        PanelActionRow(
            iconRes = null,
            text = stringResource(R.string.now_playing_about_go_album),
            onClick = actions.onTitleClick,
            modifier = Modifier.weight(1f),
        )
        PanelActionRow(
            iconRes = null,
            text = stringResource(R.string.now_playing_about_go_artist),
            onClick = actions.onArtistClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PanelActionRow(
    iconRes: Int?,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TakiTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = TakiTheme.colors.gray,
                modifier = Modifier.size(TakiTheme.dimensions.iconMd),
            )
            Spacer(Modifier.width(TakiTheme.spacing.md))
        }
        Text(text = text, style = TakiTheme.type.body)
    }
}
