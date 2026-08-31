package com.evchargebook.autotrip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.evchargebook.MainActivity
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.repository.ChargingRepository
import com.evchargebook.trip.TripStartCoordinator
import com.evchargebook.trip.TripStartRequest
import com.evchargebook.trip.TripStartResult
import com.evchargebook.trip.TripStartSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Direct notification Activity entry point.
 *
 * Android 12+ blocks notification trampolines that route notification taps through a
 * BroadcastReceiver/Service before starting an Activity. The notification therefore targets this
 * Activity directly. A tap on the explicit "立即开始" action is treated as the Phase 1 user
 * confirmation. The persisted session is revalidated and the bound vehicle ID is passed to the
 * single TripStartCoordinator authority.
 */
class AutoTripConfirmationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getStringExtra(AutoTripNotificationController.EXTRA_SESSION_ID)
        if (sessionId == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            val database = AppDatabase.getInstance(applicationContext)
            val session = withContext(Dispatchers.IO) {
                database.autoTripDetectionDao().getById(sessionId)
            }
            val valid = session != null &&
                session.closedAtEpochMillis == null &&
                session.state == AutoTripDetectionState.BLUETOOTH_CANDIDATE.name

            if (!valid || session == null) {
                AutoTripNotificationController(applicationContext).cancel(sessionId)
                finish()
                return@launch
            }

            if (!hasLocationPermission()) {
                // Existing Trip UI already owns runtime location permission UX. Select the
                // session-bound vehicle first so the fallback confirmation cannot start another
                // currently selected vehicle by mistake.
                withContext(Dispatchers.IO) {
                    ChargingRepository(database, applicationContext).selectVehicle(session.vehicleId)
                }
                AutoTripNotificationController(applicationContext).cancel(sessionId)
                startActivity(
                    Intent(this@AutoTripConfirmationActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN_TRIP_CONFIRMATION, true)
                        .putExtra(AutoTripNotificationController.EXTRA_SESSION_ID, sessionId)
                )
                finish()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                TripStartCoordinator(database, applicationContext).start(
                    TripStartRequest(
                        vehicleId = session.vehicleId,
                        source = TripStartSource.BluetoothPrompt(session.id),
                    )
                )
            }
            AutoTripNotificationController(applicationContext).cancel(sessionId)

            when (result) {
                is TripStartResult.Started,
                is TripStartResult.AlreadyActive,
                is TripStartResult.Failed,
                -> startActivity(
                    Intent(this@AutoTripConfirmationActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN_ACTIVE_TRIP, true)
                )

                is TripStartResult.Blocked -> startActivity(
                    Intent(this@AutoTripConfirmationActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
            finish()
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
