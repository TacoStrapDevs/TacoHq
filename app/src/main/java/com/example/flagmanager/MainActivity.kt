@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.flagmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/* ======================================================================
 *  1. ENGINE: JSON & SHIZUKU FILE INJECTION
 * ====================================================================== */

const val APP_VERSION = "6.1"
const val DEFAULT_PKG = "com.roblox.client"
const val DEFAULT_PATH = "/sdcard/Android/data/com.roblox.client/files/ClientSettings/ClientAppSettings.json"
const val ALT_PATH = "/data/data/com.roblox.client/files/ClientSettings/ClientAppSettings.json"

fun parseFlags(text: String): Map<String, String> {
    val o = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrElse { JSONObject() }
    val out = LinkedHashMap<String, String>()
    val it = o.keys()
    while (it.hasNext()) {
        val k = it.next()
        out[k] = when (val v = o.get(k)) {
            is Boolean -> if (v) "True" else "False"
            else -> v.toString()
        }
    }
    return out
}

fun toJson(map: Map<String, String>, indent: Int = 0): String {
    val o = JSONObject()
    map.forEach { (k, v) ->
        val trimmed = v.trim()
        when {
            trimmed.equals("true", true) -> o.put(k, true)
            trimmed.equals("false", true) -> o.put(k, false)
            trimmed.toIntOrNull() != null -> o.put(k, trimmed.toInt())
            trimmed.toLongOrNull() != null -> o.put(k, trimmed.toLong())
            trimmed.toDoubleOrNull() != null -> o.put(k, trimmed.toDouble())
            else -> o.put(k, trimmed)
        }
    }
    return if (indent > 0) o.toString(indent) else o.toString()
}

private fun shq(s: String) = "'" + s.replace("'", "'\\''") + "'"

private fun shizukuProcess(cmd: String): Process {
    val m = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
        "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
    )
    m.isAccessible = true
    return m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
}

fun runCmd(cmd: String, root: Boolean): Pair<Boolean, String> = try {
    val p = if (root) Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)) else shizukuProcess(cmd)
    val out = p.inputStream.bufferedReader().readText()
    val err = p.errorStream.bufferedReader().readText()
    val code = p.waitFor()
    (code == 0) to (if (code == 0) out else err.ifBlank { out }).trim()
} catch (e: Throwable) {
    false to (e.message ?: "command error")
}

fun isRootAvailable(): Boolean = try {
    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
    p.waitFor() == 0
} catch (e: Throwable) { false }

fun fetchText(url: String): Pair<Boolean, String> = try {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 8000
    val code = c.responseCode
    if (code in 200..299) true to c.inputStream.bufferedReader().readText()
    else false to "HTTP $code"
} catch (e: Throwable) { false to (e.message ?: "network error") }

interface Bootstrapper {
    fun shizukuState(): ShizukuState
    suspend fun applyFlags(json: String, path: String, pkg: String, fpsCap: Boolean, root: Boolean): String
    suspend fun applyCustomFont(fontPath: String?, pkg: String, root: Boolean): Pair<Boolean, String>
    suspend fun restoreDefaultFont(pkg: String, root: Boolean): Pair<Boolean, String>
    suspend fun verify(json: String, path: String, root: Boolean): String
    suspend fun launchRoblox(pkg: String, root: Boolean, restart: Boolean): String
    suspend fun launchGame(pkg: String, id: String, root: Boolean, restart: Boolean): String
    suspend fun robloxVersion(pkg: String, root: Boolean): String?
    fun rootAvailable(): Boolean
    suspend fun clearCache(pkg: String, root: Boolean): String
}

object DefaultBootstrapper : Bootstrapper {
    override fun shizukuState(): ShizukuState = try {
        when {
            !runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrDefault(false) -> ShizukuState.NotRunning
            runCatching { rikka.shizuku.Shizuku.checkSelfPermission() }
                .getOrDefault(PackageManager.PERMISSION_DENIED) != PackageManager.PERMISSION_GRANTED -> ShizukuState.NeedsGrant
            else -> ShizukuState.Connected
        }
    } catch (e: Throwable) { ShizukuState.Unavailable }

    override suspend fun applyFlags(json: String, path: String, pkg: String, fpsCap: Boolean, root: Boolean): String =
        withContext(Dispatchers.IO) {
            val b64 = Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)
            val targetPath = "/sdcard/Android/data/$pkg/files/ClientSettings/ClientAppSettings.json"
            val paths = listOf(path, targetPath, ALT_PATH).distinct()
            var success = false
            var lastOut = ""

            for (p in paths) {
                val dir = p.substringBeforeLast('/', "")
                val mk = if (dir.isNotEmpty()) "mkdir -p ${shq(dir)} && " else ""
                val cmd = "${mk}printf %s ${shq(b64)} | base64 -d > ${shq(p)} && chmod 644 ${shq(p)}"
                val (ok, out) = runCmd(cmd, root)
                if (ok) success = true else lastOut = out
            }

            if (!success) return@withContext "Failed: ${lastOut.ifBlank { "could not write flag file" }}"
            if (fpsCap) runCmd("cmd game set --mode 2 --downscale disable --fps 0 ${shq(pkg)}", root)
            "Applied ${parseFlags(json).size} flags successfully"
        }

    override suspend fun applyCustomFont(fontPath: String?, pkg: String, root: Boolean): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            if (fontPath.isNullOrEmpty()) return@withContext true to "No font set."
            val srcFile = File(fontPath)
            if (!srcFile.exists()) return@withContext false to "Font file missing."

            val fontDir = "/sdcard/Android/data/$pkg/files/content/fonts"
            val targets = listOf("Gotham-Bold.ttf", "BuilderSans-Regular.ttf", "CustomFont.ttf")
            var okAll = true
            var err = ""

            val mk = runCmd("mkdir -p ${shq(fontDir)}", root)
            if (!mk.first) return@withContext false to "Failed creating font directory: ${mk.second}"

            for (t in targets) {
                val dest = "$fontDir/$t"
                val cmd = "cp ${shq(srcFile.absolutePath)} ${shq(dest)} && chmod 644 ${shq(dest)}"
                val (ok, out) = runCmd(cmd, root)
                if (!ok) { okAll = false; err = out }
            }
            if (okAll) true to "Custom font injected." else false to "Font injection failed: $err"
        }

    override suspend fun restoreDefaultFont(pkg: String, root: Boolean): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val fontDir = "/sdcard/Android/data/$pkg/files/content/fonts"
            val (ok, out) = runCmd("rm -rf ${shq(fontDir)}", root)
            if (ok) true to "Default fonts restored." else false to "Restore failed: $out"
        }

    override suspend fun verify(json: String, path: String, root: Boolean): String =
        withContext(Dispatchers.IO) {
            val (ok, out) = runCmd("cat ${shq(path)}", root)
            if (!ok) return@withContext "Verify failed: cannot read flag file"
            val onDisk = runCatching { parseFlags(out) }.getOrNull()
                ?: return@withContext "Verify failed: file is not valid JSON"
            val want = parseFlags(json)
            val bad = want.count { onDisk[it.key] != it.value }
            if (bad == 0) "Verified: ${want.size} flags match." else "Verify failed: $bad flags differ on disk"
        }

    override suspend fun launchRoblox(pkg: String, root: Boolean, restart: Boolean): String =
        withContext(Dispatchers.IO) {
            if (restart) runCmd("am force-stop ${shq(pkg)} && sleep 1", root)
            val (ok, out) = runCmd("monkey -p ${shq(pkg)} -c android.intent.category.LAUNCHER 1", root)
            if (ok) "Launched Roblox." else "Failed: ${out.ifBlank { "could not launch Roblox" }}"
        }

    override suspend fun launchGame(pkg: String, id: String, root: Boolean, restart: Boolean): String =
        withContext(Dispatchers.IO) {
            if (restart) runCmd("am force-stop ${shq(pkg)} && sleep 1", root)
            val (ok, out) = runCmd(
                "am start -a android.intent.action.VIEW -d ${shq("roblox://placeId=$id")} -p ${shq(pkg)}", root
            )
            if (ok) "Launching game $id." else "Failed: ${out.ifBlank { "could not open game" }}"
        }

    override suspend fun robloxVersion(pkg: String, root: Boolean): String? =
        withContext(Dispatchers.IO) {
            val (ok, out) = runCmd("dumpsys package ${shq(pkg)} | grep -m1 versionName", root)
            if (ok && out.contains("=")) out.substringAfter("=").trim().ifBlank { null } else null
        }

    override fun rootAvailable(): Boolean = isRootAvailable()

    override suspend fun clearCache(pkg: String, root: Boolean): String =
        withContext(Dispatchers.IO) {
            val (ok, out) = runCmd("rm -rf /sdcard/Android/data/$pkg/cache/*", root)
            if (ok) "Cleared cache." else "Failed: $out"
        }
}

interface AllowlistProvider {
    fun isAllowed(key: String): Boolean
}

object DefaultAllowlist : AllowlistProvider {
    private val ignoredKeys = setOf(
        "FFlagHandleAltEnterFullscreen",
        "DFIntClientPhysicsPostStepMode"
    )
    override fun isAllowed(key: String): Boolean = key !in ignoredKeys
}

sealed class ShizukuState(val ok: Boolean, val short: String, val long: String, val color: Color) {
    data object Connected : ShizukuState(true, "connected", "Connected", TacoPalette.Guac)
    data object NeedsGrant : ShizukuState(false, "tap grant", "Permission needed", TacoPalette.Tortilla)
    data object NotRunning : ShizukuState(false, "not running", "Not running", TacoPalette.Salsa)
    data object Unavailable : ShizukuState(false, "unavailable", "Unavailable", TacoPalette.Salsa)
}

/* ======================================================================
 *  2. THEME & PALETTE
 * ====================================================================== */

