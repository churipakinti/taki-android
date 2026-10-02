/*
 * SettingsViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.log.FileLoggerTree
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.ui.settings.ConfirmAction
import org.moire.ultrasonic.ui.settings.KEY_CUSTOM_CACHE_LOCATION
import org.moire.ultrasonic.ui.settings.KEY_DEBUG_LOG_TO_FILE
import org.moire.ultrasonic.ui.settings.KEY_ID3_TAGS
import org.moire.ultrasonic.ui.settings.KEY_ID3_TAGS_OFFLINE
import org.moire.ultrasonic.ui.settings.SettingsActionKeys
import org.moire.ultrasonic.ui.settings.SettingsDefinitions
import org.moire.ultrasonic.ui.settings.SettingsEffect
import org.moire.ultrasonic.ui.settings.SettingsItem
import org.moire.ultrasonic.ui.settings.SettingsOverlay
import org.moire.ultrasonic.ui.settings.SettingsRowState
import org.moire.ultrasonic.ui.settings.SettingsUiState
import org.moire.ultrasonic.util.FileUtil.albumArtDirectory
import org.moire.ultrasonic.util.FileUtil.ultrasonicDirectory
import org.moire.ultrasonic.util.RecentSearches
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Owns the Compose Settings state (issue #10 phase 5A4) - a projection over the existing
 * `Settings`/`SharedPreferences` storage, never a second persistence layer. A 1:1 port of
 * `SettingsFragment`'s own logic: [SettingsDefinitions] replaces the XML tree, this class
 * replaces `onSharedPreferenceChanged`/`updatePreferenceSummaries`/`updateCustomPreferences`.
 *
 * One instance per screen (top level or a nested group), exactly matching the legacy
 * self-navigating `settingsFragment` → `settingsFragment` destination: each level gets its own
 * `by viewModels()` instance, scoped to its own back-stack entry.
 *
 * The `OnSharedPreferenceChangeListener` is registered in [init] and unregistered in
 * [onCleared] rather than the legacy Fragment's `onResume`/`onPause` - this ViewModel's own
 * lifecycle (tied to the back-stack entry, ended only when this screen is popped) is already the
 * correct scope, so there is no extra plumbing to get right and no window where an external
 * preference change (another screen, a picker result, side-effect code) could be missed while
 * this screen is merely backgrounded rather than destroyed.
 */
class SettingsViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var rootKey: String? = null
    private var items: List<SettingsItem> = emptyList()
    private var imageCacheSizeBytes: Long = 0L

    /** One-shot effect delivery - a plain callback, not a Channel/Flow, matching
     *  [EditServerViewModel.onNavigate]'s precedent (issue #10 phase 5A3): deterministic
     *  regardless of which coroutine fires it, and a late callback after the Fragment's view is
     *  destroyed is a safe no-op rather than a crash. */
    internal var onEffect: (SettingsEffect) -> Unit = {}

    internal var preferencesProvider: () -> SharedPreferences = { Settings.preferences }

    /** The `Dispatchers.IO` hop lives in this default implementation, not in [clearAllDownloads]
     *  - matching [EditServerViewModel.connectionTester]'s precedent of keeping real dispatcher
     *  hops inside the seam so a test override is a plain suspend lambda with nothing to advance
     *  on a real thread pool `advanceUntilIdle()` cannot see. */
    internal var offlineTracksLoader: suspend () -> List<Track> = {
        withContext(Dispatchers.IO) { activeServerProvider.offlineMetaDatabase.trackDao().get() }
    }
    internal var downloadDeleter: suspend (List<Track>) -> Unit = { DownloadService.deleteAsync(it) }
    internal var imageCacheSizeReader: () -> Long = {
        val dir = albumArtDirectory
        if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    }
    internal var imageCacheClearer: () -> Unit = {
        val dir = albumArtDirectory
        if (dir.exists()) dir.walkTopDown().filter { it.isFile }.forEach { it.delete() }
    }
    internal var searchHistoryClearer: () -> Unit = {
        android.provider.SearchRecentSuggestions(
            getApplication(),
            SearchSuggestionProvider.AUTHORITY,
            SearchSuggestionProvider.MODE,
        ).clearHistory()
        RecentSearches(getApplication()).clear()
    }
    internal var cacheLocationApplier: (String) -> Unit = { path ->
        Settings.cacheLocationUri = path
        DownloadService.clearDownloads()
        Storage.reset()
        Storage.checkForErrorsWithCustomRoot()
    }
    internal var logPlanter: () -> Unit = { FileLoggerTree.plantToTimberForest() }
    internal var logUprooter: () -> Unit = { FileLoggerTree.uprootFromTimberForest() }
    internal var logFileNumberReader: () -> Int = { FileLoggerTree.getLogFileNumber() }
    internal var logFileSizeReader: () -> Long = { FileLoggerTree.getLogFileSizes() }
    internal var logFileDeleter: () -> Unit = { FileLoggerTree.deleteLogFiles() }
    internal var toastShower: (CharSequence) -> Unit = { Util.toast(it, getApplication()) }

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null) return@OnSharedPreferenceChangeListener
            Timber.d("Preference changed: %s", key)
            refresh(prefs)
        }

    init {
        preferencesProvider().registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onCleared() {
        preferencesProvider().unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    /** Resolves [rootKey] to this screen's title + item list (see [SettingsDefinitions]) and
     *  builds the initial row state - the Compose-era equivalent of
     *  `onCreatePreferences`/`onCreate`/`onViewCreated`'s combined setup. */
    fun load(rootKey: String?) {
        this.rootKey = rootKey
        val (titleRes, resolvedItems) = SettingsDefinitions.forRootKey(rootKey)
        items = resolvedItems
        Settings.normalizeBitrateQualitySettings()
        if (items.any { it is SettingsItem.Action && it.key == SettingsActionKeys.CLEAR_IMAGE_CACHE }) {
            imageCacheSizeBytes = imageCacheSizeReader()
        }
        _uiState.update { it.copy(titleRes = titleRes) }
        refresh(preferencesProvider())
    }

    private fun refresh(prefs: SharedPreferences) {
        val id3Online = prefs.getBoolean(KEY_ID3_TAGS, true)
        val customCacheLocation = prefs.getBoolean(KEY_CUSTOM_CACHE_LOCATION, false)
        val debugLogOn = prefs.getBoolean(KEY_DEBUG_LOG_TO_FILE, false)

        val rows = items.map { item ->
            when (item) {
                is SettingsItem.Toggle -> buildToggleRow(item, prefs, id3Online, debugLogOn)
                is SettingsItem.Choice -> SettingsRowState.ChoiceRow(
                    item,
                    prefs.getString(item.key, item.defaultValue) ?: item.defaultValue,
                )
                is SettingsItem.Navigation -> SettingsRowState.NavigationRow(item)
                is SettingsItem.Action -> buildActionRow(item, customCacheLocation, prefs)
                is SettingsItem.Category -> SettingsRowState.CategoryRow(item)
            }
        }
        _uiState.update { it.copy(rows = rows) }
    }

    private fun buildToggleRow(
        item: SettingsItem.Toggle,
        prefs: SharedPreferences,
        id3Online: Boolean,
        debugLogOn: Boolean,
    ): SettingsRowState.ToggleRow {
        val checked = prefs.getBoolean(item.key, defaultFor(item.key))
        return when (item.key) {
            KEY_ID3_TAGS_OFFLINE -> SettingsRowState.ToggleRow(
                item,
                checked = checked,
                enabled = id3Online,
            )
            KEY_DEBUG_LOG_TO_FILE -> SettingsRowState.ToggleRow(
                item,
                checked = checked,
                dynamicSummaryRes = if (debugLogOn) R.string.settings_debug_log_path else null,
                dynamicSummaryArgs = if (debugLogOn) {
                    listOf(ultrasonicDirectory, FileLoggerTree.FILENAME)
                } else {
                    emptyList()
                },
            )
            else -> SettingsRowState.ToggleRow(item, checked = checked)
        }
    }

    private fun buildActionRow(
        item: SettingsItem.Action,
        customCacheLocation: Boolean,
        prefs: SharedPreferences,
    ): SettingsRowState.ActionRow = when (item.key) {
        SettingsActionKeys.CACHE_LOCATION -> SettingsRowState.ActionRow(
            item,
            visible = customCacheLocation,
            dynamicSummaryRes = null,
            dynamicSummaryArgs = listOf(
                prefs.getString(item.key, "")?.toUri()?.path.orEmpty(),
            ),
        )
        SettingsActionKeys.CLEAR_IMAGE_CACHE -> SettingsRowState.ActionRow(
            item,
            dynamicSummaryRes = R.string.settings_clear_image_cache_summary,
            dynamicSummaryArgs = listOf(Util.formatBytes(imageCacheSizeBytes)),
        )
        else -> SettingsRowState.ActionRow(item)
    }

    private fun defaultFor(key: String): Boolean = when (key) {
        KEY_ID3_TAGS, KEY_ID3_TAGS_OFFLINE -> true
        else -> false
    }

    // --- Actions ---------------------------------------------------------------------------

    fun onToggle(item: SettingsItem.Toggle, checked: Boolean) {
        preferencesProvider().edit { putBoolean(item.key, checked) }
        when (item.key) {
            KEY_DEBUG_LOG_TO_FILE -> onDebugLogToggled(checked)
            KEY_CUSTOM_CACHE_LOCATION -> onCustomCacheLocationToggled(checked)
        }
    }

    private fun onDebugLogToggled(enabled: Boolean) {
        if (enabled) {
            logPlanter()
            Timber.i("Enabled debug logging to file")
            return
        }
        logUprooter()
        Timber.i("Disabled debug logging to file")
        val fileNum = logFileNumberReader()
        val fileSizeMb = ceil(logFileSizeReader().toDouble() / BYTES_PER_MB).toString()
        _uiState.update {
            it.copy(
                overlay = SettingsOverlay.Confirm(
                    titleRes = R.string.common_confirm,
                    messageRes = R.string.settings_debug_log_summary,
                    messageArgs = listOf(fileNum.toString(), fileSizeMb, ultrasonicDirectory),
                    confirmLabelRes = R.string.settings_debug_log_delete,
                    dismissLabelRes = R.string.settings_debug_log_keep,
                    action = ConfirmAction.DELETE_DEBUG_LOGS,
                ),
            )
        }
    }

    private fun onCustomCacheLocationToggled(enabled: Boolean) {
        if (enabled) {
            onEffect(SettingsEffect.LaunchCacheLocationPicker(Settings.cacheLocationUri))
        } else {
            if (Settings.cacheLocationUri != "") cacheLocationApplier("")
        }
    }

    fun onChoiceClick(item: SettingsItem.Choice) {
        val current = preferencesProvider().getString(item.key, item.defaultValue) ?: item.defaultValue
        _uiState.update { it.copy(overlay = SettingsOverlay.Choice(item, current)) }
    }

    fun onChoiceSelected(item: SettingsItem.Choice, value: String) {
        preferencesProvider().edit { putString(item.key, value) }
        _uiState.update { it.copy(overlay = SettingsOverlay.None) }
    }

    fun onOverlayDismiss() {
        _uiState.update { it.copy(overlay = SettingsOverlay.None) }
    }

    fun onActionClick(item: SettingsItem.Action) {
        when (item.key) {
            SettingsActionKeys.CLEAR_SEARCH_HISTORY -> {
                searchHistoryClearer()
                toastShower(stringOf(R.string.settings_search_history_cleared))
            }
            SettingsActionKeys.CLEAR_IMAGE_CACHE -> _uiState.update {
                it.copy(
                    overlay = SettingsOverlay.Confirm(
                        titleRes = R.string.common_confirm,
                        messageRes = R.string.settings_clear_image_cache_confirm,
                        confirmLabelRes = R.string.common_ok,
                        dismissLabelRes = R.string.common_cancel,
                        action = ConfirmAction.CLEAR_IMAGE_CACHE,
                    ),
                )
            }
            SettingsActionKeys.CLEAR_DOWNLOADS -> _uiState.update {
                it.copy(
                    overlay = SettingsOverlay.Confirm(
                        titleRes = R.string.common_confirm,
                        messageRes = R.string.settings_clear_downloads_confirm,
                        confirmLabelRes = R.string.common_ok,
                        dismissLabelRes = R.string.common_cancel,
                        action = ConfirmAction.CLEAR_DOWNLOADS,
                    ),
                )
            }
            SettingsActionKeys.CACHE_LOCATION -> {
                onEffect(SettingsEffect.LaunchCacheLocationPicker(Settings.cacheLocationUri))
            }
        }
    }

    fun onConfirm() {
        val overlay = _uiState.value.overlay
        _uiState.update { it.copy(overlay = SettingsOverlay.None) }
        if (overlay !is SettingsOverlay.Confirm) return
        when (overlay.action) {
            ConfirmAction.CLEAR_DOWNLOADS -> clearAllDownloads()
            ConfirmAction.CLEAR_IMAGE_CACHE -> clearImageCache()
            ConfirmAction.DELETE_DEBUG_LOGS -> deleteDebugLogs()
        }
    }

    private fun clearAllDownloads() {
        viewModelScope.launch {
            val tracks = offlineTracksLoader()
            downloadDeleter(tracks)
            toastShower(
                getApplication<Application>().resources.getQuantityString(
                    R.plurals.n_songs_deleted,
                    tracks.size,
                    tracks.size,
                ),
            )
        }
    }

    private fun clearImageCache() {
        imageCacheClearer()
        imageCacheSizeBytes = 0L
        refresh(preferencesProvider())
        toastShower(stringOf(R.string.settings_clear_image_cache_cleared))
    }

    private fun deleteDebugLogs() {
        logFileDeleter()
        Timber.i("Deleted debug log files")
        _uiState.update { it.copy(overlay = SettingsOverlay.Info(R.string.settings_debug_log_deleted)) }
    }

    /** Fed back by the Fragment once the system directory picker returns. [uri] is `null` on
     *  cancel or a permission-grant failure - the exact legacy `parseResult`/callback contract. */
    fun onCacheLocationPicked(uri: String?) {
        if (uri != null) {
            cacheLocationApplier(uri)
            refresh(preferencesProvider())
            return
        }
        _uiState.update { it.copy(overlay = SettingsOverlay.Info(R.string.settings_cache_location_error)) }
        if (Settings.cacheLocationUri == "") {
            preferencesProvider().edit { putBoolean(KEY_CUSTOM_CACHE_LOCATION, false) }
        }
    }

    private fun stringOf(resId: Int): String = getApplication<Application>().getString(resId)

    companion object {
        private const val BYTES_PER_MB = 1000.0 * 1000.0
    }
}
