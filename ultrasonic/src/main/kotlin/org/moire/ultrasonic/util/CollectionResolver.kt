/*
 * CollectionResolver.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import java.util.Locale
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.MusicCollection
import timber.log.Timber

/**
 * Groups [Album]s that share the same `grouping` metadata value into [MusicCollection]s.
 * Detection is entirely metadata-driven - never a hardcoded name/artist/id check - so any
 * box set works the same way Bach 333 does, without code changes.
 *
 * Pure function over already-fetched/cached albums (see `AlbumDao.withGrouping()`): this never
 * fetches anything itself, so it's safe to call on every Library load without re-triggering
 * network requests or loading track lists.
 */
object CollectionResolver {

    fun resolve(albums: List<Album>): List<MusicCollection> = albums
        .filter { !it.grouping.isNullOrBlank() }
        .groupBy { it.grouping!!.trim() }
        .map { (grouping, members) ->
            val sortedMembers = members.sortedWith(discOrderComparator)
            Timber.i(
                "CollectionResolver: resolved \"%s\" with %d albums",
                grouping,
                sortedMembers.size
            )
            MusicCollection(id = collectionId(grouping), title = grouping, albums = sortedMembers)
        }
        .sortedBy { it.title.lowercase(Locale.ROOT) }

    /**
     * Stable and derived only from the grouping value itself (never a UI index), so it survives
     * re-resolving across app restarts/library refreshes without needing its own persisted table.
     */
    fun collectionId(grouping: String): String {
        val normalized = grouping.trim().lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, "-")
            .trim('-')
        return "$ID_PREFIX$normalized"
    }

    // Numeric, never lexicographic (docs section 8: 1, 2, ..., 9, 10, ..., 222, not
    // 1, 10, 100, ..., 2, 20). Priority: discNumber, then a leading number pulled out of the
    // title as a fallback for albums missing it (section 20's "DISCNUMBER ausente" case).
    private val discOrderComparator = compareBy<Album>(::discPosition).thenBy { it.title.orEmpty() }

    private fun discPosition(album: Album): Int {
        val discNumber = album.discNumber
        if (discNumber != null && discNumber > 0) return discNumber

        val fallback = extractLeadingNumber(album.title)
        if (fallback == null) {
            Timber.d(
                "CollectionResolver: album %s missing collection position, using title fallback",
                album.id
            )
            return Int.MAX_VALUE
        }
        return fallback
    }

    private fun extractLeadingNumber(text: String?): Int? {
        if (text.isNullOrEmpty()) return null
        return LEADING_NUMBER.find(text)?.value?.toIntOrNull()
    }

    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val LEADING_NUMBER = Regex("\\d+")
    private const val ID_PREFIX = "collection:"
}
