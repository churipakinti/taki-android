/*
 * CollectionListNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collectionlist

import android.content.Context
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.CollectionListFragmentDirections
import org.robolectric.RobolectricTestRunner

/**
 * Box Sets List -> Collection Detail (post-issue-#10 residual migration, phase 5A1) keeps the
 * existing `collectionListFragment`/`collectionDetailFragment` destinations and the
 * `grouping` argument contract completely unchanged: [CollectionListFragment][
 * org.moire.ultrasonic.fragment.CollectionListFragment] navigates with exactly the resolved
 * `MusicCollection.title` - `CollectionDetailModel`/`CollectionDetailViewModel` re-filters by it,
 * so this is not a display-only value.
 */
@RunWith(RobolectricTestRunner::class)
class CollectionListNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `the box sets list and collection detail destinations still exist`() {
        val graph = navController.graph
        assertNotNull("box sets list", graph.findNode(R.id.collectionListFragment))
        assertNotNull("collection detail", graph.findNode(R.id.collectionDetailFragment))
    }

    @Test
    fun `opening a collection passes its exact title as the grouping argument`() {
        navController.navigate(R.id.collectionListFragment)
        navController.navigate(
            CollectionListFragmentDirections.toCollectionDetail("Bach 333"),
        )
        assertEquals(R.id.collectionDetailFragment, navController.currentDestination?.id)
        assertEquals("Bach 333", navController.currentBackStackEntry?.arguments?.getString("grouping"))
    }

    @Test
    fun `back from Collection Detail returns to the box sets list`() {
        navController.navigate(R.id.collectionListFragment)
        navController.navigate(CollectionListFragmentDirections.toCollectionDetail("Bach 333"))
        navController.popBackStack()
        assertEquals(R.id.collectionListFragment, navController.currentDestination?.id)
    }
}
