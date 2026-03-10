/*
 * SharesFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
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
import org.moire.ultrasonic.domain.Share
import org.moire.ultrasonic.fragment.FragmentTitle
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.DownloadAction
import org.moire.ultrasonic.util.DownloadUtil
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.TimeSpanPicker
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.launchWithToast
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.ShareAdapter

/**
 * Displays the shares in the media library
 *
 * TODO: This file has been converted from Java, but not modernized yet.
 */
class SharesFragment :
    ScopeFragment(),
    KoinScopeComponent,
    RefreshableFragment {
    override var swipeRefresh: SwipeRefreshLayout? = null
    private var sharesListView: ListView? = null
    private var emptyTextView: View? = null
    private var shareAdapter: ShareAdapter? = null
    private val mediaPlayerManager: MediaPlayerManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.select_share, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        swipeRefresh = view.findViewById(R.id.select_share_refresh)
        sharesListView = view.findViewById(R.id.select_share_list)
        swipeRefresh!!.setOnRefreshListener { load(true) }
        emptyTextView = view.findViewById(R.id.select_share_empty)
        sharesListView!!.onItemClickListener = AdapterView.OnItemClickListener {
                parent,
                _,
                position,
                _
            ->
            val share = parent.getItemAtPosition(position) as Share

            val action = NavigationGraphDirections.toTrackCollection(
                shareId = share.id,
                shareName = share.name
            )
            findNavController().navigate(action)
        }
        registerForContextMenu(sharesListView!!)
        FragmentTitle.setTitle(this, R.string.button_bar_shares)
        load(false)
    }

    private fun load(refresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val result = withContext(Dispatchers.IO) {
                val musicService = MusicServiceFactory.getMusicService()
                musicService.getShares(refresh)
            }
            swipeRefresh?.isRefreshing = false
            withContext(Dispatchers.Main) {
                shareAdapter = ShareAdapter(requireContext(), result)
                sharesListView?.adapter = shareAdapter
                emptyTextView?.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        view: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, view, menuInfo)
        val inflater = requireActivity().menuInflater
        inflater.inflate(R.menu.select_share_context, menu)
    }

    override fun onContextItemSelected(menuItem: MenuItem): Boolean {
        val info = menuItem.menuInfo as AdapterView.AdapterContextMenuInfo
        val share = sharesListView!!.getItemAtPosition(info.position) as Share
        when (menuItem.itemId) {
            R.id.share_menu_pin -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.PIN,
                    fragment = this,
                    id = share.id,
                    name = share.name,
                    isShare = true,
                    isDirectory = false
                )
            }

            R.id.share_menu_unpin -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.UNPIN,
                    fragment = this,
                    id = share.id,
                    name = share.name,
                    isShare = true,
                    isDirectory = false
                )
            }

            R.id.share_menu_download -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.DOWNLOAD,
                    fragment = this,
                    id = share.id,
                    name = share.name,
                    isShare = true,
                    isDirectory = false
                )
            }

            R.id.share_menu_play_now -> {
                mediaPlayerManager.playTracksAndToast(
                    this,
                    insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
                    id = share.id,
                    name = share.name,
                    shuffle = false
                )
            }

            R.id.share_menu_play_shuffled -> {
                mediaPlayerManager.playTracksAndToast(
                    this,
                    insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
                    id = share.id,
                    name = share.name,
                    shuffle = true
                )
            }

            R.id.share_menu_delete -> {
                deleteShare(share)
            }

            R.id.share_info -> {
                displayShareInfo(share)
            }

            R.id.share_update_info -> {
                updateShareInfo(share)
            }

            else -> {
                return super.onContextItemSelected(menuItem)
            }
        }
        return true
    }

    private fun deleteShare(share: Share) {
        AlertDialog.Builder(context).setIcon(R.drawable.ic_baseline_warning)
            .setTitle(R.string.common_confirm).setMessage(
                resources.getString(R.string.delete_playlist, share.name)
            ).setPositiveButton(R.string.common_ok) { _, _ ->
                deleteShareOnServer(share)
            }.setNegativeButton(R.string.common_cancel, null).show()
    }

    private fun deleteShareOnServer(share: Share) {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler(
                resources.getString(
                    R.string.menu_deleted_share_error,
                    share.name
                )
            )
        ) {
            withContext(Dispatchers.IO) {
                val musicService = MusicServiceFactory.getMusicService()
                musicService.deleteShare(share.id)
            }

            withContext(Dispatchers.Main) {
                shareAdapter?.remove(share)
                shareAdapter?.notifyDataSetChanged()
                toast(
                    resources.getString(R.string.menu_deleted_share, share.name)
                )
            }
        }
    }

    private fun displayShareInfo(share: Share) {
        val textView = TextView(context)
        textView.setPadding(5, 5, 5, 5)
        val message: Spannable = SpannableString(
            """
                  Owner: ${share.username}
                  Comments: ${if (share.description == null) "" else share.description}
                  URL: ${share.url}
                  Entry Count: ${share.getEntries().size}
                  Visit Count: ${share.visitCount}
            """.trimIndent() +
                (
                    if (share.created == null) {
                        ""
                    } else {
                        """
     
                  Creation Date: ${share.created!!.replace('T', ' ')}
                        """.trimIndent()
                    }
                    ) +
                (
                    if (share.lastVisited == null) {
                        ""
                    } else {
                        """
     
                  Last Visited Date: ${share.lastVisited!!.replace('T', ' ')}
                        """.trimIndent()
                    }
                    ) +
                if (share.expires == null) {
                    ""
                } else {
                    """
     
                  Expiration Date: ${share.expires!!.replace('T', ' ')}
                    """.trimIndent()
                }
        )
        Linkify.addLinks(message, Linkify.WEB_URLS)
        textView.text = message
        textView.movementMethod = LinkMovementMethod.getInstance()
        AlertDialog.Builder(context).setTitle("Share Details").setCancelable(true)
            .setIcon(R.drawable.ic_baseline_info).setView(textView).show()
    }

    @SuppressLint("InflateParams")
    private fun updateShareInfo(share: Share) {
        val dialogView = layoutInflater.inflate(R.layout.share_details, null) ?: return
        val shareDescription = dialogView.findViewById<EditText>(R.id.share_description)
        val timeSpanPicker = dialogView.findViewById<TimeSpanPicker>(R.id.date_picker)
        shareDescription.setText(share.description)
        val hideDialogCheckBox = dialogView.findViewById<CheckBox>(R.id.hide_dialog)
        val saveAsDefaultsCheckBox = dialogView.findViewById<CheckBox>(R.id.save_as_defaults)
        val noExpirationCheckBox = dialogView.findViewById<CheckBox>(R.id.timeSpanDisableCheckBox)
        noExpirationCheckBox.setOnCheckedChangeListener { _, b ->
            timeSpanPicker.isEnabled = !b
        }
        noExpirationCheckBox.isChecked = true
        timeSpanPicker.setTimeSpanDisableText(resources.getText(R.string.no_expiration))
        hideDialogCheckBox.visibility = View.GONE
        saveAsDefaultsCheckBox.visibility = View.GONE
        val alertDialog = AlertDialog.Builder(context)
        alertDialog.setIcon(R.drawable.ic_baseline_warning)
        alertDialog.setTitle(R.string.playlist_update_info)
        alertDialog.setView(dialogView)
        alertDialog.setPositiveButton(R.string.common_ok) { _, _ ->
            var millis = timeSpanPicker.getTimeSpan()
            if (millis > 0) {
                millis += System.currentTimeMillis()
            }
            updateShareOnServer(millis, shareDescription.text.toString(), share)
        }
        alertDialog.setNegativeButton(R.string.common_cancel, null)
        alertDialog.show()
    }

    private fun updateShareOnServer(millis: Long, description: String, share: Share) {
        launchWithToast {
            withContext(Dispatchers.IO) {
                val musicService = MusicServiceFactory.getMusicService()
                musicService.updateShare(share.id, description, millis)
            }

            withContext(Dispatchers.Main) {
                load(true)
                resources.getString(R.string.playlist_updated_info, share.name)
            }
        }
    }
}
