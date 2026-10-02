/*
 * SettingsItem.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.settings

import org.moire.ultrasonic.R

/**
 * Where a [SettingsItem.Navigation] row leads. `Group` re-enters this same screen with a new
 * `rootKey` (the legacy `onPreferenceTreeClick`'s self-navigation via `settingsToGroup`);
 * `Equalizer`/`About` leave Settings entirely for an existing destination.
 */
enum class NavigationTarget { GROUP, EQUALIZER, ABOUT }

/**
 * One row (or visual-only category label) in a Settings screen - a 1:1 item-level port of
 * `R.xml.settings`'s `Preference`/`SwitchPreferenceCompat`/`ListPreference`/`PreferenceScreen`/
 * `PreferenceCategory` elements (issue #10 phase 5A4 audit). Deliberately small: five cases
 * cover every row the real XML has, nothing speculative (no `Value`/text-input case - zero
 * `EditTextPreference` rows exist in `settings.xml`, confirmed by full-file read).
 */
sealed interface SettingsItem {
    /**
     * A `SwitchPreferenceCompat`. [key] is the exact persisted SharedPreferences key.
     */
    data class Toggle(
        val key: String,
        val titleRes: Int,
        val summaryRes: Int? = null,
    ) : SettingsItem

    /**
     * A `ListPreference`. [entriesRes]/[valuesRes] are the same `R.array` resources the XML
     * referenced; values are matched/persisted as raw strings exactly like AndroidX Preference
     * does (every legacy `ListPreference` in this screen persists string-typed values, including
     * the numeric-looking bitrate/cache-size ones).
     */
    data class Choice(
        val key: String,
        val titleRes: Int,
        val entriesRes: Int,
        val valuesRes: Int,
        val defaultValue: String,
        val summaryRes: Int? = null,
    ) : SettingsItem

    /**
     * A row that navigates away from the current item list - either re-entering this same
     * screen for a nested group ([NavigationTarget.GROUP], using [key] as the next `rootKey`)
     * or leaving for Equalizer/About.
     */
    data class Navigation(
        val key: String,
        val titleRes: Int,
        val target: NavigationTarget,
        val summaryRes: Int? = null,
    ) : SettingsItem

    /**
     * A non-persisted action row (`a:persistent="false"` in the legacy XML) - clearing search
     * history, clearing the image cache, clearing downloads. [key] identifies which action to
     * dispatch; it is never read from or written to SharedPreferences.
     */
    data class Action(
        val key: String,
        val titleRes: Int,
        val summaryRes: Int? = null,
    ) : SettingsItem

    /** A `PreferenceCategory` - a visual-only section label, never clickable, no persisted key. */
    data class Category(val titleRes: Int) : SettingsItem
}

/** Stable keys for the six nested `PreferenceScreen` groups - also their nav `rootKey` values. */
object SettingsGroupKeys {
    const val NOW_PLAYING = "now_playing_group"
    const val DOWNLOADS = "downloads_group"
    const val ADVANCED = "advanced_group"
    const val MUSIC_CACHE = "music_cache_group"
    const val LIBRARY = "library_group"
    const val DEBUG = "debug_group"
}

/** Keys for rows whose click is an action/navigation dispatch, not a persisted value. */
object SettingsActionKeys {
    const val EQUALIZER = "equalizer"
    const val ABOUT = "about"
    const val CLEAR_DOWNLOADS = "clearDownloads"
    const val CLEAR_SEARCH_HISTORY = "clearSearchHistory"
    const val CLEAR_IMAGE_CACHE = "clearImageCache"
    const val CACHE_LOCATION = "cacheLocation"
}

const val KEY_CUSTOM_CACHE_LOCATION = "customCacheLocation"
const val KEY_ID3_TAGS = "useId3Tags"
const val KEY_ID3_TAGS_OFFLINE = "useId3TagsOffline"
const val KEY_DEBUG_LOG_TO_FILE = "debugLogToFile"

/**
 * The full Settings tree, a 1:1 transcription of `R.xml.settings` (issue #10 phase 5A4 audit).
 * [forRootKey] resolves which screen to show - `null` for the top level, matching
 * `PreferenceFragmentCompat.setPreferencesFromResource(R.xml.settings, rootKey)`'s own
 * null-means-root convention - plus that screen's own title resource.
 */
object SettingsDefinitions {

    fun forRootKey(rootKey: String?): Pair<Int, List<SettingsItem>> = when (rootKey) {
        null -> R.string.menu_settings to topLevel
        SettingsGroupKeys.NOW_PLAYING -> R.string.settings_now_playing_title to nowPlaying
        SettingsGroupKeys.DOWNLOADS -> R.string.settings_downloads_title to downloads
        SettingsGroupKeys.ADVANCED -> R.string.settings_advanced_title to advanced
        SettingsGroupKeys.MUSIC_CACHE -> R.string.settings_cache_title to musicCache
        SettingsGroupKeys.LIBRARY -> R.string.settings_library_title to library
        SettingsGroupKeys.DEBUG -> R.string.settings_debug_title to debug
        else -> R.string.menu_settings to topLevel
    }

