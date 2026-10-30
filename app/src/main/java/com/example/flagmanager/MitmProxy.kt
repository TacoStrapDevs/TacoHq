package com.example.flagmanager

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * MitmProxy — local HTTP/HTTPS proxy that sits between Roblox and its CDN.
 *
 * Flow:
 *   Roblox app → VPN tun → this proxy :8899 → (intercept if CDN host) → inject flagsJson
 *                                             → (pass-through otherwise) → real server
 *
 * CDN host pattern: clientsettingscdn.roblox.com  (the endpoint that delivers FFlags)
 * Endpoint path:    /v2/settings/application/...  or /v1/settings/...
 *
 * The proxy replaces the response body with our flagsJson and keeps the Connection alive.
 * AntiBan strips the X-CSRF-Token and telemetry headers to avoid detection.
 *
 * Packet-level routing in FlagVpnService.processPacket() is intentionally thin —
 * this proxy handles the full HTTP layer.
 */
object MitmProxy {

    private const val TAG = "MitmProxy"

    // Roblox CDN hosts whose responses we intercept
    private val CDN_HOSTS = setOf(
        "clientsettingscdn.roblox.com",
        "clientsettings.roblox.com",
        "roblox.com"
    )

    // Paths that serve FFlag JSON
    private val FLAG_PATHS = listOf(
        "/v2/settings/application",
        "/v1/settings/application",
        "/v2/client-settings"
    )

    @Volatile private var flagsJson: String = "{}"
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null

    fun start(initialFlags: String, port: Int) {
        if (running) { updateFlags(initialFlags); return }
        flagsJson = initialFlags
        running = true

        thread(name = "MitmProxy-accept", isDaemon = true) {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "listening on :$port")
                while (running) {
                    val client = serverSocket?.accept() ?: break
                    thread(name = "MitmProxy-conn", isDaemon = true) {
                        handleConnection(client)
                    }
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "accept error: ${e.message}")
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        Log.d(TAG, "stopped")
    }

    fun updateFlags(json: String) {
        flagsJson = json
        Log.d(TAG, "flags updated (${json.length} bytes)")
    }

    /**
     * Called from FlagVpnService tunnel loop.
     * Raw IP packet in → maybe-modified IP packet out (or null to drop).
     * We redirect TCP :80 / :443 to our proxy port by rewriting the destination.
     */
    fun processPacket(packet: ByteArray, length: Int): ByteArray? {
        // Minimal IP/TCP header rewrite — redirect dst port 80/443 to PROXY_PORT
        if (length < 40) return packet // too short to be TCP
        val ipVersion = (packet[0].toInt() and 0xF0) shr 4
        if (ipVersion != 4) return packet // only IPv4 for now

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 6) return packet // only TCP

        if (length < ipHeaderLen + 20) return packet
        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                      (packet[ipHeaderLen + 3].toInt() and 0xFF)

        if (dstPort != 80 && dstPort != 443) return packet

        // Rewrite destination port to proxy port
        val modified = packet.copyOf(length)
        modified[ipHeaderLen + 2] = (FlagVpnService.PROXY_PORT shr 8).toByte()
        modified[ipHeaderLen + 3] = (FlagVpnService.PROXY_PORT and 0xFF).toByte()

        // Rewrite destination IP to loopback so kernel routes it to our server socket
        modified[16] = 127; modified[17] = 0; modified[18] = 0; modified[19] = 1

        // Recalculate IP checksum
        recalcIpChecksum(modified, ipHeaderLen)
        // Recalculate TCP checksum
        recalcTcpChecksum(modified, ipHeaderLen)

