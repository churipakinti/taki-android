/*
 * EqualizerFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.media.audiofx.Equalizer
import android.os.Bundle
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.lang.Exception
import java.util.HashMap
import java.util.Locale
import org.moire.ultrasonic.R
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.util.Util.applyTheme
import timber.log.Timber

/**
 * Displays the Equalizer
 */
class EqualizerFragment : Fragment() {
    private val bars: MutableMap<Short, SeekBar> = HashMap()
    private var equalizerController: EqualizerController? = null
    private var equalizer: Equalizer? = null
    private var equalizerLayout: LinearLayout? = null
    private var presetButton: View? = null
    private var enabledCheckBox: CheckBox? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.equalizer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, R.string.equalizer_label)
        equalizerLayout = view.findViewById(R.id.equalizer_layout)
        presetButton = view.findViewById(R.id.equalizer_preset)
        enabledCheckBox = view.findViewById(R.id.equalizer_enabled)

        // Subscribe to changes in the active controller
        EqualizerController.get().observe(viewLifecycleOwner) { controller ->
            if (controller != null) {
                Timber.d("EqualizerController Observer.onChanged received controller")
                equalizerController = controller
                equalizer = controller.equalizer
                setup()
            } else {
                Timber.d("EqualizerController Observer.onChanged has no controller")
                equalizerController = null
                equalizer = null
            }
        }
    }

    override fun onPause() {
        super.onPause()
        equalizerController?.saveSettings()
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, view, menuInfo)

        if (equalizer == null) return
        val currentPreset: Short = try {
            equalizer!!.currentPreset
        } catch (ignored: Exception) {
            -1
        }
        for (preset in 0 until equalizer!!.numberOfPresets) {
            val menuItem = menu.add(
                MENU_GROUP_PRESET,
                preset,
                preset,
                equalizer!!.getPresetName(
                    preset.toShort()
                )
            )
            if (preset == currentPreset.toInt()) {
                menuItem.isChecked = true
            }
        }
        menu.setGroupCheckable(MENU_GROUP_PRESET, true, true)
    }

    override fun onContextItemSelected(menuItem: MenuItem): Boolean {
        if (equalizer == null) return true
        try {
            val preset = menuItem.itemId.toShort()
            equalizer!!.usePreset(preset)
            updateBars()
        } catch (all: Exception) {
            // TODO: Show a dialog?
            Timber.i(all, "An exception has occurred in EqualizerFragment onContextItemSelected")
        }
        return true
    }

    private fun setup() {
        initEqualizer()
        registerForContextMenu(presetButton!!)
        presetButton!!.setOnClickListener { presetButton!!.showContextMenu() }
        enabledCheckBox!!.isChecked = equalizer!!.enabled
        enabledCheckBox!!.setOnCheckedChangeListener { _, b -> setEqualizerEnabled(b) }
    }

    private fun setEqualizerEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        updateBars()
    }

    private fun updateBars() {
        if (equalizer == null) return
        try {
            for ((band, bar) in bars) {
                bar.isEnabled = equalizer!!.enabled
                val minEQLevel = equalizer!!.bandLevelRange[0]
                bar.progress = equalizer!!.getBandLevel(band) - minEQLevel
            }
        } catch (all: Exception) {
            // TODO: Show a dialog?
            Timber.i(all, "An exception has occurred in EqualizerFragment updateBars")
        }
    }

    private fun initEqualizer() {
        if (equalizer == null) return
        try {
            val bandLevelRange = equalizer!!.bandLevelRange
            val numberOfBands = equalizer!!.numberOfBands

            val minEQLevel = bandLevelRange[0]
            val maxEQLevel = bandLevelRange[1]

            for (i in 0 until numberOfBands) {
                val bandBar = createSeekBarForBand(i, maxEQLevel, minEQLevel)
                equalizerLayout!!.addView(bandBar)
            }
        } catch (all: Exception) {
            // TODO: Show a dialog?
            Timber.i(all, "An exception has occurred while initializing Equalizer")
        }
    }

    private fun createSeekBarForBand(index: Int, maxEQLevel: Short, minEQLevel: Short): View {
        val band = index.toShort()
        val bandBar = LayoutInflater.from(context)
            .inflate(R.layout.equalizer_bar, equalizerLayout, false)

        val freqTextView: TextView = bandBar.findViewById(R.id.equalizer_frequency)
        val levelTextView: TextView = bandBar.findViewById(R.id.equalizer_level)
        val bar: SeekBar = bandBar.findViewById(R.id.equalizer_bar)

        val range = equalizer!!.getBandFreqRange(band)

        freqTextView.text = String.format(
            Locale.getDefault(),
            "%d - %d Hz",
            range[0] / 1000,
            range[1] / 1000
        )

        bars[band] = bar
        bar.max = maxEQLevel - minEQLevel
        val bandLevel = equalizer!!.getBandLevel(band)
        bar.progress = bandLevel - minEQLevel
        bar.isEnabled = equalizer!!.enabled
        updateLevelText(levelTextView, bandLevel)

        bar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val level = (progress + minEQLevel).toShort()
                if (fromUser) {
                    try {
                        equalizer!!.setBandLevel(band, level)
                    } catch (all: Exception) {
                        // TODO: Show a dialog?
                        Timber.i(
                            all,
                            "An exception has occurred in Equalizer onProgressChanged"
                        )
                    }
                }
                updateLevelText(levelTextView, level)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        return bandBar
    }

    companion object {
        private const val MENU_GROUP_PRESET = 100
        private fun updateLevelText(levelTextView: TextView?, level: Short) {
            if (levelTextView != null) {
                levelTextView.text = String.format(
                    Locale.getDefault(),
                    "%s%d dB",
                    if (level > 0) "+" else "",
                    level / 100
                )
            }
        }
    }
}
