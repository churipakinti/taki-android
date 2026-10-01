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
 * Server Selector -> Edit Server (post-issue-#10 residual migration, phase 5A3) keeps the
 * existing `serverSelectorFragment`/`editServerFragment` destinations, but the nav argument
 * contract changed: the legacy `index` argument (interpreted as `ServerSetting.index`, fed the
 * tapped row's on-screen position, with no enforced relationship to the DB column it was looked
 * up against - see the phase 5A3 audit) was replaced with `serverId`
 * ([org.moire.ultrasonic.data.ServerSetting.id] directly, or `-1` for the unchanged "new server"
 * sentinel). `EditServerFragment` now resolves an existing server via `ServerSettingsModel
 * .getServerSettingById`, never a position-derived lookup.
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
    fun `the obsolete index argument no longer exists on the edit server destination`() {
        val argument = navController.graph.findNode(R.id.editServerFragment)?.arguments?.get("index")
        assertEquals(null, argument)
    }

    @Test
    fun `Add navigates to edit server with the new-server sentinel serverId of -1`() {
        navController.navigate(R.id.serverSelectorFragment)
        navController.navigate(ServerSelectorFragmentDirections.toEditServer(-1))
        assertEquals(R.id.editServerFragment, navController.currentDestination?.id)
        assertEquals(-1, navController.currentBackStackEntry?.arguments?.getInt("serverId"))
    }

    @Test
    fun `Edit navigates to edit server with the exact tapped row's stable server id`() {
        // Regression guard (issue #10 phase 5A3): this must be ServerSetting.id, never a
        // display-position-derived value - opening the second or third row must resolve the
        // correct server independent of on-screen ordering.
        navController.navigate(R.id.serverSelectorFragment)
        navController.navigate(ServerSelectorFragmentDirections.toEditServer(42))
        assertEquals(R.id.editServerFragment, navController.currentDestination?.id)
        assertEquals(42, navController.currentBackStackEntry?.arguments?.getInt("serverId"))
    }

    @Test
    fun `opening the second configured server passes its own id, not the first server's`() {
        navController.navigate(R.id.serverSelectorFragment)
        navController.navigate(ServerSelectorFragmentDirections.toEditServer(7))
        navController.popBackStack()
        navController.navigate(ServerSelectorFragmentDirections.toEditServer(3))
        assertEquals(3, navController.currentBackStackEntry?.arguments?.getInt("serverId"))
    }

    @Test
    fun `back from edit server returns to the server selector`() {
        navController.navigate(R.id.serverSelectorFragment)
        navController.navigate(ServerSelectorFragmentDirections.toEditServer(-1))
        navController.popBackStack()
        assertEquals(R.id.serverSelectorFragment, navController.currentDestination?.id)
    }
}
