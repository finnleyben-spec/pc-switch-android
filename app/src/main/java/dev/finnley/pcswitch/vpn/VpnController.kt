package dev.finnley.pcswitch.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * "Connect to home": Tailscale aus (Broadcast) + OpenVPN Connect an (Intent).
 * Reihenfolge strikt (aus PLAN.md, gegen Offizielle Doku verifiziert).
 * Beide Apps optional — fehlende Pakete werden still ignoriert.
 */
object VpnController {

    private const val TS_PKG = "com.tailscale.ipn"
    private const val TS_RECV = "com.tailscale.ipn.IPNReceiver"
    private const val OV_PKG = "net.openvpn.openvpn"
    private const val OV_MAIN = "net.openvpn.unified.MainActivity"

    /**
     * @return null = OK gestartet, sonst Toast-Text für die UI.
     */
    fun connectToHome(ctx: Context, profileId: String): String? {
        sendTailscale(ctx, "com.tailscale.ipn.DISCONNECT_VPN")
        if (profileId.isBlank()) return "OpenVPN-Profile-ID fehlt (Einstellungen)"
        return try {
            val i = Intent("net.openvpn.openvpn.CONNECT").apply {
                setComponent(ComponentName(OV_PKG, OV_MAIN))
                putExtra("net.openvpn.openvpn.AUTOSTART_PROFILE_ID", profileId)
                putExtra("net.openvpn.openvpn.AUTOCONNECT", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
            null
        } catch (e: Exception) {
            "OpenVPN Connect nicht installiert"
        }
    }

    /** Heimnetz trennen: OpenVPN DISCONNECT, dann Tailscale wieder an. */
    fun disconnectHome(ctx: Context) {
        try {
            val i = Intent("net.openvpn.openvpn.DISCONNECT").apply {
                setComponent(ComponentName(OV_PKG, OV_MAIN))
                putExtra("net.openvpn.openvpn.STOP", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        } catch (_: Exception) {
            // OpenVPN nicht installiert — ignorieren
        }
        sendTailscale(ctx, "com.tailscale.ipn.CONNECT_VPN")
    }

    private fun sendTailscale(ctx: Context, action: String) {
        try {
            ctx.sendBroadcast(
                Intent(action).setComponent(ComponentName(TS_PKG, TS_RECV))
            )
        } catch (_: Exception) {
            // Tailscale nicht installiert — ignorieren
        }
    }
}
