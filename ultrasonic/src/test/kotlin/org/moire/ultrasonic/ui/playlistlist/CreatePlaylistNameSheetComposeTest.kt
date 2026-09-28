/*
 * CreatePlaylistNameSheetComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

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
 * [CreatePlaylistNameSheet] (issue #10 phase 4M3): the sheet's own visibility, the host-owned
 * name/error state, and that every action callback fires. Validation itself lives in
 * `PlaylistListFragment`, not here - this only proves the sheet renders whatever the host decides
 * and reports taps back faithfully.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class CreatePlaylistNameSheetComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun setContent(
        visible: Boolean,
        name: String,
        errorMessage: String?,
        actions: CreatePlaylistNameActions,
    ) {
        compose.setContent {
            TakiTheme {
                Box(Modifier.fillMaxSize()) {
                    CreatePlaylistNameSheet(
                        visible = visible,
                        name = name,
                        errorMessage = errorMessage,
                        actions = actions,
                    )
                }
            }
        }
    }

    @Test
    fun `hidden when not visible`() {
        setContent(
            visible = false,
            name = "",
            errorMessage = null,
            actions = CreatePlaylistNameActions.Noop,
        )
        compose.onNodeWithTag(CREATE_PLAYLIST_NAME_SHEET_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `no error message by default`() {
        setContent(
            visible = true,
            name = "",
            errorMessage = null,
            actions = CreatePlaylistNameActions.Noop,
        )
        compose.onNodeWithText(string(R.string.playlist_name_required)).assertDoesNotExist()
    }

    @Test
    fun `shows the host-owned error message`() {
        setContent(
            visible = true,
            name = "",
            errorMessage = string(R.string.playlist_name_required),
            actions = CreatePlaylistNameActions.Noop,
        )
        compose.onNodeWithText(string(R.string.playlist_name_required)).assertIsDisplayed()
    }

    @Test
    fun `typing calls onNameChange with the new text`() {
        var changed: String? = null
        setContent(
            visible = true,
            name = "",
            errorMessage = null,
            actions = CreatePlaylistNameActions.Noop.copy(onNameChange = { changed = it }),
        )
        compose.onNodeWithTag(CREATE_PLAYLIST_NAME_FIELD_TEST_TAG).performTextInput("Road Trip")
        assertEquals("Road Trip", changed)
    }

    @Test
    fun `tapping Create calls onCreate`() {
        var created = false
        setContent(
            visible = true,
            name = "Road Trip",
            errorMessage = null,
            actions = CreatePlaylistNameActions.Noop.copy(onCreate = { created = true }),
        )
        compose.onNodeWithText(string(R.string.playlist_create_action)).performClick()
        assertTrue(created)
    }

    @Test
    fun `tapping Cancel calls onDismiss`() {
        var dismissed = false
        setContent(
            visible = true,
            name = "",
            errorMessage = null,
            actions = CreatePlaylistNameActions.Noop.copy(onDismiss = { dismissed = true }),
        )
        compose.onNodeWithText(string(R.string.common_cancel)).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `tapping the scrim calls onDismiss`() {
        var dismissed = false
        setContent(
            visible = true,
            name = "",
            errorMessage = null,
            actions = CreatePlaylistNameActions.Noop.copy(onDismiss = { dismissed = true }),
        )
        compose.onNodeWithTag(CREATE_PLAYLIST_NAME_SCRIM_TEST_TAG).performClick()
        assertTrue(dismissed)
    }
}
