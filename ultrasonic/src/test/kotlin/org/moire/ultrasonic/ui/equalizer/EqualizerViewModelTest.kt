/*
 * EqualizerViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.equalizer

import androidx.lifecycle.MutableLiveData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.audiofx.AudioFxEqualizerRuntime
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.audiofx.EqualizerRuntime
import org.moire.ultrasonic.model.EqualizerViewModel
import org.robolectric.RobolectricTestRunner

/**
 * A scriptable [EqualizerRuntime]: records every write so tests can prove what was (and was not)
 * dispatched, and can be told to fail any single call to exercise the exception paths.
 */
private class FakeRuntime(
    bandCount: Int = 5,
    val levelRange: IntRange = -1500..1500,
    val presetNames: List<String> = listOf("Normal", "Classical", "Dance", "Flat", "Folk"),
    initialLevels: List<Int>? = null,
) : EqualizerRuntime {
    var enabledValue = true
    val levels: MutableList<Int> = (initialLevels ?: List(bandCount) { 0 }).toMutableList()
    var currentPresetValue = -1
    val presetLevels: MutableMap<Int, List<Int>> = mutableMapOf()
    var bandWriteClearsPreset = true

    val bandWrites = mutableListOf<Pair<Int, Int>>()
    val enabledWrites = mutableListOf<Boolean>()
    val usedPresets = mutableListOf<Int>()
    var saveCount = 0

    var failEnabledRead = false
    var failEnabledWrite = false
    var failBandRead = false
    var failBandWrite = false
    var failPresetListing = false
    var failCurrentPreset = false
    var failUsePreset = false
    var failSave = false

    override var enabled: Boolean
        get() = if (failEnabledRead) error("enabled read") else enabledValue
        set(value) {
            if (failEnabledWrite) error("enabled write")
            enabledWrites += value
            enabledValue = value
        }

    override val numberOfBands: Int
        get() = levels.size

    override val bandLevelRange: IntRange
        get() = levelRange

    override fun bandFreqRange(band: Int): IntRange = when (levels.size) {
        STANDARD_BANDS -> STANDARD_RANGES_MILLIHERTZ[band]
        else -> ((band + 1) * TEN_HERTZ)..((band + 2) * TEN_HERTZ)
    }

    override fun getBandLevel(band: Int): Int =
        if (failBandRead) error("band read") else levels[band]

    override fun setBandLevel(band: Int, level: Int) {
        if (failBandWrite) error("band write")
        bandWrites += band to level
        levels[band] = level
        if (bandWriteClearsPreset) currentPresetValue = -1
    }

    override val numberOfPresets: Int
        get() = if (failPresetListing) error("presets") else presetNames.size

    override fun presetName(preset: Int): String = presetNames[preset]

    override val currentPreset: Int
        get() = if (failCurrentPreset) error("current preset") else currentPresetValue

    override fun usePreset(preset: Int) {
        if (failUsePreset) error("use preset")
        usedPresets += preset
        currentPresetValue = preset
        presetLevels[preset]?.let { target -> target.forEachIndexed { i, l -> levels[i] = l } }
    }

    override fun saveSettings() {
        if (failSave) error("save")
        saveCount++
    }

    companion object {
        const val STANDARD_BANDS = 5
        const val TEN_HERTZ = 10_000

        /** The five bands a typical Android equalizer reports, in milliHertz. */
        val STANDARD_RANGES_MILLIHERTZ = listOf(
            30_000..120_000,
            120_000..460_000,
            460_000..1_800_000,
            1_800_000..7_000_000,
            7_000_000..20_000_000,
        )
    }
}

@RunWith(RobolectricTestRunner::class)
class EqualizerViewModelTest {

    private val source = MutableLiveData<EqualizerController?>()

    private fun vm(vararg wiring: Pair<EqualizerController, EqualizerRuntime>): EqualizerViewModel {
        val byController = wiring.toMap()
        return EqualizerViewModel().apply {
            controllerSource = source
            runtimeFactory = { byController.getValue(it) }
            attach()
        }
    }

