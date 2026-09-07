/*
 * LibraryUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.library

import androidx.compose.runtime.Immutable

/**
 * All the *state* the Library Compose screen has. Library is a stable index, not a feed, so
 * almost every row is static navigation and carries no state at all - the only thing that
 * actually varies is whether the "Box Sets" row is shown.
 *
 * [boxSetsAvailable] mirrors the old `MainFragment.setupBoxSetsRow`: true once at least one
 * box set has been resolved from already-cached album metadata (see
 * `org.moire.ultrasonic.util.CollectionResolver`), which only happens after its member
 * albums have been browsed into. Never triggers a network call.
 */
@Immutable
data class LibraryUiState(
    val boxSetsAvailable: Boolean = false,
)
