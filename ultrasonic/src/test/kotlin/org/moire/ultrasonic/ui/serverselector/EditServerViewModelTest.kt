/*
 * EditServerViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.model.EditServerViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.robolectric.RobolectricTestRunner

/**
 * [EditServerViewModel] - a port of the legacy `EditServerFragment`'s own form/connection/save
 * logic (issue #10 phase 5A3), with the navigation contract fixed to resolve existing servers by
 * stable id instead of a screen-position-derived value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditServerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RobolectricUAppContext.install()
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun server(
        id: Int = 1,
        name: String = "Home",
        url: String = "https://home.example.com",
        username: String = "joseph",
        password: String = "secret",
        color: Int? = null,
        selfSigned: Boolean = false,
        plaintext: Boolean = false,
        jukebox: Boolean = false,
        minimumApiVersion: String? = null,
    ) = ServerSetting(
        id = id,
        index = id,
        name = name,
        url = url,
        color = color,
        userName = username,
        password = password,
        jukeboxByDefault = jukebox,
        allowSelfSignedCertificate = selfSigned,
        forcePlainTextPassword = plaintext,
        musicFolderId = null,
        minimumApiVersion = minimumApiVersion,
    )

    private fun vm(
        existingServers: Map<Int, ServerSetting> = emptyMap(),
        activeServerId: Int = -1,
        tester: suspend (ServerSetting) -> ServerSetting = { ServerSetting() },
    ): EditServerViewModel {
        var currentActiveId = activeServerId
        return EditServerViewModel(app).apply {
            loadExistingServer = { id -> MutableLiveData(existingServers[id]) }
            connectionTester = tester
            getActiveServerId = { currentActiveId }
            setActiveServer = { currentActiveId = it }
            saveNewServer = { setting, onSaved -> setting.id = 100; onSaved(setting) }
            updateServer = { }
            resetMusicService = { }
            publishActiveServerChanged = { }
            errorMessageFor = { it.message ?: "error" }
        }
    }

    // --- New mode: initial state / field edits ------------------------------------------------

    @Test
    fun `new mode starts with the virgin onboarding defaults`() {
        val model = vm()
        model.load(EditServerMode.New)
        val state = model.uiState.value
        assertTrue(state.isNewMode)
        assertEquals("", state.name)
        assertEquals("http://", state.address)
        assertEquals("", state.username)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
    }

    @Test
    fun `editing fields updates the state immediately`() {
        val model = vm()
        model.load(EditServerMode.New)
        model.onAddressChange("https://my.server.com")
        model.onUsernameChange("joseph")
        model.onPasswordChange("hunter2")
        val state = model.uiState.value
        assertEquals("https://my.server.com", state.address)
        assertEquals("joseph", state.username)
        assertEquals("hunter2", state.password)
    }

    // --- Validation --------------------------------------------------------------------------

    @Test
    fun `a blank address fails validation with a required error`() {
        val model = vm()
        model.load(EditServerMode.New)
        model.onAddressChange("")
        model.onUsernameChange("joseph")
        model.onConnectOrSave()
        assertEquals(EditServerFieldError.REQUIRED, model.uiState.value.addressError)
    }

    @Test
    fun `a malformed address fails validation with an invalid-url error`() {
        val model = vm()
        model.load(EditServerMode.New)
        model.onAddressChange("not a url")
        model.onUsernameChange("joseph")
        model.onConnectOrSave()
        assertEquals(EditServerFieldError.INVALID_URL, model.uiState.value.addressError)
    }

    @Test
    fun `a blank username fails validation with a required error`() {
        val model = vm()
        model.load(EditServerMode.New)
        model.onAddressChange("https://my.server.com")
        model.onUsernameChange("")
        model.onConnectOrSave()
        assertEquals(EditServerFieldError.REQUIRED, model.uiState.value.usernameError)
    }

    @Test
    fun `address focus loss trims surrounding spaces and a trailing slash`() {
        val model = vm()
        model.load(EditServerMode.New)
        model.onAddressChange("  https://my.server.com/  ")
        model.onAddressFocusLost()
        assertEquals("https://my.server.com", model.uiState.value.address)
    }

    @Test
    fun `the name auto-fills from the url host when blank, in either mode`() = runTest {
        val model = vm()
        model.load(EditServerMode.New)
        model.onAddressChange("https://my.server.com")
        model.onUsernameChange("joseph")
        model.onConnectOrSave()
        advanceUntilIdle()
        assertEquals("my.server.com", model.uiState.value.name)
    }

    // --- New mode: Connect flow ----------------------------------------------------------------

    @Test
    fun `a successful connect saves the new server, activates it, and navigates home`() = runTest {
        var activated: Int? = null
        val model = vm(tester = { ServerSetting().apply { jukeboxSupport = true } })
        model.setActiveServer = { activated = it }
        model.load(EditServerMode.New)
        model.onAddressChange("https://my.server.com")
        model.onUsernameChange("joseph")

        val events = mutableListOf<EditServerNavigationEvent>()
        model.onNavigate = { events.add(it) }

        model.onConnectOrSave()
        advanceUntilIdle()

        assertEquals(100, activated)
        assertEquals(listOf(EditServerNavigationEvent.NavigateHome), events)
    }

    @Test
    fun `a failed connect shows the error and does not save anything`() = runTest {
        var saveCalls = 0
        val model = vm(tester = { throw IOException("down") })
        model.saveNewServer = { _, _ -> saveCalls++ }
        model.load(EditServerMode.New)
        model.onAddressChange("https://my.server.com")
        model.onUsernameChange("joseph")
        model.onConnectOrSave()
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(ConnectionTestState.FAILED, state.connectionTestState)
        assertEquals(0, saveCalls)
        // The screen stays retryable: fields are untouched, not cleared.
        assertEquals("https://my.server.com", state.address)
        assertEquals("joseph", state.username)
    }

    @Test
    fun `a cancelled connection test is not mistaken for a failure`() = runTest {
        val model = vm(tester = { throw CancellationException("scope died") })
        model.load(EditServerMode.New)
        model.onAddressChange("https://my.server.com")
        model.onUsernameChange("joseph")
        model.onConnectOrSave()
        runCatching { advanceUntilIdle() }
        assertFalse(model.uiState.value.connectionTestState == ConnectionTestState.FAILED)
    }

    @Test
    fun `a second Connect tap while a test is already running is ignored`() = runTest {
        var testCalls = 0
        val model = vm(tester = { testCalls++; delay(1000); ServerSetting() })
        model.load(EditServerMode.New)
        model.onAddressChange("https://my.server.com")
        model.onUsernameChange("joseph")
        model.onConnectOrSave()
        model.onConnectOrSave()
        advanceUntilIdle()
        assertEquals(1, testCalls)
    }

    // --- Existing mode: load ---------------------------------------------------------------

    @Test
    fun `loading an existing server populates every field exactly`() {
        val setting = server(id = 7, name = "Office", url = "https://office.example.com", username = "jd", color = 123)
        val model = vm(existingServers = mapOf(7 to setting))
        model.load(EditServerMode.Existing(7))

        val state = model.uiState.value
        assertFalse(state.isNewMode)
        assertEquals("Office", state.name)
        assertEquals("https://office.example.com", state.address)
        assertEquals("jd", state.username)
        assertEquals("secret", state.password)
        assertEquals(123, state.color)
    }

    @Test
    fun `opening the second configured server loads its own data, not the first's`() {
        val first = server(id = 1, name = "Home")
        val second = server(id = 2, name = "Away")
        val model = vm(existingServers = mapOf(1 to first, 2 to second))
        model.load(EditServerMode.Existing(2))
        assertEquals("Away", model.uiState.value.name)
    }

    @Test
    fun `advanced auto-expands when self-signed or jukebox is already active, not for plaintext`() {
        val selfSignedServer = server(id = 1, selfSigned = true)
        val plaintextOnlyServer = server(id = 2, plaintext = true)

        val model1 = vm(existingServers = mapOf(1 to selfSignedServer))
        model1.load(EditServerMode.Existing(1))
        assertTrue(model1.uiState.value.advancedExpanded)

        val model2 = vm(existingServers = mapOf(2 to plaintextOnlyServer))
        model2.load(EditServerMode.Existing(2))
        // Matches the legacy inconsistency exactly (not fixed in this phase - see the audit).
        assertFalse(model2.uiState.value.advancedExpanded)
    }

    @Test
    fun `loading a server with a stale minimumApiVersion clears it`() {
        var updatedSetting: ServerSetting? = null
        val setting = server(id = 1, minimumApiVersion = "1.16.0")
        val model = vm(existingServers = mapOf(1 to setting))
        model.updateServer = { updatedSetting = it }
        model.load(EditServerMode.Existing(1))
        assertNull(updatedSetting?.minimumApiVersion)
    }

    @Test
    fun `loading a server with no minimumApiVersion does not trigger an update`() {
        var updateCalls = 0
        val setting = server(id = 1, minimumApiVersion = null)
        val model = vm(existingServers = mapOf(1 to setting))
        model.updateServer = { updateCalls++ }
        model.load(EditServerMode.Existing(1))
        assertEquals(0, updateCalls)
    }

    @Test
    fun `clearing minimumApiVersion resets the music service only if this server is active`() {
        var resetCalls = 0
        val setting = server(id = 1, minimumApiVersion = "1.16.0")
        val model = vm(existingServers = mapOf(1 to setting), activeServerId = 1)
        model.resetMusicService = { resetCalls++ }
        model.load(EditServerMode.Existing(1))
        assertEquals(1, resetCalls)
    }

    @Test
    fun `clearing minimumApiVersion for a non-active server does not reset the music service`() {
        var resetCalls = 0
        val setting = server(id = 1, minimumApiVersion = "1.16.0")
        val model = vm(existingServers = mapOf(1 to setting), activeServerId = 999)
        model.resetMusicService = { resetCalls++ }
        model.load(EditServerMode.Existing(1))
        assertEquals(0, resetCalls)
    }

    // --- Existing mode: Save ------------------------------------------------------------------

    @Test
    fun `saving an existing active server resets the music service and publishes the RxBus change`() {
        val calls = mutableListOf<String>()
        val setting = server(id = 1)
        val model = vm(existingServers = mapOf(1 to setting), activeServerId = 1)
        model.resetMusicService = { calls.add("reset") }
        model.publishActiveServerChanged = { calls.add("publish:${it.id}") }
        model.updateServer = { calls.add("update:${it.id}") }
        model.load(EditServerMode.Existing(1))
        model.onConnectOrSave()
        // Exact order: update -> reset -> publish.
        assertEquals(listOf("update:1", "reset", "publish:1"), calls)
    }

    @Test
    fun `saving a non-active existing server does not reset the music service or publish`() {
        var resetCalls = 0
        var publishCalls = 0
        val setting = server(id = 1)
        val model = vm(existingServers = mapOf(1 to setting), activeServerId = 999)
        model.resetMusicService = { resetCalls++ }
        model.publishActiveServerChanged = { publishCalls++ }
        model.load(EditServerMode.Existing(1))
        model.onConnectOrSave()
        assertEquals(0, resetCalls)
        assertEquals(0, publishCalls)
    }

    @Test
    fun `save never runs a connection test`() = runTest {
        var testCalls = 0
        val setting = server(id = 1)
        val model = vm(existingServers = mapOf(1 to setting), tester = { testCalls++; ServerSetting() })
        model.load(EditServerMode.Existing(1))
        model.onConnectOrSave()
        advanceUntilIdle()
        assertEquals(0, testCalls)
    }

    @Test
    fun `saving navigates up, not home`() = runTest {
        val events = mutableListOf<EditServerNavigationEvent>()
        val setting = server(id = 1)
        val model = vm(existingServers = mapOf(1 to setting), activeServerId = 999)
        model.onNavigate = { events.add(it) }
        model.load(EditServerMode.Existing(1))
        model.onConnectOrSave()
        advanceUntilIdle()
        assertEquals(listOf(EditServerNavigationEvent.NavigateUp), events)
    }

    // --- Discard / dirty-check ------------------------------------------------------------------

    @Test
    fun `requestBack navigates immediately when nothing changed`() = runTest {
        val events = mutableListOf<EditServerNavigationEvent>()
        val model = vm()
        model.onNavigate = { events.add(it) }
        model.load(EditServerMode.New)
        model.requestBack()
        advanceUntilIdle()
        assertEquals(listOf(EditServerNavigationEvent.NavigateUp), events)
        assertFalse(model.uiState.value.pendingDiscard)
    }

    @Test
    fun `requestBack shows the discard confirmation when fields changed, in new mode`() {
        val model = vm()
        model.load(EditServerMode.New)
        model.onUsernameChange("joseph")
        model.requestBack()
        assertTrue(model.uiState.value.pendingDiscard)
    }

    @Test
    fun `requestBack shows the discard confirmation when fields changed, in existing mode`() {
        val setting = server(id = 1, name = "Home")
        val model = vm(existingServers = mapOf(1 to setting))
        model.load(EditServerMode.Existing(1))
        model.onNameChange("Renamed")
        model.requestBack()
        assertTrue(model.uiState.value.pendingDiscard)
    }

    @Test
    fun `typing a value and deleting it back is not considered a change`() {
        val setting = server(id = 1, name = "Home")
        val model = vm(existingServers = mapOf(1 to setting))
        model.load(EditServerMode.Existing(1))
        model.onNameChange("Renamed")
        model.onNameChange("Home")
        model.requestBack()
        assertFalse(model.uiState.value.pendingDiscard)
    }

    @Test
    fun `changing only the color is never considered a change, matching the legacy check`() {
        val setting = server(id = 1)
        val model = vm(existingServers = mapOf(1 to setting))
        model.load(EditServerMode.Existing(1))
        model.onColorPicked(0xFF00FF)
        model.requestBack()
        assertFalse(model.uiState.value.pendingDiscard)
    }

    @Test
    fun `cancelDiscard clears pendingDiscard without navigating`() = runTest {
        val events = mutableListOf<EditServerNavigationEvent>()
        val model = vm()
        model.load(EditServerMode.New)
        model.onUsernameChange("joseph")
        model.requestBack()
        model.onNavigate = { events.add(it) }
        model.cancelDiscard()
        advanceUntilIdle()
        assertFalse(model.uiState.value.pendingDiscard)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `confirmDiscard clears pendingDiscard and navigates up`() = runTest {
        val events = mutableListOf<EditServerNavigationEvent>()
        val model = vm()
        model.load(EditServerMode.New)
        model.onUsernameChange("joseph")
        model.requestBack()
        model.onNavigate = { events.add(it) }
        model.confirmDiscard()
        advanceUntilIdle()
        assertFalse(model.uiState.value.pendingDiscard)
        assertEquals(listOf(EditServerNavigationEvent.NavigateUp), events)
    }
}