object TacoPalette {
    val Charcoal = Color(0xFF0F0B09)
    val CharcoalDeep = Color(0xFF070504)
    val Surface = Color(0xFF18120E)
    val SurfaceHigh = Color(0xFF221913)
    val Tortilla = Color(0xFFFFB84D)
    val TortillaDeep = Color(0xFFD4932A)
    val Salsa = Color(0xFFF75931)
    val Guac = Color(0xFF5CD674)
    val Cream = Color(0xFFFFF7ED)
    val Muted = Color(0xFFB8A48B)
    val Faint = Color(0xFF6E5E4E)
    val Outline = Color(0xFF382A1D)
}

data class TacoAccent(val name: String, val primary: Color, val onPrimary: Color, val deep: Color, val neon: Color)

val TACO_ACCENTS = listOf(
    TacoAccent("Amber Gold", TacoPalette.Tortilla, TacoPalette.CharcoalDeep, TacoPalette.TortillaDeep, Color(0xFFFFD166)),
    TacoAccent("Cyber Crimson", TacoPalette.Salsa, Color(0xFF2A0A04), Color(0xFFC73D17), Color(0xFFFF6B6B)),
    TacoAccent("Neon Emerald", TacoPalette.Guac, Color(0xFF061A0C), Color(0xFF3B9E50), Color(0xFF4EED77)),
    TacoAccent("Solar Flare", Color(0xFFFF8E3C), Color(0xFF241002), Color(0xFFCC5E13), Color(0xFFFFB35C)),
    TacoAccent("Electric Orchid", Color(0xFFB062FF), Color(0xFF140424), Color(0xFF7F35D4), Color(0xFFD18FFF))
)

val LocalTacoAccent = compositionLocalOf { TACO_ACCENTS[0] }
val LocalReduceMotion = compositionLocalOf { false }
val LocalCustomFont = compositionLocalOf { FontFamily.SansSerif }

object TacoType {
    @Composable
    fun getDisplayFamily(): FontFamily = LocalCustomFont.current

    val displayBig @Composable get() = TextStyle(
        fontFamily = getDisplayFamily(), fontWeight = FontWeight.ExtraBold, fontSize = 32.sp,
        letterSpacing = (-0.8).sp, color = TacoPalette.Cream
    )
    val display @Composable get() = TextStyle(
        fontFamily = getDisplayFamily(), fontWeight = FontWeight.Bold, fontSize = 22.sp,
        letterSpacing = (-0.3).sp, color = TacoPalette.Cream
    )
    val body @Composable get() = TextStyle(
        fontFamily = getDisplayFamily(), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TacoPalette.Cream
    )
    val label @Composable get() = TextStyle(
        fontFamily = getDisplayFamily(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
        letterSpacing = 1.4.sp, color = TacoPalette.Muted
    )
    val mono @Composable get() = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp,
        color = TacoPalette.Cream
    )
    val tnum @Composable get() = TextStyle(
        fontFamily = getDisplayFamily(), fontWeight = FontWeight.Bold, fontSize = 15.sp,
        fontFeatureSettings = "tnum", color = TacoPalette.Cream
    )
}

class FoldedSheetShape(private val fold: Dp = 22.dp, private val rest: Dp = 12.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val f = with(density) { fold.toPx() }
        val r = with(density) { rest.toPx() }
        return Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                topLeft = CornerRadius(f),
                topRight = CornerRadius(r),
                bottomRight = CornerRadius(f * 0.7f),
                bottomLeft = CornerRadius(r)
            )
        )
    }
}

object TacoShapes {
    val menuCard: Shape = FoldedSheetShape()
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val card = RoundedCornerShape(20.dp)
    val control = RoundedCornerShape(16.dp)
    val pill = CircleShape
}

object TacoMotion {
    val softSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 400f)
    val shimmer = tween<Float>(1200)
}

@Composable
fun TacoTheme(accentIdx: Int, amoled: Boolean, reduceMotion: Boolean, fontIdx: Int, content: @Composable () -> Unit) {
    val accent = TACO_ACCENTS[accentIdx.coerceIn(0, TACO_ACCENTS.lastIndex)]
    val base = if (amoled) TacoPalette.CharcoalDeep else TacoPalette.Charcoal
    val surface = if (amoled) Color(0xFF100B08) else TacoPalette.Surface
    val fontFamily = when (fontIdx) {
        1 -> FontFamily.Monospace
        2 -> FontFamily.Serif
        3 -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
    val scheme = darkColorScheme(
        primary = accent.primary, onPrimary = accent.onPrimary,
        primaryContainer = accent.deep, onPrimaryContainer = TacoPalette.Cream,
        secondary = accent.deep, onSecondary = TacoPalette.Cream,
        tertiary = TacoPalette.Guac, onTertiary = Color(0xFF0E220A),
        background = base, onBackground = TacoPalette.Cream,
        surface = base, onSurface = TacoPalette.Cream,
        surfaceVariant = surface, onSurfaceVariant = TacoPalette.Muted,
        error = TacoPalette.Salsa, onError = Color(0xFF2A0E06),
        outline = TacoPalette.Outline, outlineVariant = Color(0xFF2E2016)
    )
    CompositionLocalProvider(
        LocalTacoAccent provides accent,
        LocalReduceMotion provides reduceMotion,
        LocalCustomFont provides fontFamily,
        LocalContentColor provides TacoPalette.Cream
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private val grainTile: ImageBitmap by lazy {
    val ib = ImageBitmap(96, 96)
    val c = ComposeCanvas(ib)
    val rnd = java.util.Random(42)
    val paint = Paint().apply { color = Color.White.copy(alpha = 0.025f) }
    repeat(260) { c.drawCircle(Offset(rnd.nextFloat() * 96f, rnd.nextFloat() * 96f), 0.85f, paint) }
    ib
}

fun Modifier.tacoGrain(): Modifier = this.drawBehind {
    drawImage(
        image = grainTile,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(grainTile.width, grainTile.height),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = 0.55f
    )
}

/* ======================================================================
 *  3. UI COMPONENTS & ICONS
 * ====================================================================== */

enum class StepStatus { Pending, Running, Done, Failed, Skipped }

data class LaunchStep(val title: String, val detail: String = "", val status: StepStatus = StepStatus.Pending)

sealed interface LaunchState {
    data object Idle : LaunchState
    data class Running(val steps: List<LaunchStep>, val stepIndex: Int) : LaunchState
    data class Success(val message: String, val steps: List<LaunchStep>) : LaunchState
    data class Failed(val message: String, val steps: List<LaunchStep>, val failedStep: Int) : LaunchState
}

enum class LaunchMode(val label: String) { SAVE_LAUNCH("Save & launch"), APPLY_ONLY("Apply only"), VERIFY("Verify") }

fun hapticCrunch(context: Context) {
    runCatching {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 25, 35, 50), -1))
        } else @Suppress("DEPRECATION") {
            v.vibrate(60)
        }
    }
}

fun hapticTick(context: Context) {
    runCatching {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(10, 50))
        else @Suppress("DEPRECATION") v.vibrate(10)
    }
}

enum class TacoIcon {
    Home, Layers, Flag, Gamepad, Sliders, Play, Check, Close, Plus, Search, Copy, Trash,
    Grip, Globe, Doc, Bolt, Warn, Info, Star, Refresh, Save, Up, Down, Dot, Image, Font
}

private fun DrawScope.pl(c: Color, st: Stroke, s: Float, vararg p: Float) {
    val path = Path()
    path.moveTo(p[0] * s, p[1] * s)
    var i = 2
    while (i < p.size) { path.lineTo(p[i] * s, p[i + 1] * s); i += 2 }
    drawPath(path, c, style = st)
}

