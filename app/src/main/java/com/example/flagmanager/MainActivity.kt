package com.example.flagmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

private const val APP_VERSION = "5.2"
private const val DEFAULT_PKG = "com.roblox.client"
private const val DEFAULT_PATH = "/data/local/tmp/ClientAppSettings.json"
private const val FPS_PROP = "debug.graphics.game_default_frame_rate.disabled"
private const val FPS_FLAG = "DFIntTaskSchedulerTargetFps"
private const val ALLOWLIST_URL =
    "https://devforum.roblox.com/t/allowlist-for-local-client-configuration-via-fast-flags/3966569"
private const val SHIZUKU_URL = "https://shizuku.rikka.app"

private var autoLaunchedOnce = false

// ---------- Shell / logic ----------

/** Safely quote a value inserted into a shell command. */
private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private fun validPackageName(pkg: String): Boolean =
    pkg.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))

fun parseFlags(text: String): Map<String, String> {
    val o = JSONObject(text)
    return o.keys().asSequence().associateWith { o.get(it).toString() }
}

fun toJson(m: Map<String, String>): String = JSONObject(m).toString(2)

fun shizukuStatus(): String = try {
    when {
        !Shizuku.pingBinder() -> "Shizuku is not running"
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "Tap Grant"
        else -> "Connected"
    }
} catch (e: Throwable) {
    "Unavailable"
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

fun isRootAvailable(): Boolean = suSh("id").first

fun applyFlags(json: String, path: String, fps: Boolean, root: Boolean): String {
    val parsed = runCatching { parseFlags(json) }.getOrElse {
        return "Failed: invalid JSON (${it.message ?: "parse error"})."
    }
    if (path.isBlank() || path.contains("\n") || path.contains("\r")) {
        return "Failed: enter a valid flag-file path."
    }
    val b64 = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
    val dir = path.substringBeforeLast('/', ".")
    // A target above 60 needs Android's default game frame rate cap lifted.
    val target = parsed[FPS_FLAG]?.toIntOrNull() ?: 0
    val liftCap = fps || target > 60
    val qDir = shellQuote(dir)
    val qPath = shellQuote(path)
    val cmd = buildString {
        append("mkdir -p $qDir && echo '$b64' | base64 -d > $qPath && chmod 644 $qPath")
        if (liftCap) append(" && setprop $FPS_PROP true")
    }
    val (ok, msg) = runCmd(cmd, root)
    return if (ok) "Applied ${parsed.size} flags to $path" else "Failed: $msg"
}

fun readFlagFile(path: String, root: Boolean): String {
    val (_, out) = runCmd("cat ${shellQuote(path)} 2>/dev/null", root)
    return out
}

fun verifyFlags(json: String, path: String, root: Boolean): String {
    val onDisk = readFlagFile(path, root)
    if (onDisk.isBlank()) return "Verify failed: file is empty or missing."
    val wanted = parseFlags(json)
    val got = runCatching { parseFlags(onDisk) }.getOrElse { return "Verify failed: file is not valid JSON." }
    val missing = wanted.keys.filter { got[it] != wanted[it] }
    return if (missing.isEmpty()) "Verified: ${wanted.size} flags on disk match."
    else "Verify warning: ${missing.size} flags differ on disk."
}

fun applyVerified(json: String, path: String, fps: Boolean, root: Boolean): String {
    val a = applyFlags(json, path, fps, root)
    if (a.startsWith("Failed")) return a
    return a + "\n" + verifyFlags(json, path, root)
}

fun resetFlags(path: String, pkg: String, root: Boolean): String {
    if (!validPackageName(pkg)) return "Failed: invalid Roblox package name."
    val (ok, msg) = runCmd("rm -f ${shellQuote(path)}; setprop $FPS_PROP false; am force-stop ${shellQuote(pkg)}", root)
    return if (ok) "Removed flag file. Reopen Roblox." else "Failed: $msg"
}

fun launchPkg(pkg: String, root: Boolean): String {
    if (!validPackageName(pkg)) return "Failed: invalid Roblox package name."
    val (ok, msg) = runCmd("monkey -p ${shellQuote(pkg)} -c android.intent.category.LAUNCHER 1", root)
    return if (ok) "Launched Roblox." else "Failed: $msg"
}

fun launchLink(uri: String, pkg: String, root: Boolean): String {
    if (uri.contains('\'') || uri.contains(' ')) return "Failed: that link has invalid characters."
    // Try Roblox directly first, then fall back to whatever app handles the link.
    val (ok1, msg1) = runCmd("am start -a android.intent.action.VIEW -d '$uri' -p $pkg", root)
    if (ok1 && !msg1.contains("Error")) return "Opening in Roblox."
    val (ok2, msg2) = runCmd("am start -a android.intent.action.VIEW -d '$uri'", root)
    if (ok2 && !msg2.contains("Error")) return "Opening link."
    val reason = msg2.ifEmpty { msg1.ifEmpty { "could not open the link" } }
    return "Failed: $reason"
}

fun normalizeLink(input: String): String? {
    val t = input.trim()
    return when {
        t.isEmpty() -> null
        t.all { it.isDigit() } -> "roblox://placeId=$t"
        t.startsWith("roblox://") -> t
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains("roblox.com") -> "https://$t"
        else -> null
    }
}

fun extractPlaceId(input: String): String? = Regex("""\d{4,}""").find(input)?.value

fun forceStop(pkg: String, root: Boolean): String {
    if (!validPackageName(pkg)) return "Failed: invalid Roblox package name."
    val (ok, msg) = runCmd("am force-stop ${shellQuote(pkg)}", root)
    return if (ok) "Closed Roblox." else "Failed: $msg"
}

fun trimMemory(pkg: String, level: String, root: Boolean): String {
    if (!validPackageName(pkg) || level !in setOf("HIDDEN", "BACKGROUND", "MODERATE", "LOW", "CRITICAL", "RUNNING_MODERATE", "RUNNING_LOW", "RUNNING_CRITICAL")) return "Failed: invalid package or trim level."
    val (ok, msg) = runCmd("am send-trim-memory ${shellQuote(pkg)} $level", root)
    return if (ok) "Trimmed memory." else "Failed: ${msg.ifEmpty { "is Roblox running?" }}"
}

fun clearCache(pkg: String, root: Boolean): String {
    if (!validPackageName(pkg)) return "Failed: invalid package name."
    val (ok, msg) = runCmd("rm -rf ${shellQuote("/sdcard/Android/data/$pkg/cache/")}*", root)
    return if (ok) "Cleared Roblox cache." else "Failed: $msg"
}

fun fetchText(url: String): Pair<Boolean, String> = try {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10000
    c.readTimeout = 10000
    c.requestMethod = "GET"
    val code = c.responseCode
    val body = if (code in 200..299) c.inputStream.bufferedReader().readText()
    else c.errorStream?.bufferedReader()?.readText().orEmpty()
    c.disconnect()
    Pair(code in 200..299, body)
} catch (e: Throwable) {
    Pair(false, e.message ?: "Network error")
}

private fun loadRecent(s: String): List<Triple<String, String, Long>> = runCatching {
    val a = JSONArray(s)
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        Triple(o.getString("n"), o.getString("t"), o.getLong("ts"))
    }
}.getOrDefault(emptyList())

private fun recentJson(l: List<Triple<String, String, Long>>): String {
    val a = JSONArray()
    l.forEach { e ->
        val o = JSONObject()
        o.put("n", e.first)
        o.put("t", e.second)
        o.put("ts", e.third)
        a.put(o)
    }
    return a.toString()
}

// ---------- Models ----------

private data class AppTheme(val name: String, val primary: Color, val secondary: Color)

private val THEMES = listOf(
    AppTheme("Taco", Color(0xFFFFB02E), Color(0xFFF26B1D)),
    AppTheme("Salsa", Color(0xFFFF6B5E), Color(0xFFD7263D)),
    AppTheme("Guac", Color(0xFF9CCC65), Color(0xFF558B2F)),
    AppTheme("Void", Color(0xFFB69CFF), Color(0xFF7C4DFF)),
    AppTheme("Blox", Color(0xFF5BB8F5), Color(0xFF1E88E5)),
    AppTheme("Frost", Color(0xFFA8E6F0), Color(0xFF4DB6C6)),
    AppTheme("Mint", Color(0xFF6FE3B8), Color(0xFF1FA97A)),
    AppTheme("Rose", Color(0xFFF48FB1), Color(0xFFDC4C7E))
)

