package com.example.flagmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.TimeUnit

private const val ROBLOX_PACKAGE = "com.roblox.client"
private const val DEFAULT_PATH = "/data/local/tmp/ClientAppSettings.json"
private const val FPS_PROP = "debug.graphics.game_default_frame_rate.disabled"
private const val FPS_FLAG = "DFIntTaskSchedulerTargetFps"
private const val QUALITY_FLAG = "FIntRomarkStartWithGraphicQualityLevel"
private const val SHIZUKU_REQUEST_CODE = 1001

// ---------- JSON / flag logic ----------
// Values remain real JSON values (Boolean, Number, String, etc.), not all strings.
private fun jsonValue(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is JSONObject, is JSONArray -> value
    is Boolean, is Number, is String -> value
    else -> value.toString()
}

private fun parseFlags(text: String): Map<String, Any> {
    val obj = JSONObject(text)
    val result = linkedMapOf<String, Any>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = obj.get(key)
        if (value !== JSONObject.NULL) result[key] = value
    }
    return result
}

private fun flagsToJson(flags: Map<String, Any>): String {
    val obj = JSONObject()
    flags.forEach { (key, value) -> obj.put(key, jsonValue(value)) }
    return obj.toString(2)
}

private fun valueText(value: Any?): String = when (value) {
    null -> ""
    JSONObject.NULL -> "null"
    is String -> value
    else -> value.toString()
}

private fun parseEditorValue(text: String): Any {
    // Treat valid JSON literals as their native type; otherwise store as a string.
    return try {
        val wrapped = JSONArray("[$text]")
        wrapped.get(0)
    } catch (_: Throwable) {
        text
    }
}

private fun shizukuStatus(): String = try {
    when {
        !Shizuku.pingBinder() -> "Shizuku is not running. Start it first."
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
            "Shizuku is running. Tap Grant."
        else -> "Shizuku ready"
    }
} catch (e: Throwable) {
    "Shizuku unavailable: ${e.message ?: e.javaClass.simpleName}"
}

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private fun shizukuProcess(command: String): Pair<Boolean, String> {
    var process: Process? = null
    return try {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val proc = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        process = proc
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = Thread {
            runCatching { proc.inputStream.bufferedReader().useLines { lines -> lines.forEach { stdout.appendLine(it) } } }
        }
        val errThread = Thread {
            runCatching { proc.errorStream.bufferedReader().useLines { lines -> lines.forEach { stderr.appendLine(it) } } }
        }
        outThread.start()
        errThread.start()
        val finished = proc.waitFor(20, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroy()
            return Pair(false, "Command timed out after 20 seconds.")
        }
        outThread.join(1000)
        errThread.join(1000)
        val combined = listOf(stdout.toString().trim(), stderr.toString().trim())
            .filter { it.isNotEmpty() }.joinToString("\n")
        Pair(proc.exitValue() == 0, combined)
    } catch (e: Throwable) {
        Pair(false, ((e.cause ?: e).message ?: e.toString()))
    } finally {
        runCatching { process?.destroy() }
    }
}

private fun runShell(command: String): Pair<Boolean, String> = shizukuProcess(command)

