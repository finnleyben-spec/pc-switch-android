package dev.finnley.pcswitch

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import dev.finnley.pcswitch.state.PcSwitchViewModel
import dev.finnley.pcswitch.ui.Accent
import dev.finnley.pcswitch.ui.Bg
import dev.finnley.pcswitch.ui.Ink
import dev.finnley.pcswitch.ui.MainScreen
import dev.finnley.pcswitch.ui.SettingsScreen
import dev.finnley.pcswitch.ui.SetupScreen

@Composable
fun PcSwitchApp(vm: PcSwitchViewModel) {
    val state by vm.state.collectAsState()
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Accent)) {
        Surface(color = Bg, contentColor = Ink) {
            if (state.inSetup) {
                SetupScreen(
                    initial = vm.settings,
                    busy = false,
                    updateChecking = state.updateChecking,
                    updateAvailable = state.updateAvailable,
                    onSave = vm::saveSetup,
                    onBack = vm::closeSetup,
                    onCheckUpdate = vm::checkForUpdate,
                    onDownloadUpdate = vm::downloadUpdate
                )
            } else if (state.inSettings) {
                SettingsScreen(
                    lanBase = vm.settings.lanBase,
                    relayBase = vm.settings.relayBase,
                    updateChecking = state.updateChecking,
                    updateAvailable = state.updateAvailable?.versionName,
                    updateNotes = state.updateAvailable?.notes,
                    downloadProgress = state.downloadProgress,
                    downloadFailed = state.downloadFailed,
                    hasPreviousVersion = vm.hasPreviousVersion(),
                    onBack = vm::closeSettings,
                    onSaveNetwork = vm::saveNetworkSettings,
                    onCheckUpdate = vm::checkForUpdate,
                    onDownloadUpdate = vm::downloadUpdate,
                    onRetryDownload = vm::retryDownload,
                    onRollback = vm::rollback
                )
            } else {
                MainScreen(
                    state = state,
                    onConnectToHome = vm::connectToHome,
                    onDisconnect = vm::disconnectHome,
                    onPressOn = vm::pressOn,
                    onPressOff = vm::pressOff,
                    onSettings = vm::openSettings,
                    onUpdate = vm::downloadUpdate
                )
            }
        }
    }
}
