package com.evchargebook.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripDiagnosticEventEntity

@Composable
fun TripDiagnosticSummaryCard(
    events: List<TripDiagnosticEventEntity>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "诊断摘要",
                style = MaterialTheme.typography.titleMedium
            )

            if (events.isEmpty()) {
                Text(
                    text = "本次行程暂无异常诊断事件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "记录事件 ${events.size} 次",
                    style = MaterialTheme.typography.bodyMedium
                )

                events.takeLast(3).reversed().forEach { event ->
                    Row {
                        Text(
                            text = event.type,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = event.detail ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
