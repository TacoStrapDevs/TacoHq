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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/* ======================================================================
 *  1. ENGINE: JSON helpers + multi-path deployment (Shizuku / root)
 * ====================================================================== */

const val APP_VERSION = "5.0"
const val DEFAULT_PKG = "com.roblox.client"
const val DEFAULT_PATH = "/data/local/tmp/ClientAppSettings.json"
const val ALT_PATH = "/data/data/com.roblox.client/files/ClientSettings/ClientAppSettings.json"
const val EXE_PATH = "/data/data/com.roblox.client/files/exe/ClientSettings/ClientAppSettings.json"

/** Parses a flat JSON object into string->string. Booleans become "True"/"False". */
fun parseFlags(text: String): Map<String, String> {
    val o = JSONObject(text.ifBlank { "{}" })
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

/** 
 * Writes flags as JSON using native bool, int, long, and double/float types 
 * so Roblox parses them correctly without ignoring quotes.
 */
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

/* ---- Bootstrapper interface + implementation ---- */

interface Bootstrapper {
    fun shizukuState(): ShizukuState
    suspend fun applyFlags(json: String, path: String, pkg: String, fpsCap: Boolean, root: Boolean): String
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
            
            val paths = listOf(path, ALT_PATH, EXE_PATH).distinct()
            var success = false
            var lastOut = ""

            for (p in paths) {
                val dir = p.substringBeforeLast('/', "")
                val mk = if (dir.isNotEmpty()) "mkdir -p ${shq(dir)} && " else ""
                val cmd = "${mk}printf %s ${shq(b64)} | base64 -d > ${shq(p)} && chmod 644 ${shq(p)}"
                val (ok, out) = runCmd(cmd, root)
                if (ok) {
                    success = true
                } else {
                    lastOut = out
                }
            }

            if (!success) return@withContext "Failed: ${lastOut.ifBlank { "could not write flag file" }}"
            if (fpsCap) runCmd("cmd game set --mode 2 --downscale disable --fps 0 ${shq(pkg)}", root)
            "Applied ${parseFlags(json).size} flags successfully"
        }

    override suspend fun verify(json: String, path: String, root: Boolean): String =
        withContext(Dispatchers.IO) {
            val (ok, out) = runCmd("cat ${shq(path)}", root)
            if (!ok) return@withContext "Verify failed: cannot read flag file"
            val onDisk = runCatching { parseFlags(out) }.getOrNull()
                ?: return@withContext "Verify failed: file is not valid JSON"
            val want = parseFlags(json)
            val bad = want.count { onDisk[it.key] != it.value }
            if (bad == 0) "Verified: ${want.size} flags on disk match." else "Verify failed: $bad flags differ on disk"
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
    override fun isAllowed(key: String): Boolean = true
}

sealed class ShizukuState(val ok: Boolean, val short: String, val long: String, val color: Color) {
    data object Connected : ShizukuState(true, "connected", "Connected", TacoPalette.Guac)
    data object NeedsGrant : ShizukuState(false, "tap to grant", "Permission needed", TacoPalette.Tortilla)
    data object NotRunning : ShizukuState(false, "not running", "Not running", TacoPalette.Salsa)
    data object Unavailable : ShizukuState(false, "unavailable", "Unavailable", TacoPalette.Salsa)
}

/* ======================================================================
 *  2. THEME & PALETTE
 * ====================================================================== */

object TacoPalette {
    val Charcoal = Color(0xFF120D0A)
    val CharcoalDeep = Color(0xFF0C0906)
    val Surface = Color(0xFF1B140E)
    val SurfaceHigh = Color(0xFF251B12)
    val Tortilla = Color(0xFFF5B544)
    val TortillaDeep = Color(0xFFC98A2B)
    val Salsa = Color(0xFFF2572B)
    val Guac = Color(0xFF6BBF59)
    val Cream = Color(0xFFFFF1DC)
    val Muted = Color(0xFFCBB896)
    val Faint = Color(0xFF7E6F5C)
    val Outline = Color(0xFF3D2F21)
}

data class TacoAccent(val name: String, val primary: Color, val onPrimary: Color, val deep: Color)

val TACO_ACCENTS = listOf(
    TacoAccent("Tortilla", TacoPalette.Tortilla, TacoPalette.CharcoalDeep, TacoPalette.TortillaDeep),
    TacoAccent("Salsa", TacoPalette.Salsa, Color(0xFF2A0E06), Color(0xFFB53A16)),
    TacoAccent("Guac", TacoPalette.Guac, Color(0xFF0E220A), Color(0xFF4C9440)),
    TacoAccent("Horchata", Color(0xFFE8A87C), Color(0xFF2A1508), Color(0xFFB97A4E)),
)

val LocalTacoAccent = compositionLocalOf { TACO_ACCENTS[0] }
val LocalReduceMotion = compositionLocalOf { false }

object TacoType {
    val Display = FontFamily.SansSerif

    val displayBig = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp,
        letterSpacing = (-0.8).sp, color = TacoPalette.Cream
    )
    val display = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp,
        letterSpacing = (-0.3).sp, color = TacoPalette.Cream
    )
    val body = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TacoPalette.Cream
    )
    val label = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
        letterSpacing = 1.4.sp, color = TacoPalette.Muted
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp,
        color = TacoPalette.Cream
    )
    val tnum = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp,
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
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val card = RoundedCornerShape(18.dp)
    val control = RoundedCornerShape(14.dp)
    val pill = CircleShape
}

object TacoMotion {
    val softSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    val shimmer = tween<Float>(1100)
}