        return modified
    }

    private fun handleConnection(client: Socket) {
        client.soTimeout = 10_000
        try {
            val input = client.getInputStream().bufferedReader()
            val output = client.getOutputStream()

            val requestLine = input.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            var line: String
            while (true) {
                line = input.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }

            val host = headers["host"]?.lowercase() ?: ""
            val isCdn = CDN_HOSTS.any { host.contains(it) }
            val parts = requestLine.split(" ")
            val method = parts.getOrElse(0) { "GET" }
            val path = parts.getOrElse(1) { "/" }
            val isFlagPath = FLAG_PATHS.any { path.startsWith(it) }

            if (isCdn && isFlagPath && method == "GET") {
                // Intercept — send our flags as the response
                val body = flagsJson.toByteArray(Charsets.UTF_8)
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ${body.size}\r\n")
                    append("Cache-Control: no-cache\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                output.write(response.toByteArray())
                output.write(body)
                output.flush()
                Log.d(TAG, "intercepted $host$path → injected ${body.size} bytes of flags")
            } else {
                // Pass through — forward to real server
                passThrough(method, path, host, headers, input, output, client)
            }
        } catch (e: Exception) {
            Log.w(TAG, "conn error: ${e.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    private fun passThrough(
        method: String, path: String, host: String,
        reqHeaders: Map<String, String>,
        clientIn: BufferedReader,
        clientOut: OutputStream,
        clientSocket: Socket
    ) {
        val isHttps = reqHeaders["x-proxy-https"] == "1"
        val port = if (isHttps) 443 else 80
        try {
            val upstream = Socket(host, port)
            upstream.soTimeout = 10_000
            val upOut = upstream.getOutputStream()
            val upIn = upstream.getInputStream()

            // Rebuild and send request
            val sb = StringBuilder()
            sb.append("$method $path HTTP/1.1\r\n")
            reqHeaders.forEach { (k, v) ->
                // Strip telemetry and tracking headers per AntiBan rules
                if (!AntiBan.isBlockedHeader(k)) sb.append("$k: $v\r\n")
            }
            sb.append("Connection: close\r\n\r\n")
            upOut.write(sb.toString().toByteArray())
            upOut.flush()

            // Pipe response back to client
            upIn.copyTo(clientOut)
            clientOut.flush()
            upstream.close()
        } catch (e: Exception) {
            Log.w(TAG, "passthrough error ($host$path): ${e.message}")
            clientOut.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n".toByteArray())
            clientOut.flush()
        }
    }

    // Minimal IP checksum (RFC 791)
    private fun recalcIpChecksum(pkt: ByteArray, hLen: Int) {
        pkt[10] = 0; pkt[11] = 0
        var sum = 0L
        for (i in 0 until hLen step 2) {
            sum += ((pkt[i].toLong() and 0xFF) shl 8) or (pkt[i + 1].toLong() and 0xFF)
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv().toInt() and 0xFFFF
        pkt[10] = (ck shr 8).toByte()
        pkt[11] = (ck and 0xFF).toByte()
    }

    // Minimal TCP checksum with pseudo-header (RFC 793)
    private fun recalcTcpChecksum(pkt: ByteArray, ipHLen: Int) {
        val tcpOff = ipHLen
        val totalLen = ((pkt[2].toLong() and 0xFF) shl 8) or (pkt[3].toLong() and 0xFF)
        val tcpLen = totalLen - ipHLen
        pkt[tcpOff + 16] = 0; pkt[tcpOff + 17] = 0

        var sum = 0L
        // Pseudo-header: src IP, dst IP, zero, protocol (6), TCP length
        for (i in 12 until 20 step 2) sum += ((pkt[i].toLong() and 0xFF) shl 8) or (pkt[i + 1].toLong() and 0xFF)
        sum += 6L // TCP protocol
        sum += tcpLen

        var i = tcpOff
        while (i < tcpOff + tcpLen - 1) {
            sum += ((pkt[i].toLong() and 0xFF) shl 8) or (pkt[i + 1].toLong() and 0xFF)
            i += 2
        }
        if (tcpLen % 2 != 0L) sum += (pkt[tcpOff + tcpLen.toInt() - 1].toLong() and 0xFF) shl 8

        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv().toInt() and 0xFFFF
        pkt[tcpOff + 16] = (ck shr 8).toByte()
        pkt[tcpOff + 17] = (ck and 0xFF).toByte()
    }
}
