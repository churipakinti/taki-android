/*
 * ServerSettingCredentialExposureAuditTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.data

import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.robolectric.RobolectricTestRunner
import timber.log.Timber

/**
 * Permanent security regression for beta blocker BB-1 (#27): a server password must never reach
 * Timber - and therefore logcat or `FileLoggerTree`, which receives exactly the messages captured
 * here - through `ServerSetting`. Uses a unique sentinel as the password and asserts it (and any
 * fragment of it) is absent from every message the server-settings paths emit.
 */
@RunWith(RobolectricTestRunner::class)
class ServerSettingCredentialExposureAuditTest {

    private val sentinel = "SUPER_SECRET_SENTINEL_9f21"
    private val logged = CopyOnWriteArrayList<String>()
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logged += message + (t?.toString() ?: "")
        }
    }

    @Before
    fun plant() {
        RobolectricUAppContext.install()
        Timber.plant(tree)
    }

    @After
    fun uproot() {
        Timber.uproot(tree)
    }

    private fun setting(id: Int = 7, index: Int = 1) = ServerSetting(
        id = id, index = index, name = "Home", url = "https://music.example", userName = "alice",
        password = sentinel, jukeboxByDefault = false, allowSelfSignedCertificate = false,
        forcePlainTextPassword = false, musicFolderId = null, minimumApiVersion = null,
    )

    private fun assertNoSecret(text: String) {
        assertFalse("password leaked: $text", text.contains(sentinel))
        // No recoverable fragment: neither the unique tail nor the unique head
        assertFalse("password fragment leaked: $text", text.contains("9f21"))
        assertFalse("password fragment leaked: $text", text.contains("SENTINEL"))
        assertFalse("user name leaked: $text", text.contains("alice"))
    }

    private fun assertNothingLoggedLeaks() {
        assertTrue("expected at least one log message", logged.isNotEmpty())
        logged.forEach(::assertNoSecret)
    }

    @Test
    fun `toString never contains the password and carries the redaction marker`() {
        val text = setting().toString()
        assertNoSecret(text)
        assertTrue(text.contains("<redacted>"))
        assertTrue(text.contains("id=7"))
    }

    @Test
    fun `toString redaction does not alter equality hashCode copy or components`() {
        val a = setting()
        val b = setting()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == a.copy(password = "other"))
        assertEquals(sentinel, a.copy(name = "x").password)
        assertEquals(sentinel, a.component7())
    }

    @Test
    fun `interpolating a ServerSetting into a log message cannot leak the password`() {
        Timber.d("whole object: ${setting()}")
        Timber.d("whole object: %s", setting())
        assertNothingLoggedLeaks()
    }

    private fun model(dao: ServerSettingDao) = ServerSettingsModel(
        dao,
        mock<ActiveServerProvider>(),
        ApplicationProvider.getApplicationContext()
    )

    private fun awaitLogs(count: Int) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (logged.size < count && System.currentTimeMillis() < deadline) Thread.sleep(20)
    }

    @Test
    fun `updateItem logs the id and never the password`() {
        val dao = mock<ServerSettingDao>()
        model(dao).updateItem(setting())
        awaitLogs(1)
        assertNothingLoggedLeaks()
        assertTrue(logged.any { it.contains("updateItem") && it.contains("id: 7") })
    }

    @Test
    fun `saveNewItem logs the id and never the password`() {
        val dao = mock<ServerSettingDao>()
        dao.stub {
            onBlocking { count() } doReturn 0
            onBlocking { insert(any()) } doReturn 7L
        }
        model(dao).saveNewItem(setting(id = 0))
        awaitLogs(1)
        assertNothingLoggedLeaks()
        assertTrue(logged.any { it.contains("saveNewItem") && it.contains("id: 7") })
    }

    @Test
    fun `reindexSettings logs the id and never the password`() {
        // Row sits at index 2 while index 1 is missing -> getServerList() triggers a reindex
        val stored = setting(id = 7, index = 2)
        val dao = mock<ServerSettingDao>()
        dao.stub {
            onBlocking { count() } doReturn 1
            onBlocking { getMaxIndex() } doReturn 2
            onBlocking { findByIndex(1) } doReturn null
            onBlocking { findByIndex(2) } doReturn stored
        }
        runBlocking { model(dao).getServerList() }
        assertNothingLoggedLeaks()
        assertTrue(logged.any { it.contains("reindexSettings") && it.contains("id: 7") })
    }

    @Test
    fun `ActiveServerProvider retrieval log never contains the stored server`() {
        val dao = mock<ServerSettingDao>()
        dao.stub { onBlocking { findById(7) } doReturn setting() }
        ActiveServerProvider(dao).getActiveServer(7)
        assertNothingLoggedLeaks()
        assertTrue(logged.any { it.contains("getActiveServer") })
    }
}