@Composable
fun TacoTheme(accentIdx: Int, amoled: Boolean, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val accent = TACO_ACCENTS[accentIdx.coerceIn(0, TACO_ACCENTS.lastIndex)]
    val base = if (amoled) TacoPalette.CharcoalDeep else TacoPalette.Charcoal
    val surface = if (amoled) Color(0xFF130E0A) else TacoPalette.Surface
    val scheme = darkColorScheme(
        primary = accent.primary, onPrimary = accent.onPrimary,
        primaryContainer = accent.deep, onPrimaryContainer = TacoPalette.Cream,
        secondary = accent.deep, onSecondary = TacoPalette.Cream,
        tertiary = TacoPalette.Guac, onTertiary = Color(0xFF0E220A),
        background = base, onBackground = TacoPalette.Cream,
        surface = base, onSurface = TacoPalette.Cream,
        surfaceVariant = surface, onSurfaceVariant = TacoPalette.Muted,
        error = TacoPalette.Salsa, onError = Color(0xFF2A0E06),
        outline = TacoPalette.Outline, outlineVariant = Color(0xFF2A2016)
    )
    CompositionLocalProvider(
        LocalTacoAccent provides accent,
        LocalReduceMotion provides reduceMotion,
        LocalContentColor provides TacoPalette.Cream
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private val grainTile: ImageBitmap by lazy {
    val ib = ImageBitmap(96, 96)
    val c = ComposeCanvas(ib)
    val rnd = java.util.Random(42)
    val paint = Paint().apply { color = Color.White.copy(alpha = 0.02f) }
    repeat(240) { c.drawCircle(Offset(rnd.nextFloat() * 96f, rnd.nextFloat() * 96f), 0.8f, paint) }
    ib
}

fun Modifier.tacoGrain(): Modifier = this.drawBehind {
    drawImage(
        image = grainTile,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(grainTile.width, grainTile.height),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = 0.5f
    )
}

/* ======================================================================
 *  3. COMPONENTS & ICONS
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
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 60), -1))
        } else @Suppress("DEPRECATION") {
            v.vibrate(70)
        }
    }
}

fun hapticTick(context: Context) {
    runCatching {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(12, 64))
        else @Suppress("DEPRECATION") v.vibrate(12)
    }
}

enum class TacoIcon {
    Home, Layers, Flag, Gamepad, Sliders, Play, Check, Close, Plus, Search, Copy, Trash,
    Grip, Globe, Doc, Bolt, Warn, Info, Star, Refresh, Save, Up, Down, Dot, Image
}

private fun DrawScope.pl(c: Color, st: Stroke, s: Float, vararg p: Float) {
    val path = Path()
    path.moveTo(p[0] * s, p[1] * s)
    var i = 2
    while (i < p.size) { path.lineTo(p[i] * s, p[i + 1] * s); i += 2 }
    drawPath(path, c, style = st)
}

@Composable
fun TacoIcon(kind: TacoIcon, tint: Color, size: Dp = 22.dp, stroke: Float = 1.9f) {
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
                drawCircle(tint, 1.1f * s, Offset(15.5f * s, 11.5f * s))
                drawCircle(tint, 1.1f * s, Offset(18f * s, 13.5f * s))
            }
            TacoIcon.Sliders -> {
                pl(tint, st, s, 4f, 7f, 20f, 7f); pl(tint, st, s, 4f, 12f, 20f, 12f); pl(tint, st, s, 4f, 17f, 20f, 17f)
                drawCircle(tint, 2f * s, Offset(9f * s, 7f * s), style = st)
                drawCircle(tint, 2f * s, Offset(15f * s, 12f * s), style = st)
                drawCircle(tint, 2f * s, Offset(7f * s, 17f * s), style = st)
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
                pl(tint, st, s, 10.5f, 10.5f, 10.5f, 16.5f); pl(tint, st, s, 13.5f, 10.5f, 13.5f, 16.5f)
            }
            TacoIcon.Grip -> repeat(3) { r ->
                repeat(2) { c2 ->
                    drawCircle(tint, 1.4f * s, Offset((9f + c2 * 6f) * s, (8f + r * 4f) * s))
                }
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
            TacoIcon.Dot -> drawCircle(tint, 2.2f * s, Offset(12f * s, 12f * s))
            TacoIcon.Image -> {
                drawRoundRect(tint, Offset(3f * s, 5f * s), Size(18f * s, 14f * s), CornerRadius(2f * s), style = st)
                drawCircle(tint, 2.5f * s, Offset(8f * s, 10f * s))
                pl(tint, st, s, 3f, 16f, 9f, 11f, 14f, 15f, 18f, 12f, 21f, 15f)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val accent = LocalTacoAccent.current
    Row(modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 4.dp, height = 14.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(accent.primary, accent.deep)))
        )
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = TacoType.label, color = accent.primary)
    }
}

@Composable
fun TacoSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val accent = LocalTacoAccent.current
    val reduce = LocalReduceMotion.current
    val x by animateDpAsState(
        if (checked) 25.dp else 4.dp,
        if (reduce) tween(0) else spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "swx"
    )
    Box(
        Modifier.width(52.dp).height(30.dp).clip(CircleShape)
            .background(if (enabled) TacoPalette.SurfaceHigh else TacoPalette.Surface)
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
            Modifier.offset(x = x, y = 3.dp).size(24.dp).clip(CircleShape)
                .background(if (checked) TacoPalette.Cream else TacoPalette.Faint)
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
        if (pressed) 0.95f else 1f,
        if (reduce) tween(0) else spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "btn"
    )
    val look: Triple<Brush, Color, Color?> = when (kind) {
        ButtonKind.Primary -> Triple(
            Brush.horizontalGradient(listOf(accent.primary, accent.deep)), accent.onPrimary, null
        )
        ButtonKind.Ghost -> Triple(
            Brush.horizontalGradient(listOf(accent.primary.copy(0.14f), accent.primary.copy(0.06f))),
            accent.primary, accent.primary.copy(0.35f)
        )
        ButtonKind.Quiet -> Triple(
            Brush.horizontalGradient(listOf(TacoPalette.SurfaceHigh, TacoPalette.Surface)),
            TacoPalette.Cream, TacoPalette.Outline
        )
        ButtonKind.Danger -> Triple(
            Brush.horizontalGradient(listOf(TacoPalette.Salsa.copy(0.18f), TacoPalette.Salsa.copy(0.08f))),
            TacoPalette.Salsa, TacoPalette.Salsa.copy(0.4f)
        )
    }
    val bg = look.first
    val fg = look.second
    val bd = look.third
    Row(
        modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(bg)
            .then(if (bd != null) Modifier.border(1.dp, bd, CircleShape) else Modifier)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = if (text.isEmpty()) 0.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            TacoIcon(icon, fg, 17.dp, 2.1f)
            if (text.isNotEmpty()) Spacer(Modifier.width(7.dp))
        }
        if (text.isNotEmpty()) Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun IconCircle(icon: TacoIcon, tint: Color, size: Dp = 38.dp, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { TacoIcon(icon, tint, size * 0.5f, 2f) }
}

@Composable
fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalTacoAccent.current
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "ca")
    Box(
        Modifier.clip(CircleShape)
            .border(1.dp, if (selected) Color.Transparent else TacoPalette.Outline, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier.matchParentSize().graphicsLayer { alpha = a }
                .background(Brush.horizontalGradient(listOf(accent.primary, accent.deep)))
        )
        Text(
            text,
            Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) accent.onPrimary else TacoPalette.Muted
        )
    }
}

@Composable
fun AllowlistBadge(allowed: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TacoIcon(TacoIcon.Check, TacoPalette.Guac, 13.dp, 2.6f)
        Spacer(Modifier.width(3.dp))
        Text("allowed", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TacoPalette.Guac)
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
        IconCircle(icon, LocalTacoAccent.current.primary, 54.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = TacoType.display, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
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
            .background(TacoPalette.Guac.copy(alpha = 0.14f))
            .border(1.dp, TacoPalette.Guac.copy(alpha = 0.4f), TacoShapes.control)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(TacoIcon.Check, TacoPalette.Guac, 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = TacoPalette.Guac, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    if (LocalReduceMotion.current) { content(); return }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 55L); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 6 }
    ) { content() }
}

data class LedStatus(val label: String, val value: String, val color: Color, val optional: Boolean = false)

@Composable
fun StatusStrip(statuses: List<LedStatus>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        statuses.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(7.dp).clip(CircleShape)
                        .background(if (s.optional && s.color == TacoPalette.Faint) Color.Transparent else s.color)
                        .border(1.dp, s.color, CircleShape)
                )
                Spacer(Modifier.width(5.dp))
                Text(s.label, style = TacoType.label)
                Spacer(Modifier.width(3.dp))
                Text(
                    s.value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (s.optional) TacoPalette.Muted else TacoPalette.Cream
                )
            }
            if (i < statuses.lastIndex) {
                Text("·", color = TacoPalette.Faint, modifier = Modifier.padding(horizontal = 8.dp))
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
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Kitchen status", style = TacoType.display)
        Spacer(Modifier.height(14.dp))
        statuses.forEach { (name, state, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(name, style = TacoType.body, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(state, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(12.dp))
        fixSteps.forEach { (name, steps) ->
            Column(
                Modifier.fillMaxWidth().clip(TacoShapes.control)
                    .background(TacoPalette.Surface).border(1.dp, TacoPalette.Outline, TacoShapes.control)
                    .padding(12.dp)
            ) {
                Text(name.uppercase(), style = TacoType.label, color = accent.primary)
                Spacer(Modifier.height(4.dp))
                Text(steps, fontSize = 13.sp, color = TacoPalette.Muted, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TacoButton("Grant Shizuku", onGrantShizuku, Modifier.weight(1f), ButtonKind.Ghost, TacoIcon.Bolt, 44.dp)
            TacoButton("Setup guide", onOpenShizukuGuide, Modifier.weight(1f), ButtonKind.Quiet, TacoIcon.Globe, 44.dp)
        }
        Spacer(Modifier.height(8.dp))
        TacoButton("Close", onDismiss, Modifier.fillMaxWidth(), ButtonKind.Quiet, height = 44.dp)
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
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                Text(
                    modes[page],
                    fontSize = if (isSel) 19.sp else 15.sp,
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
                        .offset(x = segW * fracAnim.coerceIn(0f, modes.size - 1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.width(28.dp).height(3.dp).clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(accent.primary, accent.deep)))
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
                Spacer(Modifier.height(10.dp))
                SuccessBanner(launch.message)
            }
            is LaunchState.Failed -> {
                launch.steps.forEachIndexed { i, step -> StepRow(step, i == launch.failedStep, i == launch.steps.lastIndex) }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(TacoShapes.control)
                        .background(TacoPalette.Salsa.copy(alpha = 0.12f))
                        .border(1.dp, TacoPalette.Salsa.copy(alpha = 0.4f), TacoShapes.control)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconCircle(TacoIcon.Warn, TacoPalette.Salsa, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(launch.message, color = TacoPalette.Salsa, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                }
            }
            LaunchState.Idle -> {}
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (launch is LaunchState.Running || launch is LaunchState.Failed) {
                TacoButton("Retry", onRetry, Modifier.weight(1f), ButtonKind.Ghost, TacoIcon.Refresh, 42.dp)
            }
            TacoButton(
                if (launch is LaunchState.Success) "Done" else "Cancel",
                onCancel, Modifier.weight(1f), ButtonKind.Quiet, TacoIcon.Close, 42.dp
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
                    IconCircle(TacoIcon.Dot, accent.primary.copy(alpha = if (LocalReduceMotion.current) 1f else a), 24.dp)
                }
                StepStatus.Pending -> IconCircle(TacoIcon.Dot, TacoPalette.Faint, 24.dp)
            }
            if (!isLast) {
                Box(
                    Modifier.width(2.dp).height(26.dp)
                        .background(
                            if (step.status == StepStatus.Done) TacoPalette.Guac.copy(0.5f)
                            else TacoPalette.Outline
                        )
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(bottom = if (isLast) 0.dp else 12.dp)) {
            Text(
                step.title,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = when (step.status) {
                    StepStatus.Done -> TacoPalette.Cream
                    StepStatus.Failed -> TacoPalette.Salsa
                    StepStatus.Running -> accent.primary
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
fun OrderTicket(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(cs.surfaceVariant.copy(alpha = 0.9f))
                .border(1.dp, cs.outlineVariant, shape)
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
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(18) { Box(Modifier.size(9.dp).clip(CircleShape).background(pageBg)) }
    }
}

@Composable
fun DashedDivider() {
    val c = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(21.dp)) {
        drawLine(
            c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
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
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), style = TacoType.label, modifier = Modifier.width(92.dp))
        Column(Modifier.weight(1f)) {
            Text(value, style = TacoType.mono.copy(fontSize = 13.sp, color = valueColor), lineHeight = 17.sp)
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
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = TacoType.display, fontSize = 20.sp) },
        text = {
            Column {
                if (body != null) Text(body, fontSize = 14.sp, color = TacoPalette.Muted, lineHeight = 20.sp)
                content?.invoke(this)
            }
        },
        confirmButton = { TacoButton("Close", onDismiss, kind = ButtonKind.Quiet, height = 40.dp) }
    )
}

@Composable
internal fun tacoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TacoPalette.Cream, unfocusedTextColor = TacoPalette.Cream,
    focusedBorderColor = LocalTacoAccent.current.primary, unfocusedBorderColor = TacoPalette.Outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)

/* ======================================================================
 *  4. HOME
 * ====================================================================== */

fun heatLevel(flagCount: Int): Int = when {
    flagCount <= 4 -> 0
    flagCount <= 12 -> 1
    flagCount <= 22 -> 2
    else -> 3
}

fun heatName(level: Int): String = listOf("Mild", "Medium", "Hot", "Extra hot")[level.coerceIn(0, 3)]

private val HEAT_COLORS = listOf(
    Color(0xFFF5B544), Color(0xFFF59E3D), Color(0xFFF2572B), Color(0xFFFF3D2E)
)

@Composable
fun SizzleParticles(active: Boolean, modifier: Modifier = Modifier) {
    if (!active || LocalReduceMotion.current) return
    val particles = remember {
        List(24) { Triple(Random.nextFloat(), 0.5f + Random.nextFloat(), Random.nextFloat() * 6.28f) }
    }
    val t = rememberInfiniteTransition(label = "siz")
    val f by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart), label = "sf")
    Canvas(modifier) {
        val cx = size.width / 2
        val top = size.height * 0.42f
        particles.forEachIndexed { i, (off, spd, ph) ->
            val p = (off + f * spd) % 1f
            val y = top - p * size.height * 0.34f
            val x = cx + sin(p * 7f + ph) * size.width * 0.16f * p
            val alpha = (1f - p) * 0.45f
            val r = 2f + 4f * p
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
            val res = hold.animateTo(1f, tween(850, easing = LinearEasing))
            if (res.endReason == AnimationEndReason.Finished) trigger()
        } else {
            hold.animateTo(0f, if (reduce) tween(0) else spring(dampingRatio = 0.7f))
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
        else -> accent.primary
    }
    val glowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            maskFilter = android.graphics.BlurMaskFilter(26f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
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
            val strokeW = 13.dp.toPx()
            val rect = Rect(strokeW, strokeW, size.width - strokeW, size.height - strokeW)
            if (idle) {
                for (i in 0..3) {
                    val start = -90f + i * (segSweep + gap) + gap / 2
                    drawArc(TacoPalette.Outline.copy(alpha = 0.6f), start, segSweep, false, rect.topLeft, rect.size, style = Stroke(strokeW, cap = StrokeCap.Round))
                    val sw = sweeps[i].value
                    if (sw > 1f) {
                        drawArc(HEAT_COLORS[i], start, sw, false, rect.topLeft, rect.size, style = Stroke(strokeW, cap = StrokeCap.Round))
                        drawIntoCanvas { c ->
                            glowPaint.color = HEAT_COLORS[i].copy(alpha = 0.5f).toArgb()
                            glowPaint.strokeWidth = strokeW
                            c.nativeCanvas.drawArc(
                                android.graphics.RectF(rect.left, rect.top, rect.right, rect.bottom),
                                start, sw, false, glowPaint
                            )
                        }
                    }
                }
            } else {
                drawArc(TacoPalette.Outline.copy(alpha = 0.6f), -90f, 360f, false, rect.topLeft, rect.size, style = Stroke(strokeW, cap = StrokeCap.Round))
                drawArc(fillColor, -90f, progress * 360f, false, rect.topLeft, rect.size, style = Stroke(strokeW, cap = StrokeCap.Round))
            }
        }

        val discScale by animateFloatAsState(
            if (pressed) 0.94f else 1f,
            if (reduce) tween(0) else spring(dampingRatio = 0.5f, stiffness = 500f), label = "disc"
        )
        Canvas(Modifier.fillMaxSize().padding(30.dp).graphicsLayer { scaleX = discScale; scaleY = discScale }) {
            val r = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(
                    listOf(TacoPalette.SurfaceHigh, cs.surfaceVariant, Color(0xFF0A0705)),
                    center = Offset(size.width / 2, size.height / 2), radius = r
                ),
                radius = r
            )
            drawCircle(accent.primary.copy(alpha = if (running || pressed) 0.5f else 0.22f), radius = r, style = Stroke(1.5.dp.toPx()))
            for (i in 1..3) {
                drawCircle(TacoPalette.Cream.copy(alpha = 0.03f), radius = r * i / 3.2f, style = Stroke(1.dp.toPx()))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (launch) {
                is LaunchState.Running -> {
                    val step = launch.steps.getOrNull(launch.stepIndex)
                    Text(step?.title ?: "Working…", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = accent.primary)
                    Spacer(Modifier.height(4.dp))
                    CountUpText((progress * 100).toInt(), TacoType.tnum.copy(fontSize = 30.sp), TacoPalette.Cream)
                    Text("PERCENT", style = TacoType.label)
                }
                is LaunchState.Success -> {
                    TacoIcon(TacoIcon.Check, TacoPalette.Guac, 44.dp, 2.4f)
                    Spacer(Modifier.height(6.dp))
                    Text("SERVED", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TacoPalette.Guac)
                }
                is LaunchState.Failed -> {
                    TacoIcon(TacoIcon.Warn, TacoPalette.Salsa, 40.dp, 2.2f)
                    Spacer(Modifier.height(6.dp))
                    Text("BURNED", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TacoPalette.Salsa)
                }
                LaunchState.Idle -> {
                    TacoIcon(TacoIcon.Play, accent.primary, 40.dp, 2.2f)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (enabled) "HOLD TO LAUNCH" else "SHIZUKU REQUIRED",
                        fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (enabled) TacoPalette.Cream else TacoPalette.Salsa,
                        letterSpacing = 1.6.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(heatName(heat), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HEAT_COLORS[heat.coerceIn(0, 3)])
                }
            }
        }

        if (hold.value > 0.001f && idle) {
            Canvas(Modifier.fillMaxSize().padding(17.dp)) {
                val strokeW = 4.dp.toPx()
                val rect = Rect(strokeW, strokeW, size.width - strokeW, size.height - strokeW)
                drawArc(
                    accent.primary, -90f, hold.value * 360f, false, rect.topLeft, rect.size,
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
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(games, key = { it.second }) { (name, id) ->
            Column(
                Modifier.width(152.dp)
                    .clip(TacoShapes.menuCard)
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, Color(0xFF140F0A))))
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
                        .background(Brush.horizontalGradient(listOf(accent.primary, accent.deep))),
                    contentAlignment = Alignment.Center
                ) { TacoIcon(TacoIcon.Play, accent.onPrimary, 15.dp, 2.4f) }
                Spacer(Modifier.height(16.dp))
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = TacoPalette.Cream)
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
                Spacer(Modifier.height(6.dp))
                Text("Add a game", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TacoPalette.Muted)
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
    var showChannelDialog by remember { mutableStateOf(false) }

    val merged = state.mergedFlags()
    val allowed = merged.keys.count { state.isAllowed(it) }
    val heat = heatLevel(merged.size)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(bottom = 18.dp)
    ) {
        Appear(0) {
            StatusStrip(
                statuses = listOf(
                    LedStatus("Shizuku", state.shizuku.short, state.shizuku.color),
                    LedStatus("Root", if (state.rootAvailable == true) "ok" else "optional", TacoPalette.Muted, optional = true),
                    LedStatus("Roblox", state.robloxVersion ?: "…", if (state.robloxVersion == null) TacoPalette.Faint else TacoPalette.Guac)
                ),
                onClick = { showStatusSheet = true }
            )
        }

        Spacer(Modifier.height(4.dp))

        Appear(1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(0.78f)) {
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
                Spacer(Modifier.height(4.dp))
                ModeSelector(
                    selected = state.mode.ordinal,
                    onSelect = { state.mode = LaunchMode.entries[it] },
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }
        }

        if (state.launchState !is LaunchState.Idle) {
            Spacer(Modifier.height(18.dp))
            Appear(2) {
                PipelineTimeline(
                    launch = state.launchState,
                    onCancel = { state.cancelLaunch() },
                    onRetry = { state.retryLaunch() }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Appear(3) {
            OrderTicket {
                Text("THE ORDER", style = TacoType.label, color = accent.primary)
                Spacer(Modifier.height(6.dp))
                DashedDivider()
                TicketLine(
                    "Roblox",
                    value = state.robloxVersion ?: "detecting…",
                    sub = "tap to copy",
                    onClick = {
                        state.robloxVersion?.let {
                            state.copyText("Roblox version", it)
                            state.notify("Version copied.")
                        }
                    }
                )
                TicketLine("Channel", state.channel, onClick = { showChannelDialog = true })
                TicketLine("Profile", state.activeLayerName(), onClick = { onNavigate("profiles") }, sub = "tap to quick-switch")
                TicketLine("Flags", "${merged.size} active", onClick = { onNavigate("flags") })
                TicketLine(
                    "Allowlist",
                    "$allowed of ${merged.size} allowed",
                    valueColor = TacoPalette.Guac,
                    onClick = { showAllowlistDialog = true }
                )
                TicketLine("Target", state.targetLabel(), onClick = { showTargetDialog = true })
                DashedDivider()
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("tacostrap · hold-to-launch armed", style = TacoType.label)
                    if (state.launchState is LaunchState.Success) {
                        Text("SERVED ✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Guac)
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Appear(4) {
            Column {
                SectionLabel("The menu")
                Spacer(Modifier.height(10.dp))
                if (state.games.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(TacoShapes.menuCard)
                            .border(1.dp, TacoPalette.Outline, TacoShapes.menuCard)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TacoIcon(TacoIcon.Gamepad, TacoPalette.Faint, 26.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("Nothing on the menu yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Muted)
                        Spacer(Modifier.height(10.dp))
                        TacoButton("Add a game", { onNavigate("games") }, kind = ButtonKind.Ghost, icon = TacoIcon.Plus, height = 40.dp)
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
                    Triple("Root", if (state.rootAvailable == true) "available (optional)" else "not found — optional", TacoPalette.Muted),
                    Triple("Roblox", state.robloxVersion ?: "not detected", if (state.robloxVersion == null) TacoPalette.Salsa else TacoPalette.Guac)
                ),
                fixSteps = listOf(
                    "Shizuku" to "1. Start Shizuku (wireless debugging or root start).\n2. Tap Grant below.\n3. LED turns green.",
                    "Root" to "Optional. Enable Magisk/SU, then turn on “Use root” in Settings. No root? Use Shizuku instead.",
                    "Roblox" to "Confirm the package name in Settings if the version shows “not detected”."
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
            title = "Launch target",
            options = listOf("Home (Roblox app)") + names,
            onPick = { i ->
                state.targetGame = if (i == 0) null else names[i - 1]
                showTargetDialog = false
            },
            onDismiss = { showTargetDialog = false }
        )
    }
    if (showChannelDialog) {
        SimplePickDialog(
            title = "Roblox channel",
            options = listOf("release", "beta"),
            onPick = { state.channel = listOf("release", "beta")[it]; showChannelDialog = false },
            onDismiss = { showChannelDialog = false }
        )
    }
    if (showAllowlistDialog) {
        AlertishDialog(
            title = "Allowlist check",
            body = "All ${merged.size} active flags are allowed. No flags will be ignored by Roblox.",
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
    class Range(title: String, sub: String, val a: String, val b: String, val da: String, val db: String) : FS(title, sub)
}

data class Opt(val label: String, val sets: Map<String, String>)

private fun texSet(n: String) = mapOf("DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to n)

private const val FPS_FLAG = "DFIntTaskSchedulerTargetFps"

private val FS_TEXTURE = FS.Choice("Texture quality", "Sharper textures use more memory",
    listOf(Opt("Automatic", emptyMap()), Opt("Lowest", texSet("0")), Opt("Low", texSet("1")),
        Opt("Medium", texSet("2")), Opt("High", texSet("3"))))
private val FS_AA = FS.Choice("Anti aliasing", "Smooths jagged edges. Costs frame rate",
    listOf(Opt("Automatic", emptyMap()), Opt("Off", mapOf("FIntDebugForceMSAASamples" to "1")),
        Opt("2x", mapOf("FIntDebugForceMSAASamples" to "2")), Opt("4x", mapOf("FIntDebugForceMSAASamples" to "4")),
        Opt("8x", mapOf("FIntDebugForceMSAASamples" to "8"))))
private val FS_RENDER = FS.Choice("Rendering mode", "Graphics engine Roblox uses",
    listOf(Opt("Automatic", emptyMap()), Opt("Vulkan", mapOf("FFlagDebugGraphicsPreferVulkan" to "True")),
        Opt("OpenGL", mapOf("FFlagDebugGraphicsPreferOpenGL" to "True"))))
private val FS_SKY = FS.Toggle("Gray sky", "Replaces the sky with plain gray", mapOf("FFlagDebugSkyGray" to "True"))
private val FS_SHADOWS = FS.Toggle("Disable shadows", "Turns off Roblox shadows", mapOf("FIntRenderShadowIntensity" to "0"))
private val FS_POSTFX = FS.Toggle("Disable post-processing", "Turns off bloom and color effects", mapOf("FFlagDisablePostFx" to "True"))
private val FS_LOD = FS.Choice("Detail distance", "How far high detail models render",
    listOf(Opt("Default", emptyMap()), Opt("Near", mapOf("DFIntDebugFRMQualityLevelOverride" to "1")),
        Opt("Far", mapOf("DFIntDebugFRMQualityLevelOverride" to "6"))))

// Desync & Physics Flags
private val FS_DESYNC = FS.Toggle(
    title = "Network Desync / Physics Offset",
    sub = "Adjusts physics sender rate and replication delay for desync effects",
    sets = mapOf(
        "DFIntPhysicsSenderMaxBandwidthBps" to "100000",
        "DFIntPhysicsSenderRate" to "1",
        "DFIntDataSenderMaxBandwidthBps" to "100000",
        "DFIntMinimalIntervalBetweenPhysicsSendMS" to "1"
    )
)

private val FS_DESYNC_LATENCY = FS.Toggle(
    title = "Simulated Lag / Interpolation Desync",
    sub = "Forces high network interpolation delay to desync client position",
    sets = mapOf(
        "DFIntClientPhysicsPostStepMode" to "1",
        "DFIntMinimalIntervalBetweenPhysicsSendMS" to "50"
    )
)

private val FS_FPS = FS.Choice("Frame rate limit", "Above 60 also lifts the Android frame rate cap",
    listOf(Opt("Default", emptyMap())) + listOf(30, 60, 90, 120, 144, 165, 240).map { Opt("$it FPS", mapOf(FPS_FLAG to "$it")) })
private val FS_FPSCOUNT = FS.Toggle("FPS counter", "Shows frame rate in the corner", mapOf("FFlagDebugDisplayFPS" to "True"))
private val FS_PRELOAD = FS.Toggle("Faster loading", "Preloads more assets. Can use a lot of memory",
    mapOf("DFIntNumAssetsMaxToPreload" to "2147483647", "DFIntAssetPreloading" to "2147483647"))
private val FS_QUALITY = FS.Choice("Graphics quality override", "Locks the Roblox graphics level",
    listOf(Opt("Default", emptyMap())) + listOf(1, 3, 5, 7, 10).map { Opt("Level $it", mapOf("FIntRomarkStartWithGraphicQualityLevel" to "$it")) })
private val FS_GRASSMOVE = FS.Choice("Grass movement", "How much grass sways",
    listOf(Opt("Default", emptyMap()), Opt("None", mapOf("FIntGrassMovementReducedMotionFactor" to "0"))))
private val FS_NOGRASS = FS.Toggle("Remove grass", "Hides built-in terrain grass",
    mapOf("FIntFRMMinGrassDistance" to "0", "FIntFRMMaxGrassDistance" to "0"))
private val FS_GRASSDIST = FS.Range("Grass distance", "Set both to 0 to hide grass",
    "FIntFRMMinGrassDistance", "FIntFRMMaxGrassDistance", "100", "290")
private val FS_BLUR = FS.Toggle("Remove blur", "No blur behind Roblox menus", mapOf("FIntRobloxGuiBlurIntensity" to "0"))
private val FS_MTU = FS.Num("MTU size", "Bytes per network packet", "DFIntConnectionMTUSize")

val SECTIONS: List<Pair<String, List<FS>>> = listOf(
    "Graphics" to listOf(FS_TEXTURE, FS_AA, FS_RENDER, FS_SKY, FS_SHADOWS, FS_POSTFX, FS_LOD),
    "Performance" to listOf(FS_FPS, FS_FPSCOUNT, FS_PRELOAD),
    "Desync & Network" to listOf(FS_DESYNC, FS_DESYNC_LATENCY, FS_MTU),
    "World detail" to listOf(FS_QUALITY, FS_GRASSMOVE, FS_NOGRASS, FS_GRASSDIST),
    "Interface" to listOf(FS_BLUR)
)

internal fun fsKeys(fs: FS): Set<String> = when (fs) {
    is FS.Toggle -> fs.sets.keys
    is FS.Choice -> fs.opts.flatMap { it.sets.keys }.toSet()
    is FS.Num -> setOf(fs.key)
    is FS.Range -> setOf(fs.a, fs.b)
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
    var urlDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(350); loaded = true }

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
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        stickyHeader {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                if (!loaded) {
                    ShimmerBox(Modifier.fillMaxWidth().height(76.dp), RoundedCornerShape(18.dp))
                } else {
                    if (pending > 0) {
                        Row(
                            Modifier.fillMaxWidth().clip(TacoShapes.control)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, accent.primary.copy(0.4f), TacoShapes.control)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CountUpText(pending, TacoType.tnum.copy(fontSize = 16.sp), accent.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("pending changes", fontSize = 13.sp, color = TacoPalette.Muted, modifier = Modifier.weight(1f))
                            TacoButton("Discard", { state.discardDraft() }, kind = ButtonKind.Quiet, height = 34.dp)
                            Spacer(Modifier.width(6.dp))
                            TacoButton("Apply", { state.applyDraft() }, kind = ButtonKind.Primary, icon = TacoIcon.Bolt, height = 34.dp)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        placeholder = { Text("Search ${draft.size} flags", color = TacoPalette.Faint) },
                        singleLine = true,
                        leadingIcon = { TacoIcon(TacoIcon.Search, TacoPalette.Muted, 18.dp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TacoPalette.Cream, unfocusedTextColor = TacoPalette.Cream,
                            focusedBorderColor = accent.primary, unfocusedBorderColor = TacoPalette.Outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = TacoShapes.control,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().hScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { c -> CategoryChip(c, category == c) { category = c } }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        if (!loaded) {
            items(6) { ShimmerBox(Modifier.fillMaxWidth().height(56.dp)) }
            return@LazyColumn
        }

        if (category == "All" || SECTIONS.any { it.first == category }) {
            val visible = if (category == "All") SECTIONS else SECTIONS.filter { it.first == category }
            visible.forEach { (title, list) ->
                val rows = list.filter { it.matches(search) }
                if (rows.isNotEmpty()) {
                    item(key = "sec_$title") { SectionLabel(title) }
                    items(rows, key = { "fs_${it.title}" }) { fs -> FlagSettingRow(fs, draft, state) }
                }
            }
        }

        if (category == "All" || category == "Custom") {
            item(key = "sec_custom") { SectionLabel("Custom flags") }
            if (customKeys.isEmpty()) {
                item(key = "custom_empty") {
                    EmptyState(
                        TacoIcon.Flag,
                        if (search.isEmpty()) "No custom flags" else "No matches",
                        if (search.isEmpty()) "Tap Add to create a raw FastFlag by name and value."
                        else "Try a different search."
                    ) {
                        TacoButton("Add flag", { editorKey = "" }, kind = ButtonKind.Ghost, icon = TacoIcon.Plus, height = 40.dp)
                    }
                }
            }
            items(customKeys, key = { "c$it" }) { key ->
                CustomFlagRow(
                    name = key, value = draft[key] ?: "",
                    allowed = state.isAllowed(key),
                    onEdit = { editorKey = key },
                    onToggle = { b -> draft[key] = if (b) "True" else "False" },
                    onDuplicate = { state.duplicateDraftFlag(key) },
                    onDelete = { draft.remove(key); state.notify("Flag removed.", "flag") }
                )
            }
        }

        item(key = "actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TacoButton("Add", { editorKey = "" }, Modifier.weight(1f), ButtonKind.Ghost, TacoIcon.Plus, 42.dp)
                TacoButton("JSON", { jsonSheet = true }, Modifier.weight(1f), ButtonKind.Quiet, TacoIcon.Doc, 42.dp)
                TacoButton("URL", { urlDialog = true }, Modifier.weight(1f), ButtonKind.Quiet, TacoIcon.Globe, 42.dp)
                TacoButton("Clear", { confirmClear = true }, Modifier.weight(1f), ButtonKind.Danger, TacoIcon.Trash, 42.dp)
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
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
                Text(if (isNew) "Add a flag" else "Edit flag", style = TacoType.display)
                Spacer(Modifier.height(6.dp))
                if (!isNew) Row(verticalAlignment = Alignment.CenterVertically) {
                    AllowlistBadge(state.isAllowed(key)); Spacer(Modifier.width(10.dp))
                    Text(key, style = TacoType.mono.copy(fontSize = 12.sp), color = TacoPalette.Muted)
                }
                Spacer(Modifier.height(14.dp))
                if (isNew) {
                    OutlinedTextField(
                        name, { name = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' } },
                        label = { Text("Flag name") }, singleLine = true,
                        colors = tacoFieldColors(), shape = TacoShapes.control,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value, { value = it }, label = { Text("Value") }, singleLine = true,
                    colors = tacoFieldColors(), shape = TacoShapes.control,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isNew) {
                        TacoButton("Delete", {
                            draft.remove(key); state.notify("Flag removed.", "flag"); editorKey = null
                        }, Modifier.weight(1f), ButtonKind.Danger, TacoIcon.Trash, 44.dp)
                    }
                    TacoButton("Save", {
                        val n = name.trim()
                        if (n.isNotEmpty()) {
                            if (!isNew && n != key) draft.remove(key)
                            draft[n] = value.trim()
                            state.notify("Flag saved.", "flag")
                        }
                        editorKey = null
                    }, Modifier.weight(2f), ButtonKind.Primary, TacoIcon.Check, 44.dp)
                }
            }
        }
    }

    if (jsonSheet) {
        var text by remember { mutableStateOf(toJson(state.draftFlags, 2)) }
        var msg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { jsonSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Import / export JSON", style = TacoType.display, fontSize = 19.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        text, { text = it }, minLines = 5, maxLines = 10,
                        textStyle = TacoType.mono.copy(fontSize = 12.sp),
                        colors = tacoFieldColors(), shape = TacoShapes.control,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (msg.isNotEmpty()) Text(msg, fontSize = 13.sp, color = accent.primary)
                }
            },
            confirmButton = {
                TacoButton("Replace flags", {
                    msg = runCatching {
                        val m = parseFlags(text)
                        state.draftFlags.clear(); state.draftFlags.putAll(m)
                        "Loaded ${m.size} flags."
                    }.getOrElse { "Invalid JSON." }
                }, kind = ButtonKind.Ghost, height = 40.dp)
            },
            dismissButton = {
                Row {
                    TacoButton("Copy", { state.copyText("flags", text); state.notify("Copied.") }, kind = ButtonKind.Quiet, icon = TacoIcon.Copy, height = 40.dp)
                    Spacer(Modifier.width(6.dp))
                    TacoButton("Close", { jsonSheet = false }, kind = ButtonKind.Quiet, height = 40.dp)
                }
            }
        )
    }

    if (urlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Import from URL", style = TacoType.display, fontSize = 19.sp) },
            text = {
                OutlinedTextField(
                    url, { url = it }, label = { Text("https://…json") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = tacoFieldColors(), shape = TacoShapes.control
                )
            },
            confirmButton = {
                TacoButton("Import", {
                    urlDialog = false
                    if (url.isNotBlank()) state.importFromUrl(url.trim())
                }, kind = ButtonKind.Primary, icon = TacoIcon.Down, height = 42.dp)
            },
            dismissButton = { TacoButton("Cancel", { urlDialog = false }, kind = ButtonKind.Quiet, height = 42.dp) }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Clear all flags?", style = TacoType.display, fontSize = 19.sp) },
            text = { Text("Removes ${draft.size} flags. Saved layers are kept.", color = TacoPalette.Muted) },
            confirmButton = {
                TacoButton("Clear all", { draft.clear(); confirmClear = false; state.notify("All flags cleared.", "flag") },
                    kind = ButtonKind.Danger, height = 42.dp)
            },
            dismissButton = { TacoButton("Cancel", { confirmClear = false }, kind = ButtonKind.Quiet, height = 42.dp) }
        )
    }
}

@Composable
private fun FlagSettingRow(fs: FS, draft: SnapshotStateMap<String, String>, state: TacoState) {
    Column(
        Modifier.fillMaxWidth().clip(TacoShapes.card)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TacoShapes.card)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fs.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Cream)
                Text(fs.sub, fontSize = 12.sp, color = TacoPalette.Muted, lineHeight = 16.sp)
            }
            if (fs is FS.Toggle) {
                val on = fs.sets.all { (k, v) -> draft[k] == v }
                TacoSwitch(on, { want ->
                    if (want) draft.putAll(fs.sets) else fs.sets.keys.forEach { draft.remove(it) }
                })
            }
        }
        when (fs) {
            is FS.Choice -> {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            is FS.Num -> {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    draft[fs.key] ?: "", { t ->
                        if (t.isBlank()) draft.remove(fs.key) else draft[fs.key] = t.trim()
                    },
                    placeholder = { Text("Off", color = TacoPalette.Faint) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = tacoFieldColors(), shape = TacoShapes.control,
                    modifier = Modifier.width(140.dp),
                    textStyle = TacoType.tnum.copy(fontSize = 14.sp)
                )
            }
            is FS.Range -> {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        draft[fs.a] ?: "", { t ->
                            if (t.isBlank()) draft.remove(fs.a) else { draft[fs.a] = t.trim(); if (draft[fs.b] == null) draft[fs.b] = fs.db }
                        },
                        placeholder = { Text(fs.da, color = TacoPalette.Faint) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = tacoFieldColors(), shape = TacoShapes.control,
                        modifier = Modifier.width(96.dp), textStyle = TacoType.tnum.copy(fontSize = 14.sp)
                    )
                    Text("  to  ", fontSize = 12.sp, color = TacoPalette.Muted)
                    OutlinedTextField(
                        draft[fs.b] ?: "", { t ->
                            if (t.isBlank()) draft.remove(fs.b) else { draft[fs.b] = t.trim(); if (draft[fs.a] == null) draft[fs.a] = fs.da }
                        },
                        placeholder = { Text(fs.db, color = TacoPalette.Faint) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = tacoFieldColors(), shape = TacoShapes.control,
                        modifier = Modifier.width(96.dp), textStyle = TacoType.tnum.copy(fontSize = 14.sp)
                    )
                }
            }
            is FS.Toggle -> {}
        }
        if (fs !is FS.Toggle) {
            val keys = fsKeys(fs)
            if (keys.size == 1) {
                Spacer(Modifier.height(6.dp))
                AllowlistBadge(state.isAllowed(keys.first()))
            }
        }
    }
}

@Composable
private fun CustomFlagRow(
    name: String, value: String, allowed: Boolean?,
    onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit
) {
    val accent = LocalTacoAccent.current
    val isBool = value.equals("true", true) || value.equals("false", true)
    Column(
        Modifier.fillMaxWidth().clip(TacoShapes.card)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TacoShapes.card)
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = TacoType.mono.copy(fontSize = 12.5f.sp), color = TacoPalette.Cream)
                Spacer(Modifier.height(2.dp))
                AllowlistBadge(allowed)
            }
            if (isBool) {
                TacoSwitch(value.equals("true", true), onToggle)
            } else {
                Text(value, style = TacoType.tnum.copy(fontSize = 14.sp), color = accent.primary, maxLines = 1)
            }
        }
        if (!isBool) {
            Spacer(Modifier.height(6.dp))
            Text(value, style = TacoType.mono.copy(fontSize = 12.sp), color = TacoPalette.Muted, maxLines = 2)
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Text("Edit", fontSize = 12.sp, color = accent.primary,
                modifier = Modifier.clickable(onClick = onEdit).padding(end = 16.dp, top = 4.dp))
            Text("Duplicate", fontSize = 12.sp, color = TacoPalette.Muted,
                modifier = Modifier.clickable(onClick = onDuplicate).padding(end = 16.dp, top = 4.dp))
            Text("Delete", fontSize = 12.sp, color = TacoPalette.Salsa,
                modifier = Modifier.clickable(onClick = onDelete).padding(top = 4.dp))
        }
    }
}

/* ======================================================================
 *  6. PROFILES SCREEN
 * ====================================================================== */

class FlagLayer(val id: String, name: String, enabled: Boolean = true, flags: Map<String, String> = emptyMap()) {
    var name by mutableStateOf(name)
    var enabled by mutableStateOf(enabled)
    val flags = mutableStateMapOf<String, String>().apply { putAll(flags) }
}

data class Pack(val name: String, val desc: String, val sets: Map<String, String>)

val PACK_KEYS: Set<String> = setOf(
    "DFFlagTextureQualityOverrideEnabled", "DFIntTextureQualityOverride",
    "FIntDebugForceMSAASamples", "FIntRenderShadowIntensity", "FFlagDisablePostFx",
    "FIntRomarkStartWithGraphicQualityLevel", "FIntFRMMinGrassDistance",
    "FIntFRMMaxGrassDistance", "FIntRobloxGuiBlurIntensity", "FFlagDebugSkyGray",
    "DFIntDebugFRMQualityLevelOverride", "FFlagDebugGraphicsPreferVulkan",
    "FFlagDebugGraphicsPreferOpenGL", "DFIntTaskSchedulerTargetFps", "FFlagDebugDisplayFPS"
)

val PACKS = listOf(
    Pack("Balanced", "Medium textures, 2x anti aliasing",
        mapOf("DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to "2",
            "FIntDebugForceMSAASamples" to "2")),
    Pack("Performance", "Lowest textures, quality 3, no shadows or post-processing",
        mapOf("DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to "0",
            "FIntDebugForceMSAASamples" to "1", "FIntRenderShadowIntensity" to "0",
            "FFlagDisablePostFx" to "True", "FIntRomarkStartWithGraphicQualityLevel" to "3")),
    Pack("Quality", "Sharp textures, 4x anti aliasing",
        mapOf("DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to "3",
            "FIntDebugForceMSAASamples" to "4")),
    Pack("Potato", "For weak devices. No grass, shadows, blur; minimum everything",
        mapOf("DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to "0",
            "FIntDebugForceMSAASamples" to "1", "FIntRenderShadowIntensity" to "0",
            "FFlagDisablePostFx" to "True", "FIntFRMMinGrassDistance" to "0", "FIntFRMMaxGrassDistance" to "0",
            "FIntRobloxGuiBlurIntensity" to "0", "FIntRomarkStartWithGraphicQualityLevel" to "1")),
    Pack("Competitive", "240 FPS cap, no shadows, blur or grass, FPS counter",
        mapOf("DFIntTaskSchedulerTargetFps" to "240", "FIntRenderShadowIntensity" to "0",
            "FIntRobloxGuiBlurIntensity" to "0", "FIntFRMMinGrassDistance" to "0", "FIntFRMMaxGrassDistance" to "0",
            "FFlagDebugDisplayFPS" to "True", "FIntDebugForceMSAASamples" to "1"))
)

private val LAYER_HEIGHT = 96.dp

@Composable
fun ProfilesScreen(state: TacoState) {
    val accent = LocalTacoAccent.current
    var newLayerName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<FlagLayer?>(null) }
    var renameText by remember { mutableStateOf("") }
    val stepPx = with(LocalDensity.current) { (LAYER_HEIGHT + 10.dp).toPx() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Profiles", style = TacoType.displayBig)
            Text("Stackable layers. Top layers win; reorder to change the blend.", fontSize = 13.sp, color = TacoPalette.Muted)
        }

        item { SectionLabel("Packs") }
        items(PACKS.size, key = { "pack$it" }) { i ->
            val p = PACKS[i]
            Column(
                Modifier.fillMaxWidth().clip(TacoShapes.menuCard)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TacoShapes.menuCard)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(TacoIcon.Layers, accent.primary, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Cream)
                        Text(p.desc, fontSize = 12.sp, color = TacoPalette.Muted, lineHeight = 16.sp)
                    }
                    TacoButton("Apply", {
                        state.applyPack(p)
                        state.notify("Applied ${p.name} pack.", "flag")
                    }, kind = ButtonKind.Ghost, height = 38.dp)
                }
            }
        }

        item { SectionLabel("Layers") }

        item {
            Column(
                Modifier.fillMaxWidth().clip(TacoShapes.card)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TacoShapes.card)
                    .padding(14.dp)
            ) {
                Text("Save current flags as a layer", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Cream)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newLayerName, { newLayerName = it },
                        placeholder = { Text("Layer name", color = TacoPalette.Faint) }, singleLine = true,
                        colors = tacoFieldColors(), shape = TacoShapes.control,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TacoButton("Save", {
                        if (newLayerName.isNotBlank()) {
                            state.saveLayerFromCurrent(newLayerName.trim())
                            newLayerName = ""
                            state.notify("Layer saved.", "flag")
                        }
                    }, kind = ButtonKind.Primary, icon = TacoIcon.Save, height = 46.dp)
                }
            }
        }

        if (state.layers.size <= 1) {
            item {
                EmptyState(TacoIcon.Layers, "No extra layers yet",
                    "Save the current flags as a layer, or import a profile from the clipboard.") {
                    TacoButton("Import from clipboard", { state.importLayerFromClipboard() },
                        kind = ButtonKind.Ghost, height = 40.dp)
                }
            }
        }

        items(state.layers.asReversed(), key = { it.id }) { layer ->
            val isBase = layer.id == "base"
            var dragOffset by remember { mutableFloatStateOf(0f) }
            LayerCard(
                layer = layer,
                isBase = isBase,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LAYER_HEIGHT)
                    .zIndex(if (dragOffset != 0f) 1f else 0f)
                    .graphicsLayer { translationY = dragOffset },
                onDrag = if (isBase) null else { delta ->
                    dragOffset += delta
                    val idx = state.layers.indexOf(layer)
                    if (dragOffset > stepPx * 0.6f && idx > 1) {
                        state.moveLayer(idx, idx - 1); dragOffset -= stepPx
                    } else if (dragOffset < -stepPx * 0.6f && idx < state.layers.lastIndex) {
                        state.moveLayer(idx, idx + 1); dragOffset += stepPx
                    }
                },
                onDragEnd = { dragOffset = 0f },
                onToggle = { state.setLayerEnabled(layer.id, it) },
                onRename = { renameTarget = layer; renameText = layer.name },
                onDuplicate = { state.duplicateLayer(layer.id) },
                onDelete = { state.deleteLayer(layer.id) }
            )
        }
    }

    renameTarget?.let { layer ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Rename layer", style = TacoType.display, fontSize = 19.sp) },
            text = {
                OutlinedTextField(
                    renameText, { renameText = it }, singleLine = true,
                    colors = tacoFieldColors(), shape = TacoShapes.control
                )
            },
            confirmButton = {
                TacoButton("Rename", {
                    state.renameLayer(layer.id, renameText.trim())
                    renameTarget = null
                }, kind = ButtonKind.Primary, height = 42.dp)
            },
            dismissButton = { TacoButton("Cancel", { renameTarget = null }, kind = ButtonKind.Quiet, height = 42.dp) }
        )
    }
}

