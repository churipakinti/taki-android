/*
 * ShareCardRendererScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the exported 1080x1920 card itself (not the sheet preview), from a synthetic,
 * deterministic cover: a normal card, one with very long metadata and one without artwork.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardRendererScreenshotTest {

    private val renderer = ShareCardRenderer()

    private fun cover(): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 600f, 600f, Color.rgb(196, 82, 42), Color.rgb(34, 52, 110), Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawRect(0f, 0f, 600f, 600f, paint)
        return bitmap
    }

    @Test
    fun shareCardStandard() = renderer
        .render(ShareCardContent("Kashmir", "Led Zeppelin", "Physical Graffiti"), cover())
        .captureRoboImage("src/test/screenshots/share_card_standard.png")

    @Test
    fun shareCardLongMetadata() = renderer
        .render(
            ShareCardContent(
                "An Extraordinarily Long Track Title That Keeps Going And Going Across Many Lines (Remastered Deluxe Edition)",
                "The Very Long Named Artist Featuring Another Quite Long Named Collaborator & Friends",
                "A Similarly Overlong Album Title That Would Never Fit On A Single Line",
            ),
            cover(),
        )
        .captureRoboImage("src/test/screenshots/share_card_long_metadata.png")

    @Test
    fun shareCardNoArtworkNoAlbum() = renderer
        .render(ShareCardContent("Kashmir", "Led Zeppelin", null), null)
        .captureRoboImage("src/test/screenshots/share_card_no_artwork.png")
}
