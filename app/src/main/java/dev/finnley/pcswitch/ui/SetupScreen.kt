package dev.finnley.pcswitch.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.finnley.pcswitch.state.Settings

@Composable
fun SetupScreen(
    initial: Settings,
    busy: Boolean,
    onSave: (String, String, String, String, String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var lan by remember { mutableStateOf(initial.lanBase) }
    var relay by remember { mutableStateOf(initial.relayBase) }
    var token by remember { mutableStateOf(initial.token) }
    var onEntity by remember { mutableStateOf(initial.onEntity) }
    var offEntity by remember { mutableStateOf(initial.offEntity) }
    var vpnId by remember { mutableStateOf(initial.vpnProfileId) }
    var pcIp by remember { mutableStateOf(initial.pcIp) }

    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, tint = Ink, contentDescription = "Zurück")
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        Text(
            "PC Switch",
            fontFamily = FontFamily.Serif,
            fontSize = 36.sp,
            color = Ink,
            letterSpacing = (-0.6).sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Richte die App ein, um deinen PC zu steuern.",
            fontSize = 15.sp,
            color = InkSoft,
            fontWeight = FontWeight.Normal
        )
        Spacer(Modifier.height(24.dp))

        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Home Assistant")
                Spacer(Modifier.height(12.dp))

                GothicOutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("HA Token") },
                    placeholder = { Text("Long-Lived Access Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Erstelle in Home Assistant unter Einstellungen → Benutzer → API-Tokens",
                    fontSize = 12.sp,
                    color = InkSoft
                )
                Spacer(Modifier.height(14.dp))

                GothicOutlinedTextField(
                    value = onEntity,
                    onValueChange = { onEntity = it },
                    label = { Text("On Entity ID") },
                    placeholder = { Text("button.turn_on_my_pc") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Entity ID des Power-On Buttons in Home Assistant",
                    fontSize = 12.sp,
                    color = InkSoft
                )
                Spacer(Modifier.height(14.dp))

                GothicOutlinedTextField(
                    value = offEntity,
                    onValueChange = { offEntity = it },
                    label = { Text("Off Entity ID") },
                    placeholder = { Text("input_button.turn_off_my_pc") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Entity ID des Power-Off Buttons in Home Assistant",
                    fontSize = 12.sp,
                    color = InkSoft
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Netzwerk")
                Spacer(Modifier.height(12.dp))

                GothicOutlinedTextField(
                    value = lan,
                    onValueChange = { lan = it },
                    label = { Text("LAN Base") },
                    placeholder = { Text("z. B. homeassistant.local:8123") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Host/IP und Port deines Home Assistant Servers, z. B. homeassistant.local:8123",
                    fontSize = 12.sp,
                    color = InkSoft
                )
                Spacer(Modifier.height(14.dp))

                GothicOutlinedTextField(
                    value = relay,
                    onValueChange = { relay = it },
                    label = { Text("Relay Base") },
                    placeholder = { Text("z. B. mein-rechner.ts.net") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Host/IP deines Relay Servers (z. B. Tailscale-Adresse), optional für Remote-Zugang",
                    fontSize = 12.sp,
                    color = InkSoft
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("OpenVPN")
                Spacer(Modifier.height(12.dp))

                GothicOutlinedTextField(
                    value = vpnId,
                    onValueChange = { vpnId = it },
                    label = { Text("OpenVPN Profile ID") },
                    placeholder = { Text("Profil-ID aus OpenVPN Connect") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Profile ID von OpenVPN Connect für deine Heimnetz-Verbindung",
                    fontSize = 12.sp,
                    color = InkSoft
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("PC Status")
                Spacer(Modifier.height(12.dp))

                GothicOutlinedTextField(
                    value = pcIp,
                    onValueChange = { pcIp = it },
                    label = { Text("PC IP-Adresse") },
                    placeholder = { Text("z. B. 192.168.1.50") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "IP deines PCs für die direkte Erreichbarkeitsprüfung (TCP Port 22/80)",
                    fontSize = 12.sp,
                    color = InkSoft
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        GothicButton(
            text = if (busy) "Speichere…" else "Speichern und starten",
            onClick = { onSave(lan, relay, token, onEntity, offEntity, vpnId, pcIp) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}