package com.example.flagmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.Base64

private const val DEFAULT_PKG = "com.roblox.client"
private const val DEFAULT_PATH = "/data/local/tmp/ClientAppSettings.json"
private const val FPS_PROP = "debug.graphics.game_default_frame_rate.disabled"
private const val ALLOWLIST_URL =
    "https://devforum.roblox.com/t/allowlist-for-local-client-configuration-via-fast-flags/3966569"

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

fun suSh(cmd: String): Pair<Boolean, String> = try {
    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
    val out = p.inputStream.bufferedReader().readText()
    val err = p.errorStream.bufferedReader().readText()
    Pair(p.waitFor() == 0, (out + err).trim())
} catch (e: Throwable) {
    Pair(false, e.message ?: e.toString())
}

fun runCmd(cmd: String, root: Boolean): Pair<Boolean, String> = if (root) suSh(cmd) else sh(cmd)

fun applyFlags(json: String, path: String, fps: Boolean, root: Boolean): String {
    val b64 = Base64.getEncoder().encodeToString(json.toByteArray())
    val dir = path.substringBeforeLast('/')
    val cmd = buildString {
        append("mkdir -p '$dir' && echo '$b64' | base64 -d > '$path' && chmod 644 '$path'")
        if (fps) append(" && setprop $FPS_PROP true")
    }
    val (ok, msg) = runCmd(cmd, root)
    return if (ok) "Applied to $path" else "Failed: $msg"
}

fun resetFlags(path: String, pkg: String, root: Boolean): String {
    val (ok, msg) = runCmd("rm -f '$path'; setprop $FPS_PROP false; am force-stop $pkg", root)
    return if (ok) "Removed flag file. Reopen Roblox." else "Failed: $msg"
}

fun launchPkg(pkg: String, root: Boolean): String {
    val (ok, msg) = runCmd("monkey -p $pkg -c android.intent.category.LAUNCHER 1", root)
    return if (ok) "Launched Roblox." else "Failed: $msg"
}

fun launchGame(id: String, root: Boolean): String {
    val (ok, msg) = runCmd("am start -a android.intent.action.VIEW -d 'roblox://placeId=$id'", root)
    return if (ok) "Launching game $id." else "Failed: $msg"
}

fun clearCache(pkg: String, root: Boolean): String {
    val (ok, msg) = runCmd("rm -rf /sdcard/Android/data/$pkg/cache/*", root)
    return if (ok) "Cleared Roblox cache." else "Failed: $msg"
}

// ---------- Models ----------

private data class AppTheme(val name: String, val primary: Color, val secondary: Color)

private val THEMES = listOf(
    AppTheme("Taco", Color(0xFFFFB300), Color(0xFFFF7A1A)),
    AppTheme("Void", Color(0xFFB388FF), Color(0xFF7C4DFF)),
    AppTheme("Blox", Color(0xFF4FC3F7), Color(0xFF1E88E5)),
    AppTheme("Fish", Color(0xFF26D0B8), Color(0xFF00ACC1)),
    AppTheme("Frost", Color(0xFFB3E5FC), Color(0xFF81D4FA)),
    AppTheme("Rose", Color(0xFFFF8FAB), Color(0xFFE91E63))
)

private data class Opt(val label: String, val sets: Map<String, String>)
private data class Pack(val name: String, val desc: String, val sets: Map<String, String>)

private sealed class FS {
    data class Toggle(val title: String, val sub: String, val sets: Map<String, String>) : FS()
    data class Choice(val title: String, val sub: String, val opts: List<Opt>) : FS()
    data class Num(val title: String, val sub: String, val key: String) : FS()
    data class Range(
        val title: String, val sub: String,
        val a: String, val b: String, val da: String, val db: String
    ) : FS()
}

private fun texSet(n: String) = mapOf(
    "DFFlagTextureQualityOverrideEnabled" to "True",
    "DFIntTextureQualityOverride" to n
)

