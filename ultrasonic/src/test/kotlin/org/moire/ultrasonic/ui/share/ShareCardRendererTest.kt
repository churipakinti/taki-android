/*
 * ShareCardRendererTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Issue #20: the card is a real 1080x1920 bitmap, composed inside the Stories-safe zone, whatever
 * the metadata or artwork. Geometry is asserted through [ShareCardRenderer.layout]; a few pixel
 * checks prove the artwork is drawn sharp, cropped (never stretched) and the fallback is finished.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardRendererTest {

    private val renderer = ShareCardRenderer()
    private val short = ShareCardContent("Kashmir", "Led Zeppelin", "Physical Graffiti")
    private val long = ShareCardContent(
        title = "An Extraordinarily Long Track Title That Keeps Going And Going Across Many Many Lines " +
            "Of Text (Remastered Deluxe Anniversary Edition) [Live At The Royal Albert Hall]",
        artist = "The Very Long Named Artist Featuring Another Quite Long Named Collaborator & Friends",
        album = "A Similarly Overlong Album Title That Would Never Fit On A Single Line Of The Card",
    )

    private fun solid(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    @Test
    fun `output is exactly 1080 by 1920 and fully opaque`() {
        val card = renderer.render(short, solid(600, 600, Color.rgb(200, 40, 40)))
        assertEquals(1080, card.width)
        assertEquals(1920, card.height)
        assertEquals(Bitmap.Config.ARGB_8888, card.config)
        for ((x, y) in listOf(0 to 0, 1079 to 0, 0 to 1919, 1079 to 1919, 540 to 960)) {
            assertEquals("alpha at $x,$y", 255, Color.alpha(card.getPixel(x, y)))
        }
    }

    @Test
    fun `artwork is drawn sharp at the centre of its tile`() {
        val card = renderer.render(short, solid(900, 900, Color.rgb(10, 200, 30)))
        val tile = renderer.layout(short).artwork
        val pixel = card.getPixel(tile.centerX().toInt(), tile.centerY().toInt())
        assertEquals(Color.rgb(10, 200, 30), pixel)
    }

    @Test
    fun `rectangular artwork is cropped not stretched`() {
        // 400x200: left half red, right half blue. A centre crop to a square keeps x 100..300, so
        // the tile is red on its left half and blue on its right half, not a squashed full image.
        val wide = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(wide)
        canvas.drawRect(0f, 0f, 200f, 200f, Paint().apply { color = Color.RED })
        canvas.drawRect(200f, 0f, 400f, 200f, Paint().apply { color = Color.BLUE })
        val card = renderer.render(short, wide)
        val tile = renderer.layout(short).artwork
        val y = tile.centerY().toInt()
        assertEquals(Color.RED, card.getPixel((tile.left + 40).toInt(), y))
        assertEquals(Color.BLUE, card.getPixel((tile.right - 40).toInt(), y))
    }

    @Test
    fun `transparent artwork never shows the atmosphere through the cover`() {
        val transparent = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val card = renderer.render(short, transparent)
        val tile = renderer.layout(short).artwork
        val pixel = card.getPixel((tile.left + 60).toInt(), (tile.top + 60).toInt())
        assertEquals(255, Color.alpha(pixel))
        // surfaceHigh (0xFF1D211B): a dark neutral, not whatever colour the background happens to be.
        assertTrue(Color.red(pixel) in 0x15..0x25 && Color.green(pixel) in 0x19..0x29)
    }

    @Test
    fun `missing artwork renders a finished fallback with the same layout`() {
        val card = renderer.render(short, null)
        assertEquals(1080, card.width)
        val tile = renderer.layout(short).artwork
        // The fallback tile is the dark surface with the accent "t" symbol, not an empty hole.
        val tilePixel = card.getPixel((tile.left + 40).toInt(), (tile.top + 40).toInt())
        assertEquals(255, Color.alpha(tilePixel))
        val symbol = card.getPixel(tile.centerX().toInt(), (tile.centerY() - 0.07f * tile.height()).toInt())
        assertTrue("symbol is tinted by the accent", Color.green(symbol) > Color.green(tilePixel) + 10)
    }

    @Test
    fun `long metadata stays inside the safe zone without overlap`() {
        val layout = renderer.layout(long)
        assertBounded(layout)
        val title = layout.title
        val artist = layout.artist!!
        val album = layout.album!!
        assertTrue(title.bottom <= artist.top)
        assertTrue(artist.bottom <= album.top)
        assertTrue(layout.artwork.bottom <= title.top)
        assertTrue(album.bottom <= layout.brand.top)
    }

    @Test
    fun `short metadata and missing optional lines are bounded too`() {
        assertBounded(renderer.layout(short))
        val bare = renderer.layout(ShareCardContent("Song", null, null))
        assertBounded(bare)
        assertNull(bare.artist)
        assertNull(bare.album)
    }

    @Test
    fun `a single unbroken very long word does not overflow the card`() {
        val layout = renderer.layout(ShareCardContent("W".repeat(120), "A".repeat(90), "B".repeat(90)))
        assertBounded(layout)
        assertTrue(layout.title.width() <= ShareCardSpec.ARTWORK_SIZE + 0.5f)
    }

    @Test
    fun `non latin scripts render inside the same bounds`() {
        for (content in listOf(
            ShareCardContent("日本語の非常に長いトラックタイトルがここに入ります", "アーティスト", "アルバム"),
            ShareCardContent("مرحبا بالعالم ".repeat(6), "فنان", "ألبوم"),
            ShareCardContent("Beyoncé – Déjà Vu ✨", "Sigur Rós", "Ágætis byrjun"),
        )) {
            assertBounded(renderer.layout(content))
            assertEquals(1920, renderer.render(content, null).height)
        }
    }

    @Test
    fun `the brand mark sits below the content and above the bottom stories ui`() {
        val brand = renderer.layout(short).brand
        assertTrue(brand.top >= ShareCardSpec.CONTENT_BOTTOM)
        assertTrue(brand.bottom <= ShareCardSpec.HEIGHT - 320f)
        assertEquals(540f, brand.centerX(), 1f)
    }

    @Test
    fun `rendering is deterministic for identical input`() {
        val artwork = solid(500, 500, Color.rgb(120, 60, 200))
        val a = renderer.render(short, artwork)
        val b = renderer.render(short, artwork)
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        assertArrayEquals(pa, pb)
    }

    @Test
    fun `the source artwork is left untouched`() {
        val artwork = solid(500, 500, Color.rgb(1, 2, 3))
        renderer.render(short, artwork)
        assertFalse(artwork.isRecycled)
        assertEquals(Color.rgb(1, 2, 3), artwork.getPixel(10, 10))
    }

    private fun assertBounded(layout: ShareCardLayout) {
        val rects = listOfNotNull(layout.artwork, layout.title, layout.artist, layout.album)
        for (r in rects) {
            assertTrue("$r above safe top", r.top >= ShareCardSpec.SAFE_TOP - 0.5f)
            assertTrue("$r below content bottom", r.bottom <= ShareCardSpec.CONTENT_BOTTOM + 0.5f)
            assertTrue("$r outside the canvas", r.left >= 0f && r.right <= ShareCardSpec.WIDTH)
        }
        val canvas = RectF(0f, 0f, ShareCardSpec.WIDTH.toFloat(), ShareCardSpec.HEIGHT.toFloat())
        assertTrue(canvas.contains(layout.brand))
    }
}
