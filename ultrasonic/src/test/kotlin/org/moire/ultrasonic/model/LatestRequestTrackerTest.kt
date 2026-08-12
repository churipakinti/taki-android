/*
 * LatestRequestTrackerTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class LatestRequestTrackerTest {

    @Test
    fun `a request is current right after it begins`() {
        val tracker = LatestRequestTracker()

        val requestId = tracker.begin()

        tracker.isCurrent(requestId) shouldBeEqualTo true
    }

    @Test
    fun `an id that was never issued by begin is never current`() {
        val tracker = LatestRequestTracker()

        tracker.begin()

        tracker.isCurrent(-1L) shouldBeEqualTo false
    }

    @Test
    fun `a request is no longer current once another one begins`() {
        val tracker = LatestRequestTracker()

        val first = tracker.begin()
        val second = tracker.begin()

        tracker.isCurrent(first) shouldBeEqualTo false
        tracker.isCurrent(second) shouldBeEqualTo true
    }

    @Test
    fun `two requests get different ids even for the same logical query`() {
        // LatestRequestTracker itself has no notion of "query text" -- identity comes purely
        // from call order. This is what fixes the original bug: two searches for the exact same
        // text ("queen" typed, then retyped) used to compare equal under the old text-keyed
        // tracker, so whichever response landed last would always be treated as current even if
        // it was actually the older of the two.
        val tracker = LatestRequestTracker()

        val first = tracker.begin()
        val second = tracker.begin()

        (first == second) shouldBeEqualTo false
    }

    @Test
    fun `a faster second request finishing first is accepted as current`() {
        val tracker = LatestRequestTracker()

        tracker.begin()
        val second = tracker.begin()

        // second's response lands first (it was faster this time).
        tracker.isCurrent(second) shouldBeEqualTo true
    }

    @Test
    fun `a slower first request landing after a faster second one is rejected`() {
        val tracker = LatestRequestTracker()

        val first = tracker.begin()
        val second = tracker.begin()

        // second's response already landed and was accepted; first's response arrives after,
        // even though first started earlier -- must still be rejected.
        tracker.isCurrent(second)
        tracker.isCurrent(first) shouldBeEqualTo false
    }
}
