/*
 * SettingsUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.settings

/** The resolved, render-ready state of one [SettingsItem] row - [SettingsViewModel] re-derives
 *  this list from SharedPreferences on every relevant change, mirroring the legacy
 *  `onSharedPreferenceChanged`'s `updatePreferenceSummaries`/`updateCustomPreferences` pass. */
sealed interface SettingsRowState {
    data class ToggleRow(
        val item: SettingsItem.Toggle,
        val checked: Boolean,
        val enabled: Boolean = true,
        val dynamicSummaryRes: Int? = null,
        val dynamicSummaryArgs: List<Any> = emptyList(),
    ) : SettingsRowState

    data class ChoiceRow(
        val item: SettingsItem.Choice,
        val currentValue: String,
    ) : SettingsRowState

    data class NavigationRow(val item: SettingsItem.Navigation) : SettingsRowState

    data class ActionRow(
        val item: SettingsItem.Action,
        val visible: Boolean = true,
        val dynamicSummaryRes: Int? = null,
        val dynamicSummaryArgs: List<Any> = emptyList(),
    ) : SettingsRowState

    data class CategoryRow(val item: SettingsItem.Category) : SettingsRowState
}

/** Which transient sheet, if any, is showing over the row list. Only one at a time, matching
 *  the legacy screen's own one-dialog-at-a-time `AlertDialog` usage. */
sealed interface SettingsOverlay {
    data object None : SettingsOverlay
    data class Choice(val item: SettingsItem.Choice, val currentValue: String) : SettingsOverlay
    data class Confirm(
        val titleRes: Int,
        val messageRes: Int,
        val messageArgs: List<Any> = emptyList(),
        val confirmLabelRes: Int,
        val dismissLabelRes: Int,
        val action: ConfirmAction,
    ) : SettingsOverlay
    data class Info(val messageRes: Int, val messageArgs: List<Any> = emptyList()) : SettingsOverlay
}

/** Which destructive/confirmable action a [SettingsOverlay.Confirm] sheet is gating. */
enum class ConfirmAction { CLEAR_DOWNLOADS, CLEAR_IMAGE_CACHE, DELETE_DEBUG_LOGS }

/** One-shot effects the Fragment must perform (launching the system picker, taking a persistable
 *  URI permission) - never re-delivered on recomposition/process-death, consumed immediately by
 *  the Fragment the same way [org.moire.ultrasonic.model.EditServerViewModel]'s `onNavigate`
 *  callback is (issue #10 phase 5A3 precedent: a plain callback, not Channel/Flow - see that
 *  phase's report for why the Flow-based approach proved untestable in this project's harness). */
sealed interface SettingsEffect {
    data class LaunchCacheLocationPicker(val currentUri: String) : SettingsEffect
}

data class SettingsUiState(
    val titleRes: Int = 0,
    val rows: List<SettingsRowState> = emptyList(),
    val overlay: SettingsOverlay = SettingsOverlay.None,
)

/** Callbacks [org.moire.ultrasonic.ui.settings.SettingsScreen] invokes; the Fragment wires every
 *  one to [org.moire.ultrasonic.model.SettingsViewModel] except [onNavigateGroup]/
 *  [onNavigateEqualizer]/[onNavigateAbout], which it resolves to `findNavController()` calls. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onToggle: (SettingsItem.Toggle, Boolean) -> Unit = { _, _ -> },
    val onChoiceClick: (SettingsItem.Choice) -> Unit = {},
    val onChoiceSelected: (SettingsItem.Choice, String) -> Unit = { _, _ -> },
    val onChoiceDismiss: () -> Unit = {},
    val onActionClick: (SettingsItem.Action) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onConfirmDismiss: () -> Unit = {},
    val onInfoDismiss: () -> Unit = {},
    val onNavigateGroup: (String) -> Unit = {},
    val onNavigateEqualizer: () -> Unit = {},
    val onNavigateAbout: () -> Unit = {},
) {
    companion object {
        val Noop = SettingsActions()
    }
}
