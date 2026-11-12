package dev.finnley.pcswitch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.finnley.pcswitch.ha.HaClient
import dev.finnley.pcswitch.ha.HaDiscovery
import dev.finnley.pcswitch.state.Settings
import dev.finnley.pcswitch.state.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    // v3.0: Auto-Detection + Verbindungstest + Eingabe-Validierung
    var discovering by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var foundHosts by remember { mutableStateOf<List<HaDiscovery.FoundHost>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<HaClient.TokenCheck?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val scope = rememberCoroutineScope()

    fun validate(): Map<String, String> {
        val e = mutableMapOf<String, String>()
        if (token.trim().isBlank()) {
            e["token"] = "Token erforderlich"
        }
        if (lan.trim().isBlank()) {
            e["lan"] = "Server-Adresse erforderlich"
        } else if (!Validators.isValidHostPort(stripScheme(lan))) {
            e["lan"] = "Format: host:port, z. B. 192.168.1.10:8123"
        } else if (Validators.requiresHttps(lan)) {
            e["lan"] = "Öffentlicher Host: HTTPS erforderlich"
        }
        if (relay.trim().isNotEmpty()) {
            if (!Validators.isValidHostPort(stripScheme(relay))) {
                e["relay"] = "Format: host:port, z. B. mein-pc.ts.net:8123"
            } else if (Validators.requiresHttps(relay)) {
                e["relay"] = "Öffentlicher Host: HTTPS erforderlich"
            }
        }
        if (onEntity.trim().isBlank()) {
            e["onEntity"] = "On-Entity erforderlich (z. B. button.turn_on_my_pc)"
        } else if (!Validators.isValidEntityId(onEntity)) {
            e["onEntity"] = "Ungültige Entity-ID (domain.object, kleingeschrieben)"
        }
        if (offEntity.trim().isNotEmpty() && !Validators.isValidEntityId(offEntity)) {
            e["offEntity"] = "Ungültige Entity-ID (domain.object, kleingeschrieben)"
        }
        if (pcIp.trim().isNotEmpty() && !Validators.isValidIpv4(pcIp)) {
            e["pcIp"] = "Ungültige IPv4-Adresse"
        }
        return e
    }

    fun errorText(key: String): String = errors[key] ?: ""

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

        // ── Home Assistant ──────────────────────────────────────────────
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
                if (errorText("token").isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorText("token"), fontSize = 12.sp, color = Err)
                }
                Spacer(Modifier.height(14.dp))

                GothicButton(
                    text = when {
                        checking -> "Teste Verbindung…"
                        checkResult == null -> "Verbindung testen"
                        else -> "Nochmal testen"
                    },
                    onClick = {
                        checking = true
                        checkResult = null
                        scope.launch(Dispatchers.IO) {
                            checkResult = HaClient.checkToken(lan, token, false)
                            checking = false
                        }
                    },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                )
                checkResult?.let { r ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (r) {
                            HaClient.TokenCheck.VALID ->
                                "Verbunden — Server und Token sind gültig"
                            HaClient.TokenCheck.INVALID_TOKEN ->
                                "Server erreichbar, aber Token ungültig"
                            HaClient.TokenCheck.UNREACHABLE ->
                                "Server nicht erreichbar — Adresse, Port und HTTPS prüfen"
                        },
                        fontSize = 13.sp,
                        color = if (r == HaClient.TokenCheck.VALID) Ok else Err,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
                if (errorText("onEntity").isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorText("onEntity"), fontSize = 12.sp, color = Err)
                }
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
                    "Entity ID des Power-Off Buttons in Home Assistant (optional)",
                    fontSize = 12.sp,
                    color = InkSoft
                )
                if (errorText("offEntity").isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorText("offEntity"), fontSize = 12.sp, color = Err)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // ── Netzwerk ────────────────────────────────────────────────────
        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Netzwerk")
                Spacer(Modifier.height(12.dp))

                GothicOutlinedTextField(
                    value = lan,
                    onValueChange = { lan = it; checkResult = null },
                    label = { Text("LAN-Adresse") },
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
                if (errorText("lan").isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorText("lan"), fontSize = 12.sp, color = Err)
                }
                Spacer(Modifier.height(14.dp))

                // Auto-Detection über mDNS (Android-NsdManager, kein Extra-Dependency)
                GothicButton(
                    text = if (discovering) "Suche läuft…" else "Home Assistant automatisch suchen",
                    onClick = {
                        discovering = true
                        foundHosts = emptyList()
                        scope.launch(Dispatchers.IO) {
                            foundHosts = HaDiscovery().discover()
                            discovering = false
                            hasScanned = true
                        }
                    },
                    enabled = !discovering,
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasScanned && !discovering) {
                    if (foundHosts.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Keine HA-Instanz gefunden — Adresse manuell eingeben.",
                            fontSize = 12.sp,
                            color = InkSoft
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text("Gefunden (antippen, um zu übernehmen):", fontSize = 13.sp, color = Ink)
                        foundHosts.take(8).forEach { h ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${h.host}:${h.port}" +
                                    (if (h.serviceName.isNotBlank()) "  ·  ${h.serviceName}" else ""),
                                fontSize = 14.sp,
                                color = Accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        lan = h.base
                                        checkResult = null
                                        foundHosts = emptyList()
                                    }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                GothicOutlinedTextField(
                    value = relay,
                    onValueChange = { relay = it },
                    label = { Text("Relay-Adresse") },
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
                if (errorText("relay").isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorText("relay"), fontSize = 12.sp, color = Err)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // ── OpenVPN ─────────────────────────────────────────────────────
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

        // ── PC Status ───────────────────────────────────────────────────
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
                if (errorText("pcIp").isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorText("pcIp"), fontSize = 12.sp, color = Err)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        GothicButton(
            text = if (busy) "Speichere…" else "Speichern und starten",
            onClick = {
                val e = validate()
                errors = e
                if (e.isEmpty()) onSave(lan, relay, token, onEntity, offEntity, vpnId, pcIp)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** "http(s)://host:port" → "host:port" (für die Validatoren). */
private fun stripScheme(base: String): String =
    base.trim().removePrefix("https://").removePrefix("http://")
