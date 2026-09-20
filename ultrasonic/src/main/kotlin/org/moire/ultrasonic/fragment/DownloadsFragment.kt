/*
 * DownloadsFragment.kt
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.DownloadsEvent
import org.moire.ultrasonic.model.DownloadsViewModel
import org.moire.ultrasonic.ui.downloads.DownloadedAlbumRow
import org.moire.ultrasonic.ui.downloads.DownloadsActions
import org.moire.ultrasonic.ui.downloads.DownloadsScreen
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Util.toast

/**
 * A download manager: the albums that currently have downloaded/pinned tracks (issue #10 phase
 * 4G3) - a thin Compose host that owns the unchanged `downloadsFragment` nav-graph boundary (no
 * arguments), the tap-to-open navigation and the toasts; everything visible is
 * [DownloadsScreen]. Tapping an album opens [DownloadedAlbumFragment]; the trash icon removes
 * its downloaded tracks directly from this screen.
 *
 * The Activity's Material toolbar was already hidden for this destination before this phase
 * (`NavigationActivity.hidesSupportActionBar`'s base set includes `downloadsFragment`), so the
 * shared `content_navigation_header` supplies the back affordance. There is no RxBus subscription
 * - the legacy screen had none.
 */
class DownloadsFragment : Fragment() {

    private val viewModel: DownloadsViewModel by viewModels()
    private val fallbackChromeInset = MutableStateFlow(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val chromeInsetFlow = (activity as? NavigationActivity)?.contentBottomInset
            ?: fallbackChromeInset
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
                    DownloadsScreen(
                        state = state,
                        actions = downloadsActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, R.string.menu_downloads)
        // Re-query on every view creation (as the legacy screen did), so coming back from a
        // downloaded album shows current data.
        viewModel.load()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::onEvent)
            }
        }
    }

    private val downloadsActions: DownloadsActions by lazy {
        DownloadsActions(
            onAlbumClick = ::onAlbumClick,
            onRemoveClick = { viewModel.removeAlbum(it.id) },
            onRefresh = viewModel::refresh,
        )
    }

    private fun onAlbumClick(row: DownloadedAlbumRow) {
        findNavController().navigate(
            NavigationGraphDirections.toDownloadedAlbum(
                id = row.id,
                name = row.title,
            ),
        )
    }

    private fun onEvent(event: DownloadsEvent) {
        when (event) {
            is DownloadsEvent.Removed -> toast(
                resources.getQuantityString(
                    R.plurals.n_songs_deleted,
                    event.songCount,
                    event.songCount,
                ),
            )
            is DownloadsEvent.Error -> toast(
                " ${CommunicationError.getErrorMessage(event.cause)}",
                shortDuration = false,
            )
        }
    }
}
