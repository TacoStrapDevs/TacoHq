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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

// ---------- Logic ----------

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

fun launchRoblox(): String {
    val (ok, msg) = sh("monkey -p $PKG -c android.intent.category.LAUNCHER 1")
    return if (ok) "Launched Roblox." else "Failed: $msg"
}

fun clearCache(): String {
    val (ok, msg) = sh("rm -rf /sdcard/Android/data/$PKG/cache/*")
    return if (ok) "Cleared Roblox cache." else "Failed: $msg"
}

// ---------- Themes ----------

private data class AppTheme(val name: String, val primary: Color, val secondary: Color)

private val THEMES = listOf(
    AppTheme("Taco", Color(0xFFFFB300), Color(0xFFFF7A1A)),
    AppTheme("Void", Color(0xFFB388FF), Color(0xFF7C4DFF)),
    AppTheme("Blox", Color(0xFF4FC3F7), Color(0xFF1E88E5)),
    AppTheme("Fish", Color(0xFF26D0B8), Color(0xFF00ACC1)),
    AppTheme("Frost", Color(0xFFB3E5FC), Color(0xFF81D4FA)),
    AppTheme("Rose", Color(0xFFFF8FAB), Color(0xFFE91E63))
)

private data class Quick(val label: String, val key: String, val value: String)

private val QUICK = listOf(
    Quick("Gray sky", "FFlagDebugSkyGray", "True"),
    Quick("No shadows", "FIntRenderShadowIntensity", "0"),
    Quick("No post-FX", "FFlagDisablePostFx", "True"),
    Quick("MSAA off", "FIntDebugForceMSAASamples", "1")
)

// ---------- Reusable UI ----------

