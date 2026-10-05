/*
 * PlaylistListFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.PlaylistListViewModel
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.ui.components.TakiConfirmSheet
import org.moire.ultrasonic.ui.playlistlist.CreatePlaylistNameActions
import org.moire.ultrasonic.ui.playlistlist.CreatePlaylistNameSheet
import org.moire.ultrasonic.ui.playlistlist.PlaylistContextAction
import org.moire.ultrasonic.ui.playlistlist.PlaylistInfoSheet
import org.moire.ultrasonic.ui.playlistlist.PlaylistInfoUiState
import org.moire.ultrasonic.ui.playlistlist.PlaylistListActions
import org.moire.ultrasonic.ui.playlistlist.PlaylistListRow
import org.moire.ultrasonic.ui.playlistlist.PlaylistListScreen
import org.moire.ultrasonic.ui.playlistlist.PlaylistRowDownloadStatus
import org.moire.ultrasonic.ui.playlistlist.UpdatePlaylistInfoSheet
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.DownloadAction
import org.moire.ultrasonic.util.DownloadUtil
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * Displays the playlists stored on the server (issue #10 phase 4G1) - a thin Compose host,
 * following the phase 4E1/4E2 (`ArtistListFragment`/`AlbumListFragment`) pattern: it owns the
 * unchanged `playlistsFragment` nav-graph boundary (no arguments), the
 * `RxBus.trackDownloadStateObservable` subscription, the create/rename/delete/info dialogs
 * (reused close to verbatim from the legacy `org.moire.ultrasonic.fragment.legacy.PlaylistsFragment`
 * this replaces), and the playback/navigation commands; everything visible is
 * [PlaylistListScreen]. The Activity's Material toolbar was already hidden for this destination
 * before this phase (`NavigationActivity.hidesSupportActionBar`'s base set already includes
 * `playlistsFragment`), so the shared `content_navigation_header` supplies the back affordance
 * and no title is ever visibly shown, matching the legacy screen - this Fragment still calls
 * [setTitle] only for parity with the legacy (also invisible) ActionBar title.
 *
 * Unlike Album/Artist List, there is no `RxBus.activeServerChangedObservable`/
 * `musicFolderChangedEventObservable` subscription here either - the legacy screen never had one
 * (a pre-existing gap: switching servers while this screen is open does not auto-refresh it),
 * preserved rather than "fixed" (see the phase 4G1 report).
 */
class PlaylistListFragment : Fragment() {

    private val viewModel: PlaylistListViewModel by viewModels()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val rxBusSubscription = CompositeDisposable()
    private val fallbackChromeInset = MutableStateFlow(0)

    /** Whether the Create Playlist naming sheet is open (issue #10 phase 4M3), replacing the
     *  legacy `AlertDialog`. [createPlaylistName]/[createPlaylistError] are host-owned, matching
     *  every other migrated screen's "Compose reads, host owns the value" contract. */
    private val showCreatePlaylistSheet = mutableStateOf(false)
    private val createPlaylistName = mutableStateOf("")
    private val createPlaylistError = mutableStateOf<String?>(null)

    /** The one transient confirmation/info/form sheet this screen may show (issue #10 phase 5A6,
     *  replacing four app-owned dialogs). Plain data, no Context; `null` = nothing open. */
    private sealed interface Overlay {
        data class RemoveDownload(val playlist: Playlist, val tracks: List<Track>) : Overlay
        data class Info(val info: PlaylistInfoUiState) : Overlay
        data class UpdateInfo(val playlist: Playlist) : Overlay
        data class Delete(val playlist: Playlist) : Overlay
    }

