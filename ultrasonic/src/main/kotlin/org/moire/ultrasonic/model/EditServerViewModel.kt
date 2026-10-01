/*
 * EditServerViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import java.net.MalformedURLException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.ui.serverselector.ConnectionTestState
import org.moire.ultrasonic.ui.serverselector.EditServerFieldError
import org.moire.ultrasonic.ui.serverselector.EditServerMode
import org.moire.ultrasonic.ui.serverselector.EditServerNavigationEvent
import org.moire.ultrasonic.ui.serverselector.EditServerUiState
import org.moire.ultrasonic.util.CommunicationError
import timber.log.Timber

/** The legacy `getFields()`'s "virgin new-server" baseline (`server_edit.xml`'s literal seed
 *  text) - compared against in [EditServerViewModel.hasUnsavedChanges] for new-server mode. */
private const val VIRGIN_ADDRESS = "http://"

/**
 * Owns the Compose Edit Server state (issue #10 phase 5A3) - a port of the legacy
 * `EditServerFragment`'s own form/connection/save logic, preserving every documented behavior
 * (validation rules, URL normalization, the minimumApiVersion reset, the exact Connect/Save
 * sequencing including the active-server reset+RxBus-publish ordering) with one deliberate fix:
 * navigation now resolves an existing server by its stable [org.moire.ultrasonic.data.ServerSetting
 * .id] (see [EditServerMode.Existing]), never a screen-position-derived value.
 *
 * All real side effects (`EditServerModel.queryFeatureSupport`, `ServerSettingsModel.saveNewItem`
 * /`updateItem`, `ActiveServerProvider.setActiveServerById`, `MusicServiceFactory
 * .resetMusicService`, `RxBus.activeServerChangedPublisher`) go through test seams below, exactly
 * matching the project's established `internal var xxxLoader` pattern.
 *
 * Unlike the legacy Fragment - whose new-server success callback ran inside `ServerSettingsModel`
 * 's own Fragment-lifecycle-independent `appScope`, with no exception handler, risking an uncaught
 * crash if `findNavController()` or the DB write failed after the Fragment's view was destroyed
 * (the phase 5A3 audit's documented "scope-crossing hazard", the same bug class issue #10 phase
 * 4M2 found) - every coroutine here runs in [viewModelScope], and navigation is a one-shot
 * [Channel] event the Fragment only acts on while its own view is alive, so a stale emission is
 * simply never collected rather than crashing.
 */
class EditServerViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val serverSettingsModel: ServerSettingsModel by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val editServerModel = EditServerModel(application)

    private val _uiState = MutableStateFlow(EditServerUiState())
    val uiState: StateFlow<EditServerUiState> = _uiState.asStateFlow()

    /** One-shot navigation outcome, delivered synchronously - a plain callback rather than a
     *  Flow/Channel, so it fires deterministically regardless of which coroutine (including
     *  `ServerSettingsModel.saveNewItem`'s own Fragment-lifecycle-independent `appScope`, after a
     *  `withContext(Dispatchers.Main)` hop) calls it. The host (`EditServerFragment`) assigns this
     *  only while its view is alive and clears it in `onDestroyView`, so a late callback after the
     *  view is destroyed is simply a no-op rather than risking a crash on a stale
     *  `findNavController()` - the exact scope-crossing hazard class this phase's audit flagged. */
    internal var onNavigate: (EditServerNavigationEvent) -> Unit = {}

    /** The real entity being edited/created - mutated in place and persisted as-is, preserving
     *  every field the form doesn't expose (musicFolderId, videoSupport, chatSupport, ...),
     *  exactly like the legacy screen's own `currentServerSetting`. */
    private var currentServerSetting = ServerSetting()

    /** The dirty-check baseline: the loaded server's original field values (existing mode) or the
     *  virgin onboarding defaults (new mode) - compared against current [uiState] fields in
     *  [hasUnsavedChanges], exactly matching the legacy `areFieldsChanged()`. */
    private var baseline = EditServerUiState()

    private var existingServerLiveData: LiveData<ServerSetting?>? = null
    private val existingServerObserver = Observer<ServerSetting?> { onExistingServerLoaded(it) }

    /** `EditServerModel.queryFeatureSupport(setting).flowOn(Dispatchers.IO).collect { ... }`,
     *  unchanged - returns a transient [ServerSetting] carrying only the probed
     *  jukeboxSupport/videoSupport, exactly like the legacy `testSetting`. Test seam. */
    internal var connectionTester: suspend (ServerSetting) -> ServerSetting = { setting ->
        val testSetting = ServerSetting()
        editServerModel.queryFeatureSupport(setting).flowOn(Dispatchers.IO).collect {
            editServerModel.storeFeatureSupport(testSetting, it)
            Timber.w("${it.type} support: ${it.supported}")
        }
        testSetting
    }

    /** `serverSettingsModel.saveNewItem(setting, onSaved)`, unchanged. Test seam. */
    internal var saveNewServer: (ServerSetting, (ServerSetting) -> Unit) -> Unit =
        { setting, onSaved -> serverSettingsModel.saveNewItem(setting, onSaved) }

    /** `serverSettingsModel.updateItem(setting)`, unchanged. Test seam. */
    internal var updateServer: (ServerSetting) -> Unit = { serverSettingsModel.updateItem(it) }

    /** `activeServerProvider.setActiveServerById(id)`, unchanged. Test seam. */
    internal var setActiveServer: (Int) -> Unit = { activeServerProvider.setActiveServerById(it) }

    /** `activeServerProvider.getActiveServer().id`, unchanged (not the static
     *  `getActiveServerId()` - matching the legacy call exactly). Test seam. */
    internal var getActiveServerId: () -> Int = { activeServerProvider.getActiveServer().id }

    /** `MusicServiceFactory.resetMusicService()`, unchanged. Test seam. */
    internal var resetMusicService: () -> Unit = { MusicServiceFactory.resetMusicService() }

    /** `RxBus.activeServerChangedPublisher.onNext(setting)`, unchanged. Test seam. */
    internal var publishActiveServerChanged: (ServerSetting) -> Unit =
        { RxBus.activeServerChangedPublisher.onNext(it) }

    /** `serverSettingsModel.getServerSettingById(id)`, unchanged. Test seam. */
    internal var loadExistingServer: (Int) -> LiveData<ServerSetting?> =
        { serverSettingsModel.getServerSettingById(it) }

    /** `CommunicationError.getErrorMessage(error)`, unchanged. Test seam. */
    internal var errorMessageFor: (Throwable) -> String = { CommunicationError.getErrorMessage(it) }

    /** Loads the given mode. New mode resets to the virgin onboarding defaults synchronously;
     *  existing mode observes the server's LiveData, exactly mirroring the legacy Fragment's own
     *  per-mode `onViewCreated` branch. */
    fun load(mode: EditServerMode) {
        when (mode) {
            is EditServerMode.New -> {
                currentServerSetting = ServerSetting()
                val fresh = EditServerUiState(mode = mode)
                baseline = fresh
                _uiState.value = fresh
            }
            is EditServerMode.Existing -> {
                _uiState.value = EditServerUiState(mode = mode, isLoading = true)
                existingServerLiveData?.removeObserver(existingServerObserver)
                val liveData = loadExistingServer(mode.serverId)
                existingServerLiveData = liveData
                liveData.observeForever(existingServerObserver)
            }
        }
    }

    override fun onCleared() {
        existingServerLiveData?.removeObserver(existingServerObserver)
    }

    private fun onExistingServerLoaded(setting: ServerSetting?) {
        if (setting == null) return
        currentServerSetting = setting

        val shouldExpandAdvanced = setting.allowSelfSignedCertificate || setting.jukeboxByDefault
        val loaded = EditServerUiState(
            mode = EditServerMode.Existing(setting.id),
            isLoading = false,
            name = setting.name,
            address = setting.url,
            username = setting.userName,
            password = setting.password,
            color = setting.color,
            allowSelfSignedCertificate = setting.allowSelfSignedCertificate,
            forcePlainTextPassword = setting.forcePlainTextPassword,
            jukeboxByDefault = setting.jukeboxByDefault,
            advancedExpanded = shouldExpandAdvanced,
        )
        baseline = loaded
        _uiState.value = loaded

        // Remove the minimum API version so it can be detected again - legacy behavior,
        // unchanged (see the phase 5A3 audit for why).
        if (setting.minimumApiVersion != null) {
            setting.minimumApiVersion = null
            updateServer(setting)
            if (getActiveServerId() == setting.id) {
                resetMusicService()
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }
    fun onAddressChange(value: String) = _uiState.update { it.copy(address = value, addressError = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, usernameError = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }
    fun onSelfSignedChange(checked: Boolean) = _uiState.update { it.copy(allowSelfSignedCertificate = checked) }
    fun onPlaintextChange(checked: Boolean) = _uiState.update { it.copy(forcePlainTextPassword = checked) }
    fun onJukeboxChange(checked: Boolean) = _uiState.update { it.copy(jukeboxByDefault = checked) }
    fun onColorPicked(color: Int) = _uiState.update { it.copy(color = color) }

    fun onToggleAdvanced() = _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }

    /** `correctServerAddress()`, unchanged: trims only literal space/`/` from both ends. Called on
     *  address-field focus loss, matching the legacy `onFocusChangeListener`. */
    fun onAddressFocusLost() {
        _uiState.update { it.copy(address = it.address.trim(' ', '/')) }
    }

    /**
     * `getFields()`, ported rule-for-rule:
     * 1. Address required.
     * 2. Address must parse as a URL with a non-blank host (requires a scheme).
     * 3. Name auto-fills from the URL host when hidden (new mode) or blank.
     * 4. Username required. Password has no validation of its own.
     */
    private fun validate(): Boolean {
        val state = _uiState.value
        val normalizedAddress = state.address.trim(' ', '/')
        var isValid = true
        var url: URL? = null
        var addressError: EditServerFieldError? = null

        if (normalizedAddress.isBlank()) {
            addressError = EditServerFieldError.REQUIRED
            isValid = false
        } else {
            try {
                url = URL(normalizedAddress)
                if (normalizedAddress != normalizedAddress.trim(' ') || url.host.isNullOrBlank()) {
                    throw MalformedURLException()
                }
            } catch (expected: MalformedURLException) {
                addressError = EditServerFieldError.INVALID_URL
                isValid = false
            }
        }

        var name = state.name
        if ((state.isNewMode || name.isBlank()) && isValid && url != null) {
            name = url.host
        }

        val usernameError = if (state.username.isBlank()) EditServerFieldError.REQUIRED else null
        if (usernameError != null) isValid = false

        _uiState.update {
            it.copy(
                address = normalizedAddress,
                name = name,
                addressError = addressError,
                usernameError = usernameError,
            )
        }
        return isValid
    }

    /** Mutates and returns [currentServerSetting] with the current form state, mirroring
     *  `getFields()`'s own in-place mutation of `currentServerSetting` - every field the form
     *  doesn't expose (musicFolderId, videoSupport, ...) is preserved untouched. */
    private fun applyFieldsToCurrentServerSetting(): ServerSetting {
        val state = _uiState.value
        return currentServerSetting.apply {
            name = state.name
            url = state.address
            color = state.color
            userName = state.username
            password = state.password
            allowSelfSignedCertificate = state.allowSelfSignedCertificate
            forcePlainTextPassword = state.forcePlainTextPassword
            jukeboxByDefault = state.jukeboxByDefault
        }
    }

    /** `areFieldsChanged()`, ported exactly: compares current field values against [baseline],
     *  not a "has anything been touched" flag - typing a value and deleting it back correctly
     *  reports no change. Color is deliberately never compared, matching the legacy check. */
    private fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        if (state.isNewMode) {
            return state.name.isNotBlank() ||
                state.address != VIRGIN_ADDRESS ||
                state.username.isNotBlank() ||
                state.password.isNotBlank()
        }
        return state.name != baseline.name ||
            state.address != baseline.address ||
            state.username != baseline.username ||
            state.password != baseline.password ||
            state.allowSelfSignedCertificate != baseline.allowSelfSignedCertificate ||
            state.forcePlainTextPassword != baseline.forcePlainTextPassword ||
            state.jukeboxByDefault != baseline.jukeboxByDefault
    }

    /**
     * Back requested - from either the header's own back action or the system Back gesture (both
     * now route through here, fixing the phase 5A3 audit's documented gap where the legacy shared
     * toolbar's back arrow bypassed the leave-confirmation entirely). Shows the discard sheet only
     * when there are unsaved changes, exactly matching `finishActivity()`'s own branch.
     */
    fun requestBack() {
        if (hasUnsavedChanges()) {
            _uiState.update { it.copy(pendingDiscard = true) }
        } else {
            onNavigate(EditServerNavigationEvent.NavigateUp)
        }
    }

    fun cancelDiscard() = _uiState.update { it.copy(pendingDiscard = false) }

    fun confirmDiscard() {
        _uiState.update { it.copy(pendingDiscard = false) }
        onNavigate(EditServerNavigationEvent.NavigateUp)
    }

    /** Connect (new mode) or Save (existing mode) - the screen's single primary action, matching
     *  which one legacy button this is standing in for. */
    fun onConnectOrSave() {
        if (!validate()) return
        when (_uiState.value.mode) {
            is EditServerMode.New -> connect()
            is EditServerMode.Existing -> save()
        }
    }

    /** The standalone Test button (existing mode only) - never saves, matching the legacy
     *  `testButton`'s own listener, which calls `testConnection()` with no `onSuccess`. */
    fun onTestConnection() {
        if (!validate()) return
        runConnectionTest(onSuccess = {})
    }

    /** New-server Connect: validate -> test -> on success, save + activate + go Home. Guards
     *  against a second tap starting a concurrent test while one is already running (prevention
     *  the legacy screen never had, added deliberately per the phase 5A3 brief). */
    private fun connect() {
        if (_uiState.value.isTesting) return
        runConnectionTest {
            val setting = applyFieldsToCurrentServerSetting()
            saveNewServer(setting) { saved ->
                setActiveServer(saved.id)
                onNavigate(EditServerNavigationEvent.NavigateHome)
            }
        }
    }

    /** `saveButton`'s existing-mode listener, verbatim order: update -> (only if this is the
     *  active server) reset the music service, then publish the RxBus change -> navigate up. No
     *  connection test runs on Save - matching the legacy screen exactly. */
    private fun save() {
        val setting = applyFieldsToCurrentServerSetting()
        updateServer(setting)
        if (getActiveServerId() == setting.id) {
            resetMusicService()
            publishActiveServerChanged(setting)
        }
        onNavigate(EditServerNavigationEvent.NavigateUp)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runConnectionTest(onSuccess: () -> Unit) {
        val setting = applyFieldsToCurrentServerSetting()
        _uiState.update { it.copy(connectionTestState = ConnectionTestState.TESTING) }

        viewModelScope.launch {
            try {
                val result = connectionTester(setting)
                setting.videoSupport = result.videoSupport
                setting.jukeboxSupport = result.jukeboxSupport

                _uiState.update {
                    it.copy(
                        connectionTestState = ConnectionTestState.SUCCESS,
                        connectionErrorMessage = null,
                        jukeboxSupported = result.jukeboxSupport,
                        // Advanced settings aren't shown on the first-connection screen; only
                        // auto-expand here when editing an existing server, matching the legacy
                        // `if (advancedToggle?.isVisible == true)` guard.
                        advancedExpanded = it.advancedExpanded ||
                            (!it.isNewMode && result.jukeboxSupport == false),
                    )
                }
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                Timber.w(expected)
                _uiState.update {
                    it.copy(
                        connectionTestState = ConnectionTestState.FAILED,
                        connectionErrorMessage = errorMessageFor(expected),
                    )
                }
            }
        }
    }
}
