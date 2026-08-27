package com.evchargebook.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.spacing

/**
 * EV Charge Book v0.5 hero vehicle card.
 *
 * Dark First dashboard entry point.
 * Vehicle identity is primary, metrics are secondary.
 */
@Composable
fun HeroVehicleCard(vehicle: VehicleEntity?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Text(
                "我的爱车",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.65f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        vehicle?.let { "${it.brand} ${it.model}" } ?: "选择车辆",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                    Text(
                        "● 可出发",
                        color = MaterialTheme.colorScheme.inversePrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    "EV",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.inversePrimary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("当前电量", color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f))
                    Text("82%", color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { 0.82f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.inversePrimary,
                    trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.15f)
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("预计续航", color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f))
                    Text("420 km", color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
