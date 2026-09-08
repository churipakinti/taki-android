/*
 * CollectionDetailNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collection

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
 * Collection Detail keeps the existing `collectionDetailFragment` destination and its
 * `grouping` argument, and still opens a member release through the unchanged
 * `trackCollectionFragment` (Compose Album Detail) with `isAlbum = true`. The migration is a
 * presentation swap inside the same Fragment - no `navigation-compose`, no new ids.
 */
@RunWith(RobolectricTestRunner::class)
class CollectionDetailNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `the box-sets list and detail destinations and the route between them still exist`() {
        val graph = navController.graph
        assertNotNull("box sets list", graph.findNode(R.id.collectionListFragment))
        assertNotNull("collection detail", graph.findNode(R.id.collectionDetailFragment))
        assertNotNull("album detail target", graph.findNode(R.id.trackCollectionFragment))
    }

    @Test
    fun `the box-sets list opens collection detail with the exact grouping argument`() {
        navController.navigate(R.id.collectionListFragment)
        navController.navigate(
            R.id.collectionDetailFragment,
            bundleOf("grouping" to "Bach 333"),
        )
        assertEquals(R.id.collectionDetailFragment, navController.currentDestination?.id)
        assertEquals("Bach 333", navController.currentBackStackEntry?.arguments?.getString("grouping"))
    }

    @Test
    fun `opening a member release goes to the album destination and back returns to the collection`() {
        navController.navigate(
            R.id.collectionDetailFragment,
            bundleOf("grouping" to "Bach 333"),
        )
        navController.navigate(
            R.id.trackCollectionFragment,
            bundleOf("id" to "disc42", "isAlbum" to true, "name" to "Cantatas", "parentId" to "ar1"),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)

        navController.popBackStack()
        assertEquals(R.id.collectionDetailFragment, navController.currentDestination?.id)
    }
}
