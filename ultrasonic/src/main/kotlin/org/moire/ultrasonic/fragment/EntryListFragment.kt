/*
 * EntryListFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.adapters.FolderSelectorBinder
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.GenericEntry
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.util.ContextMenuUtil.handleContextMenu

/**
 * An extension of the MultiListFragment, with a few helper functions geared
 * towards the display of MusicDirectory.Entries.
 * @param T: The type of data which will be used (must extend GenericEntry)
 */
abstract class EntryListFragment<T : GenericEntry> :
    MultiListFragment<T>(),
    KoinScopeComponent {

    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()
    private val mediaPlayerManager: MediaPlayerManager by inject()

    /**
     * Whether to show the folder selector
     */
    private fun showFolderHeader(): Boolean =
        listModel.showSelectFolderHeader() && !listModel.isOffline() &&
            !ActiveServerProvider.shouldUseId3Tags()

    override fun onContextMenuItemSelected(menuItem: MenuItem, item: T): Boolean {
        val isArtist = (item is Artist)

        return handleContextMenu(menuItem, item, isArtist, mediaPlayerManager, this)
    }

    override fun onItemClick(item: T) {
        val action = EntryListFragmentDirections.entryListToTrackCollection(
            id = item.id,
            name = item.name,
            parentId = item.id,
            isArtist = (item is Artist)
        )

        findNavController().navigate(action)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Update displayed media when switching between online and offline or changing servers.
        rxBusSubscription += RxBus.activeServerChangedObservable.subscribe {
            getLiveData(refresh = true)
        }

        rxBusSubscription += RxBus.musicFolderChangedEventObservable.subscribe {
            if (!listModel.isOffline()) {
                val currentSetting = listModel.activeServer
                currentSetting.musicFolderId = it.id
                serverSettingsModel.updateItem(currentSetting)
            }
            listModel.refresh(swipeRefresh!!)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Call a cheap function on ServerSettingsModel to make sure it is initialized by Koin,
        // because it can't be initialized from inside the callback
        serverSettingsModel.toString()

        viewAdapter.register(
            FolderSelectorBinder(view.context)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        rxBusSubscription.dispose()
    }

    /**
     * What to do when the list has changed
     */
    override val defaultObserver: (List<T>) -> Unit = {
        // See MultiListFragment.defaultObserver for why this isn't gated on
        // swipeRefresh.isRefreshing.
        emptyView.isVisible = it.isEmpty()

        if (showFolderHeader()) {
            val list = mutableListOf<Identifiable>(folderHeader)
            list.addAll(it)
            viewAdapter.submitList(list)
        } else {
            viewAdapter.submitList(it)
        }
    }

    /**
     * Get a folder header and update it on changes
     */
    private val folderHeader: FolderSelectorBinder.FolderHeader by lazy {
        val header = FolderSelectorBinder.FolderHeader(
            listModel.musicFolders.value!!,
            listModel.activeServer.musicFolderId
        )

        listModel.musicFolders.observe(
            viewLifecycleOwner
        ) {
            header.folders = it
            viewAdapter.notifyItemChanged(0)
        }

        header
    }
}
