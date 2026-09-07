/*
 * HomeViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.moire.ultrasonic.di.OFFLINE_MUSIC_SERVICE
import org.moire.ultrasonic.di.ONLINE_MUSIC_SERVICE
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.model.HomeViewModel
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.robolectric.RobolectricTestRunner

/**
 * [HomeViewModel] state behaviour: domain -> [HomeUiState] mapping, the loading/refresh
 * flags, per-shelf error swallowing, and the per-server freshness window (all carried over
 * from the old View implementation).
 *
 * The daily-mix path is left to fail into `null` here (`DailyMixQueueBuilder` drives the
 * unstubbed fake) - it has its own coverage in `DailyMixQueueBuilderTest`.
 */
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private val albums = listOf(
        Album(id = "a1", title = "First", artist = "Artist 1"),
        Album(id = "a2", title = "Second", artist = "Artist 2"),
    )

    private lateinit var service: MusicService

    private fun install(musicService: MusicService) {
        service = musicService
        startKoin {
            modules(
                module {
                    single<MusicService>(named(ONLINE_MUSIC_SERVICE)) { service }
                    single<MusicService>(named(OFFLINE_MUSIC_SERVICE)) { service }
                },
            )
        }
    }

    private fun viewModel() =
        HomeViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun happyService(): MusicService = mock {
        onBlocking { getAlbumList(any(), any(), any(), anyOrNull()) } doReturn albums
        onBlocking { getAlbumList2(any(), any(), any(), anyOrNull(), anyOrNull()) } doReturn albums
    }

    @Test
    fun `loadHomeScreen maps every shelf and clears the loading flags`() = runTest {
        install(happyService())
        val vm = viewModel()

        vm.loadHomeScreen()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertTrue(state.hasContent)
        assertFalse(state.isEmpty)

        assertEquals(2, state.recentlyPlayed.size)
        assertEquals("a1", state.recentlyPlayed.first().id)
        assertEquals("First", state.recentlyPlayed.first().title)
        assertEquals("Artist 1", state.recentlyPlayed.first().subtitle)

        assertEquals(
            listOf(
                HomeShelfKind.LIKED,
                HomeShelfKind.NEWEST,
                HomeShelfKind.DISCOVER,
                HomeShelfKind.FREQUENT,
            ),
            state.shelves.map { it.kind },
        )
        state.shelves.forEach { assertEquals(2, it.albums.size) }
    }

    @Test
    fun `a shelf that throws is swallowed and comes back empty`() = runTest {
        install(
            mock {
                onBlocking { getAlbumList(any(), any(), any(), anyOrNull()) } doThrow RuntimeException("boom")
                onBlocking {
                    getAlbumList2(any(), any(), any(), anyOrNull(), anyOrNull())
                } doThrow RuntimeException("boom")
            },
        )
        val vm = viewModel()

        vm.loadHomeScreen()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertTrue(state.shelves.all { it.albums.isEmpty() })
        assertTrue(state.recentlyPlayed.isEmpty())
        assertNull(state.featuredMix)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `greeting is always populated`() = runTest {
        install(happyService())
        val vm = viewModel()
        vm.loadHomeScreen()
        assertTrue(vm.uiState.value.greeting in HomeGreeting.entries)
    }

    @Test
    fun `regenerateDailyMix toggles the refresh flag and settles`() = runTest {
        install(happyService())
        val vm = viewModel()
        vm.loadHomeScreen()

        vm.regenerateDailyMix()

        assertFalse(vm.uiState.value.isRefreshing)
        // The unstubbed fake cannot build a mix, so it settles to "no featured card".
        assertNull(vm.uiState.value.featuredMix)
        assertTrue(vm.mixTracks.isEmpty())
    }
}

// Freshness-window behaviour has dedicated coverage in HomeShelvesFreshnessTest.
