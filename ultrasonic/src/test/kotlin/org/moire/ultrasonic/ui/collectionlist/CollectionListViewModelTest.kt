/*
 * CollectionListViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collectionlist

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.model.CollectionListViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.robolectric.RobolectricTestRunner

/**
 * [CollectionListViewModel] - a straight StateFlow port of the legacy `CollectionListModel`
 * (post-issue-#10 residual migration, phase 5A1): reads already-cached albums, resolves
 * Collections client-side, and never re-loads once it has - the same "keep scroll position
 * across back navigation" recipe the legacy `collections.value != null` guard gave.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CollectionListViewModelTest {

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

    private fun album(id: String, title: String) = Album(id = id, title = title)

    private fun collection(id: String, title: String, albumCount: Int = 1) = MusicCollection(
        id = id,
        title = title,
        albums = (1..albumCount).map { album("$id-$it", "Disc $it") },
    )

    private fun vm(loader: suspend () -> List<MusicCollection> = { emptyList() }) =
        CollectionListViewModel(app).apply { collectionsLoader = loader }

    // --- Initial load ------------------------------------------------------------------------

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a loaded collection list publishes rows in the resolver's given order`() = runTest {
        val model = vm { listOf(collection("c1", "Bach 333"), collection("c2", "Mercury Living Presence")) }
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Bach 333", "Mercury Living Presence"), state.rows.map { it.title })
    }

    @Test
    fun `each row's albumCount and id come straight from the resolved MusicCollection`() = runTest {
        val model = vm { listOf(collection("collection:bach-333", "Bach 333", albumCount = 222)) }
        model.load()
        advanceUntilIdle()

        val row = model.uiState.value.rows.single()
        assertEquals("collection:bach-333", row.id)
        assertEquals(222, row.albumCount)
    }

    @Test
    fun `an empty collection list is published as-is`() = runTest {
        val model = vm { emptyList() }
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.showEmpty)
    }

    // --- Load-once / refresh -------------------------------------------------------------

    @Test
    fun `a second load without refresh does not call through again`() = runTest {
        var calls = 0
        val model = vm { calls++; listOf(collection("c1", "Bach 333")) }
        model.load()
        advanceUntilIdle()
        model.load()
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `a second load without refresh does not reload even when the result was empty`() = runTest {
        // Matches the legacy CollectionListModel's own guard: collections.value != null, not
        // collections.value?.isNotEmpty() - a genuinely empty library must not spin forever.
        var calls = 0
        val model = vm { calls++; emptyList() }
        model.load()
        advanceUntilIdle()
        model.load()
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `refresh always calls through, even when already loaded once`() = runTest {
        var calls = 0
        val model = vm { calls++; listOf(collection("c1", "Bach 333")) }
        model.load()
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `a failed first load ends without content, not stuck loading`() = runTest {
        val model = vm { throw IOException("server down") }
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
    }

    @Test
    fun `a cancelled load is not mistaken for a failed load`() = runTest {
        val model = vm { throw CancellationException("scope died") }
        model.load()
        runCatching { advanceUntilIdle() }
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test
    fun `a failed refresh keeps the collections already on screen`() = runTest {
        var fail = false
        val model = vm { if (fail) throw IOException("down") else listOf(collection("c1", "Bach 333")) }
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasContent)

        fail = true
        model.refresh()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertTrue(state.hasContent)
    }
}
