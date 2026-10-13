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
    fun `the signature is a footer anchored to the bottom edge`() {
        val brand = renderer.layout(short).brand
        assertEquals(ShareCardSpec.HEIGHT - ShareCardSpec.BRAND_BOTTOM_MARGIN, brand.bottom, 0.5f)
        assertEquals(124f, ShareCardSpec.BRAND_BOTTOM_MARGIN, 0f)
        assertEquals(1740f, brand.top, 0.5f)
        assertEquals(540f, brand.centerX(), 1f)
        // Fully inside the exported image, with a deliberate margin (not hugging the edge).
        assertTrue(brand.left >= 0f && brand.right <= ShareCardSpec.WIDTH)
        assertTrue(brand.bottom <= ShareCardSpec.HEIGHT - 100f)
    }

    @Test
    fun `the footer is clearly detached from the music block`() {
        for (content in listOf(short, long)) {
            val layout = renderer.layout(content)
            val lowest = listOfNotNull(layout.title, layout.artist, layout.album).maxOf { it.bottom }
            assertTrue("gap ${layout.brand.top - lowest}", layout.brand.top - lowest >= 200f)
        }
    }

    @Test
    fun `the music block did not move`() {
        val layout = renderer.layout(short)
        // Unchanged geometry of the cover and text for the short sample (centred in 250..1470).
        assertEquals(333f, layout.artwork.top, 1f)
        assertEquals(1113f, layout.artwork.bottom, 1f)
        assertEquals(150f, layout.artwork.left, 0.5f)
        assertEquals(1113f + ShareCardSpec.GAP_ARTWORK_TEXT, layout.title.top, 0.5f)
    }

    @Test
    fun `the signature is the symbol only and small`() {
        val brand = renderer.layout(short).brand
        assertEquals(ShareCardSpec.BRAND_SYMBOL_HEIGHT, brand.height(), 0.5f)
        // The "t" symbol is 232 x 238 units: roughly square, and nowhere near the width of the old
        // symbol + "aki" lockup (which was about 2.2x wider than tall).
        assertTrue("width ${brand.width()}", brand.width() < brand.height() * 1.1f)
        assertTrue(brand.height() in 50f..60f)
    }

    /** A flat mid-grey cover makes the atmosphere a plain vertical gradient, so the same row is the
     *  same colour everywhere and the signature can be isolated exactly. */
    private fun flatCard(): Pair<Bitmap, RectF> {
        val card = renderer.render(short, solid(600, 600, Color.rgb(90, 90, 90)))
        return card to renderer.layout(short).brand
    }

    private fun stemPixel(card: Bitmap, brand: RectF): Int {
        val scale = ShareCardSpec.BRAND_SYMBOL_HEIGHT / 238f
        // Middle of the stem, below the crossbar, above the foot (SVG units x 141, y 200).
        return card.getPixel((brand.left + (141f - 78f) * scale).toInt(), (brand.top + (200f - 48f) * scale).toInt())
    }

    private fun backgroundBeside(card: Bitmap, brand: RectF, dx: Float): Int =
        card.getPixel((brand.centerX() + dx).toInt(), (brand.top + (200f - 48f) * ShareCardSpec.BRAND_SYMBOL_HEIGHT / 238f).toInt())

    @Test
    fun `the signature is monochrome and neutral, not the accent green`() {
        val (card, brand) = flatCard()
        val mark = stemPixel(card, brand)
        // Ivory (F1F2ED) blended over a grey background stays neutral; the lime accent would give a
        // strongly green-over-red pixel.
        assertTrue("r=${Color.red(mark)} g=${Color.green(mark)}", Color.green(mark) - Color.red(mark) < 12)
        assertTrue(Math.abs(Color.red(mark) - Color.blue(mark)) < 14)
    }

    @Test
    fun `the signature is drawn at the specified low opacity`() {
        val (card, brand) = flatCard()
        val mark = stemPixel(card, brand)
        val background = backgroundBeside(card, brand, dx = 120f)
        val ivoryRed = 0xF1
        val observed = (Color.red(mark) - Color.red(background)).toFloat() / (ivoryRed - Color.red(background))
        assertEquals(ShareCardSpec.BRAND_ALPHA, observed, 0.06f)
    }

    @Test
    fun `there is no wordmark beside the symbol`() {
        val (card, brand) = flatCard()
        val y = (brand.top + 0.6f * brand.height()).toInt()
        val beside = card.getPixel((brand.right + 40f).toInt(), y)
        val reference = card.getPixel((brand.left - 40f).toInt(), y)
        assertEquals(Color.red(reference).toFloat(), Color.red(beside).toFloat(), 2f)
        assertEquals(Color.green(reference).toFloat(), Color.green(beside).toFloat(), 2f)
    }

    @Test
    fun `the missing artwork symbol keeps its own size and accent tint`() {
        val card = renderer.render(short, null)
        val tile = renderer.layout(short).artwork
        val symbol = card.getPixel(tile.centerX().toInt(), (tile.centerY() - 0.07f * tile.height()).toInt())
        val tileBase = card.getPixel((tile.left + 40).toInt(), (tile.top + 40).toInt())
        // Large (about 42 % of the tile) and still accent-tinted, unaffected by the signature style.
        assertTrue(Color.green(symbol) > Color.green(tileBase) + 10)
        // accent (B7D63C) at 32 % over the dark tile: green leads red by ~13, vs ~0 for a neutral mark.
        assertTrue(Color.green(symbol) - Color.red(symbol) > 8)
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
