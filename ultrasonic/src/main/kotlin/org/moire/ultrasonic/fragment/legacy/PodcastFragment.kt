/*
 * PodcastFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.scope.ScopeFragment
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.PodcastsChannel
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * Displays the podcasts available on the server
 */
class PodcastFragment :
    ScopeFragment(),
    RefreshableFragment {
    private var emptyTextView: View? = null
    private var channelItemsListView: ListView? = null
    override var swipeRefresh: SwipeRefreshLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.podcasts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.podcasts_refresh)
        swipeRefresh?.setOnRefreshListener { load(true) }
        setTitle(this, R.string.podcasts_label)
        emptyTextView = view.findViewById(R.id.select_podcasts_empty)
        channelItemsListView = view.findViewById(R.id.podcasts_channels_items_list)
        channelItemsListView?.setOnItemClickListener { parent, _, position, _ ->
            val id = (parent.getItemAtPosition(position) as PodcastsChannel).id
            val action = NavigationGraphDirections.toTrackCollection(podcastChannelId = id)
            findNavController().navigate(action)
        }
        load(false)
    }

    private fun load(refresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val result = withContext(Dispatchers.IO) {
                val musicService = getMusicService()
                musicService.getPodcastsChannels(refresh)
            }
            swipeRefresh?.isRefreshing = false
            withContext(Dispatchers.Main) {
                channelItemsListView?.adapter =
                    ArrayAdapter(requireContext(), R.layout.list_item_generic, result)
                emptyTextView?.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
