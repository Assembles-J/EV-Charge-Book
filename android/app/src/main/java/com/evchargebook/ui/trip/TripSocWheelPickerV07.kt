package com.evchargebook.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.ui.theme.EVDesignTokens

/**
 * Trip v0.7 inline SOC editor foundation.
 *
 * The picker is intentionally embedded in the completion flow:
 * - no dialog
 * - no text keyboard
 * - start/end SOC can be adjusted together
 */
@Composable
internal fun TripSocWheelPickerV07(
    startSoc: Int,
    endSoc: Int,
    onStartSocChange: (Int) -> Unit,
    onEndSocChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SocWheelColumnV07(
                modifier = Modifier.weight(1f),
                label = "开始电量",
                value = startSoc,
                onChange = onStartSocChange
            )
            SocWheelColumnV07(
                modifier = Modifier.weight(1f),
                label = "结束电量",
                value = endSoc,
                onChange = onEndSocChange
            )
        }
    }
}

@Composable
private fun SocWheelColumnV07(
    modifier: Modifier,
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            text = (value - 1).coerceIn(0, 100).toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = "$value%",
            color = EVDesignTokens.Energy.green,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = (value + 1).coerceIn(0, 100).toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
