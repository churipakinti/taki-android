/*
 * BluetoothIntentReceiver.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.receiver

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

/**
 * Resume or pause playback on Bluetooth A2DP connect/disconnect.
 */
class BluetoothIntentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        val device = intent.getBluetoothDevice()
        val action = intent.action

        // Whether to log the name of the bluetooth device
        val name = device.getNameSafely()
        Timber.d("Bluetooth device: $name; State: $state; Action: $action")

        // In these flags we store what kind of device (any or a2dp) has (dis)connected
        var connectionStatus = Constants.PREFERENCE_VALUE_DISABLED
        var disconnectionStatus = Constants.PREFERENCE_VALUE_DISABLED

        // First check for general devices
        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                connectionStatus = Constants.PREFERENCE_VALUE_ALL
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED
            -> {
                disconnectionStatus = Constants.PREFERENCE_VALUE_ALL
            }
        }

        // Then check for A2DP devices
        when (state) {
            BluetoothA2dp.STATE_CONNECTED -> {
                connectionStatus = Constants.PREFERENCE_VALUE_A2DP
            }

            BluetoothA2dp.STATE_DISCONNECTED -> {
                disconnectionStatus = Constants.PREFERENCE_VALUE_A2DP
            }
        }

        // Flags to store which action should be performed
        var shouldResume = false
        var shouldPause = false

        // Now check the settings and set the appropriate flags
        when (Settings.resumeOnBluetoothDevice) {
            Constants.PREFERENCE_VALUE_ALL -> {
                shouldResume = (connectionStatus != Constants.PREFERENCE_VALUE_DISABLED)
            }

            Constants.PREFERENCE_VALUE_A2DP -> {
                shouldResume = (connectionStatus == Constants.PREFERENCE_VALUE_A2DP)
            }
        }

        when (Settings.pauseOnBluetoothDevice) {
            Constants.PREFERENCE_VALUE_ALL -> {
                shouldPause = (disconnectionStatus != Constants.PREFERENCE_VALUE_DISABLED)
            }

            Constants.PREFERENCE_VALUE_A2DP -> {
                shouldPause = (disconnectionStatus == Constants.PREFERENCE_VALUE_A2DP)
            }
        }

        if (shouldResume) {
            Timber.i("Connected to Bluetooth device $name; Resuming playback.")
            context.sendBroadcast(
                Intent(Constants.CMD_RESUME_OR_PLAY)
                    .setPackage(context.packageName)
            )
        }

        if (shouldPause) {
            Timber.i("Disconnected from Bluetooth device $name; Requesting pause.")
            context.sendBroadcast(
                Intent(Constants.CMD_PAUSE)
                    .setPackage(context.packageName)
            )
        }
    }

    private fun BluetoothDevice?.getNameSafely(): String? {
        val logBluetoothName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (
                ActivityCompat.checkSelfPermission(
                    UApp.applicationContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
                )

        return if (logBluetoothName) this?.name else "Unknown"
    }

    private fun Intent.getBluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
