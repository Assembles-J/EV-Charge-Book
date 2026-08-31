package com.evchargebook.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.evchargebook.autotrip.AutoTripPromptCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !in setOf(
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
            ) || !hasBluetoothPermission(context)
        ) return

        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val coordinator = AutoTripPromptCoordinator(context.applicationContext)
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED ->
                        coordinator.onBluetoothConnected(device.address, device.name)

                    BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                        coordinator.onBluetoothDisconnected(device.address)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun hasBluetoothPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
}
