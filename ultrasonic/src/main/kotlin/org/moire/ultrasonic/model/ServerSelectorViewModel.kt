/*
 * ServerSelectorViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.ui.serverselector.ServerSelectorRow
import org.moire.ultrasonic.ui.serverselector.ServerSelectorUiState
import timber.log.Timber

/**
 * Owns the Compose Server Selector state (post-issue-#10 residual migration, phase 5A2). A
 * straight port of the legacy `ServerSelectorFragment`'s own logic: [ServerSettingsModel
 * .getServerList] plus the always-synthesized Offline row
 * ([ActiveServerProvider.OFFLINE_DB]), and the exact same delete side-effect sequence the legacy
 * `deleteServerById` used - preserved byte-for-byte, including its one already-flagged quirk (see
 * [confirmDelete]'s kdoc). Reuses [ServerSettingsModel]/[ActiveServerProvider] as the source of
 * truth; this class holds no server data of its own beyond the current screen projection.
 *
 * No `init`-time load: exactly like the legacy Fragment (which only ever called `getServerList()`
 * from `onResume`, never `onViewCreated`), [reload] is called by
 * [org.moire.ultrasonic.fragment.ServerSelectorFragment]'s own `onResume` - preserving the same
 * "refresh after returning from Edit Server" timing (phase 5A2 docs section 18).
 */
class ServerSelectorViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val serverSettingsModel: ServerSettingsModel by inject()
    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(ServerSelectorUiState())
    val uiState: StateFlow<ServerSelectorUiState> = _uiState.asStateFlow()

    private var serverListLiveData: LiveData<List<ServerSetting>>? = null
    private val serverListObserver = Observer<List<ServerSetting>> { publish(it) }

    /** `ServerSettingsModel.getServerList()`, unchanged. Test seam. */
    internal var serverListLoader: () -> LiveData<List<ServerSetting>> =
        { serverSettingsModel.getServerList() }

    /** `ActiveServerProvider.getActiveServerId()`, unchanged. Test seam. */
    internal var activeServerIdReader: () -> Int = { ActiveServerProvider.getActiveServerId() }

    /** `activeServerProvider.setActiveServerById(id)`, unchanged. Test seam. */
    internal var setActiveServer: (Int) -> Unit = { activeServerProvider.setActiveServerById(it) }

    /** `serverSettingsModel.deleteItemById(id)`, unchanged. Test seam. */
    internal var deleteServerById: (Int) -> Unit = { serverSettingsModel.deleteItemById(it) }

    /** `activeServerProvider.deleteMetaDatabase(id)`, unchanged. Test seam. */
    internal var deleteMetaDatabase: (Int) -> Unit = { activeServerProvider.deleteMetaDatabase(it) }

    /** Re-fetches the server list, exactly mirroring the legacy Fragment's own
     *  `onResume`-driven `getServerList()` call. */
    fun reload() {
        serverListLiveData?.removeObserver(serverListObserver)
        val liveData = serverListLoader()
        serverListLiveData = liveData
        liveData.observeForever(serverListObserver)
    }

    override fun onCleared() {
        serverListLiveData?.removeObserver(serverListObserver)
    }

    /** `activeServerProvider.setActiveServerById(server.id)`, unchanged - navigation back to
     *  Home stays the Fragment's own responsibility. */
    fun selectServer(row: ServerSelectorRow) {
        setActiveServer(row.id)
    }

    fun requestDelete(row: ServerSelectorRow) {
        _uiState.update { it.copy(pendingDelete = row) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /**
     * The exact legacy `ServerSelectorFragment.deleteServerById` sequence, preserved as-is
     * (including its own `// FIXME`-flagged quirk, not silently fixed here): `activeServerId` is
     * captured *before* the delete, and [deleteMetaDatabase] is called with that pre-delete
     * active id - which is only ever the *deleted* server's own id when the deleted server was
     * the active one. Deleting a different, non-active server therefore clears the *surviving*
     * active server's metadata cache instead of the deleted server's own (whose cache file is
     * left orphaned) - a pre-existing behavior, documented in the phase 5A2 audit, not changed by
     * this migration.
     */
    fun confirmDelete() {
        val target = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }

        val activeServerId = activeServerIdReader()
        if (target.id == activeServerId) {
            setActiveServer(ActiveServerProvider.OFFLINE_DB_ID)
        }
        deleteServerById(target.id)
        deleteMetaDatabase(activeServerId)
        Timber.i("Server deleted, id: %s", target.id)
    }

    private fun publish(list: List<ServerSetting>) {
        val activeId = activeServerIdReader()
        val rows = buildList {
            add(ActiveServerProvider.OFFLINE_DB)
            addAll(list)
        }.mapIndexed { position, setting -> setting.toRow(position, activeId) }
        _uiState.update { it.copy(rows = rows.toImmutableList()) }
    }

    private fun ServerSetting.toRow(position: Int, activeId: Int): ServerSelectorRow {
        val isOffline = id == ActiveServerProvider.OFFLINE_DB_ID
        return ServerSelectorRow(
            id = id,
            position = position,
            name = name,
            description = if (isOffline) null else url,
            color = color,
            isOffline = isOffline,
            isActive = id == activeId,
        )
    }
}
