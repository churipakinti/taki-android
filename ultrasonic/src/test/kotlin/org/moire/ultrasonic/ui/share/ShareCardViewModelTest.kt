/*
 * ShareCardViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import android.graphics.Bitmap
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Issue #20: the share flow's state machine. A stable snapshot, stale results dropped, no
 * duplicate launches, a failed card never crashing, and nothing here touching playback (the view
 * model has no player dependency at all).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShareCardViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private var clock = 0L
    private val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    private val requestA = ShareCardRequest("a", ShareCardContent("Song A", "Artist A", "Album A"), "coverA")
    private val requestB = ShareCardRequest("b", ShareCardContent("Song B", "Artist B", null), "coverB")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        render: (ShareCardContent, Bitmap?) -> Bitmap = { _, _ -> bitmap },
        loadArtwork: suspend (Any?) -> Bitmap? = { bitmap },
    ) = ShareCardViewModel(
        store = ShareCardStore(folder.root) { clock },
        loadArtwork = loadArtwork,
        render = render,
        workDispatcher = dispatcher,
        clock = { clock },
    )

    @Test
    fun `opening a card goes from generating to ready with the same snapshot`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(ShareCardUiState.Hidden, vm.state.value)
        vm.open(requestA)
        assertEquals(ShareCardUiState.Generating(requestA), vm.state.value)
        advanceUntilIdle()
        val ready = vm.state.value as ShareCardUiState.Ready
        assertEquals(requestA, ready.request)
        assertTrue(ready.file.exists())
    }

    @Test
    fun `a card for a track keeps its own title and artwork when the playing track changes`() =
        runTest(dispatcher) {
            val rendered = mutableListOf<Pair<String, Any?>>()
            val vm = viewModel(
                loadArtwork = { model -> bitmap.also { rendered += "art" to model } },
                render = { content, _ -> bitmap.also { rendered += "text" to content.title } },
            )
            vm.open(requestA)
            // The user's playback moves on to track B while the card for A is still being drawn.
            advanceUntilIdle()
            assertEquals(listOf("art" to "coverA", "text" to "Song A"), rendered)
            assertEquals(requestA, (vm.state.value as ShareCardUiState.Ready).request)
        }

    @Test
    fun `a second open while a card is showing is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(requestA)
        vm.open(requestB)
        advanceUntilIdle()
        assertEquals(requestA, (vm.state.value as ShareCardUiState.Ready).request)
        vm.open(requestB)
        assertEquals(requestA, (vm.state.value as ShareCardUiState.Ready).request)
    }

    @Test
    fun `missing artwork still produces a card using the fallback`() = runTest(dispatcher) {
        var receivedArtwork: Bitmap? = bitmap
        val vm = viewModel(
            loadArtwork = { null },
            render = { _, art -> receivedArtwork = art; bitmap },
        )
        vm.open(requestA)
        advanceUntilIdle()
        assertTrue(vm.state.value is ShareCardUiState.Ready)
        assertNull(receivedArtwork)
    }

    @Test
    fun `an artwork request that throws falls back instead of failing the card`() = runTest(dispatcher) {
        val vm = viewModel(loadArtwork = { throw IOException("offline") })
        vm.open(requestA)
        advanceUntilIdle()
        assertTrue(vm.state.value is ShareCardUiState.Ready)
    }

    @Test
    fun `an artwork request that never answers times out and the card still renders`() = runTest(dispatcher) {
        val never = CompletableDeferred<Bitmap?>()
        val vm = viewModel(loadArtwork = { never.await() })
        vm.open(requestA)
        advanceTimeBy(SHARE_ARTWORK_TIMEOUT_MS - 1)
        assertTrue(vm.state.value is ShareCardUiState.Generating)
        advanceTimeBy(2)
        advanceUntilIdle()
        assertTrue(vm.state.value is ShareCardUiState.Ready)
    }

    @Test
    fun `a failed render shows an error and retry produces the card`() = runTest(dispatcher) {
        var failures = 1
        val vm = viewModel(render = { _, _ ->
            if (failures-- > 0) throw IllegalStateException("boom") else bitmap
        })
        vm.open(requestA)
        advanceUntilIdle()
        assertEquals(ShareCardUiState.Error(requestA), vm.state.value)
        vm.retry()
        assertEquals(ShareCardUiState.Generating(requestA), vm.state.value)
        advanceUntilIdle()
        assertTrue(vm.state.value is ShareCardUiState.Ready)
    }

    @Test
    fun `an out of memory failure is reported as an error not a crash`() = runTest(dispatcher) {
        val vm = viewModel(render = { _, _ -> throw OutOfMemoryError() })
        vm.open(requestA)
        advanceUntilIdle()
        assertTrue(vm.state.value is ShareCardUiState.Error)
    }

    @Test
    fun `dismissing while generating hides the sheet and a late result cannot revive it`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Bitmap?>()
            val vm = viewModel(loadArtwork = { gate.await() })
            vm.open(requestA)
            advanceUntilIdle()
            vm.dismiss()
            assertEquals(ShareCardUiState.Hidden, vm.state.value)
            gate.complete(bitmap)
            advanceUntilIdle()
            assertEquals(ShareCardUiState.Hidden, vm.state.value)
        }

    @Test
    fun `a stale result from an earlier open never replaces the newer preview`() = runTest(dispatcher) {
        val gates = mutableMapOf<Any?, CompletableDeferred<Bitmap?>>()
        val vm = viewModel(loadArtwork = { model -> gates.getOrPut(model) { CompletableDeferred() }.await() })
        vm.open(requestA)
        runCurrent()
        vm.dismiss()
        vm.open(requestB)
        runCurrent()
        // A's artwork finally arrives, after B was opened.
        gates["coverA"]?.complete(bitmap)
        runCurrent()
        assertEquals(ShareCardUiState.Generating(requestB), vm.state.value)
        gates["coverB"]!!.complete(bitmap)
        advanceUntilIdle()
        assertEquals(requestB, (vm.state.value as ShareCardUiState.Ready).request)
    }

    @Test
    fun `claimShare only hands out a ready file`() = runTest(dispatcher) {
        val vm = viewModel()
        assertNull(vm.claimShare())
        vm.open(requestA)
        assertNull("not ready yet", vm.claimShare())
        advanceUntilIdle()
        assertNotNull(vm.claimShare())
    }

    @Test
    fun `a double tap on Share launches the chooser once`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(requestA)
        advanceUntilIdle()
        val first = vm.claimShare()
        assertNotNull(first)
        clock += SHARE_LAUNCH_GUARD_MS - 1
        assertNull("second tap inside the guard window", vm.claimShare())
        clock += 2
        assertSame("a deliberate later share is allowed", first, vm.claimShare())
    }

    @Test
    fun `reopening after dismissal starts clean`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(requestA)
        advanceUntilIdle()
        vm.claimShare()
        vm.dismiss()
        vm.open(requestB)
        advanceUntilIdle()
        assertEquals(requestB, (vm.state.value as ShareCardUiState.Ready).request)
        assertNotNull("the launch guard is reset for a new card", vm.claimShare())
    }

    @Test
    fun `retry does nothing unless there is an error`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.retry()
        assertEquals(ShareCardUiState.Hidden, vm.state.value)
        vm.open(requestA)
        advanceUntilIdle()
        val before = vm.state.value
        vm.retry()
        assertFalse(vm.state.value is ShareCardUiState.Generating)
        assertEquals(before, vm.state.value)
    }

    @Suppress("unused")
    private fun TestScope.idle() = Unit
}
