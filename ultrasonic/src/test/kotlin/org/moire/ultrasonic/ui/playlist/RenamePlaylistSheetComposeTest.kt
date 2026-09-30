/*
 * RenamePlaylistSheetComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * [RenamePlaylistSheet] (issue #10 phase 4M4): the sheet's own visibility, the host-owned
 * name/error state (seeded with the playlist's current name, unlike
 * [org.moire.ultrasonic.ui.playlistlist.CreatePlaylistNameSheet]'s blank default), and that every
 * action callback fires. Validation itself lives in `TrackCollectionFragment`, not here - this
 * only proves the sheet renders whatever the host decides and reports taps back faithfully.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class RenamePlaylistSheetComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun setContent(
        visible: Boolean,
        name: String,
        errorMessage: String?,
        actions: RenamePlaylistActions,
        bottomContentInset: Dp = 0.dp,
    ) {
        compose.setContent {
            TakiTheme {
                Box(Modifier.fillMaxSize()) {
                    RenamePlaylistSheet(
                        visible = visible,
                        name = name,
                        errorMessage = errorMessage,
                        actions = actions,
                        bottomContentInset = bottomContentInset,
                    )
                }
            }
        }
    }

    @Test
    fun `hidden when not visible`() {
        setContent(
            visible = false,
            name = "Road Trip",
            errorMessage = null,
            actions = RenamePlaylistActions.Noop,
        )
        compose.onNodeWithTag(RENAME_PLAYLIST_SHEET_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `prefills the field with the current playlist name`() {
        setContent(
            visible = true,
            name = "Road Trip",
            errorMessage = null,
            actions = RenamePlaylistActions.Noop,
        )
        compose.onNodeWithText("Road Trip").assertIsDisplayed()
    }

    @Test
    fun `no error message by default`() {
        setContent(
            visible = true,
            name = "Road Trip",
            errorMessage = null,
            actions = RenamePlaylistActions.Noop,
        )
        compose.onNodeWithText(string(R.string.playlist_name_required)).assertDoesNotExist()
    }

    @Test
    fun `shows the host-owned error message`() {
        setContent(
            visible = true,
            name = "",
            errorMessage = string(R.string.playlist_name_required),
            actions = RenamePlaylistActions.Noop,
        )
        compose.onNodeWithText(string(R.string.playlist_name_required)).assertIsDisplayed()
    }

    @Test
    fun `editing the prefilled name calls onNameChange with the new text`() {
        var changed: String? = null
        setContent(
            visible = true,
            name = "Road Trip",
            errorMessage = null,
            actions = RenamePlaylistActions.Noop.copy(onNameChange = { changed = it }),
        )
        compose.onNodeWithTag(RENAME_PLAYLIST_NAME_FIELD_TEST_TAG).performTextReplacement("Summer Mix")
        assertEquals("Summer Mix", changed)
    }

    @Test
    fun `tapping Rename calls onRename`() {
        var renamed = false
        setContent(
            visible = true,
            name = "Road Trip",
            errorMessage = null,
            actions = RenamePlaylistActions.Noop.copy(onRename = { renamed = true }),
        )
        compose.onNodeWithText(string(R.string.playlist_rename_confirm_action)).performClick()
        assertTrue(renamed)
    }

    @Test
    fun `tapping Cancel calls onDismiss`() {
        var dismissed = false
        setContent(
            visible = true,
            name = "Road Trip",
            errorMessage = null,
            actions = RenamePlaylistActions.Noop.copy(onDismiss = { dismissed = true }),
        )
        compose.onNodeWithText(string(R.string.common_cancel)).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `tapping the scrim calls onDismiss`() {
        var dismissed = false
        setContent(
            visible = true,
            name = "Road Trip",
            errorMessage = null,
            actions = RenamePlaylistActions.Noop.copy(onDismiss = { dismissed = true }),
        )
        compose.onNodeWithTag(RENAME_PLAYLIST_SCRIM_TEST_TAG).performClick()
        assertTrue(dismissed)
    }

    /**
     * Issue #10 phase 4M4: `NavigationActivity`'s bottom nav/mini-player are Activity-owned
     * overlays drawn *above* this Fragment-hosted sheet - found live to swallow every tap on the
     * Rename/Cancel row unless the sheet reserves the same [bottomContentInset] the screen behind
     * it already does. Locks that the reservation is real, not just accepted and dropped.
     */
    @Test
    fun `a nonzero bottomContentInset pushes the Rename button clear of the root's bottom edge`() {
        setContent(
            visible = true,
            name = "Road Trip",
            errorMessage = null,
            actions = RenamePlaylistActions.Noop,
            bottomContentInset = 96.dp,
        )
        val rootHeightPx = compose.onRoot().fetchSemanticsNode().size.height
        val buttonBottomPx = compose.onNodeWithText(string(R.string.playlist_rename_confirm_action))
            .fetchSemanticsNode().boundsInRoot.bottom
        val insetPx = with(compose.density) { 96.dp.toPx() }
        assertTrue(
            "expected the Rename button (bottom=$buttonBottomPx) to clear the reserved " +
                "$insetPx px chrome band (root height=$rootHeightPx)",
            buttonBottomPx <= rootHeightPx - insetPx,
        )
    }
}
