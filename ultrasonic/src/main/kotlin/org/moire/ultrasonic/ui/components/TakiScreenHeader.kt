/*
 * TakiScreenHeader.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * The lightweight top row a Compose back-nav sub-screen draws on the bare Taki canvas instead
 * of the shared Material app bar (V2 section 6 / issue #10 phase 4B): a 48dp back target, an
 * optional single-line title, and an optional trailing actions slot. Transparent, no
 * elevation, no divider - it reads as part of the screen, not a separate bar.
 *
 * The host Fragment owns navigation: [onBack] is a plain callback (usually
 * `findNavController().navigateUp()`), never a `NavController` reached from inside Compose.
 */
@Composable
fun TakiScreenHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    backContentDescription: String = stringResource(R.string.common_navigate_back),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.screenHeaderHeight)
            .padding(horizontal = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TakiIconButton(
            onClick = onBack,
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = backContentDescription,
            tint = TakiTheme.colors.ivory,
        )
        if (title != null) {
            Spacer(Modifier.width(TakiTheme.spacing.sm))
            Text(
                text = title,
                style = TakiTheme.type.hero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}
