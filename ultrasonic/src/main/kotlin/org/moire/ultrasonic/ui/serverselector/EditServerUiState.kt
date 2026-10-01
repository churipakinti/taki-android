/*
 * EditServerUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import androidx.compose.runtime.Immutable

/**
 * New server (first-connection onboarding) vs editing an existing one. [Existing.serverId] is
 * [org.moire.ultrasonic.data.ServerSetting.id] - a stable identity, replacing the legacy
 * position/index-based lookup (issue #10 phase 5A3 audit).
 */
sealed interface EditServerMode {
    data object New : EditServerMode
    data class Existing(val serverId: Int) : EditServerMode
}

enum class ConnectionTestState { IDLE, TESTING, SUCCESS, FAILED }

/** Mirrors the legacy `getFields()`'s two field-specific validation failures exactly - no
 *  aggregate dialog, only these two fields can show an inline error. */
enum class EditServerFieldError { REQUIRED, INVALID_URL }

/**
 * The immutable, presentation-ready state of the Compose Edit Server screen (issue #10 phase
 * 5A3) - a straight projection of the legacy `EditServerFragment`'s own form fields/view-model
 * state. [color] is `null` until the user explicitly picks one (or an existing server already has
 * one saved); the screen renders the Taki accent as the neutral swatch fallback in that case
 * (see [serverSwatchColors]) rather than persisting a computed default color the way the legacy
 * screen always did - a deliberate simplification, not a behavior the legacy screen's own UI
 * exposed any way to intentionally choose (see the phase 5A3 report).
 */
@Immutable
data class EditServerUiState(
    val mode: EditServerMode = EditServerMode.New,
    /** True only while an existing server's data hasn't resolved from the DB yet. */
    val isLoading: Boolean = false,
    val name: String = "",
    val address: String = "http://",
    val username: String = "",
    val password: String = "",
    val color: Int? = null,
    val allowSelfSignedCertificate: Boolean = false,
    val forcePlainTextPassword: Boolean = false,
    val jukeboxByDefault: Boolean = false,
    val addressError: EditServerFieldError? = null,
    val usernameError: EditServerFieldError? = null,
    val advancedExpanded: Boolean = false,
    val connectionTestState: ConnectionTestState = ConnectionTestState.IDLE,
    val connectionErrorMessage: String? = null,
    /** Last test result, null = unknown/not yet tested (legacy `jukeboxSupport == null`). Drives
     *  the jukebox description line and the connection-success auto-expand of Advanced. */
    val jukeboxSupported: Boolean? = null,
    /** True while the discard-changes confirmation sheet is showing. */
    val pendingDiscard: Boolean = false,
) {
    val isNewMode: Boolean get() = mode is EditServerMode.New
    val isTesting: Boolean get() = connectionTestState == ConnectionTestState.TESTING
}

/** One-shot navigation outcomes the Fragment host acts on. */
sealed interface EditServerNavigationEvent {
    /** Cancel / discard / existing-server Save - `findNavController().navigateUp()`. */
    data object NavigateUp : EditServerNavigationEvent

    /** New-server Connect succeeded - `findNavController().popBackStack(R.id.homeFragment,
     *  false)`, matching the legacy screen exactly. */
    data object NavigateHome : EditServerNavigationEvent
}
