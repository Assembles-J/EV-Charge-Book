package com.evchargebook.ui.trip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.evchargebook.trip.TripBackgroundExecutionDiagnostics
import com.evchargebook.trip.TripBackgroundExecutionState
import com.evchargebook.ui.theme.spacing

@Composable
internal fun TripBackgroundRecordingGuidance() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    val acknowledgementKey = remember { acknowledgementKey() }
    var acknowledged by remember(acknowledgementKey) {
        mutableStateOf(preferences.getBoolean(acknowledgementKey, false))
    }
    var state by remember {
        mutableStateOf(TripBackgroundExecutionDiagnostics.read(context))
    }
    var permissionState by remember {
        mutableStateOf(readLocationPermissionState(context))
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        state = TripBackgroundExecutionDiagnostics.read(context)
        permissionState = readLocationPermissionState(context)
    }

    val showBackgroundGuidance = state.backgroundRestricted ||
        (!acknowledged && !state.ignoringBatteryOptimizations)
    val showPreciseLocationGuidance = permissionState.coarseGranted && !permissionState.fineGranted

    if (!showBackgroundGuidance && !showPreciseLocationGuidance) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        if (showBackgroundGuidance) {
            BackgroundExecutionGuidanceCard(
                state = state,
                onOpenSettings = {
                    settingsLauncher.launch(
                        TripBackgroundExecutionDiagnostics.appDetailsIntent(context)
                    )
                },
                onAcknowledged = {
                    preferences.edit().putBoolean(acknowledgementKey, true).apply()
                    acknowledged = true
                },
            )
        }

        if (showPreciseLocationGuidance) {
            PreciseLocationGuidanceCard(
                onOpenSettings = {
                    settingsLauncher.launch(
                        TripBackgroundExecutionDiagnostics.appDetailsIntent(context)
                    )
                }
            )
        }
    }
}

@Composable
private fun BackgroundExecutionGuidanceCard(
    state: TripBackgroundExecutionState,
    onOpenSettings: () -> Unit,
    onAcknowledged: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            Text(
                "后台记录保护",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                backgroundGuidanceText(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TripBackgroundExecutionDiagnostics.manufacturerGuidance()?.let { guidance ->
                Text(
                    guidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenSettings) {
                    Text("去设置")
                }
                TextButton(onClick = onAcknowledged) {
                    Text("已完成")
                }
            }
        }
    }
}

@Composable
private fun PreciseLocationGuidanceCard(
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            Text(
                "建议开启精确定位",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "当前只允许近似位置。行程仍可开始，但汽车轨迹可能出现明显偏移或缺失；建议在定位权限中开启“使用精确位置”。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenSettings) {
                    Text("定位设置")
                }
            }
        }
    }
}

private data class LocationPermissionState(
    val fineGranted: Boolean,
    val coarseGranted: Boolean,
)

private fun readLocationPermissionState(context: Context): LocationPermissionState =
    LocationPermissionState(
        fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED,
        coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED,
    )

private fun backgroundGuidanceText(state: TripBackgroundExecutionState): String = when {
    state.backgroundRestricted ->
        "系统已限制 EV Charge Book 的后台活动。锁屏或切换 App 后可能出现轨迹中断，建议解除后台限制后再开始长途记录。"
    !state.ignoringBatteryOptimizations ->
        "部分手机会在锁屏或切换 App 后限制持续定位。为减少轨迹缺口，建议允许 EV Charge Book 持续后台运行。"
    else ->
        "为减少锁屏或切换 App 后的轨迹缺口，建议允许 EV Charge Book 持续后台运行。"
}

private fun acknowledgementKey(): String =
    "$KEY_ACKNOWLEDGED_PREFIX:${Build.MANUFACTURER.lowercase()}:${Build.BRAND.lowercase()}:api${Build.VERSION.SDK_INT}"

private const val PREFERENCES_NAME = "trip_background_recording_guidance"
private const val KEY_ACKNOWLEDGED_PREFIX = "acknowledged"
