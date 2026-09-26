/*
 * NowPlayingUtilityRow.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * The compact, labeled utility row under the transport (issue #10 phases 4J3/4J4): Up Next,
 * Lyrics and Sleep Timer as icon-over-label items, evenly spaced and quieter than the transport.
 * Up Next is the one permanent queue entry point - it toggles the existing full queue view; Lyrics
 * opens the existing lyrics screen; Sleep Timer opens the existing picker. Accent only marks an
 * active state.
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
            modifier = Modifier.size(TakiTheme.dimensions.iconSm),
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
