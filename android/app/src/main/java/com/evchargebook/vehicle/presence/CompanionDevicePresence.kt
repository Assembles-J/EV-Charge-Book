package com.evchargebook.vehicle.presence

import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.evchargebook.bluetooth.VehicleBluetoothBindingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

enum class CompanionPresenceSupport {
    ANDROID_TOO_OLD,
    FEATURE_MISSING,
    SUPPORTED,
}

data class CompanionPresenceStatus(
    val support: CompanionPresenceSupport,
    val associated: Boolean,
)

object CompanionPresencePolicy {
    fun support(sdkInt: Int, hasCompanionSetupFeature: Boolean): CompanionPresenceSupport =
        when {
            sdkInt < Build.VERSION_CODES.S -> CompanionPresenceSupport.ANDROID_TOO_OLD
            !hasCompanionSetupFeature -> CompanionPresenceSupport.FEATURE_MISSING
            else -> CompanionPresenceSupport.SUPPORTED
        }

    fun classicCallbackState(appeared: Boolean): VehiclePresenceState =
        if (appeared) VehiclePresenceState.CONNECTED else VehiclePresenceState.DISCONNECTED
}

/**
 * Optional #299 A2 adapter for Android Companion Device presence.
 *
 * This PoC deliberately uses the address-based Companion Device APIs that exist across Android
 * 12-16. They are deprecated on newer Android releases, but retaining them for this experiment
 * avoids loading API 33/36-only callback types on Android 12 while we collect OEM evidence.
 *
 * The association is scoped to the already-selected classic-Bluetooth MAC. For classic Bluetooth
 * Android reports connection/disconnection presence, not vehicle telemetry. ACL remains registered
 * as the fallback path and both sources converge on VehiclePresenceDispatcher.
 */
class CompanionDevicePresenceController(
    private val context: Context,
) {
    private val manager: CompanionDeviceManager?
        get() = context.getSystemService(CompanionDeviceManager::class.java)

    fun status(deviceAddress: String?): CompanionPresenceStatus {
        val support = support()
        if (support != CompanionPresenceSupport.SUPPORTED || deviceAddress.isNullOrBlank()) {
            return CompanionPresenceStatus(support = support, associated = false)
        }
        return CompanionPresenceStatus(
            support = support,
            associated = isAssociated(deviceAddress),
        )
    }

    fun requestAssociation(
        deviceAddress: String,
        onPendingUserApproval: (IntentSender) -> Unit,
        onAssociated: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (support() != CompanionPresenceSupport.SUPPORTED) {
            onFailure("当前 Android 设备不支持 Companion Device presence")
            return
        }
        val companionManager = manager ?: run {
            onFailure("系统 CompanionDeviceManager 不可用")
            return
        }
        val normalizedAddress = normalize(deviceAddress)
        if (isAssociated(normalizedAddress)) {
            ensureObservation(normalizedAddress)
                .onSuccess { onAssociated() }
                .onFailure { onFailure(it.message ?: "无法启用系统连接观察") }
            return
        }

        val request = AssociationRequest.Builder()
            .addDeviceFilter(
                BluetoothDeviceFilter.Builder()
                    .setAddress(normalizedAddress)
                    .build()
            )
            .setSingleDevice(true)
            .build()

        val callback = object : CompanionDeviceManager.Callback() {
            override fun onFailure(error: CharSequence?) {
                onFailure(error?.toString().orEmpty().ifBlank { "系统关联未完成" })
            }

            @Suppress("DEPRECATION")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                onPendingUserApproval(chooserLauncher)
            }
        }

        @Suppress("DEPRECATION")
        companionManager.associate(request, callback, Handler(Looper.getMainLooper()))
    }

    fun ensureObservation(deviceAddress: String): Result<Unit> {
        if (support() != CompanionPresenceSupport.SUPPORTED) {
            return Result.failure(IllegalStateException("Companion Device presence unsupported"))
        }
        val companionManager = manager
            ?: return Result.failure(IllegalStateException("CompanionDeviceManager unavailable"))
        val normalizedAddress = normalize(deviceAddress)
        if (!isAssociated(normalizedAddress)) {
            return Result.failure(IllegalStateException("Device is not associated"))
        }

        return runCatching {
            // Kept intentionally for the A2 matrix: this API spans Android 12-16 and delivers
            // classic-Bluetooth connect/disconnect through the String callbacks below.
            @Suppress("DEPRECATION")
            companionManager.startObservingDevicePresence(normalizedAddress)
        }
    }

    fun removeAssociation(deviceAddress: String): Result<Unit> {
        if (support() != CompanionPresenceSupport.SUPPORTED) {
            return Result.failure(IllegalStateException("Companion Device presence unsupported"))
        }
        val companionManager = manager
            ?: return Result.failure(IllegalStateException("CompanionDeviceManager unavailable"))
        val normalizedAddress = normalize(deviceAddress)

        return runCatching {
            runCatching {
                @Suppress("DEPRECATION")
                companionManager.stopObservingDevicePresence(normalizedAddress)
            }
            @Suppress("DEPRECATION")
            companionManager.disassociate(normalizedAddress)
        }
    }

    private fun support(): CompanionPresenceSupport =
        CompanionPresencePolicy.support(
            sdkInt = Build.VERSION.SDK_INT,
            hasCompanionSetupFeature = context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_COMPANION_DEVICE_SETUP
            ),
        )

    private fun isAssociated(deviceAddress: String): Boolean {
        val companionManager = manager ?: return false
        val normalizedAddress = normalize(deviceAddress)
        @Suppress("DEPRECATION")
        return companionManager.associations.any { address ->
            address.equals(normalizedAddress, ignoreCase = true)
        }
    }

    private fun normalize(address: String): String =
        VehicleBluetoothBindingPreferences.normalizeAddress(address)
}

/**
 * System-bound presence callback. It never creates or ends Trips directly.
 *
 * Android 13+ keeps compatibility by forwarding non-self-managed AssociationInfo callbacks to
 * these legacy String callbacks. The PoC therefore keeps one callback surface across API 31-36.
 */
class CompanionVehiclePresenceService : CompanionDeviceService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        dispatch(address, CompanionPresencePolicy.classicCallbackState(appeared = true))
    }

    @Suppress("DEPRECATION")
    override fun onDeviceDisappeared(address: String) {
        dispatch(address, CompanionPresencePolicy.classicCallbackState(appeared = false))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun dispatch(address: String?, state: VehiclePresenceState) {
        if (address.isNullOrBlank()) return
        serviceScope.launch {
            VehiclePresenceDispatcher.forAutoTrip(applicationContext).dispatch(
                VehiclePresenceEvent(
                    state = state,
                    source = VehiclePresenceSource.COMPANION_DEVICE,
                    deviceAddress = address,
                )
            )
        }
    }
}
