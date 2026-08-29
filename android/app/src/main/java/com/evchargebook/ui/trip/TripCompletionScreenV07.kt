package com.evchargebook.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * Trip v0.7 completion screen foundation.
 *
 * UI structure follows the approved design:
 * endpoint summary -> trip summary -> inline SOC adjustment -> route preview -> save.
 * Runtime wiring is intentionally kept separate from this layout slice.
 */
@Composable
internal fun TripCompletionScreenV07(
    startSoc: Int,
    endSoc: Int,
    onSaveAndFinish: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        TripCompletionEndpointSummaryV07()
        TripCompletionSummaryV07()

        Surface {
            Column {
                Text("SOC 调整", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth()) {
                    Text("开始电量 $startSoc%", modifier = Modifier.weight(1f))
                    Text("结束电量 $endSoc%", modifier = Modifier.weight(1f))
                }
            }
        }

        TripCompletionRoutePreviewV07()

        Button(
            onClick = onSaveAndFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存并结束")
        }
    }
}

@Composable
private fun TripCompletionSummaryV07() {
    Text("距离 · 时长 · 平均能耗")
}

@Composable
private fun TripCompletionEndpointSummaryV07() {
    Text("🟢 起点  →  🔴 终点")
}

@Composable
private fun TripCompletionRoutePreviewV07() {
    Text("行程轨迹预览")
}
