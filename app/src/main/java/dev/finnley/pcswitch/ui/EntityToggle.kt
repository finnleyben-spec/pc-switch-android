package dev.finnley.pcswitch.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Physical-feel toggle with detent resistance at center.
 * Knob follows finger with increasing resistance toward the middle.
 * Crossing the 50% threshold triggers switch + vibration + snap to end.
 */
@Composable
fun EntityToggle(
    isOn: Boolean,
    enabled: Boolean,
    transitioning: Boolean = false,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val anim = remember { Animatable(if (isOn) 1f else 0f) }
    var animProgress by remember { mutableStateOf(anim.value) }
    var toggleWidth by remember { mutableStateOf(0.dp) }
    var hasSwitched by remember { mutableStateOf(false) }

    // Smooth breathing animation for transitioning state (modern, calm)
    val pulseAlpha = if (transitioning) {
        animateFloatAsState(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOut),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "toggle_breathing"
        ).value * 0.5f + 0.5f
    } else {
        0.5f
    }

    // Sync animation with state
    LaunchedEffect(isOn) {
        // Snap to current visual position so drag switches animate from the
        // crossed threshold, not from the opposite side.
        anim.snapTo(animProgress)
        anim.animateTo(
            targetValue = if (isOn) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
        ) {
            animProgress = value
        }
    }

    // During transitioning, hold the knob in the middle
    LaunchedEffect(transitioning) {
        if (transitioning) {
            anim.snapTo(0.5f)
            animProgress = 0.5f
        }
    }

    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                if (transitioning) {
                    Accent.copy(alpha = pulseAlpha)
                } else if (isOn) {
                    Accent
                } else {
                    SurfaceAlt
                }
            )
            .onGloballyPositioned { coords ->
                val w = coords.size.width.toFloat() / density.density
                if (w > 0f && toggleWidth != w.dp) {
                    toggleWidth = w.dp
                }
            }
            .pointerInput(enabled, toggleWidth, isOn, transitioning) {
                if (enabled && !transitioning) {
                    val travel = density.run {
                        (toggleWidth.value - 60f).dp.toPx()
                    }.coerceAtLeast(10f)

                    detectDragGestures(
                        onDragStart = {
                            hasSwitched = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (hasSwitched) return@detectDragGestures

                            // Detent resistance near center (45%-55%)
                            val detentFactor = if (animProgress in 0.45f..0.55f) {
                                0.25f  // 4x resistance at detent
                            } else if (animProgress in 0.35f..0.45f || animProgress in 0.55f..0.65f) {
                                0.5f   // 2x resistance approaching detent
                            } else {
                                1.0f   // Normal movement
                            }

                            val delta = (dragAmount.x / travel) * detentFactor
                            animProgress = (animProgress + delta).coerceIn(0f, 1f)

                            // Check if crossed threshold (50%)
                            val crossed = if (isOn) {
                                animProgress < 0.5f
                            } else {
                                animProgress > 0.5f
                            }

                            if (crossed && !hasSwitched) {
                                hasSwitched = true
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onToggle(!isOn)
                            }
                        },
                        onDragEnd = {
                            if (!hasSwitched) {
                                // Spring back to original position
                                scope.launch {
                                    anim.animateTo(
                                        targetValue = if (isOn) 1f else 0f,
                                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                                    ) {
                                        animProgress = value
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            if (!hasSwitched) {
                                scope.launch {
                                    anim.animateTo(
                                        targetValue = if (isOn) 1f else 0f,
                                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                                    ) {
                                        animProgress = value
                                    }
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
                .offset(x = (animProgress * (toggleWidth.value - 60f)).dp)
                .size(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
        )
    }
}