@Composable
fun TacoIcon(kind: TacoIcon, tint: Color, size: Dp = 22.dp, stroke: Float = 2f) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val st = Stroke(width = stroke * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            TacoIcon.Home -> {
                pl(tint, st, s, 3f, 11f, 12f, 3.5f, 21f, 11f)
                pl(tint, st, s, 5.5f, 9.5f, 5.5f, 20f, 18.5f, 20f, 18.5f, 9.5f)
            }
            TacoIcon.Layers -> {
                pl(tint, st, s, 12f, 3.5f, 21f, 8.5f, 12f, 13.5f, 3f, 8.5f, 12f, 3.5f)
                pl(tint, st, s, 3f, 12.5f, 12f, 17.5f, 21f, 12.5f)
                pl(tint, st, s, 3f, 16.5f, 12f, 21.5f, 21f, 16.5f)
            }
            TacoIcon.Flag -> {
                pl(tint, st, s, 6f, 21f, 6f, 4f)
                pl(tint, st, s, 6f, 4.5f, 18f, 4.5f, 15f, 9f, 18f, 13.5f, 6f, 13.5f)
            }
            TacoIcon.Gamepad -> {
                drawRoundRect(tint, Offset(3f * s, 7.5f * s), Size(18f * s, 10f * s), CornerRadius(5f * s), style = st)
                pl(tint, st, s, 7.5f, 10.5f, 7.5f, 14.5f)
                pl(tint, st, s, 5.5f, 12.5f, 9.5f, 12.5f)
                drawCircle(tint, 1.2f * s, Offset(15.5f * s, 11.5f * s))
                drawCircle(tint, 1.2f * s, Offset(18f * s, 13.5f * s))
            }
            TacoIcon.Sliders -> {
                pl(tint, st, s, 4f, 7f, 20f, 7f); pl(tint, st, s, 4f, 12f, 20f, 12f); pl(tint, st, s, 4f, 17f, 20f, 17f)
                drawCircle(tint, 2.1f * s, Offset(9f * s, 7f * s), style = st)
                drawCircle(tint, 2.1f * s, Offset(15f * s, 12f * s), style = st)
                drawCircle(tint, 2.1f * s, Offset(7f * s, 17f * s), style = st)
            }
            TacoIcon.Play -> pl(tint, st, s, 8f, 5f, 19f, 12f, 8f, 19f, 8f, 5f)
            TacoIcon.Check -> pl(tint, st, s, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
            TacoIcon.Close -> { pl(tint, st, s, 6f, 6f, 18f, 18f); pl(tint, st, s, 18f, 6f, 6f, 18f) }
            TacoIcon.Plus -> { pl(tint, st, s, 12f, 5f, 12f, 19f); pl(tint, st, s, 5f, 12f, 19f, 12f) }
            TacoIcon.Search -> {
                drawCircle(tint, 6.5f * s, Offset(11f * s, 11f * s), style = st)
                pl(tint, st, s, 16f, 16f, 20.5f, 20.5f)
            }
            TacoIcon.Copy -> {
                drawRoundRect(tint, Offset(8.5f * s, 8.5f * s), Size(11f * s, 12f * s), CornerRadius(2f * s), style = st)
                pl(tint, st, s, 5.5f, 15f, 5.5f, 5f, 15f, 5f)
            }
            TacoIcon.Trash -> {
                pl(tint, st, s, 5f, 7f, 19f, 7f); pl(tint, st, s, 10f, 7f, 10f, 4.5f, 14f, 4.5f, 14f, 7f)
                pl(tint, st, s, 7f, 7f, 7.5f, 20f, 16.5f, 20f, 17f, 7f)
            }
            TacoIcon.Grip -> repeat(3) { r ->
                repeat(2) { c2 -> drawCircle(tint, 1.4f * s, Offset((9f + c2 * 6f) * s, (8f + r * 4f) * s)) }
            }
            TacoIcon.Globe -> {
                drawCircle(tint, 9f * s, Offset(12f * s, 12f * s), style = st)
                pl(tint, st, s, 3f, 12f, 21f, 12f)
            }
            TacoIcon.Doc -> {
                drawRoundRect(tint, Offset(5f * s, 3f * s), Size(14f * s, 18f * s), CornerRadius(2f * s), style = st)
                pl(tint, st, s, 8.5f, 9f, 15.5f, 9f); pl(tint, st, s, 8.5f, 13f, 15.5f, 13f)
            }
            TacoIcon.Bolt -> pl(tint, st, s, 13f, 3f, 6f, 13.5f, 11f, 13.5f, 10.5f, 21f, 18f, 10f, 13f, 10f, 13f, 3f)
            TacoIcon.Warn -> {
                pl(tint, st, s, 12f, 3.5f, 21.5f, 20f, 2.5f, 20f, 12f, 3.5f)
                pl(tint, st, s, 12f, 10f, 12f, 14.5f)
                drawCircle(tint, 1.1f * s, Offset(12f * s, 17.3f * s))
            }
            TacoIcon.Info -> {
                drawCircle(tint, 9f * s, Offset(12f * s, 12f * s), style = st)
                pl(tint, st, s, 12f, 11f, 12f, 16.5f)
                drawCircle(tint, 1.1f * s, Offset(12f * s, 7.8f * s))
            }
            TacoIcon.Star -> {
                val p = Path()
                p.moveTo(12f * s, 3.5f * s); p.lineTo(14.6f * s, 9f * s); p.lineTo(20.5f * s, 9.6f * s)
                p.lineTo(16f * s, 13.6f * s); p.lineTo(17.4f * s, 19.5f * s); p.lineTo(12f * s, 16.4f * s)
                p.lineTo(6.6f * s, 19.5f * s); p.lineTo(8f * s, 13.6f * s); p.lineTo(3.5f * s, 9.6f * s)
                p.lineTo(9.4f * s, 9f * s); p.close()
                drawPath(p, tint, style = st)
            }
            TacoIcon.Refresh -> {
                drawArc(tint, 40f, 290f, false, topLeft = Offset(4f * s, 4f * s), size = Size(16f * s, 16f * s), style = st)
                pl(tint, st, s, 18f, 5f, 18f, 9f, 14f, 9f)
            }
            TacoIcon.Save -> {
                pl(tint, st, s, 5f, 4.5f, 16f, 4.5f, 19f, 7.5f, 19f, 19.5f, 5f, 19.5f, 5f, 4.5f)
                drawRoundRect(tint, Offset(8f * s, 12.5f * s), Size(8f * s, 7f * s), CornerRadius(0.8f * s), style = st)
            }
            TacoIcon.Up -> pl(tint, st, s, 5f, 15f, 12f, 8f, 19f, 15f)
            TacoIcon.Down -> pl(tint, st, s, 5f, 9f, 12f, 16f, 19f, 9f)
            TacoIcon.Dot -> drawCircle(tint, 2.3f * s, Offset(12f * s, 12f * s))
            TacoIcon.Image -> {
                drawRoundRect(tint, Offset(3f * s, 5f * s), Size(18f * s, 14f * s), CornerRadius(2f * s), style = st)
                drawCircle(tint, 2.5f * s, Offset(8f * s, 10f * s))
                pl(tint, st, s, 3f, 16f, 9f, 11f, 14f, 15f, 18f, 12f, 21f, 15f)
            }
            TacoIcon.Font -> {
                pl(tint, st, s, 4f, 20f, 12f, 4f, 20f, 20f)
                pl(tint, st, s, 7f, 14f, 17f, 14f)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val accent = LocalTacoAccent.current
    Row(modifier.padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 4.dp, height = 14.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(accent.neon, accent.primary)))
        )
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = TacoType.label, color = accent.neon)
    }
}

@Composable
fun TacoSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val accent = LocalTacoAccent.current
    val reduce = LocalReduceMotion.current
    val x by animateDpAsState(
        if (checked) 26.dp else 4.dp,
        if (reduce) tween(0) else spring(dampingRatio = 0.58f, stiffness = 550f),
        label = "swx"
    )
    Box(
        Modifier.width(54.dp).height(32.dp).clip(CircleShape)
            .background(if (enabled) TacoPalette.SurfaceHigh else TacoPalette.Surface)
            .border(1.dp, if (checked) accent.primary.copy(0.6f) else TacoPalette.Outline, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) { onChange(!checked) }
    ) {
        if (checked) {
            Box(
                Modifier.matchParentSize()
                    .background(Brush.horizontalGradient(listOf(accent.primary, accent.deep)))
            )
        }
        Box(
            Modifier.offset(x = x, y = 4.dp).size(24.dp).clip(CircleShape)
                .background(if (checked) TacoPalette.Cream else TacoPalette.Muted)
        )
    }
}

enum class ButtonKind { Primary, Ghost, Quiet, Danger }

@Composable
fun TacoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: TacoIcon? = null,
    height: Dp = 48.dp
) {
    val accent = LocalTacoAccent.current
    val reduce = LocalReduceMotion.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        if (reduce) tween(0) else spring(dampingRatio = 0.55f, stiffness = 650f),
        label = "btn"
    )
    val look: Triple<Brush, Color, Color?> = when (kind) {
        ButtonKind.Primary -> Triple(
            Brush.horizontalGradient(listOf(accent.neon, accent.primary, accent.deep)), accent.onPrimary, accent.neon.copy(0.5f)
        )
        ButtonKind.Ghost -> Triple(
            Brush.horizontalGradient(listOf(accent.primary.copy(0.18f), accent.primary.copy(0.08f))),
            accent.neon, accent.primary.copy(0.4f)
        )
        ButtonKind.Quiet -> Triple(
            Brush.horizontalGradient(listOf(TacoPalette.SurfaceHigh, TacoPalette.Surface)),
            TacoPalette.Cream, TacoPalette.Outline
        )
        ButtonKind.Danger -> Triple(
            Brush.horizontalGradient(listOf(TacoPalette.Salsa.copy(0.22f), TacoPalette.Salsa.copy(0.08f))),
            TacoPalette.Salsa, TacoPalette.Salsa.copy(0.45f)
        )
    }
    Row(
        modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(look.first)
            .then(if (look.third != null) Modifier.border(1.dp, look.third!!, CircleShape) else Modifier)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = if (text.isEmpty()) 0.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            TacoIcon(icon, look.second, 17.dp, 2.2f)
            if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
        }
        if (text.isNotEmpty()) Text(text, color = look.second, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun IconCircle(icon: TacoIcon, tint: Color, size: Dp = 40.dp, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.35f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { TacoIcon(icon, tint, size * 0.48f, 2.1f) }
}

@Composable
fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalTacoAccent.current
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "ca")
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) Color.Transparent else TacoPalette.SurfaceHigh)
            .border(1.dp, if (selected) accent.neon.copy(0.7f) else TacoPalette.Outline, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier.matchParentSize().graphicsLayer { alpha = a }
                .background(Brush.horizontalGradient(listOf(accent.neon, accent.primary, accent.deep)))
        )
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) accent.onPrimary else TacoPalette.Muted,
            maxLines = 1
        )
    }
}

@Composable
fun AllowlistBadge(allowed: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (allowed != false) {
            TacoIcon(TacoIcon.Check, TacoPalette.Guac, 13.dp, 2.6f)
            Spacer(Modifier.width(4.dp))
            Text("allowed", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TacoPalette.Guac)
        } else {
            TacoIcon(TacoIcon.Warn, TacoPalette.Salsa, 13.dp, 2.6f)
            Spacer(Modifier.width(4.dp))
            Text("ignored by roblox", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TacoPalette.Salsa)
        }
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = TacoShapes.control) {
    if (LocalReduceMotion.current) {
        Box(modifier.clip(shape).background(TacoPalette.SurfaceHigh))
        return
    }
    val t = rememberInfiniteTransition(label = "shim")
    val p by t.animateFloat(
        0f, 1f, infiniteRepeatable(TacoMotion.shimmer, RepeatMode.Reverse), label = "shp"
    )
    Box(
        modifier.clip(shape).background(
            Brush.horizontalGradient(
                listOf(TacoPalette.Surface, TacoPalette.SurfaceHigh, TacoPalette.Surface),
                startX = -200f + p * 600f, endX = 400f + p * 600f
            )
        )
    )
}

@Composable
fun EmptyState(icon: TacoIcon, title: String, sub: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconCircle(icon, LocalTacoAccent.current.neon, 54.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = TacoType.display, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            sub, fontSize = 13.sp, color = TacoPalette.Muted,
            textAlign = TextAlign.Center, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        if (action != null) { Spacer(Modifier.height(14.dp)); action() }
    }
}

