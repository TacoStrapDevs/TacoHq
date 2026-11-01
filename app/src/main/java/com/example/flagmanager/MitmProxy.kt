package com.example.flagmanager

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

object MitmProxy {

    private const val TAG = "MitmProxy"
    const val PROXY_PORT_HTTP = 8899
    private const val CONNECT_TIMEOUT = 10_000
    private const val SO_TIMEOUT = 20_000

    private val CDN_HOSTS = setOf(
        "clientsettingscdn.roblox.com",
        "clientsettings.roblox.com"
    )

    private val FLAG_PATH_HINTS = listOf(
        "/v2/settings/application",
        "/v1/settings/application",
        "/v2/client-settings",
        "/v1/client-settings",
        "/settings/application",
        "/client-settings"
    )

    @Volatile var vpnService: FlagVpnService? = null

    @Volatile private var flagsJson = "{}"
    @Volatile private var running = false
    @Volatile private var appContext: Context? = null

    private var serverSocket: ServerSocket? = null

    fun isRunning(): Boolean = running
    fun currentFlags(): String = flagsJson

    fun start(initialFlags: String, context: Context) {
        if (running) {
            updateFlags(initialFlags)
            return
        }
        appContext = context.applicationContext
        flagsJson = forceAllowlist(initialFlags)
        running = true

        thread(name = "Proxy-Accept", isDaemon = true) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("127.0.0.1", PROXY_PORT_HTTP))
                serverSocket = ss
                Log.d(TAG, "proxy listening on :$PROXY_PORT_HTTP")
                while (running) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: break
                    thread(name = "Proxy-Client", isDaemon = true) { handle(client) }
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "accept: ${e.message}")
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        vpnService = null
        Log.d(TAG, "proxy stopped")
    }

    fun updateFlags(json: String) {
        flagsJson = forceAllowlist(json)
    }

    private fun forceAllowlist(json: String): String {
        return try {
            val root = JSONObject(json.ifBlank { "{}" })
            root.put("FFlagAllowList", JSONObject())
            root.put("FFlagAllowListEnabled", false)
            root.put("FFlagDisableDynamicAllowlist", true)
            root.toString()
        } catch (_: Throwable) {
            json
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = SO_TIMEOUT
        try {
            val input = BufferedInputStream(client.getInputStream())
            val firstLine = readLine(input) ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val target = parts[1]
            val headers = readHeaders(input)

            if (method == "CONNECT") {
                handleConnect(client, target)
                return
            }

            val host = headers["host"]?.lowercase() ?: ""
            if (host.isBlank()) {
                writeError(client.getOutputStream(), 400, "Bad Request")
                return
            }

            if (isFlagRequest(host, target, method)) {
                injectFlags(client.getOutputStream())
                runCatching { client.close() }
                return
            }

            httpPassthrough(client, method, target, host, headers)
        } catch (e: Exception) {
            Log.w(TAG, "handle: ${e.message}")
            runCatching { client.close() }
        }
    }

    private fun handleConnect(client: Socket, target: String) {
        val parts = target.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 443

        runCatching {
            client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            client.getOutputStream().flush()
        }

        if (CDN_HOSTS.any { host.contains(it, ignoreCase = true) }) {
            mitmTunnel(client, host)
        } else {
            rawTunnel(client, host, port)
        }
    }

    private fun mitmTunnel(client: Socket, host: String) {
        val serverSsl = try {
            val sslCtx = buildServerContext(host)
            if (sslCtx == null) {
                Log.w(TAG, "no CA context for $host")
                client.close()
                return
            }
            (sslCtx.socketFactory.createSocket(client, host, 443, true) as SSLSocket).also {
                it.useClientMode = false
                it.startHandshake()
            }
        } catch (e: Exception) {
            Log.w(TAG, "TLS handshake failed for $host: ${e.message}")
            runCatching { client.close() }
            return
        }

        try {
            val input = BufferedInputStream(serverSsl.inputStream)
            val firstLine = readLine(input) ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val target = parts[1]
            val headers = readHeaders(input)

            if (isFlagRequest(host, target, method)) {
                injectFlags(serverSsl.outputStream)
            } else {
                forwardSslRequest(serverSsl, host, method, target, headers)
            }
        } catch (e: Exception) {
            Log.w(TAG, "MITM inner: ${e.message}")
        } finally {
            runCatching { serverSsl.close() }
            runCatching { client.close() }
        }
    }

    private fun rawTunnel(client: Socket, host: String, port: Int) {
        val upstream = openUpstream(host, port) ?: run {
            runCatching { client.close() }
            return
        }
        thread(isDaemon = true) {
            runCatching { client.getInputStream().copyTo(upstream.getOutputStream()) }
            runCatching { upstream.close() }
        }
        runCatching { upstream.getInputStream().copyTo(client.getOutputStream()) }
        runCatching { upstream.close() }
        runCatching { client.close() }
    }

    private fun forwardSslRequest(
        ssl: SSLSocket,
        host: String,
        method: String,
        path: String,
        headers: Map<String, String>
    ) {
        val upstream = openUpstream(host, 443) ?: return
        try {
            val trust = CertManager.getTrustAllFactory()
            val up = trust.createSocket(upstream, host, 443, true) as SSLSocket
            up.startHandshake()
            val sb = StringBuilder("$method $path HTTP/1.1\r\n")
            headers.forEach { (k, v) -> if (!AntiBan.isBlockedHeader(k)) sb.append("$k: $v\r\n") }
            sb.append("Connection: close\r\n\r\n")
            up.outputStream.write(sb.toString().toByteArray())
            up.outputStream.flush()
            up.inputStream.copyTo(ssl.outputStream)
            ssl.outputStream.flush()
            runCatching { up.close() }
        } catch (e: Exception) {
            Log.w(TAG, "fwd ssl: ${e.message}")
            runCatching { upstream.close() }
        }
    }

    private fun httpPassthrough(
        client: Socket,
        method: String,
        path: String,
        host: String,
        headers: Map<String, String>
    ) {
        val upstream = openUpstream(host, 80) ?: run {
            runCatching { writeError(client.getOutputStream(), 502, "Bad Gateway") }
            runCatching { client.close() }
            return
        }
        try {
            val sb = StringBuilder("$method $path HTTP/1.1\r\n")
            headers.forEach { (k, v) -> if (!AntiBan.isBlockedHeader(k)) sb.append("$k: $v\r\n") }
            sb.append("Connection: close\r\n\r\n")
            upstream.getOutputStream().write(sb.toString().toByteArray())
            upstream.getOutputStream().flush()
            upstream.getInputStream().copyTo(client.getOutputStream())
        } catch (e: Exception) {
            Log.w(TAG, "httpPassthrough: ${e.message}")
        } finally {
            runCatching { upstream.close() }
            runCatching { client.close() }
        }
    }

    private fun buildServerContext(host: String): SSLContext? {
        return try {
            val leaf = CertManager.generateLeafFor(host) ?: return null
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry("leaf", leaf.keyPair.private, CharArray(0), arrayOf(leaf.cert))
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, CharArray(0))
            SSLContext.getInstance("TLS").apply {
                init(kmf.keyManagers, null, SecureRandom())
            }
        } catch (e: Exception) {
            Log.e(TAG, "buildServerContext($host): ${e.message}")
            null
        }
    }

    private fun isFlagRequest(host: String, path: String, method: String): Boolean {
        if (method != "GET") return false
        if (CDN_HOSTS.none { host.contains(it, ignoreCase = true) }) return false
        return FLAG_PATH_HINTS.any { path.startsWith(it) } ||
            path.contains("/settings", ignoreCase = true) ||
            path.contains("client-settings", ignoreCase = true)
    }

    private fun openUpstream(host: String, port: Int): Socket? {
        return try {
            val s = Socket()
            vpnService?.protect(s)
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT)
            s.soTimeout = SO_TIMEOUT
            s
        } catch (e: Exception) {
            Log.w(TAG, "upstream $host:$port: ${e.message}")
            null
        }
    }

    private fun injectFlags(out: OutputStream) {
        val body = flagsJson.toByteArray(Charsets.UTF_8)
        out.write(
            ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                "Pragma: no-cache\r\n" +
                "Expires: 0\r\n" +
                "Connection: close\r\n\r\n").toByteArray()
        )
        out.write(body)
        out.flush()
        Log.d(TAG, "injected ${body.size} bytes")
    }

    private fun writeError(out: OutputStream, code: Int, msg: String) {
        runCatching {
            out.write("HTTP/1.1 $code $msg\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                if (sb.isNotEmpty() && sb.last() == '\r') sb.deleteCharAt(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
        }
    }

    private fun readHeaders(input: InputStream): Map<String, String> {
        val h = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) h[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        return h
    }
}
