/*
 * MediaPlayerLifecycleSupportTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.view.KeyEvent
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.CacheCleaner

/**
 * Regression coverage for the security fix removing the legacy KEYCODE_1..5/KEYCODE_STAR
 * rating branches from [MediaPlayerLifecycleSupport.handleKeyEvent]. That method is reachable
 * from the exported, unauthenticated `UltrasonicIntentReceiver` (action CMD_PROCESS_KEYCODE),
 * so those keycodes must never again reach [MediaPlayerManager.legacySetRating]/
 * [MediaPlayerManager.legacyToggleStar] -- doing so previously let any app on the device
 * trigger a real server-side rating mutation with no permission check.
 *
 * `handleKeyEvent` is private and, on a fresh instance, is wrapped in [MediaPlayerLifecycleSupport
 * .onCreate], which registers a real headset [android.content.BroadcastReceiver] via a static
 * Application [android.content.Context] and touches [RatingManager.instance] -- none of which
 * are available in a plain JVM unit test without introducing Robolectric + Koin scaffolding.
 * Since `onCreate` becomes a pure passthrough once its `created` flag is already `true`, this
 * test flips that private flag via reflection so `handleKeyEvent`'s actual keycode-dispatch
 * logic runs directly against mocked collaborators, without needing that additional
 * infrastructure for what is otherwise a small, self-contained fix.
 *
 * One more real-Android dependency has to be worked around even so: merely constructing a
 * [MediaPlayerLifecycleSupport] runs [RxBus]'s companion `init`, which eagerly evaluates
 * `activeServerChangedObservable = ...observeOn(AndroidSchedulers.mainThread())` -- and
 * `AndroidSchedulers.mainThread()` calls the real `Looper.getMainLooper()`, which throws under
 * a plain JVM unit test. [RxAndroidPlugins.setInitMainThreadSchedulerHandler] is RxAndroid's
 * own, standard hook for exactly this situation; it must be installed before [RxBus] is first
 * referenced, hence the `@BeforeClass` below.
 */
class MediaPlayerLifecycleSupportTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUpRxAndroidScheduler() {
            RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        }
    }

    private val mediaPlayerManager: MediaPlayerManager = mock()
    private val playbackStateSerializer: PlaybackStateSerializer = mock()
    private val imageLoaderProvider: ImageLoaderProvider = mock()
    private val cacheCleaner: CacheCleaner = mock()

    private val support = MediaPlayerLifecycleSupport(
        mediaPlayerManager,
        playbackStateSerializer,
        imageLoaderProvider,
        cacheCleaner
    ).also { instance ->
        // Skip the real onCreate() cascade (headset receiver registration, RatingManager
        // singleton, cache cleanup) -- irrelevant to keycode routing and not safely
        // constructible in a plain JVM test. See class doc above.
        val createdField = MediaPlayerLifecycleSupport::class.java.getDeclaredField("created")
        createdField.isAccessible = true
        createdField.setBoolean(instance, true)
    }

    private fun dispatchKeyCode(keyCode: Int) {
        val event: KeyEvent = mock()
        whenever(event.action).thenReturn(KeyEvent.ACTION_DOWN)
        whenever(event.repeatCount).thenReturn(0)
        whenever(event.keyCode).thenReturn(keyCode)

        val handleKeyEvent = MediaPlayerLifecycleSupport::class.java
            .getDeclaredMethod("handleKeyEvent", KeyEvent::class.java)
        handleKeyEvent.isAccessible = true
        handleKeyEvent.invoke(support, event)
    }

    @Test
    fun `legacy star keycode does not submit a rating`() {
        dispatchKeyCode(KeyEvent.KEYCODE_STAR)

        verify(mediaPlayerManager, never()).legacyToggleStar()
        verify(mediaPlayerManager, never()).legacySetRating(any())
    }

    @Test
    fun `legacy numeric rating keycodes do not submit a rating`() {
        for (keyCode in intArrayOf(
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5
        )) {
            dispatchKeyCode(keyCode)
        }

        verify(mediaPlayerManager, never()).legacySetRating(any())
        verify(mediaPlayerManager, never()).legacyToggleStar()
    }

    @Test
    fun `legitimate transport keycodes still dispatch`() {
        dispatchKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT)
        verify(mediaPlayerManager).seekToNext()

        dispatchKeyCode(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        verify(mediaPlayerManager).seekToPrevious()

        dispatchKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        verify(mediaPlayerManager).togglePlayPause()

        dispatchKeyCode(KeyEvent.KEYCODE_MEDIA_STOP)
        verify(mediaPlayerManager).stop()
    }
}