    /** A view model already following a controller that is backed by [runtime]. */
    private fun connected(runtime: FakeRuntime): EqualizerViewModel {
        val model = EqualizerViewModel().apply {
            controllerSource = source
            runtimeFactory = { runtime }
            attach()
        }
        source.value = EqualizerController()
        return model
    }

    // --- Availability --------------------------------------------------------------------------

    @Test
    fun `without a controller the screen is unavailable and nothing crashes`() {
        val model = vm()
        val state = model.uiState.value
        assertFalse(state.controllerAvailable)
        assertTrue(state.bands.isEmpty())
        assertTrue(state.presets.isEmpty())

        // Every command is a safe no-op with no controller.
        model.onEnabledChange(true)
        model.onBandLevelChange(0, 100)
        model.onBandLevelChangeFinished(0)
        model.onPresetClick()
        model.onPresetSelected(1)
        model.saveSettings()
        model.refresh()
        assertFalse(model.uiState.value.controllerAvailable)
    }

    @Test
    fun `a controller makes the equalizer available and projects the runtime`() {
        val runtime = FakeRuntime(initialLevels = listOf(300, 0, -500, 0, 300))
        runtime.currentPresetValue = 3
        val state = connected(runtime).uiState.value

        assertTrue(state.controllerAvailable)
        assertTrue(state.enabled)
        assertEquals(5, state.bands.size)
        assertEquals(listOf(300, 0, -500, 0, 300), state.bands.map { it.level })
        assertEquals(5, state.presets.size)
        assertEquals(3, state.currentPresetIndex)
        assertEquals("Flat", state.currentPresetName)
    }

    @Test
    fun `the enabled state is the runtime's`() {
        val off = FakeRuntime().apply { enabledValue = false }
        assertFalse(connected(off).uiState.value.enabled)
    }

    // --- Bands: dynamic count, ranges, labels --------------------------------------------------

    @Test
    fun `the number of bands is whatever the runtime reports`() {
        assertEquals(3, connected(FakeRuntime(bandCount = 3)).uiState.value.bands.size)
        source.value = null
        assertEquals(8, connected(FakeRuntime(bandCount = 8)).uiState.value.bands.size)
    }

    @Test
    fun `each band carries its own frequency range and level range`() {
        val runtime = FakeRuntime(levelRange = -1200..1250)
        val bands = connected(runtime).uiState.value.bands

        assertEquals(30 to 120, bands[0].lowFrequencyHz to bands[0].highFrequencyHz)
        assertEquals(7000 to 20000, bands[4].lowFrequencyHz to bands[4].highFrequencyHz)
        bands.forEach {
            assertEquals(-1200, it.minLevel)
            assertEquals(1250, it.maxLevel)
        }
    }

    @Test
    fun `levels that are not whole decibels keep their millibel precision`() {
        val runtime = FakeRuntime(levelRange = -1250..1250, initialLevels = listOf(175, -330, 0, 1, -1))
        assertEquals(listOf(175, -330, 0, 1, -1), connected(runtime).uiState.value.bands.map { it.level })
    }

    // --- Master switch -------------------------------------------------------------------------

    @Test
    fun `toggling enabled writes the runtime and re-reads every band`() {
        val runtime = FakeRuntime().apply { enabledValue = false }
        val model = connected(runtime)

        // The runtime changes a level by itself while enabling (the legacy updateBars() re-read).
        runtime.levels[2] = 400
        model.onEnabledChange(true)

        assertEquals(listOf(true), runtime.enabledWrites)
        assertTrue(model.uiState.value.enabled)
        assertEquals(400, model.uiState.value.bands[2].level)
    }