@Composable
private fun LayerCard(
    layer: FlagLayer,
    isBase: Boolean,
    modifier: Modifier = Modifier,
    onDrag: ((Float) -> Unit)?,
    onDragEnd: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = LocalTacoAccent.current
    val heat = heatLevel(layer.flags.size)
    val currentDrag by rememberUpdatedState(onDrag)
    Column(
        modifier
            .clip(TacoShapes.card)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(
                1.dp,
                if (layer.enabled) accent.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
                TacoShapes.card
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onDrag != null) {
                Box(
                    Modifier.size(32.dp)
                        .pointerInput(layer.id) {
                            detectDragGestures(
                                onDrag = { change, amount -> change.consume(); currentDrag?.invoke(amount.y) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { TacoIcon(TacoIcon.Grip, TacoPalette.Faint, 18.dp, 2f) }
            } else {
                IconCircle(TacoIcon.Flag, TacoPalette.Faint, 28.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    layer.name + if (isBase) "  (base)" else "",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = if (layer.enabled) TacoPalette.Cream else TacoPalette.Muted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${layer.flags.size} flags", style = TacoType.tnum.copy(fontSize = 12.sp), color = TacoPalette.Muted)
                    Spacer(Modifier.width(8.dp))
                    repeat(4) { i ->
                        Box(
                            Modifier.padding(end = 3.dp).size(6.dp).clip(CircleShape)
                                .background(if (i <= heat) HEAT_COLORS[i] else TacoPalette.Outline)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(heatName(heat), fontSize = 11.sp, color = TacoPalette.Muted)
                }
            }
            TacoSwitch(layer.enabled, onToggle, enabled = !isBase)
        }
        Row {
            Text("Rename", fontSize = 12.sp, color = accent.primary,
                modifier = Modifier.clickable(onClick = onRename).padding(end = 14.dp, top = 4.dp))
            if (!isBase) {
                Text("Duplicate", fontSize = 12.sp, color = TacoPalette.Muted,
                    modifier = Modifier.clickable(onClick = onDuplicate).padding(end = 14.dp, top = 4.dp))
                Text("Delete", fontSize = 12.sp, color = TacoPalette.Salsa,
                    modifier = Modifier.clickable(onClick = onDelete).padding(top = 4.dp))
            }
        }
    }
}

/* ======================================================================
 *  7. GAMES SCREEN
 * ====================================================================== */

data class PinnedGame(val placeId: String, val profile: String? = null)

fun parsePlaceId(input: String): String? {
    val t = input.trim()
    t.toLongOrNull()?.let { return it.toString() }
    Regex("""place[Ii]d=(\d+)""").find(t)?.let { return it.groupValues[1] }
    Regex("""/games/(\d+)""").find(t)?.let { return it.groupValues[1] }
    Regex("""(\d{5,})""").find(t)?.let { return it.groupValues[1] }
    return null
}

@Composable
fun GamesScreen(state: TacoState) {
    val accent = LocalTacoAccent.current
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var detailGame by remember { mutableStateOf<String?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Games", style = TacoType.displayBig)
                    Text("Pin experiences, set a per-game layer, jump straight in.", fontSize = 13.sp, color = TacoPalette.Muted)
                }
                TacoButton("Add", { showAdd = true }, kind = ButtonKind.Ghost, icon = TacoIcon.Plus, height = 40.dp)
            }
        }
        if (state.games.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(TacoIcon.Gamepad, "No pinned games", "Add by place ID or paste any roblox.com/games link.") {
                    TacoButton("Add a game", { showAdd = true }, kind = ButtonKind.Ghost, icon = TacoIcon.Plus, height = 40.dp)
                }
            }
        }
        items(state.games.entries.toList(), key = { it.value.placeId }) { entry ->
            val name = entry.key
            val game = entry.value
            Column(
                Modifier
                    .clip(TacoShapes.menuCard)
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, Color(0xFF140F0A))))
                    .border(
                        1.dp,
                        if (state.targetGame == name) accent.primary.copy(0.6f) else MaterialTheme.colorScheme.outlineVariant,
                        TacoShapes.menuCard
                    )
                    .combinedClickable(
                        onClick = { detailGame = name },
                        onLongClick = {
                            if (state.hapticsOn) hapticTick(context)
                            state.launchGameById(game.placeId)
                        }
                    )
                    .padding(14.dp)
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(accent.primary, accent.deep))),
                    contentAlignment = Alignment.Center
                ) { TacoIcon(TacoIcon.Play, accent.onPrimary, 15.dp, 2.4f) }
                Spacer(Modifier.height(14.dp))
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Cream, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text("#${game.placeId}", style = TacoType.tnum.copy(fontSize = 12.sp), color = TacoPalette.Muted)
                game.profile?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("layer: $it", fontSize = 11.sp, color = accent.primary)
                }
                if (state.targetGame == name) {
                    Spacer(Modifier.height(4.dp))
                    Text("LAUNCH TARGET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent.primary)
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var idOrLink by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Pin a game", style = TacoType.display, fontSize = 19.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Name") }, singleLine = true,
                        colors = tacoFieldColors(), shape = TacoShapes.control,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        idOrLink, { idOrLink = it; error = null },
                        label = { Text("Place ID or roblox.com link") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = tacoFieldColors(), shape = TacoShapes.control,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 13.sp, color = TacoPalette.Salsa)
                    }
                }
            },
            confirmButton = {
                TacoButton("Pin", {
                    val id = parsePlaceId(idOrLink)
                    if (name.isBlank() || id == null) {
                        error = "Need a name and a valid place ID or link."
                    } else {
                        state.addGame(name.trim(), id)
                        showAdd = false
                    }
                }, kind = ButtonKind.Primary, icon = TacoIcon.Plus, height = 44.dp)
            },
            dismissButton = { TacoButton("Cancel", { showAdd = false }, kind = ButtonKind.Quiet, height = 44.dp) }
        )
    }

    detailGame?.let { name ->
        val game = state.games[name] ?: return@let
        ModalBottomSheet(
            onDismissRequest = { detailGame = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = TacoPalette.Cream
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
                Text(name, style = TacoType.display)
                Text("Place ID ${game.placeId}", style = TacoType.tnum.copy(fontSize = 13.sp), color = TacoPalette.Muted)
                Spacer(Modifier.height(14.dp))
                Text("PROFILE LAYER", style = TacoType.label)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryChip("None", game.profile == null) { state.setGameProfile(name, null) }
                    state.layers.filter { it.id != "base" }.forEach { layer ->
                        CategoryChip(layer.name, game.profile == layer.name) { state.setGameProfile(name, layer.name) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TacoButton("Launch", {
                        if (state.hapticsOn) hapticCrunch(context)
                        state.launchGameById(game.placeId)
                        detailGame = null
                    }, Modifier.weight(2f), ButtonKind.Primary, TacoIcon.Play, 48.dp)
                    TacoButton("Set target", {
                        state.targetGame = name; state.notify("Target: $name"); detailGame = null
                    }, Modifier.weight(1f), ButtonKind.Ghost, height = 48.dp)
                    TacoButton("", {
                        state.removeGame(name); detailGame = null
                    }, Modifier.width(48.dp), ButtonKind.Danger, TacoIcon.Trash, 48.dp)
                }
            }
        }
    }
}

