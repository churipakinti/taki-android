/*
 * NowPlayingUtilityRow.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * The quiet, icon-only utility row under the transport (issue #10 phases 4J3-4J6): Up Next,
 * Lyrics and Sleep Timer, evenly spaced with no visible labels. Each is a [TakiIconButton], so it
 * keeps a 48dp target, button role and selected semantics, and its accessible name comes from the
 * content description. Up Next is the one permanent queue entry point - it toggles the existing
 * full queue view; Lyrics opens the existing lyrics screen; Sleep Timer opens the existing
 * picker. Accent only marks an active state.
 */
@Composable
internal fun NowPlayingUtilityRow(
    sleepTimerState: SleepTimerState,
    sleepTimerDescription: String,
    showQueue: Boolean,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    val iconSize = TakiTheme.dimensions.nowPlayingUtilityIcon
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TakiIconButton(
            onClick = actions.onToggleQueue,
            painter = painterResource(R.drawable.ic_np_up_next),
            contentDescription = stringResource(R.string.buttons_queue),
            iconSize = iconSize,
            selected = showQueue,
        )
        TakiIconButton(
            onClick = actions.onLyrics,
            painter = painterResource(R.drawable.ic_np_lyrics),
            contentDescription = stringResource(R.string.download_menu_lyrics),
            iconSize = iconSize,
        )
        TakiIconButton(
            onClick = actions.onSleepTimer,
            painter = painterResource(R.drawable.ic_np_sleep),
            contentDescription = sleepTimerDescription,
            iconSize = iconSize,
            selected = sleepTimerState !is SleepTimerState.Off,
        )
    }
}
