/*
 * ShareCardRenderer.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import kotlin.math.max
import kotlin.math.min
import org.moire.ultrasonic.ui.theme.TakiColors

/**
 * The geometry and type scale of the share card (issue #20), all in pixels of the 1080x1920
 * canvas. One fixed design, no templates: a full-bleed artwork-derived atmosphere, a crisp square
 * cover, the title / artist / album, and a quiet Taki mark.
 *
 * Instagram Stories overlays roughly the top 250 px and bottom 320 px with its own UI, so
 * everything that matters stays inside [SAFE_TOP]..[CONTENT_BOTTOM] (a guideline, not a platform
 * guarantee).
 */
object ShareCardSpec {
    const val WIDTH = 1080
    const val HEIGHT = 1920

    const val SAFE_TOP = 250f

    /** Lowest y the cover + text block may reach; the mark sits below it, above the bottom UI. */
    const val CONTENT_BOTTOM = 1470f
    const val BRAND_TOP = 1520f

    const val ARTWORK_SIZE = 780f
    const val ARTWORK_CORNER = 24f
    const val GAP_ARTWORK_TEXT = 64f
    const val GAP_TITLE_ARTIST = 20f
    const val GAP_ARTIST_ALBUM = 12f

    const val TITLE_MAX_SIZE = 68f
    const val TITLE_MIN_SIZE = 46f
    const val TITLE_MAX_LINES = 2
    const val ARTIST_SIZE = 46f
    const val ARTIST_MAX_LINES = 2
    const val ALBUM_SIZE = 38f
    const val ALBUM_MAX_LINES = 1

    /** The background is built from a tiny copy of the artwork, which is what blurs it. */
    const val ATMOSPHERE_WIDTH = 54
    const val ATMOSPHERE_HEIGHT = 96
    const val ATMOSPHERE_BLUR_RADIUS = 4
    const val ATMOSPHERE_BLUR_PASSES = 3
    const val ATMOSPHERE_SATURATION = 1.3f
    const val SCRIM_ALPHA_TOP = 0.22f
    const val SCRIM_ALPHA_BOTTOM = 0.70f

    const val BRAND_SCALE = 0.26f
}

/** Where everything landed, in canvas pixels - what the layout tests assert on. */
data class ShareCardLayout(
    val artwork: RectF,
    val title: RectF,
    val artist: RectF?,
    val album: RectF?,
    val brand: RectF,
)

/**
 * Draws a [ShareCardContent] and optional artwork onto a real 1080x1920 bitmap. Pure Canvas: no
 * Compose, no screen density, no Views, so the result is the same on every device and in unit
 * tests. Allocates the output bitmap plus a few small (<= 270x480) working bitmaps.
 */
class ShareCardRenderer(private val colors: TakiColors = TakiColors()) {

    private val ivory = colors.ivory.toArgb()
    private val accent = colors.accent.toArgb()
    private val black = colors.black.toArgb()

    private class Composed(
        val layout: ShareCardLayout,
        val title: StaticLayout,
        val artist: StaticLayout?,
        val album: StaticLayout?,
    )

    /** Computes the layout without drawing (used by tests and by [render]). */
    fun layout(content: ShareCardContent): ShareCardLayout = compose(content).layout

