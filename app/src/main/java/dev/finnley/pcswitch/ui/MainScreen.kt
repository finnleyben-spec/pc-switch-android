package dev.finnley.pcswitch.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.finnley.pcswitch.state.Mode
import dev.finnley.pcswitch.state.SwitchConfig
import dev.finnley.pcswitch.state.UiState

@Composable
fun MainScreen(
    state: UiState,
    onConnectToHome: () -> Unit,
    onDisconnect: () -> Unit,
    onPressCurrentOn: () -> Unit,
    onPressCurrentOff: () -> Unit,
    onSettings: () -> Unit,
    onSwitches: () -> Unit,
    onUpdate: () -> Unit,
    onNextSwitch: () -> Unit,
    onPrevSwitch: () -> Unit
) {
    val switches = state.switches
    val currentIndex = state.currentSwitchIndex
    val currentSwitch = if (switches.isNotEmpty()) switches[currentIndex] else null
    val currentSwitchState = currentSwitch?.let { state.switchStates[it.id] } ?: "unknown"

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(24.dp)
        ) {
            // ---------- Header ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "PC Switch",
                    fontFamily = FontFamily.Serif,
                    fontSize = 36.sp,
                    color = Ink,
                    letterSpacing = (-0.6).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            state.updateAvailable?.let { up ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Accent.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(Warn)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.downloadProgress != null) {
                                "Update ${state.downloadProgress}%"
                            } else {
                                "Update · ${up.versionName}"
                            },
                            fontSize = 12.sp,
                            color = Ink
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))

        // ---------- Status Card ----------
        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                val dot = when (state.mode) {
                    Mode.HomeLan, Mode.HomeVpn -> Ok
                    Mode.Remote, Mode.Connecting -> Accent
                    Mode.Offline -> Err
                }
                val label = when (state.mode) {
                    Mode.HomeLan -> "ZU HAUSE · LAN"
                    Mode.HomeVpn -> "VPN"
                    Mode.Remote -> "Remote · Tailscale-Relay"
                    Mode.Connecting -> "Verbinde Heimnetz…"
                    Mode.Offline -> "Offline"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(dot)
                    Spacer(Modifier.width(10.dp))
                    Text(label, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Medium)
                }
                if (currentSwitch != null) {
                    val isPcSwitch = currentSwitch.name.lowercase().contains("pc")
                    val displayState = if (isPcSwitch && state.pcStatus != "unknown") state.pcStatus else currentSwitchState
                    if (displayState != "unknown") {
                        Spacer(Modifier.height(6.dp))
                        val label = if (displayState == "reachable") "An"
                            else if (displayState == "unreachable") "Aus"
                            else switchStateLabel(displayState)
                        Text(
                            "${currentSwitch.name}: $label",
                            fontSize = 13.sp,
                            color = InkSoft
                        )
                    }
                }
                if (state.statusNote.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(state.statusNote, fontSize = 13.sp, color = InkSoft)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // ---------- VPN CTA ----------
        when (state.mode) {
            Mode.Offline, Mode.Remote -> VpnButton(
                text = "Connect to home",
                accent = true,
                busy = state.vpnBusy,
                onClick = onConnectToHome
            )
            Mode.HomeLan, Mode.HomeVpn -> VpnButton(
                text = "Heimnetz trennen",
                accent = false,
                busy = state.vpnBusy,
                onClick = onDisconnect
            )
            else -> Unit
        }
        Spacer(Modifier.height(18.dp))

        // PC Status card (direct network check, independent of HA)
        val pcReachable = state.pcStatus == "reachable"
        val pcUnknown = state.pcStatus == "unknown"
        GothicCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PC Status",
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    color = InkSoft
                )
                Text(
                    if (pcReachable) "An" else if (pcUnknown) "—" else "Aus",
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pcReachable) Ok else if (pcUnknown) InkSoft else Err
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        // ---------- Switch Carousel ----------
        Box(Modifier.weight(1f)) {
            if (currentSwitch != null) {
                SwitchCarousel(
                    switch = currentSwitch,
                    totalSwitches = switches.size,
                    currentIndex = currentIndex,
                    enabled = state.mode != Mode.Offline && !state.vpnBusy,
                    onToggleOn = onPressCurrentOn,
                    onToggleOff = onPressCurrentOff,
                    onNext = onNextSwitch,
                    onPrev = onPrevSwitch,
                    state = currentSwitchState,
                    pcStatus = state.pcStatus
                )
            } else {
                // No switches configured yet
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface.copy(alpha = 0.4f))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Keine Switches konfiguriert.\nÖffne Einstellungen um Switches hinzuzufügen.",
                        color = InkSoft,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        }

        // ---------- Footer ----------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                Modifier
                    .weight(3f)
                    .clip(RoundedCornerShape(0.dp, 16.dp, 0.dp, 0.dp))
                    .background(Surface.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .clickable(onClick = onSettings)
            ) {
                Text("Einstellungen", fontSize = 13.sp, color = InkSoft)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .weight(3f)
                    .clip(RoundedCornerShape(16.dp, 0.dp, 0.dp, 0.dp))
                    .background(Surface.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .clickable(onClick = onSwitches)
            ) {
                Text("Switches", fontSize = 13.sp, color = InkSoft)
            }
        }
    }
}

