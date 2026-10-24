package com.example.flagmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import java.io.File

private const val PKG = "com.roblox.client"
// UNVERIFIED GUESS. Change it in the app if you find the right path for your Roblox version.
private const val DEFAULT_PATH = "/data/data/$PKG/files/exe/ClientSettings/ClientAppSettings.json"

fun parseFlags(text: String): Map<String, String> {
    val o = JSONObject(text)
    return o.keys().asSequence().associateWith { o.get(it).toString() }
}

fun toJson(m: Map<String, String>): String = JSONObject(m).toString(2)

fun su(cmd: String): String = try {
    val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    if (p.waitFor() == 0) "Applied. Force-close and reopen Roblox." else "Failed: $out"
} catch (e: Exception) {
    "No root access: ${e.message}"
}

fun applyFlags(ctx: Context, json: String, path: String): String {
    val tmp = File(ctx.cacheDir, "ClientAppSettings.json").apply { writeText(json) }
    val dir = path.substringBeforeLast('/')
    val chown = if (path.startsWith("/data/"))
        " && chown \$(stat -c %u:%g /data/data/$PKG) '$path'" else ""
    return su("mkdir -p '$dir' && cp '${tmp.absolutePath}' '$path' && chmod 666 '$path'$chown")
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
    var path by remember { mutableStateOf(prefs.getString("path", DEFAULT_PATH)!!) }
    var status by remember { mutableStateOf("") }
    var k by remember { mutableStateOf("") }
    var v by remember { mutableStateOf("") }
    var pn by remember { mutableStateOf("") }

    fun save() {
        prefs.edit()
            .putString("flags", toJson(flags))
            .putString("presets", toJson(presets))
            .putString("path", path)
            .apply()
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Roblox FastFlag Manager", style = MaterialTheme.typography.titleLarge) }
        item {
            OutlinedTextField(
                path, { path = it; save() },
                label = { Text("ClientAppSettings.json path") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    scope.launch(Dispatchers.IO) { status = applyFlags(ctx, toJson(flags), path) }
                }) { Text("Apply") }
                OutlinedButton({
                    scope.launch(Dispatchers.IO) { status = su("rm -f '$path'").let { if (it.startsWith("Applied")) "Removed flag file." else it } }
                }) { Text("Reset") }
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
            OutlinedTextField(k, { k = it }, label = { Text("Name (e.g. FIntDebugForceMSAASamples)") },
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
