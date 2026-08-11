/*
 * DuplicateRequestGuard.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

/**
 * Suppresses a request that exactly duplicates one already in flight, e.g. a rapid
 * double-tap on the same track re-triggering the same queue rebuild before the first
 * tap's request finished. Only one signature is tracked at a time: a second [begin] with
 * a different signature is never blocked by this guard, only an identical one is.
 */
internal class DuplicateRequestGuard {
    private var pendingSignature: String? = null

    /**
     * Returns true if [signature] may proceed (and is now considered in flight), or false
     * if an identical request is already in flight and this one should be dropped.
     */
    @Synchronized
    fun begin(signature: String): Boolean {
        if (signature == pendingSignature) return false
        pendingSignature = signature
        return true
    }

    /**
     * Marks [signature] as finished. Safe to call even if a different signature is now
     * pending (e.g. this one was itself dropped by [begin]).
     */
    @Synchronized
    fun end(signature: String) {
        if (pendingSignature == signature) pendingSignature = null
    }
}