@Composable
fun SuccessBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(TacoShapes.control)
            .background(TacoPalette.Guac.copy(alpha = 0.16f))
            .border(1.dp, TacoPalette.Guac.copy(alpha = 0.5f), TacoShapes.control)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(TacoIcon.Check, TacoPalette.Guac, 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = TacoPalette.Guac, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    if (LocalReduceMotion.current) { content(); return }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 50L); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 5 }
    ) { content() }
}

data class LedStatus(val label: String, val value: String, val color: Color, val optional: Boolean = false)

@Composable
fun StatusChipsRow(statuses: List<LedStatus>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .hScroll()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        statuses.forEach { s ->
            Surface(
                color = s.color.copy(alpha = 0.18f),
                contentColor = s.color,
                shape = CircleShape,
                modifier = Modifier
                    .border(1.dp, s.color.copy(alpha = 0.4f), CircleShape)
                    .clickable(onClick = onClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${s.label}: ${s.value}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun StatusSheetContent(
    statuses: List<Triple<String, String, Color>>,
    fixSteps: List<Pair<String, String>>,
    onGrantShizuku: () -> Unit,
    onOpenShizukuGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalTacoAccent.current
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Text("Kitchen status", style = TacoType.display)
        Spacer(Modifier.height(16.dp))
        statuses.forEach { (name, state, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(10.dp))
                Text(name, style = TacoType.body, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(state, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(14.dp))
        fixSteps.forEach { (name, steps) ->
            Column(
                Modifier.fillMaxWidth().clip(TacoShapes.control)
                    .background(TacoPalette.Surface).border(1.dp, TacoPalette.Outline, TacoShapes.control)
                    .padding(14.dp)
            ) {
                Text(name.uppercase(), style = TacoType.label, color = accent.neon)
                Spacer(Modifier.height(4.dp))
                Text(steps, fontSize = 13.sp, color = TacoPalette.Muted, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TacoButton("Grant Shizuku", onGrantShizuku, Modifier.weight(1f), ButtonKind.Ghost, TacoIcon.Bolt, 46.dp)
            TacoButton("Setup guide", onOpenShizukuGuide, Modifier.weight(1f), ButtonKind.Quiet, TacoIcon.Globe, 46.dp)
        }
        Spacer(Modifier.height(10.dp))
        TacoButton("Close", onDismiss, Modifier.fillMaxWidth(), ButtonKind.Quiet, height = 46.dp)
    }
}

@Composable
fun ModeSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val modes = LaunchMode.entries.map { it.label }
    val pagerState = rememberPagerState(initialPage = selected, pageCount = { modes.size })
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != selected) onSelect(pagerState.currentPage)
    }
    LaunchedEffect(selected) { if (selected != pagerState.currentPage) pagerState.animateScrollToPage(selected) }
    val accent = LocalTacoAccent.current
    Column(modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            val isSel = page == pagerState.currentPage
            Box(Modifier.fillMaxWidth().height(42.dp), contentAlignment = Alignment.Center) {
                Text(
                    modes[page],
                    fontSize = if (isSel) 17.sp else 14.sp,
                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSel) TacoPalette.Cream else TacoPalette.Muted,
                    letterSpacing = if (isSel) (-0.3).sp else 0.sp
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            val frac = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val fracAnim by animateFloatAsState(frac, TacoMotion.softSpring, label = "mfrac")
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val segW = maxWidth / modes.size
                Box(
                    Modifier.width(segW).fillMaxHeight()
                        .offset(x = segW * fracAnim.coerceIn(0f, (modes.size - 1).toFloat())),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.width(32.dp).height(3.dp).clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(accent.neon, accent.primary)))
                    )
                }
            }
        }
    }
}

@Composable
fun PipelineTimeline(
    launch: LaunchState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (launch is LaunchState.Idle) return
    Column(modifier) {
        when (launch) {
            is LaunchState.Running -> launch.steps.forEachIndexed { i, step ->
                StepRow(step, isCurrent = i == launch.stepIndex, isLast = i == launch.steps.lastIndex)
            }
            is LaunchState.Success -> {
                launch.steps.forEachIndexed { i, step -> StepRow(step, false, i == launch.steps.lastIndex) }
                Spacer(Modifier.height(12.dp))
                SuccessBanner(launch.message)
            }
            is LaunchState.Failed -> {
                launch.steps.forEachIndexed { i, step -> StepRow(step, i == launch.failedStep, i == launch.steps.lastIndex) }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(TacoShapes.control)
                        .background(TacoPalette.Salsa.copy(alpha = 0.15f))
                        .border(1.dp, TacoPalette.Salsa.copy(alpha = 0.45f), TacoShapes.control)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconCircle(TacoIcon.Warn, TacoPalette.Salsa, 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(launch.message, color = TacoPalette.Salsa, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                }
            }
            LaunchState.Idle -> {}
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (launch is LaunchState.Running || launch is LaunchState.Failed) {
                TacoButton("Retry", onRetry, Modifier.weight(1f), ButtonKind.Ghost, TacoIcon.Refresh, 44.dp)
            }
            TacoButton(
                if (launch is LaunchState.Success) "Done" else "Cancel",
                onCancel, Modifier.weight(1f), ButtonKind.Quiet, TacoIcon.Close, 44.dp
            )
        }
    }
}

@Composable
private fun StepRow(step: LaunchStep, isCurrent: Boolean, isLast: Boolean) {
    val accent = LocalTacoAccent.current
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (step.status) {
                StepStatus.Done -> IconCircle(TacoIcon.Check, TacoPalette.Guac, 24.dp)
                StepStatus.Failed -> IconCircle(TacoIcon.Close, TacoPalette.Salsa, 24.dp)
                StepStatus.Skipped -> IconCircle(TacoIcon.Dot, TacoPalette.Faint, 24.dp)
                StepStatus.Running -> {
                    val t = rememberInfiniteTransition(label = "run")
                    val a by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "ra")
                    IconCircle(TacoIcon.Dot, accent.neon.copy(alpha = if (LocalReduceMotion.current) 1f else a), 24.dp)
                }
                StepStatus.Pending -> IconCircle(TacoIcon.Dot, TacoPalette.Faint, 24.dp)
            }
            if (!isLast) {
                Box(
                    Modifier.width(2.dp).height(24.dp)
                        .background(
                            if (step.status == StepStatus.Done) TacoPalette.Guac.copy(0.6f)
                            else TacoPalette.Outline
                        )
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(bottom = if (isLast) 0.dp else 12.dp)) {
            Text(
                step.title,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = when (step.status) {
                    StepStatus.Done -> TacoPalette.Cream
                    StepStatus.Failed -> TacoPalette.Salsa
                    StepStatus.Running -> accent.neon
                    else -> TacoPalette.Muted
                }
            )
            if (step.detail.isNotEmpty()) {
                Text(step.detail, fontSize = 12.sp, color = TacoPalette.Muted, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun OrderTicket(modifier: Modifier = Modifier, cardCorner: Int = 20, glassOpacity: Float = 0.88f, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(cardCorner.dp)
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(cs.surfaceVariant.copy(alpha = glassOpacity))
                .border(1.dp, cs.outlineVariant.copy(alpha = 0.8f), shape)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            content = content
        )
        TicketEdge(top = true, pageBg = cs.background)
        TicketEdge(top = false, pageBg = cs.background)
    }
}

@Composable
private fun BoxScope.TicketEdge(top: Boolean, pageBg: Color) {
    Row(
        Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter).fillMaxWidth()
            .offset(y = if (top) (-4.5).dp else 4.dp)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(16) { Box(Modifier.size(8.dp).clip(CircleShape).background(pageBg)) }
    }
}

@Composable
fun DashedDivider() {
    val c = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        drawLine(
            c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        )
    }
}

@Composable
fun TicketLine(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    valueColor: Color = TacoPalette.Cream,
    sub: String? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), style = TacoType.label, modifier = Modifier.width(90.dp))
        Column(Modifier.weight(1f)) {
            Text(value, style = TacoType.mono.copy(fontSize = 13.sp, color = valueColor), lineHeight = 18.sp)
            if (sub != null) Text(sub, fontSize = 11.sp, color = TacoPalette.Muted)
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            TacoIcon(TacoIcon.Down, TacoPalette.Faint, 14.dp, 2.2f)
        }
    }
}

@Composable
fun CountUpText(target: Int, style: TextStyle = TacoType.tnum, color: Color = TacoPalette.Cream) {
    val v by animateIntAsState(target, if (LocalReduceMotion.current) tween(0) else tween(600), label = "cnt")
    Text("$v", style = style, color = color)
}

@Composable
fun Modifier.hScroll(): Modifier = this.horizontalScroll(rememberScrollState())

@Composable
fun AlertishDialog(
    title: String,
    body: String?,
    onDismiss: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(26.dp),
        title = { Text(title, style = TacoType.display, fontSize = 20.sp) },
        text = {
            Column {
                if (body != null) Text(body, fontSize = 14.sp, color = TacoPalette.Muted, lineHeight = 20.sp)
                content?.invoke(this)
            }
        },
        confirmButton = { TacoButton("Close", onDismiss, kind = ButtonKind.Quiet, height = 42.dp) }
    )
}

@Composable
internal fun tacoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TacoPalette.Cream, unfocusedTextColor = TacoPalette.Cream,
    focusedBorderColor = LocalTacoAccent.current.neon, unfocusedBorderColor = TacoPalette.Outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)

/* ======================================================================
 *  4. HOME SCREEN & REFINED HOLD-TO-INJECT COMAL DIAL
 * ====================================================================== */

fun heatLevel(flagCount: Int): Int = when {
    flagCount <= 4 -> 0
    flagCount <= 12 -> 1
    flagCount <= 22 -> 2
    else -> 3
}

fun heatName(level: Int): String = listOf("Mild", "Medium", "Hot", "Extra hot")[level.coerceIn(0, 3)]

private val HEAT_COLORS = listOf(
    Color(0xFFFFB84D), Color(0xFFFF9E3D), Color(0xFFF75931), Color(0xFFFF3D2E)
)

