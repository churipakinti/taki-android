/*
 * TakiMenu.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.ui.theme.TakiTheme

private val MENU_MIN_WIDTH = 220.dp // taki-raw-ok: floor so short labels do not make a sliver
private val MENU_MAX_WIDTH = 296.dp // taki-raw-ok: room for long labels, still a menu not a sheet
private const val MENU_LABEL_MAX_LINES = 2

/**
 * The one popup menu surface for overflow and context menus: a neutral, flat `surfaceHigh`
 * panel with the `radius_md` corner, a hairline tonal edge instead of a shadow, and a width
 * band wide enough for long labels. Anchoring, dismissal and accessibility are Material's
 * [DropdownMenu] untouched - only the surface is Taki's.
 */
@Composable
fun TakiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH),
        shape = TakiTheme.shapes.md,
        containerColor = TakiTheme.colors.surfaceHigh,
        tonalElevation = 0.dp, // taki-raw-ok: flat, no tint
        shadowElevation = 0.dp, // taki-raw-ok: no shadow
        border = BorderStroke(1.dp, TakiTheme.colors.edgeHighlight), // taki-raw-ok: hairline edge
        content = content,
    )
}

/**
 * A menu row: body-weight label (not title weight - the menu is supporting UI), at least the
 * 48dp touch target, wrapping to two lines before it ellipsizes. [destructive] tints the label
 * with the error colour, nothing else.
 */
@Composable
fun TakiMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = TakiTheme.type.body,
                color = when {
                    !enabled -> TakiTheme.colors.gray
                    destructive -> TakiTheme.colors.error
                    else -> TakiTheme.colors.ivory
                },
                maxLines = MENU_LABEL_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = if (destructive) TakiTheme.colors.error else TakiTheme.colors.ivory,
            disabledTextColor = TakiTheme.colors.gray,
        ),
        contentPadding = PaddingValues(horizontal = TakiTheme.spacing.lg),
    )
}

/** A quiet separator between groups of related actions. */
@Composable
fun TakiMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = TakiTheme.spacing.xs),
        color = TakiTheme.colors.outline,
    )
}