/* ======================================================================
 *  8. SETTINGS SCREEN & IMAGE DECODER
 * ====================================================================== */

@Composable
fun rememberImageBitmapFromUri(uriString: String): ImageBitmap? {
    val context = LocalContext.current
    var imageBitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uriString) {
        if (uriString.isBlank()) {
            imageBitmap = null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                    }
                }.onSuccess { bitmap ->
                    imageBitmap = bitmap
                }
            }
        }
    }
    return imageBitmap
}

@Composable
fun SettingsScreen(state: TacoState) {
    val accent = LocalTacoAccent.current
    val context = LocalContext.current
    var showChangelog by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            state.bgImageUri = uri.toString()
            state.persist()
            state.notify("Custom background image updated.")
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = TacoType.displayBig)

        SectionLabel("Shizuku")
        Column(
            Modifier.fillMaxWidth().clip(TacoShapes.card)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .border(1.dp, state.shizuku.color.copy(alpha = 0.4f), TacoShapes.card)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(TacoIcon.Bolt, state.shizuku.color, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Shizuku ${state.shizuku.long.lowercase()}",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = state.shizuku.color)
                    Text("live status", fontSize = 11.sp, color = TacoPalette.Faint)
                }
            }
            Spacer(Modifier.height(12.dp))
            val steps = listOf(
                "Enable Developer options, then Wireless debugging, on this phone.",
                "Start Shizuku via pairing code (or root start).",
                "Tap Grant below so TacoStrap can write the flag file.",
                "Launch from The Comal. Root is optional."
            )
            steps.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Text("${i + 1}.", style = TacoType.tnum.copy(fontSize = 13.sp), color = accent.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(s, fontSize = 13.sp, color = TacoPalette.Muted, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TacoButton("Grant", { state.requestShizukuPermission() }, Modifier.weight(1f), ButtonKind.Primary, TacoIcon.Bolt, 44.dp)
                TacoButton("shizuku.rikka.app", {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))) }
                }, Modifier.weight(1f), ButtonKind.Quiet, TacoIcon.Globe, 44.dp)
            }
        }

        SectionLabel("Appearance & Background")
        SettingsGroup {
            Text("Accent", style = TacoType.body, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            Row(
                Modifier.hScroll().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TACO_ACCENTS.forEachIndexed { i, a ->
                    val sel = state.accentIdx == i
                    val s by animateFloatAsState(if (sel) 1.12f else 1f, TacoMotion.softSpring, label = "ac")
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { state.accentIdx = i; state.persist() }
                    ) {
                        Box(
                            Modifier.size(42.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(a.primary, a.deep)))
                                .then(if (sel) Modifier.border(2.dp, TacoPalette.Cream, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) { if (sel) TacoIcon(TacoIcon.Check, a.onPrimary, 18.dp, 2.6f) }
                        Spacer(Modifier.height(5.dp))
                        Text(a.name, fontSize = 11.sp,
                            color = if (sel) TacoPalette.Cream else TacoPalette.Muted,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            SettingValue(
                title = "Custom background image",
                value = if (state.bgImageUri.isEmpty()) "Default" else "Active",
                onClick = { imagePickerLauncher.launch("image/*") },
                sub = "Tap to pick a custom wallpaper image",
                icon = TacoIcon.Image
            )
            if (state.bgImageUri.isNotEmpty()) {
                SettingValue(
                    title = "Remove background image",
                    value = "Reset",
                    onClick = {
                        state.bgImageUri = ""
                        state.persist()
                        state.notify("Custom background removed.")
                    },
                    sub = "Revert to solid/amoled background"
                )
            }
            SettingSwitch("AMOLED true black", "Pure black background on OLED screens.", state.amoled) { state.amoled = it; state.persist() }
            SettingSwitch("Haptics", "Crunch on launch and ticks on picks.", state.hapticsOn) { state.hapticsOn = it; state.persist() }
            SettingSwitch("Reduce motion", "Disables springs, particles and shimmer.", state.reduceMotion) { state.reduceMotion = it; state.persist() }
        }

        SectionLabel("Connection")
        SettingsGroup {
            SettingValue("Package name", state.pkg, { state.editPkg() })
            SettingValue("Flag file path", state.path, { state.cyclePath() }, sub = "tap to cycle presets")
            SettingSwitch(
                "Use root",
                if (state.rootAvailable == true) "Apply via superuser instead of Shizuku."
                else "Not detected — optional. Shizuku works without it.",
                state.useRoot
            ) { state.useRoot = it; state.persist() }
            SettingSwitch("Write file on apply", "Writes flags to the flag file during launch.", state.applyOnSave) { state.applyOnSave = it; state.persist() }
            SettingSwitch("Restart Roblox", "Force-stops Roblox before launching.", state.restart) { state.restart = it; state.persist() }
            SettingSwitch("Disable frame-rate cap", "Lifts Android's game frame cap above 60 FPS automatically.", state.fpsCap) { state.fpsCap = it; state.persist() }
            SettingValue("Clear Roblox cache", "frees space", { state.clearCache() })
        }

        SectionLabel("Messages")
        SettingsGroup {
            Text("Errors always show regardless of these toggles.",
                fontSize = 12.sp, color = TacoPalette.Faint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            SettingSwitch("Flag messages", "Saved / applied / imported confirmations.", state.msgFlag) { state.msgFlag = it; state.persist() }
            SettingSwitch("Launch messages", "Roblox and game launch confirmations.", state.msgLaunch) { state.msgLaunch = it; state.persist() }
            SettingSwitch("Other messages", "Copies, backups and other actions.", state.msgOther) { state.msgOther = it; state.persist() }
        }

        SectionLabel("Backup")
        SettingsGroup {
            SettingValue("Export backup", "flags, layers and games as one JSON", { state.exportBackup() }, icon = TacoIcon.Save)
            SettingValue("Import backup", "restores from clipboard", { state.importBackup() }, icon = TacoIcon.Copy)
            SettingValue("Copy diagnostics", "a support report", { state.copyDiagnostics() }, icon = TacoIcon.Doc)
        }

        SectionLabel("About")
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TacoStrap", style = TacoType.displayBig.copy(fontSize = 40.sp), color = accent.primary)
            Text("for Android · ${state.appVersion}", style = TacoType.tnum.copy(fontSize = 13.sp), color = TacoPalette.Muted)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TacoButton("Changelog", { showChangelog = true }, kind = ButtonKind.Quiet, height = 40.dp)
                TacoButton("Licenses", { showLicenses = true }, kind = ButtonKind.Quiet, height = 40.dp)
                TacoButton("Allowlist", {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://devforum.roblox.com/t/allowlist-for-local-client-configuration-via-fast-flags/3966569")))
                    }
                }, kind = ButtonKind.Quiet, icon = TacoIcon.Globe, height = 40.dp)
            }
        }
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            title = { Text("What's new", style = TacoType.display, fontSize = 19.sp) },
            text = {
                Text(
                    "5.0 — The Comal\n" +
                        "· Hold-to-launch griddle dial with heat meter\n" +
                        "· Launch pipeline with live timeline, cancel, retry\n" +
                        "· Custom Background Image Wallpaper Support\n" +
                        "· Multi-path deployment ensuring 100% flag compatibility\n" +
                        "· Stackable, reorderable flag layers\n" +
                        "· Per-game layers, pinned-game menu carousel\n" +
                        "· Universal allowlist bypass for all flags & desyncs\n",
                    fontSize = 13.sp, color = TacoPalette.Muted, lineHeight = 19.sp
                )
            },
            confirmButton = { TacoButton("Close", { showChangelog = false }, kind = ButtonKind.Quiet, height = 40.dp) }
        )
    }
    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Licenses", style = TacoType.display, fontSize = 19.sp) },
            text = { Text("TacoStrap is built with Jetpack Compose and Shizuku (Apache License 2.0).", fontSize = 13.sp, color = TacoPalette.Muted) },
            confirmButton = { TacoButton("Close", { showLicenses = false }, kind = ButtonKind.Quiet, height = 40.dp) }
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(TacoShapes.card)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TacoShapes.card)
            .padding(vertical = 6.dp),
        content = content
    )
}

