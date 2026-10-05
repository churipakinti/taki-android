/*
 * EqualizerUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.equalizer

import java.util.Locale

/** The runtime's level unit is the millibel; the legacy screen displayed `level / 100` as dB. */
private const val MILLIBELS_PER_DECIBEL = 100

/**
 * One runtime band, projected as plain data (issue #10 phase 5A5). Levels are in millibels, the
 * runtime's own unit, so the slider keeps the legacy 1-millibel stepping; frequencies are the
 * legacy `milliHertz / 1000` whole Hertz.
 */
data class EqualizerBandUiState(
    val index: Int,
    val lowFrequencyHz: Int,
    val highFrequencyHz: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val level: Int,
)

data class EqualizerPresetUiState(val index: Int, val name: String)

/**
 * The Equalizer screen's whole state: an immutable projection of the runtime plus one UI-only
 * flag ([presetSheetVisible]) that never touches the runtime. No raw `Equalizer` ever lands here.
 *
 * [currentPresetIndex] is `null` when the runtime reports no active preset (the legacy screen's
 * `-1`): no "Custom" preset is ever invented.
 */
data class EqualizerUiState(
    val controllerAvailable: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqualizerBandUiState> = emptyList(),
    val presets: List<EqualizerPresetUiState> = emptyList(),
    val currentPresetIndex: Int? = null,
    val presetSheetVisible: Boolean = false,
) {
    val currentPresetName: String?
        get() = presets.firstOrNull { it.index == currentPresetIndex }?.name
}

/**
 * The legacy `updateLevelText`, preserved: `level / 100` whole dB (Int division, so it truncates
 * toward zero), `+` only for a positive dB value, so `0 dB` for anything within +-99 millibels.
 */
fun formatEqualizerLevel(levelMillibels: Int): String {
    val decibels = levelMillibels / MILLIBELS_PER_DECIBEL
    return String.format(Locale.getDefault(), "%s%d dB", if (decibels > 0) "+" else "", decibels)
}

/** Callbacks [EqualizerScreen] invokes; the host wires them to the ViewModel and to navigation. */
data class EqualizerActions(
    val onBack: () -> Unit = {},
    val onEnabledChange: (Boolean) -> Unit = {},
    val onBandLevelChange: (band: Int, level: Int) -> Unit = { _, _ -> },
    val onBandLevelChangeFinished: (band: Int) -> Unit = {},
    val onPresetClick: () -> Unit = {},
    val onPresetSelected: (preset: Int) -> Unit = {},
    val onPresetSheetDismiss: () -> Unit = {},
) {
    companion object {
        val Noop = EqualizerActions()
    }
}
