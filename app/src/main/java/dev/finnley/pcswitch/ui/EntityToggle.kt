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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Gothic cyber toggle with detent resistance and neon glow.
 * Blue glow for transitioning state, red glow for active.
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

    // Breathing animation for transitioning state
    val pulseAlpha = if (transitioning) {
        animateFloatAsState(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOut),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "toggle_breathing"
        ).value
    } else {
        0f
    }

    // Glow intensity for active state
    val activeGlow = if (isOn && !transitioning) {
        animateFloatAsState(
            targetValue = 0.6f,
            animationSpec = tween(300),
            label = "active_glow"
        ).value
    } else {
        0f
    }

    // Sync animation with state
    LaunchedEffect(isOn) {
        anim.snapTo(animProgress)
        anim.animateTo(
            targetValue = if (isOn) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f)
        ) {
            animProgress = value
        }
    }

    // During transitioning, hold the knob in the middle with blue glow
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
                    Shutdown.copy(alpha = 0.3f + pulseAlpha * 0.3f)
                } else if (isOn) {
                    Accent.copy(alpha = 0.8f + activeGlow * 0.2f)
                } else {
                    SurfaceAlt
                }
            )
            .drawBehind {
                // Neon glow effect around the toggle
                val glowColor = if (transitioning) {
                    Shutdown.copy(alpha = 0.4f + pulseAlpha * 0.3f)
                } else if (isOn) {
                    Glow.copy(alpha = activeGlow)
                } else {
                    Color.Transparent
                }

                if (glowColor.alpha > 0f) {
                    val brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor,
                            glowColor.copy(alpha = 0f)
                        ),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.width / 2f * 1.5f
                    )
                    drawRect(brush)
                }
            }
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

                            val detentFactor = if (animProgress in 0.45f..0.55f) {
                                0.25f
                            } else if (animProgress in 0.35f..0.45f || animProgress in 0.55f..0.65f) {
                                0.5f
                            } else {
                                1.0f
                            }

                            val delta = (dragAmount.x / travel) * detentFactor
                            animProgress = (animProgress + delta).coerceIn(0f, 1f)

                            val crossed = if (isOn) {
                                animProgress < 0.5f
                            } else {
                                animProgress > 0.5f
                            }

                            if (crossed && !hasSwitched) {
                                hasSwitched = true
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onToggle(!isOn)
                            }
                        },
                        onDragEnd = {
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
        // Slider knob with glow
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 4.dp)
                .offset(x = (animProgress * (toggleWidth.value - 60f)).dp)
                .size(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .drawBehind {
                    if (transitioning) {
                        // Blue breathing glow in center during transition
                        val brush = Brush.radialGradient(
                            colors = listOf(
                                Shutdown.copy(alpha = 0.8f),
                                Shutdown.copy(alpha = 0f)
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width / 2f * 1.2f
                        )
                        drawCircle(brush)
                    }
                }
                .background(
                    if (transitioning) {
                        Shutdown.copy(alpha = 0.6f + pulseAlpha * 0.4f)
                    } else {
                        Color.White
                    }
                )
        )
    }
}