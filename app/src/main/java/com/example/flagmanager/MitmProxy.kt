package com.example.flagmanager

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * MitmProxy
 *
 * Local TCP diagnostic listener.
 * This is NOT a VPN tunnel and does not intercept Roblox HTTPS traffic.
 */
object MitmProxy {

    private const val TAG = "MitmProxy"

    @Volatile
    private var running = false

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var activePort: Int = -1

    @Synchronized
    fun start(port: Int) {
        if (running) {
            Log.d(TAG, "Already running on port $activePort")
            return
        }

        try {
            val socket = ServerSocket(port)
            serverSocket = socket
            activePort = port
            running = true

            Log.d(TAG, "Diagnostic listener started on port $port")

            thread(
                name = "MitmProxy-Accept",
                isDaemon = true
            ) {
                acceptConnections(socket)
            }

        } catch (e: Exception) {
            running = false
            serverSocket = null
            activePort = -1

            Log.e(TAG, "Failed to start listener", e)
        }
    }

    private fun acceptConnections(server: ServerSocket) {
        try {
            while (running && !server.isClosed) {
                val client = server.accept()

                thread(
                    name = "MitmProxy-Client",
                    isDaemon = true
                ) {
                    handleConnection(client)
                }
            }
        } catch (e: Exception) {
            if (running) {
                Log.e(TAG, "Accept loop failed", e)
            }
        } finally {
            synchronized(this) {
                if (serverSocket === server) {
                    running = false
                    serverSocket = null
                    activePort = -1
                }
            }
        }
    }

    private fun handleConnection(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = 5000

                val reader = BufferedReader(
                    InputStreamReader(socket.getInputStream())
                )

                val firstLine = reader.readLine() ?: return

                Log.d(TAG, "Received connection: $firstLine")

                val output: OutputStream = socket.getOutputStream()

                val response = """
                    HTTP/1.1 501 Not Implemented
                    Content-Type: text/plain; charset=utf-8
                    Connection: close

                    This diagnostic listener does not proxy or intercept traffic.
                """.trimIndent().replace("\n", "\r\n")

                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()

            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
            }
        }
    }

    @Synchronized
    fun stop() {
        running = false

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing listener", e)
        }

        serverSocket = null
        activePort = -1

        Log.d(TAG, "Diagnostic listener stopped")
    }

    fun isRunning(): Boolean = running

    fun getActivePort(): Int = activePort
}
