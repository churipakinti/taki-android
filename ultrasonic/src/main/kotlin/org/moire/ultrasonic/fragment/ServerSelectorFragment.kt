/*
 * ServerSelectorFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.MutableStateFlow
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.model.ServerSelectorViewModel
import org.moire.ultrasonic.ui.serverselector.ServerSelectorActions
import org.moire.ultrasonic.ui.serverselector.ServerSelectorRow
import org.moire.ultrasonic.ui.serverselector.ServerSelectorScreen
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * Displays the list of configured servers, they can be selected or edited.
 *
 * Post-issue-#10 residual migration (phase 5A2): now a thin Compose host, the same shape as
 * [CollectionListFragment] - it threads the live floating-chrome inset into
 * [ServerSelectorScreen] and owns navigation (select -> Home, Add/Edit -> the still-legacy
 * `editServerFragment`). Data and delete/select semantics live in [ServerSelectorViewModel],
 * unchanged from the legacy `ServerSettingsModel`/`ActiveServerProvider` calls.
 */
class ServerSelectorFragment : Fragment() {

    private val viewModel: ServerSelectorViewModel by viewModels()
    private val fallbackChromeInset = MutableStateFlow(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val chromeInsetFlow =
            (activity as? NavigationActivity)?.contentBottomInset ?: fallbackChromeInset
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TakiTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val chromeInsetPx by chromeInsetFlow.collectAsStateWithLifecycle()
                    val bottomInset = if (chromeInsetPx > 0) {
                        with(LocalDensity.current) { chromeInsetPx.toDp() }
                    } else {
                        TakiTheme.dimensions.contentInsetFloatingChrome
                    }
                    ServerSelectorScreen(
                        state = state,
                        actions = serverSelectorActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Matches the legacy Fragment's own onResume-driven getServerList() call, so returning
        // from Edit Server (add/edit/delete) always shows fresh data (docs section 18).
        viewModel.reload()
    }

    private val serverSelectorActions: ServerSelectorActions by lazy {
        ServerSelectorActions(
            onBack = { findNavController().navigateUp() },
            onServerClick = ::onServerClick,
            onAddServer = { editServer(-1) },
            // issue #10 phase 5A3: row.id (ServerSetting's stable primary key), not
            // row.position - the legacy screen-position/index contract this replaced could
            // desync from the actual DB row once ordering assumptions didn't hold (see the
            // phase 5A3 audit).
            onEditServer = { row -> editServer(row.id) },
            onDeleteRequested = viewModel::requestDelete,
            onDeleteConfirm = viewModel::confirmDelete,
            onDeleteCancel = viewModel::cancelDelete,
        )
    }

    private fun onServerClick(row: ServerSelectorRow) {
        viewModel.selectServer(row)
        findNavController().popBackStack(R.id.homeFragment, false)
    }

    private fun editServer(serverId: Int) {
        val action = ServerSelectorFragmentDirections.toEditServer(serverId)
        findNavController().navigate(action)
    }
}
