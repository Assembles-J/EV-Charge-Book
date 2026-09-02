package com.evchargebook.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.evchargebook.autotrip.AutoTripCandidateResult
import com.evchargebook.vehicle.presence.VehiclePresenceDispatchResult
import com.evchargebook.vehicle.presence.VehiclePresenceDispatcher
import com.evchargebook.vehicle.presence.VehiclePresenceEvent
import com.evchargebook.vehicle.presence.VehiclePresenceSource
import com.evchargebook.vehicle.presence.VehiclePresenceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                resolveCandidate(context, deviceAddress, onResult)
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
            val requested = runCatching {
                adapter.getProfileProxy(context.applicationContext, listener, profile)
            }.getOrDefault(false)
            if (!requested) finishOne(false)
        }
    }

    private fun resolveCandidate(
        context: Context,
        deviceAddress: String,
        onResult: (Boolean) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val dispatchResult = runCatching {
                VehiclePresenceDispatcher.forAutoTrip(context).dispatch(
                    VehiclePresenceEvent(
                        state = VehiclePresenceState.CONNECTED,
                        source = VehiclePresenceSource.FOREGROUND_CONNECTION_CHECK,
                        deviceAddress = deviceAddress,
                    )
                )
            }.getOrNull()
            val candidate = (dispatchResult as? VehiclePresenceDispatchResult.Candidate)?.result
            val shouldShowForegroundPrompt =
                candidate is AutoTripCandidateResult.Created && candidate.notificationVisible
            ContextCompat.getMainExecutor(context).execute {
                onResult(shouldShowForegroundPrompt)
            }
        }
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
}
