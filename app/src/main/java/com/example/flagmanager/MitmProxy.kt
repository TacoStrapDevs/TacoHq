package com.example.flagmanager

import android.util.Log
import java.io.BufferedReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/**
 * MitmProxy — plain TCP/TLS proxy on PROXY_PORT.
 *
 * Intercept path  : Roblox CDN flag endpoints → injectFlags() returns flagsJson.
 * Telemetry path  : AntiBan.isTelemetryPath() → silent 204, never forwarded.
 * Pass-through    : everything else → forwarded to real upstream on correct port.
 *
 * HTTPS handling  : upstream connections on port 443 use CertManager.getTrustAllFactory()
 * so the proxy can talk TLS to the real server without its own cert store blocking it.
 * Roblox → proxy TLS requires the generated CA cert installed as a system cert
 * (or user cert with network_security_config allowing user CAs). See CertManager.
 *
 * Port is parsed from the Host header (host:port); defaults to 443.
 * No raw IP packet manipulation — no kernel driver needed.
 */
object MitmProxy {

    private const val TAG = "MitmProxy"

    private val CDN_HOSTS = setOf(
        "clientsettingscdn.roblox.com",
        "clientsettings.roblox.com"
    )

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
                val ss = ServerSocket(port)
                serverSocket = ss
                Log.d(TAG, "proxy listening on :$port")
                while (running) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: break
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
        serverSocket = null
        Log.d(TAG, "proxy stopped")
    }

    fun updateFlags(json: String) {
        flagsJson = json
    }

    private fun handleConnection(client: Socket) {
        client.soTimeout = 8_000
        try {
            val input = client.getInputStream().bufferedReader()
            val output = client.getOutputStream()

            val requestLine = input.readLine() ?: return
            val headers = readHeaders(input)

            val hostHeader = headers["host"]?.lowercase() ?: ""
            // Parse host and port from Host header (e.g. "clientsettingscdn.roblox.com:443")
            val hostOnly = hostHeader.substringBefore(":")
            val port = hostHeader.substringAfter(":", "443").toIntOrNull() ?: 443

            val parts = requestLine.split(" ")
            val method = parts.getOrElse(0) { "GET" }
            val path = parts.getOrElse(1) { "/" }

            val isCdn = CDN_HOSTS.any { hostOnly.contains(it) }
            val isFlagPath = FLAG_PATHS.any { path.startsWith(it) }

            when {
                isCdn && isFlagPath && method == "GET" -> {
                    // Intercept: return our flag JSON instead of the real CDN response.
                    injectFlags(output)
                    Log.d(TAG, "intercepted $hostOnly$path → injected flags")
                }
                AntiBan.isTelemetryPath(path) -> {
                    // Drop telemetry silently — 204 No Content, nothing forwarded to Roblox.
                    output.write(
                        "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .toByteArray()
                    )
                    output.flush()
                    Log.d(TAG, "telemetry dropped: $path")
                }
                else -> {
                    passThrough(method, path, hostOnly, port, headers, output)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "conn error: ${e.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    private fun readHeaders(reader: BufferedReader): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().lowercase()] =
                    line.substring(idx + 1).trim()
            }
        }
        return headers
    }

    private fun injectFlags(output: OutputStream) {
        val body = flagsJson.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-cache\r\n" +
            "Connection: close\r\n\r\n"
        output.write(response.toByteArray())
        output.write(body)
        output.flush()
    }

    private fun passThrough(
        method: String,
        path: String,
        host: String,
        port: Int,
        reqHeaders: Map<String, String>,
        clientOut: OutputStream
    ) {
        try {
            // Use TLS for port 443; plain socket for anything else (port 80, etc.)
            val upstream: Socket = if (port == 443) {
                val ssl = CertManager.getTrustAllFactory().createSocket(host, port) as SSLSocket
                ssl.startHandshake()
                ssl
            } else {
                Socket(host, port)
            }
            upstream.soTimeout = 8_000

            val upOut = upstream.getOutputStream()
            val sb = StringBuilder()
            sb.append("$method $path HTTP/1.1\r\n")
            reqHeaders.forEach { (k, v) ->
                if (!AntiBan.isBlockedHeader(k)) sb.append("$k: $v\r\n")
            }
            sb.append("Connection: close\r\n\r\n")
            upOut.write(sb.toString().toByteArray())
            upOut.flush()

            upstream.getInputStream().copyTo(clientOut)
            clientOut.flush()
            upstream.close()
        } catch (e: Exception) {
            runCatching {
                clientOut.write(
                    "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .toByteArray()
                )
                clientOut.flush()
            }
            Log.w(TAG, "passThrough error [$host:$port$path]: ${e.message}")
        }
    }
}
