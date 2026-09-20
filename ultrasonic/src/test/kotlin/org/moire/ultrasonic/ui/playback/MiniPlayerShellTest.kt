/*
 * MiniPlayerShellTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.robolectric.RobolectricTestRunner

/**
 * The shell contract around the Activity-owned Compose mini-player (issue #10 phase 4I): the
 * bottom-content inset every screen (Compose and View) pads by, the destination-only visibility
 * rule, the touch-gesture rule carried over from the legacy fragment, and the guarantee that the
 * Activity layout hosts exactly one Compose mini-player and no leftover Fragment host.
 */
@RunWith(RobolectricTestRunner::class)
class MiniPlayerShellTest {

    // The real dimens the Activity feeds in: 16dp edge margin, 96dp mini-player band, and a
    // representative 3-button-nav-bar (48) / measured bottom-nav footprint (80 + 48 = 128).
    private val navBar = 48
    private val navFootprint = 128
    private val band = 96
    private val edge = 16

    private fun inset(nav: Boolean, mini: Boolean) = NavigationActivity.contentBottomInsetFor(
        bottomNavVisible = nav,
        miniPlayerVisible = mini,
        navigationBarBottomInset = navBar,
        bottomNavFootprintPx = navFootprint,
        floatingChromeInsetPx = band,
        miniPlayerEdgeMarginPx = edge,
    )

    // --- inset contract --------------------------------------------------------------------

    @Test
    fun `bottom nav and mini-player - footprint plus the whole mini-player band`() =
        assertEquals(navFootprint + band, inset(nav = true, mini = true))

    @Test
    fun `bottom nav only - footprint plus the edge margin`() =
        assertEquals(navFootprint + edge, inset(nav = true, mini = false))

    @Test
    fun `mini-player only - nav bar plus the whole mini-player band`() =
        assertEquals(navBar + band, inset(nav = false, mini = true))

    @Test
    fun `neither - just the system nav bar`() =
        assertEquals(navBar, inset(nav = false, mini = false))

    @Test
    fun `showing the mini-player never shrinks the inset, hiding it never grows it`() {
        assertTrue(inset(nav = true, mini = true) > inset(nav = true, mini = false))
        assertTrue(inset(nav = false, mini = true) > inset(nav = false, mini = false))
    }

    @Test
    fun `the mini-player band is exactly edge + height + edge`() {
        // 16 + 64 + 16: the value XML screens and Compose screens both rely on.
        assertEquals(96, edge + 64 + edge)
        assertEquals(band, edge + 64 + edge)
    }

    // --- visibility rule (legacy + Compose destinations alike) ------------------------------

    @Test
    fun `the mini-player is hidden only on the full player and during Search keyboard input`() {
        assertTrue(NavigationActivity.miniPlayerHiddenFor(R.id.playerFragment, imeVisible = false))
        assertTrue(NavigationActivity.miniPlayerHiddenFor(R.id.searchFragment, imeVisible = true))
        assertFalse(NavigationActivity.miniPlayerHiddenFor(R.id.searchFragment, imeVisible = false))
    }

    @Test
    fun `the shell does not depend on whether the destination is Compose or legacy`() {
        // Compose screens (Home, Library, Artist List, Playlists, Downloads, Album Detail) and
        // View / support screens (Settings, About, Equalizer, Server editor, Lyrics) all keep it.
        val destinations = listOf(
            R.id.homeFragment,
            R.id.mainFragment,
            R.id.artistListFragment,
            R.id.playlistsFragment,
            R.id.downloadsFragment,
            R.id.selectGenreFragment,
            R.id.trackCollectionFragment,
            R.id.downloadedAlbumFragment,
            R.id.settingsFragment,
            R.id.aboutFragment,
            R.id.equalizerFragment,
            R.id.editServerFragment,
            R.id.serverSelectorFragment,
            R.id.lyricsFragment,
        )
        destinations.forEach { id ->
            assertFalse(
                "mini-player must stay available on destination $id",
                NavigationActivity.miniPlayerHiddenFor(id, imeVisible = false),
            )
            // Even with the keyboard up: only Search reacts to it.
            assertFalse(NavigationActivity.miniPlayerHiddenFor(id, imeVisible = true))
        }
    }

    // --- layout: one Compose host, no Fragment host -----------------------------------------

    @Test
    fun `the Activity layout hosts exactly one Compose mini-player and no NowPlayingFragment`() {
        val layout = File("src/main/res/layout/navigation_activity.xml").readText()
        assertEquals(1, Regex("""@\+id/mini_player_host""").findAll(layout).count())
        assertTrue(layout.contains("androidx.compose.ui.platform.ComposeView"))
        assertFalse(layout.contains("NowPlayingFragment"))
        assertFalse(layout.contains("now_playing_fragment"))
    }

    @Test
    fun `no navigation-graph node points at a mini-player fragment`() {
        val graph = File("src/main/res/navigation/navigation_graph.xml").readText()
        assertFalse(graph.contains("NowPlayingFragment"))
        // The live Now Playing surface stays the legacy PlayerFragment destination.
        assertTrue(graph.contains("@+id/playerFragment"))
    }

    // --- gesture rule (carried over from the legacy handleOnTouch) --------------------------

    @Test
    fun `a small movement is a tap that opens Now Playing`() {
        assertEquals(MiniPlayerGesture.OpenNowPlaying, resolveMiniPlayerGesture(0f, 0f))
        assertEquals(MiniPlayerGesture.OpenNowPlaying, resolveMiniPlayerGesture(30f, 30f))
        assertEquals(MiniPlayerGesture.OpenNowPlaying, resolveMiniPlayerGesture(-30f, 29f))
    }

    @Test
    fun `finger moving right (down minus up is negative) is previous, left is next`() {
        assertEquals(MiniPlayerGesture.Previous, resolveMiniPlayerGesture(-31f, 0f))
        assertEquals(MiniPlayerGesture.Next, resolveMiniPlayerGesture(31f, 0f))
    }

    @Test
    fun `a mostly vertical drag does nothing and never dismisses the bar`() {
        assertEquals(MiniPlayerGesture.None, resolveMiniPlayerGesture(0f, 31f))
        assertEquals(MiniPlayerGesture.None, resolveMiniPlayerGesture(10f, -200f))
    }

    @Test
    fun `horizontal wins over vertical when both exceed the threshold`() {
        assertEquals(MiniPlayerGesture.Next, resolveMiniPlayerGesture(100f, 100f))
    }
}
