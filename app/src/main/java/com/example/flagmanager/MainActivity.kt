package com.example.flagmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.Base64

private const val PKG = "com.roblox.client"

private const val DEFAULT_PATH = "/data/local/tmp/ClientAppSettings.json"
private const val FPS_PROP = "debug.graphics.game_default_frame_rate.disabled"
private const val FPS_FLAG = "DFIntTaskSchedulerTargetFps"
private const val QUALITY_FLAG = "FIntRomarkStartWithGraphicQualityLevel"

fun parseFlags(text: String): Map<String, String> {
    val o = JSONObject(text)
    return o.keys().asSequence().associateWith { o.get(it).toString() }
}

fun toJson(m: Map<String, String>): String = JSONObject(m).toString(2)

fun shizukuStatus(): String = try {
    when {
        !Shizuku.pingBinder() -> "Shizuku is not running. Start it first."
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "Shizuku is running. Tap Grant."
        else -> "Shizuku ready"
    }
} catch (e: Throwable) {
    "Shizuku unavailable: ${e.message}"
}

private fun runProcess(p: Process): Pair<Boolean, String> {
    val out = p.inputStream.bufferedReader().readText()
    val err = p.errorStream.bufferedReader().readText()
    return Pair(p.waitFor() == 0, (out + err).trim())
}

fun shShizuku(cmd: String): Pair<Boolean, String> = try {
    val m = Shizuku::class.java.getDeclaredMethod(
        "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
    )
    m.isAccessible = true
    runProcess(m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process)
} catch (e: Throwable) {
    Pair(false, ((e.cause ?: e).message ?: e.toString()))
}

fun run(cmd: String) = shShizuku(cmd)

fun applyFlags(json: String, path: String, restart: Boolean, fps: Boolean): String {
    val b64 = Base64.getEncoder().encodeToString(json.toByteArray())
    val dir = path.substringBeforeLast('/')
    val cmd = buildString {
        append("mkdir -p '$dir' && echo '$b64' | base64 -d > '$path' && chmod 644 '$path'")
        if (fps) append(" ; setprop $FPS_PROP true")
        if (restart) append(" ; am force-stop $PKG ; sleep 1 ; monkey -p $PKG -c android.intent.category.LAUNCHER 1")
    }
    val (ok, msg) = run(cmd)
    return if (ok) "Applied to $path" else "Failed: $msg"
}

fun resetFlags(path: String): String {
    val (ok, msg) = run("rm -f '$path'; setprop $FPS_PROP false; am force-stop $PKG")
    return if (ok) "Removed flag file. Reopen Roblox." else "Failed: $msg"
}

fun fullRobloxReset(path: String): String {
    val (ok, msg) = run("""
        rm -f '$path';
        setprop $FPS_PROP false;
        rm -rf /sdcard/Android/data/$PKG/cache/*;
        am force-stop $PKG
    """.trimIndent())
    return if (ok) "Full Roblox reset complete. Reopen Roblox." else "Failed: $msg"
}

fun clearAllRobloxFlags(path: String): String {
    val (ok, msg) = run("rm -f '$path'; setprop $FPS_PROP false; am force-stop $PKG")
    return if (ok) "Removed all Roblox flags. Reopen Roblox." else "Failed: $msg"
}

fun launchRoblox(): String {
    val (ok, msg) = run("monkey -p $PKG -c android.intent.category.LAUNCHER 1")
    return if (ok) "Launched Roblox." else "Failed: $msg"
}

fun clearCache(): String {
    val (ok, msg) = run("rm -rf /sdcard/Android/data/$PKG/cache/*")
    return if (ok) "Cleared Roblox cache." else "Failed: $msg"
}

private data class AppTheme(val name: String, val primary: Color, val secondary: Color)

