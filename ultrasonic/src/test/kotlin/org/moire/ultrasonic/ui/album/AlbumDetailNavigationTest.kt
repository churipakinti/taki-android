/*
 * AlbumDetailNavigationTest.kt
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.robolectric.RobolectricTestRunner

/**
 * Album Detail keeps the existing `trackCollectionFragment` destination, its arguments and the
 * `toArtistDetail` route (issue #16) - no `navigation-compose`, no new ids (migration plan
 * section 3). The Compose migration is a presentation swap inside the same Fragment.
 */
@RunWith(RobolectricTestRunner::class)
class AlbumDetailNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `the album destination and the artist-detail route still exist`() {
        val graph = navController.graph
        assertNotNull("album detail", graph.findNode(R.id.trackCollectionFragment))
        assertNotNull("artist detail (issue #16)", graph.findNode(R.id.artistDetailFragment))
    }

    @Test
    fun `an album caller opens trackCollectionFragment in album mode`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("id" to "al1", "isAlbum" to true, "name" to "Kind of Blue", "parentId" to "ar1"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `the autoplay album argument is accepted unchanged`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("id" to "al1", "isAlbum" to true, "autoPlay" to true, "name" to "Kind of Blue"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `the tappable artist opens artist detail and back returns to the album`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("id" to "al1", "isAlbum" to true, "name" to "Kind of Blue"),
        )
        navController.navigate(
            R.id.artistDetailFragment,
            bundleOf("artistId" to "ar1", "artistName" to "Miles Davis"),
        )
        assertEquals(R.id.artistDetailFragment, navController.currentDestination?.id)

        navController.popBackStack()
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `a non-album track collection still resolves on the same destination`() {
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("getStarred" to true, "name" to "Liked Songs"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }
}
