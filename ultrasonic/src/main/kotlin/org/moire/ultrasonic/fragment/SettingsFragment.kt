/*
 * SettingsFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.flow.MutableStateFlow
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.model.SettingsViewModel
import org.moire.ultrasonic.ui.settings.SettingsActions
import org.moire.ultrasonic.ui.settings.SettingsEffect
import org.moire.ultrasonic.ui.settings.SettingsScreen
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.SelectCacheActivityContract

/**
 * Shows main app settings.
 *
 * Post-issue-#10 residual migration (phase 5A4): now a thin Compose host, the same shape as
 * [ServerSelectorFragment]/[EditServerFragment] - it threads the live floating-chrome inset into
 * [SettingsScreen] and owns the one piece of platform plumbing that must stay Fragment-side: the
 * [SelectCacheActivityContract] launcher (an `ActivityResultLauncher` can only be registered from
 * a `Fragment`/`Activity`, never from a `ViewModel`). Every other behavior - the item tree, every
 * toggle/choice/action's read-write-side-effect logic, the transient sheets - lives in
 * [SettingsViewModel]/`org.moire.ultrasonic.ui.settings`.
 *
 * One instance per screen: the legacy self-navigating `settingsFragment` → `settingsFragment`
 * destination (`onPreferenceTreeClick`'s `settingsToGroup` action) is unchanged - each nested
 * group still gets its own back-stack entry and its own `by viewModels()` instance, resolving its
 * item list from [org.moire.ultrasonic.ui.settings.SettingsDefinitions] via its own `rootKey` nav
 * argument instead of `PreferenceFragmentCompat.setPreferencesFromResource(R.xml.settings,
 * rootKey)`.
 */
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()
    private val navArgs by navArgs<SettingsFragmentArgs>()
    private val fallbackChromeInset = MutableStateFlow(0)

    private val selectCacheActivityContract =
        registerForActivityResult(SelectCacheActivityContract()) { uri ->
            if (uri != null) {
                UApp.applicationContext().contentResolver.takePersistableUriPermission(uri, RW_FLAG)
                viewModel.onCacheLocationPicked(uri.toString())
            } else {
                viewModel.onCacheLocationPicked(null)
            }
        }

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
                    SettingsScreen(
                        state = state,
                        actions = settingsActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.onEffect = ::handleEffect
        viewModel.load(navArgs.rootKey)
    }

    override fun onDestroyView() {
        viewModel.onEffect = {}
        super.onDestroyView()
    }

    private fun handleEffect(effect: SettingsEffect) {
        when (effect) {
            is SettingsEffect.LaunchCacheLocationPicker ->
                selectCacheActivityContract.launch(effect.currentUri)
        }
    }

    private val settingsActions: SettingsActions by lazy {
        SettingsActions(
            onBack = { findNavController().navigateUp() },
            onToggle = viewModel::onToggle,
            onChoiceClick = viewModel::onChoiceClick,
            onChoiceSelected = viewModel::onChoiceSelected,
            onChoiceDismiss = viewModel::onOverlayDismiss,
            onActionClick = viewModel::onActionClick,
            onConfirm = viewModel::onConfirm,
            onConfirmDismiss = viewModel::onOverlayDismiss,
            onInfoDismiss = viewModel::onOverlayDismiss,
            onNavigateGroup = { rootKey ->
                findNavController().navigate(SettingsFragmentDirections.settingsToGroup(rootKey))
            },
            onNavigateEqualizer = { findNavController().navigate(R.id.toEqualizer) },
            onNavigateAbout = { findNavController().navigate(R.id.aboutFragment) },
        )
    }

    companion object {
        const val RW_FLAG = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val PERSISTABLE_FLAG = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    }
}
