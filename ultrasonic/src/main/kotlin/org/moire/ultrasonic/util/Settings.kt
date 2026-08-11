/*
 * Settings.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.regex.Pattern
import kotlin.math.abs
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp

/**
 * Contains convenience functions for reading and writing preferences
 */
private val supportedBitrateQualities = intArrayOf(96, 160, 256, 320)

internal fun normalizeBitrateQuality(value: Int): Int {
    if (value == 0) return 0
    return supportedBitrateQualities.minBy { abs(it - value) }
}

object Settings {

    @JvmStatic
    val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(Util.appContext())

    @JvmStatic
    val maxBitRate: Int
        get() {
            return if (Util.isNetworkRestricted()) {
                maxBitRateMobile
            } else {
                maxBitRateWifi
            }
        }

    private var maxBitRateWifi
        by StringIntSetting(getKey(R.string.setting_key_max_bitrate_wifi))

    private var maxBitRateMobile
        by StringIntSetting(getKey(R.string.setting_key_max_bitrate_mobile))

    var maxBitRatePinning
        by StringIntSetting(getKey(R.string.setting_key_max_bitrate_pinning))

    fun normalizeBitrateQualitySettings() {
        val normalizedWifi = normalizeBitrateQuality(maxBitRateWifi)
        val normalizedMobile = normalizeBitrateQuality(maxBitRateMobile)
        val normalizedPinning = normalizeBitrateQuality(maxBitRatePinning)
        if (maxBitRateWifi != normalizedWifi) maxBitRateWifi = normalizedWifi
        if (maxBitRateMobile != normalizedMobile) maxBitRateMobile = normalizedMobile
        if (maxBitRatePinning != normalizedPinning) maxBitRatePinning = normalizedPinning
    }
    val pinWithHighestQuality: Boolean
        get() = (maxBitRatePinning == 0)

    const val PRELOAD_COUNT = 3

    const val PARALLEL_DOWNLOADS = 3

    @JvmStatic
    val cacheSizeMB: Int
        get() {
            val cacheSize = preferences.getString(
                getKey(R.string.setting_key_cache_size),
                "-1"
            )!!.toInt()
            return if (cacheSize == -1) Int.MAX_VALUE else cacheSize
        }

    @JvmStatic
    var customCacheLocation by BooleanSetting(
        getKey(R.string.setting_key_custom_cache_location),
        false
    )

    @JvmStatic
    var cacheLocationUri by StringSetting(
        getKey(R.string.setting_key_cache_location),
        ""
    )

    @JvmStatic
    var isWifiRequiredForDownload by BooleanSetting(
        getKey(R.string.setting_key_wifi_required_for_download),
        false
    )

    @JvmStatic
    var shareOnServer by BooleanSetting(getKey(R.string.setting_key_share_on_server), true)

    // Out-of-the-box vision: the only thing worth configuring is the server. These five used to
    // be switches under Settings > Appearance; fixed to their prior real-world default so
    // behavior doesn't change, just the ability to toggle them (see HANDOFF.md).
    const val SHOULD_DISPLAY_BITRATE_WITH_ARTIST = false

    const val SHOULD_USE_FOLDER_FOR_ARTIST_NAME = false

    const val SHOULD_SHOW_TRACK_NUMBER = false

    // Search result caps -- also double as generic page-size defaults well beyond Search itself
    // (Browse/library grids, "Play Random Songs," playlist creation, any track-collection screen
    // invoked without an explicit size). Fixed at their prior defaults so none of those callers'
    // behavior changes.
    const val DEFAULT_ALBUMS = 5

    const val MAX_ALBUMS = 20

    const val DEFAULT_SONGS = 10

    const val MAX_SONGS = 25

    const val MAX_ARTISTS = 10

    const val DEFAULT_ARTISTS = 3

    const val SEEK_INTERVAL = 5000

    val seekIntervalMillis: Long
        get() = (SEEK_INTERVAL / 1000).toLong()

    const val RESUME_PLAY_ON_HEADPHONE_PLUG = true

    // Bluetooth resume/pause used to be separately configurable (all devices / A2DP-only /
    // disabled); fixed to A2DP-only for both -- resuming/pausing only for actual audio devices
    // (headphones, speakers, car audio), not any paired Bluetooth device, is standard behavior
    // and needs no user decision. See BluetoothIntentReceiver.kt.

    const val SHOW_NOW_PLAYING = true

