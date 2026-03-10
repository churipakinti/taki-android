/*
 * PlaylistsFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.annotation.SuppressLint
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
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.scope.ScopeFragment
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.DownloadAction
import org.moire.ultrasonic.util.DownloadUtil
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
    private var playlistsListView: ListView? = null
    private var emptyTextView: View? = null
    private var playlistAdapter: ArrayAdapter<Playlist>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.select_playlist, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        swipeRefresh = view.findViewById(R.id.select_playlist_refresh)
        playlistsListView = view.findViewById(R.id.select_playlist_list)
        swipeRefresh?.setOnRefreshListener { load(true) }
        emptyTextView = view.findViewById(R.id.select_playlist_empty)
        playlistsListView?.setOnItemClickListener { parent, _, position, _ ->
            val (id, name) = parent.getItemAtPosition(position) as Playlist

            val action = NavigationGraphDirections.toTrackCollection(
                id = id,
                playlistId = id,
                name = name,
                playlistName = name
            )
            findNavController().navigate(action)
        }
        registerForContextMenu(playlistsListView!!)
        setTitle(this, R.string.playlist_label)
        load(false)
    }

    private fun load(refresh: Boolean) {
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
                playlistAdapter =
                    ArrayAdapter(requireContext(), R.layout.list_item_generic, result)
                playlistsListView?.adapter = playlistAdapter
                emptyTextView?.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
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
        }
    }

    override fun onContextItemSelected(menuItem: MenuItem): Boolean {
        val info = menuItem.menuInfo as AdapterContextMenuInfo
        val playlist = playlistsListView?.getItemAtPosition(info.position) as Playlist
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
                DownloadUtil.justDownload(
                    action = DownloadAction.DOWNLOAD,
                    fragment = this,
                    id = playlist.id,
                    name = playlist.name,
                    isShare = false,
                    isDirectory = false
                )
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
