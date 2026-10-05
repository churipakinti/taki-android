/*
 * EqualizerViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.moire.ultrasonic.audiofx.AudioFxEqualizerRuntime
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.audiofx.EqualizerRuntime
import org.moire.ultrasonic.ui.equalizer.EqualizerBandUiState
import org.moire.ultrasonic.ui.equalizer.EqualizerPresetUiState
import org.moire.ultrasonic.ui.equalizer.EqualizerUiState
import timber.log.Timber

/** The legacy screen's `range / 1000`: the runtime reports band edges in milliHertz. */
private const val MILLIHERTZ_PER_HERTZ = 1000

/**
 * Projects the active [EqualizerController] into immutable [EqualizerUiState] and dispatches the
 * user's commands back to it (issue #10 phase 5A5). Compose is presentation only: this class
 * never creates, owns or releases an `Equalizer`, never holds a raw one (the [EqualizerRuntime]
 * view reads the controller's current instance on every call), and never starts playback to
 * obtain one - a missing controller simply projects as "unavailable".
 *
 * Every runtime call is guarded ([guarded]): the legacy fragment left the `enabled` read/write
 * and the preset listing unguarded, so an `AudioEffect` failure there could crash the app; here a
 * failing read keeps the previous projected value and a failing write is logged and the screen
 * re-projects whatever the runtime actually reports. No coroutines are involved - the legacy
 * operations were synchronous - so there is no `CancellationException` to preserve.
 *
 * A new controller (the player backend was rebuilt) replaces the runtime view and rebuilds the
 * whole state from it, so no band mapping from the previous controller can go stale; the old
 * runtime is dropped and never written to again.
 */
class EqualizerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    /** The legacy `EqualizerController.get()` LiveData. Test seam. */
    internal var controllerSource: LiveData<EqualizerController?> = EqualizerController.get()

    /** Wraps a controller in the narrow runtime view. Test seam. */
    internal var runtimeFactory: (EqualizerController) -> EqualizerRuntime =
        { AudioFxEqualizerRuntime(it) }

    private var runtime: EqualizerRuntime? = null
    private var observing = false
    private val controllerObserver = Observer<EqualizerController?> { onControllerChanged(it) }

    /** Starts following the active controller (idempotent) - the legacy `onViewCreated` observe. */
    fun attach() {
        if (observing) return
        observing = true
        controllerSource.observeForever(controllerObserver)
    }

    override fun onCleared() {
        if (observing) controllerSource.removeObserver(controllerObserver)
    }

    private fun onControllerChanged(controller: EqualizerController?) {
        if (controller == null) {
            Timber.d("EqualizerController Observer.onChanged has no controller")
            runtime = null
            _uiState.value = EqualizerUiState()
            return
        }
        Timber.d("EqualizerController Observer.onChanged received controller")
        val newRuntime = runtimeFactory(controller)
        runtime = newRuntime
        _uiState.value = project(newRuntime, EqualizerUiState(controllerAvailable = true))
    }

    /** Re-projects the real runtime state (resume, configuration change, toggle, preset). */
    fun refresh() {
        val current = runtime ?: return
        _uiState.update { project(current, it) }
    }

    fun onEnabledChange(enabled: Boolean) {
        val current = runtime ?: return
        guarded("setEnabled", Unit) { current.enabled = enabled }
        // The legacy updateBars(): re-read enabled and every band level after the write.
        _uiState.update { project(current, it) }
    }

    fun onBandLevelChange(band: Int, level: Int) {
        val current = runtime ?: return
        val state = _uiState.value
        val target = state.bands.firstOrNull { it.index == band }
        // No writes while the equalizer is off (the legacy bars were disabled), and none when the
        // value did not actually change - never from merely rendering.
        if (!state.enabled || target == null) return
        val clamped = level.coerceIn(target.minLevel, target.maxLevel)
        if (clamped == target.level) return

        if (guarded("setBandLevel", false) { current.setBandLevel(band, clamped); true }) {
            _uiState.update { s -> s.withBandLevel(band, clamped) }
        } else {
            _uiState.update { project(current, it) }
        }
    }

    /**
     * The drag ended: read back what the runtime actually stored for this band (it may quantize)
     * and which preset it now reports. Reads only - no write happens here.
     */
    fun onBandLevelChangeFinished(band: Int) {
        val current = runtime ?: return
        val stored = guarded<Int?>("getBandLevel", null) { current.getBandLevel(band) }
        _uiState.update { s ->
            val withLevel = if (stored != null) s.withBandLevel(band, stored) else s
            withLevel.copy(currentPresetIndex = readCurrentPreset(current, withLevel))
        }
    }

    fun onPresetClick() {
        val state = _uiState.value
        if (runtime != null && state.presets.isNotEmpty()) {
            _uiState.update { it.copy(presetSheetVisible = true) }
        }
    }

    fun onPresetSheetDismiss() {
        _uiState.update { it.copy(presetSheetVisible = false) }
    }

    fun onPresetSelected(preset: Int) {
        val current = runtime
        val known = _uiState.value.presets.any { it.index == preset }
        if (current != null && known) {
            guarded("usePreset", Unit) { current.usePreset(preset) }
        }
        _uiState.update { s ->
            val projected = if (current != null) project(current, s) else s
            projected.copy(presetSheetVisible = false)
        }
    }

    /** Called from the host's `onPause()`, preserving the legacy persistence timing. */
    fun saveSettings() {
        val current = runtime ?: return
        guarded("saveSettings", Unit) { current.saveSettings() }
    }

    /** Builds the state from the runtime; any section that fails to read keeps [previous]'s. */
    private fun project(rt: EqualizerRuntime, previous: EqualizerUiState): EqualizerUiState {
        val enabled = guarded("enabled", previous.enabled) { rt.enabled }
        val bands = guarded("bands", previous.bands) { readBands(rt) }
        val presets = guarded("presets", previous.presets) { readPresets(rt) }
        val withPresets = previous.copy(
            controllerAvailable = true,
            enabled = enabled,
            bands = bands,
            presets = presets,
        )
        return withPresets.copy(
            currentPresetIndex = readCurrentPreset(rt, withPresets),
            presetSheetVisible = previous.presetSheetVisible && presets.isNotEmpty(),
        )
    }

    private fun readBands(rt: EqualizerRuntime): List<EqualizerBandUiState> {
        val levelRange = rt.bandLevelRange
        return (0 until rt.numberOfBands).map { band ->
            val frequency = rt.bandFreqRange(band)
            EqualizerBandUiState(
                index = band,
                lowFrequencyHz = frequency.first / MILLIHERTZ_PER_HERTZ,
                highFrequencyHz = frequency.last / MILLIHERTZ_PER_HERTZ,
                minLevel = levelRange.first,
                maxLevel = levelRange.last,
                level = rt.getBandLevel(band),
            )
        }
    }

    private fun readPresets(rt: EqualizerRuntime): List<EqualizerPresetUiState> =
        (0 until rt.numberOfPresets).map { EqualizerPresetUiState(it, displayName(rt.presetName(it))) }

    /**
     * Some devices (verified on a Pixel 7) return preset names still carrying the native
     * buffer's terminating NUL - `"Normal\u0000"`. It is not part of the name, and it breaks
     * anything that treats the string as text (accessibility, XML serialisation, comparisons), so
     * it is stripped here; the name itself is exactly what the runtime reported.
     */
    private fun displayName(runtimeName: String): String = runtimeName.replace("\u0000", "").trim()

    /** The legacy `currentPreset` read: a failure, or a value outside the presets, is "none". */
    private fun readCurrentPreset(rt: EqualizerRuntime, state: EqualizerUiState): Int? =
        guarded<Int?>("currentPreset", null) {
            rt.currentPreset.takeIf { index -> state.presets.any { it.index == index } }
        }

    private fun EqualizerUiState.withBandLevel(band: Int, level: Int): EqualizerUiState =
        copy(bands = bands.map { if (it.index == band) it.copy(level = level) else it })

    private inline fun <T> guarded(what: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (all: Exception) {
        Timber.i(all, "An exception has occurred in Equalizer %s", what)
        fallback
    }
}
