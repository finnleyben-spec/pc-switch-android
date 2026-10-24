package dev.finnley.pcswitch

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.finnley.pcswitch.state.PcSwitchViewModel
import dev.finnley.pcswitch.ui.Accent
import dev.finnley.pcswitch.ui.Bg
import dev.finnley.pcswitch.ui.Ink
import dev.finnley.pcswitch.ui.MainScreen
import dev.finnley.pcswitch.ui.SettingsScreen
import dev.finnley.pcswitch.ui.SetupScreen
import dev.finnley.pcswitch.ui.SwitchesScreen

@Composable
fun PcSwitchApp(vm: PcSwitchViewModel) {
    val state by vm.state.collectAsState()
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Accent)) {
        Surface(color = Bg, contentColor = Ink) {
            if (state.inSetup) {
                SetupScreen(
                    initial = vm.settings,
                    busy = false,
                    onSave = vm::saveSetup,
                    onBack = vm::closeSetup
                )
            } else if (state.inSwitches) {
                SwitchesScreen(
                    switches = state.switches,
                    onBack = vm::closeSwitches,
                    onAddSwitch = vm::addSwitch,
                    onEditSwitch = vm::updateSwitch,
                    onRemoveSwitch = vm::removeSwitch,
                    onReorderSwitches = vm::reorderSwitches
                )
            } else if (state.inSettings) {
                SettingsScreen(
                    lanBase = vm.settings.lanBase,
                    relayBase = vm.settings.relayBase,
                    token = vm.settings.token,
                    onEntity = vm.settings.onEntity,
                    offEntity = vm.settings.offEntity,
                    vpnProfileId = vm.settings.vpnProfileId,
                    updateChecking = state.updateChecking,
                    updateAvailable = state.updateAvailable?.versionName,
                    updateNotes = state.updateAvailable?.notes,
                    downloadProgress = state.downloadProgress,
                    downloadFailed = state.downloadFailed,
                    hasPreviousVersion = vm.hasPreviousVersion(),
                    onBack = vm::closeSettings,
                    onSave = vm::saveSetup,
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
                    onPressCurrentOn = vm::pressCurrentSwitchOn,
                    onPressCurrentOff = vm::pressCurrentSwitchOff,
                    onSettings = vm::openSettings,
                    onSwitches = vm::openSwitches,
                    onUpdate = vm::downloadUpdate,
                    onNextSwitch = vm::nextSwitch,
                    onPrevSwitch = vm::prevSwitch
                )
            }
        }
    }
}
