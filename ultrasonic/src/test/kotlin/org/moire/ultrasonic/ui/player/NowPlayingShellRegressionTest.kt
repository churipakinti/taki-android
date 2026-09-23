/*
 * NowPlayingShellRegressionTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity

/**
 * Regression coverage for phase 4I's mini-player/shell contract now that phase 4J has replaced
 * the destination it hides against: the shell logic is pure and keyed on destination *id* alone
 * (issue #10 phase 4I), so it cannot know or care that `playerFragment` changed from a legacy
 * `Fragment` to a Compose host - a real assertion, not just a restatement of phase 4I's own
 * tests, since a naive "hide the mini-player only for the legacy PlayerFragment class" mistake
 * would compile fine and only break at runtime.
 */
class NowPlayingShellRegressionTest {

    @Test
    fun `the mini-player is still hidden on the Now Playing destination after phase 4J`() {
        assertTrue(NavigationActivity.miniPlayerHiddenFor(R.id.playerFragment, imeVisible = false))
    }

    @Test
    fun `every other destination still shows the mini-player, unaffected by the 4J rewrite`() {
        assertFalse(NavigationActivity.miniPlayerHiddenFor(R.id.homeFragment, imeVisible = false))
        assertFalse(NavigationActivity.miniPlayerHiddenFor(R.id.artistListFragment, imeVisible = false))
        assertFalse(NavigationActivity.miniPlayerHiddenFor(R.id.downloadsFragment, imeVisible = false))
    }

    @Test
    fun `the Now Playing destination still hides the shared Material toolbar`() {
        assertTrue(
            NavigationActivity.hidesSupportActionBar(
                R.id.playerFragment,
                isLibraryTrackCollection = false,
                isAlbumDetail = false,
            ),
        )
    }

    @Test
    fun `the shell's bottom-inset math is unaffected by which destination is showing`() {
        // The inset formula (issue #10 phase 4I) takes only booleans/pixel counts, never a
        // destination id or Fragment type - Now Playing rewriting its own internals cannot
        // change what a Compose or legacy screen elsewhere pads its bottom by.
        val withBoth = NavigationActivity.contentBottomInsetFor(
            bottomNavVisible = true,
            miniPlayerVisible = true,
            navigationBarBottomInset = 48,
            bottomNavFootprintPx = 128,
            floatingChromeInsetPx = 96,
            miniPlayerEdgeMarginPx = 16,
        )
        val navOnly = NavigationActivity.contentBottomInsetFor(
            bottomNavVisible = true,
            miniPlayerVisible = false,
            navigationBarBottomInset = 48,
            bottomNavFootprintPx = 128,
            floatingChromeInsetPx = 96,
            miniPlayerEdgeMarginPx = 16,
        )
        assertTrue(withBoth > navOnly)
    }
}
