/*
 * CollectionListModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.util.CollectionResolver

/**
 * Collections/Box Sets list (docs/TAKI_COLLECTIONS_BOXSETS_IMPLEMENTATION.md). Reads only
 * already-cached album rows (`AlbumDao.withGrouping()`) and resolves them client-side - never
 * fetches from the network itself, so opening this screen is always cheap regardless of how many
 * box sets exist.
 */
class CollectionListModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {
    private val activeServerProvider: ActiveServerProvider by inject()

    val collections: MutableLiveData<List<MusicCollection>> = MutableLiveData()

    /**
     * Don't reload if already populated - same "keep scroll position across back navigation"
     * recipe as ArtistListModel/AlbumListFragment (docs section 14).
     */
    fun load(refresh: Boolean = false) {
        if (!refresh && collections.value != null) return

        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                val albums =
                    activeServerProvider.getActiveMetaDatabase().albumDao().withGrouping()
                CollectionResolver.resolve(albums)
            }
            collections.value = resolved
        }
    }
}