@Composable
private fun SettingSwitch(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Cream)
            Text(sub, fontSize = 12.sp, color = TacoPalette.Muted, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        TacoSwitch(checked, onChange)
    }
}

@Composable
private fun SettingValue(title: String, value: String, onClick: () -> Unit, sub: String? = null, icon: TacoIcon? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { IconCircle(icon, LocalTacoAccent.current.primary, 32.dp); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TacoPalette.Cream)
            if (sub != null) Text(sub, fontSize = 12.sp, color = TacoPalette.Muted)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value, style = TacoType.mono.copy(fontSize = 12.sp),
            color = LocalTacoAccent.current.primary, maxLines = 2,
            modifier = Modifier.weight(1.2f), textAlign = TextAlign.End
        )
        Spacer(Modifier.width(6.dp))
        TacoIcon(TacoIcon.Down, TacoPalette.Faint, 14.dp, 2.2f)
    }
}

/* ======================================================================
 *  9. APP STATE
 * ====================================================================== */

var appContext: Context? = null
private fun clipMgr(ctx: Context) = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

class TacoState(
    private val prefs: SharedPreferences,
    private val engine: Bootstrapper,
    private val allowlist: AllowlistProvider,
    val scope: CoroutineScope,
    val snackbar: SnackbarHostState
) {
    val appVersion = APP_VERSION

    val draftFlags = mutableStateMapOf<String, String>()
    val layers = mutableStateListOf<FlagLayer>()
    val games = mutableStateMapOf<String, PinnedGame>()
    private var savedFlags by mutableStateOf<Map<String, String>>(emptyMap())

    var path by mutableStateOf(prefs.getString("path2", DEFAULT_PATH) ?: DEFAULT_PATH)
    var pkg by mutableStateOf(prefs.getString("pkg", DEFAULT_PKG) ?: DEFAULT_PKG)
    var useRoot by mutableStateOf(prefs.getBoolean("root", false))
    var applyOnSave by mutableStateOf(prefs.getBoolean("applyOnSave", true))
    var restart by mutableStateOf(prefs.getBoolean("restart", true))
    var fpsCap by mutableStateOf(prefs.getBoolean("fps", false))
    var accentIdx by mutableStateOf(prefs.getInt("accent", 0).coerceIn(0, TACO_ACCENTS.lastIndex))
    var amoled by mutableStateOf(prefs.getBoolean("trueBlack", false))
    var hapticsOn by mutableStateOf(prefs.getBoolean("haptics", true))
    var reduceMotion by mutableStateOf(prefs.getBoolean("reduceMotion", false))
    var msgFlag by mutableStateOf(prefs.getBoolean("msgFlag", true))
    var msgLaunch by mutableStateOf(prefs.getBoolean("msgLaunch", true))
    var msgOther by mutableStateOf(prefs.getBoolean("msgOther", true))
    var bgImageUri by mutableStateOf(prefs.getString("bgImageUri", "") ?: "")

    var shizuku by mutableStateOf<ShizukuState>(ShizukuState.NotRunning)
    var rootAvailable by mutableStateOf<Boolean?>(null)
    var robloxVersion by mutableStateOf<String?>(null)
    var channel by mutableStateOf("release")

    var mode by mutableStateOf(LaunchMode.SAVE_LAUNCH)
    var launchState by mutableStateOf<LaunchState>(LaunchState.Idle)
    var targetGame by mutableStateOf<String?>(null)
    private var launchJob: Job? = null

    init {
        runCatching { draftFlags.putAll(parseFlags(prefs.getString("flags", "{}") ?: "{}")) }
        savedFlags = draftFlags.toMap()
        layers += FlagLayer("base", "Base flags", true, draftFlags.toMap())

        val layersJson = prefs.getString("layers2", null)
        if (layersJson != null) {
            runCatching {
                val arr = JSONArray(layersJson)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    layers += FlagLayer(
                        o.getString("id"), o.getString("name"), o.optBoolean("enabled", false),
                        parseFlags(o.getJSONObject("flags").toString())
                    )
                }
            }
        } else {
            runCatching {
                parseFlags(prefs.getString("presets", "{}") ?: "{}").forEach { (name, json) ->
                    runCatching {
                        layers += FlagLayer(UUID.randomUUID().toString(), name, false, parseFlags(json))
                    }
                }
            }
        }

        runCatching {
            val profiles = runCatching { parseFlags(prefs.getString("gameLayers", "{}") ?: "{}") }.getOrDefault(emptyMap())
            parseFlags(prefs.getString("games", "{}") ?: "{}").forEach { (name, id) ->
                games[name] = PinnedGame(id, profiles[name])
            }
        }
    }

    fun mergedFlags(extraLayer: String? = null): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        layers.forEach { if (it.enabled) out.putAll(it.flags) }
        if (extraLayer != null) layers.firstOrNull { it.name == extraLayer }?.let { out.putAll(it.flags) }
        return out
    }

    fun activeLayerName(): String =
        layers.lastOrNull { it.enabled && it.id != "base" }?.name ?: "Base flags"

    fun isAllowed(key: String): Boolean = allowlist.isAllowed(key)

    fun pendingCount(): Int {
        val keys = savedFlags.keys + draftFlags.keys
        return keys.count { savedFlags[it] != draftFlags[it] }
    }

    private fun baseLayer() = layers.first { it.id == "base" }

    private fun syncBase() {
        val b = baseLayer()
        b.flags.clear(); b.flags.putAll(draftFlags.toMap())
    }

    fun discardDraft() {
        draftFlags.clear(); draftFlags.putAll(savedFlags)
        syncBase()
        notify("Changes discarded.", "flag")
    }

    fun applyDraft() {
        savedFlags = draftFlags.toMap()
        syncBase()
        persist()
        notify("Flags saved.", "flag")
    }

    fun moveLayer(from: Int, to: Int) {
        if (from in layers.indices && to in layers.indices && from != 0 && to != 0) {
            layers.add(to, layers.removeAt(from))
            persist()
        }
    }

    fun setLayerEnabled(id: String, enabled: Boolean) {
        if (id == "base") return
        layers.firstOrNull { it.id == id }?.enabled = enabled
        persist()
    }

    fun renameLayer(id: String, name: String) {
        if (name.isEmpty()) return
        val l = layers.firstOrNull { it.id == id } ?: return
        val old = l.name
        l.name = name
        games.entries.toList().forEach { (k, g) -> if (g.profile == old) games[k] = g.copy(profile = name) }
        persist()
    }

    fun duplicateLayer(id: String) {
        layers.firstOrNull { it.id == id }?.let {
            val i = layers.indexOf(it)
            layers.add(i + 1, FlagLayer(UUID.randomUUID().toString(), it.name + " copy", false, it.flags.toMap()))
        }
        persist()
    }

    fun deleteLayer(id: String) {
        if (id == "base") return
        val l = layers.firstOrNull { it.id == id } ?: return
        layers.remove(l)
        games.entries.toList().forEach { (k, g) -> if (g.profile == l.name) games[k] = g.copy(profile = null) }
        persist()
    }

    fun saveLayerFromCurrent(name: String) {
        layers += FlagLayer(UUID.randomUUID().toString(), name, true, draftFlags.toMap())
        persist()
    }

    fun importLayerFromClipboard() {
        val ctx = appContext ?: return
        val text = clipMgr(ctx).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        val msg = runCatching {
            val m = parseFlags(text)
            layers += FlagLayer(UUID.randomUUID().toString(), "Imported layer", true, m)
            persist()
            "Imported ${m.size} flags as a layer."
        }.getOrElse { "Failed: clipboard isn't valid JSON." }
        notify(msg)
    }

    fun applyPack(p: Pack) {
        PACK_KEYS.forEach { draftFlags.remove(it) }
        draftFlags.putAll(p.sets)
        applyDraft()
    }

    fun duplicateDraftFlag(key: String) {
        draftFlags[key + "_copy"] = draftFlags[key] ?: ""
    }

    fun addGame(name: String, placeId: String) {
        games[name] = PinnedGame(placeId)
        persist()
        notify("Pinned $name.")
    }

    fun removeGame(name: String) {
        games.remove(name)
        if (targetGame == name) targetGame = null
        persist()
    }

    fun setGameProfile(name: String, profile: String?) {
        games[name]?.let { games[name] = it.copy(profile = profile) }
        persist()
    }

    fun targetLabel(): String = targetGame?.let { "$it (#${games[it]?.placeId})" } ?: "Home (Roblox app)"

    fun launchGameById(placeId: String) {
        scope.launch {
            val g = games.values.firstOrNull { it.placeId == placeId }
            val sh = engine.shizukuState()
            if (!sh.ok && !useRoot) {
                notify("Failed: Shizuku ${sh.long.lowercase()}."); return@launch
            }
            if (applyOnSave) {
                val r = engine.applyFlags(toJson(mergedFlags(g?.profile)), path, pkg, fpsCap, useRoot)
                if (r.startsWith("Failed")) { notify(r); return@launch }
            }
            notify(engine.launchGame(pkg, placeId, useRoot, restart), "launch")
        }
    }

    fun runLaunch() {
        if (launchState is LaunchState.Running) return
        launchJob?.cancel()
        val target = targetGame?.let { games[it] }
        val merged = mergedFlags(target?.profile)
        val m = mode
        val steps = mutableListOf<LaunchStep>()
        steps += LaunchStep("Check Shizuku")
        if (m != LaunchMode.VERIFY) steps += LaunchStep("Apply ${merged.size} flags")
        steps += LaunchStep("Verify")
        if (m == LaunchMode.SAVE_LAUNCH) steps += LaunchStep("Launch Roblox")
        val vIdx = steps.indexOfFirst { it.title == "Verify" }
        launchState = LaunchState.Running(steps.toList(), 0)

        launchJob = scope.launch job@{
            fun push(i: Int, status: StepStatus, detail: String = "") {
                steps[i] = steps[i].copy(status = status, detail = detail)
                launchState = LaunchState.Running(steps.toList(), i)
            }
            fun fail(i: Int, msg: String) {
                launchState = LaunchState.Failed(msg, steps.toList(), i)
            }
            try {
                val sh = engine.shizukuState()
                if (!sh.ok && !useRoot) {
                    steps[0] = steps[0].copy(status = StepStatus.Failed, detail = sh.long)
                    fail(0, "Shizuku ${sh.long.lowercase()}. Open status sheet for fix steps, or enable root.")
                    return@job
                }
                push(0, StepStatus.Done, if (useRoot) "via root" else "connected")

                if (m != LaunchMode.VERIFY) {
                    if (applyOnSave) {
                        val msg = engine.applyFlags(toJson(merged), path, pkg, fpsCap, useRoot)
                        if (msg.startsWith("Failed")) {
                            steps[1] = steps[1].copy(status = StepStatus.Failed, detail = msg)
                            fail(1, msg); return@job
                        }
                        push(1, StepStatus.Done, msg)
                    } else {
                        push(1, StepStatus.Skipped, "write-on-apply disabled in Settings")
                    }
                }

                val vmsg = engine.verify(toJson(merged), path, useRoot)
                if (vmsg.startsWith("Verify failed")) {
                    steps[vIdx] = steps[vIdx].copy(status = StepStatus.Failed, detail = vmsg)
                    fail(vIdx, vmsg); return@job
                }
                push(vIdx, StepStatus.Done, vmsg)

                val finalMsg: String
                if (m == LaunchMode.SAVE_LAUNCH) {
                    val lIdx = steps.lastIndex
                    val msg = if (target != null) engine.launchGame(pkg, target.placeId, useRoot, restart)
                    else engine.launchRoblox(pkg, useRoot, restart)
                    if (msg.startsWith("Failed")) {
                        steps[lIdx] = steps[lIdx].copy(status = StepStatus.Failed, detail = msg)
                        fail(lIdx, msg); return@job
                    }
                    push(lIdx, StepStatus.Done, msg)
                    finalMsg = if (target != null) "Launched $targetGame." else "Roblox is served."
                } else {
                    finalMsg = if (m == LaunchMode.VERIFY) "Verification complete." else "Flags applied and verified."
                }
                launchState = LaunchState.Success(finalMsg, steps.toList())
                notify(finalMsg, "launch")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                launchState = LaunchState.Failed(e.message ?: "Unknown error", steps.toList(), 0)
            }
        }
    }

    fun cancelLaunch() {
        launchJob?.cancel()
        launchState = LaunchState.Idle
    }

    fun retryLaunch() {
        cancelLaunch()
        runLaunch()
    }

    fun requestShizukuPermission() {
        runCatching { rikka.shizuku.Shizuku.requestPermission(1001) }
            .onFailure { notify("Failed: start Shizuku first.") }
    }

    fun editPkg() {
        pkg = if (pkg == DEFAULT_PKG) "com.roblox.client.chrome" else DEFAULT_PKG
        persist()
        robloxVersion = null
        notify("Package set to $pkg")
    }

    fun cyclePath() {
        path = if (path == DEFAULT_PATH) ALT_PATH else DEFAULT_PATH
        persist()
        notify("Flag file path updated.")
    }

    fun clearCache() { scope.launch { notify(engine.clearCache(pkg, useRoot)) } }

    fun importFromUrl(url: String) {
        scope.launch {
            val (ok, body) = withContext(Dispatchers.IO) { fetchText(url) }
            val msg = if (ok) runCatching {
                val m = parseFlags(body)
                draftFlags.putAll(m)
                "Imported ${m.size} flags from URL."
            }.getOrElse { "Failed: not valid JSON." } else "Failed: $body"
            notify(msg, "flag")
        }
    }

    fun exportBackup() {
        val json = JSONObject().apply {
            put("app", "TacoStrap"); put("version", APP_VERSION)
            put("flags", JSONObject(toJson(draftFlags)))
            put("layers", JSONObject().also { o ->
                layers.filter { it.id != "base" }.forEach { o.put(it.name, JSONObject(toJson(it.flags))) }
            })
            put("games", JSONObject().also { o -> games.forEach { (n, g) -> o.put(n, g.placeId) } })
        }.toString(2)
        copyText("TacoStrap backup", json)
        notify("Backup copied to clipboard.")
    }

    fun importBackup() {
        val ctx = appContext ?: return
        val text = clipMgr(ctx).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        val msg = runCatching {
            val o = JSONObject(text)
            o.optJSONObject("flags")?.let { j -> draftFlags.clear(); draftFlags.putAll(parseFlags(j.toString())) }
            o.optJSONObject("layers")?.let { j ->
                layers.removeAll { it.id != "base" }
                j.keys().forEach { n ->
                    layers += FlagLayer(UUID.randomUUID().toString(), n, false, parseFlags(j.getJSONObject(n).toString()))
                }
            }
            o.optJSONObject("games")?.let { j ->
                games.clear()
                parseFlags(j.toString()).forEach { (n, id) -> games[n] = PinnedGame(id) }
            }
            applyDraft()
            "Backup imported."
        }.getOrElse { "Failed: copy a backup to the clipboard first." }
        notify(msg)
    }

    fun copyDiagnostics() {
        copyText(
            "diagnostics",
            "TacoStrap $APP_VERSION\nShizuku: ${shizuku.long}\nRoot available: $rootAvailable (in use: $useRoot)\n" +
                "Package: $pkg (${robloxVersion ?: "?"})\nPath: $path\nFlags: ${draftFlags.size}\nLayers: ${layers.size}"
        )
        notify("Diagnostics copied.")
    }

    fun copyText(label: String, text: String) {
        appContext?.let { clipMgr(it).setPrimaryClip(ClipData.newPlainText(label, text)) }
    }

    fun notify(msg: String, kind: String = "other") {
        val enabled = when (kind) { "flag" -> msgFlag; "launch" -> msgLaunch; else -> msgOther }
        if (enabled || msg.startsWith("Failed") || msg.startsWith("Verify failed")) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg)
            }
        }
    }

    fun persist() {
        val layersArr = JSONArray()
        layers.filter { it.id != "base" }.forEach {
            layersArr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("enabled", it.enabled)
                    .put("flags", JSONObject(toJson(it.flags)))
            )
        }
        prefs.edit()
            .putString("flags", toJson(savedFlags))
            .putString("layers2", layersArr.toString())
            .putString("games", toJson(games.entries.associate { it.key to it.value.placeId }))
            .putString("gameLayers", toJson(games.entries.filter { it.value.profile != null }.associate { it.key to it.value.profile!! }))
            .putString("path2", path).putString("pkg", pkg)
            .putBoolean("root", useRoot).putBoolean("applyOnSave", applyOnSave)
            .putBoolean("restart", restart).putBoolean("fps", fpsCap)
            .putInt("accent", accentIdx).putBoolean("trueBlack", amoled)
            .putBoolean("haptics", hapticsOn).putBoolean("reduceMotion", reduceMotion)
            .putBoolean("msgFlag", msgFlag).putBoolean("msgLaunch", msgLaunch)
            .putBoolean("msgOther", msgOther)
            .putString("bgImageUri", bgImageUri)
            .apply()
    }

    suspend fun refreshLoop() {
        rootAvailable = withContext(Dispatchers.IO) { engine.rootAvailable() }
        var tick = 0
        while (currentCoroutineContext().isActive) {
            shizuku = engine.shizukuState()
            if (robloxVersion == null && (shizuku.ok || useRoot) && tick % 8 == 0) {
                robloxVersion = engine.robloxVersion(pkg, useRoot)
            }
            tick++
            delay(1500)
        }
    }
}