    @Test
    fun `a failing enabled write does not crash and the state shows what the runtime reports`() {
        val runtime = FakeRuntime().apply { enabledValue = false; failEnabledWrite = true }
        val model = connected(runtime)

        model.onEnabledChange(true)

        assertFalse(model.uiState.value.enabled)
        assertTrue(model.uiState.value.controllerAvailable)
    }

    // --- Band writes ---------------------------------------------------------------------------

    @Test
    fun `moving a band writes the exact millibel level to that band only`() {
        val runtime = FakeRuntime()
        val model = connected(runtime)

        model.onBandLevelChange(2, 750)

        assertEquals(listOf(2 to 750), runtime.bandWrites)
        assertEquals(750, model.uiState.value.bands[2].level)
        assertEquals(0, model.uiState.value.bands[1].level)
    }

    @Test
    fun `no write happens while the equalizer is off`() {
        val runtime = FakeRuntime().apply { enabledValue = false }
        val model = connected(runtime)

        model.onBandLevelChange(0, 500)

        assertTrue(runtime.bandWrites.isEmpty())
        assertEquals(0, model.uiState.value.bands[0].level)
    }

    @Test
    fun `an unchanged level and an unknown band do not write`() {
        val runtime = FakeRuntime(initialLevels = listOf(100, 0, 0, 0, 0))
        val model = connected(runtime)

        model.onBandLevelChange(0, 100)
        model.onBandLevelChange(42, 100)

        assertTrue(runtime.bandWrites.isEmpty())
    }

    @Test
    fun `out of range levels are clamped to the runtime's range`() {
        val runtime = FakeRuntime(levelRange = -1000..1000)
        val model = connected(runtime)

        model.onBandLevelChange(0, 99_999)
        model.onBandLevelChange(1, -99_999)

        assertEquals(listOf(0 to 1000, 1 to -1000), runtime.bandWrites)
    }

    @Test
    fun `projecting and refreshing never write to the runtime`() {
        val runtime = FakeRuntime()
        val model = connected(runtime)

        model.refresh()
        model.refresh()
        model.onPresetClick()
        model.onPresetSheetDismiss()

        assertTrue(runtime.bandWrites.isEmpty())
        assertTrue(runtime.enabledWrites.isEmpty())
        assertTrue(runtime.usedPresets.isEmpty())
    }

    @Test
    fun `a failing band write does not crash and re-projects the runtime's real level`() {
        val runtime = FakeRuntime().apply { failBandWrite = true }
        val model = connected(runtime)

        model.onBandLevelChange(1, 600)

        assertEquals(0, model.uiState.value.bands[1].level)
        assertTrue(model.uiState.value.controllerAvailable)
    }

    @Test
    fun `finishing a drag reads back the stored level without writing`() {
        val runtime = FakeRuntime()
        val model = connected(runtime)
        model.onBandLevelChange(0, 137)
        val writesBefore = runtime.bandWrites.size

        // The runtime quantizes to a whole decibel when it stores the level.
        runtime.levels[0] = 100
        model.onBandLevelChangeFinished(0)

        assertEquals(100, model.uiState.value.bands[0].level)
        assertEquals(writesBefore, runtime.bandWrites.size)
    }

    // --- Presets -------------------------------------------------------------------------------

    @Test
    fun `presets are exactly the runtime's, in order`() {
        val runtime = FakeRuntime(presetNames = listOf("Alpha", "Beta", "Gamma"))
        val presets = connected(runtime).uiState.value.presets

        assertEquals(listOf("Alpha", "Beta", "Gamma"), presets.map { it.name })
        assertEquals(listOf(0, 1, 2), presets.map { it.index })
    }

    @Test
    fun `preset names carrying the native terminating NUL are cleaned for display`() {
        // Verified on a real Pixel 7: getPresetName() returns "Normal\u0000". The NUL must never
        // reach the UI state (it broke accessibility text and uiautomator's XML dump).
        val runtime = FakeRuntime(presetNames = listOf("Normal\u0000", "Heavy Metal\u0000", " Jazz \u0000"))
        runtime.currentPresetValue = 1
        val state = connected(runtime).uiState.value

        assertEquals(listOf("Normal", "Heavy Metal", "Jazz"), state.presets.map { it.name })
        assertEquals("Heavy Metal", state.currentPresetName)
        assertTrue(state.presets.none { it.name.contains('\u0000') })
    }

