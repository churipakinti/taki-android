/*
 * ArtistDetailNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artist

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
 * Artist Detail keeps the existing `artistDetailFragment` destination, its three arguments and
 * its routes into `trackCollectionFragment` (an album) and back to itself (a similar artist) -
 * no `navigation-compose`, no new ids (issue #10 phase 4C). The migration is a presentation
 * swap inside the same Fragment.
 */
@RunWith(RobolectricTestRunner::class)
class ArtistDetailNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `the artist destination and its album route still exist`() {
        val graph = navController.graph
        assertNotNull("artist detail", graph.findNode(R.id.artistDetailFragment))
        assertNotNull("album detail target", graph.findNode(R.id.trackCollectionFragment))
    }

    @Test
    fun `an artist caller opens artistDetailFragment with its three arguments`() {
        navController.navigate(
            R.id.artistDetailFragment,
            bundleOf(
                "artistId" to "ar1",
                "artistName" to "Héroes del Silencio",
                "artistCoverArt" to "cover-ar1",
            ),
        )
        assertEquals(R.id.artistDetailFragment, navController.currentDestination?.id)
        assertEquals(
            "ar1",
            navController.currentBackStackEntry?.arguments?.getString("artistId"),
        )
    }

    @Test
    fun `a tapped-artist-name caller may arrive without a cover-art id (issue 16)`() {
        navController.navigate(
            R.id.artistDetailFragment,
            bundleOf("artistId" to "ar1", "artistName" to "Someone"),
        )
        assertEquals(R.id.artistDetailFragment, navController.currentDestination?.id)
    }

    @Test
    fun `opening an album goes to the album destination and back returns to the artist`() {
        navController.navigate(
            R.id.artistDetailFragment,
            bundleOf("artistId" to "ar1", "artistName" to "Héroes del Silencio"),
        )
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("id" to "al1", "isAlbum" to true, "name" to "Senderos de Traición"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)

        navController.popBackStack()
        assertEquals(R.id.artistDetailFragment, navController.currentDestination?.id)
    }

    @Test
    fun `opening a similar artist stacks another artist detail and back returns`() {
        navController.navigate(
            R.id.artistDetailFragment,
            bundleOf("artistId" to "ar1", "artistName" to "First"),
        )
        navController.navigate(
            R.id.artistDetailFragment,
            bundleOf("artistId" to "ar2", "artistName" to "Second", "artistCoverArt" to "c2"),
        )
        assertEquals("ar2", navController.currentBackStackEntry?.arguments?.getString("artistId"))

        navController.popBackStack()
        assertEquals("ar1", navController.currentBackStackEntry?.arguments?.getString("artistId"))
    }
}
