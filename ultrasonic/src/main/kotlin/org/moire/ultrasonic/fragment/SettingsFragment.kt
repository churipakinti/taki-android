/*
 * SettingsFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import android.view.View
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.File
import kotlin.math.ceil
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.log.FileLoggerTree
import org.moire.ultrasonic.log.FileLoggerTree.Companion.deleteLogFiles
import org.moire.ultrasonic.log.FileLoggerTree.Companion.getLogFileNumber
import org.moire.ultrasonic.log.FileLoggerTree.Companion.getLogFileSizes
import org.moire.ultrasonic.log.FileLoggerTree.Companion.plantToTimberForest
import org.moire.ultrasonic.log.FileLoggerTree.Companion.uprootFromTimberForest
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.ErrorDialog
import org.moire.ultrasonic.util.FileUtil.albumArtDirectory
import org.moire.ultrasonic.util.FileUtil.ultrasonicDirectory
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.SelectCacheActivityContract
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Settings.id3TagsEnabledOnline
import org.moire.ultrasonic.util.Settings.preferences
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.TimeSpanPreference
import org.moire.ultrasonic.util.TimeSpanPreferenceDialogFragmentCompat
import org.moire.ultrasonic.util.Util.formatBytes
import org.moire.ultrasonic.util.Util.toast
import timber.log.Timber

/**
 * Shows main app settings.
 */
