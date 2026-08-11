/*
 * PerfMetricsTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PerfMetricsTest {
    @Test
    fun `start returns a token and end does not throw`() {
        val token = PerfMetrics.start("test_span")
        PerfMetrics.end("test_span", token)
    }

    @Test
    fun `mark does not throw`() {
        PerfMetrics.mark("test_event")
    }

    @Test
    fun `trace returns the block result`() {
        val result = PerfMetrics.trace("test_trace") { 1 + 1 }
        assertTrue(result == 2)
    }
}
