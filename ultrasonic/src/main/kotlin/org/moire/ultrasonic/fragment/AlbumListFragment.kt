/*
 * AlbumListFragment.kt
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.AlbumListViewModel
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.ui.albumlist.AlbumContextAction
import org.moire.ultrasonic.ui.albumlist.AlbumListActions
import org.moire.ultrasonic.ui.albumlist.AlbumListRow
import org.moire.ultrasonic.ui.albumlist.AlbumListScreen
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.ContextMenuUtil
import org.moire.ultrasonic.view.SortOrder

/**
 * Displays a list of Albums from the media library (issue #10 phase 4E2) - either a paged
 * `AlbumListType` browse (id3 or folder-mode, with infinite scroll) or, when reached "by
 * artist", one artist's full album list. A thin Compose host, following the phase 4E1
 * (`ArtistListFragment`) pattern: it owns the unchanged `albumListFragment` nav-graph boundary
 * and its args, the RxBus server/folder-change subscriptions, the genre-picker dialog (`BY_GENRE`
 * always re-prompts, exactly like the legacy screen), and the playback/download/navigation
 * commands; everything visible is [AlbumListScreen]. The Activity's Material toolbar was already
 * hidden for this destination before this phase (`NavigationActivity.hidesSupportActionBar`'s
 * base set already includes `albumListFragment`), so the shared `content_navigation_header`
 * supplies the back affordance and no title is ever visibly shown, matching the legacy screen -
 * this Fragment still calls [setTitle] only for parity with the legacy (also invisible)
 * ActionBar title.
 *
 * The legacy screen's `isStandalone` branch (`parentFragment !is MainFragment`, a `ViewPager2`
 * host that no longer exists since Library moved to Compose) is always true today, so this
 * Fragment always behaves like that branch: title set, full filter/sort controls shown, grid the
 * default layout. There is no surviving embedded/non-standalone mode to port.
 */
class AlbumListFragment : Fragment() {

    private val navArgs: AlbumListFragmentArgs by navArgs()
    private val viewModel: AlbumListViewModel by viewModels()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val serverSettingsModel: ServerSettingsModel by viewModel()

    private val rxBusSubscription = CompositeDisposable()
    private val fallbackChromeInset = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Same contract EntryListFragment used: refetch on server switch. Unlike a folder
        // change (see AlbumListViewModel.onFolderSelected's kdoc for the pre-existing gap this
        // preserves), a server switch does reload the album page, exactly like the legacy
        // `getLiveData(refresh = true)` call this mirrors.
        rxBusSubscription += RxBus.activeServerChangedObservable.subscribe {
            viewModel.load(refresh = true)
        }
        rxBusSubscription += RxBus.musicFolderChangedEventObservable.subscribe { folder ->
            if (!ActiveServerProvider.isOffline()) {
                val currentSetting = activeServerProvider.getActiveServer()
                currentSetting.musicFolderId = folder.id
                serverSettingsModel.updateItem(currentSetting)
            }
            viewModel.onFolderSelected()
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
                    AlbumListScreen(
                        state = state,
                        actions = albumListActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handler for the genre-selection dialog. Invoked if the user selects "By Genre" in the
        // sort-order menu - always re-shown on tap, exactly like the legacy screen (see
        // AlbumListViewModel.beginGenreSort's kdoc).
        childFragmentManager.setFragmentResultListener(
            ItemSelectionDialogFragment.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            if (bundle.getBoolean(ItemSelectionDialogFragment.RESULT_CANCELLED)) return@setFragmentResultListener
            val genreName = bundle.getString(ItemSelectionDialogFragment.RESULT_SELECTED_ITEM)
            if (genreName != null) viewModel.selectGenre(genreName)
        }

        setTitle(this, navArgs.title ?: getString(R.string.main_albums_title))

        viewModel.initialize(
            type = navArgs.type,
            byArtist = navArgs.byArtist,
            artistId = navArgs.id,
            artistName = navArgs.title,
            size = navArgs.size,
            offset = navArgs.offset,
        )
        viewModel.load(refresh = false, append = navArgs.append)
    }

    override fun onDestroy() {
        super.onDestroy()
        rxBusSubscription.dispose()
    }

    private val albumListActions: AlbumListActions by lazy {
        AlbumListActions(
            onEntryClick = ::onEntryClick,
            onContextAction = ::onContextAction,
            onSortOrderSelected = ::onSortOrderSelected,
            onLayoutTypeSelected = viewModel::setLayoutType,
            onFolderSelected = { folderId ->
                RxBus.musicFolderChangedEventPublisher.onNext(RxBus.Folder(folderId))
            },
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
        )
    }

    private fun onEntryClick(row: AlbumListRow) {
        val item = viewModel.itemFor(row.id) ?: return
        val action = NavigationGraphDirections.toTrackCollection(
            item.id,
            isAlbum = item.isDirectory,
            name = item.title,
            parentId = item.parent,
        )
        findNavController().navigate(action)
    }

    private fun onSortOrderSelected(order: SortOrder) {
        if (order != SortOrder.BY_GENRE) {
            viewModel.setSortOrder(order)
            return
        }
        // Immediately reflect the selection in the sort chip, then fetch the genre list and
        // show the picker dialog - exactly like AlbumListFragment.fetchAlbumsByGenre.
        viewModel.beginGenreSort()
        viewLifecycleOwner.lifecycleScope.launch {
            val genres = viewModel.loadGenres()
            if (genres.isEmpty()) return@launch
            val genreStrings = genres.map { it.name }.toTypedArray()
            if (childFragmentManager.findFragmentByTag(ItemSelectionDialogFragment.TAG) == null) {
                ItemSelectionDialogFragment.create(R.string.main_genres_title, genreStrings)
                    .show(childFragmentManager, ItemSelectionDialogFragment.TAG)
            }
        }
    }

    /**
     * Reuses the unchanged [ContextMenuUtil.handleContextMenu] dispatch with a real [MenuItem]
     * taken from a never-shown [PopupMenu], the same pattern `ArtistListFragment.onContextAction`
     * established in phase 4E1. `isArtist` is always `false` here - unlike Artist List, there is
     * no `START_RADIO` action to dispatch (see [AlbumContextAction]'s kdoc).
     */
    private fun onContextAction(row: AlbumListRow, action: AlbumContextAction) {
        val item = viewModel.itemFor(row.id) ?: return
        val menuItemId = when (action) {
            AlbumContextAction.PLAY_NOW -> R.id.menu_play_now
            AlbumContextAction.PLAY_NEXT -> R.id.menu_play_next
            AlbumContextAction.PLAY_LAST -> R.id.menu_play_last
            AlbumContextAction.DOWNLOAD -> R.id.menu_download
        }
        val menu = PopupMenu(requireContext(), requireView())
        menu.menuInflater.inflate(R.menu.context_menu_artist, menu.menu)
        val menuItem = menu.menu.findItem(menuItemId) ?: return
        ContextMenuUtil.handleContextMenu(
            menuItem = menuItem,
            item = item,
            isArtist = false,
            mediaPlayerManager = mediaPlayerManager,
            fragment = this,
        )
    }
}
