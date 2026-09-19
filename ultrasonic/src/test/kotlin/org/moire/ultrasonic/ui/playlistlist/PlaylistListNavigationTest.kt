/*
 * PlaylistListNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

import android.content.Context
import androidx.core.os.bundleOf
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.robolectric.RobolectricTestRunner

/**
 * Playlists List (issue #10 phase 4G1) keeps the existing `playlistsFragment` destination
 * unchanged (no arguments, same id - only its backing class moved from
 * `org.moire.ultrasonic.fragment.legacy.PlaylistsFragment` to
 * `org.moire.ultrasonic.fragment.PlaylistListFragment`, the same package move Album/Artist List
 * made in phases 4E1/4E2) and the existing `trackCollectionFragment` destination for Playlist
 * Detail with its `playlistId`/`playlistName`/`autoPlay`/`shuffle` arguments preserved exactly -
 * no `navigation-compose`, no merged destinations.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistListNavigationTest {

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
        assertNotNull("create playlist (unchanged, out of scope)", graph.findNode(R.id.createPlaylistFragment))
    }

    @Test
    fun `the playlists list destination still takes no arguments`() {
        navController.navigate(R.id.playlistsFragment)
        assertEquals(R.id.playlistsFragment, navController.currentDestination?.id)
        assertNull(navController.currentBackStackEntry?.arguments?.getString("playlistId"))
    }

    @Test
    fun `opening a playlist preserves its id and name into Playlist Detail`() {
        navController.navigate(R.id.playlistsFragment)
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("playlistId" to "p1", "playlistName" to "Road Trip"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
        val args = navController.currentBackStackEntry?.arguments
        assertEquals("p1", args?.getString("playlistId"))
        assertEquals("Road Trip", args?.getString("playlistName"))
    }

    @Test
    fun `Play Now and Play Shuffled from the context menu preserve autoPlay and shuffle`() {
        navController.navigate(R.id.playlistsFragment)
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf(
                "playlistId" to "p1",
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
    fun `back from Playlist Detail returns to the playlists list`() {
        navController.navigate(R.id.playlistsFragment)
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("playlistId" to "p1", "playlistName" to "Road Trip"),
        )
        navController.popBackStack()
        assertEquals(R.id.playlistsFragment, navController.currentDestination?.id)
    }

    @Test
    fun `creating a playlist navigates to the unchanged CreatePlaylistFragment with the typed name`() {
        navController.navigate(R.id.playlistsFragment)
        navController.navigate(R.id.createPlaylistFragment, bundleOf("playlistName" to "New Mix"))
        assertEquals(R.id.createPlaylistFragment, navController.currentDestination?.id)
        assertEquals("New Mix", navController.currentBackStackEntry?.arguments?.getString("playlistName"))
    }
}
