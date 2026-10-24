package dev.finnley.pcswitch.pcstatus

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Direct PC reachability check via TCP socket (independent of Home Assistant).
 * Tries port 22 (SSH) first, falls back to port 80 (HTTP) if SSH is not listening.
 * Both are commonly open on Linux desktops.
 */
object PcStatusChecker {

    /** Timeout in ms for each TCP connection attempt. */
    private const val SOCKET_TIMEOUT_MS = 1500

    /** Check if the PC at [ip] is reachable via TCP. */
    fun isReachable(ip: String): Boolean {
        if (ip.isBlank()) return false
        return tryTcp(ip, 22) || tryTcp(ip, 80)
    }

    private fun tryTcp(host: String, port: Int): Boolean {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), SOCKET_TIMEOUT_MS)
            return true
        } catch (e: SocketTimeoutException) {
            return false
        } catch (e: IOException) {
            return false
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                // ignore
            }
        }
    }
}
