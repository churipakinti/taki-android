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
import android.os.Handler
import android.os.Looper
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

    // True from the moment creation starts until the controller is connected (and, when the saved
    // session is restored, until the restored queue has landed in the player). Callers that arrive
    // in that window wait in [pendingReady] instead of acting on a controller that does not exist
    // yet. Deliberately separate from [created]: a started creation never runs a second time.
    private var initializing = false
    private val pendingReady = mutableListOf<Runnable>()
    private var readyTimeout: Runnable? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var headsetEventReceiver: BroadcastReceiver? = null

    private var rxBusSubscription = CompositeDisposable()

    // Listen to lifecycle events
    init {
        rxBusSubscription += RxBus.createServiceCommandObservable.subscribe {
            onCreate()
        }
    }

    fun onCreate() {
        onCreate(false, null)
    }

    /**
     * Starts the playback infrastructure ([MediaPlayerManager] and its controller) if needed and
     * restores the last saved session into the player.
     */
    fun onCreate(autoPlay: Boolean, afterRestore: Runnable?) {
        create(autoPlay, restoreSession = true, afterReady = afterRestore)
    }

    /**
     * Starts the same playback infrastructure for a session that begins from a *live* queue the
     * service was just handed (Android Auto selecting a track on a cold, service-only start),
     * without restoring the saved session on top of it. Idempotent and safe from any thread;
     * [afterReady] runs once the controller is connected and its listeners (stall watchdog,
     * network recovery, scrobbling, queue persistence, error handling) are attached.
     */
    fun ensureCreatedForPlayback(afterReady: Runnable? = null) {
        create(autoPlay = false, restoreSession = false, afterReady = afterReady)
    }

    private fun create(autoPlay: Boolean, restoreSession: Boolean, afterReady: Runnable?) {
        var runNow = false
        synchronized(this) {
            if (created) {
                if (initializing && afterReady != null) {
                    pendingReady += afterReady
                } else {
                    runNow = true
                }
            } else {
                created = true
                initializing = true
                if (afterReady != null) pendingReady += afterReady
                subscribeToShutdown()

                mediaPlayerManager.onCreate(onFailed = { onInitializationFailed() }) {
                    if (restoreSession) {
                        restoreLastSession(autoPlay, Runnable { markReady() })
                    } else {
                        // The saved queue is not read, so the serializer has to be told that it
                        // may persist the live one (it refuses writes until a session was read).
                        playbackStateSerializer.startFresh()
                        markReady()
                    }
                }

                registerHeadsetReceiver()

                cacheCleaner.clean()
                ratingManager = RatingManager.instance

                // Never leave callers waiting forever (e.g. a restore that fails to complete).
                val timeout = Runnable { markReady() }
                readyTimeout = timeout
                mainHandler.postDelayed(timeout, READY_TIMEOUT_MS)
                Timber.i("LifecycleSupport created (restoreSession=%s)", restoreSession)
            }
        }
        if (runNow) afterReady?.run()
    }

    private fun markReady() {
        val toRun: List<Runnable>
        synchronized(this) {
            if (!initializing) return
            initializing = false
            readyTimeout?.let { mainHandler.removeCallbacks(it) }
            readyTimeout = null
            toRun = pendingReady.toList()
            pendingReady.clear()
        }
        toRun.forEach { it.run() }
    }

    private fun onInitializationFailed() {
        Timber.e("MediaPlayerManager failed to start; playback infrastructure is not active")
        // Fail open: tear the half-built state down so the next request retries, and release the
        // waiting callers (a playback request must still reach the player).
        onDestroy()
        val toRun: List<Runnable>
        synchronized(this) {
            toRun = pendingReady.toList()
            pendingReady.clear()
        }
        toRun.forEach { it.run() }
    }

    // Re-created for every lifecycle: onDestroy() disposes the subscriptions, so subscribing only
    // once (in init) left every later session deaf to the stop/shutdown commands.
    private fun subscribeToShutdown() {
        rxBusSubscription += RxBus.shutdownCommandObservable.subscribe {
            onDestroy()
        }
        rxBusSubscription += RxBus.stopServiceCommandObservable.subscribe {
            onDestroy()
        }
    }

    private fun restoreLastSession(autoPlay: Boolean, afterRestore: Runnable?) {
        playbackStateSerializer.deserialize { state ->
            if (state != null && state.songs.isNotEmpty()) {
                Timber.i("Restoring %s songs", state.songs.size)
                // afterRestore must run once the queue has actually landed in the player, not
                // when the restore has merely been launched (issue #37).
                mediaPlayerManager.restore(state, autoPlay, afterRestore)
            } else {
                afterRestore?.run()
            }
        }
    }

    private fun onDestroy() {
        synchronized(this) {
            if (!created) return
            rxBusSubscription.dispose()
            rxBusSubscription = CompositeDisposable()

            headsetEventReceiver?.let {
                try {
                    applicationContext().unregisterReceiver(it)
                } catch (e: IllegalArgumentException) {
                    Timber.w(e, "Headset receiver was not registered")
                }
            }
            headsetEventReceiver = null

            imageLoaderProvider.clearImageLoader()

            readyTimeout?.let { mainHandler.removeCallbacks(it) }
            readyTimeout = null
            initializing = false

            created = false
        }
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
                        Settings.RESUME_PLAY_ON_HEADPHONE_PLUG && !mediaPlayerManager.isPlaying
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

                // KEYCODE_1..5 / KEYCODE_STAR (legacy star-rating keycodes) are deliberately not
                // handled here. UltrasonicIntentReceiver (CMD_PROCESS_KEYCODE) is exported with
                // no permission, so any app on the device could broadcast a fabricated KeyEvent
                // and trigger an unauthenticated server-side rating mutation via
                // mediaPlayerManager.legacySetRating()/legacyToggleStar(). Legitimate rating
                // control (Android Auto, notification, Assistant, lock screen) goes through
                // Media3's own MediaLibrarySessionCallback.onSetRating(), which is unaffected.
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

/** Longest a caller waits for the playback infrastructure before it proceeds regardless. */
internal const val READY_TIMEOUT_MS = 10_000L
