/*
 * AlbumListLoadErrorTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.albumlist

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.model.AlbumListViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.util.ErrorMessageChannel
import org.moire.ultrasonic.util.Settings
import org.robolectric.RobolectricTestRunner

/**
 * Issue #31 regression coverage for the Album List: a failed load is never the empty state,
 * Retry is one new load, a failed refresh keeps the albums already on screen and raises exactly
 * one message, and cancellation is not a failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AlbumListLoadErrorTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RobolectricUAppContext.install()
        app = ApplicationProvider.getApplicationContext()
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = true
        Settings.id3TagsEnabledOffline = true
        val provider = mock<ActiveServerProvider> {
            on { getActiveServer(any()) } doReturn ServerSetting().apply { musicFolderId = null }
        }
        startKoin { modules(module { single { provider } }) }
        ErrorMessageChannel.reset()
        ErrorMessageChannel.attachHost()
    }

    @After
    fun tearDown() {
        ErrorMessageChannel.reset()
        Dispatchers.resetMain()
        stopKoin()
    }

    private fun album(id: String) = Album(id = id, title = "Album $id", artist = "Artist")

    /** A loader whose behaviour the test flips between failure and success, counting calls. */
    private class FlakyLoader(var result: () -> List<Album>) {
        var calls = 0

        /** When set, [load] suspends on it so a test can observe the in-flight Loading state. */
        var gate: CompletableDeferred<Unit>? = null
        suspend fun load(): List<Album> {
            calls++
            gate?.await()
            return result()
        }
    }

    private fun vm(
        loader: FlakyLoader,
        type: AlbumListType = AlbumListType.SORTED_BY_NAME,
    ) = AlbumListViewModel(app).apply {
        albumsLoader = { _, _, _, _, _ -> loader.load() }
        initialize(type, false, null, null, 20, 0)
    }

    private val timeout = { throw SocketTimeoutException("connect timed out") }

    @Test
    fun `a timed out first load is the error state and never the empty state`() = runTest {
        val loader = FlakyLoader(timeout)
        val model = vm(loader)
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
        assertTrue(state.showError)
        assertFalse(state.showEmpty)
        // Inline error only - the first-load failure must not also raise a sheet.
        assertTrue(ErrorMessageChannel.messages.value.isEmpty())
    }

    @Test
    fun `a successful empty response is the empty state and never the error state`() = runTest {
        val model = vm(FlakyLoader { emptyList() })
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertTrue(state.showEmpty)
        assertFalse(state.showError)
        assertFalse(state.loadFailed)
    }

    @Test
    fun `retry is exactly one new load that goes Error to Loading to Content`() = runTest {
        val loader = FlakyLoader(timeout)
        val model = vm(loader)
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.showError)
        assertEquals(1, loader.calls)

        loader.result = { listOf(album("a1"), album("a2")) }
        val gate = CompletableDeferred<Unit>()
        loader.gate = gate
        model.refresh()
        runCurrent()
        // Loading: the error is gone as soon as the retry starts, before the request returns.
        assertEquals(2, loader.calls)
        assertTrue(model.uiState.value.isLoading)
        assertFalse(model.uiState.value.loadFailed)
        assertFalse(model.uiState.value.showError)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, loader.calls)
        val state = model.uiState.value
        assertEquals(2, state.rows.size)
        assertFalse(state.loadFailed)
        assertFalse(state.showError)
        assertFalse(state.showEmpty)
    }

    @Test
    fun `retry that succeeds with zero albums becomes the genuine empty state`() = runTest {
        val loader = FlakyLoader(timeout)
        val model = vm(loader)
        model.load()
        advanceUntilIdle()

        loader.result = { emptyList() }
        model.refresh()
        advanceUntilIdle()

        assertTrue(model.uiState.value.showEmpty)
        assertFalse(model.uiState.value.showError)
    }

    @Test
    fun `retry that fails again returns to the error state`() = runTest {
        val loader = FlakyLoader(timeout)
        val model = vm(loader)
        model.load()
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()

        assertEquals(2, loader.calls)
        assertTrue(model.uiState.value.showError)
        assertFalse(model.uiState.value.showEmpty)
    }

    @Test
    fun `a failed refresh keeps the albums on screen and raises exactly one message`() = runTest {
        val loader = FlakyLoader { listOf(album("a1"), album("a2")) }
        val model = vm(loader)
        model.load()
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.rows.size)

        loader.result = { throw IOException("network down") }
        model.refresh()
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(listOf("a1", "a2"), state.rows.map { it.id })
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertFalse(state.showError)
        assertFalse(state.showEmpty)
        // Two failed refreshes, one pending message (no stacked duplicates).
        assertEquals(
            listOf(app.getString(R.string.list_refresh_failed)),
            ErrorMessageChannel.messages.value,
        )
    }

    @Test
    fun `a failed RANDOM refresh keeps the previous albums and their lookups`() = runTest {
        val loader = FlakyLoader { listOf(album("a1")) }
        val model = vm(loader, type = AlbumListType.RANDOM)
        model.load()
        advanceUntilIdle()

        loader.result = { throw IOException("network down") }
        model.refresh()
        advanceUntilIdle()

        assertEquals(listOf("a1"), model.uiState.value.rows.map { it.id })
        assertEquals("a1", model.itemFor("a1")?.id)
    }

    @Test
    fun `recovery after a failed refresh shows the new content`() = runTest {
        val loader = FlakyLoader { listOf(album("a1")) }
        val model = vm(loader)
        model.load()
        advanceUntilIdle()
        loader.result = { throw IOException("network down") }
        model.refresh()
        advanceUntilIdle()

        loader.result = { listOf(album("b1"), album("b2")) }
        model.refresh()
        advanceUntilIdle()

        assertEquals(listOf("b1", "b2"), model.uiState.value.rows.map { it.id })
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test
    fun `a cancelled load is neither an error nor empty and raises no message`() = runTest {
        val model = vm(FlakyLoader { throw CancellationException("scope died") })
        model.load()
        runCatching { advanceUntilIdle() }

        assertFalse(model.uiState.value.loadFailed)
        assertFalse(model.uiState.value.showError)
        assertTrue(ErrorMessageChannel.messages.value.isEmpty())
    }
}
