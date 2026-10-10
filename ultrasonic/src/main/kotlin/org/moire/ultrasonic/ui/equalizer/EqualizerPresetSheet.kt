/*
 * EqualizerPresetSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.takiSheetPanelTapSwallow
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests find the sheet, its scrim and one preset row. */
const val EQUALIZER_PRESET_SHEET_TEST_TAG = "equalizer_preset_sheet"
const val EQUALIZER_PRESET_SCRIM_TEST_TAG = "equalizer_preset_scrim"
const val EQUALIZER_PRESET_LIST_TEST_TAG = "equalizer_preset_list"

fun equalizerPresetTestTag(index: Int) = "equalizer_preset_$index"

private const val SCRIM_ALPHA = 0.6f
private const val DRAG_HANDLE_ALPHA = 0.4f
private val SHEET_CORNER_RADIUS = 20.dp // taki-raw-ok: radius_lg's value, top corners only
/** The list may use this share of the available height before it scrolls - a fixed dp cap hid the last of the
 *  device's 10 presets below the fold on a real Pixel 7. */
private const val LIST_MAX_HEIGHT_FRACTION = 0.75f

/**
 * The Compose replacement for the legacy preset `ContextMenu` (issue #10 phase 5A5): a scrollable
 * radio list of exactly the presets the runtime reports - none hard-coded, none invented. The
 * radio is filled only for the preset the runtime says is active; if it reports none, nothing is
 * selected. Same scrim + sliding panel language as the Settings choice sheet.
 */
@Composable
fun EqualizerPresetSheet(
    presets: List<EqualizerPresetUiState>,
    currentPresetIndex: Int?,
    onSelect: (preset: Int) -> Unit,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listMaxHeight = maxHeight * LIST_MAX_HEIGHT_FRACTION
        val dismissInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .matchParentSize()
                .testTag(EQUALIZER_PRESET_SCRIM_TEST_TAG)
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
                .testTag(EQUALIZER_PRESET_SHEET_TEST_TAG)
                .takiSheetPanelTapSwallow()
                .padding(horizontal = TakiTheme.spacing.xl)
                .padding(top = TakiTheme.spacing.sm, bottom = TakiTheme.spacing.sm + bottomContentInset),
        ) {
            DragHandle()
            Text(
                text = stringResource(R.string.equalizer_preset),
                style = TakiTheme.type.title,
                modifier = Modifier.semantics { heading() }.padding(bottom = TakiTheme.spacing.sm),
            )
            LazyColumn(Modifier.heightIn(max = listMaxHeight).testTag(EQUALIZER_PRESET_LIST_TEST_TAG)) {
                items(presets, key = { it.index }) { preset ->
                    PresetOptionRow(
                        name = preset.name,
                        isSelected = preset.index == currentPresetIndex,
                        onClick = { onSelect(preset.index) },
                        modifier = Modifier.testTag(equalizerPresetTestTag(preset.index)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetOptionRow(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
        Text(text = name, style = TakiTheme.type.body)
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