    @Test
    fun `selecting a preset uses it, refreshes every band, and closes the sheet`() {
        val runtime = FakeRuntime()
        runtime.presetLevels[1] = listOf(500, 300, 0, 300, 500)
        val model = connected(runtime)
        model.onPresetClick()
        assertTrue(model.uiState.value.presetSheetVisible)

        model.onPresetSelected(1)

        assertEquals(listOf(1), runtime.usedPresets)
        assertEquals(listOf(500, 300, 0, 300, 500), model.uiState.value.bands.map { it.level })
        assertEquals(1, model.uiState.value.currentPresetIndex)
        assertFalse(model.uiState.value.presetSheetVisible)
    }

    @Test
    fun `an unknown preset index is never sent to the runtime`() {
        val runtime = FakeRuntime()
        val model = connected(runtime)
        model.onPresetClick()

        model.onPresetSelected(99)

        assertTrue(runtime.usedPresets.isEmpty())
        assertFalse(model.uiState.value.presetSheetVisible)
    }

    @Test
    fun `a failing preset use does not crash`() {
        val runtime = FakeRuntime().apply { failUsePreset = true }
        val model = connected(runtime)

        model.onPresetSelected(2)

        assertTrue(model.uiState.value.controllerAvailable)
        assertEquals(5, model.uiState.value.bands.size)
    }

    @Test
    fun `no preset is shown as current when the runtime reports none`() {
        val runtime = FakeRuntime().apply { currentPresetValue = -1 }
        val state = connected(runtime).uiState.value

        assertNull(state.currentPresetIndex)
        assertNull(state.currentPresetName)
    }

    @Test
    fun `manual adjustment only changes the preset the runtime reports - nothing is invented`() {
        val clearing = FakeRuntime().apply { currentPresetValue = 3; bandWriteClearsPreset = true }
        val clearingModel = connected(clearing)
        assertEquals(3, clearingModel.uiState.value.currentPresetIndex)
        clearingModel.onBandLevelChange(0, 200)
        clearingModel.onBandLevelChangeFinished(0)
        assertNull(clearingModel.uiState.value.currentPresetIndex)

        source.value = null
        val sticky = FakeRuntime().apply { currentPresetValue = 3; bandWriteClearsPreset = false }
        val stickyModel = connected(sticky)
        stickyModel.onBandLevelChange(0, 200)
        stickyModel.onBandLevelChangeFinished(0)
        assertEquals(3, stickyModel.uiState.value.currentPresetIndex)
    }

    @Test
    fun `failing preset listing and current preset reads degrade without crashing`() {
        val runtime = FakeRuntime().apply { failPresetListing = true; failCurrentPreset = true }
        val state = connected(runtime).uiState.value

        assertTrue(state.controllerAvailable)
        assertTrue(state.presets.isEmpty())
        assertNull(state.currentPresetIndex)
        assertEquals(5, state.bands.size)
    }

    @Test
    fun `the preset sheet only opens when there are presets`() {
        val empty = FakeRuntime(presetNames = emptyList())
        val model = connected(empty)
        model.onPresetClick()
        assertFalse(model.uiState.value.presetSheetVisible)
    }

    // --- Controller replacement / disappearance ------------------------------------------------

