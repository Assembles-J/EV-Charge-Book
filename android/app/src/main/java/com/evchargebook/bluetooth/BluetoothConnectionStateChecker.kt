package com.evchargebook.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothConnectionStateChecker {
    fun check(context: Context, deviceAddress: String?, onResult: (Boolean) -> Unit) {
        if (deviceAddress.isNullOrBlank() || !hasPermission(context)) {
            onResult(false)
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            onResult(false)
            return
        }
        val profiles = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        var remaining = profiles.size
        var matched = false

        fun finishOne(found: Boolean) {
            if (matched) return
            if (found) {
                matched = true
                onResult(true)
                return
            }
            remaining -= 1
            if (remaining == 0) onResult(false)
        }

        profiles.forEach { profile ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profileType: Int, proxy: BluetoothProfile) {
                    val found = runCatching {
                        proxy.connectedDevices.any { it.address.equals(deviceAddress, ignoreCase = true) }
                    }.getOrDefault(false)
                    runCatching { adapter.closeProfileProxy(profileType, proxy) }
                    finishOne(found)
                }

                override fun onServiceDisconnected(profileType: Int) {
                    finishOne(false)
                }
            }
            val requested = runCatching { adapter.getProfileProxy(context.applicationContext, listener, profile) }.getOrDefault(false)
            if (!requested) finishOne(false)
        }
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
}
