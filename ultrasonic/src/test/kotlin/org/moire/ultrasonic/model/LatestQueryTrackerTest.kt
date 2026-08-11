/*
 * LatestQueryTrackerTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class LatestQueryTrackerTest {

    @Test
    fun `a query is current right after it begins`() {
        val tracker = LatestQueryTracker()

        tracker.begin("abc")

        tracker.isCurrent("abc") shouldBeEqualTo true
    }

    @Test
    fun `nothing is current before any query begins`() {
        val tracker = LatestQueryTracker()

        tracker.isCurrent("abc") shouldBeEqualTo false
    }

    @Test
    fun `an older query is no longer current once a newer one begins`() {
        val tracker = LatestQueryTracker()

        tracker.begin("ab")
        tracker.begin("abc")

        tracker.isCurrent("ab") shouldBeEqualTo false
        tracker.isCurrent("abc") shouldBeEqualTo true
    }

    @Test
    fun `a slow older response landing after a faster newer one is rejected`() {
        // Mirrors SearchListModel.search(): begin() is called synchronously for each query as
        // it starts, in the order the user typed them, regardless of which network response
        // actually comes back first.
        val tracker = LatestQueryTracker()

        tracker.begin("q1")
        tracker.begin("q2")

        // q2's response lands first (it was faster this time).
        tracker.isCurrent("q2") shouldBeEqualTo true
        // q1's response lands after, even though q1 started first -- must still be rejected.
        tracker.isCurrent("q1") shouldBeEqualTo false
    }

    @Test
    fun `re-running the same query is still current`() {
        val tracker = LatestQueryTracker()

        tracker.begin("abc")
        tracker.begin("abc")

        tracker.isCurrent("abc") shouldBeEqualTo true
    }
}
