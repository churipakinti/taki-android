/*
 * EqualizerRuntime.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.audiofx

import android.media.audiofx.Equalizer

/**
 * The narrow surface the Compose Equalizer screen needs from the active audio-effect runtime
 * (issue #10 phase 5A5): reads and commands, nothing else. It exists so the screen's state holder
 * never touches a raw [Equalizer] and so unit tests can fake the runtime without a real hardware
 * effect. Every value is the runtime's own - nothing here is hard-coded (band count, frequency
 * ranges, level range and preset names all come from the device).
 *
 * All members may throw (the underlying `AudioEffect` throws `IllegalStateException`/
 * `UnsupportedOperationException`/... when the effect is released or unsupported) - callers are
 * expected to guard every call, exactly like the legacy `EqualizerFragment` did.
 */
interface EqualizerRuntime {
    var enabled: Boolean

    val numberOfBands: Int

    /** Minimum..maximum band level, in millibels. */
    val bandLevelRange: IntRange

    /** The band's frequency range, in milliHertz. */
    fun bandFreqRange(band: Int): IntRange

    /** The band's current level, in millibels. */
    fun getBandLevel(band: Int): Int

    fun setBandLevel(band: Int, level: Int)

    val numberOfPresets: Int

    fun presetName(preset: Int): String

    /** The preset the runtime reports as active; outside `0 until numberOfPresets` means none. */
    val currentPreset: Int

    fun usePreset(preset: Int)

    /** Persists the current settings, exactly like the legacy `EqualizerFragment.onPause()`. */
    fun saveSettings()
}

/**
 * The production [EqualizerRuntime]: a thin view over the existing [EqualizerController]. It never
 * caches the [Equalizer] - the controller's current instance is read on every call - and it does
 * not own, create or release anything: the controller (and `PlaybackService`, which creates it
 * with the player's audio session) stay authoritative.
 */
class AudioFxEqualizerRuntime(private val controller: EqualizerController) : EqualizerRuntime {

    private val equalizer: Equalizer
        get() = controller.equalizer ?: error("The equalizer is not available")

    override var enabled: Boolean
        get() = equalizer.enabled
        set(value) {
            equalizer.enabled = value
        }

    override val numberOfBands: Int
        get() = equalizer.numberOfBands.toInt()

    override val bandLevelRange: IntRange
        get() = equalizer.bandLevelRange.let { it[0].toInt()..it[1].toInt() }

    override fun bandFreqRange(band: Int): IntRange =
        equalizer.getBandFreqRange(band.toShort()).let { it[0]..it[1] }

    override fun getBandLevel(band: Int): Int = equalizer.getBandLevel(band.toShort()).toInt()

    override fun setBandLevel(band: Int, level: Int) =
        equalizer.setBandLevel(band.toShort(), level.toShort())

    override val numberOfPresets: Int
        get() = equalizer.numberOfPresets.toInt()

    override fun presetName(preset: Int): String = equalizer.getPresetName(preset.toShort())

    override val currentPreset: Int
        get() = equalizer.currentPreset.toInt()

    override fun usePreset(preset: Int) = equalizer.usePreset(preset.toShort())

    override fun saveSettings() = controller.saveSettings()
}
