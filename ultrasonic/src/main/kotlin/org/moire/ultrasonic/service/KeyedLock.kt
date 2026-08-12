/*
 * KeyedLock.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A lock per key, for single-flight coalescing of cache-or-fetch calls (see
 * TAKI_CODE_OPTIMIZATION_PLAN.md Fase 3). Two concurrent callers for the *same* key serialize:
 * the second one suspends until the first finishes, then (if [block] follows the usual
 * check-cache-first shape) sees the cache the first call just populated instead of also
 * hitting the network. Callers for *different* keys never block each other.
 *
 * Uses [Mutex] rather than a JVM monitor (`synchronized`) because [block] can itself suspend
 * (Fase 7 of TAKI_CODE_OPTIMIZATION_PLAN.md): a coroutine can resume on a different thread than
 * it suspended on, which a monitor acquired via `synchronized` does not support safely across a
 * suspension point. `Mutex.withLock` is the suspend-safe equivalent with the exact same
 * single-flight-per-key semantics.
 *
 * Locks on distinct keys are never removed -- one per key value ever seen (e.g. one per album
 * id touched this process lifetime), which is bounded by how many distinct albums/artists/etc.
 * a session actually visits, not a concern in practice for a single running app process.
 */
internal class KeyedLock {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(key: String, block: suspend () -> T): T {
        val mutex = locks.computeIfAbsent(key) { Mutex() }
        return mutex.withLock { block() }
    }
}
