/*
 * SleepTimerControllerTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.Test

/**
 * Locks down SleepTimerController's contract (docs/TAKI_SLEEP_TIMER_FINAL_FEATURE.md sections 6
 * and 8): deadlines computed from a monotonic clock, expire-exactly-once, cancel/replace never
 * leaving a stray Job, and end-of-track mode reacting only to reported natural completions.
 *
 * Uses a [TestScope] for virtual time (no real delays) with [TestScope.currentTime] doubling as
 * the fake elapsedRealtime clock, since both are meant to represent the same monotonic timeline
 * in production (delay() and SystemClock.elapsedRealtime() are both driven off it).
 */
class SleepTimerControllerTest {

    private fun buildController(
        scope: TestScope,
        onExpire: () -> Unit
    ): SleepTimerController = SleepTimerController(
        scope = scope,
        elapsedRealtimeMs = { scope.currentTime },
        onExpire = onExpire
    )

    @Test
    fun `initial state is Off`() = runTest {
        var expireCount = 0
        val controller = buildController(this) { expireCount++ }

        controller.state.shouldBeInstanceOf<SleepTimerState.Off>()
        expireCount shouldBeEqualTo 0
    }

    @Test
    fun `setDuration creates the correct deadline for 15, 30, 45 and 60 minutes`() = runTest {
        val controller = buildController(this) {}

        for (minutes in listOf(15, 30, 45, 60)) {
            val before = currentTime
            controller.setDuration(minutes)
            val state = controller.state
            state.shouldBeInstanceOf<SleepTimerState.Duration>()
            (state as SleepTimerState.Duration).deadlineElapsedRealtime shouldBeEqualTo
                before + minutes * 60_000L
        }
    }

    @Test
    fun `activating a new timer cancels the previous one`() = runTest {
        var expireCount = 0
        val controller = buildController(this) { expireCount++ }

        controller.setDuration(30) // would fire at t=30
        advanceTimeBy(10 * 60_000L) // 10 of the 30 minutes elapse, t=10

        controller.setDuration(15) // replace: new deadline is t=25
        advanceTimeBy(30 * 60_000L) // well past both t=25 and the original t=30
        runCurrentSafely()

        // If the first Job hadn't been cancelled, this would be 2 (it firing at its original
        // t=30 in addition to the replacement firing at t=25).
        expireCount shouldBeEqualTo 1
    }

    @Test
    fun `cancel does not trigger the expire callback`() = runTest {
        var expireCount = 0
        val controller = buildController(this) { expireCount++ }

        controller.setDuration(30)
        advanceTimeBy(5 * 60_000L)
        controller.cancel()
        advanceUntilIdle()

        expireCount shouldBeEqualTo 0
        controller.state.shouldBeInstanceOf<SleepTimerState.Off>()
    }

    @Test
    fun `expiring triggers the expire callback exactly once`() = runTest {
        var expireCount = 0
        val controller = buildController(this) { expireCount++ }

        controller.setDuration(15)
        advanceUntilIdle()

        expireCount shouldBeEqualTo 1
    }

    @Test
    fun `deadline only depends on elapsed time, not any absolute reference`() = runTest {
        val controller = buildController(this) {}

        // Arm well after t=0, simulating a device that's been running a while - the deadline
        // must still land exactly [minutes] later, with no dependency on t=0 or wall-clock time
        // (this controller never reads System.currentTimeMillis() at all).
        advanceTimeBy(3 * 60 * 60_000L)
        val armedAt = currentTime
        controller.setDuration(30)

        val state = controller.state as SleepTimerState.Duration
        state.deadlineElapsedRealtime shouldBeEqualTo armedAt + 30 * 60_000L
    }

    @Test
    fun `remaining time never goes negative`() {
        // Pure computation, independent of the controller/scheduler: reading remaining time
        // arbitrarily far past a deadline must clamp to zero, never go negative.
        val state = SleepTimerState.Duration(deadlineElapsedRealtime = 10_000L)

        state.remainingMs(nowElapsedRealtime = 10_000L) shouldBeEqualTo 0L
        state.remainingMs(nowElapsedRealtime = 50_000L) shouldBeEqualTo 0L
        state.remainingMs(nowElapsedRealtime = Long.MAX_VALUE) shouldBeEqualTo 0L
    }

