package com.example.flagmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import java.util.zip.CRC32

class FlagVpnService : VpnService() {

    companion object {
        private const val TAG = "FlagVpnService"
        private const val CHANNEL_ID = "vpn_bypass"
        private const val NOTIF_ID = 9001
        private const val EXTRA_FLAGS = "extra_flags_json"
        private const val ACTION_UPDATE = "com.example.flagmanager.ACTION_UPDATE_FLAGS"
        private const val ACTION_STOP = "com.example.flagmanager.ACTION_STOP"
        const val PROXY_PORT = 8899

        @Volatile var activeFlagsMap: Map<String, String> = emptyMap()

        fun buildStartIntent(context: Context, flagsJson: String): Intent? = prepare(context)

        fun buildDirectIntent(context: Context, flagsJson: String): Intent =
            Intent(context, FlagVpnService::class.java).putExtra(EXTRA_FLAGS, flagsJson)

        fun updateFlags(context: Context, flagsJson: String) {
            context.startService(
                Intent(context, FlagVpnService::class.java)
                    .setAction(ACTION_UPDATE)
                    .putExtra(EXTRA_FLAGS, flagsJson)
            )
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private var vpnOutput: FileOutputStream? = null
    
    // Track TCP sessions to map sequence numbers
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

    data class TcpSession(var clientSeq: Long, var serverSeq: Long)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val json = intent.getStringExtra(EXTRA_FLAGS) ?: "{}"
                updateFlagsMap(json)
                Log.d(TAG, "flags updated live")
                return START_STICKY
            }
        }

        val flagsJson = intent?.getStringExtra(EXTRA_FLAGS) ?: "{}"
        updateFlagsMap(flagsJson)
        postNotification()

        if (vpnInterface == null) {
            setupVpnTunnel()
        }

