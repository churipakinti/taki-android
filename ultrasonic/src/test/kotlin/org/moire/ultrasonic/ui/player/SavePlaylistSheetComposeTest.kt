/*
 * SavePlaylistSheetComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SavePlaylistSheet] (issue #10 phase 4M3): the sheet's own visibility, the host-owned name
 * field, and that every action callback fires - the actual save/network call stays entirely in
 * `PlayerFragment`, so this only proves the presentation layer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class SavePlaylistSheetComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun setContent(visible: Boolean, name: String, actions: SavePlaylistActions) {
        compose.setContent {
            TakiTheme {
                Box(Modifier.fillMaxSize()) {
                    SavePlaylistSheet(visible = visible, name = name, actions = actions)
                }
            }
        }
    }

    @Test
    fun `hidden when not visible`() {
        setContent(visible = false, name = "My Mix", actions = SavePlaylistActions.Noop)
        compose.onNodeWithTag(SAVE_PLAYLIST_SHEET_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `shows the host-owned name in the field`() {
        setContent(visible = true, name = "2026-09-27", actions = SavePlaylistActions.Noop)
        compose.onNodeWithText("2026-09-27").assertIsDisplayed()
    }

    @Test
    fun `typing on an empty field calls onNameChange with the typed text`() {
        var changed: String? = null
        setContent(
            visible = true,
            name = "",
            actions = SavePlaylistActions.Noop.copy(onNameChange = { changed = it }),
        )
        compose.onNodeWithTag(SAVE_PLAYLIST_NAME_FIELD_TEST_TAG).performTextInput("Road Trip")
        assertEquals("Road Trip", changed)
    }

    @Test
    fun `tapping Save calls onSave`() {
        var saved = false
        setContent(
            visible = true,
            name = "My Mix",
            actions = SavePlaylistActions.Noop.copy(onSave = { saved = true }),
        )
        compose.onNodeWithText(string(R.string.common_save)).performClick()
        assertTrue(saved)
    }

    @Test
    fun `tapping Cancel calls onDismiss`() {
        var dismissed = false
        setContent(
            visible = true,
            name = "My Mix",
            actions = SavePlaylistActions.Noop.copy(onDismiss = { dismissed = true }),
        )
        compose.onNodeWithText(string(R.string.common_cancel)).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `tapping the scrim calls onDismiss`() {
        var dismissed = false
        setContent(
            visible = true,
            name = "My Mix",
            actions = SavePlaylistActions.Noop.copy(onDismiss = { dismissed = true }),
        )
        compose.onNodeWithTag(SAVE_PLAYLIST_SCRIM_TEST_TAG).performClick()
        assertTrue(dismissed)
    }
}
