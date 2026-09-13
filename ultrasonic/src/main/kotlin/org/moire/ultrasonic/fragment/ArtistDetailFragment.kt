/*
 * ArtistDetailFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.ArtistDetailViewModel
import org.moire.ultrasonic.service.ArtistRadioQueueBuilder
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.artist.ArtistAlbumUi
import org.moire.ultrasonic.ui.artist.ArtistDetailActions
import org.moire.ultrasonic.ui.artist.ArtistDetailArgs
import org.moire.ultrasonic.ui.artist.ArtistDetailScreen
import org.moire.ultrasonic.ui.artist.ArtistSimilarUi
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.DownloadAction
import org.moire.ultrasonic.util.DownloadUtil
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * A dedicated artist page (issue #10 phase 4C). A thin Compose host: it owns the nav-graph
 * boundary (the unchanged `artistDetailFragment` destination and its `artistId` / `artistName`
 * / `artistCoverArt` arguments) and the playback / radio / download commands; everything
 * visible is [ArtistDetailScreen]. The Activity's Material toolbar is hidden for this
 * destination (`NavigationActivity.hidesSupportActionBar`), so the artist name is shown once,
 * in the Compose hero.
 *
 * Playback, artist radio and download route through the same unchanged
 * `MediaPlayerManager` / `ArtistRadioQueueBuilder` / `DownloadUtil` paths the View screen used.
 */
class ArtistDetailFragment : Fragment() {

    private val navArgs: ArtistDetailFragmentArgs by navArgs()
    private val viewModel: ArtistDetailViewModel by viewModels()
    private val mediaPlayerManager: MediaPlayerManager by inject()

    private val fallbackChromeInset = MutableStateFlow(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val chromeInsetFlow =
            (activity as? NavigationActivity)?.contentBottomInset ?: fallbackChromeInset
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
                    ArtistDetailScreen(
                        state = state,
                        actions = artistActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The Compose hero carries the artist name; the hidden toolbar shows nothing.
        setTitle(this, "")

        viewModel.load(
            ArtistDetailArgs(
                artistId = navArgs.artistId,
                artistName = navArgs.artistName,
                knownCoverArt = navArgs.artistCoverArt,
            )
        )
    }

    private val artistActions: ArtistDetailActions by lazy {
        ArtistDetailActions(
            onBack = { findNavController().navigateUp() },
            onPlay = ::playArtist,
            onRadio = ::startArtistRadio,
            onDownload = {
                DownloadUtil.justDownload(
                    action = DownloadAction.DOWNLOAD,
                    fragment = this,
                    id = navArgs.artistId,
                    name = navArgs.artistName,
                    isArtist = true,
                )
            },
            onAlbumClick = ::openAlbum,
            onTrackClick = ::playTrack,
            onSimilarArtistClick = ::openSimilarArtist,
            onRefresh = viewModel::refresh,
        )
    }

    private fun playArtist() {
        mediaPlayerManager.playTracksAndToast(
            fragment = this,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
            id = navArgs.artistId,
            name = navArgs.artistName,
            isArtist = true,
        )
    }

    private fun startArtistRadio() {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler(getString(R.string.artist_radio_error))
        ) {
            val queue = ArtistRadioQueueBuilder(MusicServiceFactory.getMusicService())
                .build(artistId = navArgs.artistId, artistName = navArgs.artistName)
            if (queue.isEmpty()) {
                toast(R.string.artist_radio_empty)
                return@launch
            }
            if (queue.size < ArtistRadioQueueBuilder.TARGET_SIZE) {
                toast(getString(R.string.artist_radio_short, queue.size))
            }
            mediaPlayerManager.suggestedPlaylistName = getString(
                R.string.artist_radio_playlist_name,
                navArgs.artistName
            )
            mediaPlayerManager.addToPlaylist(
                songs = queue,
                autoPlay = true,
                shuffle = false,
                insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
            )
        }
    }

    private fun playTrack(trackId: String) {
        val tracks = viewModel.tracksSnapshot()
        val startIndex = tracks.indexOfFirst { it.id == trackId }
        if (startIndex < 0) return
        mediaPlayerManager.addToPlaylist(
            songs = tracks,
            autoPlay = false,
            shuffle = false,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
            startIndex = startIndex,
        )
    }

    private fun openAlbum(album: ArtistAlbumUi) {
        findNavController().navigate(
            NavigationGraphDirections.toTrackCollection(
                id = album.id,
                isAlbum = true,
                name = album.title,
                parentId = album.parent,
            )
        )
    }

    private fun openSimilarArtist(similar: ArtistSimilarUi) {
        findNavController().navigate(
            NavigationGraphDirections.toArtistDetail(
                artistId = similar.id,
                artistName = similar.name,
                artistCoverArt = similar.coverArt,
            )
        )
    }
}
