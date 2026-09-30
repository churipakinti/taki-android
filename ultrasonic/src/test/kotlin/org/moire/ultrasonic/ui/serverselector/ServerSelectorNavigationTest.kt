/*
 * ServerSelectorNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import android.content.Context
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.ServerSelectorFragmentDirections
import org.robolectric.RobolectricTestRunner

/**
 * Server Selector -> Edit Server (post-issue-#10 residual migration, phase 5A2) keeps the
 * existing `serverSelectorFragment`/`editServerFragment` destinations and the `index` argument
 * contract completely unchanged: Add passes the `-1` "new server" sentinel, Edit passes the
 * tapped row's on-screen position (the exact value the legacy `ServerRowAdapter` fed in) - not
 * `ServerSetting.id`. See the phase 5A2 audit for why this - not a stable id - is the value
 * `EditServerFragment` actually expects.
 */
@RunWith(RobolectricTestRunner::class)
class ServerSelectorNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        navController.setGraph(R.navigation.navigation_graph)
    }

    @Test
    fun `the server selector and edit server destinations still exist`() {
        val graph = navController.graph
        assertNotNull("server selector", graph.findNode(R.id.serverSelectorFragment))
        assertNotNull("edit server", graph.findNode(R.id.editServerFragment))
    }

    @Test
    fun `Add navigates to edit server with the new-server sentinel index of -1`() {
        navController.navigate(R.id.serverSelectorFragment)
        navController.navigate(ServerSelectorFragmentDirections.toEditServer(-1))
        assertEquals(R.id.editServerFragment, navController.currentDestination?.id)
        assertEquals(-1, navController.currentBackStackEntry?.arguments?.getInt("index"))
    }

    @Test
    fun `Edit navigates to edit server with the tapped row's on-screen position`() {
        navController.navigate(R.id.serverSelectorFragment)
        navController.navigate(ServerSelectorFragmentDirections.toEditServer(2))
        assertEquals(R.id.editServerFragment, navController.currentDestination?.id)
        assertEquals(2, navController.currentBackStackEntry?.arguments?.getInt("index"))
    }

    @Test
    fun `back from edit server returns to the server selector`() {
        navController.navigate(R.id.serverSelectorFragment)
        navController.navigate(ServerSelectorFragmentDirections.toEditServer(-1))
        navController.popBackStack()
        assertEquals(R.id.serverSelectorFragment, navController.currentDestination?.id)
    }
}
