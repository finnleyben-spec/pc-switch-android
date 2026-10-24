package dev.finnley.pcswitch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.finnley.pcswitch.BuildConfig
import dev.finnley.pcswitch.state.Mode
import dev.finnley.pcswitch.state.UiState

@Composable
fun MainScreen(
    state: UiState,
    onConnectToHome: () -> Unit,
    onDisconnect: () -> Unit,
    onPressOn: () -> Unit,
    onPressOff: () -> Unit,
    onSettings: () -> Unit,
    onUpdate: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
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
                        .clickable { onUpdate() }
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

        // ---------- Status Card (Glassmorphism) ----------
        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                val dot = when (state.mode) {
                    Mode.HomeLan, Mode.HomeVpn -> Ok
                    Mode.Remote, Mode.Connecting -> Accent
                    Mode.Offline -> Err
                }
                val label = when (state.mode) {
                    Mode.HomeLan -> "Verbunden · Heimnetz (WLAN)"
                    Mode.HomeVpn -> "Verbunden · Heimnetz per VPN"
                    Mode.Remote -> "Remote · Tailscale-Relay"
                    Mode.Connecting -> "Verbinde Heimnetz…"
                    Mode.Offline -> "Offline"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(dot)
                    Spacer(Modifier.width(10.dp))
                    Text(label, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Medium)
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
            Mode.HomeVpn -> VpnButton(
                text = "Heimnetz trennen",
                accent = false,
                busy = state.vpnBusy,
                onClick = onDisconnect
            )
            else -> Unit
        }
        Spacer(Modifier.height(18.dp))

        // ---------- Power Toggle ----------
        PowerToggle(
            enabled = state.mode != Mode.Offline && !state.vpnBusy,
            onToggle = { onPressOn() },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))

        // ---------- Footer ----------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSettings() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Einstellungen", fontSize = 12.sp, color = InkSoft)
            }
        }
    }
}

@Composable
private fun VpnButton(text: String, accent: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
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