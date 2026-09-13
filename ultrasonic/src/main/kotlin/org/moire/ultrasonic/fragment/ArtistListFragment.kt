/*
 * ArtistListFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.ArtistListViewModel
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.ui.artistlist.ArtistContextAction
import org.moire.ultrasonic.ui.artistlist.ArtistListActions
import org.moire.ultrasonic.ui.artistlist.ArtistListRow
import org.moire.ultrasonic.ui.artistlist.ArtistListScreen
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.ContextMenuUtil

/**
 * Displays the list of Artists or Indexes (folders) from the media library (issue #10 phase
 * 4E1). A thin Compose host: it owns the unchanged `artistListFragment` nav-graph boundary and
 * its `refresh`/`title` arguments, the RxBus server/folder-change subscriptions, and the
 * playback/radio/download/navigation commands; everything visible is [ArtistListScreen]. The
 * Activity's Material toolbar is hidden for this destination exactly as before
 * (`NavigationActivity.hidesSupportActionBar`'s base set already includes
 * `artistListFragment` - unchanged by this phase), so the shared `content_navigation_header`
 * supplies the back affordance and no title is ever visibly shown, matching the legacy screen.
 */
class ArtistListFragment : Fragment() {

    private val navArgs: ArtistListFragmentArgs by navArgs()
    private val viewModel: ArtistListViewModel by viewModels()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val serverSettingsModel: ServerSettingsModel by viewModel()

    private val rxBusSubscription = CompositeDisposable()
    private val fallbackChromeInset = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Same contract EntryListFragment used: refetch on server switch, and react to a
        // folder change - whether it came from this screen's own TakiFolderSelectorHeader or
        // (while this screen isn't even the one on screen) the still-legacy Album List's
        // FolderSelectorBinder, exactly as before this phase.
        rxBusSubscription += RxBus.activeServerChangedObservable.subscribe {
            viewModel.load(refresh = true)
        }
        rxBusSubscription += RxBus.musicFolderChangedEventObservable.subscribe { folder ->
            if (!ActiveServerProvider.isOffline()) {
                val currentSetting = activeServerProvider.getActiveServer()
                currentSetting.musicFolderId = folder.id
                serverSettingsModel.updateItem(currentSetting)
            }
            viewModel.load(refresh = true)
        }
    }

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
                    ArtistListScreen(
                        state = state,
                        actions = artistListActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The shared content_navigation_header supplies the back affordance; the toolbar is
        // hidden for this destination, so this title is never visibly shown - kept only for
        // parity with the legacy screen's (also invisible) ActionBar title.
        setTitle(this, navArgs.title ?: getString(R.string.main_artists_title))

        viewModel.load(navArgs.refresh)
    }

    override fun onDestroy() {
        super.onDestroy()
        rxBusSubscription.dispose()
    }

    private val artistListActions: ArtistListActions by lazy {
        ArtistListActions(
            onEntryClick = ::onEntryClick,
            onContextAction = ::onContextAction,
            onSortOrderSelected = viewModel::setSortOrder,
            onLayoutTypeSelected = viewModel::setLayoutType,
            onFolderSelected = { folderId ->
                RxBus.musicFolderChangedEventPublisher.onNext(RxBus.Folder(folderId))
            },
            onRefresh = viewModel::refresh,
        )
    }

    private fun onEntryClick(row: ArtistListRow) {
        val item = viewModel.itemFor(row.id)
        val action = if (row.isIndex) {
            NavigationGraphDirections.toTrackCollection(
                id = row.id,
                name = row.name,
                parentId = row.id,
                isArtist = false,
            )
        } else {
            NavigationGraphDirections.toArtistDetail(
                artistId = row.id,
                artistName = item?.name ?: getString(R.string.common_artist),
                artistCoverArt = item?.coverArt,
            )
        }
        findNavController().navigate(action)
    }

    /**
     * Reuses the unchanged [ContextMenuUtil.handleContextMenu] dispatch with a real [MenuItem]
     * taken from a never-shown [PopupMenu], the same pattern
     * `TrackCollectionFragment.runTrackContextAction` already established for Compose Album
     * Detail's track context menu.
     */
    private fun onContextAction(row: ArtistListRow, action: ArtistContextAction) {
        val item = viewModel.itemFor(row.id) ?: Artist(id = row.id, name = row.name)
        val menuItemId = when (action) {
            ArtistContextAction.PLAY_NOW -> R.id.menu_play_now
            ArtistContextAction.PLAY_NEXT -> R.id.menu_play_next
            ArtistContextAction.PLAY_LAST -> R.id.menu_play_last
            ArtistContextAction.START_RADIO -> R.id.menu_start_radio
            ArtistContextAction.DOWNLOAD -> R.id.menu_download
        }
        val menu = PopupMenu(requireContext(), requireView())
        menu.menuInflater.inflate(R.menu.context_menu_artist, menu.menu)
        val menuItem = menu.menu.findItem(menuItemId) ?: return
        ContextMenuUtil.handleContextMenu(
            menuItem = menuItem,
            item = item,
            isArtist = !row.isIndex,
            mediaPlayerManager = mediaPlayerManager,
            fragment = this,
        )
    }
}
