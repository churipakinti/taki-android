/*
 * PlaylistsFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputLayout
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.scope.ScopeFragment
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.CreatePlaylistFragment
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.DownloadState
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.DownloadAction
import org.moire.ultrasonic.util.DownloadUtil
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * Displays the playlists stored on the server
 *
 * TODO: This file has been converted from Java, but not modernized yet.
 */
@Suppress("InstanceOfCheckForException")
class PlaylistsFragment :
    ScopeFragment(),
    KoinScopeComponent,
    RefreshableFragment {
    override var swipeRefresh: SwipeRefreshLayout? = null
    private var playlistsView: GridView? = null
    private var emptyTextView: View? = null
    private var viewTypeToggle: Chip? = null
    private var playlistAdapter: PlaylistAdapter? = null
    private val playlistTracks = mutableMapOf<String, List<Track>>()
    private val playlistDownloadStates = mutableMapOf<String, PlaylistDownloadStatus>()
    private val rxBusSubscription = CompositeDisposable()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private var statusLoadGeneration = 0
    private var layoutMode = PlaylistLayoutMode.LIST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
        layoutMode = savedInstanceState?.getString(STATE_LAYOUT_MODE)
            ?.let { runCatching { PlaylistLayoutMode.valueOf(it) }.getOrNull() }
            ?: PlaylistLayoutMode.LIST
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.select_playlist, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        swipeRefresh = view.findViewById(R.id.select_playlist_refresh)
        playlistsView = view.findViewById(R.id.select_playlist_list)
        viewTypeToggle = view.findViewById(R.id.playlist_view_toggle)
        swipeRefresh?.setOnRefreshListener { load(true) }
        emptyTextView = view.findViewById(R.id.select_playlist_empty)
        viewTypeToggle?.setOnClickListener {
            layoutMode = if (layoutMode == PlaylistLayoutMode.LIST) {
                PlaylistLayoutMode.GRID
            } else {
                PlaylistLayoutMode.LIST
            }
            updateLayoutMode()
        }
        updateLayoutMode()
        registerForContextMenu(playlistsView!!)
        observeDownloadStates()
        setTitle(this, R.string.playlist_label)
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>(CreatePlaylistFragment.PLAYLIST_CREATED_RESULT)
            ?.observe(viewLifecycleOwner) { created ->
                if (created == true) {
                    findNavController().currentBackStackEntry?.savedStateHandle
                        ?.remove<Boolean>(CreatePlaylistFragment.PLAYLIST_CREATED_RESULT)
                    load(true)
                }
            }
        load(false)
    }

    private fun load(refresh: Boolean) {
        val generation = ++statusLoadGeneration
        val cacheCleaner: CacheCleaner by inject()
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val result = withContext(Dispatchers.IO) {
                val musicService = getMusicService()
                val playlists = musicService.getPlaylists(refresh)
                playlists
            }
            swipeRefresh?.isRefreshing = false
            withContext(Dispatchers.Main) {
                playlistTracks.clear()
                playlistDownloadStates.clear()
                result.forEach {
                    playlistDownloadStates[it.id] = PlaylistDownloadStatus.CHECKING
                }
                playlistAdapter = PlaylistAdapter(result)
                playlistsView?.adapter = playlistAdapter
                emptyTextView?.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
                resolveDownloadStates(result, generation)
            }
            if (!isOffline()) cacheCleaner.cleanPlaylists(result)
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, view, menuInfo)
        val inflater = requireActivity().menuInflater
        if (isOffline()) {
            inflater.inflate(
                R.menu.select_playlist_context_offline,
                menu
            )
        } else {
            inflater.inflate(R.menu.select_playlist_context, menu)
        }
        val downloadMenuItem = menu.findItem(R.id.playlist_menu_download)
        if (downloadMenuItem != null) {
            downloadMenuItem.isVisible = !isOffline()
            val info = menuInfo as? AdapterContextMenuInfo
            val playlist = info?.let {
                playlistsView?.getItemAtPosition(it.position) as? Playlist
            }
            if (playlistDownloadStates[playlist?.id] == PlaylistDownloadStatus.DOWNLOADED) {
                downloadMenuItem.setTitle(R.string.playlist_remove_download_title)
            }
        }
    }

    override fun onContextItemSelected(menuItem: MenuItem): Boolean {
        val info = menuItem.menuInfo as? AdapterContextMenuInfo
            ?: return super.onContextItemSelected(menuItem)
        val playlist = playlistsView?.getItemAtPosition(info.position) as? Playlist
            ?: return super.onContextItemSelected(menuItem)
        when (menuItem.itemId) {
            R.id.playlist_menu_pin -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.PIN,
                    fragment = this,
                    id = playlist.id,
                    name = playlist.name,
                    isShare = false,
                    isDirectory = false
                )
            }

            R.id.playlist_menu_unpin -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.UNPIN,
                    fragment = this,
                    id = playlist.id,
                    name = playlist.name,
                    isShare = false,
                    isDirectory = false
                )
            }

            R.id.playlist_menu_download -> {
                handleDownloadAction(playlist)
            }

            R.id.playlist_menu_play_now -> {
                val action = NavigationGraphDirections.toTrackCollection(
                    playlistId = playlist.id,
                    playlistName = playlist.name,
                    autoPlay = true
                )
                findNavController().navigate(action)
            }

            R.id.playlist_menu_play_shuffled -> {
                val action = NavigationGraphDirections.toTrackCollection(
                    playlistId = playlist.id,
                    playlistName = playlist.name,
                    autoPlay = true,
                    shuffle = true
                )

                findNavController().navigate(action)
            }

            R.id.playlist_menu_delete -> {
                deletePlaylist(playlist)
            }

            R.id.playlist_info -> {
                displayPlaylistInfo(playlist)
            }

            R.id.playlist_update_info -> {
                updatePlaylistInfo(playlist)
            }

            else -> {
                return super.onContextItemSelected(menuItem)
            }
        }
        return true
    }

    private fun handleDownloadAction(playlist: Playlist) {
        if (playlistDownloadStates[playlist.id] == PlaylistDownloadStatus.DOWNLOADED) {
            confirmRemoveDownload(playlist)
        } else {
            downloadPlaylist(playlist)
        }
    }

    private fun downloadPlaylist(playlist: Playlist) {
        val tracks = playlistTracks[playlist.id] ?: return
        if (tracks.isEmpty()) return
        playlistDownloadStates[playlist.id] = PlaylistDownloadStatus.DOWNLOADING
        playlistAdapter?.notifyDataSetChanged()
        DownloadUtil.justDownload(
            action = DownloadAction.DOWNLOAD,
            fragment = this,
            tracks = tracks
        )
    }

    private fun confirmRemoveDownload(playlist: Playlist) {
        val tracks = playlistTracks[playlist.id].orEmpty()
        if (tracks.isEmpty()) return
        ConfirmationDialog.Builder(requireContext())
            .setTitle(R.string.playlist_remove_download_title)
            .setMessage(getString(R.string.playlist_remove_download_message, playlist.name))
            .setPositiveButton(R.string.common_delete) { _, _ ->
                playlistDownloadStates[playlist.id] = PlaylistDownloadStatus.REMOVING
                playlistAdapter?.notifyDataSetChanged()
                DownloadUtil.justDownload(
                    action = DownloadAction.DELETE,
                    fragment = this,
                    tracks = tracks
                )
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    FileUtil.getPlaylistFile(
                        activeServerProvider.getActiveServer().name,
                        playlist.name
                    ).delete()
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun resolveDownloadStates(playlists: List<Playlist>, generation: Int) {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val resolved = withContext(Dispatchers.IO) {
                val service = getMusicService()
                playlists.map { playlist ->
                    val tracks = try {
                        service.getPlaylist(playlist.id, playlist.name)
                            .getChildren()
                            .filterIsInstance<Track>()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    playlist to tracks
                }
            }
            if (generation != statusLoadGeneration) return@launch
            resolved.forEach { (playlist, tracks) ->
                playlistTracks[playlist.id] = tracks
                playlistDownloadStates[playlist.id] = getPlaylistDownloadStatus(tracks)
            }
            playlistAdapter?.notifyDataSetChanged()
        }
    }

    private fun observeDownloadStates() {
        rxBusSubscription += RxBus.trackDownloadStateObservable.subscribe { event ->
            var changed = false
            playlistTracks.forEach { (playlistId, tracks) ->
                if (tracks.any { it.id == event.id }) {
                    val updatedStatus = getPlaylistDownloadStatus(tracks)
                    playlistDownloadStates[playlistId] = if (
                        playlistDownloadStates[playlistId] == PlaylistDownloadStatus.REMOVING &&
                        updatedStatus != PlaylistDownloadStatus.NOT_DOWNLOADED
                    ) {
                        PlaylistDownloadStatus.REMOVING
                    } else {
                        updatedStatus
                    }
                    changed = true
                }
            }
            if (changed) playlistAdapter?.notifyDataSetChanged()
        }
    }

    private fun getPlaylistDownloadStatus(tracks: List<Track>): PlaylistDownloadStatus {
        if (tracks.isEmpty()) return PlaylistDownloadStatus.EMPTY
        val states = tracks.map(DownloadService::getDownloadState)
        val downloaded = states.count { it == DownloadState.DONE || it == DownloadState.PINNED }
        return when {
            states.any {
                it == DownloadState.QUEUED || it == DownloadState.DOWNLOADING ||
                    it == DownloadState.RETRYING
            } -> PlaylistDownloadStatus.DOWNLOADING
            states.any { it == DownloadState.FAILED } -> PlaylistDownloadStatus.FAILED
            downloaded == tracks.size -> PlaylistDownloadStatus.DOWNLOADED
            downloaded > 0 -> PlaylistDownloadStatus.PARTIAL
            else -> PlaylistDownloadStatus.NOT_DOWNLOADED
        }
    }

    private fun openPlaylist(playlist: Playlist) {
        findNavController().navigate(
            NavigationGraphDirections.toTrackCollection(
                playlistId = playlist.id,
                playlistName = playlist.name
            )
        )
    }

    private fun showCreatePlaylistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.create_playlist, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.create_playlist_name)
        val inputLayout = dialogView as TextInputLayout
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
                    findNavController().navigate(
                        NavigationGraphDirections.toCreatePlaylist(name)
                    )
                }
            }
        }
        dialog.show()
    }

    private inner class PlaylistAdapter(items: List<Playlist>) : BaseAdapter() {
        private val entries = items.toMutableList()

        override fun getCount(): Int = entries.size + if (isOffline()) 0 else 1

        override fun getItem(position: Int): Playlist? = entries.getOrNull(position)

        override fun getItemId(position: Int): Long = position.toLong()

        fun remove(playlist: Playlist) {
            entries.remove(playlist)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val playlist = getItem(position)
            if (playlist == null) {
                val createTag = "${layoutMode.name}:create"
                return (convertView?.takeIf {
                    it.getTag(R.id.playlist_view_toggle) == createTag
                } ?: layoutInflater.inflate(
                    if (layoutMode == PlaylistLayoutMode.LIST) {
                        R.layout.list_item_create_playlist
                    } else {
                        R.layout.grid_item_create_playlist
                    },
                    parent,
                    false
                ).also { it.setTag(R.id.playlist_view_toggle, createTag) }).apply {
                    setOnClickListener { showCreatePlaylistDialog() }
                    setOnLongClickListener(null)
                }
            }

            val itemTag = "${layoutMode.name}:playlist"
            val row = convertView?.takeIf {
                it.getTag(R.id.playlist_view_toggle) == itemTag
            } ?: layoutInflater.inflate(
                if (layoutMode == PlaylistLayoutMode.LIST) {
                    R.layout.list_item_playlist
                } else {
                    R.layout.grid_item_playlist
                },
                parent,
                false
            ).also { it.setTag(R.id.playlist_view_toggle, itemTag) }
            row.findViewById<TextView>(R.id.playlist_name).text = playlist.name
            bindTrackCount(row, playlist)
            bindPlaylistCover(row, playlist)
            bindDownloadStatus(row, playlist)
            row.setOnClickListener { openPlaylist(playlist) }
            row.setOnLongClickListener {
                playlistsView?.showContextMenuForChild(row) ?: false
            }
            row.findViewById<MaterialButton>(R.id.playlist_download).apply {
                setOnClickListener { handleDownloadAction(playlist) }
            }
            return row
        }
    }

    private fun bindTrackCount(row: View, playlist: Playlist) {
        val count = playlistTracks[playlist.id]?.size
            ?: playlist.songCount.toIntOrNull()
            ?: 0
        row.findViewById<TextView>(R.id.playlist_song_count).text =
            resources.getQuantityString(R.plurals.n_songs, count, count)
    }

    private fun bindPlaylistCover(row: View, playlist: Playlist) {
        val coverContainer = row.findViewById<MaterialCardView>(R.id.playlist_cover_container)
        val coverGrid = row.findViewById<GridLayout>(R.id.playlist_cover_grid)
        val coverViews = listOf<ImageView>(
            row.findViewById(R.id.playlist_cover_1),
            row.findViewById(R.id.playlist_cover_2),
            row.findViewById(R.id.playlist_cover_3),
            row.findViewById(R.id.playlist_cover_4)
        )
        val tracksWithCovers = playlistTracks[playlist.id]
            .orEmpty()
            .filter { !it.coverArt.isNullOrBlank() }
            .distinctBy { it.coverArt }
            .take(MAX_COLLAGE_COVERS)
        coverContainer.setCardBackgroundColor(getPlaylistFrameColor(row))
        val outerPaddingDp = if (layoutMode == PlaylistLayoutMode.GRID) {
            GRID_COLLAGE_OUTER_PADDING_DP
        } else {
            LIST_COLLAGE_OUTER_PADDING_DP
        }
        val outerPaddingPx = (outerPaddingDp * resources.displayMetrics.density).toInt()
        coverGrid.setPadding(outerPaddingPx, outerPaddingPx, outerPaddingPx, outerPaddingPx)

        coverViews.forEachIndexed { index, cover ->
            cover.setImageDrawable(null)
            cover.tag = "${playlist.id}:$index"
            cover.isVisible = index < tracksWithCovers.size
        }
        row.findViewById<ImageView>(R.id.playlist_default_icon).isVisible =
            tracksWithCovers.isEmpty()

        if (tracksWithCovers.isNotEmpty()) {
            imageLoaderProvider.executeOn { imageLoader ->
                tracksWithCovers.forEachIndexed { index, track ->
                    val cover = coverViews[index]
                    if (cover.tag == "${playlist.id}:$index") {
                        imageLoader.loadImage(
                            cover,
                            track,
                            false,
                            0,
                            R.drawable.unknown_album
                        )
                    }
                }
            }
        }
    }

    private fun getPlaylistFrameColor(row: View): Int =
        if (layoutMode == PlaylistLayoutMode.LIST) {
            Color.BLACK
        } else {
            MaterialColors.getColor(
                row,
                com.google.android.material.R.attr.colorOutlineVariant
            )
        }

    private fun updateLayoutMode() {
        val showingGrid = layoutMode == PlaylistLayoutMode.GRID
        viewTypeToggle?.apply {
            setChipIconResource(
                if (showingGrid) {
                    R.drawable.ic_baseline_view_list
                } else {
                    R.drawable.ic_baseline_view_grid
                }
            )
            contentDescription = getString(
                if (showingGrid) R.string.list_view else R.string.grid_view
            )
        }
        playlistsView?.apply {
            numColumns = if (showingGrid) GRID_COLUMN_COUNT else 1
            horizontalSpacing = if (showingGrid) {
                (GRID_SPACING_DP * resources.displayMetrics.density).toInt()
            } else {
                0
            }
            verticalSpacing = if (showingGrid) {
                (GRID_SPACING_DP * resources.displayMetrics.density).toInt()
            } else {
                (LIST_SPACING_DP * resources.displayMetrics.density).toInt()
            }
            val horizontalPadding = if (showingGrid) {
                (GRID_HORIZONTAL_PADDING_DP * resources.displayMetrics.density).toInt()
            } else {
                (LIST_HORIZONTAL_PADDING_DP * resources.displayMetrics.density).toInt()
            }
            setPadding(horizontalPadding, paddingTop, horizontalPadding, paddingBottom)
            val adapterToRebind = playlistAdapter
            if (adapterToRebind != null) {
                adapter = null
                adapter = adapterToRebind
            }
            requestLayout()
        }
    }

    private fun bindDownloadStatus(row: View, playlist: Playlist) {
        val status = playlistDownloadStates[playlist.id] ?: PlaylistDownloadStatus.CHECKING
        val statusText = row.findViewById<TextView>(R.id.playlist_download_status)
        val action = row.findViewById<MaterialButton>(R.id.playlist_download)
        val progress = row.findViewById<ProgressBar>(R.id.playlist_download_progress)

        statusText.setText(status.textResource)
        val busy = status == PlaylistDownloadStatus.CHECKING ||
            status == PlaylistDownloadStatus.DOWNLOADING ||
            status == PlaylistDownloadStatus.REMOVING
        progress.isVisible = busy
        action.isVisible = !busy && !isOffline() && status != PlaylistDownloadStatus.EMPTY
        action.setIconResource(
            if (status == PlaylistDownloadStatus.DOWNLOADED) {
                R.drawable.ic_menu_remove_all
            } else {
                R.drawable.ic_menu_download
            }
        )
        action.contentDescription = getString(
            if (status == PlaylistDownloadStatus.DOWNLOADED) {
                R.string.playlist_remove_download_description
            } else {
                R.string.playlist_download_description
            }
        )
    }

    override fun onDestroyView() {
        rxBusSubscription.clear()
        viewTypeToggle = null
        playlistsView = null
        swipeRefresh = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_LAYOUT_MODE, layoutMode.name)
        super.onSaveInstanceState(outState)
    }

    private enum class PlaylistDownloadStatus(val textResource: Int) {
        CHECKING(R.string.playlist_status_checking),
        NOT_DOWNLOADED(R.string.playlist_status_not_downloaded),
        DOWNLOADING(R.string.playlist_status_downloading),
        DOWNLOADED(R.string.playlist_status_downloaded),
        PARTIAL(R.string.playlist_status_partial),
        FAILED(R.string.playlist_status_failed),
        EMPTY(R.string.playlist_status_empty),
        REMOVING(R.string.playlist_status_removing)
    }

    private enum class PlaylistLayoutMode {
        LIST,
        GRID
    }

    companion object {
        private const val STATE_LAYOUT_MODE = "playlist_layout_mode"
        private const val MAX_COLLAGE_COVERS = 4
        private const val GRID_COLUMN_COUNT = 2
        private const val GRID_SPACING_DP = 2
        private const val GRID_HORIZONTAL_PADDING_DP = 6
        private const val GRID_COLLAGE_OUTER_PADDING_DP = 5
        private const val LIST_COLLAGE_OUTER_PADDING_DP = 2
        private const val LIST_SPACING_DP = 4
        private const val LIST_HORIZONTAL_PADDING_DP = 6
    }

    private fun deletePlaylist(playlist: Playlist) {
        ConfirmationDialog.Builder(requireContext()).setIcon(R.drawable.ic_baseline_warning)
            .setTitle(R.string.common_confirm).setMessage(
                resources.getString(R.string.delete_playlist, playlist.name)
            ).setPositiveButton(R.string.common_ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(
                    toastingExceptionHandler(
                        resources.getString(
                            R.string.menu_deleted_playlist_error,
                            playlist.name
                        )
                    )
                ) {
                    withContext(Dispatchers.IO) {
                        val musicService = getMusicService()
                        musicService.deletePlaylist(playlist.id)
                    }

                    withContext(Dispatchers.Main) {
                        playlistAdapter?.remove(playlist)
                        playlistAdapter?.notifyDataSetChanged()
                        toast(
                            resources.getString(R.string.menu_deleted_playlist, playlist.name)
                        )
                    }
                }
            }.setNegativeButton(R.string.common_cancel, null).show()
    }

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
                    resources.getString(
                        R.string.playlist_updated_info_error,
                        playlist.name
                    )
                )
            ) {
                val nameBoxText = nameBox.text
                val commentBoxText = commentBox.text
                val name = nameBoxText?.toString()
                val comment = commentBoxText?.toString()
                val musicService = getMusicService()

                withContext(Dispatchers.IO) {
                    musicService.updatePlaylist(playlist.id, name, comment, publicBox.isChecked)
                }

                withContext(Dispatchers.Main) {
                    load(true)
                    toast(
                        resources.getString(R.string.playlist_updated_info, playlist.name)
                    )
                }
            }
        }
        alertDialog.setNegativeButton(R.string.common_cancel, null)
        alertDialog.show()
    }
}
