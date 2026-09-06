/*
 * PlaybackStallWatchdogTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.Test

/**
 * Unit coverage for the issue #17 FIX B escalation ladder. [PlaybackStallWatchdog] is a pure
 * state machine, so these drive it tick-by-tick with no Android/Media3 scaffolding.
 */
class PlaybackStallWatchdogTest {

    private val watchdog = PlaybackStallWatchdog()

    /** One checkpoint tick with sensible defaults (playing, mid-track, healthy read-ahead). */
    private fun PlaybackStallWatchdog.tick(
        positionMs: Long,
        playWhenReady: Boolean = true,
        readyOrBuffering: Boolean = true,
        bufferedPositionMs: Long = positionMs + 10_000,
        durationMs: Long? = 300_000
    ): StallAction = onTick(
        positionMs = positionMs,
        playWhenReady = playWhenReady,
        isReadyOrBuffering = readyOrBuffering,
        bufferedPositionMs = bufferedPositionMs,
        durationMs = durationMs
    )

    @Test
    fun `first tick only establishes a baseline`() {
        watchdog.tick(positionMs = 100_000) shouldBeEqualTo StallAction.None
    }

    @Test
    fun `steadily advancing playback never produces an action`() {
        var position = 0L
        repeat(50) {
            watchdog.tick(positionMs = position) shouldBeEqualTo StallAction.None
            position += 5_000
        }
    }

    @Test
    fun `frozen position with data buffered ahead recovers once then escalates`() {
        watchdog.tick(positionMs = 154_000) shouldBeEqualTo StallAction.None

        val recover = watchdog.tick(positionMs = 154_000)
        recover shouldBeInstanceOf StallAction.Recover::class
        (recover as StallAction.Recover).seekTargetMs shouldBeEqualTo 155_000L
        watchdog.isRecoveryPending shouldBeEqualTo true

        watchdog.tick(positionMs = 154_000) shouldBeEqualTo StallAction.Escalate
        watchdog.isRecoveryPending shouldBeEqualTo false
    }

    @Test
    fun `a recovery that works restarts the ladder instead of escalating`() {
        watchdog.tick(positionMs = 154_000)
        watchdog.tick(positionMs = 154_000) shouldBeInstanceOf StallAction.Recover::class

        // Nudge took effect: position is now moving again.
        watchdog.tick(positionMs = 160_000) shouldBeEqualTo StallAction.None
        watchdog.isRecoveryPending shouldBeEqualTo false

        // A fresh stall later gets its own recovery attempt, not an immediate escalation.
        watchdog.tick(positionMs = 160_000) shouldBeInstanceOf StallAction.Recover::class
    }

    @Test
    fun `frozen position without read-ahead is treated as network starvation and left alone`() {
        watchdog.tick(positionMs = 100_000, bufferedPositionMs = 100_400)
        repeat(20) {
            watchdog.tick(
                positionMs = 100_000,
                bufferedPositionMs = 100_400
            ) shouldBeEqualTo StallAction.None
        }
    }

    @Test
    fun `frozen position near the end of the track is not a stall`() {
        watchdog.tick(positionMs = 299_500, durationMs = 300_000) shouldBeEqualTo StallAction.None
        watchdog.tick(positionMs = 299_500, durationMs = 300_000) shouldBeEqualTo StallAction.None
    }

    @Test
    fun `stall is still detected when the duration is not known yet`() {
        watchdog.tick(positionMs = 154_000, durationMs = null)
        watchdog.tick(
            positionMs = 154_000,
            durationMs = null
        ) shouldBeInstanceOf StallAction.Recover::class
    }

    @Test
    fun `losing play intent resets the ladder`() {
        watchdog.tick(positionMs = 154_000)
        watchdog.tick(positionMs = 154_000) shouldBeInstanceOf StallAction.Recover::class

        watchdog.tick(positionMs = 154_000, playWhenReady = false) shouldBeEqualTo StallAction.None
        watchdog.isRecoveryPending shouldBeEqualTo false

        // Baseline is gone, so the next frozen run must recover first, not escalate.
        watchdog.tick(positionMs = 154_000) shouldBeEqualTo StallAction.None
        watchdog.tick(positionMs = 154_000) shouldBeInstanceOf StallAction.Recover::class
    }

    @Test
    fun `a non-playing state (paused) produces no action`() {
        repeat(2) {
            val action = watchdog.tick(positionMs = 50_000, readyOrBuffering = false)
            action shouldBeEqualTo StallAction.None
        }
    }

    @Test
    fun `explicit reset clears a pending recovery`() {
        watchdog.tick(positionMs = 154_000)
        watchdog.tick(positionMs = 154_000) shouldBeInstanceOf StallAction.Recover::class

        watchdog.reset()
        watchdog.isRecoveryPending shouldBeEqualTo false

        watchdog.tick(positionMs = 200_000) shouldBeEqualTo StallAction.None
        watchdog.tick(positionMs = 200_000) shouldBeInstanceOf StallAction.Recover::class
    }

    @Test
    fun `sub-threshold drift still counts as stalled`() {
        watchdog.tick(positionMs = 154_000)
        // Position crept 100 ms in 5 s - below STALL_MIN_ADVANCE_MS, so still a stall.
        watchdog.tick(positionMs = 154_100) shouldBeInstanceOf StallAction.Recover::class
    }
}
