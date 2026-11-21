package com.example.flagmanager

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

/**
 * Single entry point for privileged command execution.
 *
 * Shizuku keeps newProcess private (it returns ShizukuRemoteProcess), so it has
 * to be reached reflectively. The method is resolved by name and arity instead
 * of exact parameter types: Array<String>::class.java is not a reliable match
 * for String[] across Kotlin/AGP combinations. Streams are drained on separate
 * threads because ShizukuRemoteProcess exposes a fresh stream per getter and a
 * full pipe on either side deadlocks the remote child.
 */
object ShizukuShell {

    private const val SHELL = "/system/bin/sh"
    private const val DEFAULT_TIMEOUT_MS = 30_000L
    private const val MAX_CAPTURE_BYTES = 1_048_576

    @Volatile
    private var newProcessMethod: Method? = null

    data class Result(
        val ok: Boolean,
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )

    class ShellException(message: String, cause: Throwable? = null) :
        RuntimeException(message, cause)

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /** Starts a process on the Shizuku (shell/root) side. Caller owns the Process. */
    fun newProcess(command: List<String>): Process {
        if (!Shizuku.pingBinder()) {
            throw ShellException("Shizuku is not running")
        }
        val permission = try {
            Shizuku.checkSelfPermission()
        } catch (t: Throwable) {
            throw ShellException("Shizuku permission check failed", t)
        }
        if (permission != PackageManager.PERMISSION_GRANTED) {
            throw ShellException("Shizuku permission not granted")
        }

        val method = newProcessMethod ?: resolveMethod().also { newProcessMethod = it }

        return try {
            method.invoke(null, *arrayOf<Any?>(
                command.toTypedArray(), null, null
            )) as? Process
                ?: throw ShellException("Shizuku returned a non-process result")
        } catch (e: InvocationTargetException) {
            val cause = e.targetException ?: e
            throw ShellException("Shizuku process launch failed: ${cause.message}", cause)
        } catch (e: ShellException) {
            throw e
        } catch (t: Throwable) {
            throw ShellException("Shizuku process launch failed", t)
        }
    }

    private fun resolveMethod(): Method {
        val method = Shizuku::class.java.declaredMethods.firstOrNull {
            it.name == "newProcess" && it.parameterTypes.size == 3
        } ?: throw ShellException(
            "Shizuku.newProcess is unavailable in this API version; " +
                "the app needs Shizuku API 13.x or a UserService backend"
        )
        method.isAccessible = true
        return method
    }

    /** Runs a script through the remote shell, draining both pipes concurrently. */
    fun runShell(script: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result =
        run(listOf(SHELL, "-c", script), timeoutMs)

    /** Runs the same script through su when the user chose the root path. */
    fun runSu(script: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result =
        run(listOf("su", "-c", script), timeoutMs)

    private fun run(command: List<String>, timeoutMs: Long): Result {
        val process = when {
            command.firstOrNull() == "su" -> try {
                Runtime.getRuntime().exec(command.toTypedArray())
            } catch (t: Throwable) {
                return Result(false, "", "su unavailable: ${t.message}", -1)
            }
            else -> newProcess(command)
        }

        val out = StringBuilder()
        val err = StringBuilder()
        val outThread = drain(process.inputStream, out)
        val errThread = drain(process.errorStream, err)

        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        if (!finished) {
            runCatching { process.destroy() }
            runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        }

        runCatching { outThread.join(2_000) }
        runCatching { errThread.join(2_000) }

        val code = try {
            process.exitValue()
        } catch (_: IllegalThreadStateException) {
            -1
        }

        return Result(
            ok = finished && code == 0,
            stdout = out.toString(),
            stderr = err.toString(),
            exitCode = code
        )
    }

    private fun drain(source: InputStream, sink: StringBuilder): Thread =
        Thread({
            val buffer = ByteArray(8 * 1024)
            var total = 0
            try {
                source.use { stream ->
                    while (total < MAX_CAPTURE_BYTES) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        sink.append(String(buffer, 0, read, Charsets.UTF_8))
                        total += read
                    }
                }
            } catch (_: Throwable) {
                // A killed remote process closes its pipes; partial output is kept.
            }
        }, "shizuku-shell-drain").apply {
            isDaemon = true
            start()
        }
}
