package dev.finnley.pcswitch.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dev.finnley.pcswitch.R

@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {

    companion object {
        const val ACTION_TOGGLE = "dev.finnley.pcswitch.TILE_TOGGLE"
        const val ACTION_STATE_CHANGED = "dev.finnley.pcswitch.TILE_STATE_CHANGED"
        const val EXTRA_IS_ON = "is_on"
        const val EXTRA_SWITCH_NAME = "switch_name"

        private const val PREFS_NAME = "pcswitch_tile"
        private const val KEY_IS_ON = "tile_is_on"
        private const val KEY_SWITCH_NAME = "tile_switch_name"
    }

    private var isOn = false
    private var currentSwitchName = "PC"

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STATE_CHANGED) {
                val on = intent.getBooleanExtra(EXTRA_IS_ON, false)
                val name = intent.getStringExtra(EXTRA_SWITCH_NAME)
                if (name != null) currentSwitchName = name
                isOn = on
                updateTileVisuals()
                saveState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(stateReceiver, IntentFilter(ACTION_STATE_CHANGED))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stateReceiver)
    }

    override fun onStartListening() {
        super.onStartListening()
        loadState()
        updateTileVisuals()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()

        // Use deep link to trigger toggle — works even when app is not running
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("pcswitch://trigger?press=toggle"))
        deepLink.setPackage(packageName)
        deepLink.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(deepLink)

        // Toggle local state immediately for responsiveness
        isOn = !isOn
        updateTileVisuals()
        saveState()
    }

    private fun loadState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        isOn = prefs.getBoolean(KEY_IS_ON, false)
        currentSwitchName = prefs.getString(KEY_SWITCH_NAME, "PC") ?: "PC"
    }

    private fun saveState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_ON, isOn)
            .putString(KEY_SWITCH_NAME, currentSwitchName)
            .apply()
    }

    private fun updateTileVisuals() {
        val tile = qsTile ?: return
        tile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val iconRes = if (isOn) R.drawable.ic_tile_active else R.drawable.ic_tile_inactive
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.label = currentSwitchName
    }
}
