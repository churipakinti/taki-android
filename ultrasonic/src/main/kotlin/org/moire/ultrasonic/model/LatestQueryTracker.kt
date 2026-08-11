/*
 * LatestQueryTracker.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

/**
 * Tracks which query is the most recently started one, so a caller can tell whether a response
 * it just received is still the one the user actually wants to see (see
 * TAKI_CODE_OPTIMIZATION_PLAN.md Fase 9: "un resultado viejo nunca reemplaza uno de una consulta
 * más reciente").
 *
 * Cancelling a coroutine job doesn't stop an in-flight blocking network call from completing --
 * only from resuming past its next suspension point -- so a slow response to an older query can
 * still finish after a newer one already did. [begin] is called synchronously right before that
 * network call starts, so whichever query started last always wins the [isCurrent] check,
 * independent of which response actually completes first.
 */
internal class LatestQueryTracker {
    private var latestQuery: String? = null

    fun begin(query: String) {
        latestQuery = query
    }

    fun isCurrent(query: String): Boolean = latestQuery == query
}
