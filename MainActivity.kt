package com.example.flagmanager

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.Base64
import kotlin.math.roundToInt

private const val DEFAULT_PKG = "com.roblox.client"
private const val DEFAULT_PATH = "/data/local/tmp/ClientAppSettings.json"
private const val FPS_PROP = "debug.graphics.game_default_frame_rate.disabled"
private const val FPS_KEY = "DFIntTaskSchedulerTargetFps"
private const val ALLOWLIST_URL =
    "https://devforum.roblox.com/t/allowlist-for-local-client-configuration-via-fast-flags/3966569"

// ---------- Logic (unchanged) ----------

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

// ---------- New helpers (additions only) ----------

/** Joins a game by link code (private server). Separate from launchGame so that stays untouched. */
fun launchLink(pkg: String, id: String, code: String, root: Boolean): String {
    val url = "roblox://experiences/start?placeId=$id" + (if (code.isNotBlank()) "&linkCode=$code" else "")
    val (ok, msg) = runCmd("am start -a android.intent.action.VIEW -d '$url' -p $pkg", root)
    return if (ok && !msg.contains("Error")) "Joining private server." else "Failed: $msg"
}

fun forceStopPkg(pkg: String, root: Boolean): String {
    val (ok, msg) = runCmd("am force-stop $pkg", root)
    return if (ok) "Stopped Roblox." else "Failed: $msg"
}

/** Pulls only the version number out of dumpsys output so error text never leaks into the UI. */
fun parseVersion(out: String): String =
    Regex("versionName=([0-9][0-9.]*)").find(out)?.groupValues?.get(1) ?: "Not found"

/** (placeId, linkCode) from a number or any roblox.com link. */
fun parsePlace(raw: String): Pair<String, String>? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    if (s.all { it.isDigit() }) return Pair(s, "")
    val id = Regex("placeId=(\\d+)").find(s)?.groupValues?.get(1)
        ?: Regex("/games/(\\d+)").find(s)?.groupValues?.get(1)
        ?: return null
    val code = Regex("(?:privateServerLinkCode|linkCode)=([A-Za-z0-9_-]+)").find(s)?.groupValues?.get(1) ?: ""
    return Pair(id, code)
}

fun flagKind(name: String): String = when {
    name.startsWith("FFlag") || name.startsWith("DFFlag") || name.startsWith("SFFlag") -> "Boolean"
    name.startsWith("FInt") || name.startsWith("DFInt") || name.startsWith("SFInt") ||
        name.startsWith("FLog") || name.startsWith("DFLog") -> "Number"
    else -> "Text"
}

private val FLAG_NAME = Regex("^(D?F|SF)(Flag|Int|String|Log)[A-Za-z0-9_]+$")
fun validFlag(n: String): Boolean = FLAG_NAME.matches(n)

fun ago(t: Long): String {
    if (t <= 0L) return "never"
    val s = (System.currentTimeMillis() - t) / 1000
    return when {
        s < 45 -> "just now"
        s < 3600 -> "${maxOf(1L, s / 60)} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
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
    AppTheme("Rose", Color(0xFFFF8FB1), Color(0xFFE91E63)),
    AppTheme("Mint", Color(0xFF6EE7B7), Color(0xFF10B981))
)

private data class Opt(val label: String, val sets: Map<String, String>)
private data class Pack(val name: String, val desc: String, val sets: Map<String, String>)
private data class Known(val name: String, val sample: String, val desc: String)

private sealed class FS {
    abstract val title: String

    data class Toggle(override val title: String, val sub: String, val sets: Map<String, String>) : FS()
    data class Choice(override val title: String, val sub: String, val opts: List<Opt>) : FS()
    data class Num(override val title: String, val sub: String, val key: String) : FS()
    data class Text(override val title: String, val sub: String, val key: String, val hint: String) : FS()
    data class Range(
        override val title: String, val sub: String,
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

// New settings (additions)
private val FS_FULLRES = FS.Toggle("Full resolution", "Ignore display scaling for a sharper picture", mapOf("DFFlagDisableDPIScale" to "True"))
private val FS_MESH = FS.Toggle(
    "Mesh detail", "Lower detail on distant objects",
    mapOf(
        "DFIntCSGLevelOfDetailSwitchingDistance" to "0",
        "DFIntCSGLevelOfDetailSwitchingDistanceL12" to "0",
        "DFIntCSGLevelOfDetailSwitchingDistanceL23" to "0",
        "DFIntCSGLevelOfDetailSwitchingDistanceL34" to "0"
    )
)
private val FS_HIDEGUI = FS.Num("Hide GUIs", "Group ID that can hide the interface", "DFIntCanHideGuiGroupId")
private val FS_FONT = FS.Num("Font size", "Extra padding for menu fonts", "FIntFontSizePadding")
private val FS_LAG = FS.Toggle("Reduce lag spikes", "Smooths out network output", mapOf("DFIntS2PhysicsSenderRate" to "38000"))
private val FS_FLAGSTATE = FS.Text(
    "Flag state", "Shows these flag values in game, comma separated",
    "FStringDebugShowFlagState", "FlagA,FlagB"
)
private val FS_PING = FS.Toggle("Ping breakdown", "Prints ping details to the Roblox console", mapOf("DFFlagDebugPrintDataPingBreakDown" to "True"))

private val SECTIONS: List<Pair<String, List<FS>>> = listOf(
    "Graphics" to listOf(FS_TEXTURE, FS_AA, FS_RENDER, FS_SKY, FS_FULLRES, FS_SHADOWS, FS_POSTFX),
    "Performance" to listOf(FS_FPS, FS_FPSCOUNT),
    "World detail" to listOf(FS_QUALITY, FS_MESH, FS_GRASSMOVE, FS_NOGRASS, FS_GRASSDIST),
    "Interface" to listOf(FS_BLUR, FS_HIDEGUI, FS_FONT),
    "Network" to listOf(FS_PRELOAD, FS_LAG, FS_MTU),
    "Debug" to listOf(FS_FLAGSTATE, FS_PING)
)

private val PACKS = listOf(
    Pack(
        "Balanced", "Medium textures and 2x anti aliasing",
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
    ),
    Pack(
        "Potato", "Everything low for weak devices",
        texSet("0") + mapOf(
            "FIntDebugForceMSAASamples" to "1",
            "FIntRenderShadowIntensity" to "0",
            "FFlagDisablePostFx" to "True",
            "FIntRobloxGuiBlurIntensity" to "0",
            "FIntFRMMinGrassDistance" to "0",
            "FIntFRMMaxGrassDistance" to "0",
            "FIntRomarkStartWithGraphicQualityLevel" to "1"
        )
    )
)

private val KNOWN = listOf(
    Known("DFIntTaskSchedulerTargetFps", "120", "Target frame rate"),
    Known("FFlagTaskSchedulerLimitTargetFpsTo2402", "False", "Lets the frame rate go above 60"),
    Known("FFlagDebugDisplayFPS", "True", "Shows the frame rate counter"),
    Known("FStringDebugShowFlagState", "DFIntTaskSchedulerTargetFps", "Shows flag values in game"),
    Known("DFFlagTextureQualityOverrideEnabled", "True", "Enables the texture override"),
    Known("DFIntTextureQualityOverride", "1", "Texture level, 0 lowest to 3 highest"),
    Known("FIntDebugForceMSAASamples", "1", "Anti aliasing samples"),
    Known("FFlagDisablePostFx", "True", "Turns off post processing"),
    Known("FFlagDebugSkyGray", "True", "Plain gray sky"),
    Known("FIntRenderShadowIntensity", "0", "Shadow strength, 0 is off"),
    Known("FIntRobloxGuiBlurIntensity", "0", "Blur behind menus"),
    Known("FIntFRMMinGrassDistance", "0", "Closest grass distance"),
    Known("FIntFRMMaxGrassDistance", "0", "Farthest grass distance"),
    Known("FIntGrassMovementReducedMotionFactor", "0", "Grass sway"),
    Known("FIntRomarkStartWithGraphicQualityLevel", "5", "Starting graphics level"),
    Known("DFFlagDisableDPIScale", "True", "Ignores display scaling"),
    Known("FFlagDebugGraphicsPreferVulkan", "True", "Prefers Vulkan"),
    Known("FFlagDebugGraphicsPreferOpenGL", "True", "Prefers OpenGL"),
    Known("DFIntCanHideGuiGroupId", "0", "Group allowed to hide the GUI"),
    Known("FIntFontSizePadding", "1", "Menu font padding"),
    Known("DFIntConnectionMTUSize", "1400", "Network packet size"),
    Known("DFIntS2PhysicsSenderRate", "38000", "Physics send rate"),
    Known("DFIntNumAssetsMaxToPreload", "2147483647", "Assets to preload"),
    Known("DFIntAssetPreloading", "2147483647", "Asset preloading"),
    Known("DFFlagDebugPrintDataPingBreakDown", "True", "Ping breakdown in console")
)

private val LocalCorner = compositionLocalOf { 24.dp }
private val LocalPad = compositionLocalOf { 16.dp }
private val LocalMotion = compositionLocalOf { true }
private val LocalHaptic = compositionLocalOf<() -> Unit> { {} }

private val Amber = Color(0xFFE8B84A)
private val Good = Color(0xFF5BC27A)
private val Bad = Color(0xFFEF6B63)

// ---------- Icons (drawn, no emoji) ----------

private enum class Ic {
    Home, Grid, Layers, Flag, Sliders, Right, Down, Up, Back, Close, Plus,
    Info, Warn, Play, Check, Download, Folder, Bell, Game, Doc, Search,
    Copy, Share, Trash, Refresh, Bolt, Pulse, Clock, Terminal, Shield, Bookmark, More, Pencil
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
                drawRoundRect(tint, Offset(8.5f * s, 8.5f * s), Size(11.5f * s, 11.5f * s), CornerRadius(2.2f * s), style = st)
                pl(tint, st, s, 15.5f, 8.5f, 15.5f, 5.5f, 14f, 4f, 5.5f, 4f, 4f, 5.5f, 4f, 14f, 5.5f, 15.5f, 8.5f, 15.5f)
            }
            Ic.Share -> {
                drawCircle(tint, 2.5f * s, Offset(6f * s, 12f * s), style = st)
                drawCircle(tint, 2.5f * s, Offset(18f * s, 6f * s), style = st)
                drawCircle(tint, 2.5f * s, Offset(18f * s, 18f * s), style = st)
                pl(tint, st, s, 8.2f, 10.8f, 15.8f, 7.2f)
                pl(tint, st, s, 8.2f, 13.2f, 15.8f, 16.8f)
            }
            Ic.Trash -> {
                pl(tint, st, s, 4f, 7f, 20f, 7f)
                pl(tint, st, s, 9f, 7f, 9f, 4.5f, 15f, 4.5f, 15f, 7f)
                pl(tint, st, s, 6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f)
                pl(tint, st, s, 10f, 10.5f, 10f, 16.5f)
                pl(tint, st, s, 14f, 10.5f, 14f, 16.5f)
            }
            Ic.Refresh -> {
                drawArc(tint, -40f, 290f, false, Offset(4f * s, 4f * s), Size(16f * s, 16f * s), style = st)
                pl(tint, st, s, 12.5f, 1.8f, 9.2f, 4.5f, 12.2f, 7.6f)
            }
            Ic.Bolt -> pl(tint, st, s, 13f, 2.5f, 5f, 13.5f, 11f, 13.5f, 10f, 21.5f, 19f, 10f, 13f, 10f, 13f, 2.5f)
            Ic.Pulse -> pl(tint, st, s, 2.5f, 12f, 7f, 12f, 10f, 5f, 14f, 19f, 17f, 12f, 21.5f, 12f)
            Ic.Clock -> {
                drawCircle(tint, 9f * s, Offset(12f * s, 12f * s), style = st)
                pl(tint, st, s, 12f, 7f, 12f, 12f, 15.5f, 14f)
            }
            Ic.Terminal -> {
                drawRoundRect(tint, Offset(3f * s, 4.5f * s), Size(18f * s, 15f * s), CornerRadius(3f * s), style = st)
                pl(tint, st, s, 7f, 9.5f, 10.5f, 12.5f, 7f, 15.5f)
                pl(tint, st, s, 12.5f, 15.5f, 17f, 15.5f)
            }
            Ic.Shield -> pl(tint, st, s, 12f, 3f, 19.5f, 6f, 19.5f, 12f, 17.5f, 17f, 12f, 21f, 6.5f, 17f, 4.5f, 12f, 4.5f, 6f, 12f, 3f)
            Ic.Bookmark -> pl(tint, st, s, 6f, 3.5f, 18f, 3.5f, 18f, 21f, 12f, 17f, 6f, 21f, 6f, 3.5f)
            Ic.More -> {
                drawCircle(tint, 1.7f * s, Offset(12f * s, 5.5f * s))
                drawCircle(tint, 1.7f * s, Offset(12f * s, 12f * s))
                drawCircle(tint, 1.7f * s, Offset(12f * s, 18.5f * s))
            }
            Ic.Pencil -> {
                pl(tint, st, s, 4f, 20f, 4f, 16f, 16f, 4f, 20f, 8f, 8f, 20f, 4f, 20f)
                pl(tint, st, s, 13.5f, 6.5f, 17.5f, 10.5f)
            }
        }
    }
}

