/*
 * PlaybackStateWriteGateTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

/**
 * Unit coverage for the issue #17 serializer debounce. [PlaybackStateWriteGate] is pure and
 * takes an explicit clock, so these assert the gate decisions directly.
 *
 * Defaults under test: interval 3000 ms, delta 1000 ms.
 */
class PlaybackStateWriteGateTest {

    private val gate = PlaybackStateWriteGate()

    @Test
    fun `the first request always writes`() {
        gate.shouldWrite("queue-A|idx0", positionMs = 0, nowMs = 1_000) shouldBeEqualTo true
    }

    @Test
    fun `a position-only request inside the interval is dropped`() {
        gate.shouldWrite("queue-A|idx0", positionMs = 0, nowMs = 1_000) shouldBeEqualTo true

        // 2 s later, position moved 8 s: interval not yet elapsed -> still dropped.
        gate.shouldWrite("queue-A|idx0", positionMs = 8_000, nowMs = 3_000) shouldBeEqualTo false
    }

    @Test
    fun `a position-only request past the interval but barely moved is dropped`() {
        gate.shouldWrite("queue-A|idx0", positionMs = 0, nowMs = 1_000) shouldBeEqualTo true

        // 5 s later but only 200 ms of progress -> below the delta floor.
        gate.shouldWrite("queue-A|idx0", positionMs = 200, nowMs = 6_000) shouldBeEqualTo false
    }

    @Test
    fun `a position-only request past both thresholds writes`() {
        gate.shouldWrite("queue-A|idx0", positionMs = 0, nowMs = 1_000) shouldBeEqualTo true

        gate.shouldWrite("queue-A|idx0", positionMs = 5_000, nowMs = 6_000) shouldBeEqualTo true

        // ...and that write re-armed the clock/position: an immediate follow-up is dropped again.
        gate.shouldWrite("queue-A|idx0", positionMs = 5_500, nowMs = 6_500) shouldBeEqualTo false
    }

    @Test
    fun `threshold boundaries are inclusive`() {
        gate.shouldWrite("queue-A|idx0", positionMs = 0, nowMs = 0) shouldBeEqualTo true

        // exactly +3000 ms and +1000 ms
        gate.shouldWrite("queue-A|idx0", positionMs = 1_000, nowMs = 3_000) shouldBeEqualTo true
    }

    @Test
    fun `a structural change always writes even within the interval`() {
        gate.shouldWrite("queue-A|idx0", positionMs = 0, nowMs = 1_000) shouldBeEqualTo true

        // Next track: signature changed -> immediate, no debounce.
        gate.shouldWrite("queue-A|idx1", positionMs = 10, nowMs = 1_050) shouldBeEqualTo true
        // Queue edit: still immediate.
        gate.shouldWrite("queue-B|idx0", positionMs = 20, nowMs = 1_100) shouldBeEqualTo true
    }

    @Test
    fun `backward position movement counts toward the delta`() {
        gate.shouldWrite("queue-A|idx0", positionMs = 60_000, nowMs = 1_000) shouldBeEqualTo true

        // User scrubbed back 30 s after the interval elapsed: |delta| is large -> write.
        gate.shouldWrite("queue-A|idx0", positionMs = 30_000, nowMs = 5_000) shouldBeEqualTo true
    }

    @Test
    fun `reset makes the next request write again`() {
        gate.shouldWrite("queue-A|idx0", positionMs = 0, nowMs = 1_000) shouldBeEqualTo true
        gate.shouldWrite("queue-A|idx0", positionMs = 100, nowMs = 1_500) shouldBeEqualTo false

        gate.reset()

        gate.shouldWrite("queue-A|idx0", positionMs = 100, nowMs = 1_600) shouldBeEqualTo true
    }
}
