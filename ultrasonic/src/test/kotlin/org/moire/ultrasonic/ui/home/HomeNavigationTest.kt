/*
 * HomeNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

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
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.robolectric.RobolectricTestRunner

/**
 * Proves every destination the Compose Home screen routes to still resolves in the existing
 * Fragment `navigation_graph.xml` and can be reached with the arguments `HomeFragment`
 * passes - all without `navigation-compose` (migration plan section 3).
 */
@RunWith(RobolectricTestRunner::class)
class HomeNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `every Home destination exists in the graph`() {
        val graph = navController.graph
        assertNotNull("albums list", graph.findNode(R.id.albumListFragment))
        assertNotNull("artists list", graph.findNode(R.id.artistListFragment))
        assertNotNull("playlists", graph.findNode(R.id.playlistsFragment))
        assertNotNull("tracks / album detail / songs / daily mix", graph.findNode(R.id.trackCollectionFragment))
    }

    @Test
    fun `opening an album from a shelf carries the album arguments`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf(
                "id" to "album-42",
                "isAlbum" to true,
                "name" to "Some Album",
                "parentId" to "parent-42",
            ),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `the Songs shortcut opens the library-root track collection`() {
        navController.navigate(R.id.trackCollectionFragment, bundleOf("libraryRoot" to true))
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `the daily-mix card opens the mix track collection`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("dailyMix" to true, "name" to "Daily Mix"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `the Albums shortcut opens the album list`() {
        navController.navigate(R.id.albumListFragment, bundleOf("type" to AlbumListType.NEWEST))
        assertEquals(R.id.albumListFragment, navController.currentDestination?.id)
    }

    @Test
    fun `the Playlists shortcut opens playlists and pops back Home`() {
        navController.navigate(R.id.playlistsFragment)
        assertEquals(R.id.playlistsFragment, navController.currentDestination?.id)

        navController.popBackStack()
        assertEquals(R.id.homeFragment, navController.currentDestination?.id)
    }
}