@Composable
fun SizzleParticles(active: Boolean, modifier: Modifier = Modifier) {
    if (!active || LocalReduceMotion.current) return
    val particles = remember {
        List(26) { Triple(Random.nextFloat(), 0.5f + Random.nextFloat(), Random.nextFloat() * 6.28f) }
    }
    val t = rememberInfiniteTransition(label = "siz")
    val f by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "sf")
    Canvas(modifier) {
        val cx = size.width / 2
        val top = size.height * 0.42f
        particles.forEachIndexed { i, (off, spd, ph) ->
            val p = (off + f * spd) % 1f
            val y = top - p * size.height * 0.35f
            val x = cx + sin(p * 7f + ph) * size.width * 0.17f * p
            val alpha = (1f - p) * 0.5f
            val r = 2f + 4.5f * p
            drawCircle(
                if (i % 3 == 0) TacoPalette.Cream else HEAT_COLORS[(i + 1) % 4],
                radius = r, center = Offset(x, y), alpha = alpha
            )
        }
    }
}

@Composable
fun ComalDial(
    heat: Int,
    launch: LaunchState,
    enabled: Boolean,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalTacoAccent.current
    val cs = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val trigger by rememberUpdatedState(onTrigger)
    val idle = launch is LaunchState.Idle

    var pressed by remember { mutableStateOf(false) }
    val hold = remember { Animatable(0f) }
    
    LaunchedEffect(pressed, enabled, idle) {
        if (pressed && enabled && idle) {
            val res = hold.animateTo(1f, tween(700, easing = LinearEasing))
            if (res.endReason == AnimationEndReason.Finished) trigger()
        } else {
            hold.animateTo(0f, if (reduce) tween(0) else spring(dampingRatio = 0.65f))
        }
    }

    val gap = 7f
    val segSweep = (360f - gap * 4f) / 4f
    val sweeps = List(4) { i ->
        animateFloatAsState(
            if (i <= heat) segSweep else 0f,
            if (reduce) tween(0) else spring(dampingRatio = 0.75f, stiffness = 220f),
            label = "heat$i"
        )
    }

    val overall: Float = when (launch) {
        is LaunchState.Running -> (launch.stepIndex + 1f) / launch.steps.size
        is LaunchState.Success -> 1f
        is LaunchState.Failed -> launch.failedStep / launch.steps.size.toFloat()
        else -> 0f
    }
    val progress by animateFloatAsState(overall, if (reduce) tween(0) else tween(500), label = "prog")
    val running = launch is LaunchState.Running
    val fillColor = when (launch) {
        is LaunchState.Success -> TacoPalette.Guac
        is LaunchState.Failed -> TacoPalette.Salsa
        else -> accent.neon
    }

    Box(
        modifier
            .aspectRatio(1f)
            .pointerInput(enabled, idle) {
                if (enabled && idle) {
                    detectTapGestures(onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val strokeW = 14.dp.toPx()
            val rect = Rect(strokeW, strokeW, size.width - strokeW, size.height - strokeW)
            if (idle) {
                for (i in 0..3) {
                    val start = -90f + i * (segSweep + gap) + gap / 2
                    drawArc(TacoPalette.Outline.copy(alpha = 0.65f), start, segSweep, false, rect.topLeft, rect.size, style = Stroke(strokeW, cap = StrokeCap.Round))
                    val sw = sweeps[i].value
                    if (sw > 1f) {
                        drawArc(HEAT_COLORS[i], start, sw, false, rect.topLeft, rect.size, style = Stroke(strokeW, cap = StrokeCap.Round))
                    }
                }
            } else {
                drawArc(TacoPalette.Outline.copy(alpha = 0.65f), -90f, 360f, false, rect.topLeft, rect.size, style = Stroke(strokeW, cap = StrokeCap.Round))
                drawArc(fillColor, -90f, progress * 360f, false, rect.topLeft, rect.size, style = Stroke(strokeW, cap = StrokeCap.Round))
            }
        }

        val discScale by animateFloatAsState(
            if (pressed) 0.94f else 1f,
            if (reduce) tween(0) else spring(dampingRatio = 0.5f, stiffness = 500f), label = "disc"
        )
        Canvas(Modifier.fillMaxSize().padding(32.dp).graphicsLayer { scaleX = discScale; scaleY = discScale }) {
            val r = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(
                    listOf(TacoPalette.SurfaceHigh, cs.surfaceVariant, Color(0xFF080503)),
                    center = Offset(size.width / 2, size.height / 2), radius = r
                ),
                radius = r
            )
            drawCircle(accent.neon.copy(alpha = if (running || pressed) 0.7f else 0.3f), radius = r, style = Stroke(2.dp.toPx()))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (launch) {
                is LaunchState.Running -> {
                    val step = launch.steps.getOrNull(launch.stepIndex)
                    Text(step?.title ?: "Injecting…", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = accent.neon)
                    Spacer(Modifier.height(4.dp))
                    CountUpText((progress * 100).toInt(), TacoType.tnum.copy(fontSize = 28.sp), TacoPalette.Cream)
                    Text("INJECTING", style = TacoType.label)
                }
                is LaunchState.Success -> {
                    TacoIcon(TacoIcon.Check, TacoPalette.Guac, 42.dp, 2.5f)
                    Spacer(Modifier.height(4.dp))
                    Text("SERVED", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TacoPalette.Guac)
                }
                is LaunchState.Failed -> {
                    TacoIcon(TacoIcon.Warn, TacoPalette.Salsa, 40.dp, 2.3f)
                    Spacer(Modifier.height(4.dp))
                    Text("BURNED", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TacoPalette.Salsa)
                }
                LaunchState.Idle -> {
                    TacoIcon(TacoIcon.Bolt, accent.neon, 38.dp, 2.3f)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (enabled) "HOLD TO INJECT" else "SHIZUKU REQUIRED",
                        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (enabled) TacoPalette.Cream else TacoPalette.Salsa,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(heatName(heat), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HEAT_COLORS[heat.coerceIn(0, 3)])
                }
            }
        }

        if (hold.value > 0.001f && idle) {
            Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                val strokeW = 4.dp.toPx()
                val rect = Rect(strokeW, strokeW, size.width - strokeW, size.height - strokeW)
                drawArc(
                    accent.neon, -90f, hold.value * 360f, false, rect.topLeft, rect.size,
                    style = Stroke(strokeW, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
fun MenuCarousel(
    games: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onLongLaunch: (String) -> Unit,
    onAddGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalTacoAccent.current
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(games, key = { pair -> pair.second }) { (name, id) ->
            Column(
                Modifier.width(150.dp)
                    .clip(TacoShapes.menuCard)
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, Color(0xFF15100B))))
                    .border(1.dp, TacoPalette.Outline, TacoShapes.menuCard)
                    .combinedClickable(
                        onClick = { onPick(name) },
                        onLongClick = { onLongLaunch(id) },
                        onLongClickLabel = "Launch $name"
                    )
                    .padding(14.dp)
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(accent.neon, accent.deep))),
                    contentAlignment = Alignment.Center
                ) { TacoIcon(TacoIcon.Play, accent.onPrimary, 14.dp, 2.5f) }
                Spacer(Modifier.height(14.dp))
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = TacoPalette.Cream)
                Spacer(Modifier.height(2.dp))
                Text("#$id", style = TacoType.tnum.copy(fontSize = 12.sp), color = TacoPalette.Muted)
            }
        }
        item {
            Column(
                Modifier.width(120.dp).height(112.dp)
                    .clip(TacoShapes.menuCard)
                    .border(1.dp, TacoPalette.Outline, TacoShapes.menuCard)
                    .clickable(onClick = onAddGame),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TacoIcon(TacoIcon.Plus, TacoPalette.Muted, 22.dp)
                Spacer(Modifier.height(4.dp))
                Text("Add game", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TacoPalette.Muted)
            }
        }
    }
}

@Composable
fun SimplePickDialog(title: String, options: List<String>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertishDialog(
        title = title,
        body = null,
        onDismiss = onDismiss,
        content = {
            options.forEachIndexed { i, o ->
                Text(
                    o,
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(i) }.padding(horizontal = 8.dp, vertical = 12.dp),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TacoPalette.Cream
                )
            }
        }
    )
}