private val THEMES = listOf(
    AppTheme("Blue", Color(0xFF3D7BFF), Color(0xFF1E5BFF)),
    AppTheme("Taco", Color(0xFFFFB300), Color(0xFFFF7A1A)),
    AppTheme("Void", Color(0xFFB388FF), Color(0xFF7C4DFF)),
    AppTheme("Fish", Color(0xFF26D0B8), Color(0xFF00ACC1)),
    AppTheme("Frost", Color(0xFFB3E5FC), Color(0xFF81D4FA)),
    AppTheme("Rose", Color(0xFFFF8FAB), Color(0xFFE91E63))
)

private data class Opt(val group: String, val title: String, val sub: String, val key: String, val on: String)

private val OPTIONS = listOf(
    Opt("Graphics", "Gray sky", "Replaces the sky with plain gray", "FFlagDebugSkyGray", "True"),
    Opt("Graphics", "Disable shadows", "Turns off Roblox shadows", "FIntRenderShadowIntensity", "0"),
    Opt("Graphics", "Disable post effects", "Removes bloom, blur and color effects", "FFlagDisablePostFx", "True"),
    Opt("Graphics", "MSAA off", "Turns off anti aliasing", "FIntDebugForceMSAASamples", "1"),
    Opt("Graphics", "Texture quality override", "Forces the lowest textures", "DFFlagTextureQualityOverrideEnabled", "True"),
    Opt("Performance", "FPS counter", "Shows frame rate in the corner", "FFlagDebugDisplayFPS", "True"),
    Opt("World detail", "Graphics quality override", "Locks the Roblox graphics level", "DFIntDebugFRMQualityLevelOverride", "1"),
    Opt("World detail", "Remove grass", "Hides the built in terrain grass", "FIntFRMMaxGrassDistance", "0"),
    Opt("Interface", "Remove blur", "No blur behind Roblox menus", "FFlagDisableBlur", "True"),
    Opt("Network", "Reduce lag spikes", "Smooths out network output", "DFIntNetworkLatencyTolerance", "1"),
    Opt("Network", "Faster loading", "Preloads more assets to load games faster", "DFIntNumAssetsMaxToPreload", "2000"),
    Opt("Debug", "Ping breakdown", "Prints ping details to the Roblox console", "DFFlagDebugPrintPingBreakdown", "True")
)

private val GROUPS = listOf("Graphics", "Performance", "World detail", "Interface", "Network", "Debug")

