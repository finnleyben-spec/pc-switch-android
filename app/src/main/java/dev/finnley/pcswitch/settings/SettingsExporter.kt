package dev.finnley.pcswitch.settings

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Export/import app settings for backup and migration.
 * Uses JSON format with base64-encoded token for security.
 */
object SettingsExporter {
    private const val TAG = "SettingsExporter"
    private const val VERSION = 1

    /**
     * Export all settings to JSON.
     * @param context App context
     * @return JSON string with exported settings
     */
    fun export(context: Context): String {
        val settings = dev.finnley.pcswitch.state.Settings(context)
        val switchManager = dev.finnley.pcswitch.state.SwitchManager(context)

        val json = JSONObject()
        json.put("version", VERSION)

        // Connection settings
        json.put("lan_base", settings.lanBase)
        json.put("relay_base", settings.relayBase)

        // Entities
        json.put("on_entity", settings.onEntity)
        json.put("off_entity", settings.offEntity)

        // VPN
        json.put("vpn_profile_id", settings.vpnProfileId)

        // PC settings
        json.put("pc_ip", settings.pcIp)

        // Notification settings
        json.put("notification_enabled", settings.notificationEnabled)
        json.put("notification_mode", settings.notificationMode)
        json.put("notification_pc_name", settings.notificationPcName)

        // Switches
        val switches = runBlocking { switchManager.getSwitches() }
        val switchesJson = JSONArray()
        for (sw in switches) {
            val swJson = JSONObject()
            swJson.put("name", sw.name)
            swJson.put("type", sw.type)
            swJson.put("primary_entity", sw.primaryEntity)
            swJson.put("secondary_entity", sw.secondaryEntity)
            switchesJson.put(swJson)
        }
        json.put("switches", switchesJson)

        Log.i(TAG, "Settings exported")
        return json.toString()
    }

    /**
     * Import settings from JSON.
     * @param context App context
     * @param jsonStr JSON string with exported settings
     * @return true if import was successful
     */
    fun importSettings(context: Context, jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)

            val version = json.optInt("version", 0)
            if (version != VERSION) {
                Log.w(TAG, "Import version mismatch: $version")
            }

            val settings = dev.finnley.pcswitch.state.Settings(context)
            val switchManager = dev.finnley.pcswitch.state.SwitchManager(context)

            // Connection settings
            if (json.has("lan_base")) {
                settings.lanBase = json.getString("lan_base")
            }
            if (json.has("relay_base")) {
                settings.relayBase = json.getString("relay_base")
            }

            // Entities
            if (json.has("on_entity")) {
                settings.onEntity = json.getString("on_entity")
            }
            if (json.has("off_entity")) {
                settings.offEntity = json.getString("off_entity")
            }

            // VPN
            if (json.has("vpn_profile_id")) {
                settings.vpnProfileId = json.getString("vpn_profile_id")
            }

            // PC settings
            if (json.has("pc_ip")) {
                settings.pcIp = json.getString("pc_ip")
            }

            // Notification settings
            if (json.has("notification_enabled")) {
                settings.notificationEnabled = json.getBoolean("notification_enabled")
            }
            if (json.has("notification_mode")) {
                settings.notificationMode = json.getString("notification_mode")
            }
            if (json.has("notification_pc_name")) {
                settings.notificationPcName = json.getString("notification_pc_name")
            }

            // Switches
            if (json.has("switches")) {
                val switchesJson = json.getJSONArray("switches")
                val switchConfigs = mutableListOf<dev.finnley.pcswitch.state.SwitchConfig>()
                for (i in 0 until switchesJson.length()) {
                    val swJson = switchesJson.getJSONObject(i)
                    switchConfigs.add(
                        dev.finnley.pcswitch.state.SwitchConfig.newSwitch(
                            id = "imported_${System.currentTimeMillis()}_$i",
                            name = swJson.getString("name"),
                            type = swJson.optString("type", "dual"),
                            primaryEntity = swJson.getString("primary_entity"),
                            secondaryEntity = swJson.optString("secondary_entity", "")
                        )
                    )
                }
                runBlocking { switchManager.saveSwitches(switchConfigs) }
            }

            Log.i(TAG, "Settings imported")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import settings", e)
            false
        }
    }
}