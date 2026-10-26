package dev.finnley.pcswitch.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Window

/** Get entity type icon based on domain prefix. */
fun entityIcon(entityId: String) = when {
    entityId.startsWith("button.") || entityId.startsWith("input_button.") -> Icons.Rounded.PowerSettingsNew
    entityId.startsWith("light.") -> Icons.Rounded.BrightnessMedium
    entityId.startsWith("switch.") -> Icons.Rounded.SwapVert
    entityId.startsWith("cover.") -> Icons.Rounded.Window
    entityId.startsWith("lock.") -> Icons.Rounded.Lock
    entityId.startsWith("fan.") -> Icons.Rounded.Air
    entityId.startsWith("vacuum.") -> Icons.Rounded.Android
    else -> Icons.Rounded.Settings
}