/*
 * LibraryNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.library

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
 * Every destination a Library row routes to still resolves in the existing Fragment
 * `navigation_graph.xml` and accepts the arguments `MainFragment` passes - no
 * `navigation-compose`, no new ids (migration plan section 3).
 */
@RunWith(RobolectricTestRunner::class)
class LibraryNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `every Library destination exists in the graph`() {
        val graph = navController.graph
        assertNotNull("liked songs / songs", graph.findNode(R.id.trackCollectionFragment))
        assertNotNull("liked albums / albums", graph.findNode(R.id.albumListFragment))
        assertNotNull("artists", graph.findNode(R.id.artistListFragment))
        assertNotNull("playlists", graph.findNode(R.id.playlistsFragment))
        assertNotNull("downloads", graph.findNode(R.id.downloadsFragment))
        assertNotNull("genres", graph.findNode(R.id.selectGenreFragment))
        assertNotNull("box sets", graph.findNode(R.id.collectionListFragment))
    }

    @Test
    fun `Liked Songs opens the starred track collection`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("getStarred" to true, "name" to "Liked Songs"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `Liked Albums opens the starred album list`() {
        navController.navigate(
            R.id.albumListFragment,
            bundleOf("type" to AlbumListType.STARRED, "title" to "Liked Albums"),
        )
        assertEquals(R.id.albumListFragment, navController.currentDestination?.id)
    }

    @Test
    fun `Albums opens the by-name album list`() {
        navController.navigate(
            R.id.albumListFragment,
            bundleOf("type" to AlbumListType.SORTED_BY_NAME),
        )
        assertEquals(R.id.albumListFragment, navController.currentDestination?.id)
    }

    @Test
    fun `Songs opens the library-root track collection`() {
        navController.navigate(R.id.trackCollectionFragment, bundleOf("libraryRoot" to true))
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `Artists, Genres and Box Sets resolve to their destinations`() {
        navController.navigate(R.id.artistListFragment)
        assertEquals(R.id.artistListFragment, navController.currentDestination?.id)

        navController.navigate(R.id.selectGenreFragment)
        assertEquals(R.id.selectGenreFragment, navController.currentDestination?.id)

        navController.navigate(R.id.collectionListFragment)
        assertEquals(R.id.collectionListFragment, navController.currentDestination?.id)
    }

    @Test
    fun `Playlists and Downloads pop back to Library`() {
        navController.navigate(R.id.mainFragment)
        assertEquals(R.id.mainFragment, navController.currentDestination?.id)

        navController.navigate(R.id.playlistsFragment)
        assertEquals(R.id.playlistsFragment, navController.currentDestination?.id)
        navController.popBackStack()
        assertEquals(R.id.mainFragment, navController.currentDestination?.id)

        navController.navigate(R.id.downloadsFragment)
        assertEquals(R.id.downloadsFragment, navController.currentDestination?.id)
        navController.popBackStack()
        assertEquals(R.id.mainFragment, navController.currentDestination?.id)
    }
}
