package com.evchargebook.ui.trip

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
import androidx.compose.ui.unit.dp
import com.evchargebook.trip.TripBackgroundExecutionDiagnostics
import com.evchargebook.trip.TripBackgroundExecutionState
import com.evchargebook.ui.theme.spacing

@Composable
internal fun TripBackgroundRecordingGuidance() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(PREFERENCES_NAME, 0)
    }
    var acknowledged by remember {
        mutableStateOf(preferences.getBoolean(KEY_ACKNOWLEDGED, false))
    }
    var state by remember {
        mutableStateOf(TripBackgroundExecutionDiagnostics.read(context))
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        state = TripBackgroundExecutionDiagnostics.read(context)
    }

    if (acknowledged || !state.needsUserAttention) return

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
                TextButton(
                    onClick = {
                        settingsLauncher.launch(
                            TripBackgroundExecutionDiagnostics.appDetailsIntent(context)
                        )
                    },
                ) {
                    Text("去设置")
                }
                TextButton(
                    onClick = {
                        preferences.edit().putBoolean(KEY_ACKNOWLEDGED, true).apply()
                        acknowledged = true
                    },
                ) {
                    Text("已完成")
                }
            }
        }
    }
}

private fun backgroundGuidanceText(state: TripBackgroundExecutionState): String = when {
    state.backgroundRestricted ->
        "系统已限制 EV Charge Book 的后台活动。锁屏或切换 App 后可能出现轨迹中断，建议解除后台限制后再开始长途记录。"
    !state.ignoringBatteryOptimizations ->
        "部分手机会在锁屏或切换 App 后限制持续定位。为减少轨迹缺口，建议允许 EV Charge Book 持续后台运行。"
    else ->
        "为减少锁屏或切换 App 后的轨迹缺口，建议允许 EV Charge Book 持续后台运行。"
}

private const val PREFERENCES_NAME = "trip_background_recording_guidance"
private const val KEY_ACKNOWLEDGED = "acknowledged"