    private val overlay = mutableStateOf<Overlay?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rxBusSubscription += RxBus.trackDownloadStateObservable.subscribe { event ->
            viewModel.onTrackDownloadStateChanged(event.id)
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
                    Box(Modifier.fillMaxSize()) {
                        PlaylistListScreen(
                            state = state,
                            actions = playlistListActions,
                            bottomContentInset = bottomInset,
                        )
                        CreatePlaylistNameSheet(
                            visible = showCreatePlaylistSheet.value,
                            name = createPlaylistName.value,
                            errorMessage = createPlaylistError.value,
                            actions = createPlaylistNameActions(),
                            bottomContentInset = bottomInset,
                        )
                        OverlayHost(bottomInset)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setTitle(this, getString(R.string.playlist_label))

        // The result CreatePlaylistFragment (unchanged, out of scope) reports back after a
        // successful create - exactly the legacy screen's own savedStateHandle observation.
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>(CreatePlaylistFragment.PLAYLIST_CREATED_RESULT)
            ?.observe(viewLifecycleOwner) { created ->
                if (created == true) {
                    findNavController().currentBackStackEntry?.savedStateHandle
                        ?.remove<Boolean>(CreatePlaylistFragment.PLAYLIST_CREATED_RESULT)
                    viewModel.load(refresh = true)
                }
            }

        viewModel.load(refresh = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        rxBusSubscription.dispose()
    }

    private val playlistListActions: PlaylistListActions by lazy {
        PlaylistListActions(
            onEntryClick = ::onEntryClick,
            onContextAction = ::onContextAction,
            onLayoutTypeSelected = viewModel::setLayoutType,
            onCreatePlaylist = ::showCreatePlaylistDialog,
            onRefresh = viewModel::refresh,
        )
    }

    private fun onEntryClick(row: PlaylistListRow) {
        findNavController().navigate(
            NavigationGraphDirections.toTrackCollection(playlistId = row.id, playlistName = row.name),
        )
    }

    private fun onContextAction(row: PlaylistListRow, action: PlaylistContextAction) {
        val playlist = viewModel.playlistFor(row.id) ?: return
        when (action) {
            PlaylistContextAction.INFO -> displayPlaylistInfo(playlist)
            PlaylistContextAction.PLAY_NOW -> playPlaylist(playlist, shuffle = false)
            PlaylistContextAction.PLAY_SHUFFLED -> playPlaylist(playlist, shuffle = true)
            PlaylistContextAction.DOWNLOAD -> handleDownloadAction(playlist, row.downloadStatus)
            PlaylistContextAction.UPDATE_INFO -> updatePlaylistInfo(playlist)
            PlaylistContextAction.DELETE -> deletePlaylist(playlist)
        }
    }

    private fun playPlaylist(playlist: Playlist, shuffle: Boolean) {
        findNavController().navigate(
            NavigationGraphDirections.toTrackCollection(
                playlistId = playlist.id,
                playlistName = playlist.name,
                autoPlay = true,
                shuffle = shuffle,
            ),
        )
    }

    // ---- Download / remove download (legacy handleDownloadAction/downloadPlaylist/confirmRemoveDownload) ----

    private fun handleDownloadAction(playlist: Playlist, status: PlaylistRowDownloadStatus) {
        if (status == PlaylistRowDownloadStatus.DOWNLOADED) {
            confirmRemoveDownload(playlist)
        } else {
            downloadPlaylist(playlist)
        }
    }

    private fun downloadPlaylist(playlist: Playlist) {
        val tracks = viewModel.tracksFor(playlist.id) ?: return
        if (tracks.isEmpty()) return
        viewModel.setDownloadStatusOptimistic(playlist.id, PlaylistRowDownloadStatus.DOWNLOADING)
        DownloadUtil.justDownload(action = DownloadAction.DOWNLOAD, fragment = this, tracks = tracks)
    }

    private fun confirmRemoveDownload(playlist: Playlist) {
        val tracks = viewModel.tracksFor(playlist.id).orEmpty()
        if (tracks.isEmpty()) return
        overlay.value = Overlay.RemoveDownload(playlist, tracks)
    }

    private fun onRemoveDownloadConfirmed(target: Overlay.RemoveDownload) {
        overlay.value = null
        val playlist = target.playlist
        viewModel.setDownloadStatusOptimistic(playlist.id, PlaylistRowDownloadStatus.REMOVING)
        DownloadUtil.justDownload(action = DownloadAction.DELETE, fragment = this, tracks = target.tracks)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            FileUtil.getPlaylistFile(activeServerProvider.getActiveServer().name, playlist.name).delete()
        }
    }

    // ---- Create (issue #10 phase 4M3: Compose naming sheet, replacing the legacy AlertDialog) --

    private fun showCreatePlaylistDialog() {
        createPlaylistName.value = ""
        createPlaylistError.value = null
        showCreatePlaylistSheet.value = true
    }

    private fun createPlaylistNameActions(): CreatePlaylistNameActions = CreatePlaylistNameActions(
        onNameChange = {
            createPlaylistName.value = it
            createPlaylistError.value = null
        },
        onCreate = {
            val name = createPlaylistName.value.trim()
            if (name.isBlank()) {
                createPlaylistError.value = getString(R.string.playlist_name_required)
            } else {
                showCreatePlaylistSheet.value = false
                findNavController().navigate(NavigationGraphDirections.toCreatePlaylist(name))
            }
        },
        onDismiss = { showCreatePlaylistSheet.value = false },
    )

    // ---- Info / Update info / Delete (issue #10 phase 5A6: Compose sheets) ------------------

    private fun displayPlaylistInfo(playlist: Playlist) {
        overlay.value = Overlay.Info(
            PlaylistInfoUiState(
                name = playlist.name,
                owner = playlist.owner,
                comment = playlist.comment,
                songCount = playlist.songCount,
                isPublic = playlist.public,
                created = playlist.created,
            ),
        )
    }

    private fun updatePlaylistInfo(playlist: Playlist) {
        overlay.value = Overlay.UpdateInfo(playlist)
    }

    private fun onUpdateInfoConfirmed(
        playlist: Playlist,
        name: String,
        comment: String,
        isPublic: Boolean,
    ) {
        overlay.value = null
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler(
                getString(R.string.playlist_updated_info_error, playlist.name)
            )
        ) {
            withContext(Dispatchers.IO) {
                getMusicService().updatePlaylist(playlist.id, name, comment, isPublic)
            }

            withContext(Dispatchers.Main) {
                viewModel.load(refresh = true)
                toast(getString(R.string.playlist_updated_info, playlist.name))
            }
        }
    }