private val TRIM_LEVELS = listOf(
    "Light" to "RUNNING_MODERATE",
    "Medium" to "RUNNING_LOW",
    "Strong" to "RUNNING_CRITICAL"
)
private val TRIM_INTERVALS = listOf(30, 60, 120, 300)
private val TRIM_LABELS = listOf("30 seconds", "1 minute", "2 minutes", "5 minutes")

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
    "Anti aliasing", "Smooths jagged edges. Higher values cost a lot of frame rate",
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
private val FS_SHADOWS = FS.Toggle("Disable shadows", "Requests reduced Roblox shadow rendering; Roblox may ignore unsupported flags.", mapOf("FIntRenderShadowIntensity" to "0"))
private val FS_POSTFX = FS.Toggle("Disable post-processing", "Requests fewer bloom and color effects; Roblox may ignore unsupported flags.", mapOf("FFlagDisablePostFx" to "True"))
// These toggles reuse the same flags as the existing graphics controls and profiles.
// They modify the flag editor's saved JSON; the game must support the flag for an in-game effect.
private val FS_LOW_TEXTURES = FS.Toggle(
    "Low texture quality",
    "Uses the lowest texture override to reduce graphics memory pressure where supported.",
    texSet("0")
)
private val FS_LOW_QUALITY = FS.Toggle(
    "Low graphics quality",
    "Requests Roblox graphics quality level 1 for lower rendering load where supported.",
    mapOf("FIntRomarkStartWithGraphicQualityLevel" to "1")
)
private val FS_LOW_AA = FS.Toggle(
    "Reduce anti-aliasing",
    "Uses the lowest anti-aliasing setting to reduce GPU work where supported.",
    mapOf("FIntDebugForceMSAASamples" to "1")
)
private val FS_FPS = FS.Choice(
    "Frame rate limit", "Above 60 also lifts the Android frame rate cap when you apply",
    listOf(Opt("Default", emptyMap())) +
        listOf(30, 60, 90, 120, 144, 165, 240).map { Opt("$it FPS", mapOf(FPS_FLAG to "$it")) }
)
private val FS_FPSCOUNT = FS.Toggle("FPS counter", "Shows frame rate in the corner", mapOf("FFlagDebugDisplayFPS" to "True"))
private val FS_QUALITY = FS.Choice(
    "Graphics quality override", "Locks the Roblox graphics level. Lower is faster in games",
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
    "Faster loading", "Preloads more assets to load games faster. Can use a lot of memory",
    mapOf("DFIntNumAssetsMaxToPreload" to "2147483647", "DFIntAssetPreloading" to "2147483647")
)
private val FS_MTU = FS.Num("MTU size", "Bytes sent per network packet", "DFIntConnectionMTUSize")
private val FS_LOD = FS.Choice(
    "Detail distance", "How far high detail models render",
    listOf(
        Opt("Default", emptyMap()),
        Opt("Near", mapOf("DFIntDebugFRMQualityLevelOverride" to "1")),
        Opt("Far", mapOf("DFIntDebugFRMQualityLevelOverride" to "6"))
    )
)

private val SECTIONS: List<Pair<String, List<FS>>> = listOf(
    "Graphics" to listOf(FS_TEXTURE, FS_AA, FS_RENDER, FS_SKY, FS_SHADOWS, FS_POSTFX, FS_LOD),
    "FPS boost toggles" to listOf(FS_LOW_TEXTURES, FS_LOW_QUALITY, FS_LOW_AA, FS_SHADOWS, FS_POSTFX, FS_NOGRASS, FS_BLUR),
    "Performance" to listOf(FS_FPS, FS_FPSCOUNT, FS_PRELOAD),
    "World detail" to listOf(FS_QUALITY, FS_GRASSMOVE, FS_NOGRASS, FS_GRASSDIST),
    "Interface" to listOf(FS_BLUR),
    "Network" to listOf(FS_MTU)
)

private val PACKS = listOf(
    Pack(
        "Balanced", "Medium textures and 2x anti aliasing",
        texSet("2") + mapOf("FIntDebugForceMSAASamples" to "2")
    ),
    Pack(
        "Performance", "Lowest textures, quality level 3, no shadows or post-processing",
        texSet("0") + mapOf(
            "FIntDebugForceMSAASamples" to "1",
            "FIntRenderShadowIntensity" to "0",
            "FFlagDisablePostFx" to "True",
            "FIntRomarkStartWithGraphicQualityLevel" to "3"
        )
    ),
    Pack(
        "Quality", "Sharp textures and 4x anti aliasing",
        texSet("3") + mapOf("FIntDebugForceMSAASamples" to "4")
    ),
    Pack(
        "Potato", "For weak devices. No grass, no shadows, no blur, minimum everything",
        texSet("0") + mapOf(
            "FIntDebugForceMSAASamples" to "1",
            "FIntRenderShadowIntensity" to "0",
            "FFlagDisablePostFx" to "True",
            "FIntFRMMinGrassDistance" to "0",
            "FIntFRMMaxGrassDistance" to "0",
            "FIntRobloxGuiBlurIntensity" to "0",
            "FIntRomarkStartWithGraphicQualityLevel" to "1"
        )
    ),
    Pack(
        "Competitive", "Maximum frame rate. No shadows, no blur, no grass, 240 FPS cap",
        mapOf(
            FPS_FLAG to "240",
            "FIntRenderShadowIntensity" to "0",
            "FIntRobloxGuiBlurIntensity" to "0",
            "FIntFRMMinGrassDistance" to "0",
            "FIntFRMMaxGrassDistance" to "0",
            "FFlagDebugDisplayFPS" to "True",
            "FIntDebugForceMSAASamples" to "1"
        )
    )
)

// Every key a pack can touch, except the FPS target and counter (those are your own choices).
private val PACK_KEYS: Set<String> =
    PACKS.flatMap { it.sets.keys }.toSet() - setOf(FPS_FLAG, "FFlagDebugDisplayFPS")

// Applying a pack replaces the previous pack's settings instead of stacking on top of them.
private fun MutableMap<String, String>.applyPack(p: Pack) {
    PACK_KEYS.forEach { remove(it) }
    putAll(p.sets)
}

// ---------- Shared UI state ----------

private val LocalCorner = compositionLocalOf { 24.dp }
private val LocalPad = compositionLocalOf { 16.dp }

private val Amber = Color(0xFFE8B84A)
private val Good = Color(0xFF5BC27A)
private val Bad = Color(0xFFEF6B63)

/** Live Shizuku state, updated by the permission listener in MainActivity. */
object ShizukuBus {
    var ping by mutableStateOf(false)
    var granted by mutableStateOf(false)
}

// ---------- Icons (drawn, no emoji) ----------

private enum class Ic {
    Home, Grid, Layers, Flag, Sliders, Right, Down, Up, Back, Close, Plus,
    Info, Warn, Play, Check, Download, Folder, Bell, Game, Doc, Search,
    Copy, Trash, Sort, Refresh, Star, Save, Palette, More, Globe, Bolt,
    Memory, Clock
}

private fun DrawScope.pl(c: Color, st: Stroke, s: Float, vararg p: Float) {
    val path = Path()
    path.moveTo(p[0] * s, p[1] * s)
    var i = 2
    while (i < p.size) {
        path.lineTo(p[i] * s, p[i + 1] * s)
        i += 2
    }
    drawPath(path, c, style = st)
}

