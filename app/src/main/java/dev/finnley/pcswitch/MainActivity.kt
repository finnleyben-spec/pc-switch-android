package dev.finnley.pcswitch

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.finnley.pcswitch.deeplink.DeepLinkHandler
import dev.finnley.pcswitch.state.PcSwitchViewModel

class MainActivity : ComponentActivity() {
    private var vm: PcSwitchViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
