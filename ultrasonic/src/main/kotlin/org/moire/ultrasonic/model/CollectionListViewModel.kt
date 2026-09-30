/*
 * CollectionListViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.ui.collectionlist.CollectionListRow
import org.moire.ultrasonic.ui.collectionlist.CollectionListUiState
import org.moire.ultrasonic.util.CollectionResolver

/**
 * Owns the Compose Box Sets list state (post-issue-#10 residual migration, phase 5A1) as one
 * [StateFlow]<[CollectionListUiState]>. A straight StateFlow port of the legacy
 * `CollectionListModel`: reads only already-cached album rows (`AlbumDao.withGrouping()`) and
 * resolves them client-side via [CollectionResolver] - never fetches from the network itself, so
 * opening this screen is always cheap regardless of how many box sets exist.
 */
class CollectionListViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {
    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(CollectionListUiState())
    val uiState: StateFlow<CollectionListUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Set once a [load] has ever completed (successfully or not) - the same "keep scroll
     *  position across back navigation" guard the legacy `collections.value != null` check gave:
     *  don't reload just because the resolved list happens to be empty. */
    private var hasLoadedOnce = false

    /** `AlbumDao.withGrouping()` + `CollectionResolver.resolve`, unchanged. Test seam. */
    internal var collectionsLoader: suspend () -> List<MusicCollection> = {
        withContext(Dispatchers.IO) {
            val albums = activeServerProvider.getActiveMetaDatabase().albumDao().withGrouping()
            CollectionResolver.resolve(albums)
        }
    }

    /** Don't reload if already loaded once - same recipe as the legacy `CollectionListModel`. */
    fun load(refresh: Boolean = false) {
        if (!refresh && hasLoadedOnce) return

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }

            val result = try {
                collectionsLoader()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                null
            }

            hasLoadedOnce = true

            if (result == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = it.rows.isEmpty()) }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    rows = result.map { collection -> collection.toRow() }.toImmutableList(),
                )
            }
        }
    }

    fun refresh() = load(refresh = true)

    private fun MusicCollection.toRow(): CollectionListRow = CollectionListRow(
        id = id,
        title = title,
        albumCount = albumCount,
        covers = stackArtwork.map { it.coverArtRequestOrNull(large = false) }.toImmutableList(),
    )
}
