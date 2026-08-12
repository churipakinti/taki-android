/*
 * OpenSubsonicExtensionsCacheTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class OpenSubsonicExtensionsCacheTest {

    private val success = OpenSubsonicExtensionsCache.ProbeResult.Success(setOf("songLyrics"))
    private val successNoExtensions =
        OpenSubsonicExtensionsCache.ProbeResult.Success(emptySet())
    private val failure = OpenSubsonicExtensionsCache.ProbeResult.Failure

    @Test
    fun `first call always probes`() {
        val cache = OpenSubsonicExtensionsCache(successTtlMs = 1000L, failureTtlMs = 100L)
        var probeCount = 0

        val result = cache.supports("songLyrics", nowMs = 0L) {
            probeCount++
            success
        }

        result shouldBeEqualTo true
        probeCount shouldBeEqualTo 1
    }

    @Test
    fun `a confirmed success is not re-probed within the success TTL`() {
        val cache = OpenSubsonicExtensionsCache(successTtlMs = 1000L, failureTtlMs = 100L)
        var probeCount = 0
        val probe = {
            probeCount++
            success
        }

        cache.supports("songLyrics", nowMs = 0L, probe)
        val result = cache.supports("songLyrics", nowMs = 999L, probe)

        result shouldBeEqualTo true
        probeCount shouldBeEqualTo 1
    }

    @Test
    fun `once the success TTL elapses the extension is probed again`() {
        val cache = OpenSubsonicExtensionsCache(successTtlMs = 1000L, failureTtlMs = 100L)
        var probeCount = 0
        val probe = {
            probeCount++
            success
        }

        cache.supports("songLyrics", nowMs = 0L, probe)
        cache.supports("songLyrics", nowMs = 1000L, probe)

        probeCount shouldBeEqualTo 2
    }

    @Test
    fun `a confirmed absence is remembered and not re-probed within the success TTL`() {
        val cache = OpenSubsonicExtensionsCache(successTtlMs = 1000L, failureTtlMs = 100L)
        var probeCount = 0
        val probe = {
            probeCount++
            successNoExtensions
        }

        val first = cache.supports("songLyrics", nowMs = 0L, probe)
        val second = cache.supports("songLyrics", nowMs = 500L, probe)

        first shouldBeEqualTo false
        second shouldBeEqualTo false
        probeCount shouldBeEqualTo 1
    }

    @Test
    fun `a temporary failure falls back but is only memoized for the short failure TTL`() {
        val cache = OpenSubsonicExtensionsCache(successTtlMs = 1000L, failureTtlMs = 100L)
        var probeCount = 0
        val probe = {
            probeCount++
            failure
        }

        val duringFailureTtl = cache.supports("songLyrics", nowMs = 0L, probe)
        val stillWithinFailureTtl = cache.supports("songLyrics", nowMs = 99L, probe)

        duringFailureTtl shouldBeEqualTo false
        stillWithinFailureTtl shouldBeEqualTo false
        probeCount shouldBeEqualTo 1
    }

    @Test
    fun `a temporary failure is re-probed once its short TTL elapses`() {
        val cache = OpenSubsonicExtensionsCache(successTtlMs = 1000L, failureTtlMs = 100L)
        var probeCount = 0
        val probe = {
            probeCount++
            failure
        }

        cache.supports("songLyrics", nowMs = 0L, probe)
        cache.supports("songLyrics", nowMs = 100L, probe)

        probeCount shouldBeEqualTo 2
    }

    @Test
    fun `recovering after a temporary failure picks up the extension without an app restart`() {
        // The core bug this fixes: caching every failure as long as a real success meant a
        // server that was just restarting when the user opened lyrics could look identical to
        // "confirmed no OpenSubsonic support" for the rest of the day.
        val cache = OpenSubsonicExtensionsCache(successTtlMs = 1000L, failureTtlMs = 100L)
        var probeCount = 0

        val duringOutage = cache.supports("songLyrics", nowMs = 0L) {
            probeCount++
            failure
        }
        val afterRecovery = cache.supports("songLyrics", nowMs = 100L) {
            probeCount++
            success
        }

        duringOutage shouldBeEqualTo false
        afterRecovery shouldBeEqualTo true
        probeCount shouldBeEqualTo 2
    }

    @Test
    fun `different extension names are checked against the same probed set`() {
        val cache = OpenSubsonicExtensionsCache(successTtlMs = 1000L, failureTtlMs = 100L)
        var probeCount = 0
        val probe = {
            probeCount++
            OpenSubsonicExtensionsCache.ProbeResult.Success(setOf("songLyrics", "transcodeOffset"))
        }

        val lyricsSupported = cache.supports("songLyrics", nowMs = 0L, probe)
        val offsetSupported = cache.supports("transcodeOffset", nowMs = 1L, probe)
        val unknownSupported = cache.supports("formPost", nowMs = 2L, probe)

        lyricsSupported shouldBeEqualTo true
        offsetSupported shouldBeEqualTo true
        unknownSupported shouldBeEqualTo false
        probeCount shouldBeEqualTo 1
    }
}
