package com.evchargebook.autotrip

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.evchargebook.bluetooth.BluetoothPromptPreferences
import com.evchargebook.bluetooth.VehicleBluetoothBinding
import com.evchargebook.bluetooth.VehicleBluetoothBindingPreferences
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.AutoTripDetectionSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID

sealed interface AutoTripCandidateResult {
    data object NotConfigured : AutoTripCandidateResult
    data object ActiveTripExists : AutoTripCandidateResult
    data class Existing(val sessionId: String, val state: AutoTripDetectionState) : AutoTripCandidateResult
    data class Created(val sessionId: String, val notificationVisible: Boolean) : AutoTripCandidateResult
}

class AutoTripPromptCoordinator(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val sessionDao = database.autoTripDetectionDao()
    private val tripDao = database.tripDao()
    private val vehicleDao = database.vehicleDao()
    private val bindingPreferences = VehicleBluetoothBindingPreferences(context)
    private val legacyPreferences = BluetoothPromptPreferences(context)
    private val notifications = AutoTripNotificationController(context)

    suspend fun onBluetoothConnected(
        deviceAddress: String,
        deviceName: String?,
        now: Long = System.currentTimeMillis(),
    ): AutoTripCandidateResult = connectionMutex.withLock {
        synchronizeSingleVehicleLegacyBinding()

        val normalizedAddress = VehicleBluetoothBindingPreferences.normalizeAddress(deviceAddress)
        val binding = bindingPreferences.bindings.first().firstOrNull {
            it.enabled && it.deviceAddress.equals(normalizedAddress, ignoreCase = true)
        } ?: return@withLock AutoTripCandidateResult.NotConfigured

        if (tripDao.getActive() != null) {
            return@withLock AutoTripCandidateResult.ActiveTripExists
        }

        val deviceHash = hashDeviceAddress(normalizedAddress)
        val existing = sessionDao.getOpenForDevice(deviceHash)
        if (existing != null) {
            return@withLock AutoTripCandidateResult.Existing(
                sessionId = existing.id,
                state = existing.state.toDetectionState(),
            )
        }

        val session = AutoTripDetectionSessionEntity(
            id = UUID.randomUUID().toString(),
            vehicleId = binding.vehicleId,
            deviceAddressHash = deviceHash,
            deviceNameSnapshot = binding.deviceName ?: deviceName,
            connectionEpoch = UUID.randomUUID().toString(),
            state = AutoTripDetectionState.BLUETOOTH_CANDIDATE.name,
            connectedAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        sessionDao.insert(session)

        val vehicle = vehicleDao.observeActive().first().firstOrNull { it.id == binding.vehicleId }
        val vehicleLabel = vehicle?.let { "${it.brand} ${it.model}" } ?: binding.deviceName ?: "车辆"
        val visible = notifications.showCandidate(session, vehicleLabel)
        if (!visible) {
            sessionDao.update(
                session.copy(
                    state = AutoTripDetectionState.BLOCKED.name,
                    updatedAtEpochMillis = now,
                )
            )
        }

        AutoTripCandidateResult.Created(session.id, visible)
    }

    suspend fun onBluetoothDisconnected(
        deviceAddress: String,
        now: Long = System.currentTimeMillis(),
    ) = connectionMutex.withLock {
        val deviceHash = hashDeviceAddress(deviceAddress)
        val session = sessionDao.getOpenForDevice(deviceHash) ?: return@withLock
        sessionDao.closeSession(
            sessionId = session.id,
            state = AutoTripDetectionState.EXPIRED.name,
            closedAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        notifications.cancel(session.id)
    }

    private suspend fun synchronizeSingleVehicleLegacyBinding() {
        val activeVehicles = vehicleDao.observeActive().first()
        if (activeVehicles.size != 1) return
        val legacy = legacyPreferences.settings.first()
        val address = legacy.deviceAddress?.takeIf { it.isNotBlank() } ?: return
        bindingPreferences.save(
            VehicleBluetoothBinding(
                vehicleId = activeVehicles.single().id,
                enabled = legacy.enabled,
                deviceAddress = address,
                deviceName = legacy.deviceName,
            )
        )
    }

    companion object {
        private val connectionMutex = Mutex()

        fun hashDeviceAddress(address: String): String {
            val normalized = VehicleBluetoothBindingPreferences.normalizeAddress(address)
            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}

class AutoTripNotificationController(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun showCandidate(session: AutoTripDetectionSessionEntity, vehicleLabel: String): Boolean {
        createChannel()
        if (!canPostNotifications()) return false

        val openIntent = PendingIntent.getActivity(
            context,
            requestCode(session.id, ACTION_OPEN_CONFIRMATION),
            Intent(context, AutoTripConfirmationActivity::class.java)
                .setAction(ACTION_OPEN_CONFIRMATION)
                .putExtra(EXTRA_SESSION_ID, session.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val ignoreIntent = PendingIntent.getBroadcast(
            context,
            requestCode(session.id, ACTION_IGNORE_SESSION),
            Intent(context, AutoTripActionReceiver::class.java)
                .setAction(ACTION_IGNORE_SESSION)
                .putExtra(EXTRA_SESSION_ID, session.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        manager.notify(
            notificationId(session.id),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("已连接 $vehicleLabel")
                .setContentText("是否开始本次行程？")
                .setContentIntent(openIntent)
                .addAction(android.R.drawable.ic_media_play, "立即开始", openIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "本次忽略", ignoreIntent)
                .setAutoCancel(true)
                .build(),
        )
        return true
    }

    fun cancel(sessionId: String) {
        manager.cancel(notificationId(sessionId))
    }

    private fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "车辆检测",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "vehicle_detection"
        const val ACTION_OPEN_CONFIRMATION = "com.evchargebook.autotrip.OPEN_CONFIRMATION"
        const val ACTION_IGNORE_SESSION = "com.evchargebook.autotrip.IGNORE_SESSION"
        const val EXTRA_SESSION_ID = "auto_trip_session_id"

        fun notificationId(sessionId: String): Int = 31_000 + (sessionId.hashCode() and 0x0FFFFFFF) % 100_000

        private fun requestCode(sessionId: String, action: String): Int =
            41_000 + ((sessionId + action).hashCode() and 0x0FFFFFFF) % 100_000
    }
}

class AutoTripActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutoTripNotificationController.ACTION_IGNORE_SESSION) return
        val sessionId = intent.getStringExtra(AutoTripNotificationController.EXTRA_SESSION_ID) ?: return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).autoTripDetectionDao()
                val session = dao.getById(sessionId) ?: return@launch
                if (
                    session.closedAtEpochMillis != null ||
                    session.state != AutoTripDetectionState.BLUETOOTH_CANDIDATE.name
                ) return@launch

                val now = System.currentTimeMillis()
                dao.markIgnored(
                    sessionId = session.id,
                    state = AutoTripDetectionState.IGNORED.name,
                    ignoredAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
                AutoTripNotificationController(context).cancel(session.id)
            } finally {
                pending.finish()
            }
        }
    }
}

private fun String.toDetectionState(): AutoTripDetectionState =
    runCatching { AutoTripDetectionState.valueOf(this) }.getOrDefault(AutoTripDetectionState.BLOCKED)
