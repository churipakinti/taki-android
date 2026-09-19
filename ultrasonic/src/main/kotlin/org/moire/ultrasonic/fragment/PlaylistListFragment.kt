/*
 * PlaylistListFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
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
import org.moire.ultrasonic.ui.playlistlist.PlaylistContextAction
import org.moire.ultrasonic.ui.playlistlist.PlaylistListActions
import org.moire.ultrasonic.ui.playlistlist.PlaylistListRow
import org.moire.ultrasonic.ui.playlistlist.PlaylistListScreen
import org.moire.ultrasonic.ui.playlistlist.PlaylistRowDownloadStatus
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.DownloadAction
import org.moire.ultrasonic.util.DownloadUtil
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.InfoDialog
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
                    PlaylistListScreen(
                        state = state,
                        actions = playlistListActions,
                        bottomContentInset = bottomInset,
                    )
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
        ConfirmationDialog.Builder(requireContext())
            .setTitle(R.string.playlist_remove_download_title)
            .setMessage(getString(R.string.playlist_remove_download_message, playlist.name))
            .setPositiveButton(R.string.common_delete) { _, _ ->
                viewModel.setDownloadStatusOptimistic(playlist.id, PlaylistRowDownloadStatus.REMOVING)
                DownloadUtil.justDownload(action = DownloadAction.DELETE, fragment = this, tracks = tracks)
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    FileUtil.getPlaylistFile(activeServerProvider.getActiveServer().name, playlist.name).delete()
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    // ---- Create (legacy showCreatePlaylistDialog, reused verbatim) ----------------------------

    private fun showCreatePlaylistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.create_playlist, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.create_playlist_name)
        val inputLayout = dialogView as com.google.android.material.textfield.TextInputLayout
        val dialog = ConfirmationDialog.Builder(requireContext())
            .setTitle(R.string.playlist_create)
            .setView(dialogView)
            .setPositiveButton(R.string.playlist_create_action, null)
            .setNegativeButton(R.string.common_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    inputLayout.error = getString(R.string.playlist_name_required)
                } else {
                    dialog.dismiss()
                    findNavController().navigate(NavigationGraphDirections.toCreatePlaylist(name))
                }
            }
        }
        dialog.show()
    }

    // ---- Info / Update info / Delete (legacy displayPlaylistInfo/updatePlaylistInfo/deletePlaylist) ----

    private fun displayPlaylistInfo(playlist: Playlist) {
        val textView = TextView(requireContext())
        textView.setPadding(5, 5, 5, 5)
        val message: Spannable = SpannableString(
            """
              Owner: ${playlist.owner}
              Comments: ${playlist.comment}
              Song Count: ${playlist.songCount}
            """.trimIndent() +
                if (playlist.public == null) {
                    ""
                } else {
                    """

 Public: ${playlist.public}
                    """.trimIndent() + """

  Creation Date: ${playlist.created.replace('T', ' ')}
                    """.trimIndent()
                }
        )
        Linkify.addLinks(message, Linkify.WEB_URLS)
        textView.text = message
        textView.movementMethod = LinkMovementMethod.getInstance()
        InfoDialog.Builder(requireContext()).setTitle(playlist.name).setCancelable(true)
            .setView(textView).show()
    }

    @SuppressLint("InflateParams")
    private fun updatePlaylistInfo(playlist: Playlist) {
        val dialogView = layoutInflater.inflate(R.layout.update_playlist, null) ?: return
        val nameBox = dialogView.findViewById<EditText>(R.id.get_playlist_name)
        val commentBox = dialogView.findViewById<EditText>(R.id.get_playlist_comment)
        val publicBox = dialogView.findViewById<CheckBox>(R.id.get_playlist_public)
        nameBox.setText(playlist.name)
        commentBox.setText(playlist.comment)
        val pub = playlist.public
        if (pub == null) {
            publicBox.isEnabled = false
        } else {
            publicBox.isChecked = pub
        }
        val alertDialog = ConfirmationDialog.Builder(requireContext())
        alertDialog.setIcon(R.drawable.ic_baseline_warning)
        alertDialog.setTitle(R.string.playlist_update_info)
        alertDialog.setView(dialogView)
        alertDialog.setPositiveButton(R.string.common_ok) { _, _ ->
            viewLifecycleOwner.lifecycleScope.launch(
                toastingExceptionHandler(
                    getString(R.string.playlist_updated_info_error, playlist.name)
                )
            ) {
                val name = nameBox.text?.toString()
                val comment = commentBox.text?.toString()

                withContext(Dispatchers.IO) {
                    getMusicService().updatePlaylist(playlist.id, name, comment, publicBox.isChecked)
                }

                withContext(Dispatchers.Main) {
                    viewModel.load(refresh = true)
                    toast(getString(R.string.playlist_updated_info, playlist.name))
                }
            }
        }
        alertDialog.setNegativeButton(R.string.common_cancel, null)
        alertDialog.show()
    }

    private fun deletePlaylist(playlist: Playlist) {
        ConfirmationDialog.Builder(requireContext()).setIcon(R.drawable.ic_baseline_warning)
            .setTitle(R.string.common_confirm).setMessage(
                getString(R.string.delete_playlist, playlist.name)
            ).setPositiveButton(R.string.common_ok) { _, _ ->
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
            }.setNegativeButton(R.string.common_cancel, null).show()
    }
}