    fun render(content: ShareCardContent, artwork: Bitmap?): Bitmap {
        val composed = compose(content)
        val output = createBitmap(ShareCardSpec.WIDTH, ShareCardSpec.HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        drawAtmosphere(canvas, artwork)
        drawArtwork(canvas, composed.layout.artwork, artwork)
        drawText(canvas, composed.title, composed.layout.title, ivory, TITLE_ALPHA)
        composed.artist?.let { drawText(canvas, it, composed.layout.artist!!, ivory, ARTIST_ALPHA) }
        composed.album?.let { drawText(canvas, it, composed.layout.album!!, ivory, ALBUM_ALPHA) }
        drawTakiMark(canvas, composed.layout.brand)
        return output
    }

    // region composition

    private fun compose(content: ShareCardContent): Composed {
        val textWidth = ShareCardSpec.ARTWORK_SIZE.toInt()
        val title = fitTitle(content.title, textWidth)
        val artist = content.artist?.let {
            buildLayout(it, ShareCardSpec.ARTIST_SIZE, Typeface.create(FAMILY_REGULAR, Typeface.NORMAL), textWidth, ShareCardSpec.ARTIST_MAX_LINES)
        }
        val album = content.album?.let {
            buildLayout(it, ShareCardSpec.ALBUM_SIZE, Typeface.create(FAMILY_REGULAR, Typeface.NORMAL), textWidth, ShareCardSpec.ALBUM_MAX_LINES)
        }

        var textHeight = title.height.toFloat()
        if (artist != null) textHeight += ShareCardSpec.GAP_TITLE_ARTIST + artist.height
        if (album != null) textHeight += ShareCardSpec.GAP_ARTIST_ALBUM + album.height
        val blockHeight = ShareCardSpec.ARTWORK_SIZE + ShareCardSpec.GAP_ARTWORK_TEXT + textHeight

        // Centre the cover + text block in the safe zone; never above SAFE_TOP.
        val available = ShareCardSpec.CONTENT_BOTTOM - ShareCardSpec.SAFE_TOP
        val top = ShareCardSpec.SAFE_TOP + max(0f, (available - blockHeight) / 2f)
        val left = (ShareCardSpec.WIDTH - ShareCardSpec.ARTWORK_SIZE) / 2f

        val artworkRect = RectF(left, top, left + ShareCardSpec.ARTWORK_SIZE, top + ShareCardSpec.ARTWORK_SIZE)
        var y = artworkRect.bottom + ShareCardSpec.GAP_ARTWORK_TEXT
        val titleRect = textRect(left, y, title)
        y = titleRect.bottom
        var artistRect: RectF? = null
        if (artist != null) {
            y += ShareCardSpec.GAP_TITLE_ARTIST
            artistRect = textRect(left, y, artist)
            y = artistRect.bottom
        }
        var albumRect: RectF? = null
        if (album != null) {
            y += ShareCardSpec.GAP_ARTIST_ALBUM
            albumRect = textRect(left, y, album)
        }
        return Composed(
            ShareCardLayout(artworkRect, titleRect, artistRect, albumRect, brandRect()),
            title,
            artist,
            album,
        )
    }

    private fun textRect(left: Float, top: Float, layout: StaticLayout) =
        RectF(left, top, left + layout.width, top + layout.height)

    /** Largest title size that fits [ShareCardSpec.TITLE_MAX_LINES] lines; below the minimum it
     *  keeps the minimum size and ellipsizes instead of overflowing. */
    private fun fitTitle(text: String, width: Int): StaticLayout {
        val typeface = Typeface.create(FAMILY_MEDIUM, Typeface.NORMAL)
        var size = ShareCardSpec.TITLE_MAX_SIZE
        while (size > ShareCardSpec.TITLE_MIN_SIZE) {
            val candidate = buildLayout(text, size, typeface, width, Int.MAX_VALUE)
            if (candidate.lineCount <= ShareCardSpec.TITLE_MAX_LINES) return candidate
            size -= TITLE_SIZE_STEP
        }
        return buildLayout(text, ShareCardSpec.TITLE_MIN_SIZE, typeface, width, ShareCardSpec.TITLE_MAX_LINES)
    }

    // LineBreaker's constant is API 29, but it is a compile-time int equal to the Layout one that
    // StaticLayout.Builder has accepted since API 23, so it is safe on the app's minSdk.
    @SuppressLint("InlinedApi")
    private fun buildLayout(text: String, size: Float, typeface: Typeface, width: Int, maxLines: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = size
            this.typeface = typeface
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    // endregion

    // region drawing

    private fun drawAtmosphere(canvas: Canvas, artwork: Bitmap?) {
        val w = ShareCardSpec.WIDTH.toFloat()
        val h = ShareCardSpec.HEIGHT.toFloat()
        if (artwork != null) {
            val small = atmosphereBitmap(artwork)
            boxBlur(small, ShareCardSpec.ATMOSPHERE_BLUR_RADIUS, ShareCardSpec.ATMOSPHERE_BLUR_PASSES)
            // Doubling the blurred copy a few times with filtering keeps the gradient smooth;
            // one big jump from a tiny bitmap would show its pixel grid.
            var smooth = small
            repeat(UPSCALE_STEPS) {
                val next = smooth.scale(smooth.width * 2, smooth.height * 2, true)
                if (smooth !== next) smooth.recycle()
                smooth = next
            }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply { setSaturation(ShareCardSpec.ATMOSPHERE_SATURATION) },
                )
            }
            canvas.drawBitmap(smooth, null, RectF(0f, 0f, w, h), paint)
            smooth.recycle()
        } else {
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(colors.selectedContainer.toArgb(), black),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        // A scrim keeps the text readable on light artwork without flattening the colours.
        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(
                    withAlpha(black, ShareCardSpec.SCRIM_ALPHA_TOP),
                    withAlpha(black, SCRIM_ALPHA_MID),
                    withAlpha(black, ShareCardSpec.SCRIM_ALPHA_BOTTOM),
                ),
                floatArrayOf(0f, SCRIM_MID_STOP, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, scrim)
    }

    /** In-place separable box blur of a small [bitmap], [passes] times (three passes approximate a
     *  gaussian). Edge pixels are clamped, so the border never darkens. */
    private fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val scratch = IntArray(max(w, h))
        repeat(passes) {
            for (y in 0 until h) blurLine(pixels, y * w, 1, w, radius, scratch)
            for (x in 0 until w) blurLine(pixels, x, w, h, radius, scratch)
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun blurLine(pixels: IntArray, start: Int, stride: Int, length: Int, radius: Int, scratch: IntArray) {
        val window = 2 * radius + 1
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val p = pixels[start + min(length - 1, max(0, i)) * stride]
            a += Color.alpha(p); r += Color.red(p); g += Color.green(p); b += Color.blue(p)
        }
        for (i in 0 until length) {
            scratch[i] = Color.argb(a / window, r / window, g / window, b / window)
            val out = pixels[start + min(length - 1, max(0, i - radius)) * stride]
            val inn = pixels[start + min(length - 1, i + radius + 1) * stride]
            a += Color.alpha(inn) - Color.alpha(out)
            r += Color.red(inn) - Color.red(out)
            g += Color.green(inn) - Color.green(out)
            b += Color.blue(inn) - Color.blue(out)
        }
        for (i in 0 until length) pixels[start + i * stride] = scratch[i]
    }

    /** A tiny portrait crop of [artwork], reduced by repeated halving (a true box filter, so no
     *  aliasing) - upscaling this later is what produces the soft, colour-true blur. */
    private fun atmosphereBitmap(artwork: Bitmap): Bitmap {
        val aspect = ShareCardSpec.ATMOSPHERE_WIDTH.toFloat() / ShareCardSpec.ATMOSPHERE_HEIGHT
        val src = centerCrop(artwork.width, artwork.height, aspect)
        val cropped = Bitmap.createBitmap(artwork, src.left, src.top, src.width(), src.height())
        var current = cropped
        while (current.width / 2 >= ShareCardSpec.ATMOSPHERE_WIDTH && current.height / 2 >= ShareCardSpec.ATMOSPHERE_HEIGHT) {
            val next = current.scale(current.width / 2, current.height / 2, true)
            if (current !== cropped && current !== artwork) current.recycle()
            current = next
        }
        val result = current.scale(ShareCardSpec.ATMOSPHERE_WIDTH, ShareCardSpec.ATMOSPHERE_HEIGHT, true)
        if (current !== result && current !== cropped && current !== artwork) current.recycle()
        if (cropped !== artwork && cropped !== result) cropped.recycle()
        return result
    }

    private fun drawArtwork(canvas: Canvas, rect: RectF, artwork: Bitmap?) {
        val radius = ShareCardSpec.ARTWORK_CORNER
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.withClip(clip) {
            // An opaque base so transparent artwork never lets the atmosphere show through the cover.
            this.drawRect(rect, Paint().apply { color = colors.surfaceHigh.toArgb() })
            if (artwork != null) {
                val src = centerCrop(artwork.width, artwork.height, 1f)
                this.drawBitmap(artwork, src, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            } else {
                val markHeight = rect.height() * FALLBACK_MARK_FRACTION
                drawTakiSymbol(
                    this,
                    rect.centerX(),
                    rect.centerY(),
                    markHeight / SYMBOL_HEIGHT,
                    withAlpha(accent, FALLBACK_MARK_ALPHA),
                )
            }
        }
        // A hairline edge keeps a dark cover legible against a dark atmosphere.
        canvas.drawPath(
            clip,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = EDGE_WIDTH
                color = withAlpha(ivory, EDGE_ALPHA)
            },
        )
    }

    private fun drawText(canvas: Canvas, layout: StaticLayout, rect: RectF, color: Int, alpha: Float) {
        layout.paint.color = withAlpha(color, alpha)
        canvas.withTranslation(rect.left, rect.top) { layout.draw(this) }
    }

    // endregion

    // region Taki mark

    private fun markTextPaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(FAMILY_MEDIUM, Typeface.NORMAL)
        textSize = LOGO_TEXT_SIZE * ShareCardSpec.BRAND_SCALE
        letterSpacing = LOGO_LETTER_SPACING
        color = withAlpha(ivory, MARK_ALPHA)
    }

