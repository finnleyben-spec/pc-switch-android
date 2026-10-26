package dev.finnley.pcswitch

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.finnley.pcswitch.deeplink.DeepLinkHandler
import dev.finnley.pcswitch.notifications.NotificationHelper
import dev.finnley.pcswitch.state.PcSwitchViewModel

class MainActivity : ComponentActivity() {
    private var vm: PcSwitchViewModel? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Benachrichtigungen nicht erlaubt", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannel(this)

        // Request notification permission if not granted (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        setContent {
            val model: PcSwitchViewModel = viewModel()
            vm = model
            PcSwitchApp(model)
        }

        // Handle deep link on first launch
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: android.content.Intent) {
        val result = DeepLinkHandler.handle(intent) ?: return
        val model = vm ?: return

        when (result) {
            is DeepLinkHandler.Result.Setup -> {
                model.handleSetupDeepLink(
                    haUrl = result.haUrl,
                    haToken = result.haToken,
                    switches = result.switches,
                    autoConnect = result.autoConnect
                )
                Toast.makeText(this, "Deep link: Setup applied", Toast.LENGTH_SHORT).show()
            }
            is DeepLinkHandler.Result.Trigger -> {
                model.handleTriggerDeepLink(
                    press = result.press,
                    switchId = result.switchId
                )
                Toast.makeText(this, "Deep link: Trigger sent", Toast.LENGTH_SHORT).show()
            }
            DeepLinkHandler.Result.Unknown -> {
                // Unknown deep link, ignore
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vm = null
    }
}
