/*
 * HomeLoadErrorTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.di.OFFLINE_MUSIC_SERVICE
import org.moire.ultrasonic.di.ONLINE_MUSIC_SERVICE
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.model.HomeViewModel
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.util.ErrorMessageChannel
import org.robolectric.RobolectricTestRunner

/**
 * Issue #31 regression coverage for Home: a failed load is never the "No media found" Home, a
 * failed refresh keeps every shelf already on screen, and the failure is surfaced once.
 */
@RunWith(RobolectricTestRunner::class)
class HomeLoadErrorTest {

    private val first = listOf(Album(id = "a1", title = "First", artist = "Artist 1"))
    private val second = listOf(Album(id = "b1", title = "Second", artist = "Artist 2"))

    /** What every album-list call does; flipped by each test. */
    @Volatile
    private var respond: (AlbumListType) -> List<Album> = { first }
    private val calls = AtomicInteger(0)

    private fun serve(type: AlbumListType): List<Album> {
        calls.incrementAndGet()
        return respond(type)
    }

    private fun install() {
        val service = mock<MusicService> {
            onBlocking { getAlbumList(any(), any(), any(), anyOrNull()) } doAnswer { serve(it.getArgument(0)) }
            onBlocking {
                getAlbumList2(any(), any(), any(), anyOrNull(), anyOrNull())
            } doAnswer { serve(it.getArgument(0)) }
        }
        startKoin {
            modules(
                module {
                    single<MusicService>(named(ONLINE_MUSIC_SERVICE)) { service }
                    single<MusicService>(named(OFFLINE_MUSIC_SERVICE)) { service }
                },
            )
        }
    }

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun viewModel() = HomeViewModel(app)

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        ErrorMessageChannel.reset()
        ErrorMessageChannel.attachHost()
        install()
    }

    @After
    fun tearDown() {
        ErrorMessageChannel.reset()
        stopKoin()
    }

    private val timeout: (AlbumListType) -> List<Album> = { throw SocketTimeoutException("timed out") }

    @Test
    fun `a cold load that fails everywhere is an error and not the empty Home`() = runTest {
        respond = timeout
        val vm = viewModel()

        vm.loadHomeScreen()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertTrue(state.loadFailed)
        assertTrue(state.showError)
        assertFalse(state.isEmpty)
        // Inline error only - no sheet for the first-load failure.
        assertTrue(ErrorMessageChannel.messages.value.isEmpty())
    }

    @Test
    fun `a cold load that succeeds with nothing is the empty Home and not an error`() = runTest {
        respond = { emptyList() }
        val vm = viewModel()

        vm.loadHomeScreen()
        val state = vm.uiState.value

        assertTrue(state.isEmpty)
        assertFalse(state.showError)
        assertFalse(state.loadFailed)
    }

    @Test
    fun `retry after a cold failure recovers and clears the error`() = runTest {
        respond = timeout
        val vm = viewModel()
        vm.loadHomeScreen()
        assertTrue(vm.uiState.value.showError)

        respond = { first }
        vm.loadHomeScreen(forceRefresh = true)
        val state = vm.uiState.value

        assertTrue(state.hasContent)
        assertFalse(state.loadFailed)
        assertFalse(state.showError)
        assertEquals("a1", state.recentlyPlayed.first().id)
    }

    @Test
    fun `retry that fails again stays an error`() = runTest {
        respond = timeout
        val vm = viewModel()
        vm.loadHomeScreen()
        vm.loadHomeScreen(forceRefresh = true)

        assertTrue(vm.uiState.value.showError)
        assertFalse(vm.uiState.value.isEmpty)
    }

    @Test
    fun `a failed refresh keeps every shelf and raises exactly one message`() = runTest {
        val vm = viewModel()
        vm.loadHomeScreen()
        val before = vm.uiState.value
        assertTrue(before.hasContent)

        respond = timeout
        vm.loadHomeScreen(forceRefresh = true)
        vm.loadHomeScreen(forceRefresh = true)
        val after = vm.uiState.value

        assertEquals(before.recentlyPlayed, after.recentlyPlayed)
        assertEquals(before.shelves, after.shelves)
        assertFalse(after.isRefreshing)
        assertFalse(after.isLoading)
        assertFalse(after.loadFailed)
        assertFalse(after.showError)
        assertFalse(after.isEmpty)
        assertEquals(
            listOf(app.getString(R.string.list_refresh_failed)),
            ErrorMessageChannel.messages.value,
        )
    }

    @Test
    fun `a failed refresh keeps the featured mix even when the builder swallows the error`() = runTest {
        val vm = viewModel()
        val mixTrack = org.moire.ultrasonic.domain.Track(id = "t1", title = "Song")
        vm.mixLoader = { listOf(mixTrack) }
        vm.loadHomeScreen()
        assertEquals(1, vm.uiState.value.featuredMix?.trackCount)

        respond = timeout
        vm.mixLoader = { emptyList() } // DailyMixQueueBuilder returns empty on a network error
        vm.loadHomeScreen(forceRefresh = true)

        assertEquals(1, vm.uiState.value.featuredMix?.trackCount)
        assertEquals(listOf(mixTrack), vm.mixTracks)
    }

    @Test
    fun `recovery after a failed refresh replaces the content`() = runTest {
        val vm = viewModel()
        vm.loadHomeScreen()
        respond = timeout
        vm.loadHomeScreen(forceRefresh = true)

        respond = { second }
        vm.loadHomeScreen(forceRefresh = true)

        assertEquals("b1", vm.uiState.value.recentlyPlayed.first().id)
        assertFalse(vm.uiState.value.loadFailed)
    }

    @Test
    fun `a single failing shelf keeps the others and is not an error`() = runTest {
        respond = { type -> if (type == AlbumListType.STARRED) throw SocketTimeoutException() else first }
        val vm = viewModel()

        vm.loadHomeScreen()
        val state = vm.uiState.value

        assertTrue(state.hasContent)
        assertFalse(state.loadFailed)
        assertTrue(state.shelves.first { it.kind == HomeShelfKind.LIKED }.albums.isEmpty())
        assertEquals(1, state.shelves.first { it.kind == HomeShelfKind.NEWEST }.albums.size)
        assertTrue(ErrorMessageChannel.messages.value.isEmpty())
    }

    @Test
    fun `a failed load is not marked fresh so the next visit retries`() = runTest {
        respond = timeout
        val vm = viewModel()
        vm.loadHomeScreen()
        val afterFailure = calls.get()

        respond = { first }
        vm.loadHomeScreen() // not forced: a failed load must not satisfy the freshness window
        assertTrue(calls.get() > afterFailure)
        assertTrue(vm.uiState.value.hasContent)
    }
}
