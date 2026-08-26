/*
 * OpenSubsonicExtensionsCache.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

/**
 * Remembers which OpenSubsonic extensions the current server supports, so a plain-Subsonic
 * server -- which doesn't have the extensions
 * endpoint at all -- only gets probed once per TTL window instead of once per feature use (e.g.
 * every time the user opens a track's lyrics).
 *
 * The TTL depends on *why* the last probe ended up empty: a confirmed [ProbeResult.Success] --
 * even one that lists no extensions at all -- is memoized for [successTtlMs] (a server's feature
 * set doesn't change minute to minute). A [ProbeResult.Failure] -- the server was unreachable,
 * timed out, rejected the request, or returned something unparseable -- only proves "couldn't
 * tell this time", not "doesn't support it", so it's memoized for the much shorter
 * [failureTtlMs] instead. Caching every failure for as long as a real success (the original
 * design) meant a server restarting or a transient network blip while the user opened lyrics
 * could look identical to "confirmed no OpenSubsonic support" and silently disable synced
 * lyrics for the rest of the day, even after the server came back. [check] is still expected to
 * log which kind of failure happened, for diagnostics.
 *
 * One instance lives inside RESTMusicService, which is itself recreated whenever the active
 * server changes (see MusicServiceFactory.resetMusicService), so a profile never leaks from one
 * server/URL to another; it just doesn't survive an app process restart, which only costs one
 * extra probe per server per session.
 */
internal class OpenSubsonicExtensionsCache(
    private val successTtlMs: Long = DEFAULT_SUCCESS_TTL_MS,
    private val failureTtlMs: Long = DEFAULT_FAILURE_TTL_MS
) {
    sealed class ProbeResult {
        data class Success(val extensions: Set<String>) : ProbeResult()
        data object Failure : ProbeResult()
    }

    private var extensions: Set<String>? = null
    private var checkedAtMs: Long = 0
    private var cachedTtlMs: Long = 0

    @Synchronized
    fun supports(extension: String, nowMs: Long, check: () -> ProbeResult): Boolean {
        val cached = extensions
        if (cached == null || nowMs - checkedAtMs >= cachedTtlMs) {
            val result = check()
            val resolved = when (result) {
                is ProbeResult.Success -> result.extensions
                is ProbeResult.Failure -> emptySet()
            }
            extensions = resolved
            checkedAtMs = nowMs
            cachedTtlMs = if (result is ProbeResult.Success) successTtlMs else failureTtlMs
            return extension in resolved
        }
        return extension in cached
    }

    companion object {
        const val DEFAULT_SUCCESS_TTL_MS = 24L * 60 * 60 * 1000
        const val DEFAULT_FAILURE_TTL_MS = 2L * 60 * 1000
    }
}
