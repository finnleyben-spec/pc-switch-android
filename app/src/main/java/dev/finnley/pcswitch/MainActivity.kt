package dev.finnley.pcswitch

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.finnley.pcswitch.deeplink.DeepLinkHandler
import dev.finnley.pcswitch.notifications.NotificationHelper
import dev.finnley.pcswitch.state.PcSwitchViewModel

class MainActivity : ComponentActivity() {
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

        val initialIntent = intent

        setContent {
            val model: PcSwitchViewModel = viewModel()

            // Handle deep link after ViewModel is created
            LaunchedEffect(initialIntent) {
                val result = DeepLinkHandler.handle(initialIntent)
                if (result == null) {
                    Log.i("MainActivity", "Deep link: null")
                } else {
                    when (result) {
                        is DeepLinkHandler.Result.Setup -> {
                            Log.i("MainActivity", "Deep link: Setup (onCreate)")
                            model.handleSetupDeepLink(
                                haUrl = result.haUrl,
                                haToken = result.haToken,
                                switches = result.switches,
                                autoConnect = result.autoConnect
                            )
                            Toast.makeText(this@MainActivity, "Deep link: Setup applied", Toast.LENGTH_SHORT).show()
                        }
                        is DeepLinkHandler.Result.Trigger -> {
                            Log.i("MainActivity", "Deep link: Trigger (onCreate)")
                            model.handleTriggerDeepLink(
                                press = result.press,
                                switchId = result.switchId
                            )
                            Toast.makeText(this@MainActivity, "Deep link: Trigger sent", Toast.LENGTH_SHORT).show()
                        }
                        DeepLinkHandler.Result.Unknown -> {
                            Log.i("MainActivity", "Deep link: Unknown")
                        }
                    }
                }
            }

            PcSwitchApp(model)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        setContent {
            val model: PcSwitchViewModel = viewModel()

            LaunchedEffect(intent) {
                val result = DeepLinkHandler.handle(intent)
                if (result == null) {
                    Log.i("MainActivity", "Deep link: null (onNewIntent)")
                } else {
                    when (result) {
                        is DeepLinkHandler.Result.Setup -> {
                            Log.i("MainActivity", "Deep link: Setup (onNewIntent)")
                            model.handleSetupDeepLink(
                                haUrl = result.haUrl,
                                haToken = result.haToken,
                                switches = result.switches,
                                autoConnect = result.autoConnect
                            )
                            Toast.makeText(this@MainActivity, "Deep link: Setup applied", Toast.LENGTH_SHORT).show()
                        }
                        is DeepLinkHandler.Result.Trigger -> {
                            Log.i("MainActivity", "Deep link: Trigger (onNewIntent)")
                            model.handleTriggerDeepLink(
                                press = result.press,
                                switchId = result.switchId
                            )
                            Toast.makeText(this@MainActivity, "Deep link: Trigger sent", Toast.LENGTH_SHORT).show()
                        }
                        DeepLinkHandler.Result.Unknown -> {
                            Log.i("MainActivity", "Deep link: Unknown (onNewIntent)")
                        }
                    }
                }
            }

            PcSwitchApp(model)
        }
    }
}
