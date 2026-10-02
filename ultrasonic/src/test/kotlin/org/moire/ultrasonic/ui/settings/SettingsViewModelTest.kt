/*
 * SettingsViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.SettingsViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.robolectric.RobolectricTestRunner

/**
 * [SettingsViewModel] - a port of the legacy `SettingsFragment`'s own
 * `onSharedPreferenceChanged`/`updatePreferenceSummaries`/`updateCustomPreferences` logic (issue
 * #10 phase 5A4), a pure projection over the real `Settings`/`SharedPreferences` storage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RobolectricUAppContext.install()
        app = ApplicationProvider.getApplicationContext()
        // Every test starts from a clean slate. `Settings.preferences` is a fresh computed
        // property on every access, but `Settings`'s delegate-backed properties (cacheLocationUri,
        // customCacheLocation, ...) each lazily cache their own SharedPreferences reference once
        // and never re-resolve it - clearing only via `Settings.preferences.edit().clear()` does
        // not reach them, so every such property this suite touches is reset explicitly here too.
        org.moire.ultrasonic.util.Settings.preferences.edit().clear().commit()
        org.moire.ultrasonic.util.Settings.cacheLocationUri = ""
        org.moire.ultrasonic.util.Settings.customCacheLocation = false
        org.moire.ultrasonic.util.Settings.isWifiRequiredForDownload = false
        org.moire.ultrasonic.util.Settings.debugLogToFile = false
        org.moire.ultrasonic.util.Settings.id3TagsEnabledOnline = true
        org.moire.ultrasonic.util.Settings.id3TagsEnabledOffline = true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(): SettingsViewModel {
        val model = SettingsViewModel(app)
        model.offlineTracksLoader = { emptyList() }
        model.downloadDeleter = { }
        model.imageCacheSizeReader = { 0L }
        model.imageCacheClearer = { }
        model.searchHistoryClearer = { }
        model.cacheLocationApplier = { }
        model.logPlanter = { }
        model.logUprooter = { }
        model.logFileNumberReader = { 0 }
        model.logFileSizeReader = { 0L }
        model.logFileDeleter = { }
        model.toastShower = { }
        return model
    }

    private fun toggleItem(rows: List<SettingsRowState>, key: String) =
        rows.filterIsInstance<SettingsRowState.ToggleRow>().first { it.item.key == key }

    private fun choiceItem(rows: List<SettingsRowState>, key: String) =
        rows.filterIsInstance<SettingsRowState.ChoiceRow>().first { it.item.key == key }

    private fun actionItem(rows: List<SettingsRowState>, key: String) =
        rows.filterIsInstance<SettingsRowState.ActionRow>().first { it.item.key == key }

    // --- Screen resolution ---------------------------------------------------------------------

    @Test
    fun `top level resolves the Settings title and its five rows`() {
        val model = vm()
        model.load(null)
        val state = model.uiState.value
        assertEquals(R.string.menu_settings, state.titleRes)
        assertEquals(5, state.rows.size)
    }

    @Test
    fun `each nested group key resolves to its own title and items`() {
        val expectations = mapOf(
            SettingsGroupKeys.NOW_PLAYING to R.string.settings_now_playing_title,
            SettingsGroupKeys.DOWNLOADS to R.string.settings_downloads_title,
            SettingsGroupKeys.ADVANCED to R.string.settings_advanced_title,
            SettingsGroupKeys.MUSIC_CACHE to R.string.settings_cache_title,
            SettingsGroupKeys.LIBRARY to R.string.settings_library_title,
            SettingsGroupKeys.DEBUG to R.string.settings_debug_title,
        )
        expectations.forEach { (rootKey, expectedTitle) ->
            val model = vm()
            model.load(rootKey)
            assertEquals(expectedTitle, model.uiState.value.titleRes)
            assertTrue(model.uiState.value.rows.isNotEmpty())
        }
    }

    @Test
    fun `an unknown rootKey falls back to the top level, matching the legacy null-root convention`() {
        val model = vm()
        model.load("not_a_real_group")
        assertEquals(R.string.menu_settings, model.uiState.value.titleRes)
    }

    // --- Hardcoded key literals vs the real setting_key_* resources (safety net) ---------------

    @Test
    fun `every hardcoded persisted key matches its real setting_key resource string`() {
        val expectations = mapOf(
            "maxBitrateMobile" to R.string.setting_key_max_bitrate_mobile,
            "maxBitrateWifi" to R.string.setting_key_max_bitrate_wifi,
            "maxBitratePinning" to R.string.setting_key_max_bitrate_pinning,
            "cacheSize" to R.string.setting_key_cache_size,
            KEY_CUSTOM_CACHE_LOCATION to R.string.setting_key_custom_cache_location,
            SettingsActionKeys.CACHE_LOCATION to R.string.setting_key_cache_location,
            "wifiRequiredForDownload" to R.string.setting_key_wifi_required_for_download,
            SettingsActionKeys.CLEAR_SEARCH_HISTORY to R.string.setting_key_clear_search_history,
            SettingsActionKeys.CLEAR_IMAGE_CACHE to R.string.setting_key_clear_image_cache,
            KEY_ID3_TAGS to R.string.setting_key_id3_tags,
            KEY_ID3_TAGS_OFFLINE to R.string.setting_key_id3_tags_offline,
            "use_hw_offload" to R.string.setting_key_hardware_offload,
            KEY_DEBUG_LOG_TO_FILE to R.string.setting_key_debug_log_to_file,
            "overrideLanguage" to R.string.setting_key_override_language,
            "showConfirmationDialog" to R.string.setting_key_show_confirmation_dialog,
            "replayGain" to R.string.setting_key_replaygain,
            SettingsActionKeys.EQUALIZER to R.string.setting_key_equalizer,
            SettingsActionKeys.CLEAR_DOWNLOADS to R.string.setting_key_clear_downloads,
        )
        expectations.forEach { (literal, resId) ->
            assertEquals(app.getString(resId), literal)
        }
    }

    // --- Toggles ---------------------------------------------------------------------------

    @Test
    fun `a toggle persists to SharedPreferences and the row reflects it`() {
        val model = vm()
        model.load(SettingsGroupKeys.DOWNLOADS)
        val item = toggleItem(model.uiState.value.rows, "wifiRequiredForDownload").item
        model.onToggle(item, true)
        assertTrue(toggleItem(model.uiState.value.rows, "wifiRequiredForDownload").checked)
        assertTrue(
            org.moire.ultrasonic.util.Settings.preferences
                .getBoolean("wifiRequiredForDownload", false),
        )
    }

    @Test
    fun `id3 tags offline is enabled only while id3 tags online is on`() {
        val model = vm()
        model.load(SettingsGroupKeys.LIBRARY)
        // Default is true (both default to true per settings.xml) - enabled.
        assertTrue(toggleItem(model.uiState.value.rows, KEY_ID3_TAGS_OFFLINE).enabled)

        val onlineItem = toggleItem(model.uiState.value.rows, KEY_ID3_TAGS).item
        model.onToggle(onlineItem, false)
        assertFalse(toggleItem(model.uiState.value.rows, KEY_ID3_TAGS_OFFLINE).enabled)

        model.onToggle(onlineItem, true)
        assertTrue(toggleItem(model.uiState.value.rows, KEY_ID3_TAGS_OFFLINE).enabled)
    }

    @Test
    fun `an external preference change updates the row state reactively`() {
        val model = vm()
        model.load(SettingsGroupKeys.DOWNLOADS)
        assertFalse(toggleItem(model.uiState.value.rows, "wifiRequiredForDownload").checked)

        // Simulate another component (or a picker result) writing the same preference directly,
        // not through this ViewModel's own onToggle - the registered listener must still catch it.
        org.moire.ultrasonic.util.Settings.preferences.edit()
            .putBoolean("wifiRequiredForDownload", true).commit()

        assertTrue(toggleItem(model.uiState.value.rows, "wifiRequiredForDownload").checked)
    }

    // --- Choices -----------------------------------------------------------------------------

    @Test
    fun `a choice selection persists and closes the overlay`() {
        val model = vm()
        model.load(SettingsGroupKeys.ADVANCED)
        val item = choiceItem(model.uiState.value.rows, "replayGain").item
        model.onChoiceClick(item)
        assertTrue(model.uiState.value.overlay is SettingsOverlay.Choice)

        model.onChoiceSelected(item, "replayGainTrackOnly")
        assertEquals(SettingsOverlay.None, model.uiState.value.overlay)
        assertEquals(
            "replayGainTrackOnly",
            choiceItem(model.uiState.value.rows, "replayGain").currentValue,
        )
    }

    @Test
    fun `a stale stored choice value is carried through without crashing`() {
        org.moire.ultrasonic.util.Settings.preferences.edit()
            .putString("replayGain", "no_longer_a_real_option").commit()
        val model = vm()
        model.load(SettingsGroupKeys.ADVANCED)
        assertEquals(
            "no_longer_a_real_option",
            choiceItem(model.uiState.value.rows, "replayGain").currentValue,
        )
    }

    // --- Cache location ------------------------------------------------------------------------

    @Test
    fun `the cache location row is hidden until custom cache location is on`() {
        val model = vm()
        model.load(SettingsGroupKeys.MUSIC_CACHE)
        assertFalse(actionItem(model.uiState.value.rows, SettingsActionKeys.CACHE_LOCATION).visible)

        val toggle = toggleItem(model.uiState.value.rows, KEY_CUSTOM_CACHE_LOCATION).item
        model.onToggle(toggle, true)
        assertTrue(actionItem(model.uiState.value.rows, SettingsActionKeys.CACHE_LOCATION).visible)
    }

    @Test
    fun `turning on custom cache location launches the system picker effect`() {
        val model = vm()
        model.load(SettingsGroupKeys.MUSIC_CACHE)
        var launched: SettingsEffect? = null
        model.onEffect = { launched = it }

        val toggle = toggleItem(model.uiState.value.rows, KEY_CUSTOM_CACHE_LOCATION).item
        model.onToggle(toggle, true)

        assertTrue(launched is SettingsEffect.LaunchCacheLocationPicker)
    }

    @Test
    fun `turning off custom cache location clears a previously set uri`() {
        org.moire.ultrasonic.util.Settings.cacheLocationUri = "content://tree/123"
        var applied: String? = "untouched"
        val model = vm()
        model.cacheLocationApplier = { applied = it }
        model.load(SettingsGroupKeys.MUSIC_CACHE)

        val toggle = toggleItem(model.uiState.value.rows, KEY_CUSTOM_CACHE_LOCATION).item
        model.onToggle(toggle, false)

        assertEquals("", applied)
    }

    @Test
    fun `a picked cache location is applied and the summary refreshes`() {
        var applied: String? = null
        val model = vm()
        model.cacheLocationApplier = { applied = it }
        model.load(SettingsGroupKeys.MUSIC_CACHE)

        model.onCacheLocationPicked("content://tree/abc")

        assertEquals("content://tree/abc", applied)
        assertEquals(SettingsOverlay.None, model.uiState.value.overlay)
    }

    @Test
    fun `a cancelled or failed picker shows an error and leaves an unset uri off`() {
        val model = vm()
        model.load(SettingsGroupKeys.MUSIC_CACHE)
        val toggle = toggleItem(model.uiState.value.rows, KEY_CUSTOM_CACHE_LOCATION).item
        model.onToggle(toggle, true) // enabling without ever picking a uri

        model.onCacheLocationPicked(null)

        assertTrue(model.uiState.value.overlay is SettingsOverlay.Info)
        assertFalse(
            org.moire.ultrasonic.util.Settings.preferences
                .getBoolean(KEY_CUSTOM_CACHE_LOCATION, false),
        )
    }

    // --- Destructive actions -------------------------------------------------------------------

    @Test
    fun `clear search history runs the seam and does not open an overlay`() {
        var cleared = false
        val model = vm()
        model.searchHistoryClearer = { cleared = true }
        model.load(SettingsGroupKeys.ADVANCED)

        model.onActionClick(actionItem(model.uiState.value.rows, SettingsActionKeys.CLEAR_SEARCH_HISTORY).item)

        assertTrue(cleared)
        assertEquals(SettingsOverlay.None, model.uiState.value.overlay)
    }

    @Test
    fun `clear image cache opens a confirmation before doing anything`() {
        var cleared = false
        val model = vm()
        model.imageCacheClearer = { cleared = true }
        model.load(SettingsGroupKeys.MUSIC_CACHE)

        model.onActionClick(actionItem(model.uiState.value.rows, SettingsActionKeys.CLEAR_IMAGE_CACHE).item)
        assertFalse(cleared)
        assertTrue(model.uiState.value.overlay is SettingsOverlay.Confirm)

        model.onConfirm()
        assertTrue(cleared)
    }

    @Test
    fun `dismissing the clear image cache confirmation runs nothing`() {
        var cleared = false
        val model = vm()
        model.imageCacheClearer = { cleared = true }
        model.load(SettingsGroupKeys.MUSIC_CACHE)

        model.onActionClick(actionItem(model.uiState.value.rows, SettingsActionKeys.CLEAR_IMAGE_CACHE).item)
        model.onOverlayDismiss()

        assertFalse(cleared)
        assertEquals(SettingsOverlay.None, model.uiState.value.overlay)
    }

    @Test
    fun `clear all downloads loads offline tracks and deletes them off the main thread`() = runTest {
        var deleted: List<Track>? = null
        val tracks = listOf(Track("1", 1), Track("2", 1))
        val model = vm()
        model.offlineTracksLoader = { tracks }
        model.downloadDeleter = { deleted = it }
        model.load(SettingsGroupKeys.DOWNLOADS)

        model.onActionClick(actionItem(model.uiState.value.rows, SettingsActionKeys.CLEAR_DOWNLOADS).item)
        model.onConfirm()
        advanceUntilIdle()

        assertEquals(tracks, deleted)
    }

    // --- Debug logging -------------------------------------------------------------------------

    @Test
    fun `turning debug logging on plants the logger with no confirmation`() {
        var planted = false
        val model = vm()
        model.logPlanter = { planted = true }
        model.load(SettingsGroupKeys.DEBUG)

        val item = toggleItem(model.uiState.value.rows, KEY_DEBUG_LOG_TO_FILE).item
        model.onToggle(item, true)

        assertTrue(planted)
        assertEquals(SettingsOverlay.None, model.uiState.value.overlay)
    }

    @Test
    fun `turning debug logging off uproots immediately and asks about existing files`() {
        var uprooted = false
        val model = vm()
        model.logUprooter = { uprooted = true }
        model.logFileNumberReader = { 3 }
        model.logFileSizeReader = { 2_000_000L }
        model.load(SettingsGroupKeys.DEBUG)

        val item = toggleItem(model.uiState.value.rows, KEY_DEBUG_LOG_TO_FILE).item
        model.onToggle(item, false)

        assertTrue(uprooted) // stops immediately - the dialog is only about deleting old files
        val overlay = model.uiState.value.overlay
        assertTrue(overlay is SettingsOverlay.Confirm)
        assertEquals(ConfirmAction.DELETE_DEBUG_LOGS, (overlay as SettingsOverlay.Confirm).action)
    }

    @Test
    fun `confirming debug log deletion deletes files and shows an info sheet`() {
        var deleted = false
        val model = vm()
        model.logFileDeleter = { deleted = true }
        model.load(SettingsGroupKeys.DEBUG)
        val item = toggleItem(model.uiState.value.rows, KEY_DEBUG_LOG_TO_FILE).item
        model.onToggle(item, false)

        model.onConfirm()

        assertTrue(deleted)
        assertTrue(model.uiState.value.overlay is SettingsOverlay.Info)
    }

    @Test
    fun `keeping debug log files deletes nothing`() {
        var deleted = false
        val model = vm()
        model.logFileDeleter = { deleted = true }
        model.load(SettingsGroupKeys.DEBUG)
        val item = toggleItem(model.uiState.value.rows, KEY_DEBUG_LOG_TO_FILE).item
        model.onToggle(item, false)

        model.onOverlayDismiss()

        assertFalse(deleted)
        assertEquals(SettingsOverlay.None, model.uiState.value.overlay)
    }

    @Test
    fun `the debug log toggle shows its file path only while enabled`() {
        val model = vm()
        model.load(SettingsGroupKeys.DEBUG)
        assertNull(toggleItem(model.uiState.value.rows, KEY_DEBUG_LOG_TO_FILE).dynamicSummaryRes)

        val item = toggleItem(model.uiState.value.rows, KEY_DEBUG_LOG_TO_FILE).item
        model.onToggle(item, true)
        assertEquals(
            R.string.settings_debug_log_path,
            toggleItem(model.uiState.value.rows, KEY_DEBUG_LOG_TO_FILE).dynamicSummaryRes,
        )
    }

}
