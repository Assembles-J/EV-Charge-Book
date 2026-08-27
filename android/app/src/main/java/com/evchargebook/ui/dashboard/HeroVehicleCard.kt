package com.evchargebook.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.spacing

/**
 * EV Charge Book v0.5 hero vehicle card.
 *
 * The component establishes the new visual hierarchy:
 * vehicle first, metrics second.
 * Detailed visual assets and animations can be added without changing callers.
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
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Text(
                "我的爱车",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.65f)
            )
            Text(
                vehicle?.let { "${it.brand} ${it.model}" } ?: "选择车辆",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xl)) {
                Column {
                    Text("当前状态", color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.65f))
                    Text("准备出发", color = MaterialTheme.colorScheme.inversePrimary)
                }
                Column {
                    Text("EV Charge Book", color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.65f))
                    Text("Dark First", color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}