@Composable
fun HomeScreen(state: TacoState, onNavigate: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalTacoAccent.current
    val context = LocalContext.current
    var showStatusSheet by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var showAllowlistDialog by remember { mutableStateOf(false) }

    val merged = state.mergedFlags()
    val allowed = merged.keys.count { state.isAllowed(it) }
    val heat = heatLevel(merged.size)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 16.dp)
    ) {
        Appear(0) {
            StatusChipsRow(
                statuses = listOf(
                    LedStatus("Shizuku", state.shizuku.short, state.shizuku.color),
                    LedStatus("Root", if (state.rootAvailable == true) "ok" else "opt", TacoPalette.Muted, optional = true),
                    LedStatus("Roblox", state.robloxVersion ?: "installed", if (state.robloxVersion == null) TacoPalette.Tortilla else TacoPalette.Guac)
                ),
                onClick = { showStatusSheet = true }
            )
        }

        Spacer(Modifier.height(6.dp))

        Appear(1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(0.75f)) {
                    ComalDial(
                        heat = heat,
                        launch = state.launchState,
                        enabled = state.shizuku.ok || state.useRoot,
                        onTrigger = {
                            if (state.hapticsOn) hapticCrunch(context)
                            state.runLaunch()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SizzleParticles(
                        active = state.launchState is LaunchState.Running,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                ModeSelector(
                    selected = state.mode.ordinal,
                    onSelect = { state.mode = LaunchMode.entries[it] },
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }
        }

        if (state.launchState !is LaunchState.Idle) {
            Spacer(Modifier.height(16.dp))
            Appear(2) {
                PipelineTimeline(
                    launch = state.launchState,
                    onCancel = { state.cancelLaunch() },
                    onRetry = { state.retryLaunch() }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Appear(3) {
            OrderTicket(cardCorner = state.cardCornerRadius, glassOpacity = state.glassOpacity) {
                Text("THE ORDER (Injection Status)", style = TacoType.label, color = accent.neon)
                Spacer(Modifier.height(4.dp))
                DashedDivider()
                TicketLine("Active Profile", state.activeLayerName(), onClick = { onNavigate("profiles") })
                TicketLine("Flags Injected", "${merged.size} flags", onClick = { onNavigate("flags") })
                TicketLine("Active Font", state.activeCustomFontName ?: "Default Roblox Font", onClick = { onNavigate("fonts") })
                TicketLine(
                    "Allowlist",
                    "$allowed / ${merged.size} allowed",
                    valueColor = TacoPalette.Guac,
                    onClick = { showAllowlistDialog = true }
                )
                TicketLine("Target", state.targetLabel(), onClick = { showTargetDialog = true })
                DashedDivider()
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("tacostrap v$APP_VERSION", style = TacoType.label)
                    if (state.launchState is LaunchState.Success) {
                        Text("INJECTED ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Guac)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Appear(4) {
            Column {
                SectionLabel("The Menu")
                Spacer(Modifier.height(8.dp))
                if (state.games.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(TacoShapes.menuCard)
                            .border(1.dp, TacoPalette.Outline, TacoShapes.menuCard)
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TacoIcon(TacoIcon.Gamepad, TacoPalette.Faint, 24.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("No pinned games", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Muted)
                        Spacer(Modifier.height(8.dp))
                        TacoButton("Pin Game", { onNavigate("games") }, kind = ButtonKind.Ghost, icon = TacoIcon.Plus, height = 36.dp)
                    }
                } else {
                    MenuCarousel(
                        games = state.games.entries.map { it.key to it.value.placeId },
                        onPick = { name -> state.targetGame = name; state.notify("Target: $name") },
                        onLongLaunch = { id ->
                            if (state.hapticsOn) hapticTick(context)
                            state.launchGameById(id)
                        },
                        onAddGame = { onNavigate("games") }
                    )
                }
            }
        }
    }

    if (showStatusSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStatusSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = cs.surfaceVariant,
            contentColor = TacoPalette.Cream
        ) {
            StatusSheetContent(
                statuses = listOf(
                    Triple("Shizuku", state.shizuku.long, state.shizuku.color),
                    Triple("Root", if (state.rootAvailable == true) "available (optional)" else "not found", TacoPalette.Muted),
                    Triple("Roblox", state.robloxVersion ?: "installed", TacoPalette.Guac)
                ),
                fixSteps = listOf(
                    "Shizuku" to "1. Start Shizuku service.\n2. Tap Grant below.\n3. Verified binder connection.",
                    "Root" to "Optional superuser access for direct file injection.",
                    "Roblox" to "Configured target package: ${state.pkg}"
                ),
                onGrantShizuku = { state.requestShizukuPermission() },
                onOpenShizukuGuide = { showStatusSheet = false; onNavigate("settings") },
                onDismiss = { showStatusSheet = false }
            )
        }
    }

    if (showTargetDialog) {
        val names = state.games.keys.sorted()
        SimplePickDialog(
            title = "Launch Target",
            options = listOf("Home (Roblox App)") + names,
            onPick = { i ->
                state.targetGame = if (i == 0) null else names[i - 1]
                showTargetDialog = false
            },
            onDismiss = { showTargetDialog = false }
        )
    }
    if (showAllowlistDialog) {
        AlertishDialog(
            title = "Allowlist Status",
            body = "${allowed} of ${merged.size} FastFlags are compatible with Roblox Android.",
            onDismiss = { showAllowlistDialog = false }
        )
    }
}

/* ======================================================================
 *  5. FLAGS SCREEN
 * ====================================================================== */

sealed class FS(val title: String, val sub: String) {
    class Toggle(title: String, sub: String, val sets: Map<String, String>) : FS(title, sub)
    class Choice(title: String, sub: String, val opts: List<Opt>) : FS(title, sub)
    class Num(title: String, sub: String, val key: String) : FS(title, sub)
}

data class Opt(val label: String, val sets: Map<String, String>)

private fun texSet(n: String) = mapOf("DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to n)

private const val FPS_FLAG = "DFIntTaskSchedulerTargetFps"

private val FS_TEXTURE = FS.Choice("Texture Quality", "Sharper textures use more RAM",
    listOf(Opt("Auto", emptyMap()), Opt("Lowest", texSet("0")), Opt("Low", texSet("1")),
        Opt("Med", texSet("2")), Opt("High", texSet("3"))))
private val FS_AA = FS.Choice("Anti-Aliasing", "Smooths jagged edges",
    listOf(Opt("Auto", emptyMap()), Opt("Off", mapOf("FIntDebugForceMSAASamples" to "1")),
        Opt("2x", mapOf("FIntDebugForceMSAASamples" to "2")), Opt("4x", mapOf("FIntDebugForceMSAASamples" to "4")),
        Opt("8x", mapOf("FIntDebugForceMSAASamples" to "8"))))
private val FS_RENDER = FS.Choice("Graphics Engine", "Renderer API",
    listOf(Opt("Auto", emptyMap()), Opt("Vulkan", mapOf("FFlagDebugGraphicsPreferVulkan" to "True")),
        Opt("OpenGL", mapOf("FFlagDebugGraphicsPreferOpenGL" to "True"))))
private val FS_SHADOWS = FS.Toggle("Disable Shadows", "Turns off shadows for performance", mapOf("FIntRenderShadowIntensity" to "0"))

private val FS_DESYNC = FS.Toggle(
    title = "Network Desync / Physics Offset",
    sub = "Modifies physics sender rate and packet interval",
    sets = mapOf(
        "DFIntPhysicsSenderMaxBandwidthBps" to "100000",
        "DFIntS2PhysicsSenderRate" to "1",
        "DFIntPhysicsSenderRate" to "1",
        "DFIntMinimalIntervalBetweenPhysicsSendMS" to "1",
        "DFIntAnimationPacketDelayMs" to "250"
    )
)

private val FS_FPS = FS.Choice("Frame Rate Limit", "Target FPS cap",
    listOf(Opt("Default", emptyMap())) + listOf(30, 60, 90, 120, 144, 240).map { Opt("$it FPS", mapOf(FPS_FLAG to "$it")) })
private val FS_FPSCOUNT = FS.Toggle("FPS Counter", "Display FPS in corner", mapOf("FFlagDebugDisplayFPS" to "True"))

val SECTIONS: List<Pair<String, List<FS>>> = listOf(
    "Graphics" to listOf(FS_TEXTURE, FS_AA, FS_RENDER, FS_SHADOWS),
    "Performance" to listOf(FS_FPS, FS_FPSCOUNT),
    "Desync & Network" to listOf(FS_DESYNC)
)

internal fun fsKeys(fs: FS): Set<String> = when (fs) {
    is FS.Toggle -> fs.sets.keys
    is FS.Choice -> fs.opts.flatMap { it.sets.keys }.toSet()
    is FS.Num -> setOf(fs.key)
}

private fun FS.matches(q: String): Boolean =
    q.isBlank() || title.contains(q, true) || sub.contains(q, true) || fsKeys(this).any { it.contains(q, true) }

@Composable
fun FlagsScreen(state: TacoState) {
    val accent = LocalTacoAccent.current
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var editorKey by remember { mutableStateOf<String?>(null) }
    var jsonSheet by remember { mutableStateOf(false) }

    val draft = state.draftFlags
    val categories = listOf("All") + SECTIONS.map { it.first } + "Custom"
    val pending = state.pendingCount()

    val managedKeys = remember { SECTIONS.flatMap { (_, l) -> l.flatMap { fsKeys(it) } }.toSet() }
    val customKeys = draft.keys
        .filter { it !in managedKeys }
        .filter { it.contains(search, ignoreCase = true) && (category == "All" || category == "Custom") }
        .sorted()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        stickyHeader {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                if (pending > 0) {
                    Row(
                        Modifier.fillMaxWidth().clip(TacoShapes.control)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, accent.neon.copy(0.5f), TacoShapes.control)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CountUpText(pending, TacoType.tnum.copy(fontSize = 15.sp), accent.neon)
                        Spacer(Modifier.width(8.dp))
                        Text("pending changes", fontSize = 12.sp, color = TacoPalette.Muted, modifier = Modifier.weight(1f))
                        TacoButton("Discard", { state.discardDraft() }, kind = ButtonKind.Quiet, height = 32.dp)
                        Spacer(Modifier.width(6.dp))
                        TacoButton("Apply", { state.applyDraft() }, kind = ButtonKind.Primary, icon = TacoIcon.Bolt, height = 32.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Search ${draft.size} flags...", color = TacoPalette.Faint) },
                    singleLine = true,
                    leadingIcon = { TacoIcon(TacoIcon.Search, TacoPalette.Muted, 18.dp) },
                    colors = tacoFieldColors(),
                    shape = TacoShapes.control,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c -> CategoryChip(c, category == c) { category = c } }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        if (category == "All" || SECTIONS.any { it.first == category }) {
            val visible = if (category == "All") SECTIONS else SECTIONS.filter { it.first == category }
            visible.forEach { (title, list) ->
                val rows = list.filter { it.matches(search) }
                if (rows.isNotEmpty()) {
                    item(key = "sec_$title") { SectionLabel(title) }
                    items(rows, key = { fs -> "fs_${fs.title}" }) { fs -> FlagSettingRow(fs, draft, state) }
                }
            }
        }

        if (category == "All" || category == "Custom") {
            item(key = "sec_custom") { SectionLabel("Custom Flags") }
            if (customKeys.isEmpty()) {
                item(key = "custom_empty") {
                    EmptyState(TacoIcon.Flag, "No custom flags", "Tap Add to create a custom FastFlag.") {
                        TacoButton("Add Flag", { editorKey = "" }, kind = ButtonKind.Ghost, icon = TacoIcon.Plus, height = 38.dp)
                    }
                }
            }
            items(customKeys, key = { key -> "c$key" }) { key ->
                CustomFlagRow(
                    name = key, value = draft[key] ?: "",
                    allowed = state.isAllowed(key),
                    onEdit = { editorKey = key },
                    onToggle = { b -> draft[key] = if (b) "True" else "False" },
                    onDelete = { draft.remove(key); state.notify("Flag removed.", "flag") }
                )
            }
        }

        item(key = "actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TacoButton("Add", { editorKey = "" }, Modifier.weight(1f), ButtonKind.Ghost, TacoIcon.Plus, 40.dp)
                TacoButton("JSON", { jsonSheet = true }, Modifier.weight(1f), ButtonKind.Quiet, TacoIcon.Doc, 40.dp)
                TacoButton("Clear All", { draft.clear(); state.notify("Flags cleared.", "flag") }, Modifier.weight(1f), ButtonKind.Danger, TacoIcon.Trash, 40.dp)
            }
        }
    }

    editorKey?.let { key ->
        val isNew = key.isEmpty()
        var name by remember(key) { mutableStateOf(key) }
        var value by remember(key) { mutableStateOf(if (isNew) "" else draft[key] ?: "") }
        ModalBottomSheet(
            onDismissRequest = { editorKey = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = TacoPalette.Cream
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(if (isNew) "Add FastFlag" else "Edit FastFlag", style = TacoType.display)
                Spacer(Modifier.height(12.dp))
                if (isNew) {
                    OutlinedTextField(
                        name, { name = it.trim() }, label = { Text("Flag Name") }, singleLine = true,
                        colors = tacoFieldColors(), shape = TacoShapes.control, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value, { value = it }, label = { Text("Value") }, singleLine = true,
                    colors = tacoFieldColors(), shape = TacoShapes.control, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TacoButton("Save", {
                        if (name.isNotBlank()) {
                            if (!isNew && name != key) draft.remove(key)
                            draft[name] = value.trim()
                            state.notify("Flag saved.", "flag")
                        }
                        editorKey = null
                    }, Modifier.weight(1f), ButtonKind.Primary, TacoIcon.Check, 44.dp)
                }
            }
        }
    }

    if (jsonSheet) {
        var text by remember { mutableStateOf(toJson(state.draftFlags, 2)) }
        AlertDialog(
            onDismissRequest = { jsonSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            title = { Text("JSON Import / Export", style = TacoType.display, fontSize = 18.sp) },
            text = {
                OutlinedTextField(
                    text, { text = it }, minLines = 6, maxLines = 10,
                    textStyle = TacoType.mono.copy(fontSize = 12.sp),
                    colors = tacoFieldColors(), shape = TacoShapes.control,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TacoButton("Import JSON", {
                    runCatching {
                        val m = parseFlags(text)
                        state.draftFlags.clear(); state.draftFlags.putAll(m)
                        state.notify("Loaded ${m.size} flags.")
                    }
                    jsonSheet = false
                }, kind = ButtonKind.Primary, height = 40.dp)
            },
            dismissButton = { TacoButton("Close", { jsonSheet = false }, kind = ButtonKind.Quiet, height = 40.dp) }
        )
    }
}

@Composable
private fun FlagSettingRow(fs: FS, draft: SnapshotStateMap<String, String>, state: TacoState) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(state.cardCornerRadius.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = state.glassOpacity))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(state.cardCornerRadius.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fs.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Cream)
                Text(fs.sub, fontSize = 11.sp, color = TacoPalette.Muted, lineHeight = 15.sp)
            }
            if (fs is FS.Toggle) {
                val on = fs.sets.all { (k, v) -> draft[k] == v }
                TacoSwitch(on, { want ->
                    if (want) draft.putAll(fs.sets) else fs.sets.keys.forEach { draft.remove(it) }
                })
            }
        }
        if (fs is FS.Choice) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val managed = fs.opts.flatMap { it.sets.keys }.toSet()
                val cur = fs.opts.indexOfFirst { o ->
                    if (o.sets.isEmpty()) managed.none { draft.containsKey(it) }
                    else o.sets.all { (k, v) -> draft[k] == v }
                }
                fs.opts.forEachIndexed { i, o ->
                    CategoryChip(o.label, i == cur) {
                        managed.forEach { draft.remove(it) }
                        draft.putAll(o.sets)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomFlagRow(
    name: String, value: String, allowed: Boolean?,
    onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit
) {
    val accent = LocalTacoAccent.current
    val isBool = value.equals("true", true) || value.equals("false", true)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = TacoType.mono.copy(fontSize = 12.sp), color = TacoPalette.Cream)
                Spacer(Modifier.height(2.dp))
                AllowlistBadge(allowed)
            }
            if (isBool) {
                TacoSwitch(value.equals("true", true), onToggle)
            } else {
                Text(value, style = TacoType.tnum.copy(fontSize = 13.sp), color = accent.neon, maxLines = 1)
            }
        }
    }
}

/* ======================================================================
 *  6. CUSTOM FONT SCREEN (NEW TAB)
 * ====================================================================== */

data class FontItem(val id: String, val name: String, val filePath: String)

@Composable
fun CustomFontScreen(state: TacoState) {
    val context = LocalContext.current
    val accent = LocalTacoAccent.current

    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = "font_${System.currentTimeMillis()}.ttf"
            val file = File(context.filesDir, name)
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                if (file.length() in 1..(15 * 1024 * 1024)) {
                    state.addCustomFont(FontItem(UUID.randomUUID().toString(), uri.lastPathSegment ?: name, file.absolutePath))
                    state.notify("Font added successfully.")
                } else {
                    file.delete()
                    state.notify("Failed: Font file invalid or exceeds 15MB.")
                }
            }.onFailure {
                state.notify("Failed reading font file.")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text("Custom Font Library", style = TacoType.displayBig)
        Text("Upload custom .ttf or .otf fonts to override Roblox typography rootless.", fontSize = 13.sp, color = TacoPalette.Muted)

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TacoButton("Upload Font", { fontPicker.launch("*/*") }, Modifier.weight(1f), ButtonKind.Primary, TacoIcon.Font, 44.dp)
            TacoButton("Restore Default", {
                state.scope.launch {
                    val (_, msg) = DefaultBootstrapper.restoreDefaultFont(state.pkg, state.useRoot)
                    state.activeCustomFontPath = null
                    state.persist()
                    state.notify(msg)
                }
            }, Modifier.weight(1f), ButtonKind.Ghost, TacoIcon.Refresh, 44.dp)
        }

        Spacer(Modifier.height(16.dp))

        if (state.customFonts.isEmpty()) {
            EmptyState(TacoIcon.Font, "No custom fonts uploaded", "Upload a .ttf/.otf file from your phone storage.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.customFonts, key = { item -> item.id }) { item ->
                    val isActive = state.activeCustomFontPath == item.filePath
                    Column(
                        Modifier.fillMaxWidth().clip(TacoShapes.card)
                            .background(if (isActive) Color(0xFF2E2215) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (isActive) accent.neon else TacoPalette.Outline, TacoShapes.card)
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconCircle(TacoIcon.Font, if (isActive) accent.neon else TacoPalette.Muted, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Cream)
                                Text("Preview: The quick brown fox jumps over the lazy dog", fontSize = 12.sp, color = TacoPalette.Muted)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isActive) {
                                TacoButton("Use Font", {
                                    state.activeCustomFontPath = item.filePath
                                    state.persist()
                                    state.notify("Active font updated.")
                                }, Modifier.weight(1f), ButtonKind.Ghost, height = 36.dp)
                            } else {
                                TacoButton("Active", {}, Modifier.weight(1f), ButtonKind.Primary, icon = TacoIcon.Check, height = 36.dp)
                            }
                            TacoButton("Delete", {
                                state.removeCustomFont(item)
                                state.notify("Font removed.")
                            }, Modifier.weight(1f), ButtonKind.Danger, icon = TacoIcon.Trash, height = 36.dp)
                        }
                    }
                }
            }
        }
    }
}

/* ======================================================================
 *  7. PROFILES & SETTINGS
 * ====================================================================== */

class FlagLayer(val id: String, name: String, enabled: Boolean = true, flags: Map<String, String> = emptyMap()) {
    var name by mutableStateOf(name)
    var enabled by mutableStateOf(enabled)
    val flags = mutableStateMapOf<String, String>().apply { putAll(flags) }
}

@Composable
fun ProfilesScreen(state: TacoState) {
    val accent = LocalTacoAccent.current
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("Profiles", style = TacoType.displayBig)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                newName, { newName = it }, placeholder = { Text("Profile Name") },
                singleLine = true, colors = tacoFieldColors(), shape = TacoShapes.control, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TacoButton("Create", {
                if (newName.isNotBlank()) {
                    state.saveLayerFromCurrent(newName.trim())
                    newName = ""
                }
            }, kind = ButtonKind.Primary, icon = TacoIcon.Plus, height = 48.dp)
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.layers, key = { layer -> layer.id }) { layer ->
                Column(
                    Modifier.fillMaxWidth().clip(TacoShapes.card)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (layer.enabled) accent.neon else TacoPalette.Outline, TacoShapes.card)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(layer.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TacoSwitch(layer.enabled, { state.setLayerEnabled(layer.id, it) }, enabled = layer.id != "base")
                    }
                }
            }
        }
    }
}

