package com.example.flagmanager

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

object DesyncInjector {

    private const val TAG = "DesyncInjector"
    private const val KEY = "WorldStepMax"
    // fixed: Long values not strings — Roblox int flags reject string values
    private const val ON  = -99999999999999L
    private const val OFF = -1L

    fun ensureInMap(flags: MutableMap<String, String>) {
        flags[KEY]                                    = ON.toString()
        flags["DFIntPhysicsSenderMaxBandwidthBps"]    = "0"
        flags["DFIntPhysicsSenderMaxBandwidthBpsScaling"] = "0"
    }

    fun removeFromMap(flags: MutableMap<String, String>) {
        flags[KEY] = OFF.toString()
        flags.remove("DFIntPhysicsSenderMaxBandwidthBps")
        flags.remove("DFIntPhysicsSenderMaxBandwidthBpsScaling")
    }

    fun inject(versionDir: File, enable: Boolean): Boolean {
        if (!versionDir.isDirectory) return false
        val settings = File(versionDir, "ClientSettings").apply { mkdirs() }
        val file     = File(settings, "ClientAppSettings.json")
        return try {
            val root = if (file.exists())
                JSONObject(file.readText(StandardCharsets.UTF_8))
            else
                JSONObject()

            if (enable) {
                // put as Long so JSON serializes as a number, not a string
                root.put(KEY, ON)
                root.put("DFIntPhysicsSenderMaxBandwidthBps", 0)
                root.put("DFIntPhysicsSenderMaxBandwidthBpsScaling", 0)
            } else {
                root.put(KEY, OFF)
            }

            FileOutputStream(file).use {
                it.write(root.toString(2).toByteArray(StandardCharsets.UTF_8))
                it.fd.sync()
            }
            file.setLastModified(System.currentTimeMillis())
            Log.i(TAG, "desync ${if (enable) "ON" else "OFF"} → $KEY")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "inject failed", t)
            false
        }
    }
}
