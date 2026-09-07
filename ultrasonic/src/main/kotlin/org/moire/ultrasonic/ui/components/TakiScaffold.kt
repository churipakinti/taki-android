/*
 * TakiScaffold.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * The root container for a Compose screen hosted inside `NavigationActivity`.
 *
 * It only owns the canvas background. The status-bar inset is already applied by the
 * Activity's `navigation_root`, and the mini-player + bottom navigation are Activity-owned
 * sibling views of the nav host (V2 section 13 / migration plan section 4) - a screen must
 * not try to place them.
 */
@Composable
fun TakiScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TakiTheme.colors.black),
    ) {
        content()
    }
}