    private fun deletePlaylist(playlist: Playlist) {
        overlay.value = Overlay.Delete(playlist)
    }

    private fun onDeleteConfirmed(playlist: Playlist) {
        overlay.value = null
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler(
                getString(R.string.menu_deleted_playlist_error, playlist.name)
            )
        ) {
            withContext(Dispatchers.IO) {
                getMusicService().deletePlaylist(playlist.id)
            }

            withContext(Dispatchers.Main) {
                viewModel.removePlaylist(playlist.id)
                toast(getString(R.string.menu_deleted_playlist, playlist.name))
            }
        }
    }

    /** Draws the open [overlay], if any, above the playlist list. */
    @Composable
    private fun OverlayHost(bottomInset: Dp) {
        val dismiss = { overlay.value = null }
        val cancelLabel = stringResource(R.string.common_cancel)
        when (val current = overlay.value) {
            null -> Unit
            is Overlay.RemoveDownload -> TakiConfirmSheet(
                title = stringResource(R.string.playlist_remove_download_title),
                message = stringResource(
                    R.string.playlist_remove_download_message,
                    current.playlist.name,
                ),
                confirmLabel = stringResource(R.string.common_delete),
                dismissLabel = cancelLabel,
                onConfirm = { onRemoveDownloadConfirmed(current) },
                onDismiss = dismiss,
                bottomContentInset = bottomInset,
                sheetTestTag = REMOVE_DOWNLOAD_SHEET_TEST_TAG,
            )
            is Overlay.Info -> PlaylistInfoSheet(
                info = current.info,
                onDismiss = dismiss,
                bottomContentInset = bottomInset,
            )
            is Overlay.UpdateInfo -> UpdatePlaylistInfoSheet(
                initialName = current.playlist.name,
                initialComment = current.playlist.comment,
                initialPublic = current.playlist.public,
                onConfirm = { name, comment, isPublic ->
                    onUpdateInfoConfirmed(current.playlist, name, comment, isPublic)
                },
                onDismiss = dismiss,
                bottomContentInset = bottomInset,
            )
            is Overlay.Delete -> TakiConfirmSheet(
                title = stringResource(R.string.common_confirm),
                message = stringResource(R.string.delete_playlist, current.playlist.name),
                confirmLabel = stringResource(R.string.common_delete),
                dismissLabel = cancelLabel,
                onConfirm = { onDeleteConfirmed(current.playlist) },
                onDismiss = dismiss,
                bottomContentInset = bottomInset,
                sheetTestTag = DELETE_PLAYLIST_SHEET_TEST_TAG,
            )
        }
    }

    companion object {
        /** Lets tests and the Pixel validation find the confirmations this Fragment hosts. */
        const val REMOVE_DOWNLOAD_SHEET_TEST_TAG = "playlist_remove_download_sheet"
        const val DELETE_PLAYLIST_SHEET_TEST_TAG = "playlist_delete_sheet"
    }
}
