/*
 * EqualizerScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.equalizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.components.TakiSectionHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests find the scrollable content, the master switch, the preset row and each band. */
const val EQUALIZER_CONTENT_TEST_TAG = "equalizer_content"
const val EQUALIZER_ENABLED_ROW_TEST_TAG = "equalizer_enabled_row"
const val EQUALIZER_PRESET_ROW_TEST_TAG = "equalizer_preset_row"
const val EQUALIZER_UNAVAILABLE_TEST_TAG = "equalizer_unavailable_state"

fun equalizerBandTestTag(index: Int) = "equalizer_band_$index"

private const val CHEVRON_ROTATION_DEGREES = -90f
private const val DISABLED_ALPHA = 0.38f
private const val INACTIVE_TRACK_ALPHA = 0.35f

/**
 * Equalizer (issue #10 phase 5A5): the last post-#10 residual migration. Presentation only - every
 * band, range and preset arrives in [state] from the runtime via `EqualizerViewModel`; nothing is
 * hard-coded and no raw `Equalizer` is ever seen here. Draws its own [TakiScreenHeader]; the
 * Activity's shared Material toolbar and shared back bar are both hidden/removed for this
 * destination (see `NavigationActivity`), matching the About/Server/Settings precedent.
 */
@Composable
fun EqualizerScreen(
    state: EqualizerUiState,
    actions: EqualizerActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    TakiScaffold(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth()) {
                TakiScreenHeader(
                    onBack = actions.onBack,
                    title = stringResource(R.string.equalizer_label),
                )
                if (state.controllerAvailable) {
                    EqualizerContent(state, actions, bottomContentInset)
                } else {
                    UnavailableContent(bottomContentInset)
                }
            }
            if (state.presetSheetVisible) {
                EqualizerPresetSheet(
                    presets = state.presets,
                    currentPresetIndex = state.currentPresetIndex,
                    onSelect = actions.onPresetSelected,
                    onDismiss = actions.onPresetSheetDismiss,
                    bottomContentInset = bottomContentInset,
                )
            }
        }
    }
}

@Composable
private fun EqualizerContent(
    state: EqualizerUiState,
    actions: EqualizerActions,
    bottomContentInset: Dp,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(EQUALIZER_CONTENT_TEST_TAG),
        contentPadding = PaddingValues(
            horizontal = TakiTheme.spacing.md,
            vertical = TakiTheme.spacing.sm,
        ),
    ) {
        item(key = "enabled") { EnabledRow(state.enabled, actions.onEnabledChange) }
        item(key = "preset") {
            PresetRow(
                currentPresetName = state.currentPresetName,
                hasPresets = state.presets.isNotEmpty(),
                onClick = actions.onPresetClick,
            )
        }
        if (state.bands.isNotEmpty()) {
            item(key = "bands_header") {
                TakiSectionHeader(
                    title = stringResource(R.string.equalizer_bands),
                    modifier = Modifier.padding(
                        top = TakiTheme.spacing.lg,
                        bottom = TakiTheme.spacing.xs,
                    ),
                )
            }
        }
        items(state.bands, key = { it.index }) { band ->
            BandRow(
                band = band,
                enabled = state.enabled,
                onLevelChange = actions.onBandLevelChange,
                onLevelChangeFinished = actions.onBandLevelChangeFinished,
            )
        }
        item(key = "bottom_inset") { Spacer(Modifier.height(bottomContentInset)) }
    }
}

@Composable
private fun EnabledRow(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TakiTheme.dimensions.touchTargetMin)
            .testTag(EQUALIZER_ENABLED_ROW_TEST_TAG)
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.equalizer_enabled),
            style = TakiTheme.type.title,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(TakiTheme.spacing.sm))
        // Visual only: the whole row is the one toggle target, so there is no double-toggle.
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun PresetRow(currentPresetName: String?, hasPresets: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TakiTheme.dimensions.touchTargetMin)
            .testTag(EQUALIZER_PRESET_ROW_TEST_TAG)
            .clickable(enabled = hasPresets, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(R.string.equalizer_preset), style = TakiTheme.type.title)
            if (currentPresetName != null) {
                Text(text = currentPresetName, style = TakiTheme.type.caption)
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = TakiTheme.colors.gray,
            modifier = Modifier
                .size(TakiTheme.dimensions.iconSm)
                .rotate(CHEVRON_ROTATION_DEGREES),
        )
    }
}

/**
 * One runtime band: its `low - high Hz` range (the legacy label, not an invented centre
 * frequency), the live dB value, and a slider over the runtime's own millibel range - the legacy
 * 1-millibel stepping. The visible texts are hidden from the semantics tree because the slider
 * itself reads as one adjustable control: "<low> to <high> Hz", the current dB, its range.
 */
@Composable
private fun BandRow(
    band: EqualizerBandUiState,
    enabled: Boolean,
    onLevelChange: (band: Int, level: Int) -> Unit,
    onLevelChangeFinished: (band: Int) -> Unit,
) {
    val levelLabel = formatEqualizerLevel(band.level)
    val description = stringResource(
        R.string.equalizer_band_description,
        band.lowFrequencyHz,
        band.highFrequencyHz,
    )
    Column(Modifier.fillMaxWidth().padding(vertical = TakiTheme.spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.equalizer_band_range,
                    band.lowFrequencyHz,
                    band.highFrequencyHz,
                ),
                style = TakiTheme.type.caption,
            )
            Text(text = levelLabel, style = TakiTheme.type.caption)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatEqualizerLevel(band.minLevel),
                style = TakiTheme.type.caption,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Slider(
                value = band.level.toFloat(),
                onValueChange = { onLevelChange(band.index, it.roundToInt()) },
                onValueChangeFinished = { onLevelChangeFinished(band.index) },
                enabled = enabled,
                valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                colors = bandSliderColors(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = TakiTheme.spacing.sm)
                    .testTag(equalizerBandTestTag(band.index))
                    .semantics {
                        contentDescription = description
                        stateDescription = levelLabel
                    },
            )
            Text(
                text = formatEqualizerLevel(band.maxLevel),
                style = TakiTheme.type.caption,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun bandSliderColors() = TakiTheme.colors.let { colors ->
    val inactiveTrack: Color = colors.gray.copy(alpha = INACTIVE_TRACK_ALPHA)
    SliderDefaults.colors(
        thumbColor = colors.accent,
        activeTrackColor = colors.accent,
        inactiveTrackColor = inactiveTrack,
        disabledThumbColor = colors.gray.copy(alpha = DISABLED_ALPHA),
        disabledActiveTrackColor = colors.gray.copy(alpha = DISABLED_ALPHA),
        disabledInactiveTrackColor = colors.gray.copy(alpha = DISABLED_ALPHA / 2),
    )
}

@Composable
private fun UnavailableContent(bottomContentInset: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomContentInset),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            icon = painterResource(R.drawable.ic_info_outline),
            title = stringResource(R.string.equalizer_unavailable_title),
            message = stringResource(R.string.equalizer_unavailable_message),
            modifier = Modifier.testTag(EQUALIZER_UNAVAILABLE_TEST_TAG),
        )
    }
}
