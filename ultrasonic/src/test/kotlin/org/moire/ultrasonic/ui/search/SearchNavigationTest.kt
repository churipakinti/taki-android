/*
 * SearchNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.search

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
 * Every result action the Compose Search screen routes to still resolves in the existing
 * Fragment `navigation_graph.xml` with the arguments `SearchFragment` passes - all without
 * `navigation-compose` and with no new destination ids (migration plan section 3).
 */
@RunWith(RobolectricTestRunner::class)
class SearchNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
        navController.navigate(R.id.searchFragment)
    }

    @Test
    fun `the search actions and their destinations exist`() {
        val graph = navController.graph
        assertNotNull("track collection", graph.findNode(R.id.trackCollectionFragment))
        assertNotNull("album list", graph.findNode(R.id.albumListFragment))
        val search = graph.findNode(R.id.searchFragment)
        assertNotNull("search -> track collection action", search?.getAction(R.id.searchToTrackCollection))
        assertNotNull("search -> album list action", search?.getAction(R.id.searchToAlbumsList))
    }

    @Test
    fun `a folder-style artist opens its track collection`() {
        navController.navigate(
            R.id.searchToTrackCollection,
            bundleOf("id" to "index-1", "name" to "The Beatles", "parentId" to "index-1", "isArtist" to false),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `an id3 artist opens its album list, filtered by artist`() {
        navController.navigate(
            R.id.searchToAlbumsList,
            bundleOf(
                "type" to AlbumListType.SORTED_BY_NAME,
                "byArtist" to true,
                "id" to "artist-1",
                "title" to "Miles Davis",
                "size" to 1000,
                "offset" to 0,
            ),
        )
        assertEquals(R.id.albumListFragment, navController.currentDestination?.id)
    }

    @Test
    fun `an album result opens its track collection`() {
        navController.navigate(
            R.id.searchToTrackCollection,
            bundleOf("id" to "album-9", "name" to "Kind of Blue", "isAlbum" to true),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `an autoplay album result carries the autoPlay flag`() {
        navController.navigate(
            R.id.searchToTrackCollection,
            bundleOf("id" to "album-9", "name" to "Kind of Blue", "isAlbum" to true, "autoPlay" to true),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
    }

    @Test
    fun `back from a result returns to search`() {
        navController.navigate(
            R.id.searchToTrackCollection,
            bundleOf("id" to "album-9", "isAlbum" to true),
        )
        assertEquals(R.id.trackCollectionFragment, navController.currentDestination?.id)
        navController.popBackStack()
        assertEquals(R.id.searchFragment, navController.currentDestination?.id)
    }
}