@Composable
fun GamesScreen(state: TacoState) {
    Text("Games tab loaded.", modifier = Modifier.padding(16.dp))
}

@Composable
fun SettingsScreen(state: TacoState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = TacoType.displayBig)

        SectionLabel("Shizuku Status")
        Column(
            Modifier.fillMaxWidth().clip(TacoShapes.card)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Text("Shizuku Status: ${state.shizuku.long}", color = state.shizuku.color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TacoButton("Request Permission", { state.requestShizukuPermission() }, kind = ButtonKind.Primary, height = 40.dp)
        }

        SectionLabel("Roblox Configuration")
        SettingValue("Roblox Package", state.pkg, { state.editPkg() })
        SettingValue("Flag File Path", state.path, { state.cyclePath() })
    }
}

@Composable
private fun SettingValue(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 12.sp, color = LocalTacoAccent.current.neon)
    }
}

/* ======================================================================
 *  8. APP STATE & PERSISTENCE
 * ====================================================================== */

data class PinnedGame(val placeId: String)

var appContext: Context? = null
private fun clipMgr(ctx: Context) = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

class TacoState(
    private val prefs: SharedPreferences,
    private val engine: Bootstrapper,
    private val allowlist: AllowlistProvider,
    val scope: CoroutineScope,
    val snackbar: SnackbarHostState
) {
    val draftFlags = mutableStateMapOf<String, String>()
    val layers = mutableStateListOf<FlagLayer>()
    val customFonts = mutableStateListOf<FontItem>()
    val games = mutableStateMapOf<String, PinnedGame>()
    private var savedFlags by mutableStateOf<Map<String, String>>(emptyMap())

    var path by mutableStateOf(prefs.getString("path2", DEFAULT_PATH) ?: DEFAULT_PATH)
    var pkg by mutableStateOf(prefs.getString("pkg", DEFAULT_PKG) ?: DEFAULT_PKG)
    var useRoot by mutableStateOf(prefs.getBoolean("root", false))
    var applyOnSave by mutableStateOf(prefs.getBoolean("applyOnSave", true))
    var restart by mutableStateOf(prefs.getBoolean("restart", true))
    var fpsCap by mutableStateOf(prefs.getBoolean("fps", false))
    var accentIdx by mutableStateOf(prefs.getInt("accent", 0).coerceIn(0, TACO_ACCENTS.lastIndex))
    var fontIdx by mutableStateOf(prefs.getInt("fontIdx", 0))
    var amoled by mutableStateOf(prefs.getBoolean("trueBlack", false))
    var hapticsOn by mutableStateOf(prefs.getBoolean("haptics", true))
    var reduceMotion by mutableStateOf(prefs.getBoolean("reduceMotion", false))
    var bgImageUri by mutableStateOf(prefs.getString("bgImageUri", "") ?: "")
    var activeCustomFontPath by mutableStateOf(prefs.getString("activeFontPath", null))

    var glassOpacity by mutableStateOf(prefs.getFloat("glassOpacity", 0.88f))
    var cardCornerRadius by mutableStateOf(prefs.getInt("cardCornerRadius", 20))

    var shizuku by mutableStateOf<ShizukuState>(ShizukuState.NotRunning)
    var rootAvailable by mutableStateOf<Boolean?>(null)
    var robloxVersion by mutableStateOf<String?>(null)

    var mode by mutableStateOf(LaunchMode.SAVE_LAUNCH)
    var launchState by mutableStateOf<LaunchState>(LaunchState.Idle)
    var targetGame by mutableStateOf<String?>(null)
    private var launchJob: Job? = null

    val activeCustomFontName: String?
        get() = customFonts.find { it.filePath == activeCustomFontPath }?.name

    init {
        runCatching { draftFlags.putAll(parseFlags(prefs.getString("flags", "{}") ?: "{}")) }
        savedFlags = draftFlags.toMap()
        layers += FlagLayer("base", "Base flags", true, draftFlags.toMap())
    }

    fun mergedFlags(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        layers.forEach { if (it.enabled) out.putAll(it.flags) }
        return out
    }

    fun activeLayerName(): String = layers.lastOrNull { it.enabled && it.id != "base" }?.name ?: "Base flags"

    fun isAllowed(key: String): Boolean = allowlist.isAllowed(key)

    fun pendingCount(): Int {
        val keys = (savedFlags.keys + draftFlags.keys).toSet()
        return keys.count { savedFlags[it] != draftFlags[it] }
    }

    fun discardDraft() {
        draftFlags.clear(); draftFlags.putAll(savedFlags)
    }

    fun applyDraft() {
        savedFlags = draftFlags.toMap()
        persist()
    }

    fun addCustomFont(item: FontItem) {
        customFonts.add(item)
        if (activeCustomFontPath == null) activeCustomFontPath = item.filePath
        persist()
    }

    fun removeCustomFont(item: FontItem) {
        customFonts.remove(item)
        if (activeCustomFontPath == item.filePath) activeCustomFontPath = null
        File(item.filePath).delete()
        persist()
    }

    fun saveLayerFromCurrent(name: String) {
        layers += FlagLayer(UUID.randomUUID().toString(), name, true, draftFlags.toMap())
        persist()
    }

    fun setLayerEnabled(id: String, enabled: Boolean) {
        if (id == "base") return
        layers.firstOrNull { it.id == id }?.enabled = enabled
        persist()
    }

    fun targetLabel(): String = targetGame ?: "Home (Roblox App)"

    fun launchGameById(placeId: String) {
        scope.launch {
            engine.launchGame(pkg, placeId, useRoot, restart)
        }
    }

    fun runLaunch() {
        if (launchState is LaunchState.Running) return
        launchJob?.cancel()
        val merged = mergedFlags()
        val steps = listOf(
            LaunchStep("Checking Shizuku"),
            LaunchStep("Injecting FastFlags"),
            LaunchStep("Applying Custom Font"),
            LaunchStep("Verifying Files"),
            LaunchStep("Launching Roblox")
        )
        launchState = LaunchState.Running(steps, 0)

        launchJob = scope.launch job@{
            try {
                if (!engine.shizukuState().ok && !useRoot) {
                    launchState = LaunchState.Failed("Shizuku binder connection required.", steps, 0)
                    return@job
                }

                // Step 1: Flags
                launchState = LaunchState.Running(steps, 1)
                engine.applyFlags(toJson(merged), path, pkg, fpsCap, useRoot)

                // Step 2: Font
                launchState = LaunchState.Running(steps, 2)
                engine.applyCustomFont(activeCustomFontPath, pkg, useRoot)

                // Step 3: Verify
                launchState = LaunchState.Running(steps, 3)
                engine.verify(toJson(merged), path, useRoot)

                // Step 4: Launch
                launchState = LaunchState.Running(steps, 4)
                val msg = engine.launchRoblox(pkg, useRoot, restart)
                launchState = LaunchState.Success(msg, steps)
            } catch (e: Throwable) {
                launchState = LaunchState.Failed(e.message ?: "Launch error", steps, 0)
            }
        }
    }

    fun cancelLaunch() { launchJob?.cancel(); launchState = LaunchState.Idle }
    fun retryLaunch() { cancelLaunch(); runLaunch() }

    fun requestShizukuPermission() {
        runCatching { rikka.shizuku.Shizuku.requestPermission(1001) }
    }

    fun editPkg() {
        pkg = if (pkg == DEFAULT_PKG) "com.roblox.client.chrome" else DEFAULT_PKG
        persist()
    }

    fun cyclePath() {
        path = if (path == DEFAULT_PATH) ALT_PATH else DEFAULT_PATH
        persist()
    }

    fun notify(msg: String, kind: String = "other") {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun persist() {
        prefs.edit()
            .putString("flags", toJson(savedFlags))
            .putString("path2", path).putString("pkg", pkg)
            .putString("activeFontPath", activeCustomFontPath)
            .apply()
    }

    suspend fun refreshLoop() {
        rootAvailable = withContext(Dispatchers.IO) { engine.rootAvailable() }
        while (currentCoroutineContext().isActive) {
            shizuku = engine.shizukuState()
            if (robloxVersion == null && (shizuku.ok || useRoot)) {
                robloxVersion = engine.robloxVersion(pkg, useRoot)
            }
            delay(1500)
        }
    }
}

/* ======================================================================
 *  9. MAIN ACTIVITY & NAV ROOT
 * ====================================================================== */

private val TAB_ROUTES = listOf("home", "flags", "fonts", "profiles", "settings")
private val TAB_ICONS = listOf(
    TacoIcon.Home to "Home", TacoIcon.Flag to "Flags", TacoIcon.Font to "Fonts",
    TacoIcon.Layers to "Profiles", TacoIcon.Sliders to "Settings"
)

class MainActivity : ComponentActivity() {
    private val permListener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { rikka.shizuku.Shizuku.addRequestPermissionResultListener(permListener) }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { TacoRoot() }
    }

    override fun onDestroy() {
        runCatching { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(permListener) }
        super.onDestroy()
    }
}

