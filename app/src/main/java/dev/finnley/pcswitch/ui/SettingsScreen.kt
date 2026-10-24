package dev.finnley.pcswitch.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import dev.finnley.pcswitch.BuildConfig

@Composable
fun SettingsScreen(
    lanBase: String,
    relayBase: String,
    token: String,
    onEntity: String,
    offEntity: String,
    vpnProfileId: String,
    updateChecking: Boolean,
    updateAvailable: String?,
    updateNotes: String?,
    downloadProgress: Int?,
    downloadFailed: Boolean,
    hasPreviousVersion: Boolean,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onRetryDownload: () -> Unit,
    onRollback: () -> Unit
) {
    var showChangelog by remember { mutableStateOf(false) }
    var editedLan by remember { mutableStateOf(lanBase) }
    var editedRelay by remember { mutableStateOf(relayBase) }
    var editedToken by remember { mutableStateOf(token) }
    var visibleToken by remember { mutableStateOf(false) }
    var editedOnEntity by remember { mutableStateOf(onEntity) }
    var editedOffEntity by remember { mutableStateOf(offEntity) }
    var editedVpnId by remember { mutableStateOf(vpnProfileId) }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = {
                Text(
                    "Update verfügbar: $updateAvailable",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    color = Ink
                )
            },
            text = {
                Text(
                    updateNotes ?: "",
                    fontSize = 13.sp,
                    color = InkSoft
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showChangelog = false
                        onDownloadUpdate()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Jetzt updaten")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showChangelog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, tint = Ink, contentDescription = "Zurück")
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        Text(
            "Einstellungen",
            fontFamily = FontFamily.Serif,
            fontSize = 36.sp,
            color = Ink,
            letterSpacing = (-0.6).sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "App-Informationen, Netzwerk und Updates",
            fontSize = 15.sp,
            color = InkSoft,
            fontWeight = FontWeight.Normal
        )
        Spacer(Modifier.height(24.dp))

        // Version Info Card
        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Version")
                Spacer(Modifier.height(10.dp))
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontSize = 17.sp,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // Home Assistant Settings Card
        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Home Assistant")
                Spacer(Modifier.height(12.dp))

                GothicOutlinedTextField(
                    value = editedToken,
                    onValueChange = { editedToken = it },
                    label = { Text("HA Token") },
                    placeholder = { Text("Long-Lived Access Token") },
                    singleLine = true,
                    password = true,
                    visible = visibleToken,
                    onVisibleChange = { visibleToken = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                GothicOutlinedTextField(
                    value = editedOnEntity,
                    onValueChange = { editedOnEntity = it },
                    label = { Text("On Entity ID") },
                    placeholder = { Text("button.buro_wake_pc_buro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                GothicOutlinedTextField(
                    value = editedOffEntity,
                    onValueChange = { editedOffEntity = it },
                    label = { Text("Off Entity ID") },
                    placeholder = { Text("input_button.pc_ausschalten") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // Network Settings Card
        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Netzwerk")
                Spacer(Modifier.height(12.dp))

                GothicOutlinedTextField(
                    value = editedLan,
                    onValueChange = { editedLan = it },
                    label = { Text("LAN Base") },
                    placeholder = { Text("z. B. 192.168.0.205:8123") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                GothicOutlinedTextField(
                    value = editedRelay,
                    onValueChange = { editedRelay = it },
                    label = { Text("Relay Base") },
                    placeholder = { Text("z. B. 100.106.82.86") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // VPN Settings Card
        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("OpenVPN")
                Spacer(Modifier.height(12.dp))

                GothicOutlinedTextField(
                    value = editedVpnId,
                    onValueChange = { editedVpnId = it },
                    label = { Text("OpenVPN Profile ID") },
                    placeholder = { Text("Profil-ID aus OpenVPN Connect") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // Save Button
        Button(
            onClick = { onSave(editedLan, editedRelay, editedToken, editedOnEntity, editedOffEntity, editedVpnId) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Speichern", fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))

        // Update Section
        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Update")
                Spacer(Modifier.height(12.dp))

                // Manual check button
                Button(
                    onClick = onCheckUpdate,
                    enabled = !updateChecking,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Auf Updates prüfen", fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))

                if (updateAvailable != null) {
                    Text(
                        "Update verfügbar: $updateAvailable",
                        fontSize = 13.sp,
                        color = Accent
                    )
                    Spacer(Modifier.height(12.dp))

                    if (downloadProgress != null) {
                        Text(
                            "Herunterladen... $downloadProgress%",
                            fontSize = 13.sp,
                            color = InkSoft
                        )
                    } else if (downloadFailed) {
                        Text(
                            "Download fehlgeschlagen",
                            fontSize = 13.sp,
                            color = Err
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRetryDownload,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Erneut versuchen", fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = { showChangelog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Jetzt updaten", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // Rollback Section
        if (hasPreviousVersion) {
            GothicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    GothicLabel("Rollback")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRollback,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zurück zur vorherigen Version", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