// ---------- Reusable UI ----------

@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    if (!LocalMotion.current) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(minOf(index, 8) * 55L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(360)) + slideInVertically(tween(360)) { it / 8 }
    ) { content() }
}

@Composable
private fun Group(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(LocalCorner.current)
    Column(
        modifier.fillMaxWidth()
            .animateContentSize()
            .clip(shape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), shape)
            .padding(vertical = 4.dp),
        content = content
    )
}

@Composable
private fun Item(
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    leading: Ic? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHaptic.current
    val m = if (onClick != null) Modifier.clickable { haptic(); onClick() } else Modifier
    Row(
        Modifier.fillMaxWidth().then(m).padding(horizontal = 20.dp, vertical = LocalPad.current),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Ico(leading, cs.primary, 20.dp) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
            if (sub != null) {
                Text(sub, fontSize = 13.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant)
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
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 190.dp)
    )
}

@Composable
private fun TacoSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHaptic.current
    val track by animateColorAsState(if (checked) cs.primary else Color(0xFF3A3632), tween(220), label = "swt")
    val thumb by animateColorAsState(if (checked) cs.onPrimary else Color(0xFF9A948C), tween(220), label = "swc")
    val x by animateDpAsState(
        if (checked) 25.dp else 4.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "swx"
    )
    Box(
        Modifier.width(54.dp).height(31.dp).clip(CircleShape).background(track)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic()
                onChange(!checked)
            }
    ) {
        Box(Modifier.offset(x = x, y = 3.5.dp).size(24.dp).clip(CircleShape).background(thumb))
    }
}

@Composable
private fun SwitchItem(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Item(title, sub, { onChange(!checked) }) { TacoSwitch(checked, onChange) }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp)
    )
}

