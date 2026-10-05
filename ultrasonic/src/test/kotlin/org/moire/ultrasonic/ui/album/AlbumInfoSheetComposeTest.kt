/*
 * AlbumInfoSheetComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.components.takiSheetScrimTestTag
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Album Information sheet (issue #10 phase 5A6): R2, replacing `AlbumInfoBottomSheetFragment`. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h900dp-xxhdpi")
class AlbumInfoSheetComposeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val full = AlbumInfoUiState(
        albumName = "Era Vulgaris",
        artist = "Queens of the Stone Age",
        year = "2007",
        songCount = 11,
        discCount = 1,
        description = "Album notes supplied by the music server.",
        artworkModel = null,
    )

    private fun show(info: AlbumInfoUiState = full, onDismiss: () -> Unit = {}) {
        compose.setContent {
            TakiTheme { AlbumInfoSheet(info, onDismiss, bottomContentInset = 0.dp) }
        }
    }

    @Test
    fun `shows every album field`() {
        show()
        compose.onNodeWithText("About this album").assertIsDisplayed()
        compose.onNodeWithTag(ALBUM_INFO_NAME_TEST_TAG).assertTextEquals("Era Vulgaris")
        compose.onNodeWithTag(ALBUM_INFO_ARTIST_TEST_TAG).assertTextEquals("Queens of the Stone Age")
        compose.onNodeWithTag(ALBUM_INFO_SECONDARY_TEST_TAG).assertTextEquals("2007 · 11 songs")
        compose.onNodeWithTag(ALBUM_INFO_DESCRIPTION_TEST_TAG)
            .assertTextEquals("Album notes supplied by the music server.")
    }

    @Test
    fun `the disc count only appears for multi-disc albums`() {
        show(full.copy(discCount = 3))
        compose.onNodeWithTag(ALBUM_INFO_SECONDARY_TEST_TAG).assertTextEquals("2007 · 11 songs · 3 discs")
    }

    @Test
    fun `optional metadata that is missing is simply not shown`() {
        show(full.copy(artist = null, year = null, songCount = 0))
        compose.onNodeWithTag(ALBUM_INFO_ARTIST_TEST_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ALBUM_INFO_SECONDARY_TEST_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ALBUM_INFO_NAME_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `an empty artist is hidden like a missing one`() {
        show(full.copy(artist = ""))
        compose.onNodeWithTag(ALBUM_INFO_ARTIST_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `a very long description scrolls inside a sheet capped to the host height`() {
        val long = (1..400).joinToString(" ") { "word$it" }
        compose.setContent {
            TakiTheme {
                Box(Modifier.fillMaxWidth().height(600.dp)) {
                    AlbumInfoSheet(full.copy(description = long), {}, bottomContentInset = 0.dp)
                }
            }
        }
        val sheet = compose.onNodeWithTag(ALBUM_INFO_SHEET_TEST_TAG).getBoundsInRoot()
        assertTrue("sheet must stay within the host: ${sheet.height}", sheet.height <= 600.dp)
        compose.onNodeWithTag(ALBUM_INFO_DESCRIPTION_TEST_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
            .performTouchInput { swipeUp() }
        compose.onNodeWithText("About this album").assertIsDisplayed()
        compose.onNodeWithTag(ALBUM_INFO_NAME_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `a very long album name is limited to two lines`() {
        show(full.copy(albumName = "A really long album title ".repeat(20)))
        compose.onNodeWithTag(ALBUM_INFO_NAME_TEST_TAG).assertIsDisplayed()
        assertTrue(compose.onNodeWithTag(ALBUM_INFO_NAME_TEST_TAG).getBoundsInRoot().height < 80.dp)
    }

    @Test
    fun `scrim and Back dismiss`() {
        var dismissed = 0
        show(onDismiss = { dismissed++ })
        compose.onNodeWithTag(takiSheetScrimTestTag(ALBUM_INFO_SHEET_TEST_TAG)).performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(2, dismissed)
    }
}