@Composable
private fun Ico(kind: Ic, tint: Color, sz: Dp = 24.dp, stroke: Float = 1.9f) {
    Canvas(Modifier.size(sz)) {
        val s = this.size.width / 24f
        val st = Stroke(width = stroke * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            Ic.Home -> {
                pl(tint, st, s, 3f, 11f, 12f, 3.5f, 21f, 11f)
                pl(tint, st, s, 5.5f, 9.5f, 5.5f, 20f, 18.5f, 20f, 18.5f, 9.5f)
                pl(tint, st, s, 10f, 20f, 10f, 14f, 14f, 14f, 14f, 20f)
            }
            Ic.Grid -> {
                listOf(4f to 4f, 13.5f to 4f, 4f to 13.5f, 13.5f to 13.5f).forEach { (x, y) ->
                    drawRoundRect(tint, Offset(x * s, y * s), Size(6.5f * s, 6.5f * s), CornerRadius(1.8f * s), style = st)
                }
            }
            Ic.Layers -> {
                pl(tint, st, s, 12f, 3.5f, 21f, 8.5f, 12f, 13.5f, 3f, 8.5f, 12f, 3.5f)
                pl(tint, st, s, 3f, 12.5f, 12f, 17.5f, 21f, 12.5f)
                pl(tint, st, s, 3f, 16.5f, 12f, 21.5f, 21f, 16.5f)
            }
            Ic.Flag -> {
                pl(tint, st, s, 6f, 21f, 6f, 4f)
                pl(tint, st, s, 6f, 4.5f, 18f, 4.5f, 15f, 9f, 18f, 13.5f, 6f, 13.5f)
            }
            Ic.Sliders -> {
                pl(tint, st, s, 4f, 6f, 7f, 6f)
                pl(tint, st, s, 11f, 6f, 20f, 6f)
                drawCircle(tint, 2f * s, Offset(9f * s, 6f * s), style = st)
                pl(tint, st, s, 4f, 12f, 12f, 12f)
                pl(tint, st, s, 16f, 12f, 20f, 12f)
                drawCircle(tint, 2f * s, Offset(14f * s, 12f * s), style = st)
                pl(tint, st, s, 4f, 18f, 5f, 18f)
                pl(tint, st, s, 9f, 18f, 20f, 18f)
                drawCircle(tint, 2f * s, Offset(7f * s, 18f * s), style = st)
            }
            Ic.Right -> pl(tint, st, s, 9f, 5f, 16f, 12f, 9f, 19f)
            Ic.Down -> pl(tint, st, s, 5f, 9f, 12f, 16f, 19f, 9f)
            Ic.Up -> pl(tint, st, s, 5f, 15f, 12f, 8f, 19f, 15f)
            Ic.Back -> {
                pl(tint, st, s, 19f, 12f, 5f, 12f)
                pl(tint, st, s, 11f, 5f, 4.5f, 12f, 11f, 19f)
            }
            Ic.Close -> {
                pl(tint, st, s, 6f, 6f, 18f, 18f)
                pl(tint, st, s, 18f, 6f, 6f, 18f)
            }
            Ic.Plus -> {
                pl(tint, st, s, 12f, 5f, 12f, 19f)
                pl(tint, st, s, 5f, 12f, 19f, 12f)
            }
            Ic.Info -> {
                drawCircle(tint, 9f * s, Offset(12f * s, 12f * s), style = st)
                pl(tint, st, s, 12f, 11f, 12f, 16.5f)
                drawCircle(tint, 1.1f * s, Offset(12f * s, 7.8f * s))
            }
            Ic.Warn -> {
                pl(tint, st, s, 12f, 3.5f, 21.5f, 20f, 2.5f, 20f, 12f, 3.5f)
                pl(tint, st, s, 12f, 10f, 12f, 14.5f)
                drawCircle(tint, 1.1f * s, Offset(12f * s, 17.3f * s))
            }
            Ic.Play -> pl(tint, st, s, 8f, 5f, 19f, 12f, 8f, 19f, 8f, 5f)
            Ic.Check -> pl(tint, st, s, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
            Ic.Download -> {
                pl(tint, st, s, 12f, 4f, 12f, 15f)
                pl(tint, st, s, 7f, 10.5f, 12f, 15.5f, 17f, 10.5f)
                pl(tint, st, s, 5f, 20f, 19f, 20f)
            }
            Ic.Folder -> pl(tint, st, s, 3.5f, 6.5f, 9.5f, 6.5f, 11.5f, 9f, 20.5f, 9f, 20.5f, 18.5f, 3.5f, 18.5f, 3.5f, 6.5f)
            Ic.Bell -> {
                val p = Path()
                p.moveTo(6.5f * s, 17f * s)
                p.lineTo(6.5f * s, 11f * s)
                p.cubicTo(6.5f * s, 7.5f * s, 9f * s, 5.5f * s, 12f * s, 5.5f * s)
                p.cubicTo(15f * s, 5.5f * s, 17.5f * s, 7.5f * s, 17.5f * s, 11f * s)
                p.lineTo(17.5f * s, 17f * s)
                drawPath(p, tint, style = st)
                pl(tint, st, s, 4.5f, 17f, 19.5f, 17f)
                pl(tint, st, s, 10f, 20.5f, 14f, 20.5f)
            }
            Ic.Game -> {
                drawRoundRect(tint, Offset(3f * s, 7.5f * s), Size(18f * s, 10f * s), CornerRadius(5f * s), style = st)
                pl(tint, st, s, 7.5f, 10.5f, 7.5f, 14.5f)
                pl(tint, st, s, 5.5f, 12.5f, 9.5f, 12.5f)
                drawCircle(tint, 1.1f * s, Offset(15.5f * s, 11.5f * s))
                drawCircle(tint, 1.1f * s, Offset(18f * s, 13.5f * s))
            }
            Ic.Doc -> {
                drawRoundRect(tint, Offset(5f * s, 3f * s), Size(14f * s, 18f * s), CornerRadius(2f * s), style = st)
                pl(tint, st, s, 8.5f, 9f, 15.5f, 9f)
                pl(tint, st, s, 8.5f, 13f, 15.5f, 13f)
                pl(tint, st, s, 8.5f, 17f, 12.5f, 17f)
            }
            Ic.Search -> {
                drawCircle(tint, 6.5f * s, Offset(11f * s, 11f * s), style = st)
                pl(tint, st, s, 16f, 16f, 20.5f, 20.5f)
            }
            Ic.Copy -> {
                drawRoundRect(tint, Offset(8.5f * s, 8.5f * s), Size(11f * s, 12f * s), CornerRadius(2f * s), style = st)
                pl(tint, st, s, 5.5f, 15f, 5.5f, 5f, 15f, 5f)
            }
            Ic.Trash -> {
                pl(tint, st, s, 5f, 7f, 19f, 7f)
                pl(tint, st, s, 10f, 7f, 10f, 4.5f, 14f, 4.5f, 14f, 7f)
                pl(tint, st, s, 7f, 7f, 7.5f, 20f, 16.5f, 20f, 17f, 7f)
                pl(tint, st, s, 10.5f, 10.5f, 10.5f, 16.5f)
                pl(tint, st, s, 13.5f, 10.5f, 13.5f, 16.5f)
            }
            Ic.Sort -> {
                pl(tint, st, s, 7f, 4.5f, 7f, 19.5f)
                pl(tint, st, s, 4f, 7.5f, 7f, 4.5f, 10f, 7.5f)
                pl(tint, st, s, 17f, 4.5f, 17f, 19.5f)
                pl(tint, st, s, 14f, 16.5f, 17f, 19.5f, 20f, 16.5f)
            }
            Ic.Refresh -> {
                drawCircle(tint, 7.5f * s, Offset(12f * s, 12f * s), style = st)
                pl(tint, st, s, 17.5f, 6.5f, 17.5f, 10.5f, 13.5f, 10.5f)
            }
            Ic.Star -> {
                val p = Path()
                p.moveTo(12f * s, 3.5f * s)
                p.lineTo(14.6f * s, 9f * s)
                p.lineTo(20.5f * s, 9.6f * s)
                p.lineTo(16f * s, 13.6f * s)
                p.lineTo(17.4f * s, 19.5f * s)
                p.lineTo(12f * s, 16.4f * s)
                p.lineTo(6.6f * s, 19.5f * s)
                p.lineTo(8f * s, 13.6f * s)
                p.lineTo(3.5f * s, 9.6f * s)
                p.lineTo(9.4f * s, 9f * s)
                p.close()
                drawPath(p, tint, style = st)
            }
            Ic.Save -> {
                pl(tint, st, s, 5f, 4.5f, 16f, 4.5f, 19f, 7.5f, 19f, 19.5f, 5f, 19.5f, 5f, 4.5f)
                drawRoundRect(tint, Offset(8f * s, 4.5f * s), Size(6f * s, 5f * s), CornerRadius(0.8f * s), style = st)
                drawRoundRect(tint, Offset(8f * s, 12.5f * s), Size(8f * s, 7f * s), CornerRadius(0.8f * s), style = st)
            }
            Ic.Palette -> {
                drawCircle(tint, 8.5f * s, Offset(12f * s, 12f * s), style = st)
                drawCircle(tint, 1.2f * s, Offset(12f * s, 7.5f * s))
                drawCircle(tint, 1.2f * s, Offset(16.5f * s, 12f * s))
                drawCircle(tint, 1.2f * s, Offset(12f * s, 16.5f * s))
                drawCircle(tint, 1.2f * s, Offset(7.5f * s, 12f * s))
            }
            Ic.More -> {
                drawCircle(tint, 1.3f * s, Offset(12f * s, 5.5f * s))
                drawCircle(tint, 1.3f * s, Offset(12f * s, 12f * s))
                drawCircle(tint, 1.3f * s, Offset(12f * s, 18.5f * s))
            }
            Ic.Globe -> {
                drawCircle(tint, 9f * s, Offset(12f * s, 12f * s), style = st)
                pl(tint, st, s, 3f, 12f, 21f, 12f)
                val p = Path()
                p.moveTo(12f * s, 3f * s)
                p.cubicTo(8.5f * s, 7f * s, 8.5f * s, 17f * s, 12f * s, 21f * s)
                p.cubicTo(15.5f * s, 17f * s, 15.5f * s, 7f * s, 12f * s, 3f * s)
                drawPath(p, tint, style = st)
            }
            Ic.Bolt -> {
                pl(tint, st, s, 13f, 3f, 6f, 13.5f, 11f, 13.5f, 10.5f, 21f, 18f, 10f, 13f, 10f, 13f, 3f)
            }
            Ic.Memory -> {
                drawRoundRect(tint, Offset(7f * s, 7f * s), Size(10f * s, 10f * s), CornerRadius(2f * s), style = st)
                listOf(10f, 14f).forEach { q ->
                    pl(tint, st, s, q, 3.5f, q, 7f)
                    pl(tint, st, s, q, 17f, q, 20.5f)
                    pl(tint, st, s, 3.5f, q, 7f, q)
                    pl(tint, st, s, 17f, q, 20.5f, q)
                }
            }
            Ic.Clock -> {
                drawCircle(tint, 9f * s, Offset(12f * s, 12f * s), style = st)
                pl(tint, st, s, 12f, 7f, 12f, 12f, 15.5f, 14f)
            }
        }
    }
}

// ---------- Reusable UI (no animations, built for smooth scrolling) ----------

private fun accentBrush(cs: ColorScheme): Brush =
    Brush.linearGradient(listOf(cs.primary, lerp(cs.primary, cs.secondary, 0.6f)))

@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(LocalCorner.current)
    Column(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(cs.surfaceVariant, lerp(cs.surfaceVariant, cs.background, 0.35f))
                )
            )
            .border(1.dp, cs.outlineVariant, shape)
            .padding(vertical = 4.dp),
        content = content
    )
}

@Composable
private fun IconChip(icon: Ic, tint: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.10f)))),
        contentAlignment = Alignment.Center
    ) { Ico(icon, tint, size * 0.52f, 2f) }
}

@Composable
private fun Item(
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    leading: Ic? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val m = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        Modifier.fillMaxWidth().then(m).padding(horizontal = 18.dp, vertical = LocalPad.current),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            IconChip(leading, MaterialTheme.colorScheme.primary, 38.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (sub != null) {
                Text(
                    sub, fontSize = 13.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun ValueText(text: String) {
    Text(
        text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TacoSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.width(54.dp).height(31.dp).clip(CircleShape)
            .then(
                if (checked) Modifier.background(accentBrush(cs))
                else Modifier.background(Color(0xFF3A3632))
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onChange(!checked)
            }
    ) {
        Box(
            Modifier.offset(x = if (checked) 25.dp else 4.dp, y = 3.5.dp).size(24.dp).clip(CircleShape)
                .background(if (checked) Color.White else Color(0xFF9A948C))
        )
    }
}

@Composable
private fun SwitchItem(
    title: String,
    sub: String?,
    checked: Boolean,
    leading: Ic? = null,
    onChange: (Boolean) -> Unit
) {
    Item(title, sub, { onChange(!checked) }, leading = leading) { TacoSwitch(checked, onChange) }
}

@Composable
private fun SectionLabel(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(start = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 4.dp, height = 14.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(cs.primary, cs.secondary)))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
            color = cs.primary
        )
    }
}

@Composable
private fun PageHeader(title: String, sub: String? = null) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 4.dp, vertical = 10.dp)) {
        Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(cs.primary, cs.secondary)))
        )
        if (sub != null) {
            Spacer(Modifier.height(8.dp))
            Text(sub, fontSize = 15.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceBox(current: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp))
                .background(cs.background.copy(alpha = 0.55f))
                .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp))
                .clickable { open = true }
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(current, fontSize = 14.sp, maxLines = 1, modifier = Modifier.widthIn(max = 130.dp))
            Spacer(Modifier.width(4.dp))
            Ico(Ic.Down, cs.primary, 16.dp, 2.2f)
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
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
    )
}

