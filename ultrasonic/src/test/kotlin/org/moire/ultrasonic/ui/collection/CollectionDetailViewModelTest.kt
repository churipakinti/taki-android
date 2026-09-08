/*
 * CollectionDetailViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collection

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.model.CollectionDetailViewModel
import org.robolectric.RobolectricTestRunner

/**
 * [CollectionDetailViewModel] as a 1:1 port of the legacy `CollectionDetailModel`: the exact
 * `grouping` resolve, load-once across back navigation, refresh, and the bounded "find missing
 * discs" crawl - all with the two seams (the resolver and the crawl) replaced by fakes so no
 * `ActiveServerProvider` / network is touched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CollectionDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun album(
        id: String,
        grouping: String = "Bach 333",
        disc: Int? = 1,
        title: String = "Disc $id",
        songCount: Long? = 12,
        artistId: String? = "ar1",
    ) = Album(
        id = id,
        title = title,
        discNumber = disc,
        songCount = songCount,
        artistId = artistId,
        grouping = grouping,
    )

    private fun collection(grouping: String = "Bach 333", vararg albums: Album) =
        MusicCollection(id = "collection:x", title = grouping, albums = albums.toList())

    private fun vm(
        discover: suspend (String, List<Album>) -> Unit = { _, _ -> },
        loader: suspend (String) -> MusicCollection? = {
            collection("Bach 333", album("1"), album("2"))
        },
    ) = CollectionDetailViewModel(app).apply {
        collectionLoader = loader
        discoverer = discover
    }

    private fun members(model: CollectionDetailViewModel) = model.uiState.value.members

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a resolved collection fills the title and the members in resolver order`() = runTest {
        val model = vm {
            collection("Bach 333", album("a", disc = 1, songCount = 18), album("b", disc = 2, songCount = 21))
        }
        model.load("Bach 333")
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Bach 333", state.title)
        assertEquals("Bach 333", state.grouping)
        assertEquals(listOf("a", "b"), members(model).map { it.id })
        assertEquals(listOf(1, 2), members(model).map { it.discNumber })
        assertEquals(listOf(18L, 21L), members(model).map { it.trackCount })
    }

    @Test
    fun `a zero disc number and a zero track count are projected as absent`() = runTest {
        val model = vm {
            collection("Box", album("a", grouping = "Box", disc = 0, songCount = 0))
        }
        model.load("Box")
        advanceUntilIdle()

        val m = members(model).single()
        assertNull(m.discNumber)
        assertNull(m.trackCount)
    }

    @Test
    fun `the exact grouping string is passed to the resolver and its result used verbatim`() =
        runTest {
            var seen: String? = null
            val model = vm { grouping ->
                seen = grouping
                collection(grouping, album("only", grouping = grouping))
            }
            model.load("Mercury Living Presence")
            advanceUntilIdle()

            assertEquals("Mercury Living Presence", seen)
            assertEquals("Mercury Living Presence", model.uiState.value.title)
            assertEquals(listOf("only"), members(model).map { it.id })
        }

    @Test
    fun `a grouping that resolves to nothing ends as a finished empty collection`() = runTest {
        val model = vm { null }
        model.load("Not A Box Set")
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.showEmpty)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a failed first resolve ends as a finished empty collection`() = runTest {
        val model = vm { throw IOException("db unavailable") }
        model.load("Bach 333")
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
        assertTrue(state.showEmpty)
    }

    @Test
    fun `a cancelled load is not mistaken for a failed load`() = runTest {
        val model = vm { throw CancellationException("scope died") }
        model.load("Bach 333")
        runCatching { advanceUntilIdle() }
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test
    fun `the same grouping is not re-resolved, but refresh forces it`() = runTest {
        var calls = 0
        val model = vm { calls++; collection("Bach 333", album("1")) }
        model.load("Bach 333")
        advanceUntilIdle()
        model.load("Bach 333")
        advanceUntilIdle()
        assertEquals(1, calls)

        model.refresh()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `a refresh keeps the current members on screen, then reprojects the new data`() = runTest {
        var response = collection("Bach 333", album("a", title = "Old A"), album("b", title = "Old B"))
        val gate = CompletableDeferred<Unit>()
        var firstDone = false
        val model = vm {
            if (firstDone) gate.await()
            firstDone = true
            response
        }
        model.load("Bach 333")
        advanceUntilIdle()
        assertEquals(listOf("Old A", "Old B"), members(model).map { it.title })

        response = collection("Bach 333", album("a", title = "New A"))
        model.refresh()
        advanceUntilIdle()
        // The re-resolve is still gated: old members still shown, isLoading true.
        assertTrue(model.uiState.value.isLoading)
        assertEquals(listOf("Old A", "Old B"), members(model).map { it.title })

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertEquals(listOf("New A"), members(model).map { it.title })
    }

    @Test
    fun `a second refresh while one is running is ignored`() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val model = vm {
            calls++
            if (calls >= 2) gate.await()
            collection("Bach 333", album("1"))
        }
        model.load("Bach 333")
        advanceUntilIdle()
        assertEquals(1, calls)

        model.refresh()
        advanceUntilIdle()
        model.refresh() // ignored - the first refresh is still in flight
        advanceUntilIdle()
        assertEquals(2, calls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `a failed refresh keeps the collection that is already on screen`() = runTest {
        var fail = false
        val model = vm {
            if (fail) throw IOException("network dropped") else collection("Bach 333", album("1"))
        }
        model.load("Bach 333")
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasContent)

        fail = true
        model.refresh()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertTrue(state.hasContent)
        assertFalse(state.showEmpty)
    }

    @Test
    fun `discover more toggles the discovering flag and re-resolves with the new members`() =
        runTest {
            var resolved = collection("Bach 333", album("1"), album("2"))
            var crawled = false
            val model = vm(
                discover = { _, _ -> crawled = true },
                loader = { resolved },
            )
            model.load("Bach 333")
            advanceUntilIdle()
            assertEquals(2, members(model).size)

            resolved = collection("Bach 333", album("1"), album("2"), album("3"), album("4"))
            model.discoverMore()
            advanceUntilIdle()

            assertTrue(crawled)
            assertFalse(model.uiState.value.isDiscovering)
            assertEquals(4, members(model).size)
        }

    @Test
    fun `a second discover while one is running is ignored`() = runTest {
        var crawls = 0
        val gate = CompletableDeferred<Unit>()
        val model = vm(
            discover = { _, _ -> crawls++; gate.await() },
            loader = { collection("Bach 333", album("1")) },
        )
        model.load("Bach 333")
        advanceUntilIdle()

        model.discoverMore()
        advanceUntilIdle()
        model.discoverMore() // ignored
        advanceUntilIdle()
        assertTrue(model.uiState.value.isDiscovering)
        assertEquals(1, crawls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isDiscovering)
        assertEquals(1, crawls)
    }

    @Test
    fun `a refresh is ignored while a discovery crawl is running`() = runTest {
        var loaderCalls = 0
        val gate = CompletableDeferred<Unit>()
        val model = vm(
            discover = { _, _ -> gate.await() },
            loader = { loaderCalls++; collection("Bach 333", album("1")) },
        )
        model.load("Bach 333")
        advanceUntilIdle()
        val callsAfterLoad = loaderCalls

        model.discoverMore()
        advanceUntilIdle() // now blocked in the crawl, isDiscovering = true
        model.refresh()
        advanceUntilIdle()
        assertEquals(callsAfterLoad + 1, loaderCalls) // only discoverMore's own "known" resolve

        gate.complete(Unit)
        advanceUntilIdle()
    }
}
