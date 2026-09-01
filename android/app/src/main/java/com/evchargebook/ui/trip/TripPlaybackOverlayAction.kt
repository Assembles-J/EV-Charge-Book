package com.evchargebook.ui.trip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity

/** Makes the trusted speed-colored route/playback reachable from the v0.6 detail surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripPlaybackOverlayAction(
    points: List<TripPointEntity>,
    modifier: Modifier = Modifier,
) {
    var showPlayback by remember(points.firstOrNull()?.tripId) { mutableStateOf(false) }

    ExtendedFloatingActionButton(
        onClick = { showPlayback = true },
        modifier = modifier,
        icon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
        text = { Text("轨迹回放") },
    )

    if (showPlayback) {
        ModalBottomSheet(onDismissRequest = { showPlayback = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    "按可信 GPS 速度分段着色；灰色代表速度不可可信，真实长缺口保持断开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TripPlaybackRouteCardV06(points = points)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
