package com.example.flagmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.Base64

private const val PKG = "com.roblox.client"
// Based on FastStrap's README (writes to /data/local/tmp). Unverified: edit in the app if needed.
private const val DEFAULT_PATH = "/data/local/tmp/ClientAppSettings.json"
private const val FPS_PROP = "debug.graphics.game_default_frame_rate.disabled"

fun parseFlags(text: String): Map<String, String> {
    val o = JSONObject(text)
    return o.keys().asSequence().associateWith { o.get(it).toString() }
}

fun toJson(m: Map<String, String>): String = JSONObject(m).toString(2)

fun shizukuStatus(): String = try {
    when {
        !Shizuku.pingBinder() -> "Shizuku is not running. Start it first."
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
            "Shizuku is running. Tap Grant."
        else -> "Shizuku ready"
    }
} catch (e: Throwable) {
    "Shizuku unavailable: ${e.message}"
}

// Shizuku.newProcess is private in API 13.x, so call it by reflection.
fun sh(cmd: String): Pair<Boolean, String> = try {
    val m = Shizuku::class.java.getDeclaredMethod(
        "newProcess",
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java
    )
    m.isAccessible = true
    val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
    val out = p.inputStream.bufferedReader().readText()
    val err = p.errorStream.bufferedReader().readText()
    Pair(p.waitFor() == 0, (out + err).trim())
} catch (e: Throwable) {
    Pair(false, ((e.cause ?: e).message ?: e.toString()))
}

fun applyFlags(json: String, path: String, restart: Boolean, fps: Boolean): String {
    val b64 = Base64.getEncoder().encodeToString(json.toByteArray())
    val dir = path.substringBeforeLast('/')
    val cmd = buildString {
        append("mkdir -p '$dir' && echo '$b64' | base64 -d > '$path' && chmod 644 '$path'")
        if (fps) append(" && setprop $FPS_PROP true")
        if (restart) append(" && am force-stop $PKG && sleep 1 && monkey -p $PKG -c android.intent.category.LAUNCHER 1")
    }
    val (ok, msg) = sh(cmd)
    return if (ok) "Applied to $path" else "Failed: $msg"
}

fun resetFlags(path: String): String {
    val (ok, msg) = sh("rm -f '$path'; setprop $FPS_PROP false; am force-stop $PKG")
    return if (ok) "Removed flag file. Reopen Roblox." else "Failed: $msg"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { App() } } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("fm", 0) }
    val scope = rememberCoroutineScope()
    val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val flags = remember {
        mutableStateMapOf<String, String>().apply {
            runCatching { putAll(parseFlags(prefs.getString("flags", "{}")!!)) }
        }
    }
    val presets = remember {
        mutableStateMapOf<String, String>().apply {
            runCatching { putAll(parseFlags(prefs.getString("presets", "{}")!!)) }
        }
    }
    var path by remember { mutableStateOf(prefs.getString("path2", DEFAULT_PATH)!!) }
    var status by remember { mutableStateOf("") }
    var shStatus by remember { mutableStateOf(shizukuStatus()) }
    var restart by remember { mutableStateOf(true) }
    var fps by remember { mutableStateOf(false) }
    var k by remember { mutableStateOf("") }
    var v by remember { mutableStateOf("") }
    var pn by remember { mutableStateOf("") }

    fun save() {
        prefs.edit()
            .putString("flags", toJson(flags))
            .putString("presets", toJson(presets))
            .putString("path2", path)
            .apply()
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Roblox FastFlag Manager", style = MaterialTheme.typography.titleLarge) }
        item { Text(shStatus, color = MaterialTheme.colorScheme.primary) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    try { Shizuku.requestPermission(1001) } catch (e: Throwable) { status = "Start Shizuku first." }
                    shStatus = shizukuStatus()
                }) { Text("Grant Shizuku") }
                OutlinedButton({ shStatus = shizukuStatus() }) { Text("Refresh") }
            }
        }
        item {
            OutlinedTextField(
                path, { path = it; save() },
                label = { Text("ClientAppSettings.json path") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(restart, { restart = it })
                Text("Restart Roblox after Apply")
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(fps, { fps = it })
                Text("Disable game frame-rate cap (system prop)")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    scope.launch(Dispatchers.IO) { status = applyFlags(toJson(flags), path, restart, fps) }
                }) { Text("Apply") }
                OutlinedButton({
                    scope.launch(Dispatchers.IO) { status = resetFlags(path) }
                }) { Text("Reset") }
                OutlinedButton({
                    scope.launch(Dispatchers.IO) {
                        val (_, msg) = sh("ls -l '$path'; cat '$path'")
                        status = msg.ifEmpty { "Nothing returned." }
                    }
                }) { Text("Verify") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    status = runCatching { flags.putAll(parseFlags(text)); save(); "Imported." }
                        .getOrElse { "Clipboard isn't valid JSON." }
                }) { Text("Import clipboard") }
                OutlinedButton({
                    clip.setPrimaryClip(ClipData.newPlainText("flags", toJson(flags)))
                    status = "Copied JSON."
                }) { Text("Copy JSON") }
            }
        }
        item { if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary) }

        item { HorizontalDivider() }
        item { Text("Add flag", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ flags["DFIntTaskSchedulerTargetFps"] = "144"; save() }) { Text("FPS 144") }
                OutlinedButton({ flags["FFlagDebugSkyGray"] = "True"; save() }) { Text("Gray sky") }
            }
        }
        item {
            OutlinedTextField(k, { k = it }, label = { Text("Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(v, { v = it }, label = { Text("Value") }, singleLine = true,
                    modifier = Modifier.weight(1f))
                Button({
                    if (k.isNotBlank()) { flags[k.trim()] = v.trim(); k = ""; v = ""; save() }
                }) { Text("Add") }
            }
        }

        item { HorizontalDivider() }
        item { Text("Presets", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pn, { pn = it }, label = { Text("Preset name") }, singleLine = true,
                    modifier = Modifier.weight(1f))
                Button({
                    if (pn.isNotBlank()) { presets[pn.trim()] = toJson(flags); pn = ""; save() }
                }) { Text("Save") }
            }
        }
        items(presets.keys.sorted()) { name ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f))
                TextButton({
                    runCatching { flags.clear(); flags.putAll(parseFlags(presets[name]!!)); save() }
                }) { Text("Load") }
                TextButton({ presets.remove(name); save() }) { Text("Delete") }
            }
        }

        item { HorizontalDivider() }
        item { Text("Active flags (${flags.size})", style = MaterialTheme.typography.titleMedium) }
        items(flags.keys.sorted()) { name ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(flags[name] ?: "", { flags[name] = it; save() },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                TextButton({ flags.remove(name); save() }) { Text("X") }
            }
        }
    }
}


import androidx.compose.ui.res.painterResource
