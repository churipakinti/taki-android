/*
 * LatestRequestTracker.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

/**
 * Tracks which *execution* of a repeatable request (e.g. a search) is the most recent one, so a
 * caller can tell whether a response it just received is still the one the user actually wants
 * to see (see TAKI_CODE_OPTIMIZATION_PLAN.md Fase 9: "un resultado viejo nunca reemplaza uno de
 * una consulta más reciente").
 *
 * Superseded the query-text-keyed version of this class: comparing by the query string alone
 * correctly rejects a stale response for a *different* query (e.g. "queen" vs "queens"), but two
 * consecutive requests for the exact same text ("queen" typed, cleared, retyped, or a debounced
 * search racing a manual submit) shared one key -- if the first, slower one finished after the
 * second, faster one, both compared equal to "the latest query" and the older result could still
 * overwrite the newer one. Each call to [begin] now gets its own monotonic id, independent of
 * what the request is actually for, so identity never collides even when the text does.
 *
 * Cancelling a coroutine job doesn't stop an in-flight blocking network call from completing --
 * only from resuming past its next suspension point -- so a slow response to an older request
 * can still finish after a newer one already did. [begin] is called synchronously right before
 * that network call starts, so whichever request started last always wins the [isCurrent] check,
 * independent of which response actually completes first.
 */
internal class LatestRequestTracker {
    private var latestId = 0L

    @Synchronized
    fun begin(): Long = ++latestId

    @Synchronized
    fun isCurrent(requestId: Long): Boolean = requestId == latestId
}
