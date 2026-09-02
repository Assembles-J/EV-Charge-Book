package com.evchargebook.trip

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class TripBackgroundExecutionState(
    val powerSave: Boolean,
    val deviceIdle: Boolean,
    val interactive: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val backgroundRestricted: Boolean,
    val appStandbyBucket: Int?,
    val locationPowerSaveMode: Int?,
    val processImportance: Int,
) {
    val needsUserAttention: Boolean
        get() = backgroundRestricted || !ignoringBatteryOptimizations

    fun diagnosticDetail(): String = buildString {
        append("powerSave=$powerSave")
        append(" deviceIdle=$deviceIdle")
        append(" interactive=$interactive")
        append(" ignoringBatteryOptimizations=$ignoringBatteryOptimizations")
        append(" backgroundRestricted=$backgroundRestricted")
        append(" appStandbyBucket=${appStandbyBucket ?: -1}")
        append(" locationPowerSaveMode=${locationPowerSaveMode ?: -1}")
        append(" processImportance=$processImportance")
    }
}

object TripBackgroundExecutionDiagnostics {
    fun read(context: Context): TripBackgroundExecutionState {
        val appContext = context.applicationContext
        val power = appContext.getSystemService(PowerManager::class.java)
        val activity = appContext.getSystemService(ActivityManager::class.java)
        val usage = appContext.getSystemService(UsageStatsManager::class.java)
        val processInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processInfo)

        return TripBackgroundExecutionState(
            powerSave = power.isPowerSaveMode,
            deviceIdle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) power.isDeviceIdleMode else false,
            interactive = power.isInteractive,
            ignoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                runCatching { power.isIgnoringBatteryOptimizations(appContext.packageName) }.getOrDefault(false)
            } else {
                true
            },
            backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.isBackgroundRestricted
            } else {
                false
            },
            appStandbyBucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { usage.appStandbyBucket }.getOrNull()
            } else {
                null
            },
            locationPowerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { power.locationPowerSaveMode }.getOrNull()
            } else {
                null
            },
            processImportance = processInfo.importance,
        )
    }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun manufacturerGuidance(): String? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val isColorOsFamily = listOf("oneplus", "oppo", "realme").any { marker ->
            manufacturer.contains(marker) || brand.contains(marker)
        }
        return if (isColorOsFamily) {
            "建议在电池/耗电管理中允许后台活动，并在最近任务中锁定 EV Charge Book。"
        } else {
            null
        }
    }
}
