package com.example.flagmanager

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

object MitmProxy {

    private const val TAG = "MitmProxy"

    private val CDN_HOSTS = setOf(
        "clientsettingscdn.roblox.com",
        "clientsettings.roblox.com"
    )

    private val FLAG_PATHS = listOf(
        "/v2/settings/application",
        "/v1/settings/application",
        "/v2/client-settings",
        "/v1/client-settings",
        "/settings/application",
        "/client-settings"
    )

    @Volatile private var flagsJson: String = "{}"
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null

    fun start(initialFlags: String, port: Int) {
        if (running) {
            updateFlags(initialFlags)
            return
        }
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
        flagsJson = try {
            val root = JSONObject(json)
            root.put("FFlagAllowList", JSONObject())
            root.put("FFlagAllowListEnabled", false)
            root.toString()
        } catch (_: Throwable) {
            json
        }
    }

    private fun handleConnection(client: Socket) {
        client.soTimeout = 8_000
        try {
            val input = client.getInputStream().bufferedReader()
            val output = client.getOutputStream()

            val requestLine = input.readLine() ?: return
            val headers = readHeaders(input)

            val host = headers["host"]?.lowercase() ?: ""
            val parts = requestLine.split(" ")
            val method = parts.getOrElse(0) { "GET" }
            val path = parts.getOrElse(1) { "/" }

            val isCdn = CDN_HOSTS.any { host.contains(it) }
            val isFlagPath = FLAG_PATHS.any { path.startsWith(it) } ||
                    path.contains("settings", ignoreCase = true) ||
                    path.contains("client-settings", ignoreCase = true)

            if (isCdn && isFlagPath && method == "GET") {
                injectFlags(output)
                Log.d(TAG, "intercepted $host$path → unrestricted flags injected")
            } else if (AntiBan.isTelemetryPath(path)) {
                output.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
            } else {
                passThrough(method, path, host, headers, output)
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
                headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
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
        method: String, path: String, host: String,
        reqHeaders: Map<String, String>,
        clientOut: OutputStream
    ) {
        try {
            val upstream = Socket(host, 80)
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
                clientOut.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                clientOut.flush()
            }
        }
    }
}