        return START_STICKY
    }

    private fun updateFlagsMap(json: String) {
        activeFlagsMap = try {
            val root = JSONObject(json)
            val map = mutableMapOf<String, String>()
            root.keys().forEach { key ->
                map[key] = root.get(key).toString()
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun setupVpnTunnel() {
        try {
            val builder = Builder()
                .setSession("TacoStrap Ghost-Route")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                // Limit to Roblox and our own app to prevent blackholing other apps
                .addAllowedApplication("com.roblox.client")
                .addAllowedApplication(packageName)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                return
            }

            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            tunnelThread = thread(name = "VpnTunnel", isDaemon = true) {
                val buffer = ByteArray(32767)
                while (vpnInterface != null) {
                    try {
                        val length = vpnInput.read(buffer)
                        if (length > 0) {
                            processPacket(buffer, length)
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "VPN setup error: ${e.message}")
        }
    }

    private fun processPacket(buffer: ByteArray, length: Int) {
        if (length < 20) return
        val version = (buffer[0].toInt() and 0xF0) shr 4
        if (version != 4) return // IPv4 only

        val ihl = (buffer[0].toInt() and 0x0F) * 4
        val protocol = buffer[9].toInt() and 0xFF
        val destIp = "${buffer[16].toInt() and 0xFF}.${buffer[17].toInt() and 0xFF}.${buffer[18].toInt() and 0xFF}.${buffer[19].toInt() and 0xFF}"
        val srcIp = "${buffer[12].toInt() and 0xFF}.${buffer[13].toInt() and 0xFF}.${buffer[14].toInt() and 0xFF}.${buffer[15].toInt() and 0xFF}"

        if (protocol == 6) { // TCP
            if (length < ihl + 20) return
            val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
            val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)

            // Only intercept Roblox CDN traffic (Port 80)
            if (destPort == 80) {
                val tcpHeaderLength = ((buffer[ihl + 12].toInt() and 0xF0) shr 4) * 4
                val payloadOffset = ihl + tcpHeaderLength
                val payloadLength = length - payloadOffset
                
                if (payloadLength > 0) {
                    val payload = String(buffer, payloadOffset, payloadLength, Charsets.UTF_8)
                    if (payload.startsWith("GET ")) {
                        handleHttpIntercept(payload, srcIp, srcPort, destIp, destPort)
                    }
                }
            } else {
                // Forward non-intercepted TCP back to TUN (or drop)
            }
        } else if (protocol == 17) { // UDP (RakNet Desyncs)
            // Handle UDP Desync here
        }
    }

    private fun handleHttpIntercept(requestText: String, srcIp: String, srcPort: Int, destIp: String, destPort: Int) {
        thread {
            try {
                val lines = requestText.split("\r\n")
                val requestLineParts = lines[0].split(" ")
                if (requestLineParts.size < 2) return@thread
                val path = requestLineParts[1]

                val socket = Socket()
                socket.connect(InetSocketAddress(InetAddress.getByName(destIp), destPort), 5000)
                
                val out = socket.getOutputStream()
                val reqBuilder = StringBuilder()
                reqBuilder.append("GET $path HTTP/1.1\r\n")
                reqBuilder.append("Host: $destIp\r\n")
                lines.drop(1).takeWhile { it.isNotEmpty() }.forEach { reqBuilder.append("$it\r\n") }
                reqBuilder.append("\r\n")
                out.write(reqBuilder.toString().toByteArray())
                out.flush()

                val responseText = socket.getInputStream().bufferedReader().readText()
                socket.close()

                var finalBody = responseText
                if (path.contains("settings", true) || path.contains("client-settings", true)) {
                    val headerEnd = responseText.indexOf("\r\n\r\n")
                    if (headerEnd != -1) {
                        val headers = responseText.substring(0, headerEnd)
                        val bodyStr = responseText.substring(headerEnd + 4)
                        val root = JSONObject(bodyStr)
                        val settings = root.optJSONObject("applicationSettings") ?: JSONObject()
                        activeFlagsMap.forEach { (k, v) -> settings.put(k, v) }
                        root.put("applicationSettings", settings)
                        
                        // Rebuild HTTP response
                        finalBody = "$headers\r\n\r\n${root.toString()}"
                    }
                }

                // Write back to TUN
                writeTcpResponse(finalBody.toByteArray(), srcIp, srcPort, destIp, destPort)
                Log.i(TAG, "Intercepted and replied to $destIp$path")

            } catch (e: Exception) {
                Log.e(TAG, "HTTP intercept failed: ${e.message}")
            }
        }
    }

    private fun writeTcpResponse(responseBytes: ByteArray, destIp: String, destPort: Int, srcIp: String, srcPort: Int) {
        val vpnOut = vpnOutput ?: return
        val packet = ByteBuffer.allocate(40 + responseBytes.size)
        
        // Build IPv4 Header
        packet.put(0x45.toByte()) // Version 4, IHL 5
        packet.put(0x00.toByte()) // DSCP
        packet.putShort((40 + responseBytes.size).toShort()) // Total Length
        packet.putShort(0) // Identification
        packet.putShort(0x4000.toShort()) // Flags (Don't Fragment)
        packet.put(64.toByte()) // TTL
        packet.put(6.toByte()) // Protocol TCP
        packet.putShort(0) // Checksum (computed later)
        
        // Source IP (Dest of original request)
        packet.put(InetAddress.getByName(srcIp).address)
        // Dest IP (Source of original request)
        packet.put(InetAddress.getByName(destIp).address)

        // Build TCP Header
        packet.putShort(srcPort.toShort()) // Source Port
        packet.putShort(destPort.toShort()) // Dest Port
        packet.putInt(0) // Sequence (simplified for handshake)
        packet.putInt(0) // Ack
        packet.put(0x50.toByte()) // Data Offset (5)
        packet.put(0x18.toByte()) // Flags (PSH, ACK)
        packet.putShort(65535.toShort()) // Window
        packet.putShort(0) // Checksum
        packet.putShort(0) // Urgent Pointer
        
        // Payload
        packet.put(responseBytes)

        // Compute Checksums (simplified - real TCP checksum needs pseudo-header)
        val fullPacket = packet.array()
        // Fix IP Checksum
        var sum = 0
        for (i in 0 until 20 step 2) {
            sum += ((fullPacket[i].toInt() and 0xFF) shl 8) or (fullPacket[i+1].toInt() and 0xFF)
        }
        while (sum shr 16 > 0) { sum = (sum and 0xFFFF) + (sum shr 16) }
        val ipChecksum = sum.inv() and 0xFFFF
        fullPacket[10] = (ipChecksum shr 8).toByte()
        fullPacket[11] = ipChecksum.toByte()

        // Write to TUN
        vpnOut.write(fullPacket)
        vpnOut.flush()
    }

    private fun postNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN Bypass", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, FlagVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TacoStrap active")
            .setContentText("Ghost-Route intercepting traffic")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        tunnelThread?.interrupt()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "service destroyed")
        super.onDestroy()
    }
}
