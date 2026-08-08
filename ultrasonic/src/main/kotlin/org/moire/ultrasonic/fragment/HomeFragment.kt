/*
 * HomeFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.Calendar
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.adapters.HomeAlbumDelegate
import org.moire.ultrasonic.adapters.HomeShortcutDelegate
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.model.HomeViewModel
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.toastingExceptionHandler

private const val SHORTCUT_GRID_COLUMNS = 2
private const val MORNING_ENDS_AT_HOUR = 12
private const val AFTERNOON_ENDS_AT_HOUR = 18

/**
 * A Spotify-style "Home" screen showing recently played shortcuts and
 * horizontal shelves of albums. Built entirely from data the server
 * already exposes (recent, starred, newest, random, most played, plus
 * a daily mix of a random genre's tracks) - there is no recommendation
 * engine behind this.
 */
class HomeFragment : Fragment(), RefreshableFragment {

    private val homeViewModel: HomeViewModel by viewModels()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override var swipeRefresh: SwipeRefreshLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.home_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setGreeting(view)
        setupQuickAccessButtons(view)
        setupMixRow(view)

        val shortcutsAdapter = setupShortcutsGrid(view.findViewById(R.id.home_shortcuts_list))
        val favoritesAdapter = setupCarousel(view.findViewById(R.id.home_favorites_list))
        val newestAdapter = setupCarousel(view.findViewById(R.id.home_newest_list))
        val randomAdapter = setupCarousel(view.findViewById(R.id.home_random_list))
        val frequentAdapter = setupCarousel(view.findViewById(R.id.home_frequent_list))

        val shortcutsShelf = view.findViewById<View>(R.id.home_shortcuts_shelf)
        val favoritesShelf = view.findViewById<View>(R.id.home_favorites_shelf)
        val newestShelf = view.findViewById<View>(R.id.home_newest_shelf)
        val randomShelf = view.findViewById<View>(R.id.home_random_shelf)
        val frequentShelf = view.findViewById<View>(R.id.home_frequent_shelf)

        homeViewModel.shortcutAlbums.observe(viewLifecycleOwner) {
            shortcutsAdapter.submitList(it)
            shortcutsShelf.isVisible = it.isNotEmpty()
        }
        homeViewModel.favoriteAlbums.observe(viewLifecycleOwner) {
            favoritesAdapter.submitList(it)
            favoritesShelf.isVisible = it.isNotEmpty()
        }
        homeViewModel.newestAlbums.observe(viewLifecycleOwner) {
            newestAdapter.submitList(it)
            newestShelf.isVisible = it.isNotEmpty()
        }
        homeViewModel.randomAlbums.observe(viewLifecycleOwner) {
            randomAdapter.submitList(it)
            randomShelf.isVisible = it.isNotEmpty()
        }
        homeViewModel.frequentAlbums.observe(viewLifecycleOwner) {
            frequentAdapter.submitList(it)
            frequentShelf.isVisible = it.isNotEmpty()
        }

        swipeRefresh = view.findViewById(R.id.swipe_refresh_view)
        swipeRefresh?.setOnRefreshListener { load() }

        load()
    }

    private fun setGreeting(view: View) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greetingRes = when {
            hour < MORNING_ENDS_AT_HOUR -> R.string.home_greeting_morning
            hour < AFTERNOON_ENDS_AT_HOUR -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        }
        view.findViewById<TextView>(R.id.home_greeting).setText(greetingRes)
    }

    private fun setupQuickAccessButtons(view: View) {
        view.findViewById<View>(R.id.home_quick_playlists).setOnClickListener {
            findNavController().navigate(R.id.playlistsFragment)
        }
        view.findViewById<View>(R.id.home_quick_albums).setOnClickListener {
            findNavController().navigate(
                NavigationGraphDirections.toAlbumList(
                    type = AlbumListType.NEWEST
                )
            )
        }
        view.findViewById<View>(R.id.home_quick_artists).setOnClickListener {
            findNavController().navigate(NavigationGraphDirections.toArtistList())
        }
        view.findViewById<View>(R.id.home_quick_songs).setOnClickListener {
            findNavController().navigate(
                NavigationGraphDirections.toTrackCollection(libraryRoot = true)
            )
        }
        view.findViewById<View>(R.id.home_quick_genres).setOnClickListener {
            findNavController().navigate(NavigationGraphDirections.toGenreList())
        }
    }

    /**
     * The Mix is shown as a single playlist-style row (cover of the first track, title,
     * song count) instead of a scrollable carousel - tapping it plays the whole mix.
     */
    private fun setupMixRow(view: View) {
        val mixShelf = view.findViewById<View>(R.id.home_mix_shelf)
        val mixCover = view.findViewById<ImageView>(R.id.home_mix_cover)
        val mixTitle = view.findViewById<TextView>(R.id.home_mix_title)
        val mixSubtitle = view.findViewById<TextView>(R.id.home_mix_subtitle)

        mixShelf.setOnClickListener { playMix() }

        homeViewModel.mixGenreName.observe(viewLifecycleOwner) {
            mixTitle.text = getString(R.string.home_mix_title, it ?: "")
        }
        homeViewModel.mixTracks.observe(viewLifecycleOwner) { tracks ->
            mixShelf.isVisible = tracks.isNotEmpty()
            mixSubtitle.text = getString(R.string.home_mix_song_count, tracks.size)

            val firstTrack = tracks.firstOrNull()
            if (firstTrack != null) {
                imageLoaderProvider.executeOn {
                    it.loadImage(mixCover, firstTrack, false, 0, R.drawable.unknown_album)
                }
            }
        }
    }

    private fun playMix() {
        val tracks = homeViewModel.mixTracks.value
        if (tracks.isNullOrEmpty()) return

        mediaPlayerManager.addToPlaylist(
            songs = tracks,
            autoPlay = false,
            shuffle = false,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR
        )
        mediaPlayerManager.play(0)
    }

    private fun load() {
        homeViewModel.viewModelScope.launch(toastingExceptionHandler()) {
            swipeRefresh?.isRefreshing = true
            homeViewModel.loadHomeScreen()
            swipeRefresh?.isRefreshing = false
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        val allEmpty = homeViewModel.shortcutAlbums.value.isNullOrEmpty() &&
            homeViewModel.mixTracks.value.isNullOrEmpty() &&
            homeViewModel.favoriteAlbums.value.isNullOrEmpty() &&
            homeViewModel.newestAlbums.value.isNullOrEmpty() &&
            homeViewModel.randomAlbums.value.isNullOrEmpty() &&
            homeViewModel.frequentAlbums.value.isNullOrEmpty()
        requireView().findViewById<View>(R.id.home_empty_view).isVisible = allEmpty
    }

    private fun setupCarousel(recyclerView: RecyclerView): BaseAdapter<Identifiable> {
        val adapter = BaseAdapter<Identifiable>()
        adapter.register(HomeAlbumDelegate(::onAlbumClick))

        recyclerView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = adapter

        return adapter
    }

    private fun setupShortcutsGrid(recyclerView: RecyclerView): BaseAdapter<Identifiable> {
        val adapter = BaseAdapter<Identifiable>()
        adapter.register(HomeShortcutDelegate(::onAlbumClick))

        recyclerView.layoutManager = GridLayoutManager(context, SHORTCUT_GRID_COLUMNS)
        recyclerView.adapter = adapter

        return adapter
    }

    private fun onAlbumClick(album: Album) {
        val action = NavigationGraphDirections.toTrackCollection(
            album.id,
            isAlbum = album.isDirectory,
            name = album.title,
            parentId = album.parent
        )
        findNavController().navigate(action)
    }
}
