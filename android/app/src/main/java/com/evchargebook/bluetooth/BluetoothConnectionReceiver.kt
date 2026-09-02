package com.evchargebook.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.evchargebook.vehicle.presence.VehiclePresenceDispatcher
import com.evchargebook.vehicle.presence.VehiclePresenceEvent
import com.evchargebook.vehicle.presence.VehiclePresenceSource
import com.evchargebook.vehicle.presence.VehiclePresenceState
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
        val state = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> VehiclePresenceState.CONNECTED
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> VehiclePresenceState.DISCONNECTED
            else -> return
        }
        val event = VehiclePresenceEvent(
            state = state,
            source = VehiclePresenceSource.CLASSIC_ACL,
            deviceAddress = device.address,
            deviceName = device.name,
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                VehiclePresenceDispatcher.forAutoTrip(context).dispatch(event)
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