    private fun brandRect(): RectF {
        val scale = ShareCardSpec.BRAND_SCALE
        val textWidth = markTextPaint().measureText(LOGO_WORD)
        val width = max(LOGO_SYMBOL_RIGHT * scale, LOGO_TEXT_X * scale + textWidth) - LOGO_LEFT * scale
        val height = (LOGO_BOTTOM - LOGO_TOP) * scale
        val left = (ShareCardSpec.WIDTH - width) / 2f
        return RectF(left, ShareCardSpec.BRAND_TOP, left + width, ShareCardSpec.BRAND_TOP + height)
    }

    /** The existing primary logo (docs/graphic resources/Taki_Logo_Primary.svg), reproduced at a
     *  small scale: the accent "t" symbol with "aki" tucked under its arm. Not a new mark. */
    private fun drawTakiMark(canvas: Canvas, rect: RectF) {
        val scale = ShareCardSpec.BRAND_SCALE
        canvas.withTranslation(rect.left - LOGO_LEFT * scale, rect.top - LOGO_TOP * scale) {
            scale(scale, scale)
            drawSymbolShapes(this, withAlpha(accent, MARK_ALPHA))
        }
        canvas.drawText(
            LOGO_WORD,
            rect.left + (LOGO_TEXT_X - LOGO_LEFT) * scale,
            rect.top + (LOGO_TEXT_BASELINE - LOGO_TOP) * scale,
            markTextPaint(),
        )
    }

