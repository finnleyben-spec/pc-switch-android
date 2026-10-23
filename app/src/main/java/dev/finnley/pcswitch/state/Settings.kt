package dev.finnley.pcswitch.state

import android.content.Context

/** Geräte-Einstellungen (SharedPreferences). Defaults = bekannte Heim-Endpunkte. */
class Settings(context: Context) {
    private val p = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var lanBase: String
        get() = p.getString(K_LAN, DEF_LAN) ?: DEF_LAN
        set(v) { p.edit().putString(K_LAN, v.trim()).apply() }

    var relayBase: String
        get() = p.getString(K_RELAY, DEF_RELAY) ?: DEF_RELAY
        set(v) { p.edit().putString(K_RELAY, v.trim()).apply() }

    var token: String
        get() = p.getString(K_TOKEN, "") ?: ""
        set(v) { p.edit().putString(K_TOKEN, v.trim()).apply() }

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
        const val DEF_LAN = "192.168.0.205:8123"
        const val DEF_RELAY = "100.106.82.86"
        const val DEF_ON = "button.buro_wake_pc_buro"
        const val DEF_OFF = "input_button.pc_ausschalten"
    }
}
