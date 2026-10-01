/*
 * EditServerFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.flag.BubbleFlag
import com.skydoves.colorpickerview.flag.FlagMode
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.flow.MutableStateFlow
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.model.EditServerViewModel
import org.moire.ultrasonic.ui.serverselector.EditServerActions
import org.moire.ultrasonic.ui.serverselector.EditServerMode
import org.moire.ultrasonic.ui.serverselector.EditServerNavigationEvent
import org.moire.ultrasonic.ui.serverselector.EditServerScreen
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.ServerColor
import org.moire.ultrasonic.util.Util

private const val COLOR_PICKER_DIALOG_PADDING = 12

/**
 * Displays a form where server settings can be created / edited.
 *
 * Post-issue-#10 residual migration (phase 5A3): now a thin Compose host, the same shape as
 * [ServerSelectorFragment] - it threads the live floating-chrome inset into [EditServerScreen]
 * and owns the one piece of UI that stays a View dialog ([showColorPicker], the third-party
 * `ColorPickerDialog`) plus the `OnBackPressedCallback`, which now routes to
 * [EditServerViewModel.requestBack] exactly like the header's own back action - both trigger the
 * same dirty-check, fixing the legacy gap where only system Back did (see the phase 5A3 report).
 * Navigation resolves the destination's `serverId` argument (a stable
 * [org.moire.ultrasonic.data.ServerSetting.id], replacing the legacy position-derived `index`) in
 * [onViewCreated] and otherwise owns no business logic - that lives in [EditServerViewModel].
 */
class EditServerFragment : Fragment() {

    private val viewModel: EditServerViewModel by viewModels()
    private val navArgs by navArgs<EditServerFragmentArgs>()
    private val fallbackChromeInset = MutableStateFlow(0)

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            viewModel.requestBack()
        }
    }

    override fun onAttach(context: Context) {
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)
        super.onAttach(context)
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
                    EditServerScreen(
                        state = state,
                        actions = editServerActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mode = if (navArgs.serverId != -1) {
            EditServerMode.Existing(navArgs.serverId)
        } else {
            EditServerMode.New
        }
        viewModel.load(mode)
        viewModel.onNavigate = ::handleNavigationEvent
    }

    override fun onDestroyView() {
        // Clears the callback before the view (and findNavController()'s backing NavHostFragment
        // view) goes away - a late save-flow callback becomes a safe no-op instead of a crash.
        viewModel.onNavigate = {}
        super.onDestroyView()
    }

    override fun onStop() {
        Util.hideKeyboard(activity)
        backCallback.isEnabled = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        backCallback.isEnabled = true
    }

    private fun handleNavigationEvent(event: EditServerNavigationEvent) {
        when (event) {
            EditServerNavigationEvent.NavigateUp -> findNavController().navigateUp()
            EditServerNavigationEvent.NavigateHome ->
                findNavController().popBackStack(R.id.homeFragment, false)
        }
    }

    private val editServerActions: EditServerActions by lazy {
        EditServerActions(
            onBack = viewModel::requestBack,
            onNameChange = viewModel::onNameChange,
            onAddressChange = viewModel::onAddressChange,
            onAddressFocusLost = viewModel::onAddressFocusLost,
            onUsernameChange = viewModel::onUsernameChange,
            onPasswordChange = viewModel::onPasswordChange,
            onSelfSignedChange = viewModel::onSelfSignedChange,
            onPlaintextChange = viewModel::onPlaintextChange,
            onJukeboxChange = viewModel::onJukeboxChange,
            onToggleAdvanced = viewModel::onToggleAdvanced,
            onPickColor = ::showColorPicker,
            onTestConnection = viewModel::onTestConnection,
            onConnectOrSave = viewModel::onConnectOrSave,
            onDiscardConfirm = viewModel::confirmDiscard,
            onDiscardCancel = viewModel::cancelDiscard,
        )
    }

    /**
     * The one legacy View dialog this phase deliberately keeps (docs section 18, option A): a
     * bounded, already-themed third-party color picker, triggered here and reporting back into
     * [EditServerViewModel.onColorPicked] on confirm. Unchanged from the legacy
     * `serverColorImageView` click listener.
     */
    private fun showColorPicker() {
        val initialColor = viewModel.uiState.value.color
            ?: ServerColor.getBackgroundColor(requireContext(), null)
        val bubbleFlag = BubbleFlag(context)
        bubbleFlag.flagMode = FlagMode.LAST
        ColorPickerDialog.Builder(context).apply {
            colorPickerView.setInitialColor(initialColor)
            colorPickerView.flagView = bubbleFlag
        }
            .attachAlphaSlideBar(false)
            .setPositiveButton(
                getString(R.string.common_ok),
                ColorEnvelopeListener { envelope, _ -> viewModel.onColorPicked(envelope.color) }
            )
            .setNegativeButton(getString(R.string.common_cancel)) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .setBottomSpace(COLOR_PICKER_DIALOG_PADDING)
            .show()
    }
}