    /** Draws just the "t" symbol, centred on ([cx], [cy]) - the missing-artwork tile. */
    private fun drawTakiSymbol(canvas: Canvas, cx: Float, cy: Float, scale: Float, color: Int) {
        canvas.withTranslation(
            cx - ((LOGO_LEFT + LOGO_SYMBOL_RIGHT) / 2f) * scale,
            cy - ((LOGO_TOP + LOGO_BOTTOM) / 2f) * scale,
        ) {
            scale(scale, scale)
            drawSymbolShapes(this, color)
        }
    }

    /** The three shapes are united into one path first, so a translucent colour does not
     *  double-blend where they overlap. */
    private fun drawSymbolShapes(canvas: Canvas, color: Int) {
        val shape = Path().apply {
            addOval(RectF(118f - 48f, 246f - 40f, 118f + 48f, 246f + 40f), Path.Direction.CW)
        }
        val stem = Path().apply { addRect(118f, 48f, 118f + 46f, 48f + 200f, Path.Direction.CW) }
        val bar = Path().apply { addRect(78f, 106f, 78f + 232f, 106f + 42f, Path.Direction.CW) }
        shape.op(stem, Path.Op.UNION)
        shape.op(bar, Path.Op.UNION)
        canvas.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    // endregion

    private fun centerCrop(width: Int, height: Int, aspect: Float): Rect {
        val current = width.toFloat() / height
        return if (current > aspect) {
            val w = (height * aspect).toInt()
            val x = (width - w) / 2
            Rect(x, 0, x + w, height)
        } else {
            val h = (width / aspect).toInt()
            val y = (height - h) / 2
            Rect(0, y, width, y + h)
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((min(1f, max(0f, alpha)) * MAX_BYTE).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val FAMILY_MEDIUM = "sans-serif-medium"
        const val FAMILY_REGULAR = "sans-serif"
        const val TITLE_SIZE_STEP = 4f
        const val TITLE_ALPHA = 1f
        const val ARTIST_ALPHA = 0.86f
        const val ALBUM_ALPHA = 0.66f
        const val UPSCALE_STEPS = 3
        const val SCRIM_ALPHA_MID = 0.42f
        const val SCRIM_MID_STOP = 0.55f
        const val EDGE_WIDTH = 2f
        const val EDGE_ALPHA = 0.10f
        const val FALLBACK_MARK_FRACTION = 0.42f
        const val FALLBACK_MARK_ALPHA = 0.32f
        const val MARK_ALPHA = 0.92f
        const val MAX_BYTE = 255

        // Geometry of Taki_Logo_Primary.svg (a 900x320 viewBox), in its own units.
        const val LOGO_LEFT = 78f
        const val LOGO_TOP = 48f
        const val LOGO_BOTTOM = 286f
        const val LOGO_SYMBOL_RIGHT = 310f
        // Moved right of the SVG's 168 so the ascender of the k clears the end of the crossbar.
        const val LOGO_TEXT_X = 214f
        // A touch below the SVG's 258 so "aki" clears the arm of the t at this small size.
        const val LOGO_TEXT_BASELINE = 266f
        const val LOGO_TEXT_SIZE = 205f
        const val LOGO_LETTER_SPACING = -7f / 205f
        const val LOGO_WORD = "aki"
        const val SYMBOL_HEIGHT = LOGO_BOTTOM - LOGO_TOP
    }
}