/* ======================================================================
 *  10. NAVIGATION + MAINACTIVITY
 * ====================================================================== */

private val TAB_ROUTES = listOf("home", "flags", "profiles", "games", "settings")
private val TAB_ICONS = listOf(
    TacoIcon.Home to "Home", TacoIcon.Flag to "Flags", TacoIcon.Layers to "Profiles",
    TacoIcon.Gamepad to "Games", TacoIcon.Sliders to "Settings"
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

    TacoTheme(state.accentIdx, state.amoled, state.reduceMotion) {
        var currentRoute by rememberSaveable { mutableStateOf("home") }
        BackHandler(enabled = currentRoute != "home") { currentRoute = "home" }
        val cs = MaterialTheme.colorScheme
        val accent = LocalTacoAccent.current
        val customBgBitmap = rememberImageBitmapFromUri(state.bgImageUri)

        Box(Modifier.fillMaxSize().background(cs.background)) {
            // Optional Custom Background Image
            if (customBgBitmap != null) {
                Image(
                    bitmap = customBgBitmap,
                    contentDescription = "Custom Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.55f
                )
                // Dark overlay to keep UI legible
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }

            Box(Modifier.fillMaxSize().tacoGrain()) {
                Crossfade(
                    targetState = currentRoute,
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 84.dp),
                    animationSpec = tween(200),
                    label = "nav"
                ) { r ->
                    when (r) {
                        "flags" -> FlagsScreen(state)
                        "profiles" -> ProfilesScreen(state)
                        "games" -> GamesScreen(state)
                        "settings" -> SettingsScreen(state)
                        else -> HomeScreen(state) { target -> currentRoute = target }
                    }
                }

                Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
                    val current = currentRoute
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(CircleShape)
                            .background(cs.surfaceVariant.copy(alpha = 0.92f))
                            .border(1.dp, cs.outlineVariant, CircleShape)
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TAB_ROUTES.forEachIndexed { i, route ->
                            val selected = current == route
                            val (icon, label) = TAB_ICONS[i]
                            val w by animateFloatAsState(
                                if (selected) 1.7f else 1f,
                                if (state.reduceMotion) tween(0) else spring(dampingRatio = 0.75f, stiffness = 420f),
                                label = "tabw"
                            )
                            val tint = if (selected) accent.primary else TacoPalette.Muted
                            Row(
                                Modifier.weight(w).height(46.dp).clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { currentRoute = route },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TacoIcon(icon, tint, 21.dp)
                                if (selected) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp)
                ) { d ->
                    Snackbar(
                        d, shape = RoundedCornerShape(16.dp),
                        containerColor = cs.surfaceVariant,
                        contentColor = cs.onSurface,
                        actionColor = accent.primary
                    )
                }
            }
        }
    }
}
