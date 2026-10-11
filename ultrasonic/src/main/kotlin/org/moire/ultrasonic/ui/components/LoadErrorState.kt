/*
 * LoadErrorState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * The screen-level state of a list whose first load failed and that has nothing else to show
 * (issue #31). Deliberately a sibling of the empty state, never the same branch: a failed
 * request must not read as an empty library. The copy describes the outcome only - no exception
 * names, URLs or HTTP codes. [onRetry] is the screen's own refresh/load action, so Retry stays on
 * the same screen and re-enters the normal loading state.
 */
@Composable
fun LoadErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = painterResource(R.drawable.ic_empty),
        title = stringResource(R.string.list_load_error_title),
        modifier = modifier.padding(top = TakiTheme.spacing.xxl),
        message = stringResource(R.string.list_load_error_message),
        actionLabel = stringResource(R.string.list_load_error_retry),
        onAction = onRetry,
    )
}