private val FS_TEXTURE = FS.Choice(
    "Texture quality", "Sharper textures use more memory",
    listOf(
        Opt("Automatic", emptyMap()),
        Opt("Lowest", texSet("0")),
        Opt("Low", texSet("1")),
        Opt("Medium", texSet("2")),
        Opt("High", texSet("3"))
    )
)
private val FS_AA = FS.Choice(
    "Anti aliasing", "Smooths jagged edges",
    listOf(
        Opt("Automatic", emptyMap()),
        Opt("Off", mapOf("FIntDebugForceMSAASamples" to "1")),
        Opt("2x", mapOf("FIntDebugForceMSAASamples" to "2")),
        Opt("4x", mapOf("FIntDebugForceMSAASamples" to "4")),
        Opt("8x", mapOf("FIntDebugForceMSAASamples" to "8"))
    )
)
private val FS_RENDER = FS.Choice(
    "Rendering mode", "Graphics engine Roblox uses",
    listOf(
        Opt("Automatic", emptyMap()),
        Opt("Vulkan", mapOf("FFlagDebugGraphicsPreferVulkan" to "True")),
        Opt("OpenGL", mapOf("FFlagDebugGraphicsPreferOpenGL" to "True"))
    )
)
private val FS_SKY = FS.Toggle("Gray sky", "Replaces the sky with plain gray", mapOf("FFlagDebugSkyGray" to "True"))
private val FS_SHADOWS = FS.Toggle("Disable shadows", "Turns off Roblox shadows", mapOf("FIntRenderShadowIntensity" to "0"))
private val FS_POSTFX = FS.Toggle("Disable post-processing", "Turns off bloom and color effects", mapOf("FFlagDisablePostFx" to "True"))
private val FS_FPS = FS.Choice(
    "Frame rate limit", "Above 60 turns off vsync and the Android game frame rate cap",
    listOf(Opt("Default", emptyMap())) +
        listOf(30, 60, 90, 120, 144, 165, 240).map { Opt("$it FPS", mapOf("DFIntTaskSchedulerTargetFps" to "$it")) }
)
private val FS_FPSCOUNT = FS.Toggle("FPS counter", "Shows frame rate in the corner", mapOf("FFlagDebugDisplayFPS" to "True"))
private val FS_QUALITY = FS.Choice(
    "Graphics quality override", "Locks the Roblox graphics level",
    listOf(Opt("Default", emptyMap())) +
        listOf(1, 3, 5, 7, 10).map { Opt("Level $it", mapOf("FIntRomarkStartWithGraphicQualityLevel" to "$it")) }
)
private val FS_GRASSMOVE = FS.Choice(
    "Grass movement", "How much grass sways",
    listOf(
        Opt("Default", emptyMap()),
        Opt("None", mapOf("FIntGrassMovementReducedMotionFactor" to "0"))
    )
)
private val FS_NOGRASS = FS.Toggle(
    "Remove grass", "Hides the built in terrain grass",
    mapOf("FIntFRMMinGrassDistance" to "0", "FIntFRMMaxGrassDistance" to "0")
)
private val FS_GRASSDIST = FS.Range(
    "Grass distance", "Set both to 0 to hide grass",
    "FIntFRMMinGrassDistance", "FIntFRMMaxGrassDistance", "100", "290"
)
private val FS_BLUR = FS.Toggle("Remove blur", "No blur behind Roblox menus", mapOf("FIntRobloxGuiBlurIntensity" to "0"))
private val FS_PRELOAD = FS.Toggle(
    "Faster loading", "Preloads more assets to load games faster",
    mapOf("DFIntNumAssetsMaxToPreload" to "2147483647", "DFIntAssetPreloading" to "2147483647")
)
private val FS_MTU = FS.Num("MTU size", "Bytes sent per network packet", "DFIntConnectionMTUSize")

private val SECTIONS: List<Pair<String, List<FS>>> = listOf(
    "Graphics" to listOf(FS_TEXTURE, FS_AA, FS_RENDER, FS_SKY, FS_SHADOWS, FS_POSTFX),
    "Performance" to listOf(FS_FPS, FS_FPSCOUNT),
    "World detail" to listOf(FS_QUALITY, FS_GRASSMOVE, FS_NOGRASS, FS_GRASSDIST),
    "Interface" to listOf(FS_BLUR),
    "Network" to listOf(FS_PRELOAD, FS_MTU)
)

private val PACKS = listOf(
    Pack(
        "Balanced, recommended", "Medium textures and 2x anti aliasing",
        texSet("2") + mapOf("FIntDebugForceMSAASamples" to "2")
    ),
    Pack(
        "Performance", "Lowest textures, no shadows or post-processing",
        texSet("0") + mapOf(
            "FIntDebugForceMSAASamples" to "1",
            "FIntRenderShadowIntensity" to "0",
            "FFlagDisablePostFx" to "True"
        )
    ),
    Pack(
        "Quality", "Sharp textures and 4x anti aliasing",
        texSet("3") + mapOf("FIntDebugForceMSAASamples" to "4")
    )
)

private val LocalCorner = compositionLocalOf { 28.dp }

// ---------- Reusable UI ----------

@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 6 }
    ) { content() }
}

@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(LocalCorner.current))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .animateContentSize()
            .padding(vertical = 8.dp),
        content = content
    )
}

@Composable
private fun Item(
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val m = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        Modifier.fillMaxWidth().then(m).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp)
            if (sub != null) {
                Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun SwitchItem(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Item(title, sub, { onChange(!checked) }) { Switch(checked, onChange) }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 6.dp)
    )
}

@Composable
private fun PageHeader(title: String, sub: String? = null) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp)) {
        Text(title, fontSize = 34.sp)
        if (sub != null) Text(sub, fontSize = 17.sp)
    }
}

@Composable
private fun ChoiceBox(current: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(current, fontSize = 14.sp, maxLines = 1, modifier = Modifier.widthIn(max = 140.dp))
            Spacer(Modifier.width(6.dp))
            Text("▾", fontSize = 12.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(i) })
            }
        }
    }
}

