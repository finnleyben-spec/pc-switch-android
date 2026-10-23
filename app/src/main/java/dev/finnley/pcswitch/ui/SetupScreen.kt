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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.finnley.pcswitch.state.Settings
import dev.finnley.pcswitch.update.UpdateManager

@Composable
fun SetupScreen(
    initial: Settings,
    busy: Boolean,
    updateChecking: Boolean,
    updateAvailable: UpdateManager.UpdateInfo?,
    onSave: (String, String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit
) {
    val context = LocalContext.current
    var lan by remember { mutableStateOf(initial.lanBase) }
    var relay by remember { mutableStateOf(initial.relayBase) }
    var token by remember { mutableStateOf(initial.token) }
    var onEntity by remember { mutableStateOf(initial.onEntity) }
    var offEntity by remember { mutableStateOf(initial.offEntity) }
    var vpnId by remember { mutableStateOf(initial.vpnProfileId) }
    var updateError by remember { mutableStateOf(false) }

    // Android back button goes back to main screen
    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Header with back button
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, tint = Ink, contentDescription = "Zurück")
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        
        Text(
            "PC Switch",
            fontFamily = FontFamily.Serif,
            fontSize = 34.sp,
            color = Ink,
            letterSpacing = (-0.6).sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Home Assistant + VPN. Die Defaults zeigen deine Endpunkte — bei Bedarf anpassen.",
            fontSize = 13.sp,
            color = InkSoft
        )
        Spacer(Modifier.height(18.dp))
        Field("LAN-BASE", lan, { lan = it }, "z. B. 192.168.0.205:8123")
        Spacer(Modifier.height(14.dp))
        Field("RELAY-BASE", relay, { relay = it }, "z. B. 100.106.82.86")
        Spacer(Modifier.height(14.dp))
        Field(
            "HA-TOKEN",
            token,
            { token = it },
            "Long-Lived Access Token",
            KeyboardType.Password
        )
        Spacer(Modifier.height(14.dp))
        Field("WAKE-ENTITY", onEntity, { onEntity = it }, "button.buro_wake_pc_buro")
        Spacer(Modifier.height(14.dp))
        Field("SHUTDOWN-ENTITY", offEntity, { offEntity = it }, "input_button.pc_ausschalten")
        Spacer(Modifier.height(14.dp))
        Field(
            "OPENVPN PROFILE ID",
            vpnId,
            { vpnId = it },
            "Profil-ID aus OpenVPN Connect (leer = kein Connect-to-Home)"
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onSave(lan, relay, token, onEntity, offEntity, vpnId) },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Verbinde…" else "Speichern & Verbinden", fontSize = 15.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                updateError = false
                try {
                    onCheckUpdate()
                } catch (e: Exception) {
                    updateError = true
                    Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = !updateChecking,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (updateChecking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Accent,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.padding(8.dp))
                    Text("Prüfe…", fontSize = 13.sp)
                }
            } else {
                Text("Update prüfen", fontSize = 13.sp)
            }
        }
        if (updateAvailable != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Update verfügbar: ${updateAvailable.versionName}\n${updateAvailable.notes}",
                fontSize = 11.sp,
                color = Accent
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDownloadUpdate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Jetzt herunterladen", fontSize = 13.sp)
            }
        }
        if (updateError) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Update-Check fehlgeschlagen. Bitte später erneut versuchen.",
                fontSize = 11.sp,
                color = Err
            )
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(Modifier.fillMaxWidth()) {
        UppercaseLabel(label)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(hint, color = InkSoft.copy(alpha = 0.6f), fontSize = 12.sp)
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Line,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                cursorColor = Accent
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}