private fun applyFlags(json: String, path: String, restart: Boolean, disableFpsCap: Boolean): String {
    val parsed = runCatching { JSONObject(json).toString(2) }
        .getOrElse { return "Invalid JSON: ${it.message ?: "unable to parse"}" }
    if (!path.startsWith("/") || path.contains('\n') || path.contains('\u0000')) {
        return "Invalid file path. Use an absolute path."
    }
    val parent = path.substringBeforeLast('/', "")
    if (parent.isBlank()) return "Invalid file path."
    val encoded = android.util.Base64.encodeToString(parsed.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    val qPath = shellQuote(path)
    val qParent = shellQuote(parent)
    val command = buildString {
        append("mkdir -p $qParent && printf %s ")
        append(shellQuote(encoded))
        append(" | base64 -d > $qPath && chmod 644 $qPath")
        if (disableFpsCap) append(" && setprop $FPS_PROP true")
        if (restart) append(" && am force-stop $ROBLOX_PACKAGE && sleep 1 && monkey -p $ROBLOX_PACKAGE -c android.intent.category.LAUNCHER 1")
    }
    val (ok, message) = runShell(command)
    return if (ok) {
        "Configuration written to $path${if (restart) "\nRoblox restart command completed." else ""}${if (message.isNotBlank()) "\n$message" else ""}"
    } else {
        "Apply failed (exit/command error).\n${message.ifBlank { "No command output was returned." }}"
    }
}

private fun resetFlags(path: String): String {
    if (!path.startsWith("/") || path.contains('\n') || path.contains('\u0000')) return "Invalid file path."
    val command = "rm -f ${shellQuote(path)} && setprop $FPS_PROP false && am force-stop $ROBLOX_PACKAGE"
    val (ok, message) = runShell(command)
    return if (ok) "Removed the flag file and reset the FPS property. Reopen Roblox." else "Reset failed:\n${message.ifBlank { "No command output was returned." }}"
}

private fun launchRoblox(): String {
    val (ok, message) = runShell("monkey -p $ROBLOX_PACKAGE -c android.intent.category.LAUNCHER 1")
    return if (ok) "Roblox launch command completed." else "Launch failed:\n${message.ifBlank { "Is Roblox installed?" }}"
}

private fun verifyFile(path: String): String {
    if (!path.startsWith("/") || path.contains('\n') || path.contains('\u0000')) return "Invalid file path."
    val command = "if [ -f ${shellQuote(path)} ]; then echo 'FILE_EXISTS'; wc -c < ${shellQuote(path)}; cat ${shellQuote(path)}; else echo 'FILE_MISSING'; exit 2; fi"
    val (ok, output) = runShell(command)
    return if (ok) "File found. Contents follow:\n$output" else "Verification failed:\n${output.ifBlank { "File missing or unreadable." }}"
}

private fun clearCache(): String {
    // This may be blocked by Android's storage restrictions or app-specific permissions.
    val path = "/sdcard/Android/data/$ROBLOX_PACKAGE/cache"
    val (ok, message) = runShell("if [ -d ${shellQuote(path)} ]; then rm -rf ${shellQuote("$path/")}*; else echo 'Cache directory unavailable'; exit 2; fi")
    return if (ok) "Cache clear command completed." else "Could not clear cache:\n${message.ifBlank { "Android may restrict access to this directory." }}"
}

// ---------- Theme and options ----------
private data class AppTheme(val name: String, val primary: Color, val secondary: Color)
private val THEMES = listOf(
    AppTheme("Blue", Color(0xFF3D7BFF), Color(0xFF1E5BFF)),
    AppTheme("Taco", Color(0xFFFFB300), Color(0xFFFF7A1A)),
    AppTheme("Void", Color(0xFFB388FF), Color(0xFF7C4DFF)),
    AppTheme("Fish", Color(0xFF26D0B8), Color(0xFF00ACC1)),
    AppTheme("Frost", Color(0xFFB3E5FC), Color(0xFF81D4FA)),
    AppTheme("Rose", Color(0xFFFF8FAB), Color(0xFFE91E63))
)
private data class FlagOption(
    val group: String, val title: String, val description: String, val key: String, val value: Any
)
private val OPTIONS = listOf(
    FlagOption("Graphics", "Gray sky", "Replaces the sky with plain gray", "FFlagDebugSkyGray", true),
    FlagOption("Graphics", "Disable shadows", "Attempts to reduce Roblox shadow intensity", "FIntRenderShadowIntensity", 0),
    FlagOption("Graphics", "Disable post effects", "Attempts to disable post-processing effects", "FFlagDisablePostFx", true),
    FlagOption("Graphics", "MSAA setting", "Sets the requested MSAA sample count to 1", "FIntDebugForceMSAASamples", 1),
    FlagOption("Graphics", "Texture quality override", "Enables the texture quality override", "DFFlagTextureQualityOverrideEnabled", true),
    FlagOption("Performance", "FPS counter", "Requests an FPS counter", "FFlagDebugDisplayFPS", true),
    FlagOption("World detail", "Graphics quality override", "Requests a graphics quality override", "DFIntDebugFRMQualityLevelOverride", 1),
    FlagOption("World detail", "Remove grass", "Requests a lower terrain grass distance", "FIntFRMMaxGrassDistance", 0),
    FlagOption("Interface", "Remove blur", "Requests reduced interface blur", "FFlagDisableBlur", true),
    FlagOption("Network", "Network latency tolerance", "Experimental client flag; may be ignored", "DFIntNetworkLatencyTolerance", 1),
    FlagOption("Network", "Asset preload limit", "Requests an asset preload limit; may not improve loading", "DFIntNumAssetsMaxToPreload", 2000),
    FlagOption("Debug", "Ping breakdown", "Requests debug ping logging", "DFFlagDebugPrintPingBreakdown", true)
)
private val GROUPS = listOf("Graphics", "Performance", "World detail", "Interface", "Network", "Debug")

// ---------- Reusable UI ----------
@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 55L); visible = true }
    AnimatedVisibility(visible, enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 7 }) {
        content()
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(spring(stiffness = 420f)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun PressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    tonal: Boolean = false,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "buttonScale"
    )
    val buttonModifier = modifier.scale(scale)
    val shape = RoundedCornerShape(50)
    when {
        outlined -> OutlinedButton(onClick, buttonModifier, enabled = enabled, interactionSource = interaction, shape = shape) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
        tonal -> FilledTonalButton(onClick, buttonModifier, enabled = enabled, interactionSource = interaction, shape = shape) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
        else -> Button(onClick, buttonModifier, enabled = enabled, interactionSource = interaction, shape = shape) {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
        tween(220), label = "pillBg"
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        tween(220), label = "pillFg"
    )
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
    ) {
        Text(text, Modifier.padding(horizontal = 15.dp, vertical = 10.dp), fontSize = 13.sp, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
            .padding(13.dp)
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

class MainActivity : ComponentActivity() {
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            // App UI rechecks Shizuku status when it resumes / refreshes.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }
        setContent { TacoStrapApp() }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onDestroy()
    }
}

@Composable
fun TacoStrapApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tacostrap", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val snackbar = remember { SnackbarHostState() }

    val flags: SnapshotStateMap<String, Any> = remember {
        mutableStateMapOf<String, Any>().apply {
            runCatching { putAll(parseFlags(prefs.getString("flags", "{}") ?: "{}")) }
        }
    }
    val presets: SnapshotStateMap<String, String> = remember {
        mutableStateMapOf<String, String>().apply {
            runCatching {
                val saved = JSONObject(prefs.getString("presets", "{}") ?: "{}")
                val it = saved.keys()
                while (it.hasNext()) {
                    val name = it.next()
                    val value = saved.optString(name, "{}")
                    put(name, runCatching { flagsToJson(parseFlags(value)) }.getOrDefault("{}"))
                }
            }
        }
    }

    var path by remember { mutableStateOf(prefs.getString("path", DEFAULT_PATH) ?: DEFAULT_PATH) }
    var restart by remember { mutableStateOf(prefs.getBoolean("restart", true)) }
    var disableFpsCap by remember { mutableStateOf(prefs.getBoolean("disableFpsCap", false)) }
    var themeIndex by remember { mutableIntStateOf(prefs.getInt("theme", 1).coerceIn(0, THEMES.lastIndex)) }
    var animatedBackground by remember { mutableStateOf(prefs.getBoolean("animatedBackground", true)) }
    var busy by remember { mutableStateOf(false) }
    var shizuku by remember { mutableStateOf(shizukuStatus()) }
    var robloxVersion by remember { mutableStateOf("Unknown") }
    var tab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var customKey by remember { mutableStateOf("") }
    var customValue by remember { mutableStateOf("") }
    var presetName by remember { mutableStateOf("") }
    var renameOld by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf("") }
    var editorError by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf("") }

    fun save() {
        val presetObject = JSONObject()
        presets.forEach { (name, json) -> presetObject.put(name, json) }
        prefs.edit()
            .putString("flags", flagsToJson(flags))
            .putString("presets", presetObject.toString())
            .putString("path", path)
            .putBoolean("restart", restart)
            .putBoolean("disableFpsCap", disableFpsCap)
            .putInt("theme", themeIndex)
            .putBoolean("animatedBackground", animatedBackground)
            .apply()
    }

    fun toast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message.take(180))
        }
    }

    fun runTask(block: () -> String) {
        if (busy) return
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching(block).getOrElse { "Error: ${it.message ?: it.javaClass.simpleName}" }
            }
            output = result
            busy = false
            toast(result.lineSequence().firstOrNull().orEmpty())
            shizuku = shizukuStatus()
        }
    }

    fun toggleOption(option: FlagOption) {
        if (flags[option.key] == option.value) flags.remove(option.key)
        else flags[option.key] = option.value
        save()
    }

    LaunchedEffect(Unit) {
        while (true) {
            shizuku = shizukuStatus()
            delay(3000)
        }
    }

    val ready = shizuku == "Shizuku ready"
    LaunchedEffect(ready) {
        if (ready) {
            val (ok, result) = withContext(Dispatchers.IO) {
                runShell("dumpsys package $ROBLOX_PACKAGE | grep -m1 versionName")
            }
            robloxVersion = if (ok && result.contains("=")) result.substringAfter("=").trim() else "Not detected"
        } else robloxVersion = "Unknown"
    }

    val theme = THEMES[themeIndex]
    val primary by animateColorAsState(theme.primary, tween(500), label = "themePrimary")
    val secondary by animateColorAsState(theme.secondary, tween(500), label = "themeSecondary")
    val base = Color(0xFF111216)
    val scheme = darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF151515),
        primaryContainer = lerp(base, primary, 0.28f),
        onPrimaryContainer = Color(0xFFF7F4F1),
        secondary = secondary,
        onSecondary = Color(0xFF111216),
        secondaryContainer = lerp(base, secondary, 0.22f),
        onSecondaryContainer = Color(0xFFF7F4F1),
        background = base,
        onBackground = Color(0xFFF1EEEA),
        surface = Color(0xFF17181D),
        onSurface = Color(0xFFF1EEEA),
        surfaceVariant = lerp(Color(0xFF24252B), primary, 0.055f),
        onSurfaceVariant = Color(0xFFC8C7CC),
        outline = Color(0xFF55565E)
    )

    val infinite = rememberInfiniteTransition(label = "background")
    val shift by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift"
    )
    val pulse by infinite.animateFloat(
        0.65f, 1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val glow = if (animatedBackground) shift else 0.3f
    val statusColor by animateColorAsState(
        if (ready) Color(0xFF42D392) else if (shizuku.contains("Tap Grant")) Color(0xFFFFC857) else Color(0xFFFF6B75),
        tween(300), label = "statusColor"
    )
    val statusLabel = when {
        ready -> "Shizuku ready"
        shizuku.contains("Tap Grant") -> "Permission needed"
        else -> "Shizuku off"
    }
    val tabs = listOf("Home" to "⌂", "Flags" to "⚑", "Editor" to "✎", "Presets" to "▣", "Settings" to "⚙")

    MaterialTheme(colorScheme = scheme) {
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.2f), Color.Transparent),
                        center = Offset(180f + 650f * glow, 200f + 450f * glow),
                        radius = 1050f
                    )
                )
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar(containerColor = scheme.surfaceVariant.copy(alpha = 0.96f)) {
                        tabs.forEachIndexed { index, pair ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = {
                                    val scale by animateFloatAsState(
                                        if (tab == index) 1.16f else 1f,
                                        spring(dampingRatio = 0.45f), label = "navScale"
                                    )
                                    Text(pair.second, modifier = Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }, fontSize = 21.sp)
                                },
                                label = { Text(pair.first, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            ) { insets ->
                Column(Modifier.fillMaxSize().padding(insets)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "TacoStrap logo",
                            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("TacoStrap", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                            Text(tabs[tab].first, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(statusColor.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(8.dp)
                                    .graphicsLayer { alpha = if (ready) pulse else 1f }
                                    .clip(CircleShape).background(statusColor)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(statusLabel, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Bold)
                        }
                    }

                    AnimatedVisibility(busy, enter = fadeIn(), exit = fadeOut()) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(CircleShape)
                        )
                    }

                    Crossfade(targetState = tab, animationSpec = tween(260), label = "tabTransition") { selectedTab ->
                        when (selectedTab) {
                            0 -> LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Appear(0) {
                                        Panel {
                                            SectionTitle("Dashboard")
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Stat("Active flags", flags.size.toString(), Modifier.weight(1f))
                                                Stat("Roblox version", robloxVersion, Modifier.weight(1f))
                                            }
                                            Text("Configuration file", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                                            Text(path, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = scheme.primary)
                                        }
                                    }
                                }
                                item {
                                    Appear(1) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            PressButton(
                                                "Apply configuration",
                                                { runTask { applyFlags(flagsToJson(flags), path, restart, disableFpsCap) } },
                                                Modifier.fillMaxWidth().height(56.dp),
                                                enabled = !busy
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                PressButton("Launch Roblox", { runTask { launchRoblox() } }, Modifier.weight(1f), tonal = true, enabled = !busy)
                                                PressButton("Reset file", { runTask { resetFlags(path) } }, Modifier.weight(1f), outlined = true, enabled = !busy)
                                            }
                                        }
                                    }
                                }
                                item {
                                    Appear(2) {
                                        Panel {
                                            SectionTitle("Tools")
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                PressButton("Verify file", { runTask { verifyFile(path) } }, Modifier.weight(1f), outlined = true, enabled = !busy)
                                                PressButton("Clear cache", { runTask { clearCache() } }, Modifier.weight(1f), outlined = true, enabled = !busy)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                PressButton("Refresh status", { shizuku = shizukuStatus() }, Modifier.weight(1f), outlined = true)
                                                PressButton("Grant Shizuku", {
                                                    try {
                                                        if (Shizuku.pingBinder()) {
                                                            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                                                                toast("Shizuku permission already granted.")
                                                            } else {
                                                                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                                                            }
                                                        } else toast("Start Shizuku first.")
                                                    } catch (e: Throwable) {
                                                        toast("Shizuku unavailable: ${e.message ?: "unknown error"}")
                                                    }
                                                }, Modifier.weight(1f), outlined = true)
                                            }
                                            Text(shizuku, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                if (output.isNotBlank()) item {
                                    AnimatedVisibility(true, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                        Panel(container = scheme.primaryContainer) {
                                            SectionTitle("Latest result")
                                            Text(output, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = scheme.onPrimaryContainer)
                                            TextButton(onClick = { output = "" }) { Text("Dismiss") }
                                        }
                                    }
                                }
                            }

                            1 -> LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(13.dp)
                            ) {
                                item {
                                    Panel(container = scheme.primaryContainer) {
                                        SectionTitle("FastFlag notes")
                                        Text(
                                            "Roblox may ignore flags that are unsupported or not allowlisted. A successful file write does not guarantee each flag takes effect.",
                                            fontSize = 12.sp, color = scheme.onPrimaryContainer
                                        )
                                    }
                                }
                                item {
                                    OutlinedTextField(
                                        value = search,
                                        onValueChange = { search = it },
                                        label = { Text("Search flags and categories") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (search.isBlank()) item {
                                    Panel {
                                        SectionTitle("Frame rate request")
                                        ChipRow {
                                            listOf("30", "60", "90", "120", "144", "240").forEach { fps ->
                                                Pill("$fps FPS", valueText(flags[FPS_FLAG]) == fps) {
                                                    if (valueText(flags[FPS_FLAG]) == fps) flags.remove(FPS_FLAG)
                                                    else flags[FPS_FLAG] = fps.toInt()
                                                    save()
                                                }
                                            }
                                        }
                                        Text("Higher FPS depends on device, Roblox support, and available frame-rate controls.", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                                        SectionTitle("Graphics quality request")
                                        ChipRow {
                                            listOf("1", "3", "5", "7", "10").forEach { quality ->
                                                Pill("Level $quality", valueText(flags[QUALITY_FLAG]) == quality) {
                                                    if (valueText(flags[QUALITY_FLAG]) == quality) flags.remove(QUALITY_FLAG)
                                                    else flags[QUALITY_FLAG] = quality.toInt()
                                                    save()
                                                }
                                            }
                                        }
                                        SectionTitle("Experimental client setting")
                                        Text("DFIntConnectionMTUSize is a Roblox client flag, not the Android system MTU. Changing it may do nothing or cause connection issues.", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                                    }
                                }
                                GROUPS.forEach { group ->
                                    val matching = OPTIONS.filter {
                                        it.group == group && (
                                            search.isBlank() ||
                                                it.title.contains(search, true) ||
                                                it.key.contains(search, true) ||
                                                it.description.contains(search, true)
                                            )
                                    }
                                    if (matching.isNotEmpty()) item(key = "group_$group") {
                                        Panel {
                                            SectionTitle(group)
                                            matching.forEach { option ->
                                                ToggleRow(
                                                    option.title,
                                                    option.description,
                                                    flags[option.key] == option.value
                                                ) { toggleOption(option) }
                                            }
                                        }
                                    }
                                }
                                item {
                                    Panel {
                                        SectionTitle("Add or edit custom flag")
                                        OutlinedTextField(
                                            customKey, { customKey = it },
                                            label = { Text("Flag name") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(15.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            customValue, { customValue = it },
                                            label = { Text("Value (e.g. true, 120, or text)") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(15.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text("Valid JSON literals keep their type. Other input is stored as a string.", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                                        PressButton("Add flag", {
                                            val key = customKey.trim()
                                            if (key.isBlank()) toast("Enter a flag name.")
                                            else {
                                                flags[key] = parseEditorValue(customValue)
                                                customKey = ""
                                                customValue = ""
                                                save()
                                                toast("Flag added.")
                                            }
                                        }, Modifier.fillMaxWidth())
                                    }
                                }
                                item { SectionTitle("Active flags · ${flags.size}") }
                                if (flags.isEmpty()) item { Text("No active flags.", color = scheme.onSurfaceVariant) }
                                items(flags.keys.sorted(), key = { "flag_$it" }) { name ->
                                    val current = flags[name]
                                    Panel {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(name, Modifier.weight(1f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                            TextButton(onClick = { flags.remove(name); save() }) { Text("Remove") }
                                        }
                                        OutlinedTextField(
                                            value = valueText(current),
                                            onValueChange = { raw -> flags[name] = parseEditorValue(raw); save() },
                                            label = { Text("Value") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text("Current JSON type: ${current?.javaClass?.simpleName ?: "null"}", fontSize = 10.sp, color = scheme.onSurfaceVariant)
                                    }
                                }
                            }

                            2 -> LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Panel {
                                        SectionTitle("JSON editor")
                                        Text("Edit the complete configuration. Numbers and booleans remain their JSON types.", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                        OutlinedTextField(
                                            value = editor,
                                            onValueChange = { editor = it; editorError = null },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                            shape = RoundedCornerShape(16.dp),
                                            isError = editorError != null,
                                            supportingText = { editorError?.let { Text(it) } }
                                        )
                                        ChipRow {
                                            PressButton("Load active flags", { editor = flagsToJson(flags); editorError = null }, outlined = true)
                                            PressButton("Validate & save", {
                                                runCatching { parseFlags(editor) }
                                                    .onSuccess {
                                                        flags.clear()
                                                        flags.putAll(it)
                                                        save()
                                                        editorError = null
                                                        toast("Validated and saved ${it.size} flags.")
                                                    }
                                                    .onFailure { editorError = "Invalid JSON object: ${it.message ?: "check the syntax"}" }
                                            })
                                        }
                                        PressButton("Copy JSON", {
                                            clipboard.setPrimaryClip(ClipData.newPlainText("TacoStrap flags", if (editor.isBlank()) flagsToJson(flags) else editor))
                                            toast("JSON copied.")
                                        }, outlined = true)
                                    }
                                }
                            }

                            3 -> LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Panel {
                                        SectionTitle("Create a preset")
                                        OutlinedTextField(
                                            presetName, { presetName = it },
                                            label = { Text("Preset name") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(15.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        PressButton("Save current flags", {
                                            val name = presetName.trim()
                                            if (name.isBlank()) toast("Enter a preset name.")
                                            else {
                                                presets[name] = flagsToJson(flags)
                                                presetName = ""
                                                save()
                                                toast("Preset saved.")
                                            }
                                        }, Modifier.fillMaxWidth())
                                        ChipRow {
                                            PressButton("Import clipboard", {
                                                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                                runCatching { parseFlags(text) }
                                                    .onSuccess {
                                                        flags.clear()
                                                        flags.putAll(it)
                                                        save()
                                                        toast("Imported ${it.size} flags.")
                                                    }
                                                    .onFailure { toast("Clipboard does not contain a valid JSON object.") }
                                            }, outlined = true)
                                            PressButton("Copy active JSON", {
                                                clipboard.setPrimaryClip(ClipData.newPlainText("TacoStrap flags", flagsToJson(flags)))
                                                toast("Active JSON copied.")
                                            }, outlined = true)
                                            PressButton("Share", {
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, flagsToJson(flags))
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "Share TacoStrap flags"))
                                            }, outlined = true)
                                        }
                                    }
                                }
                                item { SectionTitle("Saved presets · ${presets.size}") }
                                if (presets.isEmpty()) item { Text("No saved presets yet.", color = scheme.onSurfaceVariant) }
                                items(presets.keys.sorted(), key = { "preset_$it" }) { name ->
                                    val count = runCatching { parseFlags(presets[name] ?: "{}").size }.getOrDefault(0)
                                    Panel {
                                        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("$count flags", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = {
                                                runCatching { parseFlags(presets[name] ?: "{}") }
                                                    .onSuccess {
                                                        flags.clear()
                                                        flags.putAll(it)
                                                        save()
                                                        toast("Loaded preset: $name")
                                                    }
                                                    .onFailure { toast("Preset is invalid JSON.") }
                                            }) { Text("Load") }
                                            TextButton(onClick = { renameOld = name; renameValue = name }) { Text("Rename") }
                                            TextButton(onClick = { presets.remove(name); save(); toast("Preset deleted.") }) { Text("Delete") }
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
                                    Panel {
                                        SectionTitle("Configuration file")
                                        OutlinedTextField(
                                            value = path,
                                            onValueChange = { path = it; save() },
                                            label = { Text("ClientAppSettings.json path") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(15.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        PressButton("Restore verified default path", { path = DEFAULT_PATH; save(); toast("Default path restored.") }, outlined = true)
                                    }
                                }
                                item {
                                    Panel {
                                        SectionTitle("Apply behaviour")
                                        ToggleRow("Restart Roblox after Apply", "Force-stop and attempt to relaunch Roblox.", restart) { restart = it; save() }
                                        ToggleRow("Set FPS-cap system property", "Sets $FPS_PROP when applying. It may not affect the current Roblox client.", disableFpsCap) { disableFpsCap = it; save() }
                                    }
                                }
                                item {
                                    Panel {
                                        SectionTitle("Accent theme")
                                        Row(
                                            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            THEMES.forEachIndexed { index, item ->
                                                val selected = themeIndex == index
                                                val scale by animateFloatAsState(if (selected) 1.1f else 1f, spring(dampingRatio = 0.5f), label = "themeScale")
                                                Column(
                                                    Modifier.clickable { themeIndex = index; save() },
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Box(
                                                        Modifier.size(48.dp).scale(scale)
                                                            .clip(CircleShape)
                                                            .background(Brush.linearGradient(listOf(item.primary, item.secondary)))
                                                            .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                                    )
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(item.name, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                        ToggleRow("Animated background", "Slow-moving ambient color glow.", animatedBackground) {
                                            animatedBackground = it
                                            save()
                                        }
                                    }
                                }
                                item {
                                    Panel {
                                        SectionTitle("Maintenance")
                                        PressButton("Clear Roblox cache", { runTask { clearCache() } }, Modifier.fillMaxWidth(), outlined = true, enabled = !busy)
                                        PressButton("Clear active flags", {
                                            flags.clear()
                                            save()
                                            toast("Active flags cleared. This does not delete the file already written to Roblox's path.")
                                        }, Modifier.fillMaxWidth(), outlined = true)
                                        PressButton("Verify current file", { runTask { verifyFile(path) } }, Modifier.fillMaxWidth(), outlined = true, enabled = !busy)
                                    }
                                }
                                item {
                                    Panel {
                                        SectionTitle("About TacoStrap")
                                        Text("TacoStrap · Android FastFlag configuration utility", fontWeight = FontWeight.SemiBold)
                                        Text("Rootless Shizuku workflow. File-write success does not guarantee that every individual FastFlag is supported by Roblox.", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                        Text("Package: com.example.flagmanager", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = scheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        renameOld?.let { oldName ->
            AlertDialog(
                onDismissRequest = { renameOld = null },
                title = { Text("Rename preset") },
                text = {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        label = { Text("New name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newName = renameValue.trim()
                        if (newName.isNotBlank()) {
                            presets[oldName]?.let { json ->
                                presets.remove(oldName)
                                presets[newName] = json
                                save()
                                toast("Preset renamed.")
                            }
                        }
                        renameOld = null
                    }) { Text("Rename") }
                },
                dismissButton = { TextButton(onClick = { renameOld = null }) { Text("Cancel") } }
            )
        }
    }
}
