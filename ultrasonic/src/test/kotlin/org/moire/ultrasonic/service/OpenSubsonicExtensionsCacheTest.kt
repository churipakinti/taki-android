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

    @Test
    fun `first call always probes`() {
        val cache = OpenSubsonicExtensionsCache(ttlMs = 1000L)
        var probeCount = 0

        val result = cache.supports("songLyrics", nowMs = 0L) {
            probeCount++
            setOf("songLyrics")
        }

        result shouldBeEqualTo true
        probeCount shouldBeEqualTo 1
    }

    @Test
    fun `within the TTL a second call reuses the cached result without probing again`() {
        val cache = OpenSubsonicExtensionsCache(ttlMs = 1000L)
        var probeCount = 0
        val probe = {
            probeCount++
            setOf("songLyrics")
        }

        cache.supports("songLyrics", nowMs = 0L, probe)
        val result = cache.supports("songLyrics", nowMs = 999L, probe)

        result shouldBeEqualTo true
        probeCount shouldBeEqualTo 1
    }

    @Test
    fun `once the TTL elapses the extension is probed again`() {
        val cache = OpenSubsonicExtensionsCache(ttlMs = 1000L)
        var probeCount = 0
        val probe = {
            probeCount++
            setOf("songLyrics")
        }

        cache.supports("songLyrics", nowMs = 0L, probe)
        cache.supports("songLyrics", nowMs = 1000L, probe)

        probeCount shouldBeEqualTo 2
    }

    @Test
    fun `an unsupported extension is remembered and not re-probed within the TTL`() {
        val cache = OpenSubsonicExtensionsCache(ttlMs = 1000L)
        var probeCount = 0
        val probe = {
            probeCount++
            emptySet<String>()
        }

        val first = cache.supports("songLyrics", nowMs = 0L, probe)
        val second = cache.supports("songLyrics", nowMs = 500L, probe)

        first shouldBeEqualTo false
        second shouldBeEqualTo false
        probeCount shouldBeEqualTo 1
    }

    @Test
    fun `a probe failure is treated like an empty extension set and still memoized`() {
        val cache = OpenSubsonicExtensionsCache(ttlMs = 1000L)
        var probeCount = 0
        // Mirrors RESTMusicService.fetchOpenSubsonicExtensions(), which never lets an
        // exception escape -- any failure (unreachable, auth, malformed response) becomes an
        // empty set so a plain-Subsonic server isn't probed again on every call.
        val probe = {
            probeCount++
            emptySet<String>()
        }

        cache.supports("songLyrics", nowMs = 0L, probe)
        cache.supports("songLyrics", nowMs = 1L, probe)
        cache.supports("songLyrics", nowMs = 2L, probe)

        probeCount shouldBeEqualTo 1
    }

    @Test
    fun `different extension names are checked against the same probed set`() {
        val cache = OpenSubsonicExtensionsCache(ttlMs = 1000L)
        var probeCount = 0
        val probe = {
            probeCount++
            setOf("songLyrics", "transcodeOffset")
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
