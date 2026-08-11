/*
 * HomeShelvesFreshnessTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class HomeShelvesFreshnessTest {

    @Test
    fun `never loaded is not fresh`() {
        val freshness = HomeShelvesFreshness(ttlMs = 1000L)

        freshness.isFresh(nowMs = 0L, serverId = 1) shouldBeEqualTo false
    }

    @Test
    fun `is fresh right after loading, before the TTL elapses`() {
        val freshness = HomeShelvesFreshness(ttlMs = 1000L)

        freshness.markLoaded(nowMs = 0L, serverId = 1)

        freshness.isFresh(nowMs = 999L, serverId = 1) shouldBeEqualTo true
    }

    @Test
    fun `is not fresh once the TTL has elapsed`() {
        val freshness = HomeShelvesFreshness(ttlMs = 1000L)

        freshness.markLoaded(nowMs = 0L, serverId = 1)

        freshness.isFresh(nowMs = 1000L, serverId = 1) shouldBeEqualTo false
    }

    @Test
    fun `switching servers is never fresh, even inside the TTL window`() {
        val freshness = HomeShelvesFreshness(ttlMs = 1000L)

        freshness.markLoaded(nowMs = 0L, serverId = 1)

        // A different server id (including going offline, e.g. id -1) must never reuse
        // shelves that were loaded for a different server.
        freshness.isFresh(nowMs = 1L, serverId = 2) shouldBeEqualTo false
    }

    @Test
    fun `marking loaded again updates both the timestamp and the server id`() {
        val freshness = HomeShelvesFreshness(ttlMs = 1000L)

        freshness.markLoaded(nowMs = 0L, serverId = 1)
        freshness.markLoaded(nowMs = 5000L, serverId = 2)

        freshness.isFresh(nowMs = 5001L, serverId = 2) shouldBeEqualTo true
        freshness.isFresh(nowMs = 5001L, serverId = 1) shouldBeEqualTo false
    }
}
