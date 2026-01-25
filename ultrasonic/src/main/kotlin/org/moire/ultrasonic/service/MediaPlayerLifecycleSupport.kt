/*
 * MediaPlayerLifecycleSupport.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.app.UApp.Companion.applicationContext
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.ifNotNull
import timber.log.Timber

/**
 * This class is responsible for handling received events for the Media Player implementation
 */
class MediaPlayerLifecycleSupport(
    val mediaPlayerManager: MediaPlayerManager,
    private val playbackStateSerializer: PlaybackStateSerializer,
    val imageLoaderProvider: ImageLoaderProvider,
    private val cacheCleaner: CacheCleaner
) : KoinComponent {
    private lateinit var ratingManager: RatingManager

    private var created = false
    private var headsetEventReceiver: BroadcastReceiver? = null

    private var rxBusSubscription = CompositeDisposable()

    // Listen to lifecycle events
    init {
        rxBusSubscription += RxBus.createServiceCommandObservable.subscribe {
            onCreate()
        }
        rxBusSubscription += RxBus.shutdownCommandObservable.subscribe {
            onDestroy()
        }
        rxBusSubscription += RxBus.stopServiceCommandObservable.subscribe {
            onDestroy()
        }
    }

    fun onCreate() {
        onCreate(false, null)
    }

    fun onCreate(autoPlay: Boolean, afterRestore: Runnable?) {
        if (created) {
            afterRestore?.run()
            return
        }

        mediaPlayerManager.onCreate {
            restoreLastSession(autoPlay, afterRestore)
        }

        registerHeadsetReceiver()

        cacheCleaner.clean()
        created = true
        ratingManager = RatingManager.instance
        Timber.i("LifecycleSupport created")
    }

    private fun restoreLastSession(autoPlay: Boolean, afterRestore: Runnable?) {
        playbackStateSerializer.deserialize {
            if (it == null) return@deserialize null
            Timber.i("Restoring %s songs", it.songs.size)

            mediaPlayerManager.restore(it, autoPlay)
            afterRestore?.run()
        }
    }

    private fun onDestroy() {
        if (!created) return
        rxBusSubscription.dispose()

        applicationContext().unregisterReceiver(headsetEventReceiver)

        imageLoaderProvider.clearImageLoader()

        created = false
        Timber.i("LifecycleSupport destroyed")
    }

    fun receiveIntent(intent: Intent?) {
        if (intent == null) return

        val intentAction = intent.action
        if (intentAction.isNullOrEmpty()) return

        Timber.i("Received intent: %s", intentAction)

        if (intentAction == Constants.CMD_PROCESS_KEYCODE) {
            if (intent.extras != null) {
                val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.extras!!.getParcelable(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.extras!![Intent.EXTRA_KEY_EVENT] as KeyEvent?
                }
                event.ifNotNull { handleKeyEvent(it) }
            }
        } else {
            handleUltrasonicIntent(intentAction)
        }
    }

    /**
     * The Headset Intent Receiver is responsible for resuming playback when a headset is inserted
     * and pausing it when it is removed.
     * Unfortunately this Intent can't be registered in the AndroidManifest, so it works only
     * while Ultrasonic is running.
     */
    private fun registerHeadsetReceiver() {
        headsetEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val extras = intent.extras ?: return

                Timber.i("Headset event for: %s", extras.getString("name"))

                val state = extras.getInt("state")

                if (state == 0) {
                    if (!mediaPlayerManager.isJukeboxEnabled) {
                        mediaPlayerManager.pause()
                    }
                } else if (state == 1) {
                    if (!mediaPlayerManager.isJukeboxEnabled &&
                        Settings.resumePlayOnHeadphonePlug && !mediaPlayerManager.isPlaying
                    ) {
                        mediaPlayerManager.prepare()
                        mediaPlayerManager.play()
                    }
                }
            }
        }

        val headsetIntentFilter = IntentFilter(AudioManager.ACTION_HEADSET_PLUG)

        applicationContext().registerReceiver(headsetEventReceiver, headsetIntentFilter)
    }

    @Suppress("MagicNumber", "ComplexMethod")
    private fun handleKeyEvent(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return

        val keyCode: Int = event.keyCode

        val autoStart =
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KeyEvent.KEYCODE_MEDIA_NEXT

        // We can receive intents (e.g. MediaButton) when everything is stopped, so we need to start
        onCreate(autoStart) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK
                -> mediaPlayerManager.togglePlayPause()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> mediaPlayerManager.seekToPrevious()
                KeyEvent.KEYCODE_MEDIA_NEXT -> mediaPlayerManager.seekToNext()
                KeyEvent.KEYCODE_MEDIA_STOP -> mediaPlayerManager.stop()
                KeyEvent.KEYCODE_MEDIA_PLAY -> mediaPlayerManager.play()
                KeyEvent.KEYCODE_MEDIA_PAUSE -> mediaPlayerManager.pause()
                KeyEvent.KEYCODE_1 -> mediaPlayerManager.legacySetRating(1)
                KeyEvent.KEYCODE_2 -> mediaPlayerManager.legacySetRating(2)
                KeyEvent.KEYCODE_3 -> mediaPlayerManager.legacySetRating(3)
                KeyEvent.KEYCODE_4 -> mediaPlayerManager.legacySetRating(4)
                KeyEvent.KEYCODE_5 -> mediaPlayerManager.legacySetRating(5)
                KeyEvent.KEYCODE_STAR -> mediaPlayerManager.legacyToggleStar()
                else -> {
                }
            }
        }
    }

    /**
     * This function processes the intent that could come from other applications.
     */
    @Suppress("ComplexMethod")
    private fun handleUltrasonicIntent(action: String) {
        val isRunning = created

        // If Ultrasonic is not running, do nothing to stop or pause
        if (!isRunning && (action == Constants.CMD_PAUSE || action == Constants.CMD_STOP)) {
            return
        }

        val autoStart = action == Constants.CMD_PLAY ||
            action == Constants.CMD_RESUME_OR_PLAY ||
            action == Constants.CMD_TOGGLEPAUSE ||
            action == Constants.CMD_PREVIOUS ||
            action == Constants.CMD_NEXT

        // We can receive intents when everything is stopped, so we need to start
        onCreate(autoStart) {
            when (action) {
                Constants.CMD_PLAY -> mediaPlayerManager.play()
                Constants.CMD_RESUME_OR_PLAY ->
                    // If Ultrasonic wasn't running, the autoStart is enough to resume,
                    // no need to call anything
                    if (isRunning) mediaPlayerManager.resumeOrPlay()

                Constants.CMD_NEXT -> mediaPlayerManager.seekToNext()
                Constants.CMD_PREVIOUS -> mediaPlayerManager.seekToPrevious()
                Constants.CMD_TOGGLEPAUSE -> mediaPlayerManager.togglePlayPause()
                Constants.CMD_STOP -> mediaPlayerManager.stop()
                Constants.CMD_PAUSE -> mediaPlayerManager.pause()
            }
        }
    }
}