@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 5 }
    ) { content() }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun PressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "press"
    )
    val m = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    if (outlined) {
        OutlinedButton(
            onClick = onClick, modifier = m, interactionSource = src,
            shape = RoundedCornerShape(16.dp)
        ) { Text(text, fontWeight = FontWeight.Medium) }
    } else {
        Button(
            onClick = onClick, modifier = m, interactionSource = src,
            shape = RoundedCornerShape(16.dp)
        ) { Text(text, fontWeight = FontWeight.Bold) }
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
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
    ) {
        Text(
            text, Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
    var themeIdx by remember { mutableStateOf(prefs.getInt("theme", 0).coerceIn(0, THEMES.size - 1)) }
    var animBg by remember { mutableStateOf(prefs.getBoolean("animbg", true)) }
    var status by remember { mutableStateOf("") }
    var shStatus by remember { mutableStateOf(shizukuStatus()) }
    var robloxVersion by remember { mutableStateOf("-") }
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
            .putInt("theme", themeIdx)
            .putBoolean("animbg", animBg)
            .apply()
    }

    fun toggleKey(key: String, value: String) {
        if (flags[key] == value) flags.remove(key) else flags[key] = value
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
            val (ok, out) = withContext(Dispatchers.IO) { sh("dumpsys package $PKG | grep -m1 versionName") }
            robloxVersion = if (ok && out.contains("=")) out.substringAfter("=").trim() else "Not found"
        }
    }

    // ----- Animated theme colors -----
    val theme = THEMES[themeIdx]
    val pc by animateColorAsState(theme.primary, tween(600), label = "pc")
    val sc by animateColorAsState(theme.secondary, tween(600), label = "sc")
    val base = Color(0xFF131316)
    val scheme = darkColorScheme(
        primary = pc,
        onPrimary = Color(0xFF16120A),
        primaryContainer = lerp(base, pc, 0.28f),
        onPrimaryContainer = lerp(pc, Color.White, 0.65f),
        secondary = sc,
        onSecondary = Color(0xFF16120A),
        secondaryContainer = lerp(base, pc, 0.3f),
        onSecondaryContainer = lerp(pc, Color.White, 0.4f),
        background = base,
        onBackground = Color(0xFFEDE8E3),
        surface = base,
        onSurface = Color(0xFFEDE8E3),
        surfaceVariant = lerp(Color(0xFF1E1D22), pc, 0.07f),
        onSurfaceVariant = Color(0xFFC9C4BE),
        outline = lerp(Color(0xFF55525A), pc, 0.25f)
    )

    val inf = rememberInfiniteTransition(label = "inf")
    val shift by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift"
    )
    val breath by inf.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val glow = if (animBg) shift else 0.3f

    val chipColor by animateColorAsState(
        if (ready) Color(0xFF4CAF50) else Color(0xFFEF5350), tween(400), label = "chip"
    )
    val chipText = when {
        ready -> "Shizuku ready"
        shStatus.contains("Tap Grant") -> "Needs permission"
        else -> "Shizuku off"
    }
    val flagCount by animateIntAsState(flags.size, tween(600), label = "count")
    val tabs = listOf("Home" to "🌮", "Flags" to "🚩", "Presets" to "💾", "Theme" to "🎨", "Settings" to "⚙️")

    MaterialTheme(colorScheme = scheme) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .background(
                    Brush.radialGradient(
                        colors = listOf(pc.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(200f + 700f * glow, 300f + 500f * glow),
                        radius = 1000f
                    )
                )
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)) {
                        tabs.forEachIndexed { i, (name, emoji) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = {
                                    val s by animateFloatAsState(
                                        if (tab == i) 1.25f else 1f,
                                        spring(dampingRatio = 0.5f), label = "ic"
                                    )
                                    Text(
                                        emoji, fontSize = 20.sp,
                                        modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }
                                    )
                                },
                                label = { Text(name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            ) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {

                    // ----- Header -----
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "TacoStrap logo",
                            modifier = Modifier.size(46.dp)
                                .graphicsLayer { scaleX = breath; scaleY = breath }
                                .clip(RoundedCornerShape(13.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "TacoStrap", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "FastFlag Manager v2.0", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(chipColor.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(chipColor))
                            Spacer(Modifier.width(6.dp))
                            Text(chipText, fontSize = 12.sp, color = chipColor)
                        }
                    }

                    Crossfade(targetState = tab, animationSpec = tween(300), label = "tab") { t ->
                        when (t) {

                            // ================= HOME =================
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
                                            Text(
                                                path, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                item {
                                    Appear(1) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            PressButton(
                                                "Apply flags",
                                                {
                                                    scope.launch(Dispatchers.IO) {
                                                        status = "Applying..."
                                                        status = applyFlags(toJson(flags), path, restart, fps)
                                                    }
                                                },
                                                Modifier.fillMaxWidth().height(58.dp)
                                            )
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                PressButton(
                                                    "Launch Roblox",
                                                    { scope.launch(Dispatchers.IO) { status = launchRoblox() } },
                                                    Modifier.weight(1f), outlined = true
                                                )
                                                PressButton(
                                                    "Reset",
                                                    { scope.launch(Dispatchers.IO) { status = resetFlags(path) } },
                                                    Modifier.weight(1f), outlined = true
                                                )
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
                                                PressButton(
                                                    "Verify",
                                                    {
                                                        scope.launch(Dispatchers.IO) {
                                                            val (_, msg) = sh("ls -l '$path'; cat '$path'")
                                                            status = msg.ifEmpty { "Nothing returned." }
                                                        }
                                                    },
                                                    Modifier.weight(1f), outlined = true
                                                )
                                                PressButton(
                                                    "Clear cache",
                                                    { scope.launch(Dispatchers.IO) { status = clearCache() } },
                                                    Modifier.weight(1f), outlined = true
                                                )
                                            }
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                PressButton(
                                                    "Grant Shizuku",
                                                    {
                                                        try { Shizuku.requestPermission(1001) }
                                                        catch (e: Throwable) { status = "Start Shizuku first." }
                                                        shStatus = shizukuStatus()
                                                    },
                                                    Modifier.weight(1f), outlined = true
                                                )
                                                PressButton(
                                                    "Refresh",
                                                    { shStatus = shizukuStatus() },
                                                    Modifier.weight(1f), outlined = true
                                                )
                                            }
                                            Text(
                                                shStatus, fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                item {
                                    AnimatedVisibility(
                                        visible = status.isNotEmpty(),
                                        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
                                        exit = fadeOut(tween(200))
                                    ) {
                                        Panel(container = MaterialTheme.colorScheme.primaryContainer) {
                                            Text(
                                                status, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }

                            // ================= FLAGS =================
                            1 -> {
                                val filtered = flags.keys.filter { it.contains(search, ignoreCase = true) }.sorted()
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                            search, { search = it },
                                            label = { Text("Search flags") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    item {
                                        Panel {
                                            SectionTitle("Frame rate cap")
                                            Row(
                                                Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                listOf("30", "60", "120", "144", "240").forEach { n ->
                                                    Pill("$n FPS", flags[FPS_FLAG] == n) { toggleKey(FPS_FLAG, n) }
                                                }
                                            }
                                            SectionTitle("Graphics quality")
                                            Row(
                                                Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                listOf("1", "3", "5", "7", "10").forEach { n ->
                                                    Pill("Level $n", flags[QUALITY_FLAG] == n) { toggleKey(QUALITY_FLAG, n) }
                                                }
                                            }
                                            SectionTitle("Visuals")
                                            Row(
                                                Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                QUICK.forEach { q ->
                                                    Pill(q.label, flags[q.key] == q.value) { toggleKey(q.key, q.value) }
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        Panel {
                                            SectionTitle("Add custom flag")
                                            OutlinedTextField(
                                                k, { k = it }, label = { Text("Name") },
                                                singleLine = true, shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    v, { v = it }, label = { Text("Value") }, singleLine = true,
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                PressButton("Add", {
                                                    if (k.isNotBlank()) {
                                                        flags[k.trim()] = v.trim(); k = ""; v = ""; save()
                                                    }
                                                })
                                            }
                                        }
                                    }
                                    item { SectionTitle("Your flags (${filtered.size})") }
                                    if (filtered.isEmpty()) {
                                        item {
                                            Text(
                                                "No flags yet. Tap a chip above or add one.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    items(filtered, key = { it }) { name ->
                                        val value = flags[name] ?: ""
                                        val isBool = value.equals("true", true) || value.equals("false", true)
                                        Panel {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    name, Modifier.weight(1f), fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium
                                                )
                                                if (isBool) {
                                                    Switch(
                                                        checked = value.equals("true", true),
                                                        onCheckedChange = {
                                                            flags[name] = if (it) "True" else "False"; save()
                                                        }
                                                    )
                                                }
                                                TextButton({ flags.remove(name); save() }) { Text("✕") }
                                            }
                                            if (!isBool) {
                                                OutlinedTextField(
                                                    value, { flags[name] = it; save() },
                                                    singleLine = true, shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // ================= PRESETS =================
                            2 -> LazyColumn(
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
                                                pn, { pn = it }, label = { Text("Preset name") },
                                                singleLine = true, shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                            PressButton("Save", {
                                                if (pn.isNotBlank()) {
                                                    presets[pn.trim()] = toJson(flags); pn = ""; save()
                                                }
                                            })
                                        }
                                        Row(
                                            Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            PressButton("Import clipboard", {
                                                val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                                status = runCatching {
                                                    flags.putAll(parseFlags(text)); save(); "Imported from clipboard."
                                                }.getOrElse { "Clipboard isn't valid JSON." }
                                            }, outlined = true)
                                            PressButton("Copy JSON", {
                                                clip.setPrimaryClip(ClipData.newPlainText("flags", toJson(flags)))
                                                status = "Copied JSON."
                                            }, outlined = true)
                                            PressButton("Share", {
                                                val i = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, toJson(flags))
                                                }
                                                ctx.startActivity(Intent.createChooser(i, "Share flags"))
                                            }, outlined = true)
                                        }
                                        if (status.isNotEmpty()) {
                                            Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                item { SectionTitle("Saved presets (${presets.size})") }
                                if (presets.isEmpty()) {
                                    item {
                                        Text("No presets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                items(presets.keys.sorted(), key = { it }) { name ->
                                    val count = runCatching { parseFlags(presets[name] ?: "{}").size }.getOrDefault(0)
                                    Panel {
                                        Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(
                                            "$count flags", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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

                            // ================= THEME =================
                            3 -> LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Panel {
                                        SectionTitle("Accent theme")
                                        Row(
                                            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            THEMES.forEachIndexed { i, th ->
                                                val sel = themeIdx == i
                                                val s by animateFloatAsState(
                                                    if (sel) 1.12f else 1f,
                                                    spring(dampingRatio = 0.5f), label = "sw"
                                                )
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.clickable { themeIdx = i; save() }
                                                ) {
                                                    Box(
                                                        Modifier.size(56.dp)
                                                            .graphicsLayer { scaleX = s; scaleY = s }
                                                            .clip(CircleShape)
                                                            .background(Brush.linearGradient(listOf(th.primary, th.secondary)))
                                                            .then(
                                                                if (sel) Modifier.border(3.dp, Color.White, CircleShape)
                                                                else Modifier
                                                            )
                                                    )
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(th.name, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                item {
                                    Panel {
                                        ToggleRow(
                                            "Animated background", "Slow moving glow behind the app.", animBg
                                        ) { animBg = it; save() }
                                    }
                                }
                            }

                            // ================= SETTINGS =================
                            else -> LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Panel {
                                        SectionTitle("Flag file")
                                        OutlinedTextField(
                                            path, { path = it; save() },
                                            label = { Text("ClientAppSettings.json path") },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        PressButton(
                                            "Reset path to default",
                                            { path = DEFAULT_PATH; save() },
                                            outlined = true
                                        )
                                    }
                                }
                                item {
                                    Panel {
                                        SectionTitle("Behaviour")
                                        ToggleRow(
                                            "Restart Roblox after Apply",
                                            "Force-stops and relaunches Roblox so flags load.", restart
                                        ) { restart = it; save() }
                                        ToggleRow(
                                            "Disable frame-rate cap",
                                            "Sets a system property when you Apply.", fps
                                        ) { fps = it; save() }
                                    }
                                }
                                item {
                                    Panel {
                                        SectionTitle("Maintenance")
                                        PressButton(
                                            "Clear Roblox cache",
                                            { scope.launch(Dispatchers.IO) { status = clearCache() } },
                                            outlined = true
                                        )
                                        if (status.isNotEmpty()) {
                                            Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                item {
                                    Panel {
                                        SectionTitle("About")
                                        Text(
                                            "TacoStrap v2.0. Layout inspired by Bloxstrap, Fishstrap and Voidstrap.",
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
}