    @Test
    fun `state returns to Off after completing`() = runTest {
        val controller = buildController(this) {}
        controller.setDuration(15)
        advanceUntilIdle()

        controller.state.shouldBeInstanceOf<SleepTimerState.Off>()
    }

    @Test
    fun `onTrackFinishedNaturally is a no-op when not armed for end-of-track`() = runTest {
        var expireCount = 0
        val controller = buildController(this) { expireCount++ }

        // Off
        controller.onTrackFinishedNaturally()
        expireCount shouldBeEqualTo 0

        // Duration - a manual skip or natural track change must not touch a running minutes
        // timer at all.
        controller.setDuration(30)
        controller.onTrackFinishedNaturally()
        expireCount shouldBeEqualTo 0
        controller.state.shouldBeInstanceOf<SleepTimerState.Duration>()
    }

    @Test
    fun `onTrackFinishedNaturally expires an armed end-of-track timer`() = runTest {
        var expireCount = 0
        val controller = buildController(this) { expireCount++ }

        controller.setEndOfTrack()
        controller.state.shouldBeInstanceOf<SleepTimerState.EndOfTrack>()

        // Represents any of the three natural-completion paths MediaPlayerManager reports here:
        // an automatic transition to the next track, a repeat-one loop, or the queue reaching
        // STATE_ENDED - the controller itself does not distinguish between them, by design (see
        // class doc); MediaPlayerManager is the one that must never call this for a manual skip.
        controller.onTrackFinishedNaturally()

        expireCount shouldBeEqualTo 1
        controller.state.shouldBeInstanceOf<SleepTimerState.Off>()
    }

    @Test
    fun `end-of-track mode never fires on its own without a reported completion`() = runTest {
        var expireCount = 0
        val controller = buildController(this) { expireCount++ }

        controller.setEndOfTrack()
        advanceTimeBy(60 * 60_000L) // an hour of virtual time, no delay Job exists for this mode
        advanceUntilIdle()

        expireCount shouldBeEqualTo 0
        controller.state.shouldBeInstanceOf<SleepTimerState.EndOfTrack>()
    }

    @Test
    fun `cancelling after expiry is already scheduled does not invoke a late callback`() =
        runTest {
            var expireCount = 0
            val controller = buildController(this) { expireCount++ }

            controller.setDuration(15)
            advanceTimeBy(5 * 60_000L) // Job is pending, not yet fired
            controller.cancel() // simulates MediaPlayerManager.onDestroy()/clear()
            advanceUntilIdle() // let any (wrongly) still-pending Job run if it exists

            expireCount shouldBeEqualTo 0
        }

    @Test
    fun `two rapid activations leave only one active Job`() = runTest {
        var expireCount = 0
        val controller = buildController(this) { expireCount++ }

        controller.setDuration(15)
        controller.setDuration(30)
        controller.setDuration(45)
        advanceUntilIdle()

        // If the first two Jobs weren't cancelled, this would be 3.
        expireCount shouldBeEqualTo 1
    }

    @Test
    fun `state read at any point reflects whatever is currently active`() = runTest {
        val controller = buildController(this) {}

        controller.state.shouldBeInstanceOf<SleepTimerState.Off>()

        controller.setEndOfTrack()
        controller.state.shouldBeInstanceOf<SleepTimerState.EndOfTrack>()

        controller.setDuration(45)
        controller.state.shouldBeInstanceOf<SleepTimerState.Duration>()

        controller.cancel()
        controller.state.shouldBeInstanceOf<SleepTimerState.Off>()
    }

    // advanceUntilIdle()/advanceTimeBy() already run all due work; this alias just documents
    // intent at call sites where nothing new is expected to become due.
    private fun TestScope.runCurrentSafely() = advanceUntilIdle()
}
