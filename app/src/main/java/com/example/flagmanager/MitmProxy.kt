package com.example.flagmanager

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

object MitmProxy {

    private const val TAG           = "MitmProxy"
    const val PROXY_PORT_HTTP       = 8899
    const val PROXY_PORT_HTTPS      = 8900

    private val CDN_HOSTS = setOf(
        "clientsettingscdn.roblox.com",
        "clientsettings.roblox.com"
    )
    private val FLAG_PATHS = listOf(
        "/v2/settings/application",
        "/v1/settings/application",
        "/v2/client-settings"
    )

    // set by FlagVpnService before start() — needed so protect() works in passThrough
    @Volatile var vpnService: FlagVpnService? = null

    @Volatile private var flagsJson = "{}"
    @Volatile private var running   = false

    private var httpServer  : ServerSocket?    = null
    private var httpsServer : SSLServerSocket? = null

    fun start(initialFlags: String, port: Int, context: Context) {
        if (running) { updateFlags(initialFlags); return }
        flagsJson = initialFlags
        running   = true

        // HTTP — port 8899
        thread(name = "Proxy-HTTP", isDaemon = true) {
            try {
                val ss = ServerSocket(PROXY_PORT_HTTP)
                httpServer = ss
                Log.d(TAG, "HTTP proxy :$PROXY_PORT_HTTP")
                while (running) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { handlePlain(client) }
                }
            } catch (e: Exception) { if (running) Log.e(TAG, "HTTP accept: ${e.message}") }
        }

        // HTTPS — port 8900
        thread(name = "Proxy-HTTPS", isDaemon = true) {
            val ssf = CertManager.getServerSocketFactory(context) ?: run {
                Log.w(TAG, "no CA cert — HTTPS interception disabled"); return@thread
            }
            try {
                val ss = ssf.createServerSocket(PROXY_PORT_HTTPS) as SSLServerSocket
                httpsServer = ss
                Log.d(TAG, "HTTPS proxy :$PROXY_PORT_HTTPS")
                while (running) {
                    val client = runCatching { ss.accept() as SSLSocket }.getOrNull() ?: break
                    thread(isDaemon = true) { handleSsl(client) }
                }
            } catch (e: Exception) { if (running) Log.e(TAG, "HTTPS accept: ${e.message}") }
        }
    }

    fun stop() {
        running = false
        runCatching { httpServer?.close() }
        runCatching { httpsServer?.close() }
        httpServer = null; httpsServer = null; vpnService = null
        Log.d(TAG, "proxy stopped")
    }

    fun updateFlags(json: String) { flagsJson = json }

    // ── plain HTTP ────────────────────────────────────────────────────────────

    private fun handlePlain(client: Socket) {
        client.soTimeout = 8_000
        try {
            processRequest(client.getInputStream().bufferedReader(), client.getOutputStream(), ssl = false)
        } catch (e: Exception) { Log.w(TAG, "plain: ${e.message}") }
        finally { runCatching { client.close() } }
    }

    // ── HTTPS ─────────────────────────────────────────────────────────────────

    private fun handleSsl(client: SSLSocket) {
        client.soTimeout     = 8_000
        client.useClientMode = false
        try {
            client.startHandshake()
            processRequest(client.inputStream.bufferedReader(), client.outputStream, ssl = true)
        } catch (e: Exception) { Log.w(TAG, "ssl: ${e.message}") }
        finally { runCatching { client.close() } }
    }

    // ── shared handler ────────────────────────────────────────────────────────

    private fun processRequest(input: BufferedReader, output: OutputStream, ssl: Boolean) {
        val requestLine = input.readLine() ?: return
        val headers     = readHeaders(input)
        val host        = headers["host"]?.lowercase() ?: ""
        val parts       = requestLine.split(" ")
        val method      = parts.getOrElse(0) { "GET" }
        val path        = parts.getOrElse(1) { "/" }

        // drop telemetry silently
        if (AntiBan.isTelemetryPath(path)) {
            output.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            output.flush()
            Log.d(TAG, "telemetry dropped: $path")
            return
        }

        val isCdn      = CDN_HOSTS.any { host.contains(it) }
        val isFlagPath = FLAG_PATHS.any { path.startsWith(it) }

        if (isCdn && isFlagPath && method == "GET") {
            injectFlags(output)
            Log.d(TAG, "[${if (ssl) "HTTPS" else "HTTP"}] intercepted $host$path")
        } else {
            passThrough(method, path, host, headers, output, ssl)
        }
    }

    private fun readHeaders(reader: BufferedReader): Map<String, String> {
        val h = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) h[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        return h
    }

    private fun injectFlags(output: OutputStream) {
        val body = flagsJson.toByteArray(Charsets.UTF_8)
        output.write(
            ("HTTP/1.1 200 OK\r\n" +
             "Content-Type: application/json; charset=utf-8\r\n" +
             "Content-Length: ${body.size}\r\n" +
             "Cache-Control: no-cache\r\n" +
             "Connection: close\r\n\r\n").toByteArray()
        )
        output.write(body)
        output.flush()
    }

    private fun passThrough(
        method: String, path: String, host: String,
        reqHeaders: Map<String, String>, clientOut: OutputStream, ssl: Boolean
    ) {
        try {
            val port    = if (ssl) 443 else 80
            val rawSock = Socket(host, port)

            // CRITICAL: protect so upstream exits through real NIC, not back into TUN (infinite loop)
            vpnService?.protect(rawSock)
                ?: Log.w(TAG, "vpnService null — upstream may loop into TUN")

            val upstream = if (ssl)
                CertManager.getTrustAllFactory().createSocket(rawSock, host, port, true) as SSLSocket
            else rawSock

            upstream.soTimeout = 8_000
            val upOut = upstream.getOutputStream()
            val sb    = StringBuilder("$method $path HTTP/1.1\r\n")
            reqHeaders.forEach { (k, v) -> if (!AntiBan.isBlockedHeader(k)) sb.append("$k: $v\r\n") }
            sb.append("Connection: close\r\n\r\n")
            upOut.write(sb.toString().toByteArray())
            upOut.flush()

            upstream.getInputStream().copyTo(clientOut)
            clientOut.flush()
            upstream.close()
        } catch (e: Exception) {
            runCatching {
                clientOut.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                clientOut.flush()
            }
        }
    }
}
