/*
 * DownloadsNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.downloads

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
 * Downloads (issue #10 phase 4G3) keeps the existing `downloadsFragment` destination (no
 * arguments, same id - only its backing class changed to a Compose host) and its single outgoing
 * route, `toDownloadedAlbum` -> `downloadedAlbumFragment` with the album `id` and `name`
 * arguments preserved exactly. The album row is the only navigation target: there are no queue
 * items, folders or per-track routes on this screen.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadsNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `the downloads and downloaded album destinations still exist`() {
        val graph = navController.graph
        assertNotNull("downloads", graph.findNode(R.id.downloadsFragment))
        assertNotNull("downloaded album", graph.findNode(R.id.downloadedAlbumFragment))
    }

    @Test
    fun `the downloads destination is reachable and takes no arguments`() {
        navController.navigate(R.id.downloadsFragment)
        assertEquals(R.id.downloadsFragment, navController.currentDestination?.id)
    }

    @Test
    fun `opening an album preserves its id and name into the downloaded album screen`() {
        navController.navigate(R.id.downloadsFragment)
        navController.navigate(
            R.id.downloadedAlbumFragment,
            bundleOf("id" to "album-42", "name" to "Abbey Road"),
        )
        assertEquals(R.id.downloadedAlbumFragment, navController.currentDestination?.id)
        val args = navController.currentBackStackEntry?.arguments
        assertEquals("album-42", args?.getString("id"))
        assertEquals("Abbey Road", args?.getString("name"))
    }

    @Test
    fun `the toDownloadedAlbum action still targets the downloaded album destination`() {
        navController.navigate(R.id.downloadsFragment)
        navController.navigate(R.id.toDownloadedAlbum, bundleOf("id" to "a", "name" to "n"))
        assertEquals(R.id.downloadedAlbumFragment, navController.currentDestination?.id)
    }

    @Test
    fun `back from a downloaded album returns to Downloads`() {
        navController.navigate(R.id.downloadsFragment)
        navController.navigate(
            R.id.downloadedAlbumFragment,
            bundleOf("id" to "a", "name" to "n"),
        )
        navController.popBackStack()
        assertEquals(R.id.downloadsFragment, navController.currentDestination?.id)
    }
}
