/*
 * ServerSelectorViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.model.ServerSelectorViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.robolectric.RobolectricTestRunner

/**
 * [ServerSelectorViewModel] - a straight port of the legacy `ServerSelectorFragment`'s own logic
 * (post-issue-#10 residual migration, phase 5A2), including the delete sequence's own already-
 * flagged quirk (`deleteMetaDatabase` is called with the pre-delete active id, not necessarily the
 * deleted server's own id - preserved, not fixed, per the phase 5A2 audit).
 */
@RunWith(RobolectricTestRunner::class)
class ServerSelectorViewModelTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        app = ApplicationProvider.getApplicationContext()
    }

    private fun server(id: Int, name: String, color: Int? = null) = ServerSetting(
        id = id,
        index = id,
        name = name,
        url = "https://$name.example.com",
        color = color,
        userName = "user",
        password = "pass",
        jukeboxByDefault = false,
        allowSelfSignedCertificate = false,
        forcePlainTextPassword = false,
        musicFolderId = null,
        minimumApiVersion = null,
    )

    private fun vm(
        servers: List<ServerSetting> = emptyList(),
        activeId: Int = ActiveServerProvider.OFFLINE_DB_ID,
        onSetActiveServer: (Int) -> Unit = {},
        onDeleteServerById: (Int) -> Unit = {},
        onDeleteMetaDatabase: (Int) -> Unit = {},
    ) = ServerSelectorViewModel(app).apply {
        serverListLoader = { MutableLiveData(servers) }
        activeServerIdReader = { activeId }
        setActiveServer = onSetActiveServer
        deleteServerById = onDeleteServerById
        deleteMetaDatabase = onDeleteMetaDatabase
    }

    // --- Load / rows -----------------------------------------------------------------------

    @Test
    fun `the default state has no rows until reload is called`() {
        val model = vm()
        assertTrue(model.uiState.value.rows.isEmpty())
    }

    @Test
    fun `reload always synthesizes the Offline row first, regardless of DB order`() {
        val model = vm(servers = listOf(server(1, "Home"), server(2, "Away")))
        model.reload()
        val rows = model.uiState.value.rows
        assertEquals(3, rows.size)
        assertTrue(rows[0].isOffline)
        assertEquals(ActiveServerProvider.OFFLINE_DB_ID, rows[0].id)
        assertEquals("Home", rows[1].name)
        assertEquals("Away", rows[2].name)
    }

    @Test
    fun `each row's id is the real ServerSetting id, independent of its on-screen position`() {
        // issue #10 phase 5A3: navigation to Edit Server now uses this stable id directly,
        // not the row's on-screen position (the legacy index-based contract this replaced).
        val model = vm(servers = listOf(server(7, "Home"), server(3, "Away")))
        model.reload()
        val rows = model.uiState.value.rows
        assertEquals(ActiveServerProvider.OFFLINE_DB_ID, rows[0].id)
        assertEquals(7, rows[1].id)
        assertEquals(3, rows[2].id)
    }

    @Test
    fun `the offline row never shows a description, a real server's url does`() {
        val model = vm(servers = listOf(server(1, "Home")))
        model.reload()
        val rows = model.uiState.value.rows
        assertNull(rows[0].description)
        assertEquals("https://Home.example.com", rows[1].description)
    }

    @Test
    fun `the row matching the active server id is marked active, others are not`() {
        val model = vm(servers = listOf(server(1, "Home"), server(2, "Away")), activeId = 2)
        model.reload()
        val rows = model.uiState.value.rows
        assertFalse(rows[0].isActive) // Offline
        assertFalse(rows[1].isActive) // Home
        assertTrue(rows[2].isActive) // Away
    }

    @Test
    fun `offline is marked active when it is the active server id`() {
        val model = vm(servers = listOf(server(1, "Home")), activeId = ActiveServerProvider.OFFLINE_DB_ID)
        model.reload()
        assertTrue(model.uiState.value.rows[0].isActive)
    }

    // --- selectServer ------------------------------------------------------------------------

    @Test
    fun `selecting a server calls setActiveServer with that server's id`() {
        var calledWith: Int? = null
        val model = vm(servers = listOf(server(1, "Home")), onSetActiveServer = { calledWith = it })
        model.reload()
        model.selectServer(model.uiState.value.rows[1])
        assertEquals(1, calledWith)
    }

    // --- Delete request / cancel / confirm ----------------------------------------------------

    @Test
    fun `requestDelete sets pendingDelete to that row`() {
        val model = vm(servers = listOf(server(1, "Home")))
        model.reload()
        val row = model.uiState.value.rows[1]
        model.requestDelete(row)
        assertEquals(row, model.uiState.value.pendingDelete)
    }

    @Test
    fun `cancelDelete clears pendingDelete without deleting anything`() {
        var deleteCalls = 0
        val model = vm(servers = listOf(server(1, "Home")), onDeleteServerById = { deleteCalls++ })
        model.reload()
        model.requestDelete(model.uiState.value.rows[1])
        model.cancelDelete()
        assertNull(model.uiState.value.pendingDelete)
        assertEquals(0, deleteCalls)
    }

    @Test
    fun `reopening delete for a different server shows the correct new target`() {
        val model = vm(servers = listOf(server(1, "Home"), server(2, "Away")))
        model.reload()
        model.requestDelete(model.uiState.value.rows[1])
        model.cancelDelete()
        model.requestDelete(model.uiState.value.rows[2])
        assertEquals("Away", model.uiState.value.pendingDelete?.name)
    }

    @Test
    fun `confirming delete dismisses the confirmation immediately`() {
        val model = vm(servers = listOf(server(1, "Home")))
        model.reload()
        model.requestDelete(model.uiState.value.rows[1])
        model.confirmDelete()
        assertNull(model.uiState.value.pendingDelete)
    }

    @Test
    fun `confirming delete of a non-active server does not switch to offline`() {
        var setActiveCalls = 0
        val model = vm(
            servers = listOf(server(1, "Home"), server(2, "Away")),
            activeId = 1,
            onSetActiveServer = { setActiveCalls++ },
        )
        model.reload()
        model.requestDelete(model.uiState.value.rows[2]) // "Away", not active
        model.confirmDelete()
        assertEquals(0, setActiveCalls)
    }

    @Test
    fun `confirming delete of the active server switches to offline before deleting`() {
        val calls = mutableListOf<String>()
        val model = vm(
            servers = listOf(server(1, "Home")),
            activeId = 1,
            onSetActiveServer = { calls.add("setActive:$it") },
            onDeleteServerById = { calls.add("delete:$it") },
            onDeleteMetaDatabase = { calls.add("deleteMeta:$it") },
        )
        model.reload()
        model.requestDelete(model.uiState.value.rows[1]) // "Home", active
        model.confirmDelete()
        assertEquals(
            listOf(
                "setActive:${ActiveServerProvider.OFFLINE_DB_ID}",
                "delete:1",
                "deleteMeta:1",
            ),
            calls,
        )
    }

    @Test
    fun `deleteMetaDatabase receives the pre-delete active id, matching the legacy quirk exactly`() {
        // Regression guard for the audit's documented (not fixed) behavior: deleting a
        // non-active server clears the *surviving* active server's metadata cache, not the
        // deleted server's own.
        var metaDatabaseArg: Int? = null
        val model = vm(
            servers = listOf(server(1, "Home"), server(2, "Away")),
            activeId = 1,
            onDeleteMetaDatabase = { metaDatabaseArg = it },
        )
        model.reload()
        model.requestDelete(model.uiState.value.rows[2]) // delete "Away" (id 2), "Home" (id 1) stays active
        model.confirmDelete()
        assertEquals(1, metaDatabaseArg) // the surviving active server's id, not 2
    }

    @Test
    fun `confirmDelete does nothing when there is no pending target`() {
        var calls = 0
        val model = vm(onDeleteServerById = { calls++ })
        model.confirmDelete()
        assertEquals(0, calls)
    }
}
