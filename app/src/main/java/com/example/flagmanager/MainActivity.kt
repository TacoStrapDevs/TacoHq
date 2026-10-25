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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    AppTheme("Taco", Color(0xFFFFB02E), Color(0xFFF26B1D)),
    AppTheme("Salsa", Color(0xFFFF6B5E), Color(0xFFD7263D)),
    AppTheme("Guac", Color(0xFF9CCC65), Color(0xFF558B2F)),
    AppTheme("Void", Color(0xFFB69CFF), Color(0xFF7C4DFF)),
    AppTheme("Blox", Color(0xFF5BB8F5), Color(0xFF1E88E5)),
    AppTheme("Frost", Color(0xFFA8E6F0), Color(0xFF4DB6C6))
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
    )
)

private val LocalCorner = compositionLocalOf { 24.dp }
private val LocalPad = compositionLocalOf { 16.dp }
private val LocalMotion = compositionLocalOf { true }

private val Amber = Color(0xFFE8B84A)
private val Good = Color(0xFF5BC27A)
private val Bad = Color(0xFFEF6B63)

// ---------- Icons (drawn, no emoji) ----------

private enum class Ic {
    Home, Grid, Layers, Flag, Sliders, Right, Down, Up, Back, Close, Plus,
    Info, Warn, Play, Check, Download, Folder, Bell, Game, Doc, Search
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
        delay(index * 55L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(360)) + slideInVertically(tween(360)) { it / 8 }
    ) { content() }
}

@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(LocalCorner.current))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .animateContentSize()
            .padding(vertical = 4.dp),
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
        Modifier.fillMaxWidth().then(m).padding(horizontal = 20.dp, vertical = LocalPad.current),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    Text(text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

@Composable
private fun TacoSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val track by animateColorAsState(if (checked) cs.primary else Color(0xFF3A3632), tween(220), label = "swt")
    val thumb by animateColorAsState(if (checked) cs.onPrimary else Color(0xFF9A948C), tween(220), label = "swc")
    val x by animateDpAsState(
        if (checked) 25.dp else 4.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "swx"
    )
    Box(
        Modifier.width(54.dp).height(31.dp).clip(CircleShape).background(track)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
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
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
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
            Text(current, fontSize = 14.sp, maxLines = 1, modifier = Modifier.widthIn(max = 130.dp))
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
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
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
            .clickable(interactionSource = src, indication = null, onClick = onClick)
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
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary else Color.Transparent, tween(220), label = "pbg")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, tween(220), label = "pfg")
    Box(
        Modifier.clip(CircleShape).background(bg)
            .border(1.dp, if (selected) Color.Transparent else cs.outline, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text, Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg
        )
    }
}

@Composable
private fun TabStrip(items: List<Pair<String, Ic>>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        items.forEachIndexed { i, (label, ic) ->
            val sel = i == selected
            val c by animateColorAsState(if (sel) cs.primary else cs.onSurfaceVariant, tween(220), label = "tc")
            val w by animateDpAsState(if (sel) 36.dp else 0.dp, tween(280), label = "tw")
            Column(
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onSelect(i)
                }.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Ico(ic, c, 24.dp)
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(w).height(3.dp).clip(CircleShape).background(cs.primary))
            }
        }
    }
}