@Composable
private fun NumBox(
    value: String,
    hint: String,
    modifier: Modifier = Modifier.width(100.dp),
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value, onChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(hint, fontSize = 14.sp) },
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
    )
}

@Composable
private fun PressButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: Int = 0) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "press"
    )
    val m = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    val shape = RoundedCornerShape(50)
    when (style) {
        0 -> Button(onClick = onClick, modifier = m, interactionSource = src, shape = shape) {
            Text(text, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 15.sp)
        }
        1 -> Button(
            onClick = onClick, modifier = m, interactionSource = src, shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) { Text(text, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 15.sp) }
        else -> OutlinedButton(onClick = onClick, modifier = m, interactionSource = src, shape = shape) {
            Text(text, fontWeight = FontWeight.Medium, maxLines = 1, fontSize = 15.sp)
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
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)) {
        Text(
            text, Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = color)
    }
}

@Composable
private fun TabStrip(items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        items.forEachIndexed { i, (label, icon) ->
            val sel = i == selected
            val c by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                tween(250), label = "tabc"
            )
            val w by animateDpAsState(if (sel) 44.dp else 0.dp, tween(300), label = "tabw")
            Column(
                Modifier.clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(icon, fontSize = 22.sp)
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.width(w).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun FlagSetting(fs: FS, flags: MutableMap<String, String>, onChange: () -> Unit) {
    when (fs) {
        is FS.Toggle -> {
            val on = fs.sets.all { (k, v) -> flags[k] == v }
            SwitchItem(fs.title, fs.sub, on) { want ->
                if (want) flags.putAll(fs.sets) else fs.sets.keys.forEach { flags.remove(it) }
                onChange()
            }
        }
        is FS.Choice -> {
            val managed = fs.opts.flatMap { it.sets.keys }.toSet()
            val idx = fs.opts.indexOfFirst { o ->
                if (o.sets.isEmpty()) managed.none { flags.containsKey(it) }
                else o.sets.all { (k, v) -> flags[k] == v }
            }
            val cur = if (idx >= 0) fs.opts[idx].label else "Custom"
            Item(fs.title, fs.sub) {
                ChoiceBox(cur, fs.opts.map { it.label }) { i ->
                    managed.forEach { flags.remove(it) }
                    flags.putAll(fs.opts[i].sets)
                    onChange()
                }
            }
        }
        is FS.Num -> {
            Item(fs.title, fs.sub) {
                NumBox(flags[fs.key] ?: "", "Off") { t ->
                    if (t.isBlank()) flags.remove(fs.key) else flags[fs.key] = t.trim()
                    onChange()
                }
            }
        }
        is FS.Range -> {
            Item(fs.title, fs.sub) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumBox(flags[fs.a] ?: "", fs.da, Modifier.width(76.dp)) { t ->
                        if (t.isBlank()) flags.remove(fs.a) else {
                            flags[fs.a] = t.trim()
                            if (flags[fs.b] == null) flags[fs.b] = fs.db
                        }
                        onChange()
                    }
                    Text("  to  ", fontSize = 13.sp)
                    NumBox(flags[fs.b] ?: "", fs.db, Modifier.width(76.dp)) { t ->
                        if (t.isBlank()) flags.remove(fs.b) else {
                            flags[fs.b] = t.trim()
                            if (flags[fs.a] == null) flags[fs.a] = fs.da
                        }
                        onChange()
                    }
                }
            }
        }
    }
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
    val snackbar = remember { SnackbarHostState() }

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
    val games = remember {
        mutableStateMapOf<String, String>().apply {
            runCatching { putAll(parseFlags(prefs.getString("games", "{}")!!)) }
        }
    }

    var path by remember { mutableStateOf(prefs.getString("path2", DEFAULT_PATH)!!) }
    var pkg by remember { mutableStateOf(prefs.getString("pkg", DEFAULT_PKG)!!) }
    var restart by remember { mutableStateOf(prefs.getBoolean("restart", true)) }
    var fps by remember { mutableStateOf(prefs.getBoolean("fps", false)) }
    var useRoot by remember { mutableStateOf(prefs.getBoolean("root", false)) }
    var applyOnSave by remember { mutableStateOf(prefs.getBoolean("applyOnSave", true)) }
    var themeIdx by remember { mutableStateOf(prefs.getInt("theme", 0).coerceIn(0, THEMES.size - 1)) }
    var animBg by remember { mutableStateOf(prefs.getBoolean("animbg", true)) }
    var glowAmt by remember { mutableStateOf(prefs.getFloat("glow", 0.22f)) }
    var cornerDp by remember { mutableStateOf(prefs.getFloat("corner", 28f)) }
    var msgFlag by remember { mutableStateOf(prefs.getBoolean("msgFlag", true)) }
    var msgLaunch by remember { mutableStateOf(prefs.getBoolean("msgLaunch", true)) }
    var msgOther by remember { mutableStateOf(prefs.getBoolean("msgOther", true)) }

    var shStatus by remember { mutableStateOf(shizukuStatus()) }
    var robloxVersion by remember { mutableStateOf("-") }

    var page by remember { mutableStateOf(0) }
    var modsTab by remember { mutableStateOf(0) }
    var setTab by remember { mutableStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var edTab by remember { mutableStateOf(0) }
    var quickIdx by remember { mutableStateOf(0) }

    var search by remember { mutableStateOf("") }
    var k by remember { mutableStateOf("") }
    var v by remember { mutableStateOf("") }
    var pn by remember { mutableStateOf("") }
    var gn by remember { mutableStateOf("") }
    var gid by remember { mutableStateOf("") }
    var jsonText by remember { mutableStateOf("") }
    var jsonMsg by remember { mutableStateOf("") }
    var fileOut by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var pkgDialog by remember { mutableStateOf(false) }
    var pkgInput by remember { mutableStateOf("") }
    var pkgMenu by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }

    fun save() {
        prefs.edit()
            .putString("flags", toJson(flags))
            .putString("presets", toJson(presets))
            .putString("games", toJson(games))
            .putString("path2", path)
            .putString("pkg", pkg)
            .putBoolean("restart", restart)
            .putBoolean("fps", fps)
            .putBoolean("root", useRoot)
            .putBoolean("applyOnSave", applyOnSave)
            .putInt("theme", themeIdx)
            .putBoolean("animbg", animBg)
            .putFloat("glow", glowAmt)
            .putFloat("corner", cornerDp)
            .putBoolean("msgFlag", msgFlag)
            .putBoolean("msgLaunch", msgLaunch)
            .putBoolean("msgOther", msgOther)
            .apply()
    }

    fun toast(kind: String, msg: String) {
        val allowed = when (kind) {
            "flag" -> msgFlag
            "launch" -> msgLaunch
            else -> msgOther
        }
        if (allowed || msg.startsWith("Failed")) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg)
            }
        }
    }

    fun doSave(launch: Boolean) {
        save()
        scope.launch(Dispatchers.IO) {
            val msg = if (applyOnSave) applyFlags(toJson(flags), path, fps, useRoot) else "Saved in app."
            toast("flag", msg)
            if (launch && !msg.startsWith("Failed")) {
                if (restart) runCmd("am force-stop $pkg && sleep 1", useRoot)
                toast("launch", launchPkg(pkg, useRoot))
            }
        }
    }

    fun openUrl(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun backupJson(): String = JSONObject().apply {
        put("flags", JSONObject(flags.toMap()))
        put("presets", JSONObject(presets.toMap()))
        put("games", JSONObject(games.toMap()))
    }.toString(2)

    fun share(text: String, title: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, title))
    }

    LaunchedEffect(Unit) {
        while (true) {
            shStatus = shizukuStatus()
            delay(2000)
        }
    }

    val ready = shStatus == "Shizuku ready"

    LaunchedEffect(ready, useRoot, pkg) {
        if (ready || useRoot) {
            val (ok, out) = withContext(Dispatchers.IO) {
                runCmd("dumpsys package $pkg | grep -m1 versionName", useRoot)
            }
            robloxVersion = if (ok && out.contains("=")) out.substringAfter("=").trim() else "Not found"
        }
    }

    // ----- Theme -----
    val theme = THEMES[themeIdx]
    val pc by animateColorAsState(theme.primary, tween(600), label = "pc")
    val sc by animateColorAsState(theme.secondary, tween(600), label = "sc")
    val base = Color(0xFF151518)
    val scheme = darkColorScheme(
        primary = pc,
        onPrimary = Color(0xFF16120A),
        primaryContainer = lerp(base, pc, 0.28f),
        onPrimaryContainer = lerp(pc, Color.White, 0.65f),
        secondary = sc,
        onSecondary = Color(0xFF16120A),
        secondaryContainer = lerp(Color(0xFF2A2A30), pc, 0.22f),
        onSecondaryContainer = lerp(pc, Color.White, 0.4f),
        background = base,
        onBackground = Color(0xFFEDE8E3),
        surface = base,
        onSurface = Color(0xFFEDE8E3),
        surfaceVariant = lerp(Color(0xFF242427), pc, 0.05f),
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
    val flagCount by animateIntAsState(flags.size, tween(600), label = "count")
    val shColor by animateColorAsState(
        if (ready) Color(0xFF4CAF50) else Color(0xFFEF5350), tween(400), label = "shc"
    )
    val navItems = listOf("Home" to "🌮", "Library" to "🎮", "Mods" to "🛠️", "FastFlags" to "🚩", "Settings" to "⚙️")

    // ================= PAGES =================

    @Composable
    fun ProfilesUI() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Group {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Save current flags as a profile", fontSize = 17.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            pn, { pn = it }, label = { Text("Profile name") }, singleLine = true,
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                        )
                        PressButton("Save", {
                            if (pn.isNotBlank()) {
                                presets[pn.trim()] = toJson(flags); pn = ""; save()
                                toast("flag", "Profile saved.")
                            }
                        })
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PressButton("Import clipboard", {
                            val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            val msg = runCatching {
                                flags.putAll(parseFlags(text)); save(); "Imported from clipboard."
                            }.getOrElse { "Failed: clipboard isn't valid JSON." }
                            toast("flag", msg)
                        }, style = 2)
                        PressButton("Copy JSON", {
                            clip.setPrimaryClip(ClipData.newPlainText("flags", toJson(flags)))
                            toast("other", "Copied JSON.")
                        }, style = 2)
                        PressButton("Share", { share(toJson(flags), "Share flags") }, style = 2)
                    }
                }
            }
            if (presets.isEmpty()) {
                Group { Item("No profiles yet", "Save one above.") }
            }
            presets.keys.sorted().forEach { name ->
                val count = runCatching { parseFlags(presets[name] ?: "{}").size }.getOrDefault(0)
                Group {
                    Item(name, "$count flags")
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({
                            runCatching {
                                flags.clear(); flags.putAll(parseFlags(presets[name]!!)); save()
                                toast("flag", "Loaded $name.")
                            }
                        }) { Text("Load") }
                        TextButton({ renaming = name; newName = name }) { Text("Rename") }
                        TextButton({ presets.remove(name); save() }) { Text("Delete") }
                    }
                }
            }
        }
    }

    @Composable
    fun HomePage() {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageHeader("Home", "Welcome to TacoStrap.")
            Appear(1) {
                Group {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painterResource(id = R.mipmap.ic_launcher), "TacoStrap logo",
                            Modifier.size(64.dp).graphicsLayer { scaleX = breath; scaleY = breath }
                                .clip(RoundedCornerShape(18.dp))
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("TacoStrap", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Taco edition v3.0", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("Roblox", robloxVersion, Modifier.weight(1f))
                        Stat("Active flags", "$flagCount", Modifier.weight(1f))
                    }
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(if (ready) "Shizuku ready" else "Shizuku off", shColor)
                        StatusChip(if (useRoot) "Root on" else "Root off", if (useRoot) Color(0xFF4CAF50) else Color(0xFF8D8D93))
                    }
                }
            }
            Appear(2) {
                PressButton(
                    "Launch Roblox",
                    { scope.launch(Dispatchers.IO) { toast("launch", launchPkg(pkg, useRoot)) } },
                    Modifier.fillMaxWidth().height(58.dp)
                )
            }
            Appear(3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Pinned games")
                    Group {
                        if (games.isEmpty()) {
                            Item("No pinned games", "Add games in the Library tab.", { page = 1 })
                        }
                        games.keys.sorted().take(4).forEach { name ->
                            Item(name, "Place ID ${games[name]}", {
                                scope.launch(Dispatchers.IO) { toast("launch", launchGame(games[name] ?: "", useRoot)) }
                            }) { Text("Play", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Appear(4) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Status")
                    Group {
                        Item("Shizuku", shStatus) {
                            PressButton("Grant", {
                                try { Shizuku.requestPermission(1001) } catch (e: Throwable) { toast("other", "Failed: start Shizuku first.") }
                                shStatus = shizukuStatus()
                            }, style = 1)
                        }
                        Item("Flag file", path)
                    }
                }
            }
        }
    }

    @Composable
    fun LibraryPage() {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageHeader("Library", "Pin games and jump straight in.")
            Appear(1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Add a game")
                    Group {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                gn, { gn = it }, label = { Text("Name") }, singleLine = true,
                                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    gid, { gid = it.filter { c -> c.isDigit() } }, label = { Text("Place ID") },
                                    singleLine = true, shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                PressButton("Add", {
                                    if (gn.isNotBlank() && gid.isNotBlank()) {
                                        games[gn.trim()] = gid; gn = ""; gid = ""; save()
                                        toast("other", "Game added.")
                                    }
                                })
                            }
                        }
                    }
                }
            }
            Appear(2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Pinned (${games.size})")
                    Group {
                        if (games.isEmpty()) Item("No games yet", "Add one above.")
                        games.keys.sorted().forEach { name ->
                            Item(name, "Place ID ${games[name]}", {
                                scope.launch(Dispatchers.IO) { toast("launch", launchGame(games[name] ?: "", useRoot)) }
                            }) {
                                Row {
                                    TextButton({
                                        scope.launch(Dispatchers.IO) { toast("launch", launchGame(games[name] ?: "", useRoot)) }
                                    }) { Text("Play") }
                                    TextButton({ games.remove(name); save() }) { Text("✕") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ModsPage() {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageHeader("Mods", "Tweak Roblox with packs, profiles and your own files.")
            Appear(1) {
                Group {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("ⓘ", fontSize = 28.sp, color = Color(0xFFFFD54F))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Write flags to Roblox", fontSize = 20.sp)
                            Text(
                                "Writes your flags to the flag file when you tap Save. Roblox on Android only reads files from its own app package, so root may be needed.",
                                fontSize = 14.sp
                            )
                        }
                        Switch(applyOnSave, { applyOnSave = it; save() })
                    }
                    Item("Roblox allowlist", "Flags Roblox allows on PC", { openUrl(ALLOWLIST_URL) }) { Text("›", fontSize = 24.sp) }
                }
            }
            Appear(2) {
                TabStrip(
                    listOf("Customize" to "🛠️", "Packs" to "⬇️", "My Mods" to "🧩", "Files" to "📁"),
                    modsTab
                ) { modsTab = it }
            }
            Crossfade(targetState = modsTab, animationSpec = tween(250), label = "modsTab") { t ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (t) {
                        0 -> {
                            SectionLabel("Display")
                            Group {
                                FlagSetting(FS_FPSCOUNT, flags) { save() }
                                FlagSetting(FS_BLUR, flags) { save() }
                                FlagSetting(FS_SKY, flags) { save() }
                            }
                            SectionLabel("Performance")
                            Group {
                                FlagSetting(FS_FPS, flags) { save() }
                                FlagSetting(FS_QUALITY, flags) { save() }
                            }
                        }
                        1 -> {
                            SectionLabel("Flag packs")
                            Group {
                                PACKS.forEach { p ->
                                    Item(p.name, p.desc) {
                                        PressButton("Apply", {
                                            flags.putAll(p.sets); save()
                                            toast("flag", "Added ${p.name} flags.")
                                        }, style = 1)
                                    }
                                }
                            }
                        }
                        2 -> ProfilesUI()
                        else -> {
                            SectionLabel("Flag file")
                            Group {
                                Item("Path", path)
                                Item("View file", "Shows the file Roblox would read", {
                                    scope.launch(Dispatchers.IO) {
                                        val (_, out) = runCmd("ls -l '$path'; cat '$path'", useRoot)
                                        fileOut = out.ifEmpty { "Nothing returned." }
                                    }
                                }) { Text("›", fontSize = 24.sp) }
                                Item("Delete flag file", "Removes the file and restarts nothing", {
                                    scope.launch(Dispatchers.IO) { toast("flag", resetFlags(path, pkg, useRoot)) }
                                }) { Text("›", fontSize = 24.sp) }
                                Item("Clear Roblox cache", "Frees up space", {
                                    scope.launch(Dispatchers.IO) { toast("other", clearCache(pkg, useRoot)) }
                                }) { Text("›", fontSize = 24.sp) }
                            }
                            if (fileOut.isNotEmpty()) {
                                Group {
                                    Text(
                                        fileOut, Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                        fontFamily = FontFamily.Monospace, fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun FlagsPage() {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageHeader("FastFlag Settings", "Presets for your Default profile.")
            Appear(1) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠", fontSize = 24.sp, color = Color(0xFFFFD54F))
                    Spacer(Modifier.width(12.dp))
                    Text("Roblox may ignore flags outside its allowlist.", Modifier.weight(1f), fontSize = 14.sp)
                    TextButton({ showEditor = true }) {
                        Text("Open FastFlag Editor", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            SECTIONS.forEachIndexed { idx, (title, list) ->
                Appear(2 + idx) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(title)
                        Group { list.forEach { FlagSetting(it, flags) { save() } } }
                    }
                }
            }
            Appear(8) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Quick setup")
                    Group {
                        Item("Recommended", PACKS[quickIdx].desc)
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ChoiceBox(PACKS[quickIdx].name, PACKS.map { it.name }) { quickIdx = it }
                            PressButton("Apply", {
                                flags.putAll(PACKS[quickIdx].sets); save()
                                toast("flag", "Added ${PACKS[quickIdx].name} flags.")
                            })
                        }
                        Item("Roblox allowlist", "Flags Roblox allows on PC", { openUrl(ALLOWLIST_URL) }) {
                            Text("›", fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsPage() {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageHeader("Settings")
            TabStrip(
                listOf("Appearance" to "🎚️", "Notifications" to "🔔", "Roblox" to "🎮", "Backup" to "📁", "About" to "ℹ️"),
                setTab
            ) { setTab = it }
            Crossfade(targetState = setTab, animationSpec = tween(250), label = "setTab") { t ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (t) {
                        0 -> {
                            Group {
                                Item("Accent color", THEMES[themeIdx].name) {
                                    ChoiceBox(THEMES[themeIdx].name, THEMES.map { it.name }) { themeIdx = it; save() }
                                }
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    THEMES.forEachIndexed { i, th ->
                                        val sel = themeIdx == i
                                        val s by animateFloatAsState(
                                            if (sel) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "sw"
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable { themeIdx = i; save() }
                                        ) {
                                            Box(
                                                Modifier.size(52.dp)
                                                    .graphicsLayer { scaleX = s; scaleY = s }
                                                    .clip(CircleShape)
                                                    .background(Brush.linearGradient(listOf(th.primary, th.secondary)))
                                                    .then(if (sel) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(th.name, fontSize = 12.sp)
                                        }
                                    }
                                }
                                SwitchItem("Animated background", "Slow moving glow behind the app.", animBg) { animBg = it; save() }
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                    Text("Background glow", fontSize = 17.sp)
                                    Slider(
                                        value = glowAmt, onValueChange = { glowAmt = it },
                                        valueRange = 0f..0.5f, onValueChangeFinished = { save() }
                                    )
                                }
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                    Text("Corner roundness", fontSize = 17.sp)
                                    Slider(
                                        value = cornerDp, onValueChange = { cornerDp = it },
                                        valueRange = 8f..40f, onValueChangeFinished = { save() }
                                    )
                                }
                            }
                        }
                        1 -> {
                            SectionLabel("In app messages")
                            Group {
                                Item("Errors always show, whatever you turn off here.")
                                SwitchItem("FastFlag messages", "Confirms when flags are saved, applied or imported.", msgFlag) { msgFlag = it; save() }
                                SwitchItem("Launch messages", "Confirms when Roblox or a game is launched.", msgLaunch) { msgLaunch = it; save() }
                                SwitchItem("Other messages", "Confirms copies, backups and other actions.", msgOther) { msgOther = it; save() }
                            }
                        }
                        2 -> {
                            Group {
                                Item("Roblox", if (robloxVersion == "Not found") "Not found" else "Installed · version $robloxVersion")
                                Item("Package name", pkg, { pkgInput = pkg; pkgDialog = true }) { Text("›", fontSize = 24.sp) }
                                Item("Shizuku", shStatus) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PressButton("Grant", {
                                            try { Shizuku.requestPermission(1001) } catch (e: Throwable) { toast("other", "Failed: start Shizuku first.") }
                                            shStatus = shizukuStatus()
                                        }, style = 1)
                                    }
                                }
                                SwitchItem(
                                    "Use root", "Apply FastFlags and mods with root. Turn this on to ask your root manager for permission.",
                                    useRoot
                                ) { useRoot = it; save() }
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        path, { path = it; save() }, label = { Text("Flag file path") },
                                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Pill("Default", path == DEFAULT_PATH) { path = DEFAULT_PATH; save() }
                                        val appPath = "/data/data/$pkg/files/ClientSettings/ClientAppSettings.json"
                                        Pill("App data (root, unverified)", path == appPath) { path = appPath; save() }
                                    }
                                }
                                SwitchItem("Restart Roblox on Save and Launch", "Force-stops Roblox before launching.", restart) { restart = it; save() }
                                SwitchItem("Disable frame-rate cap", "Sets a system property when you save.", fps) { fps = it; save() }
                                Item("Clear Roblox cache", "Frees up space", {
                                    scope.launch(Dispatchers.IO) { toast("other", clearCache(pkg, useRoot)) }
                                }) { Text("›", fontSize = 24.sp) }
                            }
                        }
                        3 -> {
                            Group {
                                Item("Save your pinned games, FastFlag profiles and flags to a file, or restore them on another device.")
                                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PressButton("Export backup", { share(backupJson(), "Export backup") }, style = 1)
                                    PressButton("Import backup", {
                                        val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                        val msg = runCatching {
                                            val o = JSONObject(text)
                                            o.optJSONObject("flags")?.let { j -> flags.clear(); flags.putAll(parseFlags(j.toString())) }
                                            o.optJSONObject("presets")?.let { j -> presets.clear(); presets.putAll(parseFlags(j.toString())) }
                                            o.optJSONObject("games")?.let { j -> games.clear(); games.putAll(parseFlags(j.toString())) }
                                            save()
                                            "Backup imported."
                                        }.getOrElse { "Failed: copy a backup to the clipboard first." }
                                        toast("other", msg)
                                    }, style = 1)
                                }
                            }
                        }
                        else -> {
                            Group {
                                Item("Diagnostics", "A report you can copy or share for support", {
                                    val report = "TacoStrap 3.0\nAndroid ${Build.VERSION.SDK_INT}\n$shStatus\nRoot: $useRoot\nPackage: $pkg ($robloxVersion)\nPath: $path\nFlags: ${flags.size}"
                                    clip.setPrimaryClip(ClipData.newPlainText("report", report))
                                    toast("other", "Copied diagnostics.")
                                }) { Text("›", fontSize = 24.sp) }
                                Item("Licenses", "Open source notices", { licenses = true }) { Text("›", fontSize = 24.sp) }
                                Item("Roblox allowlist", "Flags Roblox allows on PC", { openUrl(ALLOWLIST_URL) }) { Text("›", fontSize = 24.sp) }
                            }
                            Text(
                                "TacoStrap for Android 3.0 · Taco edition",
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EditorScreen() {
        val filtered = flags.keys.filter { it.contains(search, ignoreCase = true) }.sorted()
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding().navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ showEditor = false }) { Text("←", fontSize = 26.sp) }
                Text("FastFlag Editor", fontSize = 26.sp, modifier = Modifier.weight(1f))
                TextButton({ confirmClear = true }) { Text("Clear all") }
            }
            TabStrip(listOf("Flags" to "🚩", "JSON" to "🧾", "Profiles" to "💾"), edTab) { edTab = it }
            when (edTab) {
                0 -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            search, { search = it }, label = { Text("Search flags") }, singleLine = true,
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Group {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Add custom flag", fontSize = 17.sp)
                                OutlinedTextField(
                                    k, { k = it }, label = { Text("Name") }, singleLine = true,
                                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        v, { v = it }, label = { Text("Value") }, singleLine = true,
                                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                                    )
                                    PressButton("Add", {
                                        if (k.isNotBlank()) { flags[k.trim()] = v.trim(); k = ""; v = ""; save() }
                                    })
                                }
                            }
                        }
                    }
                    item { SectionLabel("Your flags (${filtered.size})") }
                    if (filtered.isEmpty()) {
                        item { Text("No flags yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(filtered, key = { it }) { name ->
                        val value = flags[name] ?: ""
                        val isBool = value.equals("true", true) || value.equals("false", true)
                        Group {
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    name, Modifier.weight(1f), fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium
                                )
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
                                    value, { flags[name] = it; save() }, singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                1 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        jsonText, { jsonText = it }, label = { Text("ClientAppSettings JSON") },
                        minLines = 8, maxLines = 16,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PressButton("Load current", { jsonText = toJson(flags); jsonMsg = "" }, style = 2)
                        PressButton("Replace flags", {
                            jsonMsg = runCatching {
                                val m = parseFlags(jsonText)
                                flags.clear(); flags.putAll(m); save()
                                "Loaded ${m.size} flags."
                            }.getOrElse { "Invalid JSON." }
                        })
                    }
                    if (jsonMsg.isNotEmpty()) Text(jsonMsg, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                ) { ProfilesUI() }
            }
        }
    }

    // ================= ROOT UI =================

    BackHandler(enabled = showEditor) { showEditor = false }

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalCorner provides cornerDp.dp) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(pc.copy(alpha = glowAmt), Color.Transparent),
                            center = Offset(200f + 700f * glow, 300f + 500f * glow),
                            radius = 1000f
                        )
                    )
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        Column(Modifier.background(lerp(Color(0xFF2A2A2E), pc, 0.04f))) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box {
                                    PressButton(
                                        (if (pkg == DEFAULT_PKG) "Roblox" else "Custom") + " ⌃",
                                        { pkgMenu = true },
                                        Modifier.height(56.dp), style = 1
                                    )
                                    DropdownMenu(expanded = pkgMenu, onDismissRequest = { pkgMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Roblox ($DEFAULT_PKG)") },
                                            onClick = { pkg = DEFAULT_PKG; pkgMenu = false; save() }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Custom package...") },
                                            onClick = { pkgMenu = false; pkgInput = pkg; pkgDialog = true }
                                        )
                                    }
                                }
                                PressButton("Save and Launch", { doSave(true) }, Modifier.weight(1.6f).height(56.dp))
                                PressButton("Save", { doSave(false) }, Modifier.weight(1f).height(56.dp))
                            }
                            NavigationBar(containerColor = Color.Transparent) {
                                navItems.forEachIndexed { i, (name, emoji) ->
                                    NavigationBarItem(
                                        selected = page == i,
                                        onClick = { page = i },
                                        icon = {
                                            val s by animateFloatAsState(
                                                if (page == i) 1.25f else 1f, spring(dampingRatio = 0.5f), label = "ic"
                                            )
                                            Text(emoji, fontSize = 20.sp, modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s })
                                        },
                                        label = { Text(name, fontSize = 11.sp, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }
                ) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        Crossfade(targetState = page, animationSpec = tween(300), label = "page") { p ->
                            when (p) {
                                0 -> HomePage()
                                1 -> LibraryPage()
                                2 -> ModsPage()
                                3 -> FlagsPage()
                                else -> SettingsPage()
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showEditor,
                    enter = slideInHorizontally(tween(320)) { it } + fadeIn(tween(320)),
                    exit = slideOutHorizontally(tween(260)) { it } + fadeOut(tween(260))
                ) { EditorScreen() }
            }

            renaming?.let { old ->
                AlertDialog(
                    onDismissRequest = { renaming = null },
                    title = { Text("Rename profile") },
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

            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Clear all flags?") },
                    text = { Text("This removes all ${flags.size} flags from the list. Saved profiles are kept.") },
                    confirmButton = { TextButton({ flags.clear(); save(); confirmClear = false }) { Text("Clear") } },
                    dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } }
                )
            }

            if (pkgDialog) {
                AlertDialog(
                    onDismissRequest = { pkgDialog = false },
                    title = { Text("Package name") },
                    text = {
                        OutlinedTextField(
                            pkgInput, { pkgInput = it }, singleLine = true,
                            label = { Text("e.g. com.roblox.client") }
                        )
                    },
                    confirmButton = {
                        TextButton({
                            if (pkgInput.isNotBlank()) { pkg = pkgInput.trim(); save() }
                            pkgDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton({ pkgDialog = false }) { Text("Cancel") } }
                )
            }

            if (licenses) {
                AlertDialog(
                    onDismissRequest = { licenses = false },
                    title = { Text("Licenses") },
                    text = { Text("TacoStrap is built with Jetpack Compose and Shizuku (Apache License 2.0).") },
                    confirmButton = { TextButton({ licenses = false }) { Text("Close") } }
                )
            }
        }
    }
}
