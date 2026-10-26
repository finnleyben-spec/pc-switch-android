package dev.finnley.pcswitch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.finnley.pcswitch.state.SwitchConfig

data class SwitchDragData(val switchId: String, val index: Int)

@Composable
fun SwitchesScreen(
    switches: List<SwitchConfig>,
    onBack: () -> Unit,
    onAddSwitch: (String, String, String, String) -> Unit,
    onEditSwitch: (SwitchConfig) -> Unit,
    onRemoveSwitch: (String) -> Unit,
    onReorderSwitches: (Int, Int) -> Unit,
    entitySearchLoading: Boolean = false,
    entitySearchError: String? = null,
    entitySearchResults: List<dev.finnley.pcswitch.ha.HaClient.EntityState> = emptyList(),
    onSearchEntity: (String) -> Unit = {},
    onEntitySelected: (String) -> Unit = {},
    onEntitySearchDismiss: () -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSwitch by remember { mutableStateOf<SwitchConfig?>(null) }

    BackHandler { onBack() }

    if (showAddDialog) {
        SwitchDialog(
            title = "Switch hinzufügen",
            switchConfig = SwitchConfig("new", "", "dual", "", ""),
            onDismiss = { showAddDialog = false },
            onSave = { sw ->
                onAddSwitch(sw.name, sw.type, sw.primaryEntity, sw.secondaryEntity)
                showAddDialog = false
            },
            entitySearchLoading = entitySearchLoading,
            entitySearchError = entitySearchError,
            entitySearchResults = entitySearchResults,
            onSearchEntity = onSearchEntity,
            onEntitySelected = onEntitySelected,
            onEntitySearchDismiss = onEntitySearchDismiss
        )
    }

    editingSwitch?.let { sw ->
        SwitchDialog(
            title = "Switch bearbeiten",
            switchConfig = sw,
            onDismiss = { editingSwitch = null },
            onSave = { updated ->
                onEditSwitch(updated)
                editingSwitch = null
            },
            entitySearchLoading = entitySearchLoading,
            entitySearchError = entitySearchError,
            entitySearchResults = entitySearchResults,
            onSearchEntity = onSearchEntity,
            onEntitySelected = onEntitySelected,
            onEntitySearchDismiss = onEntitySearchDismiss
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
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Rounded.Add, tint = Accent, contentDescription = "Hinzufügen")
            }
        }
        Spacer(Modifier.height(12.dp))

        Text(
            "Switches",
            fontFamily = FontFamily.Serif,
            fontSize = 36.sp,
            color = Ink,
            letterSpacing = (-0.6).sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Verwalte deine Power-Switches",
            fontSize = 15.sp,
            color = InkSoft,
            fontWeight = FontWeight.Normal
        )
        Spacer(Modifier.height(24.dp))

        // Switch list
        if (switches.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Keine Switches. Tippe auf + um einen hinzuzufügen.",
                    color = InkSoft,
                    fontSize = 15.sp
                )
            }
        } else {
            switches.forEachIndexed { index, switchConfig ->
                SwitchListItem(
                    switchConfig = switchConfig,
                    index = index,
                    onEdit = { editingSwitch = switchConfig },
                    onRemove = { onRemoveSwitch(switchConfig.id) },
                    onDragStart = { dragData ->
                        // Drag handling happens via modifier
                    },
                    onDrop = { sourceIndex, targetIndex ->
                        if (sourceIndex != targetIndex) {
                            onReorderSwitches(sourceIndex, targetIndex)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SwitchListItem(
    switchConfig: SwitchConfig,
    index: Int,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: (SwitchDragData) -> Unit,
    onDrop: (Int, Int) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Drag handle
            Icon(
                Icons.Rounded.DragIndicator,
                tint = InkSoft.copy(alpha = 0.5f),
                contentDescription = "Ziehen",
                modifier = Modifier.size(18.dp)
            )

            // Entity type icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Accent.copy(alpha = 0.15f))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    entityIcon(switchConfig.primaryEntity),
                    tint = Accent,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Switch name
            Column(Modifier.weight(1f)) {
                Text(
                    switchConfig.name,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    switchConfig.primaryEntity,
                    color = InkSoft,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            // Type badge
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Accent.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    if (switchConfig.type == "toggle") "Toggle" else "Dual",
                    color = Accent,
                    fontSize = 11.sp
                )
            }

            // Edit button
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Edit, tint = InkSoft, contentDescription = "Bearbeiten", modifier = Modifier.size(16.dp))
            }

            // Delete button
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Delete, tint = Err, contentDescription = "Löschen", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SwitchDialog(
    title: String,
    switchConfig: SwitchConfig,
    onDismiss: () -> Unit,
    onSave: (SwitchConfig) -> Unit,
    entitySearchLoading: Boolean,
    entitySearchError: String?,
    entitySearchResults: List<dev.finnley.pcswitch.ha.HaClient.EntityState>,
    onSearchEntity: (String) -> Unit,
    onEntitySelected: (String) -> Unit,
    onEntitySearchDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(switchConfig.name) }
    var type by remember { mutableStateOf(switchConfig.type) }
    var primaryEntity by remember { mutableStateOf(switchConfig.primaryEntity) }
    var secondaryEntity by remember { mutableStateOf(switchConfig.secondaryEntity) }
    var stateEntity by remember { mutableStateOf(switchConfig.stateEntity) }
    var searchTarget by remember { mutableStateOf("") }

    // Entity search overlay
    if (searchTarget != "") {
        EntitySearchDialog(
            title = when (searchTarget) {
                "primary" -> "Primary Entity suchen"
                "secondary" -> "Secondary Entity suchen"
                "state" -> "State Entity suchen"
                else -> "Entity suchen"
            },
            entities = entitySearchResults,
            loading = entitySearchLoading,
            error = entitySearchError,
            onDismiss = {
                searchTarget = ""
                onEntitySearchDismiss()
            },
            onSelect = { entityId ->
                when (searchTarget) {
                    "primary" -> primaryEntity = entityId
                    "secondary" -> secondaryEntity = entityId
                    "state" -> stateEntity = entityId
                }
                searchTarget = ""
                onEntitySelected(entityId)
                onEntitySearchDismiss()
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    color = Ink
                )
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    GothicOutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        placeholder = { Text("z. B. Büro PC") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))

                    // Type selector
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { type = "toggle" },
                            enabled = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = if (type == "toggle") {
                                ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
                            } else {
                                ButtonDefaults.buttonColors(containerColor = Surface, contentColor = InkSoft)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Toggle", fontSize = 13.sp)
                        }
                        Button(
                            onClick = { type = "dual" },
                            enabled = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = if (type == "dual") {
                                ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
                            } else {
                                ButtonDefaults.buttonColors(containerColor = Surface, contentColor = InkSoft)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dual", fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // Primary entity with search button
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            GothicOutlinedTextField(
                                value = primaryEntity,
                                onValueChange = { primaryEntity = it },
                                label = { Text("Primary Entity") },
                                placeholder = { Text("button.buro_wake_pc") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            searchTarget = "primary"
                            onSearchEntity("primary")
                        }) {
                            Icon(Icons.Rounded.Search, tint = Accent, contentDescription = "Suche")
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    if (type == "dual") {
                        // Secondary entity with search button
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                GothicOutlinedTextField(
                                    value = secondaryEntity,
                                    onValueChange = { secondaryEntity = it },
                                    label = { Text("Secondary Entity (Off)") },
                                    placeholder = { Text("input_button.pc_aus") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                searchTarget = "secondary"
                                onSearchEntity("secondary")
                            }) {
                                Icon(Icons.Rounded.Search, tint = Accent, contentDescription = "Suche")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // State entity with search button
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            GothicOutlinedTextField(
                                value = stateEntity,
                                onValueChange = { stateEntity = it },
                                label = { Text("State Entity (optional)") },
                                placeholder = { Text("switch.buro_pc") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            searchTarget = "state"
                            onSearchEntity("state")
                        }) {
                            Icon(Icons.Rounded.Search, tint = Accent, contentDescription = "Suche")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSave(
                            switchConfig.copy(
                                name = name,
                                type = type,
                                primaryEntity = primaryEntity,
                                secondaryEntity = secondaryEntity,
                                stateEntity = stateEntity
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        )
    }
}