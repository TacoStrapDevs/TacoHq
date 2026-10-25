package com.example.flagmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.Base64

private const val PKG = "com.roblox.client"
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

private val TacoColors = darkColorScheme(
    primary = Color(0xFFFFB300),
    onPrimary = Color(0xFF2B1D00),
    primaryContainer = Color(0xFF4A3300),
    onPrimaryContainer = Color(0xFFFFDEA0),
    secondary = Color(0xFFFF7A1A),
    onSecondary = Color(0xFF2E1300),
    background = Color(0xFF14110F),
    onBackground = Color(0xFFF1E9E2),
    surface = Color(0xFF14110F),
    onSurface = Color(0xFFF1E9E2),
    surfaceVariant = Color(0xFF2A231E),
    onSurfaceVariant = Color(0xFFCFC4BA),
    outline = Color(0xFF6E6259)
)

private val Green = Color(0xFF4CAF50)
private val Red = Color(0xFFEF5350)

private data class Quick(val label: String, val key: String, val value: String)

private val QUICK: Map<String, List<Quick>> = linkedMapOf(
    "Performance" to listOf(
        Quick("FPS 60", "DFIntTaskSchedulerTargetFps", "60"),
        Quick("FPS 144", "DFIntTaskSchedulerTargetFps", "144"),
        Quick("FPS 240", "DFIntTaskSchedulerTargetFps", "240")
    ),
    "Graphics" to listOf(
        Quick("Gray sky", "FFlagDebugSkyGray", "True"),
        Quick("No shadows", "FIntRenderShadowIntensity", "0"),
        Quick("No post-FX", "FFlagDisablePostFx", "True"),
        Quick("MSAA off", "FIntDebugForceMSAASamples", "1")
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = TacoColors) { Surface { App() } } }
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
    var restart by remember { mutableStateOf(prefs.getBoolean("restart", true)) }
    var fps by remember { mutableStateOf(prefs.getBoolean("fps", false)) }
    var status by remember { mutableStateOf("") }
    var shStatus by remember { mutableStateOf(shizukuStatus()) }
    var tab by remember { mutableStateOf(0) }

    var search by remember { mutableStateOf("") }
    var k by remember { mutableStateOf("") }
    var v by remember { mutableStateOf("") }
    var pn by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }

    fun save() {
        prefs.edit()
            .putString("flags", toJson(flags))
            .putString("presets", toJson(presets))
            .putString("path2", path)
            .putBoolean("restart", restart)
            .putBoolean("fps", fps)
            .apply()
    }

    fun toggleQuick(q: Quick) {
        if (flags[q.key] == q.value) flags.remove(q.key) else flags[q.key] = q.value
        save()
    }

    LaunchedEffect(Unit) {
        while (true) {
            shStatus = shizukuStatus()
            delay(2000)
        }
    }

    val ready = shStatus == "Shizuku ready"
    val chipText = when {
        ready -> "Shizuku ready"
        shStatus.contains("Tap Grant") -> "Needs permission"
        else -> "Shizuku off"
    }
    val chipColor = if (ready) Green else Red
    val tabs = listOf("Home" to "🌮", "Flags" to "🚩", "Presets" to "💾", "Settings" to "⚙️")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                tabs.forEachIndexed { i, (name, emoji) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(emoji, fontSize = 20.sp) },
                        label = { Text(name) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "TacoStrap logo",
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("TacoStrap", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Roblox FastFlag Manager", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = chipColor.copy(alpha = 0.18f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(chipColor))
                        Spacer(Modifier.width(6.dp))
                        Text(chipText, fontSize = 12.sp, color = chipColor)
                    }
                }
            }

            when (tab) {
                0 -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Active flags", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${flags.size}", fontSize = 40.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(path, style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    status = "Applying..."
                                    status = applyFlags(toJson(flags), path, restart, fps)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text("Apply flags", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { scope.launch(Dispatchers.IO) { status = resetFlags(path) } },
                                modifier = Modifier.weight(1f)
                            ) { Text("Reset") }
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val (_, msg) = sh("ls -l '$path'; cat '$path'")
                                        status = msg.ifEmpty { "Nothing returned." }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Verify") }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    try { Shizuku.requestPermission(1001) } catch (e: Throwable) { status = "Start Shizuku first." }
                                    shStatus = shizukuStatus()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) { Text("Grant Shizuku") }
                            OutlinedButton(
                                onClick = { shStatus = shizukuStatus() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Refresh") }
                        }
                    }
                    item {
                        Text(shStatus, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (status.isNotEmpty()) {
                        item {
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(status, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }

                1 -> {
                    val filtered = flags.keys.filter { it.contains(search, ignoreCase = true) }.sorted()
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                search, { search = it },
                                label = { Text("Search flags") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        QUICK.forEach { (category, list) ->
                            item {
                                Text(category, style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            item {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    list.forEach { q ->
                                        val on = flags[q.key] == q.value
                                        if (on) Button(onClick = { toggleQuick(q) }) { Text(q.label) }
                                        else OutlinedButton(onClick = { toggleQuick(q) }) { Text(q.label) }
                                    }
                                }
                            }
                        }
                        item { HorizontalDivider() }
                        item { Text("Add custom flag", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary) }
                        item {
                            OutlinedTextField(k, { k = it }, label = { Text("Name") },
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(v, { v = it }, label = { Text("Value") }, singleLine = true,
                                    modifier = Modifier.weight(1f))
                                Button({
                                    if (k.isNotBlank()) { flags[k.trim()] = v.trim(); k = ""; v = ""; save() }
                                }) { Text("Add") }
                            }
                        }
                        item { HorizontalDivider() }
                        item {
                            Text("Your flags (${filtered.size})", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text("No flags yet. Tap a quick flag or add one above.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(filtered, key = { it }) { name ->
                            val value = flags[name] ?: ""
                            val isBool = value.equals("true", true) || value.equals("false", true)
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, Modifier.weight(1f), fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                                        if (isBool) {
                                            Switch(
                                                checked = value.equals("true", true),
                                                onCheckedChange = { flags[name] = if (it) "True" else "False"; save() }
                                            )
                                        }
                                        TextButton({ flags.remove(name); save() }) { Text("✕") }
                                    }
                                    if (!isBool) {
                                        OutlinedTextField(
                                            value, { flags[name] = it; save() },
                                            singleLine = true, modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Save current flags as preset", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(pn, { pn = it }, label = { Text("Preset name") }, singleLine = true,
                                modifier = Modifier.weight(1f))
                            Button({
                                if (pn.isNotBlank()) { presets[pn.trim()] = toJson(flags); pn = ""; save() }
                            }) { Text("Save") }
                        }
                    }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({
                                val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                status = runCatching { flags.putAll(parseFlags(text)); save(); "Imported from clipboard." }
                                    .getOrElse { "Clipboard isn't valid JSON." }
                            }) { Text("Import clipboard") }
                            OutlinedButton({
                                clip.setPrimaryClip(ClipData.newPlainText("flags", toJson(flags)))
                                status = "Copied JSON."
                            }) { Text("Copy JSON") }
                            OutlinedButton({
                                val i = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, toJson(flags))
                                }
                                ctx.startActivity(Intent.createChooser(i, "Share flags"))
                            }) { Text("Share") }
                        }
                    }
                    if (status.isNotEmpty()) {
                        item { Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
                    }
                    item { HorizontalDivider() }
                    item {
                        Text("Saved presets (${presets.size})", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (presets.isEmpty()) {
                        item {
                            Text("No presets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(presets.keys.sorted(), key = { it }) { name ->
                        val count = runCatching { parseFlags(presets[name] ?: "{}").size }.getOrDefault(0)
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("$count flags", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton({
                                        runCatching {
                                            flags.clear(); flags.putAll(parseFlags(presets[name]!!)); save()
                                            status = "Loaded $name."
                                        }
                                    }) { Text("Load") }
                                    TextButton({ renaming = name; newName = name }) { Text("Rename") }
                                    TextButton({ presets.remove(name); save() }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            path, { path = it; save() },
                            label = { Text("ClientAppSettings.json path") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedButton({ path = DEFAULT_PATH; save() }) { Text("Reset path to default") }
                    }
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Restart Roblox after Apply", fontWeight = FontWeight.Medium)
                                    Text("Force-stops and relaunches Roblox so flags load.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(restart, { restart = it; save() })
                            }
                        }
                    }
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Disable frame-rate cap", fontWeight = FontWeight.Medium)
                                    Text("Sets a system property when you Apply.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(fps, { fps = it; save() })
                            }
                        }
                    }
                    item {
                        Text("TacoStrap v1.0", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    renaming?.let { old ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename preset") },
            text = { OutlinedTextField(newName, { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton({
                    val n = newName.trim()
                    if (n.isNotEmpty() && n != old) {
                        presets[old]?.let { presets[n] = it; presets.remove(old); save() }
                    }
                    renaming = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } }
        )
    }
}