@Composable
private fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: Int = 0,
    icon: Ic? = null,
    h: Dp = 48.dp
) {
    val cs = MaterialTheme.colorScheme
    val fg = when (kind) {
        0 -> cs.onPrimary
        1 -> cs.primary
        else -> cs.onSurface
    }
    Row(
        modifier.height(h)
            .clip(CircleShape)
            .then(
                when (kind) {
                    0 -> Modifier.background(accentBrush(cs))
                    1 -> Modifier.background(cs.primary.copy(alpha = 0.16f))
                    else -> Modifier
                }
            )
            .then(
                when (kind) {
                    1 -> Modifier.border(1.dp, cs.primary.copy(alpha = 0.35f), CircleShape)
                    2 -> Modifier.border(1.dp, cs.outline, CircleShape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Ico(icon, fg, 18.dp, 2.1f)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun LaunchButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.height(62.dp)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(cs.primary, cs.secondary)))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Ico(Ic.Play, cs.onPrimary, 22.dp, 2.2f)
        Spacer(Modifier.width(10.dp))
        Text(text, color = cs.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun IconBtn(
    icon: Ic,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    sz: Dp = 40.dp
) {
    Box(
        Modifier.size(sz).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Ico(icon, tint, 20.dp) }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.clip(CircleShape)
            .then(
                if (selected) Modifier.background(accentBrush(cs))
                else Modifier.border(1.dp, cs.outline, CircleShape)
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text, Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = if (selected) cs.onPrimary else cs.onSurface
        )
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: String, icon: Ic, tint: Color) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(LocalCorner.current * 3 / 4)
    Column(
        Modifier.weight(1f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.18f), cs.surfaceVariant)))
            .border(1.dp, tint.copy(alpha = 0.25f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon, tint, 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp, color = cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowScope.ActionTile(label: String, icon: Ic, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(LocalCorner.current * 3 / 4)
    Column(
        Modifier.weight(1f)
            .clip(shape)
            .background(cs.surfaceVariant)
            .border(1.dp, cs.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconChip(icon, cs.primary, 42.dp)
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun EmptyHint(title: String, sub: String) {
    val cs = MaterialTheme.colorScheme
    Group {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconChip(Ic.Info, cs.primary, 52.dp)
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                sub, fontSize = 13.sp, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(
        Modifier.clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

private fun packIcon(name: String): Ic = when (name) {
    "Performance" -> Ic.Bolt
    "Quality" -> Ic.Star
    "Potato" -> Ic.Down
    "Competitive" -> Ic.Flag
    else -> Ic.Layers
}

/** Floating pill navigation. The selected tab fills with the accent and shows its label. */
@Composable
private fun FloatingNav(items: List<Pair<String, Ic>>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(lerp(cs.surfaceVariant, cs.primary, 0.07f), cs.surfaceVariant)
                    )
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            cs.primary.copy(alpha = 0.40f),
                            cs.outlineVariant,
                            cs.secondary.copy(alpha = 0.28f)
                        )
                    ),
                    shape
                )
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, (label, ic) ->
                NavTab(label, ic, i == selected) { onSelect(i) }
            }
        }
    }
}

@Composable
private fun RowScope.NavTab(label: String, icon: Ic, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tint = if (selected) cs.onPrimary else cs.onSurfaceVariant
    Box(
        Modifier.weight(if (selected) 1.8f else 1f).height(48.dp).clip(CircleShape)
            .then(if (selected) Modifier.background(accentBrush(cs)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(icon, tint, 22.dp)
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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

// ---------- Activity ----------

class MainActivity : ComponentActivity() {

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        ShizukuBus.granted = grantResult == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { Shizuku.addRequestPermissionResultListener(permListener) }
        setContent { App() }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permListener) }
        super.onDestroy()
    }
}

// ---------- App ----------

@OptIn(ExperimentalMaterial3Api::class)
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
    val instances = remember {
        mutableStateMapOf<String, String>().apply {
            runCatching { putAll(parseFlags(prefs.getString("instances", "{}")!!)) }
        }
    }
    val recent = remember {
        mutableStateListOf<Triple<String, String, Long>>().apply {
            addAll(loadRecent(prefs.getString("recent", "[]")!!))
        }
    }

    var path by remember { mutableStateOf(prefs.getString("path2", DEFAULT_PATH)!!) }
    var pkg by remember { mutableStateOf(prefs.getString("pkg", DEFAULT_PKG)!!) }
    var restart by remember { mutableStateOf(prefs.getBoolean("restart", true)) }
    var fps by remember { mutableStateOf(prefs.getBoolean("fps", false)) }
    var useRoot by remember { mutableStateOf(prefs.getBoolean("root", false)) }
    var applyBeforeLaunch by remember { mutableStateOf(prefs.getBoolean("applyLaunch", true)) }
    var autoLaunch by remember { mutableStateOf(prefs.getBoolean("autoLaunch", false)) }
    val autoLaunchOnOpen = remember { prefs.getBoolean("autoLaunch", false) }
    var trimAuto by remember { mutableStateOf(prefs.getBoolean("trimAuto", false)) }
    var trimInterval by remember { mutableStateOf(prefs.getInt("trimInterval", 60)) }
    var trimLevelIdx by remember { mutableStateOf(prefs.getInt("trimLvl", 1).coerceIn(0, TRIM_LEVELS.size - 1)) }
    var themeIdx by remember { mutableStateOf(prefs.getInt("theme", 0).coerceIn(0, THEMES.size - 1)) }
    var dynColor by remember { mutableStateOf(prefs.getBoolean("dyn", false)) }
    var trueBlack by remember { mutableStateOf(prefs.getBoolean("trueBlack", false)) }
    var compact by remember { mutableStateOf(prefs.getBoolean("compact", false)) }
    var glowAmt by remember { mutableStateOf(prefs.getFloat("glow3", 0.10f)) }
    var cornerDp by remember { mutableStateOf(prefs.getFloat("corner2", 24f)) }
    var msgFlag by remember { mutableStateOf(prefs.getBoolean("msgFlag", true)) }
    var msgLaunch by remember { mutableStateOf(prefs.getBoolean("msgLaunch", true)) }
    var msgOther by remember { mutableStateOf(prefs.getBoolean("msgOther", true)) }
    var activeProfile by remember { mutableStateOf(prefs.getString("activeProfile", "")!!) }

    var shStatus by remember { mutableStateOf(shizukuStatus()) }
    var rootAvailable by remember { mutableStateOf(false) }
    var robloxVersion by remember { mutableStateOf("-") }

    var page by remember { mutableStateOf(0) }
    var setTab by remember { mutableStateOf(0) }
    var quickIdx by remember { mutableStateOf(0) }

    var search by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(0) }
    var filterMode by remember { mutableStateOf(0) }
    var k by remember { mutableStateOf("") }
    var v by remember { mutableStateOf("") }
    var pn by remember { mutableStateOf("") }
    var gn by remember { mutableStateOf("") }
    var gl by remember { mutableStateOf("") }
    var inName by remember { mutableStateOf("") }
    var inPkg by remember { mutableStateOf("") }
    var fileOut by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var duplicating by remember { mutableStateOf<String?>(null) }
    var dupName by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var pkgDialog by remember { mutableStateOf(false) }
    var pkgInput by remember { mutableStateOf("") }
    var licenses by remember { mutableStateOf(false) }
    var changelog by remember { mutableStateOf(false) }
    var showJson by remember { mutableStateOf(false) }
    var jsonText by remember { mutableStateOf("") }
    var jsonMsg by remember { mutableStateOf("") }
    var urlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var onboarding by remember { mutableStateOf(!prefs.getBoolean("seen5", false)) }

    val homeState = rememberLazyListState()
    val flagsState = rememberLazyListState()
    val presetsState = rememberLazyListState()
    val gamesState = rememberLazyListState()
    val settingsState = rememberLazyListState()

    fun save() {
        prefs.edit()
            .putString("flags", toJson(flags))
            .putString("presets", toJson(presets))
            .putString("games", toJson(games))
            .putString("instances", toJson(instances))
            .putString("path2", path)
            .putString("pkg", pkg)
            .putBoolean("restart", restart)
            .putBoolean("fps", fps)
            .putBoolean("root", useRoot)
            .putBoolean("applyLaunch", applyBeforeLaunch)
            .putBoolean("autoLaunch", autoLaunch)
            .putBoolean("trimAuto", trimAuto)
            .putInt("trimInterval", trimInterval)
            .putInt("trimLvl", trimLevelIdx)
            .putInt("theme", themeIdx)
            .putBoolean("dyn", dynColor)
            .putBoolean("trueBlack", trueBlack)
            .putBoolean("compact", compact)
            .putFloat("glow3", glowAmt)
            .putFloat("corner2", cornerDp)
            .putBoolean("msgFlag", msgFlag)
            .putBoolean("msgLaunch", msgLaunch)
            .putBoolean("msgOther", msgOther)
            .putString("activeProfile", activeProfile)
            .apply()
    }

    fun toast(kind: String, msg: String) {
        val allowed = when (kind) {
            "flag" -> msgFlag
            "launch" -> msgLaunch
            else -> msgOther
        }
        if (allowed || msg.startsWith("Failed") || msg.contains("Verify")) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg)
            }
        }
    }

    fun pushRecent(name: String, target: String) {
        val entry = Triple(name, target, System.currentTimeMillis())
        recent.removeAll { it.first == name && it.second == target }
        recent.add(0, entry)
        while (recent.size > 8) recent.removeAt(recent.size - 1)
        prefs.edit().putString("recent", recentJson(recent)).apply()
    }

    fun clearRecent() {
        recent.clear()
        prefs.edit().putString("recent", "[]").apply()
    }

    fun launchNow(applyFirst: Boolean) {
        scope.launch(Dispatchers.IO) {
            if (applyFirst) {
                val msg = applyVerified(toJson(flags), path, fps, useRoot)
                toast("flag", msg)
                if (msg.startsWith("Failed")) return@launch
            }
            if (restart) runCmd("am force-stop ${shellQuote(pkg)} && sleep 1", useRoot)
            val r = launchPkg(pkg, useRoot)
            toast("launch", r)
            if (!r.startsWith("Failed")) pushRecent("Roblox", "")
        }
    }

    fun playTarget(name: String, target: String) {
        scope.launch(Dispatchers.IO) {
            val r = if (target.isBlank()) {
                launchPkg(pkg, useRoot)
            } else {
                val uri = normalizeLink(target)
                if (uri == null) "Failed: not a valid place ID or link." else launchLink(uri, pkg, useRoot)
            }
            toast("launch", r)
            if (!r.startsWith("Failed")) pushRecent(name, target)
        }
    }

    fun openUrl(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun backupJson(): String = JSONObject().apply {
        put("app", "TacoStrap")
        put("version", APP_VERSION)
        put("flags", JSONObject(flags.toMap()))
        put("presets", JSONObject(presets.toMap()))
        put("games", JSONObject(games.toMap()))
        put("instances", JSONObject(instances.toMap()))
    }.toString(2)

    fun share(text: String, title: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { ctx.startActivity(Intent.createChooser(i, title)) }
    }

    fun copyText(label: String, text: String) {
        clip.setPrimaryClip(ClipData.newPlainText(label, text))
        toast("other", "Copied to clipboard.")
    }

    fun relTime(ts: Long): String =
        DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    LaunchedEffect(Unit) {
        while (true) {
            shStatus = shizukuStatus()
            ShizukuBus.ping = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            ShizukuBus.granted = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            delay(2000)
        }
    }

    LaunchedEffect(Unit) {
        rootAvailable = withContext(Dispatchers.IO) { isRootAvailable() }
    }

    val ready = shStatus == "Connected"

    LaunchedEffect(ready, useRoot, pkg) {
        if (ready || useRoot) {
            val (ok, out) = withContext(Dispatchers.IO) {
                runCmd("dumpsys package ${shellQuote(pkg)} | grep -m1 versionName", useRoot)
            }
            robloxVersion = if (ok && out.contains("=")) out.substringAfter("=").trim() else "Not found"
        }
    }

    // Automatic memory trimmer. Runs while TacoStrap's process is alive.
    LaunchedEffect(trimAuto, trimInterval, trimLevelIdx, pkg, useRoot, ready) {
        if (trimAuto && (ready || useRoot)) {
            while (true) {
                delay(trimInterval * 1000L)
                withContext(Dispatchers.IO) {
                    runCmd("am send-trim-memory ${shellQuote(pkg)} ${TRIM_LEVELS[trimLevelIdx].second}", useRoot)
                }
            }
        }
    }

    // Launch Roblox once when TacoStrap opens, only if the option was already on at startup.
    LaunchedEffect(ready) {
        if (autoLaunchOnOpen && ready && !autoLaunchedOnce) {
            autoLaunchedOnce = true
            launchNow(true)
        }
    }

    // ----- Theme -----
    val theme = THEMES[themeIdx]
    val pc = theme.primary
    val sc = theme.secondary
    val base = if (trueBlack) Color.Black else Color(0xFF121110)
    val sv = lerp(if (trueBlack) Color(0xFF141414) else Color(0xFF1D1B19), pc, 0.04f)
    val customScheme = darkColorScheme(
        primary = pc,
        onPrimary = Color(0xFF1A1206),
        primaryContainer = lerp(base, pc, 0.25f),
        onPrimaryContainer = lerp(pc, Color.White, 0.7f),
        secondary = sc,
        onSecondary = Color(0xFF1A1206),
        secondaryContainer = lerp(sv, pc, 0.2f),
        onSecondaryContainer = lerp(pc, Color.White, 0.4f),
        background = base,
        onBackground = Color(0xFFF2ECE6),
        surface = base,
        onSurface = Color(0xFFF2ECE6),
        surfaceVariant = sv,
        onSurfaceVariant = Color(0xFFB5ADA4),
        outline = Color(0xFF4A443E),
        outlineVariant = Color(0xFF2E2A26)
    )
    val scheme = when {
        dynColor && Build.VERSION.SDK_INT >= 31 ->
            runCatching { dynamicDarkColorScheme(ctx) }.getOrNull() ?: customScheme
        else -> customScheme
    }

    val navItems = listOf(
        "Home" to Ic.Home, "Flags" to Ic.Flag, "Presets" to Ic.Layers,
        "Games" to Ic.Game, "Settings" to Ic.Sliders
    )

    val sortedFlags by remember {
        derivedStateOf {
            flags.keys
                .filter { it.contains(search, ignoreCase = true) }
                .filter { name ->
                    val value = flags[name] ?: ""
                    val isBool = value.equals("true", true) || value.equals("false", true)
                    val isNum = value.toIntOrNull() != null
                    when (filterMode) {
                        1 -> isBool
                        2 -> isNum
                        3 -> !isBool && !isNum
                        else -> true
                    }
                }
                .sortedWith(
                    when (sortMode) {
                        1 -> compareByDescending<String> { it.lowercase() }
                        2 -> compareBy<String>({ (flags[it] ?: "").lowercase() }, { it.lowercase() })
                        else -> compareBy<String> { it.lowercase() }
                    }
                )
        }
    }

    // ================= PAGES =================

    @Composable
    fun HomePage() {
        val cs = MaterialTheme.colorScheme
        val flagCount = flags.size
        LazyColumn(
            state = homeState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val heroShape = RoundedCornerShape(LocalCorner.current + 4.dp)
                Column(
                    Modifier.fillMaxWidth()
                        .clip(heroShape)
                        .background(
                            Brush.linearGradient(
                                listOf(pc.copy(alpha = 0.32f), sc.copy(alpha = 0.10f), cs.surfaceVariant)
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(pc.copy(alpha = 0.6f), sc.copy(alpha = 0.12f))),
                            heroShape
                        )
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TacoStrap", fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            APP_VERSION, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = cs.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(cs.primary.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "FastFlag manager and bootstrapper for Roblox",
                        fontSize = 14.sp, color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    StatusPill(
                        if (ready) "Shizuku connected" else shStatus,
                        if (ready) Good else if (ShizukuBus.ping) Amber else Bad
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Roblox", robloxVersion, Ic.Game, cs.primary)
                        StatCard("Flags", "$flagCount", Ic.Flag, cs.primary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            "Shizuku", shStatus, Ic.Bolt,
                            if (ready) Good else if (ShizukuBus.ping) Amber else Bad
                        )
                        StatCard(
                            "Root", if (rootAvailable) "Available" else "Not found", Ic.Star,
                            if (rootAvailable) Good else cs.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                LaunchButton("Launch Roblox", { launchNow(applyBeforeLaunch) }, Modifier.fillMaxWidth())
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionTile("Apply", Ic.Save) {
                            scope.launch(Dispatchers.IO) {
                                save()
                                toast("flag", applyVerified(toJson(flags), path, fps, useRoot))
                            }
                        }
                        ActionTile("Verify", Ic.Check) {
                            scope.launch(Dispatchers.IO) {
                                toast("flag", verifyFlags(toJson(flags), path, useRoot))
                                val (_, out) = runCmd("ls -l ${shellQuote(path)}; cat ${shellQuote(path)}", useRoot)
                                fileOut = out.ifEmpty { "Nothing returned." }
                            }
                        }
                        ActionTile("Close", Ic.Close) {
                            scope.launch(Dispatchers.IO) { toast("launch", forceStop(pkg, useRoot)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionTile("Trim RAM", Ic.Memory) {
                            scope.launch(Dispatchers.IO) {
                                toast("other", trimMemory(pkg, TRIM_LEVELS[trimLevelIdx].second, useRoot))
                            }
                        }
                        ActionTile("Cache", Ic.Trash) {
                            scope.launch(Dispatchers.IO) { toast("other", clearCache(pkg, useRoot)) }
                        }
                        ActionTile("Reset", Ic.Refresh) {
                            scope.launch(Dispatchers.IO) { toast("flag", resetFlags(path, pkg, useRoot)) }
                        }
                    }
                }
            }
            if (fileOut.isNotEmpty()) {
                item {
                    Group {
                        Text(
                            fileOut, Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                }
            }
            if (activeProfile.isNotEmpty()) {
                item {
                    Group {
                        Item("Active profile", activeProfile, { page = 2 }, leading = Ic.Layers) {
                            Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Recent launches")
                    Group {
                        if (recent.isEmpty()) {
                            Item("Nothing yet", "Your launches show up here.", leading = Ic.Clock)
                        }
                        recent.forEach { (n, t, ts) ->
                            Item(
                                n,
                                if (t.isBlank()) relTime(ts) else "$t  -  ${relTime(ts)}",
                                { playTarget(n, t) },
                                leading = Ic.Clock
                            ) {
                                Ico(Ic.Play, cs.primary, 20.dp)
                            }
                        }
                        if (recent.isNotEmpty()) {
                            Item("Clear history", null, { clearRecent() }) {
                                Ico(Ic.Trash, cs.onSurfaceVariant, 20.dp)
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Pinned games")
                    Group {
                        if (games.isEmpty()) {
                            Item("No pinned games", "Add games in the Games tab.", { page = 3 }) {
                                Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                            }
                        }
                        games.keys.sorted().take(4).forEach { name ->
                            Item(name, "Place ID ${games[name]}", { playTarget(name, games[name] ?: "") }) {
                                Ico(Ic.Play, cs.primary, 20.dp)
                            }
                        }
                    }
                }
            }
            item {
                Group {
                    Item("Roblox allowlist", "Flags Roblox allows on PC", { openUrl(ALLOWLIST_URL) }, leading = Ic.Globe) {
                        Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                    }
                    Item("What's new in $APP_VERSION", "Changelog", { changelog = true }, leading = Ic.Doc) {
                        Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                    }
                }
            }
        }
    }

    @Composable
    fun FlagsPage() {
        val cs = MaterialTheme.colorScheme
        LazyColumn(
            state = flagsState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { PageHeader("FastFlags", "Quick settings, the full editor and JSON tools.") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Ico(Ic.Warn, Amber, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "These toggles edit your saved FastFlags. Tap Apply on Home, then restart Roblox. Only flags Roblox supports can affect in-game FPS.",
                        Modifier.weight(1f), fontSize = 13.sp, color = cs.onSurfaceVariant
                    )
                }
            }
            SECTIONS.forEach { (title, list) ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(title)
                        Group { list.forEach { FlagSetting(it, flags) { save() } } }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Quick setup")
                    Group {
                        Item("Recommended pack", PACKS[quickIdx].desc)
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ChoiceBox(PACKS[quickIdx].name, PACKS.map { it.name }) { quickIdx = it }
                            Btn("Apply", {
                                flags.applyPack(PACKS[quickIdx]); activeProfile = ""; save()
                                toast("flag", "Added ${PACKS[quickIdx].name} pack.")
                            }, h = 44.dp)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Flag editor")
                    Group {
                        Column(
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Add a custom flag", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            OutlinedTextField(
                                k, { k = it }, label = { Text("Flag name") }, singleLine = true,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    v, { v = it }, label = { Text("Value") }, singleLine = true,
                                    shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                                )
                                Btn("Add", {
                                    if (k.isNotBlank()) {
                                        flags[k.trim()] = v.trim(); activeProfile = ""; k = ""; v = ""; save()
                                        toast("flag", "Flag added.")
                                    }
                                }, icon = Ic.Plus)
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        search, { search = it }, label = { Text("Search ${flags.size} flags") }, singleLine = true,
                        leadingIcon = { Ico(Ic.Search, cs.onSurfaceVariant, 20.dp) },
                        trailingIcon = {
                            if (search.isNotEmpty()) IconBtn(Ic.Close, { search = "" }, sz = 36.dp)
                        },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChoiceBox(
                            when (sortMode) { 1 -> "Z to A"; 2 -> "By value"; else -> "A to Z" },
                            listOf("A to Z", "Z to A", "By value")
                        ) { sortMode = it }
                        Pill("All", filterMode == 0) { filterMode = 0 }
                        Pill("Booleans", filterMode == 1) { filterMode = 1 }
                        Pill("Numbers", filterMode == 2) { filterMode = 2 }
                        Pill("Text", filterMode == 3) { filterMode = 3 }
                    }
                    Text(
                        "${sortedFlags.size} shown",
                        fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            if (sortedFlags.isEmpty()) {
                item {
                    EmptyHint(
                        if (search.isEmpty() && filterMode == 0) "No flags yet" else "No matches",
                        if (search.isEmpty() && filterMode == 0) "Add a flag above or apply a pack from the Presets tab."
                        else "Try a different search or filter."
                    )
                }
            }
            items(sortedFlags, key = { it }) { name ->
                val value = flags[name] ?: ""
                val isBool = value.equals("true", true) || value.equals("false", true)
                var menu by remember { mutableStateOf(false) }
                Group {
                    Row(
                        Modifier.padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name, Modifier.weight(1f), fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium
                        )
                        if (isBool) {
                            TacoSwitch(value.equals("true", true)) {
                                flags[name] = if (it) "True" else "False"; save()
                            }
                        }
                        Box {
                            IconBtn(Ic.More, { menu = true }, sz = 40.dp)
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Copy name") },
                                    leadingIcon = { Ico(Ic.Copy, cs.onSurface, 18.dp) },
                                    onClick = { menu = false; copyText("flag name", name) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy value") },
                                    leadingIcon = { Ico(Ic.Copy, cs.onSurface, 18.dp) },
                                    onClick = { menu = false; copyText("flag value", flags[name] ?: "") }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    leadingIcon = { Ico(Ic.Plus, cs.onSurface, 18.dp) },
                                    onClick = {
                                        menu = false
                                        duplicating = name
                                        dupName = name + "_copy"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Ico(Ic.Trash, cs.onSurface, 18.dp) },
                                    onClick = { menu = false; flags.remove(name); activeProfile = ""; save() }
                                )
                            }
                        }
                    }
                    if (!isBool) {
                        OutlinedTextField(
                            value, { flags[name] = it; save() }, singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Btn("Copy all JSON", { copyText("flags", toJson(flags)) }, kind = 2, h = 40.dp)
                    Btn("Import clipboard", {
                        val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        val msg = runCatching {
                            val m = parseFlags(text)
                            flags.putAll(m); save()
                            "Imported ${m.size} flags from clipboard."
                        }.getOrElse { "Failed: clipboard isn't valid JSON." }
                        toast("flag", msg)
                    }, kind = 2, h = 40.dp)
                    Btn("Share", { share(toJson(flags), "Share flags") }, kind = 2, h = 40.dp)
                    Btn("JSON tools", { jsonText = toJson(flags); jsonMsg = ""; showJson = true }, kind = 1, h = 40.dp)
                    Btn("Import URL", { urlInput = ""; urlDialog = true }, kind = 2, h = 40.dp)
                    Btn("Clear all", { confirmClear = true }, kind = 2, h = 40.dp)
                }
            }
        }
    }

    @Composable
    fun PresetsPage() {
        val cs = MaterialTheme.colorScheme
        LazyColumn(
            state = presetsState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { PageHeader("Presets", "One-tap flag packs and your own profiles.") }
            item { SectionLabel("Flag packs") }
            PACKS.forEach { p ->
                item {
                    Group {
                        Row(
                            Modifier.padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconChip(packIcon(p.name), cs.primary, 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(p.desc, fontSize = 13.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant)
                                Text(
                                    "${p.sets.size} flags",
                                    fontSize = 12.sp, color = cs.primary, fontWeight = FontWeight.Medium
                                )
                            }
                            Btn("Apply", {
                                flags.applyPack(p); activeProfile = ""; save()
                                toast("flag", "Added ${p.name} pack.")
                            }, kind = 1, h = 40.dp)
                        }
                    }
                }
            }
            item { SectionLabel("Profiles") }
            item {
                Group {
                    Column(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Save current flags as a profile", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                pn, { pn = it }, label = { Text("Profile name") }, singleLine = true,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                            )
                            Btn("Save", {
                                if (pn.isNotBlank()) {
                                    presets[pn.trim()] = toJson(flags); pn = ""; save()
                                    toast("flag", "Profile saved.")
                                }
                            }, icon = Ic.Save)
                        }
                    }
                }
            }
            if (presets.isEmpty()) {
                item { EmptyHint("No profiles yet", "Save one above, then load it any time.") }
            }
            items(presets.keys.sorted(), key = { it }) { name ->
                val count = runCatching { parseFlags(presets[name] ?: "{}").size }.getOrDefault(0)
                Group {
                    Item(name, "$count flags" + if (activeProfile == name) "  -  active" else "")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton({
                            runCatching {
                                flags.clear()
                                flags.putAll(parseFlags(presets[name]!!))
                                activeProfile = name
                                save()
                                toast("flag", "Loaded $name.")
                            }
                        }) { Text("Load") }
                        TextButton({ renaming = name; newName = name }) { Text("Rename") }
                        TextButton({ duplicating = name; dupName = name + "_copy" }) { Text("Duplicate") }
                        TextButton({ copyText("profile $name", presets[name] ?: "{}") }) { Text("Export") }
                        TextButton({
                            presets.remove(name)
                            if (activeProfile == name) activeProfile = ""
                            save()
                        }) { Text("Delete", color = Bad) }
                    }
                }
            }
            item {
                Btn("Import profile from clipboard", {
                    val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    val msg = runCatching {
                        val m = parseFlags(text)
                        presets["Imported profile"] = toJson(m)
                        save()
                        "Imported ${m.size} flags as a profile."
                    }.getOrElse { "Failed: clipboard isn't valid JSON." }
                    toast("flag", msg)
                }, kind = 2, h = 44.dp)
            }
        }
    }

    @Composable
    fun GamesPage() {
        val cs = MaterialTheme.colorScheme
        LazyColumn(
            state = gamesState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageHeader("Games", "Launch by place ID or link, and pin favorites.") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Quick launch")
                    Group {
                        Column(
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                gn, { gn = it }, label = { Text("Name (optional)") }, singleLine = true,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                gl, { gl = it }, label = { Text("Place ID or Roblox link") }, singleLine = true,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Btn("Launch", {
                                    val raw = gl.trim()
                                    val label = gn.trim().ifEmpty { "Link launch" }
                                    if (normalizeLink(raw) == null) {
                                        toast("other", "Failed: enter a place ID or a Roblox link.")
                                    } else {
                                        playTarget(label, raw)
                                    }
                                }, Modifier.weight(1f), icon = Ic.Play)
                                Btn("Pin", {
                                    val raw = gl.trim()
                                    val id = extractPlaceId(raw)
                                    if (id == null) {
                                        toast("other", "Failed: could not find a place ID.")
                                    } else {
                                        games[gn.trim().ifEmpty { "Game $id" }] = id
                                        gn = ""; gl = ""; save()
                                        toast("other", "Game pinned.")
                                    }
                                }, Modifier.weight(1f), kind = 1, icon = Ic.Plus)
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Pinned (${games.size})") }
            if (games.isEmpty()) {
                item { EmptyHint("No games yet", "Paste a place ID or a game link above, then tap Pin.") }
            }
            items(games.keys.sorted(), key = { it }) { name ->
                Group {
                    Item(name, "Place ID ${games[name]}", { playTarget(name, games[name] ?: "") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Btn("Play", { playTarget(name, games[name] ?: "") }, kind = 1, h = 36.dp)
                            Spacer(Modifier.width(6.dp))
                            IconBtn(Ic.Trash, { games.remove(name); save() }, cs.onSurfaceVariant, 36.dp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsPage() {
        val cs = MaterialTheme.colorScheme
        LazyColumn(
            state = settingsState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageHeader("Settings") }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Appearance", "Roblox", "Bootstrapper", "Backup", "About").forEachIndexed { i, label ->
                        Pill(label, setTab == i) { setTab = i }
                    }
                }
            }
            when (setTab) {
                0 -> {
                    item { SectionLabel("Accent") }
                    item {
                        Group {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                THEMES.forEachIndexed { i, th ->
                                    val sel = themeIdx == i
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable { themeIdx = i; dynColor = false; save() }
                                    ) {
                                        Box(
                                            Modifier.size(46.dp)
                                                .clip(CircleShape)
                                                .background(Brush.linearGradient(listOf(th.primary, th.secondary)))
                                                .then(if (sel) Modifier.border(2.dp, cs.onSurface, CircleShape) else Modifier),
                                            contentAlignment = Alignment.Center
                                        ) { if (sel) Ico(Ic.Check, Color(0xFF1A1206), 20.dp, 2.4f) }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            th.name, fontSize = 12.sp,
                                            color = if (sel) cs.onSurface else cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        item { SectionLabel("System") }
                        item {
                            Group {
                                SwitchItem(
                                    "Dynamic color",
                                    "Use your wallpaper colors on Android 12 and up.",
                                    dynColor, leading = Ic.Palette
                                ) { dynColor = it; save() }
                            }
                        }
                    }
                    item { SectionLabel("Surface") }
                    item {
                        Group {
                            SwitchItem("True black", "Pure black background for OLED screens.", trueBlack, leading = Ic.Star) { trueBlack = it; save() }
                            SwitchItem("Compact lists", "Tighter spacing between rows.", compact, leading = Ic.Sort) { compact = it; save() }
                        }
                    }
                    item { SectionLabel("Shape") }
                    item {
                        Group {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Row {
                                    Text("Background glow", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    ValueText("${(glowAmt * 200).toInt()}%")
                                }
                                Slider(
                                    value = glowAmt, onValueChange = { glowAmt = it },
                                    valueRange = 0f..0.5f, onValueChangeFinished = { save() }
                                )
                            }
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Row {
                                    Text("Corner roundness", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    ValueText("${cornerDp.toInt()} dp")
                                }
                                Slider(
                                    value = cornerDp, onValueChange = { cornerDp = it },
                                    valueRange = 6f..40f, onValueChangeFinished = { save() }
                                )
                            }
                        }
                    }
                    item { SectionLabel("Messages") }
                    item {
                        Group {
                            Item("Errors always show, whatever you turn off here.")
                            SwitchItem("FastFlag messages", "Confirms when flags are applied, imported or changed.", msgFlag, leading = Ic.Flag) { msgFlag = it; save() }
                            SwitchItem("Launch messages", "Confirms when Roblox or a game is launched.", msgLaunch, leading = Ic.Play) { msgLaunch = it; save() }
                            SwitchItem("Other messages", "Confirms copies, backups and other actions.", msgOther, leading = Ic.Bell) { msgOther = it; save() }
                        }
                    }
                }
                1 -> {
                    item { SectionLabel("Connection") }
                    item {
                        Group {
                            Item("Roblox", if (robloxVersion == "Not found") "Not found" else "Installed, version $robloxVersion", leading = Ic.Game)
                            Item("Package name", pkg, { pkgInput = pkg; pkgDialog = true }, leading = Ic.Folder) {
                                Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                            }
                            Item("Shizuku", shStatus, leading = Ic.Bolt) {
                                Btn("Grant", {
                                    try {
                                        Shizuku.requestPermission(1001)
                                    } catch (e: Throwable) {
                                        toast("other", "Failed: start Shizuku first.")
                                    }
                                }, kind = 1, h = 36.dp)
                            }
                            SwitchItem(
                                "Use root",
                                if (rootAvailable) "Apply flags with superuser instead of Shizuku."
                                else "No root detected on this device.",
                                useRoot, leading = Ic.Warn
                            ) { useRoot = it; save() }
                        }
                    }
                    item { SectionLabel("Flag file") }
                    item {
                        Group {
                            Column(
                                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    path, { path = it; save() }, label = { Text("Flag file path") },
                                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                                )
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Pill("Default", path == DEFAULT_PATH) { path = DEFAULT_PATH; save() }
                                    val appPath = "/data/data/$pkg/files/ClientSettings/ClientAppSettings.json"
                                    Pill("App data (root)", path == appPath) { path = appPath; save() }
                                }
                            }
                            SwitchItem(
                                "Disable frame-rate cap",
                                "Always lifts Android's game frame cap when you apply. Done automatically for limits above 60 FPS.",
                                fps, leading = Ic.Bolt
                            ) { fps = it; save() }
                        }
                    }
                }
                2 -> {
                    item { SectionLabel("Launch") }
                    item {
                        Group {
                            SwitchItem(
                                "Apply flags before launching",
                                "Writes and verifies your flags every time you launch from Home.",
                                applyBeforeLaunch, leading = Ic.Flag
                            ) { applyBeforeLaunch = it; save() }
                            SwitchItem(
                                "Restart Roblox before launching",
                                "Force-stops Roblox first so new flags are always read.",
                                restart, leading = Ic.Refresh
                            ) { restart = it; save() }
                            SwitchItem(
                                "Launch Roblox when TacoStrap opens",
                                "Takes effect the next time you open TacoStrap, once Shizuku is connected.",
                                autoLaunch, leading = Ic.Play
                            ) { autoLaunch = it; save() }
                        }
                    }
                    item { SectionLabel("Memory trimmer") }
                    item {
                        Group {
                            SwitchItem(
                                "Automatic trimming",
                                "Asks Roblox to release memory on a timer. Runs while TacoStrap is alive in the background.",
                                trimAuto, leading = Ic.Memory
                            ) { trimAuto = it; save() }
                            Item("Interval", "How often to trim") {
                                ChoiceBox(
                                    TRIM_LABELS[TRIM_INTERVALS.indexOf(trimInterval).coerceAtLeast(0)],
                                    TRIM_LABELS
                                ) { trimInterval = TRIM_INTERVALS[it]; save() }
                            }
                            Item("Strength", "Stronger levels free more memory") {
                                ChoiceBox(TRIM_LEVELS[trimLevelIdx].first, TRIM_LEVELS.map { it.first }) {
                                    trimLevelIdx = it; save()
                                }
                            }
                            Item("Trim now", "Runs one trim immediately", {
                                scope.launch(Dispatchers.IO) {
                                    toast("other", trimMemory(pkg, TRIM_LEVELS[trimLevelIdx].second, useRoot))
                                }
                            }, leading = Ic.Bolt) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                        }
                    }
                    item { SectionLabel("Cleanup") }
                    item {
                        Group {
                            Item("Clear Roblox cache", "Frees up space", {
                                scope.launch(Dispatchers.IO) { toast("other", clearCache(pkg, useRoot)) }
                            }, leading = Ic.Trash) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                            Item("Close Roblox", "Force-stops the app", {
                                scope.launch(Dispatchers.IO) { toast("launch", forceStop(pkg, useRoot)) }
                            }, leading = Ic.Close) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                            Item("Delete flag file", "Removes the file and stops Roblox", {
                                scope.launch(Dispatchers.IO) { toast("flag", resetFlags(path, pkg, useRoot)) }
                            }, leading = Ic.Refresh) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                        }
                    }
                    item { SectionLabel("Instances") }
                    item {
                        Group {
                            val all = listOf("Roblox" to DEFAULT_PKG) +
                                instances.keys.sorted().map { n -> n to (instances[n] ?: "") }
                            all.forEach { (n, p) ->
                                Item(
                                    n,
                                    p + if (pkg == p) "  -  active" else "",
                                    { pkg = p; save() },
                                    leading = Ic.Game
                                ) {
                                    if (pkg == p) {
                                        Ico(Ic.Check, cs.primary, 20.dp)
                                    } else if (n != "Roblox") {
                                        IconBtn(Ic.Trash, { instances.remove(n); save() }, sz = 36.dp)
                                    }
                                }
                            }
                            Column(
                                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Add an instance, such as a Roblox clone", fontSize = 14.sp, color = cs.onSurfaceVariant)
                                OutlinedTextField(
                                    inName, { inName = it }, label = { Text("Name") }, singleLine = true,
                                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        inPkg, { inPkg = it }, label = { Text("Package") }, singleLine = true,
                                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                                    )
                                    Btn("Add", {
                                        if (inName.isNotBlank() && inPkg.isNotBlank()) {
                                            instances[inName.trim()] = inPkg.trim()
                                            inName = ""; inPkg = ""; save()
                                            toast("other", "Instance added.")
                                        }
                                    }, icon = Ic.Plus)
                                }
                            }
                        }
                    }
                }
                3 -> {
                    item { SectionLabel("Backup and restore") }
                    item {
                        Group {
                            Item(
                                "Export", "Flags, profiles, games and instances as one JSON file.",
                                { share(backupJson(), "Export backup") }, leading = Ic.Save
                            ) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                            Item("Import", "Restores a backup from the clipboard.", {
                                val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                val msg = runCatching {
                                    val o = JSONObject(text)
                                    o.optJSONObject("flags")?.let { j -> flags.clear(); flags.putAll(parseFlags(j.toString())) }
                                    o.optJSONObject("presets")?.let { j -> presets.clear(); presets.putAll(parseFlags(j.toString())) }
                                    o.optJSONObject("games")?.let { j -> games.clear(); games.putAll(parseFlags(j.toString())) }
                                    o.optJSONObject("instances")?.let { j -> instances.clear(); instances.putAll(parseFlags(j.toString())) }
                                    activeProfile = ""
                                    save()
                                    "Backup imported."
                                }.getOrElse { "Failed: copy a backup to the clipboard first." }
                                toast("other", msg)
                            }, leading = Ic.Download) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                        }
                    }
                    item { SectionLabel("Tools") }
                    item {
                        Group {
                            Item("Diagnostics", "A report you can copy for support", {
                                val report = "TacoStrap $APP_VERSION\nAndroid ${Build.VERSION.SDK_INT}\n" +
                                    "Shizuku: $shStatus\nRoot available: $rootAvailable (in use: $useRoot)\n" +
                                    "Package: $pkg ($robloxVersion)\nPath: $path\nFlags: ${flags.size}\nProfiles: ${presets.size}"
                                copyText("diagnostics", report)
                            }, leading = Ic.Doc) { Ico(Ic.Copy, cs.onSurfaceVariant, 20.dp) }
                        }
                    }
                }
                else -> {
                    item { SectionLabel("About") }
                    item {
                        Group {
                            Item("Changelog", "What's new in $APP_VERSION", { changelog = true }, leading = Ic.Doc) {
                                Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                            }
                            Item("Licenses", "Open source notices", { licenses = true }, leading = Ic.Info) {
                                Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                            }
                            Item("Roblox allowlist", "Flags Roblox allows on PC", { openUrl(ALLOWLIST_URL) }, leading = Ic.Globe) {
                                Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                            }
                            Item("Shizuku website", "Set up wireless debugging", { openUrl(SHIZUKU_URL) }, leading = Ic.Globe) {
                                Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                            }
                        }
                    }
                    item {
                        Text(
                            "TacoStrap for Android $APP_VERSION",
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            fontSize = 13.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // ================= ROOT UI =================

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalCorner provides cornerDp.dp,
            LocalPad provides (if (compact) 10.dp else 16.dp)
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(pc.copy(alpha = glowAmt), Color.Transparent),
                            center = Offset(700f, -80f),
                            radius = 1100f
                        )
                    )
                    .background(
                        Brush.radialGradient(
                            colors = listOf(sc.copy(alpha = glowAmt * 0.8f), Color.Transparent),
                            center = Offset(300f, 1900f),
                            radius = 1000f
                        )
                    )
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = {
                        SnackbarHost(snackbar) { d ->
                            Snackbar(
                                d, shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    bottomBar = { FloatingNav(navItems, page) { page = it } }
                ) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        AnimatedContent(
                            targetState = page,
                            transitionSpec = {
                                (fadeIn() + slideInHorizontally { it / 18 }) togetherWith
                                    (fadeOut() + slideOutHorizontally { -it / 24 })
                            },
                            label = "TacoStrapPageTransition"
                        ) { targetPage ->
                            when (targetPage) {
                                0 -> HomePage()
                                1 -> FlagsPage()
                                2 -> PresetsPage()
                                3 -> GamesPage()
                                else -> SettingsPage()
                            }
                        }
                    }
                }
            }

            // ---------- Dialogs ----------

            if (onboarding) {
                AlertDialog(
                    onDismissRequest = { },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Welcome to TacoStrap $APP_VERSION") },
                    text = {
                        Text(
                            "TacoStrap writes a ClientAppSettings.json file that Roblox reads on launch, " +
                                "and works as a small bootstrapper: launch, close, trim memory, clear cache and open games by link.\n\n" +
                                "You need one of these for it to work:\n" +
                                "  - Shizuku running on the device, with permission granted to this app, or\n" +
                                "  - Root access (Settings, Roblox, Use root).\n\n" +
                                "Check the status cards on the Home tab. If Shizuku is not running, start it first, then tap Grant."
                        )
                    },
                    confirmButton = {
                        TextButton({
                            onboarding = false
                            prefs.edit().putBoolean("seen5", true).apply()
                        }) { Text("Got it") }
                    }
                )
            }

            renaming?.let { old ->
                AlertDialog(
                    onDismissRequest = { renaming = null },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Rename profile") },
                    text = { OutlinedTextField(newName, { newName = it }, singleLine = true) },
                    confirmButton = {
                        TextButton({
                            val n = newName.trim()
                            if (n.isNotEmpty() && n != old) {
                                presets[old]?.let {
                                    presets[n] = it
                                    presets.remove(old)
                                    if (activeProfile == old) activeProfile = n
                                    save()
                                }
                            }
                            renaming = null
                        }) { Text("Rename") }
                    },
                    dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } }
                )
            }

            duplicating?.let { old ->
                AlertDialog(
                    onDismissRequest = { duplicating = null },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Duplicate") },
                    text = { OutlinedTextField(dupName, { dupName = it }, singleLine = true, label = { Text("New name") }) },
                    confirmButton = {
                        TextButton({
                            val n = dupName.trim()
                            if (n.isNotEmpty()) {
                                if (flags.containsKey(old)) {
                                    flags[n] = flags[old] ?: ""
                                } else {
                                    presets[old]?.let { presets[n] = it }
                                }
                                activeProfile = ""
                                save()
                            }
                            duplicating = null
                        }) { Text("Duplicate") }
                    },
                    dismissButton = { TextButton({ duplicating = null }) { Text("Cancel") } }
                )
            }

            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Clear all flags?") },
                    text = { Text("This removes all ${flags.size} flags from the list. Saved profiles are kept.") },
                    confirmButton = {
                        TextButton({
                            flags.clear(); activeProfile = ""; save(); confirmClear = false
                        }) { Text("Clear", color = Bad) }
                    },
                    dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } }
                )
            }

            if (pkgDialog) {
                AlertDialog(
                    onDismissRequest = { pkgDialog = false },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
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

            if (showJson) {
                AlertDialog(
                    onDismissRequest = { showJson = false },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("JSON tools") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                jsonText, { jsonText = it }, label = { Text("ClientAppSettings JSON") },
                                minLines = 6, maxLines = 12,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                            )
                            if (jsonMsg.isNotEmpty()) {
                                Text(jsonMsg, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton({
                            jsonMsg = runCatching {
                                val m = parseFlags(jsonText)
                                flags.clear(); flags.putAll(m); activeProfile = ""; save()
                                "Loaded ${m.size} flags."
                            }.getOrElse { "Invalid JSON." }
                        }) { Text("Replace flags") }
                    },
                    dismissButton = {
                        TextButton({
                            jsonText = toJson(flags); jsonMsg = ""
                        }) { Text("Load current") }
                    }
                )
            }

            if (urlDialog) {
                AlertDialog(
                    onDismissRequest = { urlDialog = false },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Import flags from URL") },
                    text = {
                        OutlinedTextField(
                            urlInput, { urlInput = it }, singleLine = true,
                            label = { Text("https://...json") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                    },
                    confirmButton = {
                        TextButton({
                            val u = urlInput.trim()
                            urlDialog = false
                            if (u.isNotEmpty()) {
                                scope.launch(Dispatchers.IO) {
                                    val (ok, body) = fetchText(u)
                                    val msg = if (ok) runCatching {
                                        val m = parseFlags(body)
                                        flags.putAll(m); save()
                                        "Imported ${m.size} flags from URL."
                                    }.getOrElse { "Failed: the file isn't valid JSON." }
                                    else "Failed: $body"
                                    toast("flag", msg)
                                }
                            }
                        }) { Text("Import") }
                    },
                    dismissButton = { TextButton({ urlDialog = false }) { Text("Cancel") } }
                )
            }

            if (licenses) {
                AlertDialog(
                    onDismissRequest = { licenses = false },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("Licenses") },
                    text = { Text("TacoStrap is built with Jetpack Compose and Shizuku (Apache License 2.0).") },
                    confirmButton = { TextButton({ licenses = false }) { Text("Close") } }
                )
            }

            if (changelog) {
                AlertDialog(
                    onDismissRequest = { changelog = false },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    title = { Text("What's new in $APP_VERSION") },
                    text = {
                        Text(
                            "Fixed in $APP_VERSION:\n" +
                                "  - Removed the dead write-on-save setting. Use Apply or Launch on Home\n" +
                                "  - Links now try Roblox first, then fall back to any app that can open them\n" +
                                "  - Launch on open no longer fires when you flip the switch\n" +
                                "  - Apply now saves your flags before writing the file\n\n" +
                                "New in 5.0:\n" +
                                "  - Smoother page transitions with remembered scroll positions\n" +
                                "  - Bootstrapper tools: launch, close, memory trimmer, cache cleaner\n" +
                                "  - Launch by place ID or Roblox link, recent launches history\n" +
                                "  - Instances: save and switch between Roblox package names\n" +
                                "  - Apply flags before launching, and optional launch on open"
                        )
                    },
                    confirmButton = { TextButton({ changelog = false }) { Text("Close") } }
                )
            }
        }
    }
}