@Composable
private fun NavBar(items: List<Pair<String, Ic>>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
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
                ) { onSelect(i) },
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
    var trueBlack by remember { mutableStateOf(prefs.getBoolean("trueBlack", false)) }
    var compact by remember { mutableStateOf(prefs.getBoolean("compact", false)) }
    var motion by remember { mutableStateOf(prefs.getBoolean("motion", true)) }
    var glowAmt by remember { mutableStateOf(prefs.getFloat("glow2", 0.10f)) }
    var cornerDp by remember { mutableStateOf(prefs.getFloat("corner2", 24f)) }
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
            .putBoolean("trueBlack", trueBlack)
            .putBoolean("compact", compact)
            .putBoolean("motion", motion)
            .putFloat("glow2", glowAmt)
            .putFloat("corner2", cornerDp)
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
    val navItems = listOf(
        "Home" to Ic.Home, "Library" to Ic.Grid, "Mods" to Ic.Layers,
        "FastFlags" to Ic.Flag, "Settings" to Ic.Sliders
    )
    val pageAnim = if (motion) 260 else 0

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
                        }, kind = 2, h = 40.dp)
                        Btn("Copy JSON", {
                            clip.setPrimaryClip(ClipData.newPlainText("flags", toJson(flags)))
                            toast("other", "Copied JSON.")
                        }, kind = 2, h = 40.dp)
                        Btn("Share", { share(toJson(flags), "Share flags") }, kind = 2, h = 40.dp)
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
                        TextButton({ presets.remove(name); save() }) { Text("Delete") }
                    }
                }
            }
        }
    }

    @Composable
    fun HomePage() {
        val cs = MaterialTheme.colorScheme
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
                        Text("TacoStrap", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
                        Text("FastFlag manager for Roblox", fontSize = 14.sp, color = cs.onSurfaceVariant)
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (ready) Good else Bad))
                }
            }
            Appear(1) {
                Group {
                    Item("Roblox") { ValueText(robloxVersion) }
                    Item("Active flags") {
                        Text("$flagCount", fontSize = 15.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Item("Shizuku") {
                        if (ready) ValueText("Connected") else Btn("Grant", {
                            try { Shizuku.requestPermission(1001) } catch (e: Throwable) { toast("other", "Failed: start Shizuku first.") }
                            shStatus = shizukuStatus()
                        }, kind = 1, h = 36.dp)
                    }
                    Item("Root") { ValueText(if (useRoot) "On" else "Off") }
                    Item("Flag file", path)
                }
            }
            Appear(2) {
                Btn(
                    "Launch Roblox",
                    { scope.launch(Dispatchers.IO) { toast("launch", launchPkg(pkg, useRoot)) } },
                    Modifier.fillMaxWidth(), icon = Ic.Play, h = 56.dp
                )
            }
            Appear(3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Verify file", {
                        scope.launch(Dispatchers.IO) {
                            val (_, out) = runCmd("ls -l '$path'; cat '$path'", useRoot)
                            fileOut = out.ifEmpty { "Nothing returned." }
                        }
                    }, Modifier.weight(1f), kind = 1)
                    Btn("Reset", {
                        scope.launch(Dispatchers.IO) { toast("flag", resetFlags(path, pkg, useRoot)) }
                    }, Modifier.weight(1f), kind = 2)
                }
            }
            if (fileOut.isNotEmpty()) {
                Group {
                    Text(
                        fileOut, Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
            }
            Appear(4) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Pinned games")
                    Group {
                        if (games.isEmpty()) {
                            Item("No pinned games", "Add games in the Library tab.", { page = 1 }) {
                                Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                            }
                        }
                        games.keys.sorted().take(4).forEach { name ->
                            Item(name, "Place ID ${games[name]}", {
                                scope.launch(Dispatchers.IO) { toast("launch", launchGame(games[name] ?: "", useRoot)) }
                            }) { Ico(Ic.Play, cs.primary, 20.dp) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun LibraryPage() {
        val cs = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageHeader("Library", "Pin games and jump straight in.")
            Appear(1) {
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
            Appear(2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Pinned (${games.size})")
                    Group {
                        if (games.isEmpty()) Item("No games yet", "Add one above.")
                        games.keys.sorted().forEach { name ->
                            Item(name, "Place ID ${games[name]}", {
                                scope.launch(Dispatchers.IO) { toast("launch", launchGame(games[name] ?: "", useRoot)) }
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Btn("Play", {
                                        scope.launch(Dispatchers.IO) { toast("launch", launchGame(games[name] ?: "", useRoot)) }
                                    }, kind = 1, h = 36.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape).clickable { games.remove(name); save() },
                                        contentAlignment = Alignment.Center
                                    ) { Ico(Ic.Close, cs.onSurfaceVariant, 18.dp) }
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
        val cs = MaterialTheme.colorScheme
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
                            Text("Write flags to Roblox", fontSize = 17.sp, fontWeight = FontWeight.Medium)
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
                    listOf("Customize" to Ic.Sliders, "Packs" to Ic.Download, "Profiles" to Ic.Layers, "Files" to Ic.Folder),
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
                                        Btn("Apply", {
                                            flags.putAll(p.sets); save()
                                            toast("flag", "Added ${p.name} flags.")
                                        }, kind = 1, h = 38.dp)
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
                                }) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                                Item("Delete flag file", "Removes the file and stops Roblox", {
                                    scope.launch(Dispatchers.IO) { toast("flag", resetFlags(path, pkg, useRoot)) }
                                }) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                                Item("Clear Roblox cache", "Frees up space", {
                                    scope.launch(Dispatchers.IO) { toast("other", clearCache(pkg, useRoot)) }
                                }) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                            }
                            if (fileOut.isNotEmpty()) {
                                Group {
                                    Text(
                                        fileOut, Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp
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
                        Item("Roblox allowlist", "Flags Roblox allows on PC", { openUrl(ALLOWLIST_URL) }) {
                            Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                        }
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
            PageHeader("Settings")
            TabStrip(
                listOf(
                    "Appearance" to Ic.Sliders, "Notifications" to Ic.Bell, "Roblox" to Ic.Game,
                    "Backup" to Ic.Folder, "About" to Ic.Info
                ),
                setTab
            ) { setTab = it }
            Crossfade(targetState = setTab, animationSpec = tween(pageAnim), label = "setTab") { t ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (t) {
                        0 -> {
                            SectionLabel("Accent")
                            Group {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    THEMES.forEachIndexed { i, th ->
                                        val sel = themeIdx == i
                                        val s by animateFloatAsState(
                                            if (sel) 1.1f else 1f, spring(dampingRatio = 0.6f), label = "sw"
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() }, indication = null
                                            ) { themeIdx = i; save() }
                                        ) {
                                            Box(
                                                Modifier.size(46.dp)
                                                    .graphicsLayer { scaleX = s; scaleY = s }
                                                    .clip(CircleShape)
                                                    .background(Brush.linearGradient(listOf(th.primary, th.secondary)))
                                                    .then(if (sel) Modifier.border(2.dp, cs.onSurface, CircleShape) else Modifier),
                                                contentAlignment = Alignment.Center
                                            ) { if (sel) Ico(Ic.Check, Color(0xFF1A1206), 22.dp, 2.4f) }
                                            Spacer(Modifier.height(6.dp))
                                            Text(th.name, fontSize = 12.sp, color = if (sel) cs.onSurface else cs.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            SectionLabel("Surface")
                            Group {
                                SwitchItem("True black", "Pure black background for OLED screens.", trueBlack) { trueBlack = it; save() }
                                SwitchItem("Animated background", "A faint accent glow that drifts slowly.", animBg) { animBg = it; save() }
                                SwitchItem("Compact lists", "Tighter spacing between rows.", compact) { compact = it; save() }
                                SwitchItem("Motion", "Turn off for no animations or transitions.", motion) { motion = it; save() }
                            }
                            SectionLabel("Shape")
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
                                Item("Roblox", if (robloxVersion == "Not found") "Not found" else "Installed, version $robloxVersion")
                                Item("Package name", pkg, { pkgInput = pkg; pkgDialog = true }) {
                                    Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                                }
                                Item("Shizuku", shStatus) {
                                    Btn("Grant", {
                                        try { Shizuku.requestPermission(1001) } catch (e: Throwable) { toast("other", "Failed: start Shizuku first.") }
                                        shStatus = shizukuStatus()
                                    }, kind = 1, h = 36.dp)
                                }
                                SwitchItem(
                                    "Use root",
                                    "Apply FastFlags and mods with root. Turn this on to ask your root manager for permission.",
                                    useRoot
                                ) { useRoot = it; save() }
                            }
                            SectionLabel("Flag file")
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
                                        Pill("App data (root, unverified)", path == appPath) { path = appPath; save() }
                                    }
                                }
                                SwitchItem("Restart on Save and Launch", "Force-stops Roblox before launching.", restart) { restart = it; save() }
                                SwitchItem("Disable frame-rate cap", "Sets a system property when you save.", fps) { fps = it; save() }
                                Item("Clear Roblox cache", "Frees up space", {
                                    scope.launch(Dispatchers.IO) { toast("other", clearCache(pkg, useRoot)) }
                                }) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                            }
                        }
                        3 -> {
                            Group {
                                Item("Save your pinned games, profiles and flags to a file, or restore them on another device.")
                                Row(
                                    Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Btn("Export backup", { share(backupJson(), "Export backup") }, kind = 1)
                                    Btn("Import backup", {
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
                                    }, kind = 1)
                                }
                            }
                        }
                        else -> {
                            Group {
                                Item("Diagnostics", "A report you can copy for support", {
                                    val report = "TacoStrap 3.1\nAndroid ${Build.VERSION.SDK_INT}\n$shStatus\nRoot: $useRoot\nPackage: $pkg ($robloxVersion)\nPath: $path\nFlags: ${flags.size}"
                                    clip.setPrimaryClip(ClipData.newPlainText("report", report))
                                    toast("other", "Copied diagnostics.")
                                }) { Ico(Ic.Right, cs.onSurfaceVariant, 20.dp) }
                                Item("Licenses", "Open source notices", { licenses = true }) {
                                    Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                                }
                                Item("Roblox allowlist", "Flags Roblox allows on PC", { openUrl(ALLOWLIST_URL) }) {
                                    Ico(Ic.Right, cs.onSurfaceVariant, 20.dp)
                                }
                            }
                            Text(
                                "TacoStrap for Android 3.1",
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                fontSize = 13.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EditorScreen() {
        val cs = MaterialTheme.colorScheme
        val filtered = flags.keys.filter { it.contains(search, ignoreCase = true) }.sorted()
        Column(
            Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { showEditor = false },
                    contentAlignment = Alignment.Center
                ) { Ico(Ic.Back, cs.onSurface, 24.dp) }
                Text(
                    "FastFlag editor", Modifier.weight(1f).padding(start = 4.dp),
                    fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp
                )
                TextButton({ confirmClear = true }) { Text("Clear all") }
            }
            TabStrip(listOf("Flags" to Ic.Flag, "JSON" to Ic.Doc, "Profiles" to Ic.Layers), edTab) { edTab = it }
            when (edTab) {
                0 -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            search, { search = it }, label = { Text("Search flags") }, singleLine = true,
                            leadingIcon = { Ico(Ic.Search, cs.onSurfaceVariant, 20.dp) },
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Group {
                            Column(
                                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Add custom flag", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                OutlinedTextField(
                                    k, { k = it }, label = { Text("Name") }, singleLine = true,
                                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        v, { v = it }, label = { Text("Value") }, singleLine = true,
                                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                                    )
                                    Btn("Add", {
                                        if (k.isNotBlank()) { flags[k.trim()] = v.trim(); k = ""; v = ""; save() }
                                    }, icon = Ic.Plus)
                                }
                            }
                        }
                    }
                    item { SectionLabel("Your flags (${filtered.size})") }
                    if (filtered.isEmpty()) {
                        item { Text("No flags yet.", color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
                    }
                    items(filtered, key = { it }) { name ->
                        val value = flags[name] ?: ""
                        val isBool = value.equals("true", true) || value.equals("false", true)
                        Group {
                            Row(
                                Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).clickable { flags.remove(name); save() },
                                    contentAlignment = Alignment.Center
                                ) { Ico(Ic.Close, cs.onSurfaceVariant, 18.dp) }
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
                }
                1 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        jsonText, { jsonText = it }, label = { Text("ClientAppSettings JSON") },
                        minLines = 8, maxLines = 16,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Load current", { jsonText = toJson(flags); jsonMsg = "" }, kind = 2)
                        Btn("Replace flags", {
                            jsonMsg = runCatching {
                                val m = parseFlags(jsonText)
                                flags.clear(); flags.putAll(m); save()
                                "Loaded ${m.size} flags."
                            }.getOrElse { "Invalid JSON." }
                        })
                    }
                    if (jsonMsg.isNotEmpty()) Text(jsonMsg, fontSize = 13.sp, color = cs.primary)
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
        CompositionLocalProvider(
            LocalCorner provides cornerDp.dp,
            LocalPad provides (if (compact) 10.dp else 16.dp),
            LocalMotion provides motion
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(pc.copy(alpha = glowAmt), Color.Transparent),
                            center = Offset(300f + 480f * glow, -80f),
                            radius = 1100f
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
                    bottomBar = {
                        Column(
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant).navigationBarsPadding()
                        ) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box {
                                    Row(
                                        Modifier.height(48.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                            .clickable { pkgMenu = true }
                                            .padding(start = 18.dp, end = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (pkg == DEFAULT_PKG) "Roblox" else "Custom",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Ico(Ic.Up, MaterialTheme.colorScheme.primary, 16.dp, 2.2f)
                                    }
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
                                Btn("Save and Launch", { doSave(true) }, Modifier.weight(1.5f))
                                Btn("Save", { doSave(false) }, Modifier.weight(1f), kind = 1)
                            }
                            NavBar(navItems, page) { page = it }
                        }
                    }
                ) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
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
                    enter = slideInHorizontally(tween(if (motion) 300 else 0)) { it } + fadeIn(tween(if (motion) 300 else 0)),
                    exit = slideOutHorizontally(tween(if (motion) 240 else 0)) { it } + fadeOut(tween(if (motion) 240 else 0))
                ) { EditorScreen() }
            }

            renaming?.let { old ->
                AlertDialog(
                    onDismissRequest = { renaming = null },
                    shape = RoundedCornerShape(28.dp),
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
                    shape = RoundedCornerShape(28.dp),
                    title = { Text("Clear all flags?") },
                    text = { Text("This removes all ${flags.size} flags from the list. Saved profiles are kept.") },
                    confirmButton = { TextButton({ flags.clear(); save(); confirmClear = false }) { Text("Clear") } },
                    dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } }
                )
            }

            if (pkgDialog) {
                AlertDialog(
                    onDismissRequest = { pkgDialog = false },
                    shape = RoundedCornerShape(28.dp),
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
                    shape = RoundedCornerShape(28.dp),
                    title = { Text("Licenses") },
                    text = { Text("TacoStrap is built with Jetpack Compose and Shizuku (Apache License 2.0).") },
                    confirmButton = { TextButton({ licenses = false }) { Text("Close") } }
                )
            }
        }
    }
}
