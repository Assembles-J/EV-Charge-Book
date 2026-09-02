package com.evchargebook.vehicle.presence

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
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
 * The request is deliberately scoped to the already-selected classic-Bluetooth MAC. For classic
 * Bluetooth Android reports connection/disconnection presence, not vehicle telemetry. ACL remains
 * registered as the fallback path and both sources converge on VehiclePresenceDispatcher.
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

            override fun onAssociationPending(intentSender: IntentSender) {
                onPendingUserApproval(intentSender)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                val createdAddress = associationInfo.deviceMacAddress?.toString()
                    ?: normalizedAddress
                ensureObservation(createdAddress)
                    .onSuccess { onAssociated() }
                    .onFailure { onFailure(it.message ?: "系统关联已创建，但连接观察启用失败") }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            companionManager.associate(request, ContextCompat.getMainExecutor(context), callback)
        } else {
            @Suppress("DEPRECATION")
            companionManager.associate(request, callback, Handler(Looper.getMainLooper()))
        }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                val association = findAssociation(normalizedAddress)
                    ?: error("Associated device record unavailable")
                val request = ObservingDevicePresenceRequest.Builder()
                    .setAssociationId(association.id)
                    .build()
                companionManager.startObservingDevicePresence(request)
            } else {
                @Suppress("DEPRECATION")
                companionManager.startObservingDevicePresence(normalizedAddress)
            }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val association = findAssociation(normalizedAddress) ?: return@runCatching
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                        val request = ObservingDevicePresenceRequest.Builder()
                            .setAssociationId(association.id)
                            .build()
                        companionManager.stopObservingDevicePresence(request)
                    } else {
                        @Suppress("DEPRECATION")
                        companionManager.stopObservingDevicePresence(normalizedAddress)
                    }
                }
                companionManager.disassociate(association.id)
            } else {
                runCatching {
                    @Suppress("DEPRECATION")
                    companionManager.stopObservingDevicePresence(normalizedAddress)
                }
                @Suppress("DEPRECATION")
                companionManager.disassociate(normalizedAddress)
            }
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
        val normalizedAddress = normalize(deviceAddress)
        val companionManager = manager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            companionManager.myAssociations.any { association ->
                association.deviceMacAddress?.toString()
                    ?.equals(normalizedAddress, ignoreCase = true) == true
            }
        } else {
            @Suppress("DEPRECATION")
            companionManager.associations.any { address ->
                address.equals(normalizedAddress, ignoreCase = true)
            }
        }
    }

    private fun findAssociation(deviceAddress: String): AssociationInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val normalizedAddress = normalize(deviceAddress)
        return manager?.myAssociations?.firstOrNull { association ->
            association.deviceMacAddress?.toString()
                ?.equals(normalizedAddress, ignoreCase = true) == true
        }
    }

    private fun normalize(address: String): String =
        VehicleBluetoothBindingPreferences.normalizeAddress(address)
}

/**
 * System-bound presence callback. It never creates or ends Trips directly.
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

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        dispatch(
            associationInfo.deviceMacAddress?.toString(),
            CompanionPresencePolicy.classicCallbackState(appeared = true),
        )
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        dispatch(
            associationInfo.deviceMacAddress?.toString(),
            CompanionPresencePolicy.classicCallbackState(appeared = false),
        )
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        val state = when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED -> VehiclePresenceState.CONNECTED
            DevicePresenceEvent.EVENT_BT_DISCONNECTED -> VehiclePresenceState.DISCONNECTED
            DevicePresenceEvent.EVENT_BLE_APPEARED -> VehiclePresenceState.PRESENT
            else -> return
        }
        val associationId = event.associationId
        if (associationId == DevicePresenceEvent.NO_ASSOCIATION) return
        val address = getSystemService(CompanionDeviceManager::class.java)
            ?.myAssociations
            ?.firstOrNull { it.id == associationId }
            ?.deviceMacAddress
            ?.toString()
        dispatch(address, state)
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
