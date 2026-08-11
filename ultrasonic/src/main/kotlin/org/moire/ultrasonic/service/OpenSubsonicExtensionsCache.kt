/*
 * OpenSubsonicExtensionsCache.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

/**
 * Remembers which OpenSubsonic extensions (see TAKI_CODE_OPTIMIZATION_PLAN.md Fase 4) the
 * current server supports, so a plain-Subsonic server -- which doesn't have the extensions
 * endpoint at all -- only gets probed once per [ttlMs] window instead of once per feature use
 * (e.g. every time the user opens a track's lyrics).
 *
 * Any probe failure (unreachable server, auth failure, malformed response, or a genuine "no
 * extensions") is memoized the same way, as an empty set: the caller only ever needs a yes/no
 * answer, and the alternative -- not caching failures -- would repeatedly re-probe a server that
 * will predictably keep failing, which is exactly the "requests destined to fail" this phase is
 * meant to eliminate. [check] is still expected to log the distinction for diagnostics.
 *
 * One instance lives inside RESTMusicService, which is itself recreated whenever the active
 * server changes (see MusicServiceFactory.resetMusicService), so a profile never leaks from one
 * server/URL to another; it just doesn't survive an app process restart, which only costs one
 * extra probe per server per session.
 */
internal class OpenSubsonicExtensionsCache(private val ttlMs: Long = DEFAULT_TTL_MS) {
    private var extensions: Set<String>? = null
    private var checkedAtMs: Long = 0

    @Synchronized
    fun supports(extension: String, nowMs: Long, check: () -> Set<String>): Boolean {
        val cached = extensions
        if (cached == null || nowMs - checkedAtMs >= ttlMs) {
            val result = check()
            extensions = result
            checkedAtMs = nowMs
            return extension in result
        }
        return extension in cached
    }

    companion object {
        const val DEFAULT_TTL_MS = 24L * 60 * 60 * 1000
    }
}
