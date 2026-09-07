/*
 * HomeFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.model.HomeViewModel
import org.moire.ultrasonic.service.DailyMixQueueBuilder
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.ui.home.HomeActions
import org.moire.ultrasonic.ui.home.HomeAlbumUi
import org.moire.ultrasonic.ui.home.HomeScreen
import org.moire.ultrasonic.ui.home.HomeUiState
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * Thin Compose host for the Home screen. Owns nothing of the UI: it collects
 * [HomeViewModel.uiState], renders [HomeScreen] under [TakiTheme], and forwards navigation
 * (via `NavController`) and playback (via [MediaPlayerManager]) commands - the boundary
 * fixed in issue #8. The `homeFragment` navigation destination, id, arguments, back stack,
 * Activity chrome, mini-player and bottom navigation are unchanged.
 */
class HomeFragment : Fragment() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val mediaPlayerManager: MediaPlayerManager by inject()

    // Fallback for the brief window before the Activity has computed its first inset (and for
    // the impossible case of a non-NavigationActivity host).
    private val fallbackChromeInset = MutableStateFlow(0)

    // A zero-width strip pinned top-end, purely so the library-hub PopupMenu has a small
    // anchor near the Compose header's overflow glyph (a full-size ComposeView anchor drops
    // the menu in the wrong place).
    private var overflowAnchor: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val chromeInsetFlow = (activity as? NavigationActivity)?.contentBottomInset
            ?: fallbackChromeInset
        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TakiTheme {
                    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
                    val chromeInsetPx by chromeInsetFlow.collectAsStateWithLifecycle()
                    val bottomInset = if (chromeInsetPx > 0) {
                        with(LocalDensity.current) { chromeInsetPx.toDp() }
                    } else {
                        TakiTheme.dimensions.contentInsetFloatingChrome
                    }
                    HomeScreen(
                        state = state,
                        actions = homeActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
        val anchor = View(requireContext())
        overflowAnchor = anchor
        return FrameLayout(requireContext()).apply {
            addView(
                composeView,
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
            )
            val anchorHeightPx = (OVERFLOW_ANCHOR_HEIGHT_DP * resources.displayMetrics.density)
                .toInt()
            addView(anchor, FrameLayout.LayoutParams(1, anchorHeightPx))
            anchor.updateLayoutParams<FrameLayout.LayoutParams> {
                gravity = Gravity.TOP or Gravity.END
            }
        }
    }

    override fun onDestroyView() {
        overflowAnchor = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            homeViewModel.loadHomeScreen()
        }

        // One-time "what is the Daily Mix" toast, kept from the View implementation.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.uiState.collect(::maybeShowMixIntro)
            }
        }
    }

    private val homeActions: HomeActions by lazy {
        HomeActions(
            onRefresh = {
                viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
                    homeViewModel.loadHomeScreen(forceRefresh = true)
                }
            },
            onOverflow = {
                (activity as? NavigationActivity)?.showLibraryHub(overflowAnchor ?: requireView())
            },
            onAlbumClick = ::openAlbum,
            onOpenPlaylists = { findNavController().navigate(R.id.playlistsFragment) },
            onOpenAlbums = {
                findNavController().navigate(
                    NavigationGraphDirections.toAlbumList(type = AlbumListType.NEWEST),
                )
            },
            onOpenArtists = {
                findNavController().navigate(NavigationGraphDirections.toArtistList())
            },
            onOpenSongs = {
                findNavController().navigate(
                    NavigationGraphDirections.toTrackCollection(libraryRoot = true),
                )
            },
            onPlayMix = ::playMix,
            onOpenMix = ::openMixDetail,
            onRegenerateMix = ::regenerateMix,
        )
    }

    private fun openAlbum(album: HomeAlbumUi) {
        findNavController().navigate(
            NavigationGraphDirections.toTrackCollection(
                album.id,
                isAlbum = album.isDirectory,
                name = album.title,
                parentId = album.parentId,
            ),
        )
    }

    private fun playMix() {
        val tracks = homeViewModel.mixTracks
        if (tracks.isEmpty()) return
        mediaPlayerManager.addToPlaylist(
            songs = tracks,
            autoPlay = true,
            shuffle = false,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
            startIndex = 0,
        )
    }

    private fun openMixDetail() {
        if (homeViewModel.uiState.value.featuredMix == null) return
        findNavController().navigate(
            NavigationGraphDirections.toTrackCollection(
                dailyMix = true,
                name = getString(R.string.home_mix_title),
            ),
        )
    }

    private fun regenerateMix() {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            homeViewModel.regenerateDailyMix()
            val size = homeViewModel.uiState.value.featuredMix?.trackCount ?: 0
            if (size in 1 until DailyMixQueueBuilder.TARGET_SIZE) {
                toast(getString(R.string.home_mix_short, size))
            }
        }
    }

    private fun maybeShowMixIntro(state: HomeUiState) {
        if (state.featuredMix != null && !Settings.homeMixIntroShown) {
            Settings.homeMixIntroShown = true
            toast(getString(R.string.home_mix_intro), shortDuration = false)
        }
    }

    private companion object {
        private const val OVERFLOW_ANCHOR_HEIGHT_DP = 48f
    }
}
