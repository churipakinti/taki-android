/*
 * MainFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.model.LibraryViewModel
import org.moire.ultrasonic.ui.library.LibraryActions
import org.moire.ultrasonic.ui.library.LibraryScreen
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities

/**
 * Thin Compose host for the Library screen (issue #10 phase 2). Owns nothing of the UI: it
 * collects [LibraryViewModel.uiState], renders [LibraryScreen] under [TakiTheme], and
 * forwards navigation via the `NavController` behind [LibraryActions] callbacks - the
 * boundary fixed in issue #8. The `mainFragment` destination id, graph actions/args, back
 * stack, Activity chrome and the floating shell are unchanged; Library still consumes the
 * shared `NavigationActivity.contentBottomInset` (no shell logic here).
 */
class MainFragment : Fragment() {

    private val libraryViewModel: LibraryViewModel by viewModels()

    // Fallback for the brief window before the Activity has computed its first inset (and the
    // impossible case of a non-NavigationActivity host). Mirrors HomeFragment.
    private val fallbackChromeInset = MutableStateFlow(0)

    // A zero-width strip pinned top-end so the library-hub PopupMenu anchors near the Compose
    // header's overflow glyph (a full-size ComposeView anchor drops the menu in the wrong
    // place). Same trick as HomeFragment.
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
                    val state by libraryViewModel.uiState.collectAsStateWithLifecycle()
                    val chromeInsetPx by chromeInsetFlow.collectAsStateWithLifecycle()
                    val bottomInset = if (chromeInsetPx > 0) {
                        with(LocalDensity.current) { chromeInsetPx.toDp() }
                    } else {
                        TakiTheme.dimensions.contentInsetFloatingChrome
                    }
                    LibraryScreen(
                        state = state,
                        actions = libraryActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
        val anchor = View(requireContext())
        overflowAnchor = anchor
        return FrameLayout(requireContext()).apply {
            addView(composeView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
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
        // Recompute Box Sets visibility on every open, like the old setupBoxSetsRow (cached
        // Room read only - no network).
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            libraryViewModel.refresh()
        }
    }

    private val libraryActions: LibraryActions by lazy {
        LibraryActions(
            onOverflow = {
                (activity as? NavigationActivity)?.showLibraryHub(overflowAnchor ?: requireView())
            },
            onLikedSongs = {
                findNavController().navigate(
                    NavigationGraphDirections.toTrackCollection(
                        getStarred = true,
                        name = getString(R.string.library_liked_songs),
                    ),
                )
            },
            onLikedAlbums = {
                findNavController().navigate(
                    NavigationGraphDirections.toAlbumList(
                        type = AlbumListType.STARRED,
                        title = getString(R.string.library_liked_albums),
                    ),
                )
            },
            onPlaylists = { findNavController().navigate(R.id.playlistsFragment) },
            onDownloads = { findNavController().navigate(R.id.downloadsFragment) },
            onAlbums = {
                findNavController().navigate(
                    NavigationGraphDirections.toAlbumList(AlbumListType.SORTED_BY_NAME),
                )
            },
            onArtists = { findNavController().navigate(NavigationGraphDirections.toArtistList()) },
            onSongs = {
                findNavController().navigate(
                    NavigationGraphDirections.toTrackCollection(libraryRoot = true),
                )
            },
            onGenres = { findNavController().navigate(NavigationGraphDirections.toGenreList()) },
            onBoxSets = { findNavController().navigate(R.id.collectionListFragment) },
        )
    }

    private companion object {
        private const val OVERFLOW_ANCHOR_HEIGHT_DP = 48f
    }
}

interface FilterableFragment {
    fun setLayoutType(newType: org.moire.ultrasonic.util.LayoutType) {}
    fun setOrderType(newOrder: SortOrder)
    fun getOrderType(): SortOrder? = null
    fun onPrimaryAction() {}
    var viewCapabilities: ViewCapabilities
}