    @JvmStatic
    var shouldTransitionOnPlayback by BooleanSetting(
        getKey(R.string.setting_key_download_transition),
        true
    )

    // No per-server capability flag exists for scrobble support (unlike chat/bookmarks/shares),
    // so there's nothing to auto-detect -- always attempt it; a server with no scrobble target
    // configured (e.g. no Last.fm link) just no-ops.
    const val SCROBBLE_ENABLED = true

    // Normally you don't need to use these Settings directly,
    // use ActiveServerProvider.isID3Enabled() instead
    @JvmStatic
    var id3TagsEnabledOnline by BooleanSetting(getKey(R.string.setting_key_id3_tags), true)

    // See comment above.
    @JvmStatic
    var id3TagsEnabledOffline by BooleanSetting(getKey(R.string.setting_key_id3_tags_offline), true)

    var activeServer by IntSetting(getKey(R.string.setting_key_server_instance), -1)

    const val SERVER_SCALING = true

    var firstRunExecuted by BooleanSetting(getKey(R.string.setting_key_first_run_executed), false)

    const val SHOULD_SHOW_ARTIST_PICTURE = true

    // Chat is unreachable from the UI (hidden drawer item, see HANDOFF.md); kept only so
    // ChatFragment.kt -- deliberately not deleted, same "hide don't delete" pattern as the rest
    // of that feature -- still compiles unchanged if ever reactivated.
    const val CHAT_REFRESH_INTERVAL = 5000

    const val DIRECTORY_CACHE_TIME = 300

    const val SHOULD_SORT_BY_DISC = true

    var shouldClearBookmark
        by BooleanSetting(getKey(R.string.setting_key_clear_bookmark), false)

    var shouldAskForShareDetails
        by BooleanSetting(getKey(R.string.setting_key_ask_for_share_details), true)

    var defaultShareDescription
        by StringSetting(getKey(R.string.setting_key_default_share_description), "")

    @JvmStatic
    val shareGreeting: String?
        get() {
            val context = Util.appContext()
            val defaultVal = String.format(
                context.resources.getString(R.string.share_default_greeting),
                context.resources.getString(R.string.taki_appname)
            )
            return preferences.getString(
                getKey(R.string.setting_key_default_share_greeting),
                defaultVal
            )
        }

    var defaultShareExpiration by StringSetting(
        getKey(R.string.setting_key_default_share_expiration),
        "0"
    )

    val defaultShareExpirationInMillis: Long
        get() {
            val preference = defaultShareExpiration
            val split = COLON_PATTERN.split(preference)
            if (split.size == 2) {
                val timeSpanAmount = split[0].toLong()
                val timeSpanType = split[1]
                return TimeSpanPicker.calculateTimeSpan(appContext, timeSpanType, timeSpanAmount)
            }
            return 0
        }

    @JvmStatic
    var debugLogToFile by BooleanSetting(getKey(R.string.setting_key_debug_log_to_file), false)

    @JvmStatic
    val overrideLanguage by StringSetting(getKey(R.string.setting_key_override_language), "")

    var useHwOffload by BooleanSetting(getKey(R.string.setting_key_hardware_offload), false)

    @JvmStatic
    var replayGain by StringSetting(
        getKey(R.string.setting_key_replaygain),
        getKey(R.string.setting_key_replaygain_disabled)
    )

    @JvmStatic
    var firstInstalledVersion by IntSetting(
        getKey(R.string.setting_key_first_installed_version),
        0
    )

    @JvmStatic
    var showConfirmationDialog by BooleanSetting(
        getKey(R.string.setting_key_show_confirmation_dialog),
        false
    )

    @JvmStatic
    var lastViewType by IntSetting(
        getKey(R.string.setting_key_last_view_type),
        0
    )

    // Internal cache for the Home screen's daily genre Mix - not user-facing, so plain keys
    // (not backed by a setting_keys.xml resource like the settings above).
    @JvmStatic
    var homeMixDate by StringSetting("home_mix_date", "")

    @JvmStatic
    var homeMixGenre by StringSetting("home_mix_genre", "")

    @JvmStatic
    var homeMixTrackIds by StringSetting("home_mix_track_ids", "")

    fun hasKey(key: String): Boolean = preferences.contains(key)

    private fun getKey(key: Int): String = appContext.getString(key)

    fun getAllKeys(): List<String> = preferences.all.keys.toList()

    private val appContext: Context
        get() = UApp.applicationContext()

    val COLON_PATTERN: Pattern = Pattern.compile(":")
}
