/*
 * BluetoothIntentReceiverTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.receiver

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.util.Constants
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * AA-05. The receiver is exported (the Bluetooth stack delivers to it) and an app can address it
 * explicitly, so it must act only on the A2DP connection-state action and only ever relay an internal,
 * same-package resume/pause to the (non-exported) command receiver.
 */
@RunWith(RobolectricTestRunner::class)
class BluetoothIntentReceiverTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
    }

    private fun deliver(action: String, state: Int): List<Intent> {
        val intent = Intent(action).putExtra(BluetoothProfile.EXTRA_STATE, state)
        BluetoothIntentReceiver().onReceive(context, intent)
        return shadowOf(context as android.app.Application).broadcastIntents
    }

    @Test
    fun `a2dp connect resumes through an explicit same-package broadcast`() {
        val sent = deliver(A2DP, BluetoothA2dp.STATE_CONNECTED)

        val relayed = sent.last()
        assertEquals(Constants.CMD_RESUME_OR_PLAY, relayed.action)
        assertEquals(context.packageName, relayed.`package`)
    }

    @Test
    fun `a2dp disconnect pauses through an explicit same-package broadcast`() {
        val sent = deliver(A2DP, BluetoothA2dp.STATE_DISCONNECTED)

        assertEquals(Constants.CMD_PAUSE, sent.last().action)
        assertEquals(context.packageName, sent.last().`package`)
    }

    @Test
    fun `any other action is ignored even with a connected state extra`() {
        val before = shadowOf(context as android.app.Application).broadcastIntents.size

        for (action in listOf("x.fake.action", "android.bluetooth.device.action.ACL_CONNECTED", "")) {
            deliver(action, BluetoothA2dp.STATE_CONNECTED)
        }

        assertTrue(shadowOf(context as android.app.Application).broadcastIntents.size == before)
    }

    private companion object {
        const val A2DP = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
    }
}
