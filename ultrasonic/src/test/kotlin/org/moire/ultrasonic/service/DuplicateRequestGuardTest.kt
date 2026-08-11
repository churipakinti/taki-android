/*
 * DuplicateRequestGuardTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class DuplicateRequestGuardTest {

    @Test
    fun `first request with a signature is allowed to proceed`() {
        val guard = DuplicateRequestGuard()

        guard.begin("a") shouldBeEqualTo true
    }

    @Test
    fun `identical signature is rejected while the first is still in flight`() {
        val guard = DuplicateRequestGuard()

        guard.begin("a") shouldBeEqualTo true
        // Simulates a rapid double-tap on the same track: same signature, first hasn't
        // called end() yet.
        guard.begin("a") shouldBeEqualTo false
    }

    @Test
    fun `a different signature is never blocked by a pending one`() {
        val guard = DuplicateRequestGuard()

        guard.begin("a") shouldBeEqualTo true
        guard.begin("b") shouldBeEqualTo true
    }

    @Test
    fun `signature is allowed again once the in-flight request ends`() {
        val guard = DuplicateRequestGuard()

        guard.begin("a") shouldBeEqualTo true
        guard.end("a")

        // A genuine later replay of the same track (not a duplicate tap) must not be
        // suppressed forever.
        guard.begin("a") shouldBeEqualTo true
    }

    @Test
    fun `ending a signature that is not currently pending is a no-op`() {
        val guard = DuplicateRequestGuard()

        // Nothing pending yet - must not throw, and must not affect a later request.
        guard.end("a")
        guard.begin("a") shouldBeEqualTo true

        // "b" was never begun, so ending it must not clear "a", which is genuinely pending.
        guard.end("b")
        guard.begin("a") shouldBeEqualTo false
    }
}
