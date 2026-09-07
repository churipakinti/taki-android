/*
 * LibraryViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.ui.library.LibraryUiState
import org.moire.ultrasonic.util.CollectionResolver

/**
 * The only state the Compose Library screen has: whether the "Box Sets" row should show.
 * Everything else on Library is static navigation. Folds in the old
 * `MainFragment.setupBoxSetsRow` - a single read of already-cached Room data
 * (`AlbumDao.withGrouping()` + [CollectionResolver]), never a network call, so it is cheap
 * to run on every Library open.
 */
class LibraryViewModel : ViewModel(), KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /**
     * Resolves "is at least one box set known?" from cached metadata. Overridable in tests so
     * the state transitions can be verified without a Room instance.
     */
    internal var boxSetsProbe: suspend () -> Boolean = {
        withContext(Dispatchers.IO) {
            val albums = activeServerProvider.getActiveMetaDatabase().albumDao().withGrouping()
            CollectionResolver.resolve(albums).isNotEmpty()
        }
    }

    /** Recompute Box Sets visibility. Failures leave the row hidden (matches the old View). */
    suspend fun refresh() {
        val available = try {
            boxSetsProbe()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
            false
        }
        _uiState.update { it.copy(boxSetsAvailable = available) }
    }
}
