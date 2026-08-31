package com.evchargebook.autotrip

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.evchargebook.MainActivity
import com.evchargebook.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Direct notification Activity entry point.
 *
 * Android 12+ blocks notification trampolines that route notification taps through a
 * BroadcastReceiver/Service before starting an Activity. This Activity is therefore the direct
 * PendingIntent target. It validates persisted session state before forwarding to the existing
 * Trip confirmation flow.
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
            val allowed = withContext(Dispatchers.IO) {
                val database = AppDatabase.getInstance(applicationContext)
                val session = database.autoTripDetectionDao().getById(sessionId) ?: return@withContext false
                session.closedAtEpochMillis == null &&
                    session.state == AutoTripDetectionState.BLUETOOTH_CANDIDATE.name &&
                    database.tripDao().getActive() == null
            }

            if (allowed) {
                AutoTripNotificationController(applicationContext).cancel(sessionId)
                startActivity(
                    Intent(this@AutoTripConfirmationActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN_TRIP_CONFIRMATION, true)
                        .putExtra(AutoTripNotificationController.EXTRA_SESSION_ID, sessionId)
                )
            } else {
                AutoTripNotificationController(applicationContext).cancel(sessionId)
            }
            finish()
        }
    }
}
