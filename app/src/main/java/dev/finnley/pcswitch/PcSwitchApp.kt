package dev.finnley.pcswitch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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

sealed class Dest {
    data object Main : Dest()
    data object Settings : Dest()
    data object Switches : Dest()
    data object Setup : Dest()
}

@Composable
fun PcSwitchApp(vm: PcSwitchViewModel) {
    val state by vm.state.collectAsState()
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Accent)) {
        Surface(color = Bg, contentColor = Ink) {
            val dest = when {
                state.inSetup -> Dest.Setup
                state.inSwitches -> Dest.Switches
                state.inSettings -> Dest.Settings
                else -> Dest.Main
            }

            // Settings slides in from left
            AnimatedVisibility(
                visible = dest == Dest.Settings,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                SettingsScreen(
                    lanBase = vm.settings.lanBase,
                    relayBase = vm.settings.relayBase,
                    token = vm.settings.token,
                    onEntity = vm.settings.onEntity,
                    offEntity = vm.settings.offEntity,
                    vpnProfileId = vm.settings.vpnProfileId,
                    pcIp = vm.settings.pcIp,
                    updateChecking = state.updateChecking,
                    updateAvailable = state.updateAvailable?.versionName,
                    updateNotes = state.updateAvailable?.notes,
                    downloadProgress = state.downloadProgress,
                    downloadFailed = state.downloadFailed,
                    hasPreviousVersion = vm.hasPreviousVersion(),
                    onBack = vm::closeSettings,
                    onSave = vm::saveSetup,
                    onPcIpSave = vm::savePcIp,
                    onCheckUpdate = vm::checkForUpdate,
                    onDownloadUpdate = vm::downloadUpdate,
                    onRetryDownload = vm::retryDownload,
                    onRollback = vm::rollback
                )
            }

            // Switches slides in from right
            AnimatedVisibility(
                visible = dest == Dest.Switches,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it })
            ) {
                SwitchesScreen(
                    switches = state.switches,
                    onBack = vm::closeSwitches,
                    onAddSwitch = vm::addSwitch,
                    onEditSwitch = vm::updateSwitch,
                    onRemoveSwitch = vm::removeSwitch,
                    onReorderSwitches = vm::reorderSwitches,
                    entitySearchLoading = state.entitySearchLoading,
                    entitySearchError = state.entitySearchError,
                    entitySearchResults = state.entitySearchResults,
                    onSearchEntity = vm::openEntitySearch,
                    onEntitySelected = vm::onEntitySelected,
                    onEntitySearchDismiss = vm::closeEntitySearch
                )
            }

            // Setup
            AnimatedVisibility(
                visible = dest == Dest.Setup,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it })
            ) {
                SetupScreen(
                    initial = vm.settings,
                    busy = false,
                    onSave = vm::saveSetup,
                    onBack = vm::closeSetup
                )
            }

            // Main screen is always shown as background
            if (dest == Dest.Main) {
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
                    onPrevSwitch = vm::prevSwitch,
                    onDismissAuthError = vm::dismissAuthError
                )
            }
        }
    }
}
