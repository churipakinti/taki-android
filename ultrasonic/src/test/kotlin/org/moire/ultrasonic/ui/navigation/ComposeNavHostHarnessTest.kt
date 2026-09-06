/*
 * ComposeNavHostHarnessTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.navigation

import android.content.Context
import androidx.core.os.bundleOf
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.robolectric.RobolectricTestRunner

/**
 * Proves that the existing flat Fragment navigation graph (`res/navigation/navigation_graph.xml`)
 * can be driven from plain code with `TestNavHostController` and no `navigation-compose`.
 *
 * This is the harness the migrated, Compose-hosting Fragments of issue #10 rely on: they stay
 * `<fragment>` nodes with unchanged ids / arguments / actions, so a `TestNavHostController` can
 * exercise their routing without any Compose navigation library.
 */
@RunWith(RobolectricTestRunner::class)
class ComposeNavHostHarnessTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // TestNavHostController supplies a stand-in navigator for every destination type in the
        // graph (fragment, dialog, ...), so the real Fragment graph can be driven from plain
        // code - no ComposeNavigator, no navigation-compose.
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `graph inflates and starts at Home`() {
        assertEquals(R.id.homeFragment, navController.currentDestination?.id)
    }

    @Test
    fun `the top-level and contextual destinations are all present`() {
        val graph = navController.graph
        assertNotNull("homeFragment missing", graph.findNode(R.id.homeFragment))
        assertNotNull("mainFragment (Library) missing", graph.findNode(R.id.mainFragment))
        assertNotNull("searchFragment missing", graph.findNode(R.id.searchFragment))
        assertNotNull(
            "trackCollectionFragment missing",
            graph.findNode(R.id.trackCollectionFragment)
        )
        assertNotNull("artistDetailFragment missing", graph.findNode(R.id.artistDetailFragment))
        assertNotNull("playerFragment missing", graph.findNode(R.id.playerFragment))
    }

    @Test
    fun `contextual navigation album - artist - player and back-stack pop work`() {
        val albumArgs = bundleOf("id" to "album-1", "isAlbum" to true, "name" to "An Album")
        val artistArgs = bundleOf("artistId" to "artist-1", "artistName" to "An Artist")

        navController.navigate(R.id.searchFragment)
        assertEquals(R.id.searchFragment, navController.currentDestination?.id)

        navController.navigate(R.id.trackCollectionFragment, albumArgs)
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)

        navController.navigate(R.id.artistDetailFragment, artistArgs)
        assertEquals(R.id.artistDetailFragment, navController.currentDestination?.id)

        navController.navigate(R.id.playerFragment)
        assertEquals(R.id.playerFragment, navController.currentDestination?.id)

        navController.popBackStack()
        assertEquals(R.id.artistDetailFragment, navController.currentDestination?.id)

        navController.popBackStack()
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `popping back to a tab root mirrors the bottom-nav behaviour`() {
        val artistArgs = bundleOf("artistId" to "a", "artistName" to "A")

        navController.navigate(R.id.mainFragment)
        navController.navigate(R.id.artistDetailFragment, artistArgs)
        assertEquals(R.id.artistDetailFragment, navController.currentDestination?.id)

        // NavigationActivity.switchToBottomNavTab: popBackStack(tabRootId, inclusive = false).
        val popped = navController.popBackStack(R.id.mainFragment, false)
        assertTrue(popped)
        assertEquals(R.id.mainFragment, navController.currentDestination?.id)
    }
}
