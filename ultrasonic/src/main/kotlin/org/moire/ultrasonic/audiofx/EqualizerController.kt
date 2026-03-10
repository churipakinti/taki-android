/*
 * EqualizerController.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.audiofx

import android.media.audiofx.Equalizer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.Serializable
import java.lang.Exception
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.util.FileUtil.deserialize
import org.moire.ultrasonic.util.FileUtil.serialize
import timber.log.Timber

/**
 * Wrapper for [Equalizer] with automatic restoration of presets and settings.
 *
 * TODO: Maybe store the settings in the DB?
 */
class EqualizerController : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    @JvmField
    var equalizer: Equalizer? = null
    private var audioSessionId = 0

    fun saveSettings() {
        if (equalizer == null) return
        launch {
            try {
                serialize(
                    UApp.applicationContext(),
                    EqualizerSettings(equalizer!!),
                    "equalizer.dat"
                )
            } catch (all: Throwable) {
                Timber.w(all, "Failed to save equalizer settings.")
            }
        }
    }

    fun loadSettings() {
        if (equalizer == null) return
        launch {
            try {
                val settings = deserialize<EqualizerSettings>(
                    UApp.applicationContext(),
                    "equalizer.dat"
                )
                settings?.apply(equalizer!!)
            } catch (all: Throwable) {
                Timber.w(all, "Failed to load equalizer settings.")
            }
        }
    }

    private class EqualizerSettings(equalizer: Equalizer) : Serializable {
        private val bandLevels: ShortArray
        private var preset: Short = 0
        private val enabled: Boolean

        fun apply(equalizer: Equalizer) {
            for (i in bandLevels.indices) {
                equalizer.setBandLevel(i.toShort(), bandLevels[i])
            }
            if (preset >= 0 && preset < equalizer.numberOfPresets) {
                equalizer.usePreset(preset)
            }
            equalizer.enabled = enabled
        }

        init {
            enabled = equalizer.enabled
            bandLevels = ShortArray(equalizer.numberOfBands.toInt())
            for (i in 0 until equalizer.numberOfBands) {
                bandLevels[i] = equalizer.getBandLevel(i.toShort())
            }
            preset = try {
                equalizer.currentPreset
            } catch (ignored: Exception) {
                -1
            }
        }

        companion object {
            private const val serialVersionUID = 6269873247206061L
        }
    }

    companion object {
        private val instance = MutableLiveData<EqualizerController?>()

        /**
         * Retrieves the EqualizerController as LiveData
         */
        @JvmStatic
        fun get(): LiveData<EqualizerController?> = instance

        /**
         * Initializes the EqualizerController instance with a Session
         *
         * @param sessionId
         * @return the new controller
         */
        fun create(sessionId: Int): EqualizerController? {
            val controller = EqualizerController()
            return try {
                controller.audioSessionId = sessionId
                controller.equalizer = Equalizer(0, controller.audioSessionId)
                controller.loadSettings()
                instance.postValue(controller)
                controller
            } catch (all: Throwable) {
                Timber.w(all, "Failed to create equalizer.")
                null
            }
        }

        /**
         * Releases the EqualizerController instance when the underlying MediaPlayer is no longer available
         */
        fun release() {
            val controller = instance.value ?: return
            controller.equalizer!!.release()
            instance.postValue(null)
        }
    }
}
