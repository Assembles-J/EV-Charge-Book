package com.evchargebook.autotrip

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.evchargebook.AppVisibilityTracker
import com.evchargebook.MainActivity
import com.evchargebook.bluetooth.BluetoothPromptPreferences
import com.evchargebook.bluetooth.VehicleBluetoothBinding
import com.evchargebook.bluetooth.VehicleBluetoothBindingPreferences
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.AutoTripDetectionSessionEntity
import com.evchargebook.trip.TripStartCoordinator
import com.evchargebook.trip.TripStartRequest
import com.evchargebook.trip.TripStartResult
import com.evchargebook.trip.TripStartSource
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
    data class AutoStarted(val sessionId: String, val tripId: Long) : AutoTripCandidateResult
    data class AutoStartFailed(
        val sessionId: String,
        val tripId: Long,
        val notificationVisible: Boolean,
    ) : AutoTripCandidateResult
}

class AutoTripPromptCoordinator(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val sessionDao = database.autoTripDetectionDao()
    private val tripDao = database.tripDao()
    private val vehicleDao = database.vehicleDao()
    private val bindingPreferences = VehicleBluetoothBindingPreferences(context)
    private val legacyPreferences = BluetoothPromptPreferences(context)
    private val notifications = AutoTripNotificationController(context)
    private val tripStartCoordinator = TripStartCoordinator(database, context)

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
        val appInForeground = AppVisibilityTracker.hasVisibleActivity
        val execution = BluetoothAutoStartExecutionPolicy.decide(
            autoStartEnabled = binding.autoStartOnConnect,
            sdkInt = Build.VERSION.SDK_INT,
            appInForeground = appInForeground,
        )

        Log.i(
            TAG,
            "session=${session.id} vehicleId=${binding.vehicleId} autoStart=${binding.autoStartOnConnect} " +
                "sdk=${Build.VERSION.SDK_INT} activityVisible=$appInForeground execution=$execution",
        )

        return@withLock when (execution) {
            BluetoothAutoStartExecution.CONFIRMATION_PROMPT ->
                createVisibleCandidate(
                    session = session,
                    vehicleLabel = vehicleLabel,
                    now = now,
                    autoStartUserActionRequired = false,
                )

            BluetoothAutoStartExecution.USER_ACTION_REQUIRED -> {
                Log.i(TAG, "session=${session.id} fallback=user_action_notification reason=background_fgs_restriction")
                createVisibleCandidate(
                    session = session,
                    vehicleLabel = vehicleLabel,
                    now = now,
                    autoStartUserActionRequired = true,
                )
            }

            BluetoothAutoStartExecution.DIRECT_START ->
                startDirectBluetoothAutoTrip(
                    session = session,
                    binding = binding,
                    vehicleLabel = vehicleLabel,
                    now = now,
                )
        }
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
        Log.i(TAG, "session=${session.id} bluetooth=disconnected action=expire_candidate_only")
    }

    private suspend fun startDirectBluetoothAutoTrip(
        session: AutoTripDetectionSessionEntity,
        binding: VehicleBluetoothBinding,
        vehicleLabel: String,
        now: Long,
    ): AutoTripCandidateResult {
        // Android 13+ notification denial must never turn this into silent automation.
        if (!notifications.canPostNotifications()) {
            markBlocked(session.id, now)
            Log.w(TAG, "session=${session.id} autoStart=blocked reason=notification_permission_missing")
            return AutoTripCandidateResult.Existing(
                sessionId = session.id,
                state = AutoTripDetectionState.BLOCKED,
            )
        }

        // A visible Activity can request runtime location permission. Keep the persisted Bluetooth
        // session pending and route through the one-tap confirmation Activity instead of creating a
        // Trip that would immediately fail to acquire location.
        if (!hasLocationPermission()) {
            Log.i(TAG, "session=${session.id} fallback=user_action_notification reason=location_permission_missing")
            return createVisibleCandidate(
                session = session,
                vehicleLabel = vehicleLabel,
                now = now,
                autoStartUserActionRequired = true,
            )
        }

        return when (
            val start = tripStartCoordinator.start(
                TripStartRequest(
                    vehicleId = binding.vehicleId,
                    source = TripStartSource.BluetoothAuto(session.id),
                    requestedAtEpochMillis = now,
                )
            )
        ) {
            is TripStartResult.Started -> {
                notifications.showAutoStarted(session.id, vehicleLabel)
                Log.i(TAG, "session=${session.id} autoStart=started tripId=${start.tripId}")
                AutoTripCandidateResult.AutoStarted(session.id, start.tripId)
            }

            is TripStartResult.AlreadyActive -> {
                Log.i(TAG, "session=${session.id} autoStart=skipped reason=active_trip tripId=${start.tripId}")
                AutoTripCandidateResult.ActiveTripExists
            }

            is TripStartResult.Blocked -> {
                markBlocked(session.id, now)
                Log.w(TAG, "session=${session.id} autoStart=blocked reason=${start.reason}")
                AutoTripCandidateResult.Existing(session.id, AutoTripDetectionState.BLOCKED)
            }

            is TripStartResult.Failed -> {
                val visible = notifications.showAutoStartFailed(
                    sessionId = session.id,
                    vehicleLabel = vehicleLabel,
                )
                Log.e(TAG, "session=${session.id} autoStart=failed tripId=${start.tripId} reason=${start.reason}")
                AutoTripCandidateResult.AutoStartFailed(
                    sessionId = session.id,
                    tripId = start.tripId,
                    notificationVisible = visible,
                )
            }
        }
    }

    private suspend fun createVisibleCandidate(
        session: AutoTripDetectionSessionEntity,
        vehicleLabel: String,
        now: Long,
        autoStartUserActionRequired: Boolean,
    ): AutoTripCandidateResult.Created {
        val visible = notifications.showCandidate(
            session = session,
            vehicleLabel = vehicleLabel,
            autoStartUserActionRequired = autoStartUserActionRequired,
        )
        if (!visible) {
            markBlocked(session.id, now)
            Log.w(TAG, "session=${session.id} candidate=blocked reason=notification_permission_missing")
        } else {
            Log.i(
                TAG,
                "session=${session.id} candidate=visible autoStartUserActionRequired=$autoStartUserActionRequired",
            )
        }
        return AutoTripCandidateResult.Created(session.id, visible)
    }

    private suspend fun markBlocked(
        sessionId: String,
        now: Long = System.currentTimeMillis(),
    ) {
        sessionDao.getById(sessionId)?.let { current ->
            if (
                current.closedAtEpochMillis == null &&
                current.state == AutoTripDetectionState.BLUETOOTH_CANDIDATE.name
            ) {
                sessionDao.update(
                    current.copy(
                        state = AutoTripDetectionState.BLOCKED.name,
                        updatedAtEpochMillis = now,
                    )
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

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
                autoStartOnConnect = legacy.autoStartOnConnect,
            )
        )
    }

    companion object {
        private const val TAG = "AutoTripBluetooth"
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

    fun showCandidate(
        session: AutoTripDetectionSessionEntity,
        vehicleLabel: String,
        autoStartUserActionRequired: Boolean = false,
    ): Boolean {
        createChannel()
        if (!canPostNotifications()) return false

        val copy = AutoTripNotificationCopy.candidate(
            vehicleLabel = vehicleLabel,
            autoStartUserActionRequired = autoStartUserActionRequired,
        )
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
                .setContentTitle(copy.title)
                .setContentText(copy.text)
                .setContentIntent(openIntent)
                .addAction(android.R.drawable.ic_media_play, copy.actionLabel ?: "开始行程", openIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "本次忽略", ignoreIntent)
                .setAutoCancel(true)
                .build(),
        )
        return true
    }

    fun showAutoStarted(sessionId: String, vehicleLabel: String): Boolean {
        createChannel()
        if (!canPostNotifications()) return false

        val copy = AutoTripNotificationCopy.autoStarted(vehicleLabel)
        val openIntent = openActiveTripPendingIntent(sessionId)
        manager.notify(
            notificationId(sessionId),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(copy.title)
                .setContentText(copy.text)
                .setContentIntent(openIntent)
                .addAction(android.R.drawable.ic_menu_view, "查看行程", openIntent)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setTimeoutAfter(AUTO_STARTED_TIMEOUT_MS)
                .build(),
        )
        return true
    }

    fun showAutoStartFailed(sessionId: String, vehicleLabel: String): Boolean {
        createChannel()
        if (!canPostNotifications()) return false

        val openIntent = openActiveTripPendingIntent(sessionId)
        manager.notify(
            notificationId(sessionId),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("$vehicleLabel 自动开始未完成")
                .setContentText("点击打开行程并恢复记录。")
                .setContentIntent(openIntent)
                .addAction(android.R.drawable.ic_media_play, "打开行程", openIntent)
                .setAutoCancel(true)
                .build(),
        )
        return true
    }

    fun cancel(sessionId: String) {
        manager.cancel(notificationId(sessionId))
    }

    private fun openActiveTripPendingIntent(sessionId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(sessionId, ACTION_OPEN_ACTIVE_TRIP),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_ACTIVE_TRIP, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "车辆检测",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "vehicle_detection"
        const val ACTION_OPEN_CONFIRMATION = "com.evchargebook.autotrip.OPEN_CONFIRMATION"
        const val ACTION_IGNORE_SESSION = "com.evchargebook.autotrip.IGNORE_SESSION"
        private const val ACTION_OPEN_ACTIVE_TRIP = "com.evchargebook.autotrip.OPEN_ACTIVE_TRIP"
        private const val AUTO_STARTED_TIMEOUT_MS = 8_000L
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