    private val topLevel = listOf(
        SettingsItem.Navigation(
            SettingsGroupKeys.NOW_PLAYING,
            R.string.settings_now_playing_title,
            NavigationTarget.GROUP,
            R.string.settings_now_playing_summary,
        ),
        SettingsItem.Navigation(
            SettingsGroupKeys.DOWNLOADS,
            R.string.settings_downloads_title,
            NavigationTarget.GROUP,
            R.string.settings_downloads_summary,
        ),
        SettingsItem.Choice(
            key = "overrideLanguage",
            titleRes = R.string.settings_override_language,
            entriesRes = R.array.languageNames,
            valuesRes = R.array.languageValues,
            defaultValue = "",
            summaryRes = R.string.settings_override_language_summary,
        ),
        SettingsItem.Navigation(
            SettingsGroupKeys.ADVANCED,
            R.string.settings_advanced_title,
            NavigationTarget.GROUP,
            R.string.settings_advanced_summary,
        ),
        SettingsItem.Navigation(
            SettingsActionKeys.ABOUT,
            R.string.menu_about,
            NavigationTarget.ABOUT,
        ),
    )

    private val nowPlaying = listOf(
        SettingsItem.Navigation(
            SettingsActionKeys.EQUALIZER,
            R.string.settings_equalizer_title,
            NavigationTarget.EQUALIZER,
            R.string.settings_equalizer_summary,
        ),
        SettingsItem.Choice(
            key = "maxBitrateMobile",
            titleRes = R.string.settings_max_bitrate_mobile,
            entriesRes = R.array.maxBitrateNames,
            valuesRes = R.array.maxBitrateValues,
            defaultValue = "256",
        ),
        SettingsItem.Choice(
            key = "maxBitrateWifi",
            titleRes = R.string.settings_max_bitrate_wifi,
            entriesRes = R.array.maxBitrateNames,
            valuesRes = R.array.maxBitrateValues,
            defaultValue = "0",
        ),
    )

    private val downloads = listOf(
        SettingsItem.Choice(
            key = "maxBitratePinning",
            titleRes = R.string.settings_max_bitrate_pinning,
            entriesRes = R.array.maxBitrateNames,
            valuesRes = R.array.maxBitrateValues,
            defaultValue = "0",
        ),
        SettingsItem.Toggle(
            key = "wifiRequiredForDownload",
            titleRes = R.string.settings_wifi_required_title,
            summaryRes = R.string.settings_wifi_required_summary,
        ),
        SettingsItem.Action(
            SettingsActionKeys.CLEAR_DOWNLOADS,
            R.string.settings_clear_downloads,
            R.string.settings_clear_downloads_summary,
        ),
    )

    private val advanced = listOf(
        SettingsItem.Category(R.string.settings_advanced_playback),
        SettingsItem.Choice(
            key = "replayGain",
            titleRes = R.string.settings_replaygain_title,
            entriesRes = R.array.replayGainNames,
            valuesRes = R.array.replayGainValues,
            defaultValue = "replayGainDisabled",
        ),
        SettingsItem.Toggle(
            key = "use_hw_offload",
            titleRes = R.string.settings_use_hw_offload_title,
            summaryRes = R.string.settings_use_hw_offload_description,
        ),
        SettingsItem.Navigation(
            SettingsGroupKeys.MUSIC_CACHE,
            R.string.settings_cache_title,
            NavigationTarget.GROUP,
            R.string.settings_cache_summary,
        ),
        SettingsItem.Navigation(
            SettingsGroupKeys.LIBRARY,
            R.string.settings_library_title,
            NavigationTarget.GROUP,
            R.string.settings_library_summary,
        ),
        SettingsItem.Navigation(
            SettingsGroupKeys.DEBUG,
            R.string.settings_debug_title,
            NavigationTarget.GROUP,
            R.string.settings_debug_summary,
        ),
        SettingsItem.Category(R.string.settings_other_title),
        SettingsItem.Toggle(
            key = "showConfirmationDialog",
            titleRes = R.string.settings_show_confirmation_dialog,
            summaryRes = R.string.settings_show_confirmation_dialog_summary,
        ),
        SettingsItem.Action(
            SettingsActionKeys.CLEAR_SEARCH_HISTORY,
            R.string.settings_clear_search_history,
        ),
    )

    private val musicCache = listOf(
        SettingsItem.Choice(
            key = "cacheSize",
            titleRes = R.string.settings_cache_size,
            entriesRes = R.array.cacheSizeNames,
            valuesRes = R.array.cacheSizeValues,
            defaultValue = "500",
        ),
        SettingsItem.Toggle(
            key = KEY_CUSTOM_CACHE_LOCATION,
            titleRes = R.string.settings_custom_cache_location,
        ),
        SettingsItem.Action(
            SettingsActionKeys.CACHE_LOCATION,
            R.string.settings_cache_location,
        ),
        SettingsItem.Action(
            SettingsActionKeys.CLEAR_IMAGE_CACHE,
            R.string.settings_clear_image_cache,
        ),
    )

    private val library = listOf(
        SettingsItem.Toggle(
            key = KEY_ID3_TAGS,
            titleRes = R.string.settings_use_id3,
            summaryRes = R.string.settings_use_id3_summary,
        ),
        SettingsItem.Toggle(
            key = KEY_ID3_TAGS_OFFLINE,
            titleRes = R.string.settings_use_id3_offline,
            summaryRes = R.string.settings_use_id3_offline_summary,
        ),
    )

    private val debug = listOf(
        SettingsItem.Toggle(
            key = KEY_DEBUG_LOG_TO_FILE,
            titleRes = R.string.settings_debug_log_to_file,
        ),
    )
}
