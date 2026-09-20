/*
 * AlbumDetailModesNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import android.content.Context
import androidx.core.os.bundleOf
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.robolectric.RobolectricTestRunner

/**
 * Navigation contract for the converged Album Detail modes (issue #10 phase 4H1). The XML graph
 * stays authoritative: `downloadedAlbumFragment` (id, name) and `trackCollectionFragment` for
 * folder albums (id, isAlbum, name, parentId) keep their destinations and arguments, and a folder
 * album can open a sub-folder as another folder album.
 */
@RunWith(RobolectricTestRunner::class)
class AlbumDetailModesNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `downloads to a downloaded album keeps its id and name and backs out to Downloads`() {
        navController.navigate(R.id.downloadsFragment)
        navController.navigate(
            R.id.downloadedAlbumFragment,
            bundleOf("id" to "album-1", "name" to "Cheese"),
        )
        assertEquals(R.id.downloadedAlbumFragment, navController.currentDestination?.id)
        val args = navController.currentBackStackEntry?.arguments
        assertEquals("album-1", args?.getString("id"))
        assertEquals("Cheese", args?.getString("name"))

        navController.popBackStack()
        assertEquals(R.id.downloadsFragment, navController.currentDestination?.id)
    }

    @Test
    fun `a folder album is reachable with its id, isAlbum, name and parentId`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("id" to "dir-1", "isAlbum" to true, "name" to "Live", "parentId" to "root"),
        )
        val args = navController.currentBackStackEntry?.arguments
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
        assertEquals("dir-1", args?.getString("id"))
        assertEquals(true, args?.getBoolean("isAlbum"))
        assertEquals("Live", args?.getString("name"))
        assertEquals("root", args?.getString("parentId"))
    }

    @Test
    fun `opening a sub-folder pushes another folder album and back returns to the first`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("id" to "dir-1", "isAlbum" to true, "name" to "Box"),
        )
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("id" to "dir-1-cd1", "isAlbum" to true, "name" to "CD1", "parentId" to "dir-1"),
        )
        assertEquals("dir-1-cd1", navController.currentBackStackEntry?.arguments?.getString("id"))

        navController.popBackStack()
        assertEquals("dir-1", navController.currentBackStackEntry?.arguments?.getString("id"))
    }

    @Test
    fun `the downloaded album and folder album share the Compose Album Detail chrome`() {
        // Both destinations hide the Material toolbar the same way (no visual jump between modes).
        assertTrue(NavigationActivity.isAlbumDetailDestination(R.id.trackCollectionFragment, true))
        assertTrue(NavigationActivity.isAlbumDetailDestination(R.id.downloadedAlbumFragment, true))
        assertTrue(
            NavigationActivity.hidesSupportActionBar(
                R.id.downloadedAlbumFragment,
                isLibraryTrackCollection = false,
                isAlbumDetail = true,
            ),
        )
        assertTrue(
            NavigationActivity.hidesSupportActionBar(
                R.id.trackCollectionFragment,
                isLibraryTrackCollection = false,
                isAlbumDetail = true,
            ),
        )
    }
}
