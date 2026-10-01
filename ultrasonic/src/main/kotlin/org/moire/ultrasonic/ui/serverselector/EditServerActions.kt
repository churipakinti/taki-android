/*
 * EditServerActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

/** Callbacks [EditServerScreen] needs from its host. [onPickColor] stays Fragment-owned since it
 *  launches the existing Android `ColorPickerDialog` (a third-party View dialog, not ported to
 *  Compose this phase - see the phase 5A3 report's color-picker decision). */
data class EditServerActions(
    val onBack: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onAddressChange: (String) -> Unit,
    val onAddressFocusLost: () -> Unit,
    val onUsernameChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onSelfSignedChange: (Boolean) -> Unit,
    val onPlaintextChange: (Boolean) -> Unit,
    val onJukeboxChange: (Boolean) -> Unit,
    val onToggleAdvanced: () -> Unit,
    val onPickColor: () -> Unit,
    val onTestConnection: () -> Unit,
    val onConnectOrSave: () -> Unit,
    val onDiscardConfirm: () -> Unit,
    val onDiscardCancel: () -> Unit,
) {
    companion object {
        val Noop = EditServerActions(
            onBack = {},
            onNameChange = {},
            onAddressChange = {},
            onAddressFocusLost = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSelfSignedChange = {},
            onPlaintextChange = {},
            onJukeboxChange = {},
            onToggleAdvanced = {},
            onPickColor = {},
            onTestConnection = {},
            onConnectOrSave = {},
            onDiscardConfirm = {},
            onDiscardCancel = {},
        )
    }
}
