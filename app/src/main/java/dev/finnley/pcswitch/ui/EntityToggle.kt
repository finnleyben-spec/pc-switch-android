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

    // B4: isOn is read through a state holder inside the gesture, so a state
    // flip mid-drag (optimistic press / WS push) does NOT restart the gesture
    // detector — a restart would cancel the in-progress drag.
    val isOnState = remember { mutableStateOf(isOn) }
    LaunchedEffect(isOn) { isOnState.value = isOn }
    var isDragging by remember { mutableStateOf(false) }

    // Sync animation with state — but while the user is dragging, the finger
    // owns the knob: the effect is cancelled on drag start, so its spring can
    // no longer overwrite the drag progress (root cause of the B4 ON->OFF bug).
    LaunchedEffect(isOn, isDragging) {
        if (isDragging) return@LaunchedEffect
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
            .pointerInput(enabled, toggleWidth, transitioning) {
                if (enabled && !transitioning) {
                    val travel = density.run {
                        (toggleWidth.value - 60f).dp.toPx()
                    }.coerceAtLeast(10f)

                    // B4: crossing is decided from the FINGER position relative to
                    // the drag start, never from the animated knob value. The knob
                    // can lag or be disturbed by state-sync animations; the press
                    // must still register in rapid ON->OFF sequences.
                    var dragBase = 0f
                    var dragStartX = 0f

                    detectDragGestures(
                        onDragStart = { pos ->
                            hasSwitched = false
                            isDragging = true
                            dragBase = if (isOnState.value) 1f else 0f
                            dragStartX = pos.x
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (hasSwitched) return@detectDragGestures
                            val fingerP = dragBase + (change.position.x - dragStartX) / travel

                            // Visual: knob follows the finger directly.
                            animProgress = fingerP.coerceIn(0f, 1f)

                            val crossedOn = dragBase < 0.5f && fingerP >= 0.5f
                            val crossedOff = dragBase > 0.5f && fingerP <= 0.5f
                            if ((crossedOn || crossedOff) && !hasSwitched) {
                                hasSwitched = true
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onToggle(crossedOn)
                            }
                        },
                        onDragEnd = {
                            // Release: LaunchedEffect(isOn, isDragging) re-syncs the
                            // knob to the (possibly updated) logical state.
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
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