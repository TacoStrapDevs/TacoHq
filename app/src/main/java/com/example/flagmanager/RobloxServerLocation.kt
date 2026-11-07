
package com.example.flagmanager

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Estimated country information for a Roblox server endpoint.
 *
 * This is IP geolocation, not proof of the physical
 * location of the actual Roblox game server.
 */
data class RobloxServerLocationResult(
    val ipAddress: String?,
    val country: String,
    val countryCode: String?,
    val isKnown: Boolean,
    val message: String?
) {
    companion object {
        fun unknown(
            ip: String? = null,
            reason: String = "Location unavailable"
        ) = RobloxServerLocationResult(
            ipAddress = ip,
            country = "Unknown",
            countryCode = null,
            isKnown = false,
            message = reason
        )
    }
}

class RobloxServerLocation : AutoCloseable {

    companion object {
        private const val TAG = "TacoStrapLocation"

        private const val API_BASE =
            "https://ipwho.is/"

        private const val CONNECT_TIMEOUT = 7000
        private const val READ_TIMEOUT = 7000

        private const val CACHE_DURATION =
            6 * 60 * 60 * 1000L

        private val BLOCKED_IPV4_RANGES = listOf(
            "0.0.0.0" to 8,
            "10.0.0.0" to 8,
            "100.64.0.0" to 10,
            "127.0.0.0" to 8,
            "169.254.0.0" to 16,
            "172.16.0.0" to 12,
            "192.0.0.0" to 24,
            "192.0.2.0" to 24,
            "192.88.99.0" to 24,
            "192.168.0.0" to 16,
            "198.18.0.0" to 15,
            "198.51.100.0" to 24,
            "203.0.113.0" to 24,
            "224.0.0.0" to 4,
            "240.0.0.0" to 4
        )
    }

    private data class CachedLocation(
        val result: RobloxServerLocationResult,
        val timestamp: Long
    )

