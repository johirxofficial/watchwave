package com.watchwave.app.core

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** LAN helpers used to display an honest, directly reachable share link. */
object NetworkUtils {

    data class LanAddress(val ipv4: String, val interfaceName: String)

    fun lanAddress(): LanAddress? {
        val candidates = mutableListOf<LanAddress>()
        val interfaces = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
        } catch (_: Exception) {
            return null
        }
        for (network in interfaces) {
            try {
                if (!network.isUp || network.isLoopback || network.isVirtual) continue
                for (address in Collections.list(network.inetAddresses)) {
                    if (address is Inet4Address && !address.isLoopbackAddress && address.isSiteLocalAddress) {
                        candidates += LanAddress(address.hostAddress.orEmpty(), network.name.orEmpty())
                    }
                }
            } catch (_: Exception) {
                // A disappearing interface is normal on mobile devices.
            }
        }
        // Prefer Wi-Fi/Ethernet over cellular tethering interfaces.
        return candidates.firstOrNull {
            val name = it.interfaceName.lowercase()
            name.startsWith("wlan") || name.startsWith("eth")
        } ?: candidates.firstOrNull()
    }

    fun lanBaseUrl(port: Int): String? {
        val address = lanAddress()?.ipv4?.takeIf { it.isNotBlank() } ?: return null
        return "http://$address:$port"
    }

    fun isLocalBaseUrl(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("http://localhost") ||
            normalized.startsWith("http://127.0.0.1") ||
            normalized.startsWith("http://10.") ||
            normalized.startsWith("http://192.168.") ||
            normalized.startsWith("http://172.") ||
            normalized.startsWith("http://[::1]")
    }
}
