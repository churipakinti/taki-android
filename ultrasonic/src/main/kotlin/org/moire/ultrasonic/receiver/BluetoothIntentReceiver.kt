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
import timber.log.Timber

/**
 * Resume or pause playback on Bluetooth A2DP (audio device, e.g. headphones/speakers/car audio)
 * connect/disconnect. Deliberately A2DP-only, not any paired Bluetooth device (a fitness tracker
 * connecting shouldn't start music) -- this used to be user-configurable, but resuming/pausing
 * for actual audio devices is expected default behavior, not a decision worth asking for.
 */
class BluetoothIntentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        val device = intent.getBluetoothDevice()
        val action = intent.action

        // Whether to log the name of the bluetooth device
        val name = device.getNameSafely()
        Timber.d("Bluetooth device: $name; State: $state; Action: $action")

        val shouldResume = state == BluetoothA2dp.STATE_CONNECTED
        val shouldPause = state == BluetoothA2dp.STATE_DISCONNECTED

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
