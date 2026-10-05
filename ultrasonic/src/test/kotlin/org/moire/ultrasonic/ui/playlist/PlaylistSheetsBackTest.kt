/*
 * PlaylistSheetsBackTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.playlistlist.CreatePlaylistNameActions
import org.moire.ultrasonic.ui.playlistlist.CreatePlaylistNameSheet
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * System Back must close the Rename / Create-name sheets instead of leaving the screen (issue #10
 * phase 5A6; found on a Pixel 7: with the Rename sheet open, Back used to navigate away).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h900dp-xxhdpi")
class PlaylistSheetsBackTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `Back closes the open Rename sheet`() {
        var dismissed = 0
        compose.setContent {
            TakiTheme {
                Box(Modifier.fillMaxSize()) {
                    RenamePlaylistSheet(
                        visible = true,
                        name = "Mix",
                        errorMessage = null,
                        actions = RenamePlaylistActions.Noop.copy(onDismiss = { dismissed++ }),
                        bottomContentInset = 0.dp,
                    )
                }
            }
        }
        pressBack()
        assertEquals(1, dismissed)
    }

    @Test
    fun `a hidden Rename sheet does not swallow Back`() {
        var dismissed = 0
        val visible = mutableStateOf(true)
        compose.setContent {
            TakiTheme {
                Box(Modifier.fillMaxSize()) {
                    RenamePlaylistSheet(
                        visible = visible.value,
                        name = "Mix",
                        errorMessage = null,
                        actions = RenamePlaylistActions.Noop.copy(onDismiss = { dismissed++ }),
                        bottomContentInset = 0.dp,
                    )
                }
            }
        }
        visible.value = false
        compose.waitForIdle()
        pressBack()
        assertEquals("a closed sheet must let Back reach the screen's navigation", 0, dismissed)
    }

    @Test
    fun `Back closes the open Create-name sheet`() {
        var dismissed = 0
        compose.setContent {
            TakiTheme {
                Box(Modifier.fillMaxSize()) {
                    CreatePlaylistNameSheet(
                        visible = true,
                        name = "",
                        errorMessage = null,
                        actions = CreatePlaylistNameActions.Noop.copy(onDismiss = { dismissed++ }),
                        bottomContentInset = 0.dp,
                    )
                }
            }
        }
        pressBack()
        assertEquals(1, dismissed)
    }
}
