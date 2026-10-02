/*
 * SettingsScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.components.TakiSectionHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests scroll to a row / find the scrollable content. */
const val SETTINGS_CONTENT_TEST_TAG = "settings_content"

/**
 * Settings (issue #10 phase 5A4) - a 1:1 behavioural port of the legacy
 * `SettingsFragment`/`R.xml.settings` (`PreferenceFragmentCompat`), rendered from
 * [SettingsDefinitions] instead of an inflated preference tree. One instance of this composable
 * serves both the top level and every nested group - [state]/[actions] already carry whichever
 * screen's own title and item list [org.moire.ultrasonic.model.SettingsViewModel.load] resolved.
 *
 * Draws its own [TakiScreenHeader]; the Activity's shared Material toolbar and shared back bar
 * are both hidden/removed for this destination (see `NavigationActivity`), matching the
 * `aboutFragment`/`serverSelectorFragment`/`editServerFragment` precedent (issue #10 phases
 * 5A1-5A3).
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    TakiScaffold(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth()) {
                TakiScreenHeader(
                    onBack = actions.onBack,
                    title = if (state.titleRes != 0) stringResource(state.titleRes) else null,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SETTINGS_CONTENT_TEST_TAG),
                    contentPadding = PaddingValues(
                        horizontal = TakiTheme.spacing.md,
                        vertical = TakiTheme.spacing.sm,
                    ),
                ) {
                    items(state.rows, key = { rowKey(it) }) { row ->
                        SettingsRow(row, actions)
                    }
                    item(key = "bottom_inset") {
                        Spacer(Modifier.height(bottomContentInset))
                    }
                }
            }

            when (val overlay = state.overlay) {
                is SettingsOverlay.Choice -> SettingsChoiceSheet(
                    item = overlay.item,
                    currentValue = overlay.currentValue,
                    onSelect = { actions.onChoiceSelected(overlay.item, it) },
                    onDismiss = actions.onChoiceDismiss,
                    bottomContentInset = bottomContentInset,
                )

                is SettingsOverlay.Confirm -> SettingsConfirmSheet(
                    overlay = overlay,
                    onConfirm = actions.onConfirm,
                    onDismiss = actions.onConfirmDismiss,
                    bottomContentInset = bottomContentInset,
                )

                is SettingsOverlay.Info -> SettingsInfoSheet(
                    overlay = overlay,
                    onDismiss = actions.onInfoDismiss,
                    bottomContentInset = bottomContentInset,
                )

                SettingsOverlay.None -> Unit
            }
        }
    }
}

private fun rowKey(row: SettingsRowState): String = when (row) {
    is SettingsRowState.ToggleRow -> "toggle_${row.item.key}"
    is SettingsRowState.ChoiceRow -> "choice_${row.item.key}"
    is SettingsRowState.NavigationRow -> "nav_${row.item.key}"
    is SettingsRowState.ActionRow -> "action_${row.item.key}"
    is SettingsRowState.CategoryRow -> "category_${row.item.titleRes}"
}

@Composable
private fun SettingsRow(row: SettingsRowState, actions: SettingsActions) {
    when (row) {
        is SettingsRowState.CategoryRow -> TakiSectionHeader(
            title = stringResource(row.item.titleRes),
            modifier = Modifier.padding(vertical = TakiTheme.spacing.sm),
        )

        is SettingsRowState.ToggleRow -> ToggleRow(row, actions)

        is SettingsRowState.ChoiceRow -> ChoiceRow(row, actions)

        is SettingsRowState.NavigationRow -> NavigationRow(row, actions)

        is SettingsRowState.ActionRow -> if (row.visible) ActionRow(row, actions)
    }
}

@Composable
private fun ToggleRow(row: SettingsRowState.ToggleRow, actions: SettingsActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TakiTheme.dimensions.touchTargetMin)
            .toggleable(
                value = row.checked,
                enabled = row.enabled,
                role = Role.Switch,
                onValueChange = { actions.onToggle(row.item, it) },
            )
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(row.item.titleRes),
                style = TakiTheme.type.title,
                color = if (row.enabled) TakiTheme.colors.ivory else TakiTheme.colors.gray,
            )
            RowSummary(row.item.summaryRes, row.dynamicSummaryRes, row.dynamicSummaryArgs)
        }
        Spacer(Modifier.width(TakiTheme.spacing.sm))
        Switch(checked = row.checked, onCheckedChange = null, enabled = row.enabled)
    }
}

@Composable
private fun ChoiceRow(row: SettingsRowState.ChoiceRow, actions: SettingsActions) {
    val entries = stringArrayResource(row.item.entriesRes)
    val values = stringArrayResource(row.item.valuesRes)
    val index = values.indexOf(row.currentValue)
    val selectedLabel = entries.getOrNull(index)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TakiTheme.dimensions.touchTargetMin)
            .clickable(role = Role.Button) { actions.onChoiceClick(row.item) }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(row.item.titleRes), style = TakiTheme.type.title)
            val summary = selectedLabel ?: row.item.summaryRes?.let { stringResource(it) }
            if (summary != null) {
                Text(text = summary, style = TakiTheme.type.caption)
            }
        }
        ChevronIcon()
    }
}

@Composable
private fun NavigationRow(row: SettingsRowState.NavigationRow, actions: SettingsActions) {
    val onClick: () -> Unit = when (row.item.target) {
        NavigationTarget.GROUP -> { { actions.onNavigateGroup(row.item.key) } }
        NavigationTarget.EQUALIZER -> actions.onNavigateEqualizer
        NavigationTarget.ABOUT -> actions.onNavigateAbout
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TakiTheme.dimensions.touchTargetMin)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(row.item.titleRes), style = TakiTheme.type.title)
            RowSummary(row.item.summaryRes, null, emptyList())
        }
        ChevronIcon()
    }
}

@Composable
private fun ActionRow(row: SettingsRowState.ActionRow, actions: SettingsActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TakiTheme.dimensions.touchTargetMin)
            .clickable(role = Role.Button) { actions.onActionClick(row.item) }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(row.item.titleRes), style = TakiTheme.type.title)
            RowSummary(row.item.summaryRes, row.dynamicSummaryRes, row.dynamicSummaryArgs)
        }
    }
}

@Composable
private fun RowSummary(staticRes: Int?, dynamicRes: Int?, dynamicArgs: List<Any>) {
    val text = when {
        dynamicRes != null -> stringResource(dynamicRes, *dynamicArgs.toTypedArray())
        dynamicArgs.isNotEmpty() && staticRes == null -> dynamicArgs.first().toString()
        staticRes != null -> stringResource(staticRes)
        else -> null
    }
    if (!text.isNullOrEmpty()) {
        Text(text = text, style = TakiTheme.type.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChevronIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_expand_more),
        contentDescription = null,
        tint = TakiTheme.colors.gray,
        modifier = Modifier
            .size(TakiTheme.dimensions.iconSm)
            .rotate(CHEVRON_ROTATION_DEGREES),
    )
}

private const val CHEVRON_ROTATION_DEGREES = -90f
