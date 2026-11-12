package dev.finnley.pcswitch.ui

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
    pcIp: String,
    notificationEnabled: Boolean,
    notificationMode: String,
    notificationPcName: String,
    updateChecking: Boolean,
    updateAvailable: String?,
    updateNotes: String?,
    downloadProgress: Int?,
    downloadFailed: Boolean,
    hasPreviousVersion: Boolean,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String, String, String) -> Unit,
    onSaveNotificationSettings: (Boolean, String, String) -> Unit,
    onPcIpSave: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onRetryDownload: () -> Unit,
    onRollback: () -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit
) {
    var showChangelog by remember { mutableStateOf(false) }
    var editedLan by remember { mutableStateOf(lanBase) }
    var editedRelay by remember { mutableStateOf(relayBase) }
    var editedToken by remember { mutableStateOf(token) }
    var visibleToken by remember { mutableStateOf(false) }
    var editedOnEntity by remember { mutableStateOf(onEntity) }
    var editedOffEntity by remember { mutableStateOf(offEntity) }
    var editedVpnId by remember { mutableStateOf(vpnProfileId) }
    var editedPcIp by remember { mutableStateOf(pcIp) }
    var editedNotificationEnabled by remember { mutableStateOf(notificationEnabled) }
    var editedNotificationMode by remember { mutableStateOf(notificationMode) }
    var editedNotificationPcName by remember { mutableStateOf(notificationPcName) }

    BackHandler { onBack() }

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
                GothicButton(
                    text = "Jetzt updaten",
                    onClick = {
                        showChangelog = false
                        onDownloadUpdate()
                    }
                )
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
                    placeholder = { Text("button.turn_on_my_pc") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                GothicOutlinedTextField(
                    value = editedOffEntity,
                    onValueChange = { editedOffEntity = it },
                    label = { Text("Off Entity ID") },
                    placeholder = { Text("input_button.turn_off_my_pc") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Netzwerk")
                Spacer(Modifier.height(12.dp))

                GothicOutlinedTextField(
                    value = editedLan,
                    onValueChange = { editedLan = it },
                    label = { Text("LAN Base") },
                    placeholder = { Text("z. B. homeassistant.local:8123") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                GothicOutlinedTextField(
                    value = editedRelay,
                    onValueChange = { editedRelay = it },
                    label = { Text("Relay Base") },
                    placeholder = { Text("z. B. mein-rechner.ts.net") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(20.dp))

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

        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("PC Status")
                Spacer(Modifier.height(12.dp))
                GothicOutlinedTextField(
                    value = editedPcIp,
                    onValueChange = { editedPcIp = it },
                    label = { Text("PC IP-Adresse") },
                    placeholder = { Text("z. B. 192.168.1.50") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Direkte Erreichbarkeitsprüfung (TCP Port 22/80), unabhängig von Home Assistant.",
                    fontSize = 12.sp,
                    color = InkSoft
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Benachrichtigungen")
                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Benachrichtigungen aktivieren",
                        fontSize = 14.sp,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = editedNotificationEnabled,
                        onCheckedChange = { editedNotificationEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Accent,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = SurfaceAlt,
                            uncheckedThumbColor = InkSoft
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text("Benachrichtigungsmode", fontSize = 13.sp, color = InkSoft)
                Spacer(Modifier.height(8.dp))

                val modes = listOf("all", "boot_only", "shutdown_only")
                val modeLabels = mapOf(
                    "all" to "Alle Statusänderungen",
                    "boot_only" to "Nur beim Starten",
                    "shutdown_only" to "Nur beim Abschalten"
                )

                modes.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = editedNotificationMode == mode,
                            onClick = { editedNotificationMode = mode },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Accent,
                                unselectedColor = InkSoft
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            modeLabels[mode] ?: mode,
                            fontSize = 13.sp,
                            color = Ink
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(12.dp))

                GothicOutlinedTextField(
                    value = editedNotificationPcName,
                    onValueChange = { editedNotificationPcName = it },
                    label = { Text("PC-Name") },
                    placeholder = { Text("PC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Name, der in Benachrichtigungen angezeigt wird.",
                    fontSize = 12.sp,
                    color = InkSoft
                )

                Spacer(Modifier.height(12.dp))
                GothicButton(
                    text = "Benachrichtigungen speichern",
                    onClick = {
                        onSaveNotificationSettings(
                            editedNotificationEnabled,
                            editedNotificationMode,
                            editedNotificationPcName
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        GothicButton(
            text = "Speichern",
            onClick = { onSave(editedLan, editedRelay, editedToken, editedOnEntity, editedOffEntity, editedVpnId, editedPcIp) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Update")
                Spacer(Modifier.height(12.dp))

                GothicButton(
                    text = "Auf Updates prüfen",
                    onClick = onCheckUpdate,
                    modifier = Modifier.fillMaxWidth()
                )
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
                        GothicButton(
                            text = "Erneut versuchen",
                            onClick = onRetryDownload,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        GothicButton(
                            text = "Jetzt updaten",
                            onClick = { showChangelog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        if (hasPreviousVersion) {
            GothicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    GothicLabel("Rollback")
                    Spacer(Modifier.height(12.dp))
                    GothicButton(
                        text = "Zurück zur vorherigen Version",
                        onClick = onRollback,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        GothicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                GothicLabel("Backup")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onExportSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Einstellungen exportieren")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onImportSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Einstellungen importieren")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Backup und Wiederherstellung der App-Einstellungen.",
                    fontSize = 12.sp,
                    color = InkSoft
                )
            }
        }
    }
}