@Composable
private fun PageHeader(title: String, sub: String? = null) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 14.dp)) {
        Text(
            title, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .clickable { open = true }
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                current, fontSize = 14.sp, maxLines = 1, color = cs.onSurface,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp)
            )
            Spacer(Modifier.width(4.dp))
            Ico(Ic.Down, cs.onSurfaceVariant, 16.dp, 2.2f)
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
    kt: KeyboardType = KeyboardType.Number,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value, onChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(hint, fontSize = 14.sp) },
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = kt),
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
    h: Dp = 48.dp,
    loading: Boolean = false,
    fs: Int = 15
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHaptic.current
    val sc by animateFloatAsState(
        if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "btn"
    )
    val cs = MaterialTheme.colorScheme
    val bg = when (kind) {
        0 -> cs.primary
        1 -> cs.primary.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val fg = when (kind) {
        0 -> cs.onPrimary
        1 -> cs.primary
        else -> cs.onSurface
    }
    Row(
        modifier.height(h)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(CircleShape)
            .background(bg)
            .then(if (kind == 2) Modifier.border(1.dp, cs.outline, CircleShape) else Modifier)
            .clickable(interactionSource = src, indication = null) {
                if (!loading) {
                    haptic()
                    onClick()
                }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Ico(icon, fg, 18.dp, 2.1f)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, color = fg, fontSize = fs.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHaptic.current
    val bg by animateColorAsState(if (selected) cs.primary else Color.Transparent, tween(220), label = "pbg")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, tween(220), label = "pfg")
    Box(
        Modifier.clip(CircleShape).background(bg)
            .border(1.dp, if (selected) Color.Transparent else cs.outline, CircleShape)
            .clickable { haptic(); onClick() }
    ) {
        Text(
            text, Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1
        )
    }
}

@Composable
private fun TabStrip(items: List<Pair<String, Ic>>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHaptic.current
    val scroll = rememberScrollState()
    val xs = remember(items.size) { mutableStateListOf<Float>().also { l -> repeat(items.size) { l.add(0f) } } }
    val ws = remember(items.size) { mutableStateListOf<Float>().also { l -> repeat(items.size) { l.add(0f) } } }
    var viewport by remember { mutableStateOf(0) }
    val padPx = with(density) { 16.dp.toPx() }
    val ix by animateFloatAsState(
        xs.getOrElse(selected) { 0f } + padPx, spring(dampingRatio = 0.8f, stiffness = 380f), label = "ix"
    )
    val iw by animateFloatAsState(
        (ws.getOrElse(selected) { 0f } - 2 * padPx).coerceAtLeast(0f),
        spring(dampingRatio = 0.8f, stiffness = 380f), label = "iw"
    )

    LaunchedEffect(selected, xs.getOrElse(selected) { 0f }, viewport) {
        val x = xs.getOrElse(selected) { 0f }
        val w = ws.getOrElse(selected) { 0f }
        if (viewport > 0 && w > 0f) {
            scroll.animateScrollTo((x + w / 2f - viewport / 2f).toInt().coerceIn(0, scroll.maxValue))
        }
    }

    Column(Modifier.fillMaxWidth().onSizeChanged { viewport = it.width }) {
        Box(Modifier.horizontalScroll(scroll)) {
            Row {
                items.forEachIndexed { i, (label, ic) ->
                    val sel = i == selected
                    val c by animateColorAsState(if (sel) cs.primary else cs.onSurfaceVariant, tween(220), label = "tc")
                    Column(
                        Modifier
                            .onGloballyPositioned {
                                val nx = it.positionInParent().x
                                val nw = it.size.width.toFloat()
                                if (xs[i] != nx) xs[i] = nx
                                if (ws[i] != nw) ws[i] = nw
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                haptic()
                                onSelect(i)
                            }
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Ico(ic, c, 24.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c, maxLines = 1)
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomStart)
                    .offset { IntOffset(ix.roundToInt(), 0) }
                    .width(with(density) { iw.toDp() })
                    .height(3.dp).clip(CircleShape).background(cs.primary)
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
    }
}

@Composable
private fun NavBar(items: List<Pair<String, Ic>>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHaptic.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
        items.forEachIndexed { i, (label, ic) ->
            val sel = i == selected
            val tint by animateColorAsState(if (sel) cs.primary else cs.onSurfaceVariant, tween(220), label = "nt")
            val pillW by animateDpAsState(
                if (sel) 60.dp else 30.dp, spring(dampingRatio = 0.8f, stiffness = 400f), label = "pw"
            )
            val pillA by animateFloatAsState(if (sel) 1f else 0f, tween(220), label = "pa")
            Column(
                Modifier.weight(1f).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { haptic(); onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.width(pillW).height(32.dp).clip(CircleShape)
                        .background(cs.primary.copy(alpha = 0.18f * pillA)),
                    contentAlignment = Alignment.Center
                ) { Ico(ic, tint, 22.dp) }
                Spacer(Modifier.height(4.dp))
                Text(
                    label, fontSize = 11.sp, color = tint, maxLines = 1,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun Orb(color: Color, icon: Ic, busy: Boolean, size: Dp = 64.dp) {
    val cs = MaterialTheme.colorScheme
    val inf = rememberInfiniteTransition(label = "orb")
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "spin"
    )
    val sweep by animateFloatAsState(if (busy) 110f else 360f, tween(500), label = "sweep")
    val c by animateColorAsState(color, tween(400), label = "orbc")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 5.dp.toPx()
            val tl = Offset(w / 2, w / 2)
            val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(cs.outlineVariant, 0f, 360f, false, tl, sz, style = Stroke(w))
            drawArc(c, if (busy) spin else -90f, sweep, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Ico(icon, c, size / 2.3f)
    }
}

@Composable
private fun Spark(values: List<Float>, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val hi = values.maxOrNull() ?: 1f
        val lo = values.minOrNull() ?: 0f
        val span = (hi - lo).coerceAtLeast(hi * 0.1f).coerceAtLeast(1f)
        val line = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * (1f - ((v - lo) / span).coerceIn(0f, 1f)) * 0.9f + size.height * 0.05f
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, c, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun StatTile(label: String, value: String, ic: Ic, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(LocalCorner.current)
    Column(
        modifier.clip(shape).background(cs.surfaceVariant)
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.7f), shape).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(ic, cs.primary, 16.dp, 2.2f)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GameAvatar(name: String, id: String, size: Dp = 44.dp) {
    val hue = ((id.hashCode().toLong().let { if (it < 0) -it else it }) % 360).toFloat()
    val a = Color.hsv(hue, 0.55f, 0.95f)
    val b = Color.hsv((hue + 40f) % 360f, 0.7f, 0.75f)
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?", color = Color.White,
            fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp
        )
    }
}

@Composable
private fun IconBtn(ic: Ic, tint: Color? = null, size: Dp = 40.dp, onClick: () -> Unit) {
    val haptic = LocalHaptic.current
    Box(
        Modifier.size(size).clip(CircleShape).clickable { haptic(); onClick() },
        contentAlignment = Alignment.Center
    ) { Ico(ic, tint ?: MaterialTheme.colorScheme.onSurfaceVariant, 20.dp) }
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
        is FS.Text -> {
            Item(fs.title, fs.sub) {
                NumBox(flags[fs.key] ?: "", "Off", Modifier.width(150.dp), KeyboardType.Text) { t ->
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
                    Text("  to  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val view = LocalView.current
    val activity = ctx as? Activity
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
    val recent = remember {
        mutableStateMapOf<String, String>().apply {
            runCatching { putAll(parseFlags(prefs.getString("recent", "{}")!!)) }
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
    var trueBlack by remember { mutableStateOf(prefs.getBoolean("trueBlack", false)) }
    var compact by remember { mutableStateOf(prefs.getBoolean("compact", false)) }
    var motion by remember { mutableStateOf(prefs.getBoolean("motion", true)) }
    var glowAmt by remember { mutableStateOf(prefs.getFloat("glow2", 0.10f)) }
    var cornerDp by remember { mutableStateOf(prefs.getFloat("corner2", 24f)) }
    var msgFlag by remember { mutableStateOf(prefs.getBoolean("msgFlag", true)) }
    var msgLaunch by remember { mutableStateOf(prefs.getBoolean("msgLaunch", true)) }
    var msgOther by remember { mutableStateOf(prefs.getBoolean("msgOther", true)) }

    // new settings
    var haptics by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }
    var keepAwake by remember { mutableStateOf(prefs.getBoolean("keepAwake", false)) }
    var confirmReset by remember { mutableStateOf(prefs.getBoolean("confirmReset", false)) }
    var applyBeforeGame by remember { mutableStateOf(prefs.getBoolean("applyBeforeGame", false)) }
    var textScale by remember { mutableStateOf(prefs.getFloat("textScale", 1f)) }
    var lastApplied by remember { mutableStateOf(prefs.getString("applied", null) ?: toJson(flags)) }

    var shStatus by remember { mutableStateOf(shizukuStatus()) }
    var robloxVersion by remember { mutableStateOf("-") }
    var busy by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf("") }

    // session monitor
    var running by remember { mutableStateOf(false) }
    var pid by remember { mutableStateOf("") }
    var uptime by remember { mutableStateOf("") }
    var memMb by remember { mutableStateOf(0) }
    val memHist = remember { mutableStateListOf<Float>() }

    // logs
    var logLines by remember { mutableStateOf(listOf<String>()) }
    var logLevel by remember { mutableStateOf("W") }
    var logOnly by remember { mutableStateOf(true) }
    var logLoading by remember { mutableStateOf(false) }

    var page by remember { mutableStateOf(0) }
    var modsTab by remember { mutableStateOf(0) }
    var setTab by remember { mutableStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var edTab by remember { mutableStateOf(0) }
    var quickIdx by remember { mutableStateOf(0) }

    var search by remember { mutableStateOf("") }
    var fsQuery by remember { mutableStateOf("") }
    var ksearch by remember { mutableStateOf("") }
    var k by remember { mutableStateOf("") }
    var v by remember { mutableStateOf("") }
    var pn by remember { mutableStateOf("") }
    var gn by remember { mutableStateOf("") }
    var gid by remember { mutableStateOf("") }
    var qj by remember { mutableStateOf("") }
    var qjCode by remember { mutableStateOf("") }
    var qjAdv by remember { mutableStateOf(false) }
    var jsonText by remember { mutableStateOf("") }
    var jsonMsg by remember { mutableStateOf("") }
    var fileOut by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var gameMenu by remember { mutableStateOf<String?>(null) }
    var gameRename by remember { mutableStateOf<String?>(null) }
    var gameNewName by remember { mutableStateOf("") }
    var pinDialog by remember { mutableStateOf(false) }
    var pinName by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmResetDlg by remember { mutableStateOf(false) }
    var confirmSettingsReset by remember { mutableStateOf(false) }
    var pkgDialog by remember { mutableStateOf(false) }
    var pkgInput by remember { mutableStateOf("") }
    var pkgMenu by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }

    fun save() {
        prefs.edit()
            .putString("flags", toJson(flags))
            .putString("presets", toJson(presets))
            .putString("games", toJson(games))
            .putString("recent", toJson(recent))
            .putString("applied", lastApplied)
            .putString("path2", path)
            .putString("pkg", pkg)
            .putBoolean("restart", restart)
            .putBoolean("fps", fps)
            .putBoolean("root", useRoot)
            .putBoolean("applyOnSave", applyOnSave)
            .putInt("theme", themeIdx)
            .putBoolean("animbg", animBg)
            .putBoolean("trueBlack", trueBlack)
            .putBoolean("compact", compact)
            .putBoolean("motion", motion)
            .putFloat("glow2", glowAmt)
            .putFloat("corner2", cornerDp)
            .putBoolean("msgFlag", msgFlag)
            .putBoolean("msgLaunch", msgLaunch)
            .putBoolean("msgOther", msgOther)
            .putBoolean("haptics", haptics)
            .putBoolean("keepAwake", keepAwake)
            .putBoolean("confirmReset", confirmReset)
            .putBoolean("applyBeforeGame", applyBeforeGame)
            .putFloat("textScale", textScale)
            .apply()
    }

    fun toast(kind: String, msg: String) {
        val allowed = when (kind) {
            "flag" -> msgFlag
            "launch" -> msgLaunch
            else -> msgOther
        }
        if (allowed || msg.startsWith("Failed")) {
            toastText = msg
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg)
            }
        }
    }

    fun doSave(launch: Boolean) {
        save()
        scope.launch(Dispatchers.IO) {
            busy = true
            try {
                val msg = if (applyOnSave) applyFlags(toJson(flags), path, fps, useRoot) else "Saved in app."
                if (applyOnSave && !msg.startsWith("Failed")) {
                    lastApplied = toJson(flags)
                    prefs.edit().putString("applied", lastApplied).apply()
                }
                toast("flag", msg)
                if (launch && !msg.startsWith("Failed")) {
                    if (restart) runCmd("am force-stop $pkg && sleep 1", useRoot)
                    toast("launch", launchPkg(pkg, useRoot))
                }
            } finally {
                busy = false
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

    fun copyText(label: String, text: String, msg: String) {
        clip.setPrimaryClip(ClipData.newPlainText(label, text))
        toast("other", msg)
    }

    fun grant() {
        try {
            Shizuku.requestPermission(1001)
        } catch (e: Throwable) {
            toast("other", "Failed: start Shizuku first.")
        }
        shStatus = shizukuStatus()
    }

    fun openShizuku() {
        val i = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (i != null) ctx.startActivity(i) else toast("other", "Failed: couldn't open Shizuku, open it yourself.")
    }

    fun addRecent(id: String, name: String) {
        recent[id] = "$name\t${System.currentTimeMillis()}"
        if (recent.size > 12) {
            val oldest = recent.entries.minByOrNull { it.value.substringAfterLast('\t').toLongOrNull() ?: 0L }?.key
            if (oldest != null) recent.remove(oldest)
        }
        save()
    }

    fun playGame(name: String, id: String, code: String = "") {
        addRecent(id, name)
        scope.launch(Dispatchers.IO) {
            if (applyBeforeGame) {
                val m = applyFlags(toJson(flags), path, fps, useRoot)
                if (!m.startsWith("Failed")) {
                    lastApplied = toJson(flags)
                    runCmd("am force-stop $pkg && sleep 1", useRoot)
                }
                toast("flag", m)
            }
            toast("launch", if (code.isBlank()) launchGame(id, useRoot) else launchLink(pkg, id, code, useRoot))
        }
    }

    fun doReset() {
        scope.launch(Dispatchers.IO) { toast("flag", resetFlags(path, pkg, useRoot)) }
    }

    fun loadLogs() {
        scope.launch(Dispatchers.IO) {
            logLoading = true
            val (_, out) = runCmd("logcat -d -v threadtime -t 1500 '*:$logLevel'", useRoot)
            logLines = out.lines()
            logLoading = false
        }
    }

    fun verifyFile() {
        scope.launch(Dispatchers.IO) {
            val (_, out) = runCmd("ls -l '$path'; cat '$path'", useRoot)
            fileOut = out.ifEmpty { "Nothing returned." }
        }
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
            val (_, out) = withContext(Dispatchers.IO) {
                runCmd("dumpsys package $pkg | grep -m1 versionName", useRoot)
            }
            robloxVersion = parseVersion(out)
        }
    }

    LaunchedEffect(page, ready, useRoot, pkg) {
        if (page == 0 && (ready || useRoot)) {
            while (true) {
                val (_, out) = withContext(Dispatchers.IO) {
                    runCmd(
                        "PID=\$(pidof $pkg 2>/dev/null); echo \"P:\$PID\"; " +
                            "if [ -n \"\$PID\" ]; then ps -A -o PID,ETIME,RSS,NAME 2>/dev/null | grep -F '$pkg'; fi",
                        useRoot
                    )
                }
                val p0 = out.lines().firstOrNull { it.startsWith("P:") }?.removePrefix("P:")?.trim().orEmpty()
                running = p0.isNotEmpty()
                pid = p0.split(" ").firstOrNull().orEmpty()
                if (running) {
                    for (line in out.lines()) {
                        val f = line.trim().split(Regex("\\s+"))
                        if (f.size >= 4 && f.last() == pkg) {
                            uptime = f[1]
                            memMb = (f[2].toIntOrNull() ?: 0) / 1024
                            break
                        }
                    }
                    memHist.add(memMb.toFloat())
                    while (memHist.size > 40) memHist.removeAt(0)
                } else {
                    uptime = ""
                    memMb = 0
                    memHist.clear()
                }
                delay(3000)
            }
        } else if (page == 0) {
            running = false
        }
    }

    LaunchedEffect(Unit) {
        val w = activity?.window
        if (w != null) {
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.statusBarColor = android.graphics.Color.TRANSPARENT
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = false
            c.isAppearanceLightNavigationBars = false
        }
    }
    LaunchedEffect(keepAwake) {
        val w = activity?.window
        if (w != null) {
            if (keepAwake) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ----- Theme -----
    val theme = THEMES[themeIdx]
    val pc by animateColorAsState(theme.primary, tween(500), label = "pc")
    val sc by animateColorAsState(theme.secondary, tween(500), label = "sc")
    val base = if (trueBlack) Color.Black else Color(0xFF121110)
    val sv = lerp(if (trueBlack) Color(0xFF141414) else Color(0xFF1D1B19), pc, 0.04f)
    val scheme = darkColorScheme(
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

    val inf = rememberInfiniteTransition(label = "inf")
    val shift by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift"
    )
    val glow = if (animBg && motion) shift else 0.5f
    val flagCount by animateIntAsState(flags.size, tween(500), label = "count")
    val dirty = toJson(flags) != lastApplied
    val navItems = listOf(
        "Home" to Ic.Home, "Library" to Ic.Grid, "Mods" to Ic.Layers,
        "FastFlags" to Ic.Flag, "Settings" to Ic.Sliders
    )
    val pageAnim = if (motion) 260 else 0
    val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val tick: () -> Unit = remember(haptics) {
        { if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }

    // ================= PAGES =================

    @Composable
    fun ProfilesUI() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        })
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Import clipboard", {
                            val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            val msg = runCatching {
                                flags.putAll(parseFlags(text)); save(); "Imported from clipboard."
                            }.getOrElse { "Failed: clipboard isn't valid JSON." }
                            toast("flag", msg)
                        }, kind = 2, icon = Ic.Download, h = 40.dp)
                        Btn("Copy JSON", { copyText("flags", toJson(flags), "Copied JSON.") }, kind = 2, icon = Ic.Copy, h = 40.dp)
                        Btn("Share", { share(toJson(flags), "Share flags") }, kind = 2, icon = Ic.Share, h = 40.dp)
                    }
                }
            }
            if (presets.isEmpty()) {
                Group { Item("No profiles yet", "Save one above.", leading = Ic.Layers) }
            }
            presets.keys.sorted().forEach { name ->
                val count = runCatching { parseFlags(presets[name] ?: "{}").size }.getOrDefault(0)
                Group {
                    Item(name, "$count flags", leading = Ic.Layers)
                    Row(
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton({
                            runCatching {
                                flags.clear(); flags.putAll(parseFlags(presets[name]!!)); save()
                                toast("flag", "Loaded $name.")
                            }
                        }) { Text("Load") }
                        TextButton({ renaming = name; newName = name }) { Text("Rename") }
                        TextButton({
                            presets[name] = toJson(flags); save()
                            toast("flag", "Updated $name with current flags.")
                        }) { Text("Overwrite") }
                        TextButton({ presets.remove(name); save() }) { Text("Delete") }
                    }
                }
            }
        }
    }

    @Composable
    fun HomePage() {
        val cs = MaterialTheme.colorScheme
        val usable = ready || useRoot
        val heroColor = if (!usable) Bad else if (running) Good else cs.primary
        val title = when {
            !usable -> "Setup needed"
            busy -> "Working..."
            running -> "Roblox is running"
            else -> "Ready to launch"
        }
        val sub = when {
            !usable -> "Start Shizuku and grant permission, or turn on root."
            running -> "Up for ${uptime.ifEmpty { "a moment" }}, using $memMb MB"
            else -> "$flagCount flags ready. Using ${if (useRoot) "root" else "Shizuku"}."
        }
        val shape = RoundedCornerShape(LocalCorner.current + 4.dp)
        val recents = recent.entries
            .filter { e -> games.values.none { it == e.key } }
            .sortedByDescending { it.value.substringAfterLast('\t').toLongOrNull() ?: 0L }
            .take(4)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Appear(0) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(id = R.mipmap.ic_launcher), "TacoStrap logo",
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "TacoStrap", fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp, color = cs.onBackground
                        )
                        Text("FastFlag manager for Roblox", fontSize = 14.sp, color = cs.onSurfaceVariant)
                    }
                    Row(
                        Modifier.clip(CircleShape).background((if (usable) Good else Bad).copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (usable) Good else Bad))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (useRoot) "Root" else if (ready) "Shizuku" else "Offline",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (usable) Good else Bad
                        )
                    }
                }
            }

            Appear(1) {
                Column(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(Brush.linearGradient(listOf(heroColor.copy(alpha = 0.20f), cs.surfaceVariant)))
                        .border(1.dp, heroColor.copy(alpha = 0.35f), shape)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Orb(heroColor, if (usable) Ic.Check else Ic.Warn, busy)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                            Text(sub, fontSize = 14.sp, lineHeight = 19.sp, color = cs.onSurfaceVariant)
                        }
                    }
                    Btn(
                        "Launch Roblox",
                        { scope.launch(Dispatchers.IO) { toast("launch", launchPkg(pkg, useRoot)) } },
                        Modifier.fillMaxWidth(), icon = Ic.Play, h = 56.dp
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn("Verify file", { verifyFile() }, Modifier.weight(1f), kind = 1)
                        Btn("Reset", {
                            if (confirmReset) confirmResetDlg = true else doReset()
                        }, Modifier.weight(1f), kind = 2)
                    }
                }
            }

            if (!usable) {
                Appear(2) {
                    Group {
                        Item("Set up TacoStrap", "Three quick steps and you are done.", leading = Ic.Bolt)
                        Item("1. Start Shizuku", "Open Shizuku and start its service.") {
                            Btn("Open", { openShizuku() }, kind = 1, h = 36.dp)
                        }
                        Item("2. Grant permission", "Let TacoStrap run shell commands.") {
                            Btn("Grant", { grant() }, kind = 1, h = 36.dp)
                        }
                        Item("3. Save your flags", "Tap Save and Launch at the bottom.")
                    }
                }
            }

            AnimatedVisibility(dirty) {
                Group(color = cs.primary.copy(alpha = 0.14f)) {
                    Item("Unsaved flag changes", "Tap Save to write them to Roblox.", leading = Ic.Info) {
                        Btn("Save", { doSave(false) }, h = 38.dp)
                    }
                }
            }

            Appear(3) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Active flags", "$flagCount", Ic.Flag, Modifier.weight(1f))
                        StatTile("Roblox", robloxVersion, Ic.Game, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            "Frame limit",
                            flags[FPS_KEY]?.let { "$it FPS" } ?: "Default", Ic.Bolt, Modifier.weight(1f)
                        )
                        StatTile("Method", if (useRoot) "Root" else "Shizuku", Ic.Shield, Modifier.weight(1f))
                    }
                }
            }

            Appear(4) {
                Group {
                    Item("Frame rate", "Tap to set the limit", leading = Ic.Bolt)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Pill("Default", flags[FPS_KEY] == null) { flags.remove(FPS_KEY); save() }
                        listOf(30, 60, 90, 120, 144, 240).forEach { n ->
                            Pill("$n", flags[FPS_KEY] == "$n") { flags[FPS_KEY] = "$n"; save() }
                        }
                    }
                }
            }

            Appear(5) {
                Group {
                    Item(
                        "Session", if (running) "Roblox is running" else "Roblox isn't running",
                        leading = Ic.Pulse
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (running) Good else cs.outline))
                    }
                    if (running) {
                        Item("Process ID") { ValueText(pid) }
                        Item("Uptime") { ValueText(uptime.ifEmpty { "-" }) }
                        Item("Memory") { ValueText("$memMb MB") }
                        Spark(memHist.toList(), Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 20.dp, vertical = 8.dp))
                        Row(
                            Modifier.padding(start = 12.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton({ scope.launch(Dispatchers.IO) { toast("launch", forceStopPkg(pkg, useRoot)) } }) {
                                Text("Stop Roblox")
                            }
                        }
                    }
                }
            }

            Appear(6) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Pinned games")
                    Group {
                        if (games.isEmpty()) {
                            Item("No pinned games", "Add games in the Library tab.", { page = 1 }, Ic.Grid) {
                                Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                            }
                        }
                        games.keys.sorted().take(4).forEach { name ->
                            Item(name, "Place ID ${games[name]}", { playGame(name, games[name] ?: "") }, Ic.Game) {
                                Ico(Ic.Play, cs.primary, 20.dp)
                            }
                        }
                    }
                }
            }

            if (recents.isNotEmpty()) {
                Appear(7) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Recent")
                        Group {
                            recents.forEach { e ->
                                val rn = e.value.substringBeforeLast('\t')
                                val rt = e.value.substringAfterLast('\t').toLongOrNull() ?: 0L
                                Item(rn, "ID ${e.key}, ${ago(rt)}", { playGame(rn, e.key) }, Ic.Clock) {
                                    Ico(Ic.Play, cs.primary, 20.dp)
                                }
                            }
                        }
                    }
                }
            }

            if (fileOut.isNotEmpty()) {
                Group {
                    Text(
                        fileOut, Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, color = cs.onSurface
                    )
                }
            }
        }
    }

    @Composable
    fun LibraryPage() {
        val cs = MaterialTheme.colorScheme
        val target = parsePlace(qj)
        val code = qjCode.ifBlank { target?.second ?: "" }
        fun lastOf(id: String) = recent[id]?.substringAfterLast('\t')?.toLongOrNull() ?: 0L
        val pinned = games.keys.sortedWith(compareByDescending<String> { lastOf(games[it] ?: "") }.thenBy { it })
        val recents = recent.entries
            .filter { e -> games.values.none { it == e.key } }
            .sortedByDescending { it.value.substringAfterLast('\t').toLongOrNull() ?: 0L }
            .take(6)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageHeader("Library", "Pin games and jump straight in.")

            Appear(1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Quick join")
                    Group {
                        Column(
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                qj, { qj = it }, label = { Text("Place ID or game link") }, singleLine = true,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                                supportingText = {
                                    Text(
                                        if (qj.isBlank()) "Paste a roblox.com link or a number."
                                        else if (target != null) "Place ID ${target.first}" + (if (code.isNotBlank()) " with private server code" else "")
                                        else "Couldn't find a place ID."
                                    )
                                }
                            )
                            AnimatedVisibility(qjAdv) {
                                OutlinedTextField(
                                    qjCode, { qjCode = it.trim() }, label = { Text("Private server link code") },
                                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Btn("Join", {
                                    target?.let { t -> playGame("Game ${t.first}", t.first, code) }
                                }, Modifier.weight(1f), icon = Ic.Play)
                                Btn("Pin", {
                                    if (target != null) { pinName = "Game ${target.first}"; pinDialog = true }
                                }, Modifier.weight(1f), kind = 1, icon = Ic.Bookmark)
                            }
                        }
                        SwitchItem("Private server", "Add a link code when joining.", qjAdv) { qjAdv = it }
                        SwitchItem("Apply flags first", "Writes flags and restarts Roblox before joining.", applyBeforeGame) {
                            applyBeforeGame = it; save()
                        }
                    }
                }
            }

            Appear(2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Add a game")
                    Group {
                        Column(
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                gn, { gn = it }, label = { Text("Name") }, singleLine = true,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    gid, { gid = it.filter { c -> c.isDigit() } }, label = { Text("Place ID") },
                                    singleLine = true, shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                Btn("Add", {
                                    if (gn.isNotBlank() && gid.isNotBlank()) {
                                        games[gn.trim()] = gid; gn = ""; gid = ""; save()
                                        toast("other", "Game added.")
                                    }
                                }, icon = Ic.Plus)
                            }
                        }
                    }
                }
            }

            Appear(3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Pinned (${games.size})")
                    Group {
                        if (games.isEmpty()) Item("No games yet", "Add one above.", leading = Ic.Bookmark)
                        pinned.forEach { name ->
                            val id = games[name] ?: ""
                            Row(
                                Modifier.fillMaxWidth().clickable { tick(); playGame(name, id) }
                                    .padding(start = 20.dp, end = 8.dp, top = LocalPad.current, bottom = LocalPad.current),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GameAvatar(name, id)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = cs.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text("ID $id, ${ago(lastOf(id))}", fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1)
                                }
                                Btn("Play", { playGame(name, id) }, kind = 1, h = 36.dp)
                                Box {
                                    IconBtn(Ic.More) { gameMenu = name }
                                    DropdownMenu(expanded = gameMenu == name, onDismissRequest = { gameMenu = null }) {
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = { gameMenu = null; gameNewName = name; gameRename = name }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Copy link") },
                                            onClick = {
                                                gameMenu = null
                                                copyText("link", "roblox://experiences/start?placeId=$id", "Copied link.")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Remove") },
                                            onClick = { gameMenu = null; games.remove(name); save() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (recents.isNotEmpty()) {
                Appear(4) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Recent")
                        Group {
                            recents.forEach { e ->
                                val rn = e.value.substringBeforeLast('\t')
                                val rt = e.value.substringAfterLast('\t').toLongOrNull() ?: 0L
                                Item(rn, "ID ${e.key}, ${ago(rt)}", { playGame(rn, e.key) }, Ic.Clock) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconBtn(Ic.Bookmark) {
                                            if (games.values.none { it == e.key }) {
                                                games[rn] = e.key; save(); toast("other", "Pinned $rn.")
                                            }
                                        }
                                        Ico(Ic.Play, cs.primary, 20.dp)
                                    }
                                }
                            }
                            TextButton(
                                { recent.clear(); save() },
                                modifier = Modifier.padding(start = 12.dp)
                            ) { Text("Clear history") }
                        }
                    }
                }
            }
        }
    }

    fun testWrite() {
        scope.launch(Dispatchers.IO) {
            val dir = path.substringBeforeLast('/', "/")
            val (_, out) = runCmd(
                "mkdir -p '$dir' && touch '$dir/.fm_probe' && rm -f '$dir/.fm_probe' && echo WRITE_OK", useRoot
            )
            toast("other", if (out.contains("WRITE_OK")) "That folder is writable." else "Failed: ${out.take(120)}")
        }
    }

    fun detectFiles() {
        scope.launch(Dispatchers.IO) {
            val (_, out) = runCmd(
                "find /sdcard/Android/data/$pkg/ -maxdepth 6 \\( -iname 'ClientAppSettings*' -o -iname 'ClientSettings' \\) 2>/dev/null; " +
                    "echo '---'; ls -1 /sdcard/Android/data/$pkg/files 2>&1 | head -n 40",
                useRoot
            )
            fileOut = out.ifEmpty { "Nothing found." }
        }
    }

    fun resetAppearance() {
        themeIdx = 0; animBg = true; trueBlack = false; compact = false; motion = true
        glowAmt = 0.10f; cornerDp = 24f; textScale = 1f; haptics = true
        save()
    }

    LaunchedEffect(page, modsTab, logLevel, ready, useRoot) {
        if (page == 2 && modsTab == 4 && (ready || useRoot)) loadLogs()
    }

    @Composable
    fun ModsPage() {
        val cs = MaterialTheme.colorScheme
        val shownLogs = logLines
            .filter { l -> l.isNotBlank() && (!logOnly || l.contains(pkg) || l.contains("roblox", true)) }
            .takeLast(150).reversed()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageHeader("Mods", "Tweak Roblox with packs, profiles and your own files.")
            Appear(1) {
                Group {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Ico(Ic.Info, Amber, 26.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Write flags to Roblox", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
                            Text(
                                "Writes your flags to the flag file when you tap Save. Roblox on Android only reads files from its own app package, so root may be needed.",
                                fontSize = 13.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        TacoSwitch(applyOnSave) { applyOnSave = it; save() }
                    }
                    Item("Roblox allowlist", "Flags Roblox allows on PC", { openUrl(ALLOWLIST_URL) }) {
                        Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                    }
                }
            }
            Appear(2) {
                TabStrip(
                    listOf(
                        "Customize" to Ic.Sliders, "Packs" to Ic.Download, "Profiles" to Ic.Layers,
                        "Files" to Ic.Folder, "Logs" to Ic.Terminal
                    ),
                    modsTab
                ) { modsTab = it }
            }
            Crossfade(targetState = modsTab, animationSpec = tween(pageAnim), label = "modsTab") { t ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (t) {
                        0 -> {
                            SectionLabel("Display")
                            Group {
                                FlagSetting(FS_FPSCOUNT, flags) { save() }
                                FlagSetting(FS_BLUR, flags) { save() }
                                FlagSetting(FS_SKY, flags) { save() }
                                FlagSetting(FS_FULLRES, flags) { save() }
                            }
                            SectionLabel("Performance")
                            Group {
                                FlagSetting(FS_FPS, flags) { save() }
                                FlagSetting(FS_QUALITY, flags) { save() }
                            }
                            SectionLabel("Visuals")
                            Group {
                                FlagSetting(FS_SHADOWS, flags) { save() }
                                FlagSetting(FS_POSTFX, flags) { save() }
                                FlagSetting(FS_MESH, flags) { save() }
                            }
                        }
                        1 -> {
                            SectionLabel("Flag packs")
                            Group {
                                PACKS.forEach { p ->
                                    Item(p.name, p.desc, leading = Ic.Layers) {
                                        Btn("Apply", {
                                            flags.putAll(p.sets); save()
                                            toast("flag", "Added ${p.name} flags.")
                                        }, kind = 1, h = 38.dp)
                                    }
                                }
                            }
                        }
                        2 -> ProfilesUI()
                        3 -> {
                            SectionLabel("Flag file")
                            Group {
                                Item("Path", path, leading = Ic.Doc)
                                Item("View file", "Shows the file Roblox would read", { verifyFile() }, Ic.Search) {
                                    Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                                }
                                Item("Test write access", "Checks that this folder can be written", { testWrite() }, Ic.Check) {
                                    Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                                }
                                Item("Find flag folder", "Searches Roblox's folder for settings files", { detectFiles() }, Ic.Folder) {
                                    Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                                }
                                Item("Delete flag file", "Removes the file and stops Roblox", {
                                    if (confirmReset) confirmResetDlg = true else doReset()
                                }, Ic.Trash) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                            }
                            SectionLabel("Roblox")
                            Group {
                                Item("Stop Roblox", "Force-stops the app", {
                                    scope.launch(Dispatchers.IO) { toast("launch", forceStopPkg(pkg, useRoot)) }
                                }, Ic.Close) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                                Item("Clear Roblox cache", "Frees up space", {
                                    scope.launch(Dispatchers.IO) { toast("other", clearCache(pkg, useRoot)) }
                                }, Ic.Trash) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                            }
                            if (fileOut.isNotEmpty()) {
                                Group {
                                    Text(
                                        fileOut, Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp,
                                        color = cs.onSurface
                                    )
                                }
                            }
                        }
                        else -> {
                            SectionLabel("Roblox log")
                            Group {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("V" to "All", "I" to "Info", "W" to "Warn", "E" to "Error").forEach { (lv, n) ->
                                            Pill(n, logLevel == lv) { logLevel = lv }
                                        }
                                    }
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Btn("Refresh", { loadLogs() }, kind = 1, icon = Ic.Refresh, h = 42.dp, loading = logLoading)
                                        Btn("Copy", { copyText("log", shownLogs.reversed().joinToString("\n"), "Copied log.") }, kind = 2, icon = Ic.Copy, h = 42.dp)
                                        Btn("Wipe", {
                                            scope.launch(Dispatchers.IO) { runCmd("logcat -c", useRoot); logLines = emptyList() }
                                        }, kind = 2, icon = Ic.Trash, h = 42.dp)
                                    }
                                }
                                SwitchItem("Only Roblox", "Hide lines from other apps.", logOnly) { logOnly = it }
                            }
                            Group {
                                if (shownLogs.isEmpty()) {
                                    Item("No lines", "Open Roblox, then tap Refresh.", leading = Ic.Terminal)
                                } else {
                                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        shownLogs.forEach { l ->
                                            val lv = Regex("\\s([VDIWEF])\\s").find(l)?.groupValues?.get(1) ?: "I"
                                            Text(
                                                l, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                                                color = when (lv) {
                                                    "E", "F" -> Bad
                                                    "W" -> Amber
                                                    "V", "D" -> cs.onSurfaceVariant
                                                    else -> cs.onSurface
                                                }
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
    }

    @Composable
    fun FlagsPage() {
        val cs = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageHeader("FastFlags", "Settings for your current profile.")
            Appear(1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Ico(Ic.Warn, Amber, 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Roblox may ignore flags outside its allowlist.",
                        Modifier.weight(1f), fontSize = 14.sp, color = cs.onSurfaceVariant
                    )
                    TextButton({ showEditor = true }) {
                        Text("Open editor", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            OutlinedTextField(
                fsQuery, { fsQuery = it }, label = { Text("Search settings") }, singleLine = true,
                leadingIcon = { Ico(Ic.Search, cs.onSurfaceVariant, 20.dp) },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            )
            SECTIONS.forEachIndexed { idx, (title, list) ->
                val rows = list.filter { fsQuery.isBlank() || it.title.contains(fsQuery, ignoreCase = true) }
                if (rows.isNotEmpty()) {
                    Appear(2 + idx) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel(title)
                            Group { rows.forEach { FlagSetting(it, flags) { save() } } }
                        }
                    }
                }
            }
            if (fsQuery.isBlank()) {
                Appear(9) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Quick setup")
                        Group {
                            Item("Recommended", PACKS[quickIdx].desc, leading = Ic.Bolt)
                            Row(
                                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ChoiceBox(PACKS[quickIdx].name, PACKS.map { it.name }) { quickIdx = it }
                                Btn("Apply", {
                                    flags.putAll(PACKS[quickIdx].sets); save()
                                    toast("flag", "Added ${PACKS[quickIdx].name} flags.")
                                }, h = 44.dp)
                            }
                        }
                    }
                }
            }
            Appear(10) {
                Group {
                    Item("Clear all flags", "Removes every flag from this profile", { confirmClear = true }, Ic.Trash) {
                        Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsPage() {
        val cs = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageHeader("Settings", "Make TacoStrap yours.")
            TabStrip(
                listOf(
                    "Look" to Ic.Sliders, "Behavior" to Ic.Bolt, "Roblox" to Ic.Game,
                    "Backup" to Ic.Download, "About" to Ic.Info
                ),
                setTab
            ) { setTab = it }
            when (setTab) {
                0 -> {
                    SectionLabel("Theme")
                    Group {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            THEMES.forEachIndexed { i, t -> Pill(t.name, themeIdx == i) { themeIdx = i; save() } }
                        }
                    }
                    SectionLabel("Style")
                    Group {
                        SwitchItem("True black", "Pure black background for OLED screens", trueBlack) { trueBlack = it; save() }
                        SwitchItem("Animated background", "Slow color glow behind the app", animBg) { animBg = it; save() }
                        SwitchItem("Compact layout", "Tighter spacing between rows", compact) { compact = it; save() }
                        SwitchItem("Motion", "Turn animations on or off", motion) { motion = it; save() }
                    }
                    SectionLabel("Sliders")
                    Group {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text("Glow ${(glowAmt * 100).roundToInt()}%", fontSize = 14.sp, color = cs.onSurface)
                            Slider(glowAmt, { glowAmt = it }, valueRange = 0f..0.3f, onValueChangeFinished = { save() })
                        }
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text("Corner radius ${cornerDp.roundToInt()} dp", fontSize = 14.sp, color = cs.onSurface)
                            Slider(cornerDp, { cornerDp = it }, valueRange = 8f..32f, onValueChangeFinished = { save() })
                        }
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text("Text size ${(textScale * 100).roundToInt()}%", fontSize = 14.sp, color = cs.onSurface)
                            Slider(textScale, { textScale = it }, valueRange = 0.85f..1.3f, onValueChangeFinished = { save() })
                        }
                    }
                    Btn("Reset appearance", { confirmSettingsReset = true }, Modifier.fillMaxWidth(), kind = 2, icon = Ic.Refresh)
                }
                1 -> {
                    SectionLabel("Launching")
                    Group {
                        SwitchItem("Restart Roblox on launch", "Force-stops Roblox before Save and Launch", restart) { restart = it; save() }
                        SwitchItem("Unlock frame rate cap", "Also sets the Android game frame rate property when saving", fps) { fps = it; save() }
                        SwitchItem("Apply flags before joining", "Writes flags and restarts Roblox before joining a game", applyBeforeGame) {
                            applyBeforeGame = it; save()
                        }
                        SwitchItem("Confirm before reset", "Ask first when deleting the flag file", confirmReset) { confirmReset = it; save() }
                    }
                    SectionLabel("Access")
                    Group {
                        SwitchItem("Use root", "Run commands with su instead of Shizuku", useRoot) { useRoot = it; save() }
                        Item("Shizuku", shStatus, leading = Ic.Shield) {
                            Btn("Grant", { grant() }, kind = 1, h = 36.dp)
                        }
                        Item("Open Shizuku", "Start or check the Shizuku service", { openShizuku() }, Ic.Right) {
                            Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                        }
                    }
                    SectionLabel("Feel")
                    Group {
                        SwitchItem("Haptics", "Vibrate lightly when you tap", haptics) { haptics = it; save() }
                        SwitchItem("Keep screen on", "Stops the screen from sleeping while TacoStrap is open", keepAwake) { keepAwake = it; save() }
                    }
                    SectionLabel("Messages")
                    Group {
                        SwitchItem("Flag messages", "Saved, applied and loaded", msgFlag) { msgFlag = it; save() }
                        SwitchItem("Launch messages", "Launching and stopping Roblox", msgLaunch) { msgLaunch = it; save() }
                        SwitchItem("Other messages", "Copy, pin and everything else", msgOther) { msgOther = it; save() }
                    }
                }
                2 -> {
                    SectionLabel("Roblox app")
                    Group {
                        Item("Package", pkg, { pkgInput = pkg; pkgDialog = true }, Ic.Game) {
                            Ico(Ic.Pencil, cs.onSurfaceVariant, 20.dp)
                        }
                        Item("Version", leading = Ic.Info) { ValueText(robloxVersion) }
                    }
                    SectionLabel("Flag file")
                    Group {
                        Column(
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                path, { path = it.trim() }, label = { Text("Flag file path") }, singleLine = true,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Btn("Save path", { save(); toast("other", "Path saved.") }, kind = 1, h = 42.dp)
                                Btn("Use default", { path = DEFAULT_PATH; save() }, kind = 2, h = 42.dp)
                            }
                        }
                    }
                }
                3 -> {
                    SectionLabel("Backup")
                    Group {
                        Item("Copy backup", "Flags, profiles and pinned games as JSON", {
                            copyText("backup", backupJson(), "Copied backup.")
                        }, Ic.Copy) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                        Item("Share backup", "Send the backup to another app", {
                            share(backupJson(), "Share backup")
                        }, Ic.Share) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                        Item("Restore from clipboard", "Replaces flags, profiles and games with a copied backup", {
                            val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            try {
                                val o = JSONObject(text)
                                if (o.has("flags")) {
                                    val f = parseFlags(o.getJSONObject("flags").toString())
                                    flags.clear(); flags.putAll(f)
                                }
                                if (o.has("presets")) {
                                    val p = parseFlags(o.getJSONObject("presets").toString())
                                    presets.clear(); presets.putAll(p)
                                }
                                if (o.has("games")) {
                                    val g = parseFlags(o.getJSONObject("games").toString())
                                    games.clear(); games.putAll(g)
                                }
                                save()
                                toast("other", "Backup restored.")
                            } catch (e: Throwable) {
                                toast("other", "Failed: clipboard isn't a TacoStrap backup.")
                            }
                        }, Ic.Download) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                    }
                    SectionLabel("Danger zone")
                    Group {
                        Item("Clear all flags", "Removes every flag from this profile", { confirmClear = true }, Ic.Trash) {
                            Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                        }
                    }
                }
                else -> {
                    SectionLabel("About")
                    Group {
                        Item("TacoStrap", "FastFlag manager for Roblox", leading = Ic.Bolt)
                        Item("Roblox allowlist", "Flags Roblox allows on PC", { openUrl(ALLOWLIST_URL) }, Ic.Flag) {
                            Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                        }
                        Item("Open source licenses", "Libraries used in this app", { licenses = true }, Ic.Doc) {
                            Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EditorScreen() {
        val cs = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize().background(cs.background).statusBarsPadding().imePadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(Ic.Back) { showEditor = false }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text("Flag editor", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                    Text("${flags.size} flags", fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
                Btn("Save", { doSave(false) }, kind = 1, h = 38.dp)
            }
            TabStrip(
                listOf("Current" to Ic.Flag, "Add" to Ic.Plus, "Known" to Ic.Search, "JSON" to Ic.Terminal),
                edTab
            ) { edTab = it }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (edTab) {
                    0 -> {
                        OutlinedTextField(
                            search, { search = it }, label = { Text("Search flags") }, singleLine = true,
                            leadingIcon = { Ico(Ic.Search, cs.onSurfaceVariant, 20.dp) },
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                        )
                        val keys = flags.keys.sorted().filter { search.isBlank() || it.contains(search, ignoreCase = true) }
                        Group {
                            if (keys.isEmpty()) {
                                Item("No flags", "Add some in the Add tab.", leading = Ic.Flag)
                            }
                            keys.take(200).forEach { name ->
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = cs.onSurface,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis
                                        )
                                        Text(flagKind(name), fontSize = 12.sp, color = cs.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    NumBox(flags[name] ?: "", "Value", Modifier.width(120.dp), KeyboardType.Text) { t ->
                                        flags[name] = t
                                        save()
                                    }
                                    IconBtn(Ic.Trash) { flags.remove(name); save() }
                                }
                            }
                        }
                    }
                    1 -> {
                        Group {
                            Column(
                                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    k, { k = it.trim() }, label = { Text("Flag name") }, singleLine = true,
                                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                                    supportingText = {
                                        Text(
                                            if (k.isBlank()) "Example: DFIntTaskSchedulerTargetFps"
                                            else if (validFlag(k)) "${flagKind(k)} flag"
                                            else "That name doesn't look like a Roblox flag."
                                        )
                                    }
                                )
                                OutlinedTextField(
                                    v, { v = it }, label = { Text("Value") }, singleLine = true,
                                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                                )
                                Btn("Add flag", {
                                    if (k.isNotBlank() && v.isNotBlank()) {
                                        flags[k] = v.trim()
                                        k = ""
                                        v = ""
                                        save()
                                        toast("flag", "Flag added.")
                                    }
                                }, Modifier.fillMaxWidth(), icon = Ic.Plus)
                            }
                        }
                    }
                    2 -> {
                        OutlinedTextField(
                            ksearch, { ksearch = it }, label = { Text("Search known flags") }, singleLine = true,
                            leadingIcon = { Ico(Ic.Search, cs.onSurfaceVariant, 20.dp) },
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                        )
                        val list = KNOWN.filter {
                            ksearch.isBlank() || it.name.contains(ksearch, ignoreCase = true) ||
                                it.desc.contains(ksearch, ignoreCase = true)
                        }
                        Group {
                            if (list.isEmpty()) Item("No matches", "Try another search.", leading = Ic.Search)
                            list.forEach { kn ->
                                Item(kn.name, "${kn.desc}, sample ${kn.sample}", {
                                    flags[kn.name] = kn.sample
                                    save()
                                    toast("flag", "Added ${kn.name}.")
                                }, Ic.Flag) { Ico(Ic.Plus, cs.primary, 20.dp) }
                            }
                        }
                    }
                    else -> {
                        Group {
                            Column(
                                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    jsonText, { jsonText = it }, label = { Text("Flags JSON") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(220.dp),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                )
                                if (jsonMsg.isNotEmpty()) {
                                    Text(jsonMsg, fontSize = 13.sp, color = cs.onSurfaceVariant)
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Btn("Load current", { jsonText = toJson(flags); jsonMsg = "" }, kind = 1, h = 42.dp)
                                    Btn("Replace", {
                                        try {
                                            val m = parseFlags(jsonText)
                                            flags.clear(); flags.putAll(m); save()
                                            jsonMsg = "Loaded ${m.size} flags."
                                        } catch (e: Throwable) {
                                            jsonMsg = "Failed: that isn't valid JSON."
                                        }
                                    }, kind = 1, h = 42.dp)
                                    Btn("Merge", {
                                        try {
                                            val m = parseFlags(jsonText)
                                            flags.putAll(m); save()
                                            jsonMsg = "Merged ${m.size} flags."
                                        } catch (e: Throwable) {
                                            jsonMsg = "Failed: that isn't valid JSON."
                                        }
                                    }, kind = 2, h = 42.dp)
                                    Btn("Copy", { copyText("flags", toJson(flags), "Copied JSON.") }, kind = 2, icon = Ic.Copy, h = 42.dp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ================= ROOT =================

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalCorner provides cornerDp.dp,
            LocalPad provides (if (compact) 10.dp else 16.dp),
            LocalMotion provides motion,
            LocalHaptic provides tick,
            LocalDensity provides Density(LocalDensity.current.density, textScale)
        ) {
            BackHandler(enabled = showEditor || page != 0) {
                if (showEditor) showEditor = false else page = 0
            }

            Box(Modifier.fillMaxSize().background(scheme.background)) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(pc.copy(alpha = (glowAmt * (0.5f + glow)).coerceIn(0f, 1f)), Color.Transparent)
                        )
                    )
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (!imeUp) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(scheme.background.copy(alpha = 0.92f))
                                    .navigationBarsPadding()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Btn("Save", { doSave(false) }, Modifier.weight(1f), kind = 1, loading = busy)
                                    Btn("Save and Launch", { doSave(true) }, Modifier.weight(1f), icon = Ic.Play)
                                }
                                NavBar(navItems, page) { page = it }
                            }
                        }
                    }
                ) { inner ->
                    Box(Modifier.fillMaxSize().padding(inner).statusBarsPadding()) {
                        Crossfade(targetState = page, animationSpec = tween(pageAnim), label = "page") { p ->
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
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut()
                ) { EditorScreen() }
            }

            // ----- Dialogs -----

            renaming?.let { old ->
                AlertDialog(
                    onDismissRequest = { renaming = null },
                    title = { Text("Rename profile") },
                    text = {
                        OutlinedTextField(
                            newName, { newName = it }, label = { Text("Profile name") }, singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    },
                    confirmButton = {
                        TextButton({
                            val data = presets[old]
                            if (data != null && newName.isNotBlank()) {
                                presets.remove(old)
                                presets[newName.trim()] = data
                                save()
                            }
                            renaming = null
                        }) { Text("Rename") }
                    },
                    dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } }
                )
            }

            gameRename?.let { old ->
                AlertDialog(
                    onDismissRequest = { gameRename = null },
                    title = { Text("Rename game") },
                    text = {
                        OutlinedTextField(
                            gameNewName, { gameNewName = it }, label = { Text("Game name") }, singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    },
                    confirmButton = {
                        TextButton({
                            val id = games[old]
                            if (id != null && gameNewName.isNotBlank()) {
                                games.remove(old)
                                games[gameNewName.trim()] = id
                                save()
                            }
                            gameRename = null
                        }) { Text("Rename") }
                    },
                    dismissButton = { TextButton({ gameRename = null }) { Text("Cancel") } }
                )
            }

            if (pinDialog) {
                AlertDialog(
                    onDismissRequest = { pinDialog = false },
                    title = { Text("Pin game") },
                    text = {
                        OutlinedTextField(
                            pinName, { pinName = it }, label = { Text("Game name") }, singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    },
                    confirmButton = {
                        TextButton({
                            val id = parsePlace(qj)?.first
                            if (id != null && pinName.isNotBlank()) {
                                games[pinName.trim()] = id
                                save()
                                toast("other", "Pinned ${pinName.trim()}.")
                            }
                            pinDialog = false
                        }) { Text("Pin") }
                    },
                    dismissButton = { TextButton({ pinDialog = false }) { Text("Cancel") } }
                )
            }

            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Clear all flags?") },
                    text = { Text("This removes every flag from the current profile. Saved profiles are not touched.") },
                    confirmButton = {
                        TextButton({ flags.clear(); save(); confirmClear = false }) { Text("Clear") }
                    },
                    dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } }
                )
            }

            if (confirmResetDlg) {
                AlertDialog(
                    onDismissRequest = { confirmResetDlg = false },
                    title = { Text("Delete the flag file?") },
                    text = { Text("This removes the file from the device and stops Roblox.") },
                    confirmButton = {
                        TextButton({ confirmResetDlg = false; doReset() }) { Text("Delete") }
                    },
                    dismissButton = { TextButton({ confirmResetDlg = false }) { Text("Cancel") } }
                )
            }

            if (confirmSettingsReset) {
                AlertDialog(
                    onDismissRequest = { confirmSettingsReset = false },
                    title = { Text("Reset appearance?") },
                    text = { Text("Theme, spacing, text size and motion go back to their defaults.") },
                    confirmButton = {
                        TextButton({ confirmSettingsReset = false; resetAppearance() }) { Text("Reset") }
                    },
                    dismissButton = { TextButton({ confirmSettingsReset = false }) { Text("Cancel") } }
                )
            }

            if (pkgDialog) {
                AlertDialog(
                    onDismissRequest = { pkgDialog = false },
                    title = { Text("Roblox package") },
                    text = {
                        OutlinedTextField(
                            pkgInput, { pkgInput = it.trim() }, label = { Text("Package name") }, singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    },
                    confirmButton = {
                        TextButton({
                            if (pkgInput.isNotBlank()) {
                                pkg = pkgInput
                                save()
                            }
                            pkgDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        Row {
                            TextButton({ pkgInput = DEFAULT_PKG }) { Text("Default") }
                            TextButton({ pkgDialog = false }) { Text("Cancel") }
                        }
                    }
                )
            }

            if (licenses) {
                AlertDialog(
                    onDismissRequest = { licenses = false },
                    title = { Text("Open source licenses") },
                    text = {
                        Text("TacoStrap is built with Kotlin, Jetpack Compose and the Shizuku API. See each project for its license terms.")
                    },
                    confirmButton = { TextButton({ licenses = false }) { Text("Close") } }
                )
            }
        }
    }
}
                        }
                    }
                }
            }
            Appear(10) {
                Group {
                    Item("Clear all flags", "Removes every flag from this profile", { confirmClear = true }, Ic.Trash) {
                        Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsPage() {
        val cs = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageHeader("Settings", "Make TacoStrap yours.")
            TabStrip(
                listOf(
                    "Look" to Ic.Sliders, "Behavior" to Ic.Bolt, "Roblox" to Ic.Game,
                    "Backup" to Ic.Download, "About" to Ic.Info
                ),
                setTab
            ) { setTab = it }
            when (setTab) {
                0 -> {
                    SectionLabel("Theme")
                    Group {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            THEMES.forEachIndexed { i, t -> Pill(t.name, themeIdx == i) { themeIdx = i; save() } }
                        }
                    }
                    SectionLabel("Style")
                    Group {
                        SwitchItem("True black", "Pure black background for OLED screens", trueBlack) { trueBlack = it; save() }
                        SwitchItem("Animated background", "Slow color glow behind the app", animBg) { animBg = it; save() }
                        SwitchItem("Compact layout", "Tighter spacing between rows", compact) { compact = it; save() }
                        SwitchItem("Motion", "Turn animations on or off", motion) { motion = it; save() }
                    }
                    SectionLabel("Sliders")
                    Group {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text("Glow ${(glowAmt * 100).roundToInt()}%", fontSize = 14.sp, color = cs.onSurface)
                            Slider(glowAmt, { glowAmt = it }, valueRange = 0f..0.3f, onValueChangeFinished = { save() })
                        }
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text("Corner radius ${cornerDp.roundToInt()} dp", fontSize = 14.sp, color = cs.onSurface)
                            Slider(cornerDp, { cornerDp = it }, valueRange = 8f..32f, onValueChangeFinished = { save() })
                        }
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text("Text size ${(textScale * 100).roundToInt()}%", fontSize = 14.sp, color = cs.onSurface)
                            Slider(textScale, { textScale = it }, valueRange = 0.85f..1.3f, onValueChangeFinished = { save() })
                        }
                    }
                    Btn("Reset appearance", { confirmSettingsReset = true }, Modifier.fillMaxWidth(), kind = 2, icon = Ic.Refresh)
                }
                1 -> {
                    SectionLabel("Launching")
                    Group {
                        SwitchItem("Restart Roblox on launch", "Force-stops Roblox before Save and Launch", restart) { restart = it; save() }
                        SwitchItem("Unlock frame rate cap", "Also sets the Android game frame rate property when saving", fps) { fps = it; save() }
                        SwitchItem("Apply flags before joining", "Writes flags and restarts Roblox before joining a game", applyBeforeGame) {
                            applyBeforeGame = it; save()
                        }
                        SwitchItem("Confirm before reset", "Ask first when deleting the flag file", confirmReset) { confirmReset = it; save() }
                    }
                    SectionLabel("Access")
                    Group {
                        SwitchItem("Use root", "Run commands with su instead of Shizuku", useRoot) { useRoot = it; save() }
                        Item("Shizuku", shStatus, leading = Ic.Shield) {
                            Btn("Grant", { grant() }, kind = 1, h = 36.dp)
                        }
                        Item("Open Shizuku", "Start or check the Shizuku service", { openShizuku() }, Ic.Right) {
                            Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                        }
                    }
                    SectionLabel("Feel")
                    Group {
                        SwitchItem("Haptics", "Vibrate lightly when you tap", haptics) { haptics = it; save() }
                        SwitchItem("Keep screen on", "Stops the screen from sleeping while TacoStrap is open", keepAwake) { keepAwake = it; save() }
                    }
                    SectionLabel("Messages")
                    Group {
                        SwitchItem("Flag messages", "Saved, applied and loaded", msgFlag) { msgFlag = it; save() }
                        SwitchItem("Launch messages", "Launching and stopping Roblox", msgLaunch) { msgLaunch = it; save() }
                        SwitchItem("Other messages", "Copy, pin and everything else", msgOther) { msgOther = it; save() }
                    }
                }
                2 -> {
                    SectionLabel("Roblox app")
                    Group {
                        Item("Package", pkg, { pkgInput = pkg; pkgDialog = true }, Ic.Game) {
                            Ico(Ic.Pencil, cs.onSurfaceVariant, 20.dp)
                        }
                        Item("Version", leading = Ic.Info) { ValueText(robloxVersion) }
                    }
                    SectionLabel("Flag file")
                    Group {
                        Column(
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                path, { path = it.trim() }, label = { Text("Flag file path") }, singleLine = true,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Btn("Save path", { save(); toast("other", "Path saved.") }, kind = 1, h = 42.dp)
                                Btn("Use default", { path = DEFAULT_PATH; save() }, kind = 2, h = 42.dp)
                            }
                        }
                    }
                }
                3 -> {
                    SectionLabel("Backup")
                    Group {
                        Item("Copy backup", "Flags, profiles and pinned games as JSON", {
                            copyText("backup", backupJson(), "Copied backup.")
                        }, Ic.Copy) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                        Item("Share backup", "Send the backup to another app", {
                            share(backupJson(), "Share backup")
                        }, Ic.Share) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                        Item("Restore from clipboard", "Replaces flags, profiles and games with a copied backup", {
                            val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            try {
                                val o = JSONObject(text)
                                if (o.has("flags")) {
                                    val f = parseFlags(o.getJSONObject("flags").toString())
                                    flags.clear(); flags.putAll(f)
                                }
                                if (o.has("presets")) {
                                    val p = parseFlags(o.getJSONObject("presets").toString())
                                    presets.clear(); presets.putAll(p)
                                }
                                if (o.has("games")) {
                                    val g = parseFlags(o.getJSONObject("games").toString())
                                    games.clear(); games.putAll(g)
                                }
                                save()
                                toast("other", "Backup restored.")
                            } catch (e: Throwable) {
                                toast("other", "Failed: clipboard isn't a TacoStrap backup.")
                            }
                        }, Ic.Download) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                    }
                    SectionLabel("Danger zone")
                    Group {
                        Item("Clear all flags", "Removes every flag from this profile", { confirmClear = true }, Ic.Trash) {
                            Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                        }
                    }
                }
                else -> {
                    SectionLabel("About")
                    Group {
                        Item("TacoStrap", "FastFlag manager for Roblox", leading = Ic.Bolt)
                        Item("Roblox allowlist", "Flags Roblox allows on PC", { openUrl(ALLOWLIST_URL) }, Ic.Flag) {
                            Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                        }
                        Item("Open source licenses", "Libraries used in this app", { licenses = true }, Ic.Doc) {
                            Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EditorScreen() {
        val cs = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize().background(cs.background).statusBarsPadding().imePadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(Ic.Back) { showEditor = false }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text("Flag editor", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                    Text("${flags.size} flags", fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
                Btn("Save", { doSave(false) }, kind = 1, h = 38.dp)
            }
            TabStrip(
                listOf("Current" to Ic.Flag, "Add" to Ic.Plus, "Known" to Ic.Search, "JSON" to Ic.Terminal),
                edTab
            ) { edTab = it }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (edTab) {
                    0 -> {
                        OutlinedTextField(
                            search, { search = it }, label = { Text("Search flags") }, singleLine = true,
                            leadingIcon = { Ico(Ic.Search, cs.onSurfaceVariant, 20.dp) },
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                        )
                        val keys = flags.keys.sorted().filter { search.isBlank() || it.contains(search, ignoreCase = true) }
                        Group {
                            if (keys.isEmpty()) {
                                Item("No flags", "Add some in the Add tab.", leading = Ic.Flag)
                            }
                            keys.take(200).forEach { name ->
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = cs.onSurface,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis
                                        )
                                        Text(flagKind(name), fontSize = 12.sp, color = cs.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    NumBox(flags[name] ?: "", "Value", Modifier.width(120.dp), KeyboardType.Text) { t ->
                                        flags[name] = t
                                        save()
                                    }
                                    IconBtn(Ic.Trash) { flags.remove(name); save() }
                                }
                            }
                        }
                    }
                    1 -> {
                        Group {
                            Column(
                                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    k, { k = it.trim() }, label = { Text("Flag name") }, singleLine = true,
                                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                                    supportingText = {
                                        Text(
                                            if (k.isBlank()) "Example: DFIntTaskSchedulerTargetFps"
                                            else if (validFlag(k)) "${flagKind(k)} flag"
                                            else "That name doesn't look like a Roblox flag."
                                        )
                                    }
                                )
                                OutlinedTextField(
                                    v, { v = it }, label = { Text("Value") }, singleLine = true,
                                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                                )
                                Btn("Add flag", {
                                    if (k.isNotBlank() && v.isNotBlank()) {
                                        flags[k] = v.trim()
                                        k = ""
                                        v = ""
                                        save()
                                        toast("flag", "Flag added.")
                                    }
                                }, Modifier.fillMaxWidth(), icon = Ic.Plus)
                            }
                        }
                    }
                    2 -> {
                        OutlinedTextField(
                            ksearch, { ksearch = it }, label = { Text("Search known flags") }, singleLine = true,
                            leadingIcon = { Ico(Ic.Search, cs.onSurfaceVariant, 20.dp) },
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                        )
                        val list = KNOWN.filter {
                            ksearch.isBlank() || it.name.contains(ksearch, ignoreCase = true) ||
                                it.desc.contains(ksearch, ignoreCase = true)
                        }
                        Group {
                            if (list.isEmpty()) Item("No matches", "Try another search.", leading = Ic.Search)
                            list.forEach { kn ->
                                Item(kn.name, "${kn.desc}, sample ${kn.sample}", {
                                    flags[kn.name] = kn.sample
                                    save()
                                    toast("flag", "Added ${kn.name}.")
                                }, Ic.Flag) { Ico(Ic.Plus, cs.primary, 20.dp) }
                            }
                        }
                    }
                    else -> {
                        Group {
                            Column(
                                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    jsonText, { jsonText = it }, label = { Text("Flags JSON") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(220.dp),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                )
                                if (jsonMsg.isNotEmpty()) {
                                    Text(jsonMsg, fontSize = 13.sp, color = cs.onSurfaceVariant)
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Btn("Load current", { jsonText = toJson(flags); jsonMsg = "" }, kind = 1, h = 42.dp)
                                    Btn("Replace", {
                                        try {
                                            val m = parseFlags(jsonText)
                                            flags.clear(); flags.putAll(m); save()
                                            jsonMsg = "Loaded ${m.size} flags."
                                        } catch (e: Throwable) {
                                            jsonMsg = "Failed: that isn't valid JSON."
                                        }
                                    }, kind = 1, h = 42.dp)
                                    Btn("Merge", {
                                        try {
                                            val m = parseFlags(jsonText)
                                            flags.putAll(m); save()
                                            jsonMsg = "Merged ${m.size} flags."
                                        } catch (e: Throwable) {
                                            jsonMsg = "Failed: that isn't valid JSON."
                                        }
                                    }, kind = 2, h = 42.dp)
                                    Btn("Copy", { copyText("flags", toJson(flags), "Copied JSON.") }, kind = 2, icon = Ic.Copy, h = 42.dp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ================= ROOT =================

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalCorner provides cornerDp.dp,
            LocalPad provides (if (compact) 10.dp else 16.dp),
            LocalMotion provides motion,
            LocalHaptic provides tick,
            LocalDensity provides Density(LocalDensity.current.density, textScale)
        ) {
            BackHandler(enabled = showEditor || page != 0) {
                if (showEditor) showEditor = false else page = 0
            }

            Box(Modifier.fillMaxSize().background(scheme.background)) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(pc.copy(alpha = (glowAmt * (0.5f + glow)).coerceIn(0f, 1f)), Color.Transparent)
                        )
                    )
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (!imeUp) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(scheme.background.copy(alpha = 0.92f))
                                    .navigationBarsPadding()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Btn("Save", { doSave(false) }, Modifier.weight(1f), kind = 1, loading = busy)
                                    Btn("Save and Launch", { doSave(true) }, Modifier.weight(1f), icon = Ic.Play)
                                }
                                NavBar(navItems, page) { page = it }
                            }
                        }
                    }
                ) { inner ->
                    Box(Modifier.fillMaxSize().padding(inner).statusBarsPadding()) {
                        Crossfade(targetState = page, animationSpec = tween(pageAnim), label = "page") { p ->
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
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut()
                ) { EditorScreen() }
            }

            // ----- Dialogs -----

            renaming?.let { old ->
                AlertDialog(
                    onDismissRequest = { renaming = null },
                    title = { Text("Rename profile") },
                    text = {
                        OutlinedTextField(
                            newName, { newName = it }, label = { Text("Profile name") }, singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    },
                    confirmButton = {
                        TextButton({
                            val data = presets[old]
                            if (data != null && newName.isNotBlank()) {
                                presets.remove(old)
                                presets[newName.trim()] = data
                                save()
                            }
                            renaming = null
                        }) { Text("Rename") }
                    },
                    dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } }
                )
            }

            gameRename?.let { old ->
                AlertDialog(
                    onDismissRequest = { gameRename = null },
                    title = { Text("Rename game") },
                    text = {
                        OutlinedTextField(
                            gameNewName, { gameNewName = it }, label = { Text("Game name") }, singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    },
                    confirmButton = {
                        TextButton({
                            val id = games[old]
                            if (id != null && gameNewName.isNotBlank()) {
                                games.remove(old)
                                games[gameNewName.trim()] = id
                                save()
                            }
                            gameRename = null
                        }) { Text("Rename") }
                    },
                    dismissButton = { TextButton({ gameRename = null }) { Text("Cancel") } }
                )
            }

            if (pinDialog) {
                AlertDialog(
                    onDismissRequest = { pinDialog = false },
                    title = { Text("Pin game") },
                    text = {
                        OutlinedTextField(
                            pinName, { pinName = it }, label = { Text("Game name") }, singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    },
                    confirmButton = {
                        TextButton({
                            val id = parsePlace(qj)?.first
                            if (id != null && pinName.isNotBlank()) {
                                games[pinName.trim()] = id
                                save()
                                toast("other", "Pinned ${pinName.trim()}.")
                            }
                            pinDialog = false
                        }) { Text("Pin") }
                    },
                    dismissButton = { TextButton({ pinDialog = false }) { Text("Cancel") } }
                )
            }

            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Clear all flags?") },
                    text = { Text("This removes every flag from the current profile. Saved profiles are not touched.") },
                    confirmButton = {
                        TextButton({ flags.clear(); save(); confirmClear = false }) { Text("Clear") }
                    },
                    dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } }
                )
            }

            if (confirmResetDlg) {
                AlertDialog(
                    onDismissRequest = { confirmResetDlg = false },
                    title = { Text("Delete the flag file?") },
                    text = { Text("This removes the file from the device and stops Roblox.") },
                    confirmButton = {
                        TextButton({ confirmResetDlg = false; doReset() }) { Text("Delete") }
                    },
                    dismissButton = { TextButton({ confirmResetDlg = false }) { Text("Cancel") } }
                )
            }

            if (confirmSettingsReset) {
                AlertDialog(
                    onDismissRequest = { confirmSettingsReset = false },
                    title = { Text("Reset appearance?") },
                    text = { Text("Theme, spacing, text size and motion go back to their defaults.") },
                    confirmButton = {
                        TextButton({ confirmSettingsReset = false; resetAppearance() }) { Text("Reset") }
                    },
                    dismissButton = { TextButton({ confirmSettingsReset = false }) { Text("Cancel") } }
                )
            }

            if (pkgDialog) {
                AlertDialog(
                    onDismissRequest = { pkgDialog = false },
                    title = { Text("Roblox package") },
                    text = {
                        OutlinedTextField(
                            pkgInput, { pkgInput = it.trim() }, label = { Text("Package name") }, singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    },
                    confirmButton = {
                        TextButton({
                            if (pkgInput.isNotBlank()) {
                                pkg = pkgInput
                                save()
                            }
                            pkgDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        Row {
                            TextButton({ pkgInput = DEFAULT_PKG }) { Text("Default") }
                            TextButton({ pkgDialog = false }) { Text("Cancel") }
                        }
                    }
                )
            }

            if (licenses) {
                AlertDialog(
                    onDismissRequest = { licenses = false },
                    title = { Text("Open source licenses") },
                    text = {
                        Text("TacoStrap is built with Kotlin, Jetpack Compose and the Shizuku API. See each project for its license terms.")
                    },
                    confirmButton = { TextButton({ licenses = false }) { Text("Close") } }
                )
            }
        }
    }
}
