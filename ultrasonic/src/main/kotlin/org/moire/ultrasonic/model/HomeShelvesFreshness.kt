/*
 * HomeShelvesFreshness.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

/**
 * Tracks whether Home's album shelves were loaded recently enough (for the same server) to
 * skip a reload. getAlbumList()/getAlbumList2() have no caching of their own in
 * CachedMusicService, so without this, Home re-fetched all 6 shelves from the network every
 * time its view was recreated -- which happens far more often than the underlying ViewModel,
 * e.g. just switching bottom-nav tabs and back.
 */
internal class HomeShelvesFreshness(private val ttlMs: Long) {
    private var lastLoadedAt: Long? = null
    private var lastLoadedServerId: Int? = null

    /**
     * True if the shelves were last loaded for [serverId] less than [ttlMs] ago (relative to
     * [nowMs]). Switching servers/offline always counts as not fresh, even inside the TTL
     * window, so shelves from a different server are never shown stale.
     */
    fun isFresh(nowMs: Long, serverId: Int): Boolean {
        val loadedAt = lastLoadedAt ?: return false
        return lastLoadedServerId == serverId && nowMs - loadedAt < ttlMs
    }

    fun markLoaded(nowMs: Long, serverId: Int) {
        lastLoadedAt = nowMs
        lastLoadedServerId = serverId
    }
}
