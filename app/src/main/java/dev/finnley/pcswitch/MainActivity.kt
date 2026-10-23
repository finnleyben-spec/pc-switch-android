package dev.finnley.pcswitch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.finnley.pcswitch.state.PcSwitchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: PcSwitchViewModel = viewModel()
            PcSwitchApp(vm)
        }
    }
}
