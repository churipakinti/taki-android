/*
 * KeyedLock.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.util.concurrent.ConcurrentHashMap

/**
 * A lock per key, for single-flight coalescing of synchronous cache-or-fetch calls (see
 * TAKI_CODE_OPTIMIZATION_PLAN.md Fase 3). Two concurrent callers for the *same* key serialize:
 * the second one blocks until the first finishes, then (if [block] follows the usual
 * check-cache-first shape) sees the cache the first call just populated instead of also
 * hitting the network. Callers for *different* keys never block each other.
 *
 * Locks on distinct keys are never removed -- one per key value ever seen (e.g. one per album
 * id touched this process lifetime), which is bounded by how many distinct albums/artists/etc.
 * a session actually visits, not a concern in practice for a single running app process.
 */
internal class KeyedLock {
    private val locks = ConcurrentHashMap<String, Any>()

    fun <T> withLock(key: String, block: () -> T): T {
        val lock = locks.computeIfAbsent(key) { Any() }
        return synchronized(lock) { block() }
    }
}
