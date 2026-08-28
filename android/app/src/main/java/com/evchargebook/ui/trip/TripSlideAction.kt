package com.evchargebook.ui.trip

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.evchargebook.ui.theme.EVDesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Restrained confirmation slider for Trip start/end actions.
 *
 * Dragging is the primary visual interaction. Accessibility services can invoke the semantic
 * click action directly so the gesture never becomes the only way to operate the control.
 */
@Composable
internal fun TripSlideAction(
    label: String,
    enabled: Boolean,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = EVDesignTokens.Energy.green
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val thumbSize = 50.dp
    val trackHeight = 58.dp
    val horizontalPadding = 4.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (enabled) "$label，向右滑动确认" else "$label，不可用"
                onClick(label = label) {
                    if (enabled) {
                        onConfirmed()
                        true
                    } else {
                        false
                    }
                }
            }
    ) {
        val maxOffsetPx = with(density) {
            (maxWidth - thumbSize - horizontalPadding * 2).coerceAtLeast(0.dp).toPx()
        }
        var targetOffsetPx by remember(maxOffsetPx) { mutableFloatStateOf(0f) }
        val displayedOffsetPx by animateFloatAsState(
            targetValue = targetOffsetPx,
            animationSpec = tween(durationMillis = if (targetOffsetPx == 0f) 180 else 70),
            label = "trip-slide-offset"
        )
        val threshold = maxOffsetPx * 0.78f
        val readyToConfirm = targetOffsetPx >= threshold && maxOffsetPx > 0f

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (enabled) accent.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                    )
            ) {
                if (enabled && maxOffsetPx > 0f) {
                    Box(
                        modifier = Modifier
                            .height(trackHeight)
                            .fillMaxWidth(
                                ((displayedOffsetPx + with(density) { thumbSize.toPx() }) /
                                    with(density) { maxWidth.toPx() }).coerceIn(0f, 1f)
                            )
                            .background(accent.copy(alpha = 0.10f))
                    )
                }

                Text(
                    text = if (readyToConfirm) "松开开始" else label,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding, vertical = 4.dp)
                        .size(thumbSize)
                        .offset { IntOffset(displayedOffsetPx.roundToInt(), 0) }
                        .pointerInput(enabled, maxOffsetPx) {
                            if (!enabled || maxOffsetPx <= 0f) return@pointerInput
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    targetOffsetPx = (targetOffsetPx + dragAmount).coerceIn(0f, maxOffsetPx)
                                },
                                onDragCancel = {
                                    targetOffsetPx = 0f
                                },
                                onDragEnd = {
                                    if (targetOffsetPx >= threshold) {
                                        targetOffsetPx = maxOffsetPx
                                        onConfirmed()
                                        scope.launch {
                                            delay(220)
                                            targetOffsetPx = 0f
                                        }
                                    } else {
                                        targetOffsetPx = 0f
                                    }
                                }
                            )
                        },
                    shape = CircleShape,
                    color = if (enabled) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (enabled) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
