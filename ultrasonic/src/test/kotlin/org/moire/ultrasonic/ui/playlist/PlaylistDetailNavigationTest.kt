/*
 * PlaylistDetailNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

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
import org.robolectric.RobolectricTestRunner

/**
 * Playlist Detail (issue #10 phase 4F3) keeps the existing `trackCollectionFragment`
 * destination and its `playlistId`/`playlistName`/`autoPlay`/`shuffle` arguments unchanged - no
 * `navigation-compose`, no new destination id, no merge with the generic Track List/Album
 * detail destinations. The Compose migration is a presentation swap inside the same Fragment
 * (`isComposePlaylistDetailMode`), exactly like Album Detail's own navigation test.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistDetailNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `the playlists list and playlist detail destinations still exist`() {
        val graph = navController.graph
        assertNotNull("playlists list", graph.findNode(R.id.playlistsFragment))
        assertNotNull("playlist detail (shared trackCollectionFragment)", graph.findNode(R.id.trackCollectionFragment))
    }

    @Test
    fun `a playlist caller opens trackCollectionFragment with its id and name preserved`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("playlistId" to "pl1", "playlistName" to "Road Trip"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
        val args = navController.currentBackStackEntry?.arguments
        assertEquals("pl1", args?.getString("playlistId"))
        assertEquals("Road Trip", args?.getString("playlistName"))
    }

    @Test
    fun `Play Now from the playlist list preserves autoPlay, Play Shuffled preserves shuffle too`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf(
                "playlistId" to "pl1",
                "playlistName" to "Road Trip",
                "autoPlay" to true,
                "shuffle" to true,
            ),
        )
        val args = navController.currentBackStackEntry?.arguments
        assertEquals(true, args?.getBoolean("autoPlay"))
        assertEquals(true, args?.getBoolean("shuffle"))
    }

    @Test
    fun `back from playlist detail returns to the playlists list`() {
        navController.navigate(R.id.playlistsFragment)
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("playlistId" to "pl1", "playlistName" to "Road Trip"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)

        navController.popBackStack()
        assertEquals(R.id.playlistsFragment, navController.currentDestination?.id)
    }

    @Test
    fun `deleting a playlist navigates up, same as the legacy confirmDeletePlaylist`() {
        // confirmDeletePlaylist() (reused as-is by the Compose header menu) calls
        // findNavController().navigateUp() on success - this locks that trackCollectionFragment
        // remains a normal back-stack entry a plain navigateUp() can pop, unaffected by the
        // Compose migration.
        navController.navigate(R.id.playlistsFragment)
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("playlistId" to "pl1", "playlistName" to "Road Trip"),
        )
        navController.navigateUp()
        assertEquals(R.id.playlistsFragment, navController.currentDestination?.id)
    }

    @Test
    fun `a plain browsing track collection still resolves on the same destination, no playlist args`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("id" to "al1", "isAlbum" to true, "name" to "Kind of Blue"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
        assertEquals(null, navController.currentBackStackEntry?.arguments?.getString("playlistId"))
    }
}
