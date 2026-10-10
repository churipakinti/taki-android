/*
 * SettingsChoiceSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.takiSheetPanelTapSwallow
import org.moire.ultrasonic.ui.components.TakiBackHandler
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests find the sheet and its options. */
const val SETTINGS_CHOICE_SHEET_TEST_TAG = "settings_choice_sheet"
const val SETTINGS_CHOICE_SCRIM_TEST_TAG = "settings_choice_scrim"

/** Generous enough for the longest list (cache size, 18 entries) to feel scrollable rather than
 *  cramped, while still leaving the scrim visible above it on a typical phone. */
private val SHEET_MAX_HEIGHT = 420.dp // taki-raw-ok: one-off sheet height cap, not a spacing/size token

/**
 * The Compose replacement for a legacy `ListPreference`'s dialog (issue #10 phase 5A4): a
 * scrollable radio list, the same scrim + sliding panel pattern as
 * `DeleteServerSheet`/`DiscardServerChangesSheet`. A full-width scrollable sheet rather than a
 * `TakiSortMenu`-style anchored dropdown, since some of these lists are long (cache size has 18
 * entries, language has 15) - a small anchored menu would be awkward at that length.
 *
 * [currentValue] may not be present in [SettingsItem.Choice.valuesRes] (a stale value from a
 * removed/changed option set) - no row is then pre-selected, and nothing crashes.
 */
@Composable
fun SettingsChoiceSheet(
    item: SettingsItem.Choice,
    currentValue: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
) {
    TakiBackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        val dismissInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .matchParentSize()
                .testTag(SETTINGS_CHOICE_SCRIM_TEST_TAG)
                .background(TakiTheme.colors.black.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClickLabel = stringResource(R.string.common_cancel),
                    role = Role.Button,
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = SHEET_CORNER_RADIUS, topEnd = SHEET_CORNER_RADIUS))
                .background(TakiTheme.colors.surface)
                .testTag(SETTINGS_CHOICE_SHEET_TEST_TAG)
                .takiSheetPanelTapSwallow()
                .padding(horizontal = TakiTheme.spacing.xl)
                .padding(top = TakiTheme.spacing.sm, bottom = TakiTheme.spacing.sm + bottomContentInset),
        ) {
            DragHandle()
            Text(
                text = stringResource(item.titleRes),
                style = TakiTheme.type.title,
                modifier = Modifier.semantics { heading() }.padding(bottom = TakiTheme.spacing.sm),
            )
            val entries = stringArrayResource(item.entriesRes)
            val values = stringArrayResource(item.valuesRes)
            LazyColumn(Modifier.heightIn(max = SHEET_MAX_HEIGHT)) {
                items(entries.size) { index ->
                    val value = values.getOrElse(index) { entries[index] }
                    ChoiceOptionRow(
                        label = entries[index],
                        isSelected = value == currentValue,
                        onClick = { onSelect(value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceOptionRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TakiTheme.dimensions.touchTargetMin)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { selected = isSelected }
            .padding(vertical = TakiTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = TakiTheme.colors.accent),
        )
        Spacer(Modifier.width(TakiTheme.spacing.sm))
        Text(text = label, style = TakiTheme.type.body)
    }
}

/** Purely decorative - the scrim tap and system back already dismiss the sheet. */
@Composable
private fun DragHandle() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = TakiTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(TakiTheme.spacing.xxl)
                .height(TakiTheme.spacing.xs)
                .clip(TakiTheme.shapes.xs)
                .background(TakiTheme.colors.gray.copy(alpha = DRAG_HANDLE_ALPHA)),
        )
    }
}

private const val SCRIM_ALPHA = 0.6f
private const val DRAG_HANDLE_ALPHA = 0.4f
private val SHEET_CORNER_RADIUS = 20.dp // taki-raw-ok: radius_lg's value, top corners only