    @Test
    fun `a replacement controller rebuilds the state and the old runtime is never written again`() {
        val oldRuntime = FakeRuntime(bandCount = 5, initialLevels = listOf(100, 100, 100, 100, 100))
        val newRuntime = FakeRuntime(bandCount = 3, initialLevels = listOf(-200, 0, 200))
        val oldController = EqualizerController()
        val newController = EqualizerController()
        val model = vm(oldController to oldRuntime, newController to newRuntime)

        source.value = oldController
        assertEquals(5, model.uiState.value.bands.size)
        model.onPresetClick()

        source.value = newController
        val rebuilt = model.uiState.value
        assertEquals(listOf(-200, 0, 200), rebuilt.bands.map { it.level })
        assertFalse("stale sheet must not survive a new controller", rebuilt.presetSheetVisible)

        model.onBandLevelChange(0, 50)
        model.saveSettings()
        assertEquals(listOf(0 to 50), newRuntime.bandWrites)
        assertTrue(oldRuntime.bandWrites.isEmpty())
        assertEquals(0, oldRuntime.saveCount)
        assertEquals(1, newRuntime.saveCount)
    }

    @Test
    fun `a controller that disappears makes the equalizer unavailable and commands stop`() {
        val runtime = FakeRuntime()
        val model = connected(runtime)
        model.onPresetClick()

        source.value = null

        assertFalse(model.uiState.value.controllerAvailable)
        assertFalse(model.uiState.value.presetSheetVisible)
        model.onBandLevelChange(0, 400)
        model.onEnabledChange(false)
        assertTrue(runtime.bandWrites.isEmpty())
        assertTrue(runtime.enabledWrites.isEmpty())
    }

    @Test
    fun `null then controller then null round-trips`() {
        val runtime = FakeRuntime()
        val controller = EqualizerController()
        val model = vm(controller to runtime)

        assertFalse(model.uiState.value.controllerAvailable)
        source.value = controller
        assertTrue(model.uiState.value.controllerAvailable)
        source.value = null
        assertFalse(model.uiState.value.controllerAvailable)
    }

    @Test
    fun `refresh re-projects what the runtime now holds`() {
        val runtime = FakeRuntime()
        val model = connected(runtime)

        runtime.levels[4] = -700
        runtime.enabledValue = false
        model.refresh()

        assertEquals(-700, model.uiState.value.bands[4].level)
        assertFalse(model.uiState.value.enabled)
    }

    // --- Persistence ---------------------------------------------------------------------------

    @Test
    fun `saveSettings is forwarded to the runtime exactly once per call`() {
        val runtime = FakeRuntime()
        val model = connected(runtime)

        model.saveSettings()

        assertEquals(1, runtime.saveCount)
    }

    @Test
    fun `a failing save does not crash`() {
        val runtime = FakeRuntime().apply { failSave = true }
        connected(runtime).saveSettings()
    }

    @Test
    fun `the real runtime adapter over a controller without an equalizer degrades safely`() {
        val controller = EqualizerController() // equalizer == null: every read throws
        val model = EqualizerViewModel().apply {
            controllerSource = source
            runtimeFactory = { AudioFxEqualizerRuntime(it) }
            attach()
        }

        source.value = controller
        model.onEnabledChange(true)
        model.onBandLevelChange(0, 100)
        model.onPresetSelected(0)
        model.saveSettings()

        val state = model.uiState.value
        assertTrue(state.controllerAvailable)
        assertTrue(state.bands.isEmpty())
        assertTrue(state.presets.isEmpty())
    }

    // --- Level formatting (the legacy updateLevelText) -----------------------------------------

    @Test
    fun `levels format exactly like the legacy dB text`() {
        assertEquals("0 dB", formatEqualizerLevel(0))
        assertEquals("+3 dB", formatEqualizerLevel(300))
        assertEquals("-15 dB", formatEqualizerLevel(-1500))
        assertEquals("+15 dB", formatEqualizerLevel(1500))
        assertEquals("+1 dB", formatEqualizerLevel(150))
        assertEquals("-1 dB", formatEqualizerLevel(-150))
        assertEquals("0 dB", formatEqualizerLevel(50))
        assertEquals("0 dB", formatEqualizerLevel(-50))
    }
}