private fun switchStateLabel(state: String): String {
    return when (state.lowercase()) {
        "on", "on", "true", "1", "active", "connected" -> "An"
        "off", "off", "false", "0", "inactive", "disconnected" -> "Aus"
        else -> state
    }
}

@Composable
private fun SwitchCarousel(
    switch: SwitchConfig,
    totalSwitches: Int,
    currentIndex: Int,
    enabled: Boolean,
    onToggleOn: () -> Unit,
    onToggleOff: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    state: String = "unknown",
    pcStatus: String = "unknown"
) {
    val density = LocalDensity.current
    var dragOffset by remember { mutableStateOf(0f) }

    // Swipe gesture detection
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(switch.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 80.dp.toPx()
                        if (dragOffset < -threshold) {
                            onNext()
                        } else if (dragOffset > threshold) {
                            onPrev()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        dragOffset = 0f
                    }
                )
            }
    ) {
        Crossfade(targetState = switch.id, label = "switch_carousel") { id ->
            Column(
                Modifier
                    .fillMaxSize()
                    .offset(x = dragOffset.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Switch name
                Text(
                    switch.name,
                    fontFamily = FontFamily.Serif,
                    fontSize = 28.sp,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Status text
                val isPcSwitch = switch.name.lowercase().contains("pc")
                val displayState = if (isPcSwitch && pcStatus != "unknown") pcStatus else state
                if (displayState != "unknown") {
                    val label = if (displayState == "reachable") "An"
                        else if (displayState == "unreachable") "Aus"
                        else switchStateLabel(displayState)
                    Text(
                        label,
                        fontSize = 14.sp,
                        color = if (isPcSwitch && displayState == "reachable") Ok
                                else if (isPcSwitch && displayState == "unreachable") Err
                                else InkSoft,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Switch indicator dots
                if (totalSwitches > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        for (i in 0 until totalSwitches) {
                            Box(
                                Modifier
                                    .width(if (i == currentIndex) 24.dp else 8.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (i == currentIndex) Accent else InkSoft.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }

                // Toggle controls
                if (switch.type == "toggle") {
                    // Single toggle button
                    ToggleButton(
                        label = "Schalten",
                        enabled = enabled,
                        onClick = onToggleOn
                    )
                } else {
                    // Dual buttons: On / Off
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ToggleButton(
                            label = "An",
                            enabled = enabled,
                            accent = true,
                            onClick = onToggleOn,
                            modifier = Modifier.weight(1f)
                        )
                        ToggleButton(
                            label = "Aus",
                            enabled = enabled,
                            onClick = onToggleOff,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleButton(
    label: String,
    enabled: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "toggle_button_scale"
    )

    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = if (accent) {
            ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
        } else {
            ButtonDefaults.buttonColors(containerColor = Surface, contentColor = Ink)
        },
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true },
                    onTap = { pressed = false }
                )
            }
            .scale(scale)
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VpnButton(text: String, accent: Boolean, busy: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        enabled = !busy,
        shape = RoundedCornerShape(16.dp),
        colors = if (accent) {
            ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
        } else {
            ButtonDefaults.outlinedButtonColors(contentColor = Ink)
        },
        border = if (accent) {
            BorderStroke(0.dp, Color.Transparent)
        } else {
            BorderStroke(1.5.dp, Line)
        },
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}