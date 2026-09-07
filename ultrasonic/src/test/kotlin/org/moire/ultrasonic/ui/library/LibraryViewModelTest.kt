/*
 * LibraryViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.moire.ultrasonic.model.LibraryViewModel

/**
 * [LibraryViewModel.refresh] state transitions, with the cached-metadata probe replaced so
 * no Room instance / Koin is needed. Mirrors the old `MainFragment.setupBoxSetsRow`:
 * available -> row shows, unavailable or error -> row hidden.
 */
class LibraryViewModelTest {

    @Test
    fun `starts with Box Sets hidden`() {
        assertFalse(LibraryViewModel().uiState.value.boxSetsAvailable)
    }

    @Test
    fun `a resolved box set shows the row`() = runTest {
        val vm = LibraryViewModel().apply { boxSetsProbe = { true } }
        vm.refresh()
        assertTrue(vm.uiState.value.boxSetsAvailable)
    }

    @Test
    fun `no resolved box set keeps the row hidden`() = runTest {
        val vm = LibraryViewModel().apply { boxSetsProbe = { false } }
        vm.refresh()
        assertFalse(vm.uiState.value.boxSetsAvailable)
    }

    @Test
    fun `a probe failure leaves the row hidden`() = runTest {
        val vm = LibraryViewModel().apply {
            boxSetsProbe = { error("cache read blew up") }
        }
        vm.refresh()
        assertFalse(vm.uiState.value.boxSetsAvailable)
    }

    @Test
    fun `a previously visible row is hidden again if a later probe fails`() = runTest {
        val vm = LibraryViewModel().apply { boxSetsProbe = { true } }
        vm.refresh()
        assertTrue(vm.uiState.value.boxSetsAvailable)

        vm.boxSetsProbe = { false }
        vm.refresh()
        assertFalse(vm.uiState.value.boxSetsAvailable)
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        val vm = LibraryViewModel().apply {
            boxSetsProbe = { throw CancellationException("scope died") }
        }
        var caught: Throwable? = null
        try {
            vm.refresh()
        } catch (expected: CancellationException) {
            caught = expected
        }
        assertTrue(caught is CancellationException)
    }
}
