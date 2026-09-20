/*
 * GenreListNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.genrelist

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
 * Genres List (issue #10 phase 4G2) keeps the existing `selectGenreFragment` destination
 * unchanged (no arguments, same id - only its backing class moved from
 * `org.moire.ultrasonic.fragment.legacy.SelectGenreFragment` to
 * `org.moire.ultrasonic.fragment.GenreListFragment`, the same package move Album/Artist/
 * Playlists List made) and the existing `trackCollectionFragment` destination for the already-
 * Compose Genre Tracks screen (phase 4F2) with its `genreName`/`size`/`offset` arguments
 * preserved exactly.
 */
@RunWith(RobolectricTestRunner::class)
class GenreListNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `the genres list and genre tracks destinations still exist`() {
        val graph = navController.graph
        assertNotNull("genres list", graph.findNode(R.id.selectGenreFragment))
        assertNotNull("genre tracks (shared trackCollectionFragment)", graph.findNode(R.id.trackCollectionFragment))
    }

    @Test
    fun `the genres list destination still takes no arguments`() {
        navController.navigate(R.id.selectGenreFragment)
        assertEquals(R.id.selectGenreFragment, navController.currentDestination?.id)
        assertNull(navController.currentBackStackEntry?.arguments?.getString("genreName"))
    }

    @Test
    fun `opening a genre preserves its name, size and offset into Genre Tracks`() {
        navController.navigate(R.id.selectGenreFragment)
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("genreName" to "Rock", "size" to 25, "offset" to 0),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
        val args = navController.currentBackStackEntry?.arguments
        assertEquals("Rock", args?.getString("genreName"))
        assertEquals(25, args?.getInt("size"))
        assertEquals(0, args?.getInt("offset"))
    }

    @Test
    fun `opening a genre does not set isAlbum, isArtist, or libraryRoot`() {
        navController.navigate(R.id.selectGenreFragment)
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("genreName" to "Rock", "size" to 25, "offset" to 0),
        )
        val args = navController.currentBackStackEntry?.arguments
        assertEquals(false, args?.getBoolean("isAlbum"))
        assertEquals(false, args?.getBoolean("isArtist"))
        assertEquals(false, args?.getBoolean("libraryRoot"))
    }

    @Test
    fun `back from Genre Tracks returns to the genres list`() {
        navController.navigate(R.id.selectGenreFragment)
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("genreName" to "Rock", "size" to 25, "offset" to 0),
        )
        navController.popBackStack()
        assertEquals(R.id.selectGenreFragment, navController.currentDestination?.id)
    }
}
