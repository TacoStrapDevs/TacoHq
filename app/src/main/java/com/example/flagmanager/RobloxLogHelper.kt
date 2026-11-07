
package com.example.flagmanager

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class RobloxLogHelper(
    private val onEvent: (RobloxLogEvent) -> Unit,
    private val onStatus: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "TacoStrapLogHelper"
        private const val ROBLOX_PACKAGE = "com.roblox.client"
        private const val CHECK_INTERVAL = 1500L
    }

    private val parser = RobloxSessionParser()
    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var logProcess: Process? = null

    @Volatile
    private var workerThread: Thread? = null

    @Synchronized
    fun start(): Boolean {
        if (running.get()) return true

        if (!isShizukuReady()) {
            notifyStatus("Shizuku unavailable or permission missing")
            return false
        }

        parser.reset()
        running.set(true)

        workerThread = Thread(
            { monitorRoblox() },
            "TacoStrap-Roblox-Monitor"
        ).apply {
            isDaemon = true
            start()
        }

        notifyStatus("Roblox monitoring started")
        return true
    }

    @Synchronized
    fun stop() {
        running.set(false)
        closeLogProcess()

        workerThread?.interrupt()
        workerThread = null

        parser.reset()
        notifyStatus("Roblox monitoring stopped")
    }

    fun isRunning(): Boolean = running.get()

    private fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku check failed", e)
            false
        }
    }

    /**
     * Uses the same Shizuku process approach already present
     * in TacoStrap's MainActivity.kt.
     */
    private fun executeShizuku(command: String): Process {
        check(isShizukuReady()) {
            "Shizuku is not authorized"
        }

        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )

        method.isAccessible = true

        return method.invoke(
            null,
            arrayOf("sh", "-c", command),
            null,
            null
        ) as Process
    }

    private fun findRobloxPid(): String? {
        var process: Process? = null

        return try {
            process = executeShizuku(
                "pidof $ROBLOX_PACKAGE 2>/dev/null"
            )

            val output = process.inputStream
                .bufferedReader()
                .use { it.readText() }
                .trim()

            process.waitFor()

            output.split(Regex("\\s+"))
                .firstOrNull {
                    it.matches(Regex("\\d+"))
                }

        } catch (e: Exception) {
            Log.e(TAG, "Roblox PID lookup failed", e)
            null
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    private fun monitorRoblox() {
        try {
            while (running.get()) {

                if (!isShizukuReady()) {
                    notifyStatus("Shizuku authorization lost")
                    break
                }

                val pid = findRobloxPid()

                if (pid == null) {
                    notifyStatus("Waiting for Roblox...")
                    sleepSafely(CHECK_INTERVAL)
                    continue
                }

                notifyStatus("Roblox detected. Monitoring logs...")

                try {
                    readRobloxLogs(pid)
                } catch (e: Exception) {
                    if (running.get()) {
                        Log.e(TAG, "Log monitoring error", e)
                    }
                }

                if (running.get()) {
                    notifyStatus("Roblox log stream ended. Reconnecting...")
                    sleepSafely(1000L)
                }
            }
        } finally {
            closeLogProcess()
            running.set(false)
            parser.reset()
            notifyStatus("Roblox monitoring ended")
        }
    }

    private fun readRobloxLogs(pid: String) {
        val process = executeShizuku(
            "logcat -v raw --pid=$pid -s Roblox:I 2>&1"
        )

        logProcess = process

        try {
            BufferedReader(
                InputStreamReader(process.inputStream)
            ).use { reader ->

                var lastPidCheck = 0L

                while (running.get()) {

                    if (!isShizukuReady()) {
                        notifyStatus("Shizuku authorization lost")
                        break
                    }

                    val now = System.currentTimeMillis()

                    if (now - lastPidCheck >= CHECK_INTERVAL) {
                        lastPidCheck = now

                        if (findRobloxPid() != pid) {
                            notifyStatus("Roblox process ended")
                            break
                        }
                    }

                    if (reader.ready()) {
                        val line = reader.readLine() ?: break

                        if (line.isNotBlank()) {
                            processLogLine(line)
                        }
                    } else {
                        sleepSafely(150L)
                    }
                }
            }
        } finally {
            try {
                process.destroyForcibly()
            } catch (e: Exception) {
                Log.w(TAG, "Could not terminate logcat", e)
            }

            if (logProcess === process) {
                logProcess = null
            }
        }
    }

    private fun processLogLine(line: String) {
        try {
            val event = parser.parse(line) ?: return

            mainHandler.post {
                if (running.get()) {
                    try {
                        onEvent(event)
                    } catch (e: Exception) {
                        Log.e(TAG, "Session callback failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Log parsing failed", e)
        }
    }

    private fun closeLogProcess() {
        val process = logProcess
        logProcess = null

        try {
            process?.destroyForcibly()
        } catch (e: Exception) {
            Log.w(TAG, "Process cleanup failed", e)
        }
    }

    private fun sleepSafely(duration: Long) {
        try {
            Thread.sleep(duration)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun notifyStatus(message: String) {
        mainHandler.post {
            try {
                onStatus(message)
            } catch (e: Exception) {
                Log.e(TAG, "Status callback failed", e)
            }
        }
    }
}
