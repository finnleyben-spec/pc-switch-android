package dev.finnley.pcswitch.state

import android.content.Context

/** Geräte-Einstellungen (SharedPreferences). Token wird verschlüsselt gespeichert (security audit). */
class Settings(context: Context) {
    private val p = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val secure = SecureSettings(context)

    var lanBase: String
        get() = p.getString(K_LAN, DEF_LAN) ?: DEF_LAN
        set(v) { p.edit().putString(K_LAN, v.trim()).apply() }

    var relayBase: String
        get() = p.getString(K_RELAY, DEF_RELAY) ?: DEF_RELAY
        set(v) { p.edit().putString(K_RELAY, v.trim()).apply() }

    /** HA-Access-Token, verschlüsselt in EncryptedSharedPreferences gespeichert. */
    var token: String
        get() = secure.getToken()
        set(v) { secure.setToken(v) }

    var onEntity: String
        get() = p.getString(K_ON, DEF_ON) ?: DEF_ON
        set(v) { p.edit().putString(K_ON, v.trim()).apply() }

    var offEntity: String
        get() = p.getString(K_OFF, DEF_OFF) ?: DEF_OFF
        set(v) { p.edit().putString(K_OFF, v.trim()).apply() }

    /** OpenVPN-Connect-Profil-ID für "Connect to home" (leer = noch nicht gesetzt). */
    var vpnProfileId: String
        get() = p.getString(K_VPN, "") ?: ""
        set(v) { p.edit().putString(K_VPN, v.trim()).apply() }

    var setupDone: Boolean
        get() = p.getBoolean(K_DONE, false)
        set(v) { p.edit().putBoolean(K_DONE, v).apply() }

    /** Direct PC IP address for TCP status check (independent of HA). */
    var pcIp: String
        get() = p.getString(K_PC_IP, DEF_PC_IP) ?: DEF_PC_IP
        set(v) { p.edit().putString(K_PC_IP, v.trim()).apply() }

    /** Notification settings */
    var notificationEnabled: Boolean
        get() = p.getBoolean(K_NOTIFICATION_ENABLED, true)
        set(v) { p.edit().putBoolean(K_NOTIFICATION_ENABLED, v).apply() }

    var notificationMode: String
        get() = p.getString(K_NOTIFICATION_MODE, "all") ?: "all"
        set(v) { p.edit().putString(K_NOTIFICATION_MODE, v.trim()).apply() }

    var notificationPcName: String
        get() = p.getString(K_NOTIFICATION_PC_NAME, DEF_PC_NAME) ?: DEF_PC_NAME
        set(v) { p.edit().putString(K_NOTIFICATION_PC_NAME, v.trim()).apply() }

    fun entitySet(): Set<String> = setOf(onEntity, offEntity).filter { it.isNotBlank() }.toSet()

    companion object {
        private const val NAME = "pcswitch"
        private const val K_LAN = "lan_base"
        private const val K_RELAY = "relay_base"
        private const val K_TOKEN = "token"
        private const val K_ON = "on_entity"
        private const val K_OFF = "off_entity"
        private const val K_VPN = "vpn_profile_id"
        private const val K_DONE = "setup_done"
        private const val K_PC_IP = "pc_ip"
        private const val K_NOTIFICATION_ENABLED = "notification_enabled"
        private const val K_NOTIFICATION_MODE = "notification_mode"
        private const val K_NOTIFICATION_PC_NAME = "notification_pc_name"
        // v3.0 (Public Release): keine personalen Defaults mehr — alles leer,
        // wird im Setup/Settings vom Nutzer konfiguriert.
        const val DEF_LAN = ""
        const val DEF_RELAY = ""
        const val DEF_ON = ""
        const val DEF_OFF = ""
        const val DEF_PC_IP = ""
        const val DEF_PC_NAME = "PC"
    }
}