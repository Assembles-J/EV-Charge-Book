package com.evchargebook.ui.trip

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.evchargebook.data.entity.TripPointEntity

/** Makes the trusted speed-colored route/playback reachable from the v0.6 detail surface. */
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
        TripPlaybackFullScreenV07(
            points = points,
            onDismiss = { showPlayback = false },
        )
    }
}
