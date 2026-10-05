/*
 * EqualizerFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.moire.ultrasonic.model.EqualizerViewModel
import org.moire.ultrasonic.ui.equalizer.EqualizerActions
import org.moire.ultrasonic.ui.equalizer.EqualizerScreen
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * Displays the Equalizer.
 *
 * Post-issue-#10 residual migration (phase 5A5): now a thin Compose host, the same shape as
 * [SettingsFragment]. The audio runtime stays authoritative and outside Compose - `PlaybackService`
 * still creates the `EqualizerController` with the player's audio session, and this Fragment never
 * touches an `Equalizer`: [EqualizerViewModel] projects the controller into immutable state and
 * dispatches commands back to it. The Fragment owns only what is genuinely lifecycle/navigation:
 * following the controller ([EqualizerViewModel.attach]), re-projecting on resume, saving the
 * settings in [onPause] exactly like the legacy screen, and closing the preset sheet on Back.
 *
 * Bottom inset: `equalizerFragment` is a hidden-bottom-nav destination, so
 * `NavigationActivity.applyBottomInset` already pads the whole nav host above the floating
 * mini-player. Threading `contentBottomInset` on top of that double-counts the chrome (verified on a
 * Pixel 7: a ~144dp dead band under the preset sheet and a preset pushed off its list), so the
 * screen is given only a little breathing room at the end of its content instead.
 */
class EqualizerFragment : Fragment() {

    private val viewModel: EqualizerViewModel by viewModels()

    private val presetSheetBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = viewModel.onPresetSheetDismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            TakiTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                EqualizerScreen(
                    state = state,
                    actions = equalizerActions,
                    bottomContentInset = TakiTheme.spacing.lg,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, presetSheetBackCallback)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState
                .map { it.presetSheetVisible }
                .distinctUntilChanged()
                .collect { presetSheetBackCallback.isEnabled = it }
        }
        // Subscribe to changes in the active controller
        viewModel.attach()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveSettings()
    }

    private val equalizerActions: EqualizerActions by lazy {
        EqualizerActions(
            onBack = { findNavController().navigateUp() },
            onEnabledChange = viewModel::onEnabledChange,
            onBandLevelChange = viewModel::onBandLevelChange,
            onBandLevelChangeFinished = viewModel::onBandLevelChangeFinished,
            onPresetClick = viewModel::onPresetClick,
            onPresetSelected = viewModel::onPresetSelected,
            onPresetSheetDismiss = viewModel::onPresetSheetDismiss,
        )
    }
}
