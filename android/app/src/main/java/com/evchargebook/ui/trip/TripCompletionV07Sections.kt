package com.evchargebook.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.evchargebook.ui.theme.EVDesignTokens

/**
 * Trip v0.7 completion flow UI foundations.
 *
 * This keeps the new completion concepts isolated before wiring them into the existing
 * completion state machine:
 * - endpoint summary
 * - inline SOC adjustment area
 * - compact route preview placeholder
 *
 * No persistence or tracking behaviour belongs here.
 */
@Composable
internal fun TripCompletionEndpointSummaryV07(
    startText: String,
    endText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large
    ) {
        Column {
            Text("起点", color = EVDesignTokens.Energy.green)
            Text(startText)
            Text("终点", color = MaterialTheme.colorScheme.error)
            Text(endText)
        }
    }
}

@Composable
internal fun TripCompletionSocAdjustmentHeaderV07() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("开始电量")
        Text("结束电量")
    }
}

@Composable
internal fun TripCompletionRoutePreviewPlaceholderV07() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large
    ) {
        Text("行程轨迹预览")
    }
}
