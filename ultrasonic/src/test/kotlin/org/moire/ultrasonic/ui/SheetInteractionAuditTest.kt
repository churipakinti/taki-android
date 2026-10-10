/*
 * SheetInteractionAuditTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.ui.album.ALBUM_INFO_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.album.AlbumInfoSheet
import org.moire.ultrasonic.ui.album.AlbumInfoUiState
import org.moire.ultrasonic.ui.components.APP_ERROR_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.components.AppErrorSheet
import org.moire.ultrasonic.ui.equalizer.EQUALIZER_PRESET_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.equalizer.EqualizerPresetSheet
import org.moire.ultrasonic.ui.equalizer.EqualizerPresetUiState
import org.moire.ultrasonic.ui.library.LIBRARY_HUB_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.library.LibraryHubSheet
import org.moire.ultrasonic.ui.player.SAVE_PLAYLIST_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.player.SLEEP_TIMER_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.player.SavePlaylistActions
import org.moire.ultrasonic.ui.player.SavePlaylistSheet
import org.moire.ultrasonic.ui.player.SleepTimerActions
import org.moire.ultrasonic.ui.player.SleepTimerSheet
import org.moire.ultrasonic.ui.playlist.ADD_TO_PLAYLIST_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.playlist.AddToPlaylistOption
import org.moire.ultrasonic.ui.playlist.AddToPlaylistSheet
import org.moire.ultrasonic.ui.playlist.RENAME_PLAYLIST_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.playlist.RenamePlaylistActions
import org.moire.ultrasonic.ui.playlist.RenamePlaylistSheet
import org.moire.ultrasonic.ui.playlistlist.CREATE_PLAYLIST_NAME_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.playlistlist.CreatePlaylistNameActions
import org.moire.ultrasonic.ui.playlistlist.CreatePlaylistNameSheet
import org.moire.ultrasonic.ui.playlistlist.PLAYLIST_INFO_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.playlistlist.PlaylistInfoSheet
import org.moire.ultrasonic.ui.playlistlist.PlaylistInfoUiState
import org.moire.ultrasonic.ui.playlistlist.UPDATE_PLAYLIST_INFO_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.playlistlist.UpdatePlaylistInfoSheet
import org.moire.ultrasonic.ui.serverselector.DELETE_SERVER_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.serverselector.DISCARD_SERVER_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.serverselector.DeleteServerSheet
import org.moire.ultrasonic.ui.serverselector.DiscardServerChangesSheet
import org.moire.ultrasonic.ui.settings.ConfirmAction
import org.moire.ultrasonic.ui.settings.SETTINGS_CHOICE_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.settings.SETTINGS_CONFIRM_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.settings.SETTINGS_INFO_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.settings.SettingsChoiceSheet
import org.moire.ultrasonic.ui.settings.SettingsConfirmSheet
import org.moire.ultrasonic.ui.settings.SettingsInfoSheet
import org.moire.ultrasonic.ui.settings.SettingsItem
import org.moire.ultrasonic.ui.settings.SettingsOverlay
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 5B sheet interaction audit: the SAME two probes run against EVERY reachable Compose sheet
 * of the app, and the outcome of each is pinned in the table below.
 *
 *  - **tap-through** - a tap on the panel's own, non-interactive top edge (the drag handle). It
 *    must never reach the scrim behind the panel and dismiss the sheet. Phase 5A6 found and fixed
 *    this on `TakiSheet`; this audit found the same defect in every sheet built *before* 5A6.
 *  - **Back** - the system Back button, handled by the composable itself (a `TakiBackHandler`).
 *    A sheet whose composable does not handle Back relies on its host Fragment - or, if the host
 *    registers nothing either, Back navigates away from the screen with the sheet still open
 *    (confirmed live for the Settings choice sheet and others; see the audit document).
 *
 * The expectations are the CURRENT behaviour, deliberately including the known defects (BF-1
 * tap-through, BF-2 Back), so the build stays green and the table is the durable evidence. When a
 * sheet is fixed, its row here flips and must be updated - that is the point.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h900dp-xxhdpi")
class SheetInteractionAuditTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var dismissed = 0
    private val onDismiss: () -> Unit = { dismissed++ }

    private fun host(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        compose.setContent { TakiTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    /** Taps the panel's top edge (drag handle) - never an interactive element. */
    private fun tapPanelEdge(panelTag: String): Int {
        dismissed = 0
        compose.onNodeWithTag(panelTag).performTouchInput { click(topCenter + androidx.compose.ui.geometry.Offset(0f, 6f)) }
        compose.waitForIdle()
        return dismissed
    }

    private fun pressBack(): Int {
        dismissed = 0
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        return dismissed
    }

    /** [tapThrough] / [backHandled] = what the sheet does today. */
    private fun audit(panelTag: String, tapThrough: Boolean, backHandled: Boolean) {
        assertEquals(
            "$panelTag: panel tap dismissed=${tapThrough}",
            if (tapThrough) 1 else 0,
            tapPanelEdge(panelTag),
        )
        assertEquals(
            "$panelTag: composable-level Back handled=${backHandled}",
            if (backHandled) 1 else 0,
            pressBack(),
        )
    }

    // ---- Built on TakiSheet (5A6): panel swallows taps, composable handles Back ---------------

    @Test
    fun addToPlaylist() {
        host { AddToPlaylistSheet(listOf(AddToPlaylistOption("1", "A")), {}, onDismiss, 0.dp) }
        audit(ADD_TO_PLAYLIST_SHEET_TEST_TAG, tapThrough = false, backHandled = true)
    }

    @Test
    fun albumInfo() {
        host {
            AlbumInfoSheet(AlbumInfoUiState("A", "B", "2020", 3, 1, "d", null), onDismiss, 0.dp)
        }
        audit(ALBUM_INFO_SHEET_TEST_TAG, tapThrough = false, backHandled = true)
    }

    @Test
    fun playlistInfo() {
        host { PlaylistInfoSheet(PlaylistInfoUiState("P", "o", "c", "1", true, "d"), onDismiss, 0.dp) }
        audit(PLAYLIST_INFO_SHEET_TEST_TAG, tapThrough = false, backHandled = true)
    }

    @Test
    fun playlistUpdateInfo() {
        host { UpdatePlaylistInfoSheet("P", "c", true, { _, _, _ -> }, onDismiss, 0.dp) }
        audit(UPDATE_PLAYLIST_INFO_SHEET_TEST_TAG, tapThrough = false, backHandled = true)
    }

    @Test
    fun libraryHub() {
        host { LibraryHubSheet("srv", {}, onDismiss, 0.dp) }
        audit(LIBRARY_HUB_SHEET_TEST_TAG, tapThrough = false, backHandled = true)
    }

    @Test
    fun appError() {
        host { AppErrorSheet(listOf("boom"), onDismiss, 0.dp) }
        audit(APP_ERROR_SHEET_TEST_TAG, tapThrough = false, backHandled = true)
    }

    // ---- Back fixed in 5A6, panel tap-through still open (BF-1) -------------------------------

    @Test
    fun renamePlaylist() {
        host {
            RenamePlaylistSheet(true, "n", null, RenamePlaylistActions.Noop.copy(onDismiss = onDismiss), 0.dp)
        }
        audit(RENAME_PLAYLIST_SHEET_TEST_TAG, tapThrough = true, backHandled = true)
    }

    @Test
    fun createPlaylistName() {
        host {
            CreatePlaylistNameSheet(
                true, "n", null, CreatePlaylistNameActions.Noop.copy(onDismiss = onDismiss), 0.dp,
            )
        }
        audit(CREATE_PLAYLIST_NAME_SHEET_TEST_TAG, tapThrough = true, backHandled = true)
    }

    // ---- Built before 5A6: tap-through (BF-1) AND no composable-level Back (BF-2) -------------

    @Test
    fun settingsChoice() {
        host {
            SettingsChoiceSheet(
                SettingsItem.Choice(
                    key = "k",
                    titleRes = R.string.settings_max_bitrate_mobile,
                    entriesRes = R.array.maxBitrateNames,
                    valuesRes = R.array.maxBitrateValues,
                    defaultValue = "0",
                ),
                "0", {}, onDismiss, 0.dp,
            )
        }
        audit(SETTINGS_CHOICE_SHEET_TEST_TAG, tapThrough = true, backHandled = false)
    }

    @Test
    fun settingsConfirm() {
        host {
            SettingsConfirmSheet(
                SettingsOverlay.Confirm(
                    titleRes = R.string.common_confirm,
                    messageRes = R.string.common_confirm,
                    confirmLabelRes = R.string.common_ok,
                    dismissLabelRes = R.string.common_cancel,
                    action = ConfirmAction.CLEAR_DOWNLOADS,
                ),
                {}, onDismiss, 0.dp,
            )
        }
        audit(SETTINGS_CONFIRM_SHEET_TEST_TAG, tapThrough = true, backHandled = false)
    }

    @Test
    fun settingsInfo() {
        host { SettingsInfoSheet(SettingsOverlay.Info(R.string.common_ok), onDismiss, 0.dp) }
        audit(SETTINGS_INFO_SHEET_TEST_TAG, tapThrough = true, backHandled = false)
    }

    @Test
    fun deleteServer() {
        host { DeleteServerSheet(true, "srv", {}, onDismiss, 0.dp) }
        audit(DELETE_SERVER_SHEET_TEST_TAG, tapThrough = true, backHandled = false)
    }

    @Test
    fun discardServerChanges() {
        host { DiscardServerChangesSheet(true, {}, onDismiss, 0.dp) }
        audit(DISCARD_SERVER_SHEET_TEST_TAG, tapThrough = true, backHandled = false)
    }

    @Test
    fun savePlaylist() {
        host {
            SavePlaylistSheet(true, "n", SavePlaylistActions.Noop.copy(onDismiss = onDismiss))
        }
        audit(SAVE_PLAYLIST_SHEET_TEST_TAG, tapThrough = true, backHandled = false)
    }

    @Test
    fun sleepTimer() {
        host {
            SleepTimerSheet(true, SleepTimerState.Off, true, SleepTimerActions.Noop.copy(onDismiss = onDismiss))
        }
        audit(SLEEP_TIMER_SHEET_TEST_TAG, tapThrough = true, backHandled = false)
    }

    @Test
    fun equalizerPreset() {
        host {
            EqualizerPresetSheet(listOf(EqualizerPresetUiState(0, "Normal")), 0, {}, onDismiss, 0.dp)
        }
        // Back is handled by EqualizerFragment's OnBackPressedCallback, not by the composable.
        audit(EQUALIZER_PRESET_SHEET_TEST_TAG, tapThrough = true, backHandled = false)
    }
}