    private val cache =
        ConcurrentHashMap<String, CachedLocation>()

    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(
                runnable,
                "TacoStrap-LocationLookup"
            ).apply {
                isDaemon = true
            }
        }

    /**
     * Performs a synchronous lookup.
     *
     * IMPORTANT:
     * Call this from a background thread, never
     * directly from Android's main/UI thread.
     */
    fun lookup(rawIp: String): RobloxServerLocationResult {

        val ip = normalizeIp(rawIp)
            ?: return RobloxServerLocationResult.unknown(
                reason = "Invalid IP address"
            )

        val address = try {
            InetAddress.getByName(ip)
        } catch (e: Exception) {
            return RobloxServerLocationResult.unknown(
                ip,
                "IP validation failed"
            )
        }

        if (!isPublicAddress(address)) {
            return RobloxServerLocationResult.unknown(
                ip,
                "Endpoint is not a public IP address"
            )
        }

        // Return a cached result when still valid.
        val cached = cache[ip]

        if (
            cached != null &&
            System.currentTimeMillis() - cached.timestamp <
            CACHE_DURATION
        ) {
            return cached.result
        }

        var connection: HttpURLConnection? = null

        return try {
            val url = URL("$API_BASE$ip")

            connection =
                url.openConnection() as HttpURLConnection

            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "User-Agent",
                "TacoStrap-Android"
            )

            val responseCode = connection.responseCode

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return RobloxServerLocationResult.unknown(
                    ip,
                    "Location service unavailable"
                )
            }

            val response = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val json = JSONObject(response)

            if (!json.optBoolean("success", false)) {
                return RobloxServerLocationResult.unknown(
                    ip,
                    "Location lookup was unsuccessful"
                )
            }

            val country = json.optString("country")
                .trim()

            val countryCode = json.optString("country_code")
                .trim()
                .uppercase()

            if (
                country.isBlank() ||
                country == "null" ||
                !countryCode.matches(Regex("[A-Z]{2}"))
            ) {
                return RobloxServerLocationResult.unknown(
                    ip,
                    "Country information is incomplete"
                )
            }

            val result = RobloxServerLocationResult(
                ipAddress = ip,
                country = country,
                countryCode = countryCode,
                isKnown = true,
                message = "IP geolocation result"
            )

            if (cache.size >= 128) {
                cache.clear()
            }

            cache[ip] = CachedLocation(
                result = result,
                timestamp = System.currentTimeMillis()
            )

            result

        } catch (e: Exception) {
            Log.e(TAG, "Country lookup failed", e)

            RobloxServerLocationResult.unknown(
                ip,
                "Network or lookup error"
            )

        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Performs a lookup without blocking the UI thread.
     *
     * The callback executes on the background worker thread.
     */
    fun lookupAsync(
        ip: String,
        callback: (RobloxServerLocationResult) -> Unit
    ) {
        try {
            executor.execute {
                val result = lookup(ip)

                try {
                    callback(result)
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Location callback failed",
                        e
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not schedule lookup", e)

            callback(
                RobloxServerLocationResult.unknown(
                    ip,
                    "Lookup could not be scheduled"
                )
            )
        }
    }

    /**
     * Accepts only numeric IPv4 or IPv6 addresses.
     * Hostnames and malformed addresses are rejected.
     */
    private fun normalizeIp(rawIp: String): String? {

        val candidate = rawIp.trim()

        if (
            candidate.isEmpty() ||
            candidate.contains("%") ||
            !candidate.matches(Regex("[0-9a-fA-F:.]+"))
        ) {
            return null
        }

        // Validate IPv4 without allowing hostnames.
        if (!candidate.contains(":")) {

            val parts = candidate.split(".")

            if (parts.size != 4) return null

            val octets = parts.map { part ->
                if (part.isEmpty() || part.length > 3) {
                    return null
                }

                val value = part.toIntOrNull()
                    ?: return null

                if (value !in 0..255) {
                    return null
                }

                value
            }

            return octets.joinToString(".")
        }

        // IPv6 contains colons and cannot trigger DNS lookup.
        return try {
            val address = InetAddress.getByName(candidate)

            if (address !is Inet6Address) {
                null
            } else {
                address.hostAddress.substringBefore("%")
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Rejects private, reserved, loopback,
     * link-local, and non-global addresses.
     */
    private fun isPublicAddress(
        address: InetAddress
    ): Boolean {

        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }

        return when (address) {

            is Inet4Address -> {
                val bytes = address.address
                val value = bytesToLong(bytes)

                val blocked = BLOCKED_IPV4_RANGES.any {
                    (network, prefix) ->

                    val networkValue = ipv4ToLong(network)

                    val shift = 32 - prefix

                    (value shr shift) ==
                        (networkValue shr shift)
                }

                !blocked
            }

            is Inet6Address -> {
                val bytes = address.address

                // Only accept IPv6 global unicast space (2000::/3).
                val globalUnicast =
                    (bytes[0].toInt() and 0xE0) == 0x20

                // Reject IPv6 documentation addresses.
                val documentationAddress =
                    bytes[0] == 0x20.toByte() &&
                    bytes[1] == 0x01.toByte() &&
                    bytes[2] == 0x0D.toByte() &&
                    bytes[3] == 0xB8.toByte()

                globalUnicast && !documentationAddress
            }

            else -> false
        }
    }

    private fun bytesToLong(bytes: ByteArray): Long {
        var result = 0L

        for (byte in bytes) {
            result = (result shl 8) or
                (byte.toLong() and 0xFFL)
        }

        return result
    }

    private fun ipv4ToLong(ip: String): Long {
        val parts = ip.split(".")

        var result = 0L

        for (part in parts) {
            result = (result shl 8) or
                (part.toLong() and 0xFFL)
        }

        return result
    }

    fun clearCache() {
        cache.clear()
    }

    override fun close() {
        executor.shutdownNow()
        cache.clear()
    }
}
