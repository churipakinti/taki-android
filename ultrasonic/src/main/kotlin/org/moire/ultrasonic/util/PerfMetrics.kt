/*
 * PerfMetrics.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.os.SystemClock
import timber.log.Timber

/**
 * Baseline performance instrumentation (Fase 0 of the optimization plan). Logs timestamped
 * marks and spans via Timber so p50/p95 can be computed from logcat across repeated manual
 * runs. Never sends data off the device.
 *
 * Safe by construction, not just by convention: `Timber.plant()` is only ever called for debug
 * builds (see `UApp.onCreate`), so every call in this object is a silent no-op in `release` --
 * there is no separately maintained "is this enabled" flag to get out of sync. Do not log
 * request/response bodies, credentials, tokens, or full server URLs here.
 */
object PerfMetrics {
    private const val TAG = "PerfMetrics"

    /**
     * Records a single instantaneous event, e.g. a screen becoming visible.
     */
    fun mark(event: String) {
        Timber.tag(TAG).d("MARK %s @ %d", event, SystemClock.elapsedRealtime())
    }

    /**
     * Starts a named span. Pair with [end] using the returned token to log its duration.
     * Safe to call across suspend boundaries (a plain timestamp, no coroutine/thread affinity).
     */
    fun start(label: String): Long {
        Timber.tag(TAG).d("START %s", label)
        return SystemClock.elapsedRealtime()
    }

    /**
     * Ends a span started with [start] and logs its elapsed duration in milliseconds.
     */
    fun end(label: String, startToken: Long) {
        val elapsedMs = SystemClock.elapsedRealtime() - startToken
        Timber.tag(TAG).d("END %s = %dms", label, elapsedMs)
    }

    /**
     * Measures a synchronous block inline.
     */
    inline fun <T> trace(label: String, block: () -> T): T {
        val token = start(label)
        val result = block()
        end(label, token)
        return result
    }
}
