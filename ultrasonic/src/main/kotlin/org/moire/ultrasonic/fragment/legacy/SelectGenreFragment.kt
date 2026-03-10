/*
 * SelectGenreFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Settings.maxSongs
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.GenreAdapter

/**
 * Displays the available genres in the media library
 */
class SelectGenreFragment :
    Fragment(),
    RefreshableFragment {
    override var swipeRefresh: SwipeRefreshLayout? = null
    private var genreListView: ListView? = null
    private var emptyView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.select_genre, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.select_genre_refresh)
        genreListView = view.findViewById(R.id.select_genre_list)
        swipeRefresh?.setOnRefreshListener { load(true) }

        genreListView?.setOnItemClickListener {
                parent: AdapterView<*>,
                _: View?,
                position: Int,
                _: Long
            ->
            val genre = parent.getItemAtPosition(position) as Genre

            val action = NavigationGraphDirections.toTrackCollection(
                genreName = genre.name,
                size = maxSongs,
                offset = 0
            )
            findNavController().navigate(action)
        }
        emptyView = view.findViewById(R.id.select_genre_empty)
        registerForContextMenu(genreListView!!)
        setTitle(this, R.string.main_genres_title)
        load(false)
    }

    private fun load(refresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val result = withContext(Dispatchers.IO) {
                val musicService = getMusicService()
                musicService.getGenres(refresh)
            }
            swipeRefresh?.isRefreshing = false
            withContext(Dispatchers.Main) {
                emptyView?.isVisible = result.isEmpty()
                genreListView?.adapter = GenreAdapter(requireContext(), result)
            }
        }
    }
}
