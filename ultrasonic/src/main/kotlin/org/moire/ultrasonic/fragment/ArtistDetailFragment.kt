/*
 * ArtistDetailFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.ArtistPopularTrackDelegate
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.adapters.HomeAlbumDelegate
import org.moire.ultrasonic.adapters.SimilarArtistDelegate
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.ArtistDetailModel
import org.moire.ultrasonic.service.ArtistRadioQueueBuilder
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.DownloadAction
import org.moire.ultrasonic.util.DownloadUtil
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Util.navigateToCurrent
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.toastingExceptionHandler

/** A dedicated, playback-first artist page backed entirely by server data. */
class ArtistDetailFragment :
    Fragment(),
    RefreshableFragment {

    private val navArgs: ArtistDetailFragmentArgs by navArgs()
    private val model: ArtistDetailModel by viewModels()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    private val tracksAdapter = BaseAdapter<Identifiable>()
    private val albumsAdapter = BaseAdapter<Identifiable>()
    private val similarArtistsAdapter = BaseAdapter<Identifiable>()

    private var biographyExpanded = false

    override var swipeRefresh: SwipeRefreshLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.artist_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FragmentTitle.setTitle(this, "")

        view.findViewById<TextView>(R.id.artist_detail_name).text = navArgs.artistName
        setupArtistArt(view)
        setupLists(view)
        setupActions(view)
        setupToolbarBehavior(view)
        observeData(view)

        swipeRefresh = view.findViewById(R.id.swipe_refresh_view)
        swipeRefresh?.setOnRefreshListener { load(refresh = true) }
        load(refresh = false)
    }

    private fun setupArtistArt(view: View) {
        val art = view.findViewById<ImageView>(R.id.artist_detail_art)
        val artist = Artist(
            id = navArgs.artistId,
            name = navArgs.artistName,
            coverArt = navArgs.artistCoverArt
        )

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val key = FileUtil.getArtistArtKey(artist.name, true)
            withContext(Dispatchers.Main) {
                imageLoaderProvider.executeOn {
                    it.loadImage(
                        view = art,
                        id = artist.coverArt,
                        key = key,
                        large = true,
                        size = 0,
                        defaultResourceId = R.drawable.artist_placeholder
                    )
                }
            }
        }
    }

    private fun setupLists(view: View) {
        tracksAdapter.register(ArtistPopularTrackDelegate(::playTrack))
        view.findViewById<RecyclerView>(R.id.artist_detail_tracks).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = tracksAdapter
        }

        albumsAdapter.register(HomeAlbumDelegate(::openAlbum))
        view.findViewById<RecyclerView>(R.id.artist_detail_albums).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = albumsAdapter
        }

        similarArtistsAdapter.register(SimilarArtistDelegate(::openArtist))
        view.findViewById<RecyclerView>(R.id.artist_detail_similar).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = similarArtistsAdapter
        }
    }

    private fun setupActions(view: View) {
        view.findViewById<View>(R.id.artist_detail_play).setOnClickListener { playArtist() }
        view.findViewById<View>(R.id.artist_detail_radio).setOnClickListener { startArtistRadio() }
        view.findViewById<View>(R.id.artist_detail_download).setOnClickListener {
            DownloadUtil.justDownload(
                action = DownloadAction.DOWNLOAD,
                fragment = this,
                id = navArgs.artistId,
                name = navArgs.artistName,
                isArtist = true
            )
        }
        view.findViewById<View>(R.id.artist_detail_biography_toggle).setOnClickListener {
            biographyExpanded = !biographyExpanded
            updateBiographyExpansion(view)
        }
    }

    private fun setupToolbarBehavior(view: View) {
        val scroll = view.findViewById<NestedScrollView>(R.id.artist_detail_scroll)
        val hero = view.findViewById<View>(R.id.artist_detail_hero)
        val toolbarRevealOffset = (TOOLBAR_REVEAL_OFFSET_DP * resources.displayMetrics.density)
            .toInt()
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val collapsed = hero.height > 0 && scrollY >= hero.height - toolbarRevealOffset
            FragmentTitle.setTitle(this, if (collapsed) navArgs.artistName else "")
        }

        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

                override fun onPrepareMenu(menu: Menu) {
                    menu.findItem(R.id.action_search)?.isVisible = false
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false
            },
            viewLifecycleOwner
        )
    }

    private fun observeData(view: View) {
        val albumCount = view.findViewById<TextView>(R.id.artist_detail_album_count)
        model.tracks.observe(viewLifecycleOwner) { tracks ->
            tracksAdapter.submitList(tracks.take(POPULAR_TRACK_COUNT))
            view.findViewById<View>(R.id.artist_detail_popular_section).isVisible =
                tracks.isNotEmpty()
            updateEmptyState(view)
        }
        model.albums.observe(viewLifecycleOwner) { albums ->
            albumsAdapter.submitList(albums)
            view.findViewById<View>(R.id.artist_detail_albums_section).isVisible =
                albums.isNotEmpty()
            albumCount.isVisible = albums.isNotEmpty()
            albumCount.text = resources.getQuantityString(
                R.plurals.artist_album_count,
                albums.size,
                albums.size
            )
            updateEmptyState(view)
        }
        model.loaded.observe(viewLifecycleOwner) {
            updateEmptyState(view)
        }
        model.artistInfo.observe(viewLifecycleOwner) { artistInfo ->
            val biography = HtmlCompat.fromHtml(
                artistInfo?.biography.orEmpty(),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            ).toString().trim()
            val similarArtists = artistInfo?.similarArtists.orEmpty()

            biographyExpanded = false
            view.findViewById<View>(R.id.artist_detail_about_section).isVisible =
                biography.isNotEmpty()
            view.findViewById<TextView>(R.id.artist_detail_biography).text = biography
            view.findViewById<View>(R.id.artist_detail_biography_toggle).isVisible =
                biography.length > BIOGRAPHY_COLLAPSE_LENGTH
            updateBiographyExpansion(view)

            similarArtistsAdapter.submitList(similarArtists)
            view.findViewById<View>(R.id.artist_detail_similar_section).isVisible =
                similarArtists.isNotEmpty()
        }
    }

    private var loadJob: Job? = null

    private fun load(refresh: Boolean) {
        loadJob?.cancel()
        loadJob = model.viewModelScope.launch(toastingExceptionHandler()) {
            swipeRefresh?.isRefreshing = true
            try {
                model.load(navArgs.artistId, navArgs.artistName, refresh)
            } finally {
                swipeRefresh?.isRefreshing = false
            }
        }
    }

    private fun playArtist() {
        mediaPlayerManager.playTracksAndToast(
            fragment = this,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
            id = navArgs.artistId,
            name = navArgs.artistName,
            isArtist = true
        )
    }

    private fun startArtistRadio() {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler(getString(R.string.artist_radio_error))
        ) {
            val queue = ArtistRadioQueueBuilder(MusicServiceFactory.getMusicService())
                .build(
                    artistId = navArgs.artistId,
                    artistName = navArgs.artistName
                )
            if (queue.isEmpty()) {
                toast(R.string.artist_radio_empty)
                return@launch
            }
            mediaPlayerManager.suggestedPlaylistName = getString(
                R.string.artist_radio_playlist_name,
                navArgs.artistName
            )
            mediaPlayerManager.addToPlaylist(
                songs = queue,
                autoPlay = true,
                shuffle = false,
                insertionMode = MediaPlayerManager.InsertionMode.CLEAR
            )
            navigateToCurrent()
        }
    }

    private fun playTrack(track: Track) {
        val tracks = model.tracks.value.orEmpty()
        val startIndex = tracks.indexOfFirst { it.id == track.id }
        if (startIndex < 0) return

        mediaPlayerManager.addToPlaylist(
            songs = tracks,
            autoPlay = false,
            shuffle = false,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
            startIndex = startIndex
        )
    }

    private fun openAlbum(album: Album) {
        findNavController().navigate(
            NavigationGraphDirections.toTrackCollection(
                id = album.id,
                isAlbum = true,
                name = album.title,
                parentId = album.parent
            )
        )
    }

    private fun openArtist(artist: Artist) {
        findNavController().navigate(
            NavigationGraphDirections.toArtistDetail(
                artistId = artist.id,
                artistName = artist.name.orEmpty(),
                artistCoverArt = artist.coverArt
            )
        )
    }

    private fun updateBiographyExpansion(view: View) {
        val biography = view.findViewById<TextView>(R.id.artist_detail_biography)
        val toggle = view.findViewById<TextView>(R.id.artist_detail_biography_toggle)
        biography.maxLines = if (biographyExpanded) Int.MAX_VALUE else BIOGRAPHY_COLLAPSED_LINES
        biography.ellipsize = if (biographyExpanded) null else android.text.TextUtils.TruncateAt.END
        toggle.setText(
            if (biographyExpanded) R.string.artist_show_less else R.string.artist_show_more
        )
    }

    private fun updateEmptyState(view: View) {
        val loaded = model.loaded.value == true
        val isEmpty = model.tracks.value.isNullOrEmpty() && model.albums.value.isNullOrEmpty()
        view.findViewById<View>(R.id.artist_detail_empty).isVisible = loaded && isEmpty
    }

    companion object {
        private const val POPULAR_TRACK_COUNT = 5
        private const val TOOLBAR_REVEAL_OFFSET_DP = 56
        private const val BIOGRAPHY_COLLAPSE_LENGTH = 320
        private const val BIOGRAPHY_COLLAPSED_LINES = 5
    }
}