@Composable
fun TacoRoot() {
    val context = LocalContext.current
    appContext = context.applicationContext
    val prefs = remember { context.getSharedPreferences("fm", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val state = remember { TacoState(prefs, DefaultBootstrapper, DefaultAllowlist, scope, snackbar) }

    LaunchedEffect(Unit) { state.refreshLoop() }

    TacoTheme(state.accentIdx, state.amoled, state.reduceMotion, state.fontIdx) {
        var currentRoute by rememberSaveable { mutableStateOf("home") }
        BackHandler(enabled = currentRoute != "home") { currentRoute = "home" }
        val cs = MaterialTheme.colorScheme
        val accent = LocalTacoAccent.current

        Box(Modifier.fillMaxSize().background(cs.background)) {
            Box(Modifier.fillMaxSize().tacoGrain()) {
                Crossfade(
                    targetState = currentRoute,
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 80.dp),
                    animationSpec = tween(200),
                    label = "nav"
                ) { r ->
                    when (r) {
                        "flags" -> FlagsScreen(state)
                        "fonts" -> CustomFontScreen(state)
                        "profiles" -> ProfilesScreen(state)
                        "settings" -> SettingsScreen(state)
                        else -> HomeScreen(state) { target -> currentRoute = target }
                    }
                }

                Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(CircleShape)
                            .background(cs.surfaceVariant.copy(alpha = 0.94f))
                            .border(1.dp, accent.neon.copy(0.35f), CircleShape)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TAB_ROUTES.forEachIndexed { i, route ->
                            val selected = currentRoute == route
                            val (icon, label) = TAB_ICONS[i]
                            val tint = if (selected) accent.neon else TacoPalette.Muted
                            Row(
                                Modifier.height(44.dp).clip(CircleShape)
                                    .clickable { currentRoute = route }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TacoIcon(icon, tint, 20.dp)
                                if (selected) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp)
                ) { d ->
                    Snackbar(
                        d, shape = RoundedCornerShape(16.dp),
                        containerColor = cs.surfaceVariant,
                        contentColor = cs.onSurface,
                        actionColor = accent.neon
                    )
                }
            }
        }
    }
}
