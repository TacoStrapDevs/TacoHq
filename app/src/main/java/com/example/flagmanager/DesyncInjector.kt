package com.example.flagmanager

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

object DesyncInjector {

    private const val TAG = "DesyncInjector"
    private const val KEY = "WorldStepMax"
    private const val ON = "-99999999999999"
    private const val OFF = "-1"

    /** Call only when the toggle is ON. */
    fun ensureInMap(flags: MutableMap<String, String>) {
        flags[KEY] = ON
        flags["DFIntPhysicsSenderMaxBandwidthBps"] = "0"
        flags["DFIntPhysicsSenderMaxBandwidthBpsScaling"] = "0"
    }

    /** Soft reset — call when the toggle is turned OFF. */
    fun removeFromMap(flags: MutableMap<String, String>) {
        flags[KEY] = OFF
        flags.remove("DFIntPhysicsSenderMaxBandwidthBps")
        flags.remove("DFIntPhysicsSenderMaxBandwidthBpsScaling")
    }

    fun inject(versionDir: File, enable: Boolean): Boolean {
        if (!versionDir.isDirectory) return false
        val settings = File(versionDir, "ClientSettings").apply { mkdirs() }
        val file = File(settings, "ClientAppSettings.json")
        return try {
            val root = if (file.exists()) JSONObject(file.readText(StandardCharsets.UTF_8))
                       else JSONObject()
            if (enable) {
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
