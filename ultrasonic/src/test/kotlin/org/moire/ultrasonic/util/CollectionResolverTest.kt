/*
 * CollectionResolverTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.Test
import org.moire.ultrasonic.domain.Album

/**
 * Locks down CollectionResolver's core contract: metadata-only detection, stable ids,
 * numeric disc ordering, and safe degradation when position metadata is missing or
 * inconsistent.
 */
class CollectionResolverTest {

    @Test
    fun `album without grouping is not part of any collection`() {
        val albums = listOf(album(id = "a1", grouping = null))

        CollectionResolver.resolve(albums).shouldBeEmpty()
    }

    @Test
    fun `album with blank grouping is not part of any collection`() {
        val albums = listOf(album(id = "a1", grouping = "   "))

        CollectionResolver.resolve(albums).shouldBeEmpty()
    }

    @Test
    fun `three albums with same grouping become one collection with three members`() {
        val albums = listOf(
            album(id = "a1", grouping = "Bach 333", disc = 1),
            album(id = "a2", grouping = "Bach 333", disc = 2),
            album(id = "a3", grouping = "Bach 333", disc = 3)
        )

        val collections = CollectionResolver.resolve(albums)

        collections shouldHaveSize 1
        collections[0].title shouldBeEqualTo "Bach 333"
        collections[0].albumCount shouldBeEqualTo 3
    }

    @Test
    fun `different groupings become separate collections`() {
        val albums = listOf(
            album(id = "a1", grouping = "Bach 333", disc = 1),
            album(id = "b1", grouping = "Mozart 225", disc = 1)
        )

        val collections = CollectionResolver.resolve(albums)

        collections shouldHaveSize 2
        collections.map { it.title } shouldBeEqualTo listOf("Bach 333", "Mozart 225")
    }

    @Test
    fun `disc numbers 1, 10, 2 resolve to numeric order 1, 2, 10`() {
        val albums = listOf(
            album(id = "disc10", grouping = "Box", disc = 10),
            album(id = "disc1", grouping = "Box", disc = 1),
            album(id = "disc2", grouping = "Box", disc = 2)
        )

        val collection = CollectionResolver.resolve(albums).single()

        collection.albums.map { it.id } shouldBeEqualTo listOf("disc1", "disc2", "disc10")
    }

    @Test
    fun `missing discNumber falls back to a leading number in the title deterministically`() {
        val albums = listOf(
            album(id = "known", grouping = "Box", disc = 1),
            album(id = "fallback", grouping = "Box", disc = null, title = "Box 002")
        )

        val collection = CollectionResolver.resolve(albums).single()

        collection.albums.map { it.id } shouldBeEqualTo listOf("known", "fallback")
    }

    @Test
    fun `missing discNumber and no parseable title falls back to a stable trailing position`() {
        val albums = listOf(
            album(id = "known", grouping = "Box", disc = 1),
            album(id = "unknown-position", grouping = "Box", disc = null, title = "Untitled disc")
        )

        val collection = CollectionResolver.resolve(albums).single()

        // Unresolvable position sorts last, not first or randomly, and never crashes.
        collection.albums.map { it.id } shouldBeEqualTo listOf("known", "unknown-position")
    }

    @Test
    fun `zero discNumber is treated as missing, not as a valid position`() {
        val albums = listOf(
            album(id = "disc1", grouping = "Box", disc = 1),
            album(id = "disc-zero", grouping = "Box", disc = 0, title = "Box 003")
        )

        val collection = CollectionResolver.resolve(albums).single()

        collection.albums.map { it.id } shouldBeEqualTo listOf("disc1", "disc-zero")
    }

    @Test
    fun `collection id is stable and derived only from the grouping value`() {
        CollectionResolver.collectionId("Bach 333") shouldBeEqualTo
            CollectionResolver.collectionId("Bach 333")
        CollectionResolver.collectionId("Bach 333") shouldBeEqualTo "collection:bach-333"
    }

    @Test
    fun `collection id normalizes case and punctuation so near-duplicates still merge`() {
        CollectionResolver.collectionId("Bach 333") shouldBeEqualTo
            CollectionResolver.collectionId("  bach   333  ")
    }

    @Test
    fun `regression - a normal multidisc album without grouping never becomes a collection`() {
        // "The Wall Deluxe": DISCNUMBER=1 and DISCNUMBER=2 across tracks, but no GROUPING - a
        // multidisc album on its own must never be mistaken for a Collection (section 18).
        val albums = listOf(album(id = "wall-deluxe", grouping = null, disc = 1))

        CollectionResolver.resolve(albums).shouldBeEmpty()
    }

    @Test
    fun `no hardcoded collection names - any grouping value works the same way`() {
        val albums = listOf(
            album(id = "m1", grouping = "Mercury Living Presence", disc = 1),
            album(id = "m2", grouping = "Mercury Living Presence", disc = 2)
        )

        CollectionResolver.resolve(albums).single().title shouldBeEqualTo "Mercury Living Presence"
    }

    private fun album(
        id: String,
        grouping: String?,
        disc: Int? = null,
        title: String? = id
    ): Album = Album(id = id, title = title, discNumber = disc, grouping = grouping)
}
