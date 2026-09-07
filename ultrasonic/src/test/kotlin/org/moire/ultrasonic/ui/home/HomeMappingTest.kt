/*
 * HomeMappingTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.robolectric.RobolectricTestRunner

/**
 * The pure domain -> Home UI-model mapping ([toHomeAlbumUi], [mixToFeaturedUi]).
 * Robolectric only because the artwork-key derivation reaches `FileUtil` / `UApp`.
 */
@RunWith(RobolectricTestRunner::class)
class HomeMappingTest {

    init {
        RobolectricUAppContext.install()
    }

    @Test
    fun `album fields map straight through`() {
        val ui = Album(
            id = "al-1",
            title = "Kind of Blue",
            artist = "Miles Davis",
            parent = "p-1",
        ).toHomeAlbumUi()

        assertEquals("al-1", ui.id)
        assertEquals("Kind of Blue", ui.title)
        assertEquals("Miles Davis", ui.subtitle)
        assertEquals("p-1", ui.parentId)
        assertTrue(ui.isDirectory) // Album is always a directory
    }

    @Test
    fun `null title and artist become empty strings, not the literal null`() {
        val ui = Album(id = "al-2").toHomeAlbumUi()
        assertEquals("", ui.title)
        assertEquals("", ui.subtitle)
    }

    @Test
    fun `an album with no cover art has no artwork model`() {
        assertNull(Album(id = "al-3", coverArt = null).toHomeAlbumUi().artworkModel)
        assertNull(Album(id = "al-4", coverArt = "").toHomeAlbumUi().artworkModel)
    }

    @Test
    fun `an album with cover art produces a CoverArtRequest keyed on that id`() {
        val model = Album(id = "al-5", coverArt = "cover-5", artist = "A", album = "B")
            .toHomeAlbumUi().artworkModel
        assertTrue(model is CoverArtRequest)
        assertEquals("cover-5", (model as CoverArtRequest).id)
    }

    @Test
    fun `an empty mix has no featured card`() {
        assertNull(mixToFeaturedUi(emptyList()))
    }

    @Test
    fun `a non-empty mix carries its track count and the first track's artwork`() {
        val mix = mixToFeaturedUi(
            listOf(Track(id = "t1", coverArt = null), Track(id = "t2"), Track(id = "t3")),
        )
        assertEquals(3, mix?.trackCount)
        assertNull(mix?.artworkModel) // first track has no cover
        assertFalse(mix == null)
    }
}