@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 60L); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 6 }
    ) { content() }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier.fillMaxWidth().animateContentSize(spring(stiffness = 400f)),
        shape = RoundedCornerShape(26.dp),
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
    tonal: Boolean = false
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "press")
    val m = modifier.scale(sc)
    val shape = RoundedCornerShape(50)
    when {
        outlined -> OutlinedButton(onClick, m, interactionSource = src, shape = shape) {
            Text(text, fontWeight = FontWeight.Medium)
        }
        tonal -> FilledTonalButton(onClick, m, interactionSource = src, shape = shape) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
        else -> Button(onClick, m, interactionSource = src, shape = shape) {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
        tween(250), label = "pillbg"
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        tween(250), label = "pillfg"
    )
    val sc by animateFloatAsState(if (selected) 1.05f else 1f, spring(dampingRatio = 0.5f), label = "pillsc")
    Box(Modifier.scale(sc).clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)) {
        Text(text, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .padding(14.dp)
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("fm", 0) }
    val scope = rememberCoroutineScope()
    val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val snack = remember { SnackbarHostState() }

    val flags = remember {
        mutableStateMapOf<String, String>().apply {
            runCatching {
                putAll(parseFlags(prefs.getString("flags", "{}") ?: "{}"))
            }
        }
    }

    val presets = remember {
        mutableStateMapOf<String, String>().apply {
            runCatching {
                putAll(parseFlags(prefs.getString("presets", "{}") ?: "{}"))
            }
        }
    }

    var path by remember { mutableStateOf(prefs.getString("path3", DEFAULT_PATH) ?: DEFAULT_PATH) }
    var restart by remember { mutableStateOf(prefs.getBoolean("restart", true)) }
    var fps by remember { mutableStateOf(prefs.getBoolean("fps", false)) }
    var themeIdx by remember { mutableStateOf(prefs.getInt("theme", 0).coerceIn(0, THEMES.size - 1)) }
    var animBg by remember { mutableStateOf(prefs.getBoolean("animbg", true)) }
    var busy by remember { mutableStateOf(false) }
    var shStatus by remember { mutableStateOf(shizukuStatus()) }
    var robloxVersion by remember { mutableStateOf("-") }
    var tab by remember { mutableStateOf(0) }

    var search by remember { mutableStateOf("") }
    var k by remember { mutableStateOf("") }
    var v by remember { mutableStateOf("") }
    var pn by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf("") }
    var editorErr by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf("") }

    fun save() {
        prefs.edit()
            .putString("flags", toJson(flags))
            .putString("presets", toJson(presets))
            .putString("path3", path)
            .putBoolean("restart", restart)
            .putBoolean("fps", fps)
            .putInt("theme", themeIdx)
            .putBoolean("animbg", animBg)
            .apply()
    }

    fun say(msg: String) {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(msg)
        }
    }

    fun task(block: () -> String) {
        if (busy) return
        scope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) { block() }
            output = r
            busy = false
            say(r.lineSequence().firstOrNull().orEmpty().take(120))
        }
    }

    fun toggleOpt(o: Opt) {
        if (flags[o.key] == o.on) flags.remove(o.key) else flags[o.key] = o.on
        save()
    }

    LaunchedEffect(Unit) {
        while (true) {
            shStatus = shizukuStatus()
            delay(2000)
        }
    }

    val ready = shStatus == "Shizuku ready"

    LaunchedEffect(ready) {
        if (ready) {
            val (ok, out) = withContext(Dispatchers.IO) {
                run("dumpsys package $PKG | grep -m1 versionName")
            }
            robloxVersion = if (ok && out.contains("=")) out.substringAfter("=").trim() else "Not found"
        }
    }

    val theme = THEMES[themeIdx]
    val pc by animateColorAsState(theme.primary, tween(600), label = "pc")
    val sc by animateColorAsState(theme.secondary, tween(600), label = "sc")
    val base = Color(0xFF131316)
    val scheme = darkColorScheme(
        primary = pc,
        onPrimary = Color(0xFF0B0F1A),
        primaryContainer = lerp(base, pc, 0.28f),
        onPrimaryContainer = lerp(pc, Color.White, 0.65f),
        secondary = sc,
        onSecondary = Color(0xFF0B0F1A),
        secondaryContainer = lerp(base, pc, 0.3f),
        onSecondaryContainer = lerp(pc, Color.White, 0.4f),
        background = base,
        onBackground = Color(0xFFEDE8E3),
        surface = base,
        onSurface = Color(0xFFEDE8E3),
        surfaceVariant = lerp(Color(0xFF222226), pc, 0.06f),
        onSurfaceVariant = Color(0xFFC9C4BE),
        outline = lerp(Color(0xFF55525A), pc, 0.25f)
    )

    val inf = rememberInfiniteTransition(label = "inf")
    val shift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    val pulse by inf.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    val glow = if (animBg) shift else 0.3f

    val statusColor by animateColorAsState(if (ready) Color(0xFF4CAF50) else Color(0xFFEF5350), tween(400), label = "chip")
    val statusText = when {
        ready -> "Shizuku ready"
        shStatus.contains("Tap Grant") -> "Needs permission"
        else -> "Shizuku off"
    }

    val flagCount by animateIntAsState(flags.size, tween(600), label = "count")
    val tabs = listOf("Home" to "🏠", "Flags" to "🚩", "Editor" to "📝", "Presets" to "💾", "Settings" to "⚙️")

    MaterialTheme(colorScheme = scheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .background(
                    Brush.radialGradient(
                        listOf(pc.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(150f + 700f * glow, 250f + 700f * glow),
                        radius = 1100f
                    )
                )
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snack) },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)) {
                        tabs.forEachIndexed { i, (name, emoji) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = {
                                    val s by animateFloatAsState(if (tab == i) 1.3f else 1f, spring(dampingRatio = 0.4f), label = "ic")
                                    Text(emoji, fontSize = 20.sp, modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s })
                                },
                                label = { Text(name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            ) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painterResource(id = R.mipmap.ic_launcher),
                            "TacoStrap logo",
                            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("TacoStrap", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(tabs[tab].first, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(statusColor.copy(alpha = 0.18f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .graphicsLayer { alpha = if (ready) pulse else 1f }
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(statusText, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Medium)
                        }
                    }

                    AnimatedVisibility(busy, enter = fadeIn(), exit = fadeOut()) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(50))
                        )
                    }

                    Crossfade(targetState = tab, animationSpec = tween(280), label = "tab") { t ->
                        when (t) {
                            0 -> LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Appear(0) {
                                        Panel {
                                            SectionTitle("Dashboard")
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Stat("Active flags", "$flagCount", Modifier.weight(1f))
                                                Stat("Roblox", robloxVersion, Modifier.weight(1f))
                                            }
                                            Text(path, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                item {
                                    Appear(1) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            PressButton("Apply flags", {
                                                task { applyFlags(toJson(flags), path, restart, fps) }
                                            }, Modifier.fillMaxWidth().height(58.dp))

                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                PressButton("Launch", { task { launchRoblox() } }, Modifier.weight(1f), tonal = true)
                                                PressButton("Reset", { task { resetFlags(path) } }, Modifier.weight(1f), outlined = true)
                                            }

                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                PressButton("Full reset", {
                                                    flags.clear()
                                                    save()
                                                    task { fullRobloxReset(path) }
                                                }, Modifier.fillMaxWidth(), outlined = true)
                                            }

                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                PressButton("Clear all flags", {
                                                    flags.clear()
                                                    save()
                                                    task { clearAllRobloxFlags(path) }
                                                }, Modifier.fillMaxWidth(), outlined = true)
                                            }
                                        }
                                    }
                                }

                                item {
                                    Appear(2) {
                                        Panel {
                                            SectionTitle("Tools")
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                PressButton("Verify", {
                                                    task {
                                                        val (_, msg) = run("ls -l '$path'; cat '$path'")
                                                        msg.ifEmpty { "Nothing returned. File may not exist." }
                                                    }
                                                }, Modifier.weight(1f), outlined = true)
                                                PressButton("Clear cache", { task { clearCache() } }, Modifier.weight(1f), outlined = true)
                                            }

                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                PressButton("Refresh", { shStatus = shizukuStatus() }, Modifier.weight(1f), outlined = true)
                                                PressButton("Grant Shizuku", {
                                                    try {
                                                        Shizuku.requestPermission(1001)
                                                    } catch (e: Throwable) {
                                                        say("Start Shizuku first.")
                                                    }
                                                    shStatus = shizukuStatus()
                                                }, Modifier.weight(1f), outlined = true)
                                            }

                                            Text(shStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                item {
                                    AnimatedVisibility(
                                        output.isNotEmpty(),
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Panel(container = MaterialTheme.colorScheme.primaryContainer) {
                                            Text(output, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            TextButton({ output = "" }) { Text("Dismiss") }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    item {
                                        Panel(container = MaterialTheme.colorScheme.primaryContainer) {
                                            Text(
                                                "Roblox only accepts a whitelist of FastFlags. Flags outside it are ignored, so some of these may do nothing.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    item {
                                        OutlinedTextField(
                                            value = search,
                                            onValueChange = { search = it },
                                            label = { Text("Search options") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    if (search.isBlank()) {
                                        item {
                                            Panel {
                                                SectionTitle("Frame rate limit")
                                                ChipRow {
                                                    listOf("30", "60", "90", "120", "144", "240").forEach { n ->
                                                        Pill("$n FPS", flags[FPS_FLAG] == n) {
                                                            if (flags[FPS_FLAG] == n) flags.remove(FPS_FLAG) else flags[FPS_FLAG] = n
                                                            save()
                                                        }
                                                    }
                                                }
                                                Text(
                                                    "Above 60 also needs Disable frame-rate cap in Settings.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                SectionTitle("Graphics quality")
                                                ChipRow {
                                                    listOf("1", "3", "5", "7", "10").forEach { n ->
                                                        Pill("Level $n", flags[QUALITY_FLAG] == n) {
                                                            if (flags[QUALITY_FLAG] == n) flags.remove(QUALITY_FLAG) else flags[QUALITY_FLAG] = n
                                                            save()
                                                        }
                                                    }
                                                }
                                                SectionTitle("Network packet size (MTU)")
                                                ChipRow {
                                                    listOf("900", "1200", "1400", "1492").forEach { n ->
                                                        Pill(n, flags["DFIntConnectionMTUSize"] == n) {
                                                            if (flags["DFIntConnectionMTUSize"] == n) flags.remove("DFIntConnectionMTUSize") else flags["DFIntConnectionMTUSize"] = n
                                                            save()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    GROUPS.forEach { g ->
                                        val opts = OPTIONS.filter {
                                            it.group == g && (search.isBlank() || it.title.contains(search, true) || it.key.contains(search, true))
                                        }
                                        if (opts.isNotEmpty()) item(key = "g_$g") {
                                            Panel {
                                                SectionTitle(g)
                                                opts.forEach { o ->
                                                    ToggleRow(o.title, o.sub, flags[o.key] == o.on) { toggleOpt(o) }
                                                }
                                            }
                                        }
                                    }

                                    item {
                                        Panel {
                                            SectionTitle("Add custom flag")
                                            OutlinedTextField(
                                                value = k,
                                                onValueChange = { k = it },
                                                label = { Text("Name") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = v,
                                                    onValueChange = { v = it },
                                                    label = { Text("Value") },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                PressButton("Add", {
                                                    if (k.isNotBlank()) {
                                                        flags[k.trim()] = v.trim()
                                                        k = ""
                                                        v = ""
                                                        save()
                                                    }
                                                })
                                            }
                                        }
                                    }

                                    item { SectionTitle("Active flags (${flags.size})") }
                                    if (flags.isEmpty()) item { Text("Nothing enabled yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    items(flags.keys.sorted(), key = { "f_$it" }) { name ->
                                        val value = flags[name] ?: ""
                                        Panel {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(name, Modifier.weight(1f), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                                                TextButton({ flags.remove(name); save() }) { Text("✕") }
                                            }
                                            OutlinedTextField(
                                                value = value,
                                                onValueChange = { flags[name] = it; save() },
                                                singleLine = true,
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }

                            2 -> {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    item {
                                        Panel {
                                            SectionTitle("Raw JSON")
                                            OutlinedTextField(
                                                value = editor,
                                                onValueChange = { editor = it; editorErr = null },
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                                shape = RoundedCornerShape(18.dp),
                                                isError = editorErr != null,
                                                supportingText = { editorErr?.let { Text(it) } }
                                            )
                                            ChipRow {
                                                PressButton("Load current", { editor = toJson(flags); editorErr = null }, outlined = true)
                                                PressButton("Validate & save", {
                                                    runCatching { parseFlags(editor) }
                                                        .onSuccess {
                                                            flags.clear()
                                                            flags.putAll(it)
                                                            save()
                                                            editorErr = null
                                                            say("Saved ${it.size} flags.")
                                                        }
                                                        .onFailure { editorErr = "Invalid JSON" }
                                                })
                                            }
                                        }
                                    }
                                }
                            }

                            3 -> {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Panel {
                                            SectionTitle("Save current flags as preset")
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = pn,
                                                    onValueChange = { pn = it },
                                                    label = { Text("Preset name") },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                PressButton("Save", {
                                                    if (pn.isNotBlank()) {
                                                        presets[pn.trim()] = toJson(flags)
                                                        pn = ""
                                                        save()
                                                        say("Preset saved.")
                                                    }
                                                })
                                            }
                                            ChipRow {
                                                PressButton("Import clipboard", {
                                                    val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                                    runCatching { flags.putAll(parseFlags(text)); save() }
                                                        .onSuccess { say("Imported from clipboard.") }
                                                        .onFailure { say("Clipboard isn't valid JSON.") }
                                                }, outlined = true)
                                                PressButton("Copy JSON", {
                                                    clip.setPrimaryClip(ClipData.newPlainText("flags", toJson(flags)))
                                                    say("Copied JSON.")
                                                }, outlined = true)
                                                PressButton("Share", {
                                                    val i = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, toJson(flags))
                                                    }
                                                    ctx.startActivity(Intent.createChooser(i, "Share flags"))
                                                }, outlined = true)
                                            }
                                        }
                                    }

                                    item { SectionTitle("Saved presets (${presets.size})") }
                                    if (presets.isEmpty()) item {
                                        Text("No presets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    items(presets.keys.sorted(), key = { "p_$it" }) { name ->
                                        val count = runCatching { parseFlags(presets[name] ?: "{}").size }.getOrDefault(0)
                                        Panel {
                                            Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("$count flags", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton({
                                                    runCatching {
                                                        flags.clear()
                                                        flags.putAll(parseFlags(presets[name]!!))
                                                        save()
                                                        say("Loaded $name.")
                                                    }
                                                }) { Text("Load") }
                                                TextButton({ renaming = name; newName = name }) { Text("Rename") }
                                                TextButton({ presets.remove(name); save() }) { Text("Delete") }
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Panel {
                                            SectionTitle("Flag file")
                                            OutlinedTextField(
                                                value = path,
                                                onValueChange = { path = it; save() },
                                                label = { Text("ClientAppSettings.json path") },
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            PressButton("Reset path to default", { path = DEFAULT_PATH; save() }, outlined = true)
                                        }
                                    }

                                    item {
                                        Panel {
                                            SectionTitle("Behaviour")
                                            ToggleRow("Restart Roblox after Apply", "Force-stops and relaunches Roblox so flags load.", restart) { restart = it; save() }
                                            ToggleRow("Disable frame-rate cap", "Sets a system property when you Apply.", fps) { fps = it; save() }
                                        }
                                    }

                                    item {
                                        Panel {
                                            SectionTitle("Accent theme")
                                            Row(
                                                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                THEMES.forEachIndexed { i, th ->
                                                    val sel = themeIdx == i
                                                    val s by animateFloatAsState(if (sel) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "sw")
                                                    Column(Modifier.clickable { themeIdx = i; save() }, horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Box(
                                                            Modifier.size(54.dp).scale(s).clip(CircleShape)
                                                                .background(Brush.linearGradient(listOf(th.primary, th.secondary)))
                                                                .then(if (sel) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                                        )
                                                        Spacer(Modifier.height(6.dp))
                                                        Text(th.name, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                            ToggleRow("Animated background", "Slow moving glow behind the app.", animBg) { animBg = it; save() }
                                        }
                                    }

                                    item {
                                        Panel {
                                            SectionTitle("Maintenance")
                                            PressButton("Clear Roblox cache", { task { clearCache() } }, outlined = true)
                                            PressButton("Clear all flags", {
                                                flags.clear()
                                                save()
                                                task { clearAllRobloxFlags(path) }
                                            }, outlined = true)
                                            PressButton("Full reset", {
                                                flags.clear()
                                                save()
                                                task { fullRobloxReset(path) }
                                            }, outlined = true)
                                        }
                                    }

                                    item {
                                        Panel {
                                            SectionTitle("About")
                                            Text(
                                                "TacoStrap v3.0. Layout inspired by Bloxstrap, Fishstrap and Voidstrap.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
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
                            presets[old]?.let {
                                presets[n] = it
                                presets.remove(old)
                                save()
                            }
                        }
                        renaming = null
                    }) { Text("Rename") }
                },
                dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } }
            )
        }
    }
}