@Suppress("TooManyFunctions")
class SettingsFragment :
    PreferenceFragmentCompat(),
    OnSharedPreferenceChangeListener,
    KoinComponent {
    private var theme: ListPreference? = null
    private var cacheLocation: Preference? = null
    private var showArtistPicture: CheckBoxPreference? = null
    private var useId3TagsOffline: CheckBoxPreference? = null
    private var resumeOnBluetoothDevice: Preference? = null
    private var pauseOnBluetoothDevice: Preference? = null
    private var debugLogToFile: CheckBoxPreference? = null
    private var customCacheLocation: CheckBoxPreference? = null
    private var clearImageCache: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, R.string.menu_settings)
        theme = findPreference(getString(R.string.setting_key_theme))
        resumeOnBluetoothDevice =
            findPreference(getString(R.string.setting_key_resume_on_bluetooth_device))
        pauseOnBluetoothDevice =
            findPreference(getString(R.string.setting_key_pause_on_bluetooth_device))
        debugLogToFile = findPreference(getString(R.string.setting_key_debug_log_to_file))
        showArtistPicture = findPreference(getString(R.string.setting_key_show_artist_picture))
        useId3TagsOffline = findPreference(getString(R.string.setting_key_id3_tags_offline))
        customCacheLocation = findPreference(getString(R.string.setting_key_custom_cache_location))
        cacheLocation = findPreference(getString(R.string.setting_key_cache_location))
        clearImageCache = findPreference(getString(R.string.setting_key_clear_image_cache))

        setupClearSearchPreference()
        setupClearImageCachePreference()
        setupCacheLocationPreference()
        setupBluetoothDevicePreferences()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get all setting keys and populate the summaries
        Settings.getAllKeys().forEach {
            updatePreferenceSummaries(it)
        }

        updateCustomPreferences()
    }

    override fun onResume() {
        super.onResume()
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null || sharedPreferences == null) return

        Timber.d("Preference changed: %s", key)
        updateCustomPreferences()

        updatePreferenceSummaries(key)

        when (key) {
            getString(R.string.setting_key_hide_media) -> {
                setHideMedia(sharedPreferences.getBoolean(key, false))
            }

            getString(R.string.setting_key_debug_log_to_file) -> {
                setDebugLogToFile(sharedPreferences.getBoolean(key, false))
            }

            getString(R.string.setting_key_id3_tags) -> {
                val enabled = sharedPreferences.getBoolean(key, false)
                showArtistPicture?.isEnabled = enabled
                useId3TagsOffline?.isEnabled = enabled
            }

            getString(R.string.setting_key_theme) -> {
                RxBus.themeChangedEventPublisher.onNext(Unit)
            }

            getString(R.string.setting_key_custom_cache_location) -> {
                if (Settings.customCacheLocation) {
                    selectCacheLocation()
                } else {
                    if (Settings.cacheLocationUri != "") setCacheLocation("")
                    setupCacheLocationPreference()
                }
            }
        }
    }

    /**
     * Update preference summaries to reflect the current select item (or entered text) in the UI
     *
     * @param key: The key of the preference to update
     */
    private fun updatePreferenceSummaries(key: String) {
        try {
            when (val pref: Preference? = findPreference(key)) {
                is ListPreference -> {
                    pref.summary = pref.entry
                }

                is EditTextPreference -> {
                    pref.summary = pref.text
                }

                is TimeSpanPreference -> {
                    pref.summary = pref.text
                }
            }
        } catch (ignored: Exception) {
            // If we have updated a ListPreferences possible values, and the user has now an
            // impossible value, getEntry() will throw an Exception.
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is TimeSpanPreference) {
            val dialogFragment = TimeSpanPreferenceDialogFragmentCompat()
            val bundle = Bundle(1)
            bundle.putString("key", preference.key)
            dialogFragment.arguments = bundle
            @Suppress("DEPRECATION") // Their own super class uses this call :shrug:
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(
                this.parentFragmentManager,
                "androidx.preference.PreferenceFragment.DIALOG"
            )
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun setupCacheLocationPreference() {
        if (!Settings.customCacheLocation) {
            cacheLocation?.isVisible = false
            return
        }

        cacheLocation?.isVisible = true
        val uri = Settings.cacheLocationUri.toUri()
        cacheLocation!!.summary = uri.path
        cacheLocation!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            selectCacheLocation()
            true
        }
    }

    private fun selectCacheLocation() {
        // Start the activity to pick a directory using the system's file picker.
        selectCacheActivityContract.launch(Settings.cacheLocationUri)
    }

    // Custom activity result contract
    private val selectCacheActivityContract =
        registerForActivityResult(SelectCacheActivityContract()) { uri ->
            // parseResult will return the chosen path as an Uri
            if (uri != null) {
                val contentResolver = UApp.applicationContext().contentResolver
                contentResolver.takePersistableUriPermission(uri, RW_FLAG)
                setCacheLocation(uri.toString())
                setupCacheLocationPreference()
            } else {
                ErrorDialog.Builder(requireContext())
                    .setMessage(R.string.settings_cache_location_error)
                    .show()
                if (Settings.cacheLocationUri == "") {
                    Settings.customCacheLocation = false
                    customCacheLocation?.isChecked = false
                    setupCacheLocationPreference()
                }
            }
        }

    private fun setupBluetoothDevicePreferences() {
        val resumeSetting = Settings.resumeOnBluetoothDevice
        val pauseSetting = Settings.pauseOnBluetoothDevice
        resumeOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(resumeSetting)
        pauseOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(pauseSetting)
        resumeOnBluetoothDevice!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showBluetoothDevicePreferenceDialog(
                    R.string.settings_playback_resume_on_bluetooth_device,
                    Settings.resumeOnBluetoothDevice
                ) { choice: Int ->
                    Settings.resumeOnBluetoothDevice = choice
                    resumeOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(choice)
                }
                true
            }
        pauseOnBluetoothDevice!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showBluetoothDevicePreferenceDialog(
                    R.string.settings_playback_pause_on_bluetooth_device,
                    Settings.pauseOnBluetoothDevice
                ) { choice: Int ->
                    Settings.pauseOnBluetoothDevice = choice
                    pauseOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(choice)
                }
                true
            }
    }

    private fun showBluetoothDevicePreferenceDialog(
        @StringRes title: Int,
        defaultChoice: Int,
        onChosen: (Int) -> Unit
    ) {
        val choice = intArrayOf(defaultChoice)
        ConfirmationDialog.Builder(requireContext()).setTitle(title)
            .setSingleChoiceItems(
                R.array.bluetoothDeviceSettingNames,
                defaultChoice
            ) { _: DialogInterface?, i: Int -> choice[0] = i }
            .setNegativeButton(R.string.common_cancel) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.cancel()
            }
            .setPositiveButton(R.string.common_ok) { dialogInterface: DialogInterface, _: Int ->
                onChosen(choice[0])
                dialogInterface.dismiss()
            }
            .create().show()
    }

    private fun bluetoothDevicePreferenceToString(preferenceValue: Int): String =
        when (preferenceValue) {
            Constants.PREFERENCE_VALUE_ALL -> {
                getString(R.string.settings_playback_bluetooth_all)
            }

            Constants.PREFERENCE_VALUE_A2DP -> {
                getString(R.string.settings_playback_bluetooth_a2dp)
            }

            Constants.PREFERENCE_VALUE_DISABLED -> {
                getString(R.string.settings_playback_bluetooth_disabled)
            }

            else -> ""
        }

    private fun setupClearSearchPreference() {
        val clearSearchPreference =
            findPreference<Preference>(getString(R.string.setting_key_clear_search_history))
        if (clearSearchPreference != null) {
            clearSearchPreference.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val suggestions = SearchRecentSuggestions(
                        activity,
                        SearchSuggestionProvider.AUTHORITY,
                        SearchSuggestionProvider.MODE
                    )
                    suggestions.clearHistory()
                    toast(R.string.settings_search_history_cleared)
                    false
                }
        }
    }

    private fun setupClearImageCachePreference() {
        val clearImagePreference =
            findPreference<Preference>(getString(R.string.setting_key_clear_image_cache))
        if (clearImagePreference != null) {
            val cacheSize = getImageCacheSize()
            clearImagePreference.summary =
                getString(R.string.settings_clear_image_cache_summary, cacheSize)
            clearImagePreference.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    ConfirmationDialog.Builder(requireContext())
                        .setMessage(R.string.settings_clear_image_cache_confirm)
                        .setNegativeButton(R.string.common_cancel) { dIf: DialogInterface, _: Int ->
                            dIf.cancel()
                        }
                        .setPositiveButton(R.string.common_ok) { _: DialogInterface, _: Int ->
                            clearImageCache()
                        }
                        .create().show()
                    false
                }
        }
    }

    private fun getImageCacheSize(): String {
        val albumArtDir = albumArtDirectory
        if (!albumArtDir.exists()) return "0 B"

        var totalSize = 0L
        albumArtDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                totalSize += file.length()
            }
        }
        return formatBytes(totalSize)
    }

    private fun clearImageCache() {
        val albumArtDir = albumArtDirectory
        if (!albumArtDir.exists()) {
            toast(R.string.settings_clear_image_cache_cleared)
            return
        }

        var deletedCount = 0
        albumArtDir.walkTopDown().forEach { file ->
            if (file.isFile && file.delete()) {
                deletedCount++
            }
        }
        Timber.i("Deleted %d image cache files", deletedCount)
        clearImageCache?.summary = getString(R.string.settings_clear_image_cache_summary, "0 B")
        toast(R.string.settings_clear_image_cache_cleared)
    }

    private fun updateCustomPreferences() {
        if (debugLogToFile?.isChecked == true) {
            debugLogToFile?.summary = getString(
                R.string.settings_debug_log_path,
                ultrasonicDirectory,
                FileLoggerTree.FILENAME
            )
        } else {
            debugLogToFile?.summary = ""
        }

        showArtistPicture?.isEnabled = id3TagsEnabledOnline
        useId3TagsOffline?.isEnabled = id3TagsEnabledOnline
    }

    private fun setHideMedia(hide: Boolean) {
        // TODO this only hides the media files in the Ultrasonic dir and not in the music cache
        val nomediaDir = File(ultrasonicDirectory, ".nomedia")
        if (hide && !nomediaDir.exists()) {
            if (!nomediaDir.mkdir()) {
                Timber.w("Failed to create %s", nomediaDir)
            }
        } else if (nomediaDir.exists()) {
            if (!nomediaDir.delete()) {
                Timber.w("Failed to delete %s", nomediaDir)
            }
        }
        toast(R.string.settings_hide_media_toast, false)
    }

    private fun setCacheLocation(path: String) {
        if (path != "") {
            val uri = path.toUri()
            cacheLocation!!.summary = uri.path ?: ""
        }

        Settings.cacheLocationUri = path

        // Clear download queue.
        DownloadService.clearDownloads()
        Storage.reset()
        Storage.checkForErrorsWithCustomRoot()
    }

    private fun setDebugLogToFile(writeLog: Boolean) {
        if (writeLog) {
            plantToTimberForest()
            Timber.i("Enabled debug logging to file")
        } else {
            uprootFromTimberForest()
            Timber.i("Disabled debug logging to file")
            val fileNum = getLogFileNumber()
            val fileSize = getLogFileSizes()
            val message = getString(
                R.string.settings_debug_log_summary,
                fileNum.toString(),
                ceil(fileSize.toDouble() / 1000 / 1000).toString(),
                ultrasonicDirectory
            )
            val keep = R.string.settings_debug_log_keep
            val delete = R.string.settings_debug_log_delete
            ConfirmationDialog.Builder(requireContext())
                .setMessage(message)
                .setNegativeButton(keep) { dIf: DialogInterface, _: Int ->
                    dIf.cancel()
                }
                .setPositiveButton(delete) { dIf: DialogInterface, _: Int ->
                    deleteLogFiles()
                    Timber.i("Deleted debug log files")
                    dIf.dismiss()
                    InfoDialog.Builder(requireContext())
                        .setMessage(R.string.settings_debug_log_deleted)
                        .setPositiveButton(R.string.common_ok) { dIf2: DialogInterface, _: Int ->
                            dIf2.dismiss()
                        }
                        .create().show()
                }
                .create().show()
        }
    }

    companion object {
        const val RW_FLAG = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val PERSISTABLE_FLAG = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    }
}
