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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Settings.MAX_SONGS
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
    private var genreListView: RecyclerView? = null
    private var genreAdapter: GenreAdapter? = null
    private var emptyView: View? = null
    private val requestedCovers = mutableSetOf<String>()
    private val coverSemaphore = Semaphore(MAX_CONCURRENT_COVER_REQUESTS)
    private var coverGeneration = 0

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

        genreAdapter = GenreAdapter(
            onItemClick = { genre ->
                val action = NavigationGraphDirections.toTrackCollection(
                    genreName = genre.name,
                    size = MAX_SONGS,
                    offset = 0
                )
                findNavController().navigate(action)
            },
            onCoverNeeded = ::loadGenreCover
        )
        genreListView?.layoutManager = GridLayoutManager(requireContext(), GENRE_COLUMNS)
        genreListView?.adapter = genreAdapter

        emptyView = view.findViewById(R.id.select_genre_empty)
        setTitle(this, R.string.main_genres_title)
        load(false)
    }

    private fun load(refresh: Boolean) {
        if (refresh) {
            coverGeneration++
            requestedCovers.clear()
        }

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
                genreAdapter?.submitGenres(result, clearCovers = refresh)
            }
        }
    }

    private fun loadGenreCover(genre: Genre) {
        if (!requestedCovers.add(genre.name)) return
        val generation = coverGeneration

        viewLifecycleOwner.lifecycleScope.launch {
            val coverTrack = withContext(Dispatchers.IO) {
                coverSemaphore.withPermit {
                    runCatching {
                        getMusicService()
                            .getSongsByGenre(genre.name, COVER_CANDIDATES, 0)
                            .getTracks()
                            .firstOrNull { !it.coverArt.isNullOrBlank() }
                    }.getOrNull()
                }
            }

            if (generation == coverGeneration) {
                genreAdapter?.setCover(genre.name, coverTrack)
            }
        }
    }

    companion object {
        private const val GENRE_COLUMNS = 2
        private const val COVER_CANDIDATES = 10
        private const val MAX_CONCURRENT_COVER_REQUESTS = 4
    }
}
