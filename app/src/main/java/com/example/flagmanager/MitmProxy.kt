package com.example.flagmanager

import android.util.Log
import java.io.BufferedReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Local HTTP diagnostic listener. It deliberately does not rewrite Roblox responses,
 * inject FastFlags, intercept HTTPS, or bypass application allowlists.
 */
object MitmProxy {
    private const val TAG = "MitmProxy"
    @Volatile private var running = false
    @Volatile private var serverSocket: ServerSocket? = null

    @Synchronized
    @Throws(Exception::class)
    fun start(port: Int) {
        if (running) return
        val socket = ServerSocket(port) // Bind synchronously so startup errors are reported accurately.
        serverSocket = socket
        running = true
        thread(name = "TacoStrap-HTTP-listener", isDaemon = true) {
            try {
                while (running) {
                    val client = try { socket.accept() } catch (e: Exception) {
                        if (running) Log.e(TAG, "Accept failed", e)
                        break
                    }
                    thread(name = "TacoStrap-HTTP-client", isDaemon = true) { handle(client) }
                }
            } finally {
                running = false
                runCatching { socket.close() }
                if (serverSocket === socket) serverSocket = null
            }
        }
        Log.i(TAG, "Diagnostic listener bound on :$port")
    }

    @Synchronized
    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun isRunning(): Boolean = running && serverSocket?.isBound == true && !serverSocket!!.isClosed

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 5_000
            val reader: BufferedReader = socket.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            // Drain headers; this listener is diagnostic and does not forward or modify traffic.
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val body = "TacoStrap local diagnostic listener is running. No Roblox traffic was intercepted.\n"
                .toByteArray(Charsets.UTF_8)
            val output = socket.getOutputStream()
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
            output.write(body)
            output.flush()
            Log.d(TAG, "Diagnostic request received: $requestLine")
        }
    }
}
