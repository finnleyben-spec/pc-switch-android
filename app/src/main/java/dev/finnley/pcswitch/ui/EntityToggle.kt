package dev.finnley.pcswitch.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * iOS-style toggle slider.
 * Shows on/off state via slider position only (no text).
 * Supports swipe/drag gesture to toggle.
 */
@Composable
fun EntityToggle(
    isOn: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val anim = remember { Animatable(if (isOn) 1f else 0f) }
    var animProgress by remember { mutableStateOf(anim.value) }

    // Travel distance: toggle is 80dp, knob is 40dp, padding is 4dp each side
    // So knob travels 32dp
    val travelPx = density.run { 32.dp.toPx() }

    // Sync animation with state
    LaunchedEffect(isOn) {
        animProgress = anim.value
        anim.animateTo(
            targetValue = if (isOn) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
        ) {
            animProgress = value
        }
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isOn) Accent else SurfaceAlt)
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // Start tracking
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            // Track drag progress
                            val current = anim.value
                            val delta = dragAmount / travelPx
                            val newProgress = (current + delta).coerceIn(0f, 1f)
                            animProgress = newProgress
                        },
                        onDragEnd = {
                            // Decide toggle based on final position
                            val shouldToggle = if (isOn) {
                                animProgress < 0.5f
                            } else {
                                animProgress > 0.5f
                            }
                            if (shouldToggle) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onToggle(!isOn)
                            }
                            // Snap back to correct position
                            scope.launch {
                                anim.animateTo(
                                    targetValue = if (isOn) 1f else 0f,
                                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                                ) {
                                    animProgress = value
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                anim.animateTo(
                                    targetValue = if (isOn) 1f else 0f,
                                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                                ) {
                                    animProgress = value
                                }
                            }
                        }
                    )
                }
            }
    ) {
        // Slider knob
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 4.dp)
                .offset(x = (animProgress * 32).dp)
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
        )
    }
}