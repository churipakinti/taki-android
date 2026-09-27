/*
 * LyricsViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.lyrics

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.domain.Lyrics
import org.moire.ultrasonic.domain.LyricsLine
import org.moire.ultrasonic.model.LyricsViewModel
import org.moire.ultrasonic.model.fetchLyrics
import org.moire.ultrasonic.model.toContent
import org.robolectric.RobolectricTestRunner

/**
 * [LyricsViewModel] (issue #10 phase 4K3): the synced-first/plain-fallback lookup sequencing,
 * the track-change cancel-and-restart in [LyricsViewModel.show], [LyricsViewModel.retry], and
 * the pure [fetchLyrics] / [Lyrics.toContent] mapping functions ported from the legacy
 * `LyricsFragment`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LyricsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(loader: suspend (String, String, String) -> Lyrics? = { _, _, _ -> null }) =
        LyricsViewModel().apply { lyricsLoader = loader }

    // region LyricsViewModel

    @Test
    fun `the default state is loading with no track`() {
        val state = vm().uiState.value
        assertEquals(LyricsContent.Loading, state.content)
        assertNull(state.trackId)
    }

    @Test
    fun `show fills the track identity immediately and the content once the loader resolves`() =
        runTest {
            val model = vm { _, artist, title ->
                Lyrics(artist, title, lines = listOf(LyricsLine(null, "a plain line")))
            }
            model.show("t1", "An Artist", "A Title")

            // Identity and Loading are set synchronously, before the loader resolves.
            val loading = model.uiState.value
            assertEquals("t1", loading.trackId)
            assertEquals("A Title", loading.title)
            assertEquals("An Artist", loading.artist)
            assertEquals(LyricsContent.Loading, loading.content)

            advanceUntilIdle()
            val loaded = model.uiState.value.content
            assertTrue(loaded is LyricsContent.Plain)
            assertEquals(listOf("a plain line"), (loaded as LyricsContent.Plain).lines)
        }

    @Test
    fun `show is a no-op when the track is already shown`() = runTest {
        var calls = 0
        val model = vm { _, _, _ -> calls++; null }
        model.show("t1", "artist", "title")
        advanceUntilIdle()
        model.show("t1", "artist", "title")
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `show reloads the same track when it last ended in error`() = runTest {
        var calls = 0
        val model = vm { _, _, _ -> calls++; if (calls == 1) error("boom") else null }
        model.show("t1", "artist", "title")
        advanceUntilIdle()
        assertEquals(LyricsContent.Error, model.uiState.value.content)

        model.show("t1", "artist", "title")
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(LyricsContent.Empty, model.uiState.value.content)
    }

    @Test
    fun `a new track cancels the in-flight load so the previous track's lyrics never land`() =
        runTest {
            val firstLoad = CompletableDeferred<Lyrics?>()
            var secondLoaderCalled = false
            val model = vm { id, _, _ ->
                if (id == "t1") firstLoad.await() else { secondLoaderCalled = true; null }
            }
            model.show("t1", "artist", "title one")
            model.show("t2", "artist", "title two")
            advanceUntilIdle()

            assertTrue(secondLoaderCalled)
            assertEquals("t2", model.uiState.value.trackId)
            // The first load's eventual result must not overwrite the second track's state.
            firstLoad.complete(Lyrics(text = "stale"))
            advanceUntilIdle()
            assertEquals("t2", model.uiState.value.trackId)
            assertEquals(LyricsContent.Empty, model.uiState.value.content)
        }

    @Test
    fun `an unexpected exception maps to Error, not a crash`() = runTest {
        val model = vm { _, _, _ -> error("network down") }
        model.show("t1", "artist", "title")
        advanceUntilIdle()

        assertEquals(LyricsContent.Error, model.uiState.value.content)
    }

    @Test
    fun `cancellation is rethrown, not swallowed as an Error`() = runTest {
        val model = vm { _, _, _ -> throw CancellationException("cancelled") }
        model.show("t1", "artist", "title")
        advanceUntilIdle()

        // The launched coroutine is cancelled (structured concurrency swallows it there); the
        // point under test is that _uiState is never written to Error for a genuine cancellation.
        assertEquals(LyricsContent.Loading, model.uiState.value.content)
    }

    @Test
    fun `retry re-issues the load for the last shown track`() = runTest {
        var calls = 0
        val model = vm { _, artist, title ->
            calls++
            Lyrics(artist, title, lines = listOf(LyricsLine(null, "line $calls")))
        }
        model.show("t1", "artist", "title")
        advanceUntilIdle()
        model.retry()
        advanceUntilIdle()

        assertEquals(2, calls)
        val content = model.uiState.value.content as LyricsContent.Plain
        assertEquals(listOf("line 2"), content.lines)
    }

    @Test
    fun `retry before anything was shown is a no-op`() = runTest {
        var calls = 0
        val model = vm { _, _, _ -> calls++; null }
        model.retry()
        advanceUntilIdle()

        assertEquals(0, calls)
    }

    // endregion

    // region fetchLyrics

    @Test
    fun `a non-empty synced result wins and the artist-title lookup is never called`() {
        val synced = Lyrics(synced = true, lines = listOf(LyricsLine(0L, "line")))
        var legacyCalled = false

        val result = fetchLyrics(
            bySongId = { synced },
            byArtistTitle = { legacyCalled = true; null },
        )

        assertEquals(synced, result)
        assertTrue(!legacyCalled)
    }

    @Test
    fun `an empty synced result falls back to the artist-title lookup`() {
        val result = fetchLyrics(
            bySongId = { Lyrics(synced = true, lines = emptyList()) },
            byArtistTitle = { Lyrics(text = "line one\nline two") },
        )

        assertEquals(listOf("line one", "line two"), result?.lines?.map { it.value })
        assertTrue(result?.lines?.all { it.start == null } == true)
    }

    @Test
    fun `a blank or missing legacy text yields null`() {
        assertNull(fetchLyrics(bySongId = { null }, byArtistTitle = { null }))
        assertNull(fetchLyrics(bySongId = { null }, byArtistTitle = { Lyrics(text = "  ") }))
    }

    @Test
    fun `a failed synced lookup is swallowed once the legacy lookup succeeds`() {
        val result = fetchLyrics(
            bySongId = { error("song-id lookup failed") },
            byArtistTitle = { Lyrics(text = "ok") },
        )

        assertEquals(listOf("ok"), result?.lines?.map { it.value })
    }

    @Test
    fun `a successful-but-empty synced call still swallows a failing legacy lookup`() {
        // bySongId succeeds (returns null) so the exception from byArtistTitle need not surface.
        val result = fetchLyrics(
            bySongId = { null },
            byArtistTitle = { error("legacy lookup failed") },
        )

        assertNull(result)
    }

    @Test(expected = IllegalStateException::class)
    fun `both lookups failing throws`() {
        fetchLyrics(
            bySongId = { error("song-id lookup failed") },
            byArtistTitle = { error("legacy lookup failed") },
        )
    }

    // endregion

    // region toContent

    @Test
    fun `null lyrics map to Empty`() {
        assertEquals(LyricsContent.Empty, null.toContent())
    }

    @Test
    fun `unsynced lyrics map to Plain with leading and trailing blanks dropped, middle blanks kept`() {
        val lyrics = Lyrics(
            lines = listOf("", "verse one", "", "verse two", "").map { LyricsLine(null, it) },
        )

        val content = lyrics.toContent() as LyricsContent.Plain

        assertEquals(listOf("verse one", "", "verse two"), content.lines)
    }

    @Test
    fun `the synced flag alone is not enough - at least one real timestamp is required`() {
        val lyrics = Lyrics(synced = true, lines = listOf(LyricsLine(null, "a"), LyricsLine(null, "b")))

        assertTrue(lyrics.toContent() is LyricsContent.Plain)
    }

    @Test
    fun `synced lyrics carry a non-decreasing timestamp forward across null-start lines`() {
        val lyrics = Lyrics(
            synced = true,
            lines = listOf(
                LyricsLine(1000L, "first"),
                LyricsLine(null, "held at first's timestamp"),
                LyricsLine(500L, "an out-of-order timestamp is clamped up, never back"),
                LyricsLine(2000L, "last"),
            ),
        )

        val content = lyrics.toContent() as LyricsContent.Synced
        assertEquals(listOf(1000L, 1000L, 1000L, 2000L), content.lines.map { it.startMs })
    }

    @Test
    fun `an all-blank plain result maps to Empty`() {
        val lyrics = Lyrics(lines = listOf(LyricsLine(null, ""), LyricsLine(null, "  ")))
        assertEquals(LyricsContent.Empty, lyrics.toContent())
    }

    // endregion

    // region activeLineIndex

    @Test
    fun `before the first line's start, the index is -1`() {
        val lines = listOf(LyricsLineUi(1000L, "a"), LyricsLineUi(2000L, "b"))
        assertEquals(-1, activeLineIndex(lines, 500L))
    }

    @Test
    fun `the active index is the last line starting at or before the position`() {
        val lines = listOf(LyricsLineUi(0L, "a"), LyricsLineUi(1000L, "b"), LyricsLineUi(2000L, "c"))

        assertEquals(0, activeLineIndex(lines, 0L))
        assertEquals(0, activeLineIndex(lines, 999L))
        assertEquals(1, activeLineIndex(lines, 1000L))
        assertEquals(2, activeLineIndex(lines, 5000L))
    }

    @Test
    fun `an empty line list has no active index`() {
        assertEquals(-1, activeLineIndex(emptyList(), 1000L))
    }

    // endregion
}
