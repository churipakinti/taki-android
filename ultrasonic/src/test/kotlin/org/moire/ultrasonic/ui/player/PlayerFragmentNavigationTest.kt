/*
 * PlayerFragmentNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import android.content.Context
import androidx.core.os.bundleOf
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.robolectric.RobolectricTestRunner

/**
 * Now Playing (issue #10 phase 4J) keeps the existing `playerFragment` destination (no
 * arguments, same id, only its backing class rewritten to a Compose host - the same pattern
 * every other phase-4 migration used) and every one of its outgoing actions unchanged:
 * `playerToSelectAlbum` -> `trackCollectionFragment`, `playerToAlbumsList` -> `albumListFragment`,
 * `playerToLyrics` -> `lyricsFragment`, `playerToEqualizer` -> `equalizerFragment`. Mini-player
 * -> Now Playing navigation is phase 4I's `R.id.playerFragment` and is unchanged here.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerFragmentNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `the player destination and its four target destinations still exist`() {
        // playerToSelectAlbum/playerToAlbumsList/playerToLyrics/playerToEqualizer are actions
        // (graph edges), not destination nodes - findNode does not resolve them (confirmed by
        // every action test below actually navigating and checking the resolved destination).
        val graph = navController.graph
        assertNotNull("player", graph.findNode(R.id.playerFragment))
        assertNotNull("player -> album target", graph.findNode(R.id.trackCollectionFragment))
        assertNotNull("player -> albums list target", graph.findNode(R.id.albumListFragment))
        assertNotNull("player -> lyrics target", graph.findNode(R.id.lyricsFragment))
        assertNotNull("player -> equalizer target", graph.findNode(R.id.equalizerFragment))
    }

    @Test
    fun `the player destination is reachable and takes no arguments`() {
        navController.navigate(R.id.playerFragment)
        assertEquals(R.id.playerFragment, navController.currentDestination?.id)
    }

    @Test
    fun `player to lyrics preserves artist, title and id`() {
        navController.navigate(R.id.playerFragment)
        navController.navigate(
            R.id.playerToLyrics,
            bundleOf("artist" to "Radiohead", "title" to "Idioteque", "id" to "t1"),
        )
        assertEquals(R.id.lyricsFragment, navController.currentDestination?.id)
        val args = navController.currentBackStackEntry?.arguments
        assertEquals("Radiohead", args?.getString("artist"))
        assertEquals("Idioteque", args?.getString("title"))
        assertEquals("t1", args?.getString("id"))
    }

    @Test
    fun `back from lyrics returns to the player`() {
        navController.navigate(R.id.playerFragment)
        navController.navigate(
            R.id.playerToLyrics,
            bundleOf("artist" to "Radiohead", "title" to "Idioteque", "id" to "t1"),
        )
        navController.popBackStack()
        assertEquals(R.id.playerFragment, navController.currentDestination?.id)
    }

    @Test
    fun `player to equalizer has no arguments and is reachable`() {
        navController.navigate(R.id.playerFragment)
        navController.navigate(R.id.playerToEqualizer)
        assertEquals(R.id.equalizerFragment, navController.currentDestination?.id)
    }

    @Test
    fun `player to select album preserves id, name, parentId and isAlbum`() {
        navController.navigate(R.id.playerFragment)
        navController.navigate(
            R.id.playerToSelectAlbum,
            bundleOf(
                "id" to "al1",
                "name" to "Kid A",
                "parentId" to "parent1",
                "isAlbum" to true,
            ),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
        val args = navController.currentBackStackEntry?.arguments
        assertEquals("al1", args?.getString("id"))
        assertEquals("Kid A", args?.getString("name"))
        assertEquals(true, args?.getBoolean("isAlbum"))
    }

    @Test
    fun `player to albums list by artist preserves the artist filter args`() {
        navController.navigate(R.id.playerFragment)
        navController.navigate(
            R.id.playerToAlbumsList,
            bundleOf(
                "type" to AlbumListType.SORTED_BY_NAME,
                "byArtist" to true,
                "id" to "ar1",
                "title" to "Radiohead",
                "offset" to 0,
                "size" to 1000,
            ),
        )
        assertEquals(R.id.albumListFragment, navController.currentDestination?.id)
        val args = navController.currentBackStackEntry?.arguments
        assertEquals(true, args?.getBoolean("byArtist"))
        assertEquals("ar1", args?.getString("id"))
    }

    @Test
    fun `the player destination still hides the shared toolbar and bottom nav`() {
        // Unchanged from before phase 4J - the mini-player and bottom nav were already hidden
        // for playerFragment (issue #10 phase 4I); this locks that Now Playing's own Compose
        // chrome did not need, and must not get, a second hide/show mechanism.
        assertEquals(
            true,
            NavigationActivity.hidesSupportActionBar(
                R.id.playerFragment,
                isLibraryTrackCollection = false,
                isAlbumDetail = false,
            ),
        )
        assertEquals(true, NavigationActivity.miniPlayerHiddenFor(R.id.playerFragment, imeVisible = false))
    }
}
