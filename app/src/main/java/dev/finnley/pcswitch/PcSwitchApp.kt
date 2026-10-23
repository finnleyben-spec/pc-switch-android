package dev.finnley.pcswitch

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.finnley.pcswitch.state.PcSwitchViewModel
import dev.finnley.pcswitch.ui.Accent
import dev.finnley.pcswitch.ui.Ink
import dev.finnley.pcswitch.ui.MainScreen
import dev.finnley.pcswitch.ui.Paper
import dev.finnley.pcswitch.ui.SetupScreen

@Composable
fun PcSwitchApp(vm: PcSwitchViewModel) {
    val state by vm.state.collectAsState()
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Accent)) {
        Surface(color = Paper, contentColor = Ink) {
            if (state.inSetup) {
                SetupScreen(
                    initial = vm.settings,
                    busy = false,
                    onSave = vm::saveSetup
                )
            } else {
                MainScreen(
                    state = state,
                    onEntityId = vm.settings.onEntity,
                    offEntityId = vm.settings.offEntity,
                    onConnectToHome = vm::connectToHome,
                    onDisconnect = vm::disconnectHome,
                    onPressOn = vm::pressOn,
                    onPressOff = vm::pressOff,
                    onSettings = vm::openSetup,
                    onUpdate = vm::downloadUpdate
                )
            }
        }
    }
}
