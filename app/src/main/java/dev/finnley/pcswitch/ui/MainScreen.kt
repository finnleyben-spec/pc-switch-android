package dev.finnley.pcswitch.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onEntityId: String,
    offEntityId: String,
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
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // ---------- Header ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "PC Switch",
                    fontFamily = FontFamily.Serif,
                    fontSize = 34.sp,
                    color = Ink,
                    letterSpacing = (-0.6).sp
                )
                Text("v${BuildConfig.VERSION_NAME}", fontSize = 11.sp, color = InkSoft)
            }
            state.updateAvailable?.let { up ->
                Surface(
                    onClick = { onUpdate() },
                    shape = RoundedCornerShape(999.dp),
                    color = Paper
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

        // ---------- Status ----------
        val dot = when (state.mode) {
            Mode.HomeLan, Mode.HomeVpn -> Ok
            Mode.Remote, Mode.Connecting -> Warn
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
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
        }
        if (state.statusNote.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(state.statusNote, fontSize = 12.sp, color = InkSoft)
        }
        Spacer(Modifier.height(18.dp))

        // ---------- VPN CTA ----------
        when (state.mode) {
            Mode.Offline -> VpnButton(
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

        // ---------- Power ----------
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val enabled = state.mode != Mode.Offline && !state.vpnBusy
            PowerButton(
                label = "An",
                active = true,
                enabled = enabled,
                onClick = onPressOn,
                modifier = Modifier.weight(1f)
            )
            PowerButton(
                label = "Aus",
                active = false,
                enabled = enabled,
                onClick = onPressOff,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(18.dp))

        // ---------- Entities ----------
        EntityCard("WECKEN", onEntityId, state.onState)
        Spacer(Modifier.height(12.dp))
        EntityCard("AUSCHALTEN", offEntityId, state.offState)
        Spacer(Modifier.height(22.dp))

        // ---------- Footer ----------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("v${BuildConfig.VERSION_NAME}", fontSize = 11.sp, color = InkSoft)
            TextButton(onClick = onSettings) {
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
        shape = RoundedCornerShape(12.dp),
        colors = if (accent) {
            ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
        } else {
            ButtonDefaults.outlinedButtonColors(contentColor = Ink)
        },
        border = if (accent) {
            androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent)
        } else {
            androidx.compose.foundation.BorderStroke(1.5.dp, Line)
        },
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
