/*
 * PlaybackInfrastructureStartTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Looper
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.CacheCleaner
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Issue #43 / AA-01: a cold, service-only Android Auto session has to start the same playback
 * infrastructure (MediaPlayerManager + controller, with the stall watchdog, network recovery,
 * scrobbling, queue persistence and error handling that hang off it) as the phone UI, exactly
 * once, from any entry point, without restoring the saved queue on top of a live one.
 *
 * The real [MediaPlayerLifecycleSupport] runs against a mocked [MediaPlayerManager]; the
 * manager's `onCreate(onFailed, onCreated)` is captured so each test decides when the controller
 * "connects".
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackInfrastructureStartTest {

    private val manager: MediaPlayerManager = mock()
    private val serializer: PlaybackStateSerializer = mock()
    private val imageLoaderProvider: ImageLoaderProvider = mock()
    private val cacheCleaner: CacheCleaner = mock()

    private lateinit var support: MediaPlayerLifecycleSupport

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        support = MediaPlayerLifecycleSupport(manager, serializer, imageLoaderProvider, cacheCleaner)
    }

    @After
    fun tearDown() {
        // Leave the process-wide bus clean for the next test.
        RxBus.stopServiceCommandPublisher.onNext(Unit)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun managerStarts(times: Int = 1) = verify(manager, times(times)).onCreate(any(), any())

    /** Simulates the manager's controller connecting for the [index]-th start. */
    private fun controllerConnects(index: Int = 0) {
        val created = argumentCaptor<() -> Unit>()
        verify(manager, org.mockito.kotlin.atLeast(index + 1)).onCreate(any(), created.capture())
        created.allValues[index].invoke()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun controllerFails(index: Int = 0) {
        val failed = argumentCaptor<() -> Unit>()
        verify(manager, org.mockito.kotlin.atLeast(index + 1)).onCreate(failed.capture(), any())
        failed.allValues[index].invoke()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun savedState() = PlaybackState(listOf(Track(id = "t1"), Track(id = "t2")), 1, 4_200, false, 0)

    private fun deserializeAnswers(state: PlaybackState?) {
        val callback = argumentCaptor<(PlaybackState?) -> Unit?>()
        verify(serializer).deserialize(callback.capture())
        callback.firstValue.invoke(state)
    }

    // ---- TEST 1: cold service playback ----

    @Test
    fun `a playback request on a cold service starts the manager once and waits for the controller`() {
        var ready = 0
        support.ensureCreatedForPlayback { ready++ }

        managerStarts()
        assertEquals("callers wait until the controller is connected", 0, ready)

        controllerConnects()

        assertEquals(1, ready)
        managerStarts()
    }

    @Test
    fun `a cold service playback start does not restore the saved queue but enables persistence`() {
        support.ensureCreatedForPlayback()
        controllerConnects()

        verify(serializer, never()).deserialize(any())
        verify(manager, never()).restore(any(), any(), anyOrNull())
        // Without this the serializer refuses every write (it only trusts a session it has read).
        verify(serializer).startFresh()
    }

    // ---- TEST 3 / 4: repeated and concurrent requests ----

    @Test
    fun `repeated requests start the infrastructure only once and each caller runs once`() {
        var ready = 0
        repeat(3) { support.ensureCreatedForPlayback { ready++ } }
        managerStarts()

        controllerConnects()
        assertEquals(3, ready)

        // Already running: immediate, still a single start.
        support.ensureCreatedForPlayback { ready++ }
        assertEquals(4, ready)
        managerStarts()
        verify(serializer, times(1)).startFresh()
    }

    @Test
    fun `concurrent entry points produce exactly one initialization`() {
        val threads = 16
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val callbacks = AtomicInteger()

        repeat(threads) { i ->
            thread {
                start.await()
                when (i % 3) {
                    0 -> support.ensureCreatedForPlayback { callbacks.incrementAndGet() }
                    1 -> support.onCreate(false) { callbacks.incrementAndGet() }
                    else -> support.onCreate()
                }
                done.countDown()
            }
        }
        start.countDown()
        done.await()

        managerStarts()
        controllerConnects()
        // Only one entry point won the start; whichever it was, the single restore/fresh decision
        // was taken once.
        verify(serializer, org.mockito.kotlin.atMost(1)).startFresh()
        verify(serializer, org.mockito.kotlin.atMost(1)).deserialize(any())
        // Every caller that supplied a callback eventually runs (immediately or when ready).
        shadowOf(Looper.getMainLooper()).idle()
        val expected = (0 until threads).count { it % 3 != 2 }
        // Callers from the restore path wait for the restore; complete it if it was the winner.
        if (callbacks.get() < expected) {
            deserializeAnswers(null)
        }
        assertEquals(expected, callbacks.get())
    }

    // ---- TEST 5: phone first ----

    @Test
    fun `phone first reuses the manager and a later car request is immediate`() {
        support.onCreate()
        controllerConnects()
        deserializeAnswers(null)

        var ready = 0
        support.ensureCreatedForPlayback { ready++ }

        assertEquals("the existing manager is reused, with no wait", 1, ready)
        managerStarts()
        verify(serializer, never()).startFresh()
    }

    // ---- TEST 6: car first, phone later ----

    @Test
    fun `car first then phone attaches to the live queue without restoring over it`() {
        support.ensureCreatedForPlayback()
        controllerConnects()

        // The phone UI opens afterwards (NavigationActivity.onCreate).
        support.onCreate()

        managerStarts()
        verify(serializer, never()).deserialize(any())
        verify(manager, never()).restore(any(), any(), anyOrNull())
    }

    // ---- TEST 7: resumption ----

    @Test
    fun `playback resumption still restores the saved queue and completes after it landed`() {
        var resumed = 0
        support.onCreate(true) { resumed++ }
        controllerConnects()
        assertEquals("not before the queue is in the player", 0, resumed)

        val state = savedState()
        deserializeAnswers(state)
        val onRestored = argumentCaptor<Runnable>()
        verify(manager).restore(org.mockito.kotlin.eq(state), org.mockito.kotlin.eq(true), onRestored.capture())
        assertEquals(0, resumed)

        onRestored.firstValue.run()
        assertEquals(1, resumed)
        verify(serializer, never()).startFresh()
    }

    @Test
    fun `resumption after a car start uses the live queue and does not restore`() {
        support.ensureCreatedForPlayback()
        controllerConnects()

        var resumed = 0
        support.onCreate(true) { resumed++ }

        assertEquals(1, resumed)
        verify(manager, never()).restore(any(), any(), anyOrNull())
    }

    // ---- failure and timeout ----

    @Test
    fun `a controller that cannot connect releases the waiting caller and allows a retry`() {
        var ready = 0
        support.ensureCreatedForPlayback { ready++ }

        controllerFails()
        assertEquals("playback must not be blocked by a failed start", 1, ready)

        support.ensureCreatedForPlayback { ready++ }
        managerStarts(2)
        controllerConnects(1)
        assertEquals(2, ready)
    }

    @Test
    fun `a start that never completes releases waiting callers after the timeout`() {
        var ready = 0
        support.ensureCreatedForPlayback { ready++ }
        assertEquals(0, ready)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(READY_TIMEOUT_MS + 100))

        assertEquals(1, ready)
        managerStarts()
    }

    // ---- TEST 8: cleanup ----

    @Test
    fun `every lifecycle releases its receiver and listens for the next stop command`() {
        val application = org.robolectric.RuntimeEnvironment.getApplication()
        val receivers = { shadowOf(application).registeredReceivers.size }
        val baseline = receivers()

        repeat(3) { round ->
            support.ensureCreatedForPlayback()
            controllerConnects(round)
            assertEquals("one headset receiver while running", baseline + 1, receivers())

            RxBus.stopServiceCommandPublisher.onNext(Unit)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("released on stop", baseline, receivers())
        }

        // Each round was a fresh start: the third stop would have been ignored by the old code
        // (its subscriptions were disposed by the first stop), leaving the receiver registered.
        managerStarts(3)
    }
}
