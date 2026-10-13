/*
 * ShareCardContentTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Issue #20: what ends up printed on the card is cleaned, bounded metadata and nothing else. */
@RunWith(RobolectricTestRunner::class)
class ShareCardContentTest {

    @Test
    fun `title artist and album are carried through`() {
        val content = ShareCardContent.from("Kashmir", "Led Zeppelin", "Physical Graffiti")
        assertEquals(ShareCardContent("Kashmir", "Led Zeppelin", "Physical Graffiti"), content)
    }

    @Test
    fun `a track without a title cannot be shared`() {
        assertNull(ShareCardContent.from(null, "Artist", "Album"))
        assertNull(ShareCardContent.from("   ", "Artist", "Album"))
    }

    @Test
    fun `missing artist and album are left out instead of printed as placeholders`() {
        val content = ShareCardContent.from("Song", "", null)
        assertNotNull(content)
        assertNull(content!!.artist)
        assertNull(content.album)
    }

    @Test
    fun `an album that only repeats the title is dropped`() {
        assertNull(ShareCardContent.from("Cheese", "Stromae", "cheese")!!.album)
    }

    @Test
    fun `html entities and tags from the server are decoded`() {
        val content = ShareCardContent.from("Rock &amp; Roll", "<b>AC/DC</b>", "Let&#39;s Go")!!
        assertEquals("Rock & Roll", content.title)
        assertEquals("AC/DC", content.artist)
        assertEquals("Let's Go", content.album)
    }

    @Test
    fun `whitespace is collapsed and control characters removed`() {
        assertEquals("A B C", cleanShareText("  A \n\t B\u0000   C  "))
    }

    @Test
    fun `unicode and non latin scripts survive unchanged`() {
        assertEquals("Beyoncé – Déjà Vu", cleanShareText("Beyoncé – Déjà Vu"))
        assertEquals("日本語のタイトル", cleanShareText("日本語のタイトル"))
        assertEquals("مرحبا بالعالم", cleanShareText("مرحبا بالعالم"))
    }

    @Test
    fun `accessibility description names title and artist`() {
        assertEquals("Song - Artist", ShareCardContent("Song", "Artist", null).describe())
        assertEquals("Song", ShareCardContent("Song", null, null).describe())
    }
}
