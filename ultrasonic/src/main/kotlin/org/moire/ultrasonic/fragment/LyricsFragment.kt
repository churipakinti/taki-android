/*
 * LyricsFragment.kt
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.model.LyricsViewModel
import org.moire.ultrasonic.ui.lyrics.LyricsActions
import org.moire.ultrasonic.ui.lyrics.LyricsScreen
import org.moire.ultrasonic.ui.playback.PlaybackUiStateHolder
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * Displays the lyrics of the playing track (issue #10 phase 4K3, given the Taki atmosphere in
 * phase 4K4) - a thin Compose host over the unchanged `lyricsFragment` nav-graph boundary
 * (`artist`/`title`/`id` arguments, opened from Now Playing and the Up Next menu). It owns the
 * back command, the seek command and the link from playback to [LyricsViewModel]: the arguments
 * select the first track, and when playback moves on the screen follows, so the previous track's
 * lyrics never stay up. Playback itself is only read ([PlaybackUiStateHolder]) - for a tapped
 * synced line it is seeked through the same holder, and its `artworkModelLarge` is read (through
 * Compose state, so [LyricsScreen]'s backdrop only recomposes when the artwork actually changes)
 * for the backdrop, exactly like Now Playing's own atmosphere.
 */
class LyricsFragment : Fragment() {

    private val viewModel: LyricsViewModel by viewModels()
    private val playbackUiStateHolder: PlaybackUiStateHolder by inject()
    private val navArgs by navArgs<LyricsFragmentArgs>()

    private val fallbackChromeInset = MutableStateFlow(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val chromeInsetFlow =
            (activity as? NavigationActivity)?.contentBottomInset ?: fallbackChromeInset
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TakiTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val playerState by playbackUiStateHolder.playerState.collectAsStateWithLifecycle()
                    val chromeInsetPx by chromeInsetFlow.collectAsStateWithLifecycle()
                    val bottomInset = if (chromeInsetPx > 0) {
                        with(LocalDensity.current) { chromeInsetPx.toDp() }
                    } else {
                        TakiTheme.dimensions.contentInsetFloatingChrome
                    }
                    LyricsScreen(
                        state = state,
                        positionMs = { playbackUiStateHolder.snapshotProgress().positionMs },
                        actions = lyricsActions,
                        artworkModel = playerState.artworkModelLarge,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.show(navArgs.id, navArgs.artist, navArgs.title)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastTrackId: String? = null
                playbackUiStateHolder.playerState.collect { playing ->
                    val id = playing.trackId
                    val title = playing.title
                    // Only a real track change reloads; play/pause/like emissions must not
                    // re-trigger a failed load.
                    if (id != null && title != null && id != lastTrackId) {
                        lastTrackId = id
                        viewModel.show(id, playing.artist, title)
                    }
                }
            }
        }
    }

    private val lyricsActions: LyricsActions by lazy {
        LyricsActions(
            onBack = { findNavController().navigateUp() },
            onRetry = viewModel::retry,
            onSeek = { positionMs -> playbackUiStateHolder.onSeekTo(positionMs.toInt()) },
        )
    }
}
