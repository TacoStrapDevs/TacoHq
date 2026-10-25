package com.example.flagmanager

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import java.util.Base64
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const val DEFAULT_PKG = "com.roblox.client"
const val DEFAULT_PATH = "/data/local/tmp/ClientAppSettings.json"
const val FPS_PROP = "debug.graphics.game_default_frame_rate.disabled"
const val FPS_FLAG = "DFIntTaskSchedulerTargetFps"
const val FPS_LIMIT_FLAG = "FFlagTaskSchedulerLimitTargetFpsTo2402"
const val QUALITY_FLAG = "FIntRomarkStartWithGraphicQualityLevel"

// ---------------------------------------------------------------------------
// Result types
// ---------------------------------------------------------------------------

enum class ShizukuState { READY, NEEDS_PERMISSION, NOT_RUNNING, UNAVAILABLE }

data class ShResult(val ok: Boolean, val out: String, val ms: Long = 0L, val code: Int = -1)

data class RobloxInfo(
    val checked: Boolean = false,
    val installed: Boolean = false,
    val version: String = "-",
    val running: Boolean = false,
    val pid: String = "",
    val uptime: String = "",
    val memMb: Int = 0
)

data class DeviceInfo(
    val model: String = "-",
    val android: String = "-",
    val sdk: String = "-",
    val battTemp: String = "-",
    val battLevel: Int = -1,
    val refresh: String = "-"
)

data class ApplyResult(val ok: Boolean, val message: String, val details: String = "")

data class JoinTarget(val placeId: String, val linkCode: String = "", val jobId: String = "")

enum class FlagType { BOOL, INT, TEXT }

// ---------------------------------------------------------------------------
// Flag JSON helpers
// ---------------------------------------------------------------------------

fun parseFlags(text: String): Map<String, String> {
    val o = JSONObject(text)
    val out = LinkedHashMap<String, String>()
    val keys = o.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        out[k] = o.get(k).toString()
    }
    return out
}

fun toJson(m: Map<String, String>): String = JSONObject(m.toSortedMap()).toString(2)

fun flagType(name: String): FlagType = when {
    name.startsWith("FFlag") || name.startsWith("DFFlag") || name.startsWith("SFFlag") -> FlagType.BOOL
    name.startsWith("FInt") || name.startsWith("DFInt") || name.startsWith("SFInt") ||
        name.startsWith("FLog") || name.startsWith("DFLog") -> FlagType.INT
    else -> FlagType.TEXT
}

private val FLAG_NAME = Regex("^(D?F|SF)(Flag|Int|String|Log)[A-Za-z0-9_]+$")

fun validFlagName(n: String): Boolean = FLAG_NAME.matches(n)

fun validPackage(s: String): Boolean = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(s)

fun isTrue(v: String?): Boolean = v != null && v.equals("true", ignoreCase = true)

// ---------------------------------------------------------------------------
// Deep links
// ---------------------------------------------------------------------------

fun buildDeepLink(t: JoinTarget): String = buildString {
    append("roblox://experiences/start?placeId=").append(t.placeId)
    if (t.linkCode.isNotBlank()) append("&linkCode=").append(t.linkCode)
    if (t.jobId.isNotBlank()) append("&gameInstanceId=").append(t.jobId)
}

fun parseJoinInput(raw: String): JoinTarget? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    if (s.all { it.isDigit() }) return JoinTarget(s)
    val place = Regex("placeId=(\\d+)").find(s)?.groupValues?.get(1)
        ?: Regex("/games/(\\d+)").find(s)?.groupValues?.get(1)
        ?: return null
    val code = Regex("(?:privateServerLinkCode|linkCode)=([A-Za-z0-9_-]+)").find(s)?.groupValues?.get(1) ?: ""
    val job = Regex("gameInstanceId=([A-Za-z0-9-]+)").find(s)?.groupValues?.get(1) ?: ""
    return JoinTarget(place, code, job)
}

fun ago(t: Long, now: Long = System.currentTimeMillis()): String {
    if (t <= 0L) return "never"
    val s = (now - t) / 1000
    return when {
        s < 45 -> "just now"
        s < 90 -> "1 min ago"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}

fun pathCandidates(pkg: String): List<String> = listOf(
    DEFAULT_PATH,
    "/sdcard/Android/data/$pkg/files/ClientSettings/ClientAppSettings.json",
    "/sdcard/Android/data/$pkg/files/exe/ClientSettings/ClientAppSettings.json",
    "/sdcard/Android/data/$pkg/files/ClientAppSettings.json"
)

// ---------------------------------------------------------------------------
// Engine: everything that talks to the system through Shizuku
// ---------------------------------------------------------------------------

object Engine {
    private var method: java.lang.reflect.Method? = null

    fun state(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.NEEDS_PERMISSION
            else -> ShizukuState.READY
        }
    } catch (e: Throwable) {
        ShizukuState.UNAVAILABLE
    }

    fun requestPermission(): Boolean = try {
        Shizuku.requestPermission(1001)
        true
    } catch (e: Throwable) {
        false
    }

    // Shizuku.newProcess is private in API 13.x, so call it by reflection.
    private fun newProcess(cmd: String): Process {
        val m = method ?: Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).also {
            it.isAccessible = true
            method = it
        }
        return m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
    }

    fun sh(cmd: String): ShResult {
        val t0 = System.currentTimeMillis()
        return try {
            val p = newProcess(cmd)
            var err = ""
            val th = Thread {
                err = try {
                    p.errorStream.bufferedReader().readText()
                } catch (e: Throwable) {
                    ""
                }
            }
            th.start()
            val out = p.inputStream.bufferedReader().readText()
            th.join(4000)
            val code = p.waitFor()
            val text = (out + (if (err.isNotBlank()) "\n" + err else "")).trim()
            ShResult(code == 0, text, System.currentTimeMillis() - t0, code)
        } catch (e: Throwable) {
            val m = (e.cause ?: e).message ?: e.toString()
            ShResult(false, m, System.currentTimeMillis() - t0, -1)
        }
    }

    /** Single-quote a string for sh. */
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    // ---- files ------------------------------------------------------------

    fun writeFile(path: String, content: String): ShResult {
        val b64 = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        val dir = path.substringBeforeLast('/', "")
        val tmp = "$path.b64"
        val mk = if (dir.isNotEmpty()) "mkdir -p ${q(dir)} && " else ""
        if (b64.length <= 90_000) {
            return sh(
                mk + "printf '%s' '$b64' > ${q(tmp)} && base64 -d < ${q(tmp)} > ${q(path)} && " +
                    "rm -f ${q(tmp)} && chmod 644 ${q(path)}"
            )
        }
        // Very large files: send in chunks so we never hit the argument size limit.
        val start = sh(mk + ": > ${q(tmp)}")
        if (!start.ok) return start
        for (chunk in b64.chunked(60_000)) {
            val r = sh("printf '%s' '$chunk' >> ${q(tmp)}")
            if (!r.ok) return r
        }
        return sh("base64 -d < ${q(tmp)} > ${q(path)} && rm -f ${q(tmp)} && chmod 644 ${q(path)}")
    }

    fun readFile(path: String): ShResult = sh("cat ${q(path)}")

    fun fileSize(path: String): Int? = sh("wc -c < ${q(path)}").out.trim().toIntOrNull()

    fun testWrite(path: String): ShResult {
        val dir = path.substringBeforeLast('/', "/")
        val probe = "$dir/.fm_probe"
        return sh("mkdir -p ${q(dir)} && touch ${q(probe)} && rm -f ${q(probe)} && echo WRITE_OK")
    }

    fun checkPaths(paths: List<String>): Map<String, Boolean> {
        val cmd = paths.joinToString("; ") {
            "if [ -e ${q(it)} ]; then echo \"Y $it\"; else echo \"N $it\"; fi"
        }
        val out = sh(cmd).out
        val map = LinkedHashMap<String, Boolean>()
        for (line in out.lines()) {
            if (line.length > 2 && (line[0] == 'Y' || line[0] == 'N')) map[line.substring(2)] = line[0] == 'Y'
        }
        for (p in paths) if (!map.containsKey(p)) map[p] = false
        return map
    }

    fun detect(pkg: String): ShResult = sh(
        "find /sdcard/Android/data/$pkg/ -maxdepth 6 \\( -iname 'ClientAppSettings*' -o -iname 'ClientSettings' \\) 2>/dev/null; " +
            "echo @@LS; ls -1 /sdcard/Android/data/$pkg/files 2>&1 | head -n 40"
    )

    // ---- Roblox actions ---------------------------------------------------

    fun launchApp(pkg: String): ShResult {
        val r = sh("monkey -p ${q(pkg)} -c android.intent.category.LAUNCHER 1")
        val bad = r.out.contains("No activities found") || r.out.contains("monkey aborted")
        return if (bad) r.copy(ok = false) else r
    }

    fun forceStop(pkg: String): ShResult = sh("am force-stop ${q(pkg)}")

    fun openDeepLink(pkg: String, url: String): ShResult {
        val r = sh("am start -a android.intent.action.VIEW -d ${q(url)} -p ${q(pkg)}")
        val bad = r.out.contains("Error", ignoreCase = true) && r.out.contains("not started", ignoreCase = true)
        return if (bad) r.copy(ok = false) else r
    }

    fun clearCache(pkg: String): ShResult = sh("rm -rf /sdcard/Android/data/$pkg/cache/*")

    fun clearLogs(): ShResult = sh("logcat -c")

    fun apply(
        pkg: String,
        path: String,
        json: String,
        disableCap: Boolean,
        stop: Boolean,
        launch: Boolean,
        wipeCache: Boolean
    ): ApplyResult {
        val w = writeFile(path, json)
        if (!w.ok) return ApplyResult(false, "Couldn't write the flag file", w.out)
        val expected = json.toByteArray(Charsets.UTF_8).size
        val size = fileSize(path)
        sh("setprop $FPS_PROP ${if (disableCap) "true" else "false"}")
        if (wipeCache) clearCache(pkg)
        if (stop) {
            forceStop(pkg)
            if (launch) sh("sleep 1")
        }
        if (launch) {
            val l = launchApp(pkg)
            if (!l.ok) return ApplyResult(false, "Flags saved, but Roblox didn't launch", l.out)
        }
        val note = if (size != null && size != expected) "Wrote $size of $expected bytes" else "Wrote $expected bytes to $path"
        return ApplyResult(true, if (launch) "Saved and launched" else "Saved", note)
    }

    fun reset(pkg: String, path: String): ShResult =
        sh("rm -f ${q(path)}; setprop $FPS_PROP false; am force-stop ${q(pkg)}")

    // ---- probes -----------------------------------------------------------

    private fun sections(out: String): Map<String, List<String>> {
        val map = LinkedHashMap<String, MutableList<String>>()
        var cur = ""
        for (line in out.lines()) {
            if (line.startsWith("@@")) {
                cur = line.substring(2).trim()
                map[cur] = mutableListOf()
            } else {
                map[cur]?.add(line)
            }
        }
        return map
    }

    fun probeRoblox(pkg: String): RobloxInfo {
        val p = q(pkg)
        val cmd = "echo @@PATH; pm path $p 2>/dev/null | head -n 1; " +
            "echo @@VER; dumpsys package $p 2>/dev/null | grep -m1 versionName; " +
            "PID=\$(pidof $p 2>/dev/null); echo @@PID; echo \"\$PID\"; " +
            "if [ -n \"\$PID\" ]; then " +
            "echo @@PS; ps -A -o PID,ETIME,RSS,NAME 2>/dev/null | grep -F $p; " +
            "echo @@MEM; dumpsys meminfo $p 2>/dev/null | grep -m1 -E 'TOTAL'; fi"
        val r = sh(cmd)
        val s = sections(r.out)
        val installed = s["PATH"].orEmpty().any { it.startsWith("package:") }
        val version = s["VER"].orEmpty().firstOrNull { it.contains("=") }?.substringAfter("=")?.trim() ?: "-"
        val pid = s["PID"].orEmpty().firstOrNull { it.isNotBlank() }?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
        var uptime = ""
        for (line in s["PS"].orEmpty()) {
            val f = line.trim().split(Regex("\\s+"))
            if (f.size >= 4 && f.last() == pkg) {
                uptime = f[1]
                break
            }
        }
        val mem = s["MEM"].orEmpty().firstOrNull()?.let {
            Regex("TOTAL(?: PSS:)?\\s+(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull()
        } ?: 0
        return RobloxInfo(
            checked = true,
            installed = installed,
            version = if (installed) version else "-",
            running = pid.isNotEmpty(),
            pid = pid,
            uptime = uptime,
            memMb = mem / 1024
        )
    }

    fun probeDevice(): DeviceInfo {
        val r = sh(
            "echo @@MODEL; getprop ro.product.model; echo @@REL; getprop ro.build.version.release; " +
                "echo @@SDK; getprop ro.build.version.sdk; echo @@BATT; dumpsys battery 2>/dev/null | grep -E 'level|temperature'; " +
                "echo @@DISP; dumpsys display 2>/dev/null | grep -m1 -oE '(fps|refreshRate)=[0-9.]+'"
        )
        val s = sections(r.out)
        fun first(k: String) = s[k].orEmpty().firstOrNull { it.isNotBlank() }?.trim() ?: "-"
        var level = -1
        var temp = "-"
        for (line in s["BATT"].orEmpty()) {
            val t = line.trim()
            if (t.startsWith("level:")) level = t.substringAfter(":").trim().toIntOrNull() ?: -1
            if (t.startsWith("temperature:")) {
                val n = t.substringAfter(":").trim().toIntOrNull()
                if (n != null) temp = String.format("%.1f\u00B0C", n / 10.0)
            }
        }
        val disp = first("DISP")
        val hz = if (disp.contains("=")) disp.substringAfter("=").toFloatOrNull()?.let { "${it.toInt()} Hz" } ?: "-" else "-"
        return DeviceInfo(first("MODEL"), first("REL"), first("SDK"), temp, level, hz)
    }

    fun logcat(level: Char, lines: Int = 1500): ShResult = sh("logcat -d -v threadtime -t $lines '*:$level'")
}

// ---------------------------------------------------------------------------
// Appearance options
// ---------------------------------------------------------------------------

data class AccentDef(val name: String, val a: Color, val b: Color)

val ACCENTS = listOf(
    AccentDef("Blue", Color(0xFF3E7BFF), Color(0xFF7BA6FF)),
    AccentDef("Taco", Color(0xFFFFB300), Color(0xFFFF7A1A)),
    AccentDef("Void", Color(0xFFB388FF), Color(0xFF7C4DFF)),
    AccentDef("Fish", Color(0xFF26D0B8), Color(0xFF00ACC1)),
    AccentDef("Frost", Color(0xFF9AD8FF), Color(0xFFC4ECFF)),
    AccentDef("Rose", Color(0xFFFF6F9C), Color(0xFFE91E63)),
    AccentDef("Mint", Color(0xFF4ADE80), Color(0xFF22C55E)),
    AccentDef("Crimson", Color(0xFFF05252), Color(0xFFB91C1C))
)

val FONTS = listOf(
    "Default" to FontFamily.Default,
    "Serif" to FontFamily.Serif,
    "Monospace" to FontFamily.Monospace,
    "Cursive" to FontFamily.Cursive
)

val THEME_MODES = listOf("Use device setting", "Dark", "Light", "AMOLED black")
val BACKGROUNDS = listOf("None", "Aurora glow", "Dusk gradient", "Dot grid")
val ANIM_LEVELS = listOf("Off", "Reduced", "Full")
val MSG_LENGTHS = listOf("Short", "Normal", "Detailed")

// ---------------------------------------------------------------------------
// FastFlag preset rows (drive the FastFlags > Presets page)
// ---------------------------------------------------------------------------

data class Choice(val label: String, val sets: Map<String, String>)

sealed class FRow(val title: String, val sub: String)

class FToggle(title: String, sub: String, val sets: Map<String, String>) : FRow(title, sub)

/** A row where exactly one choice applies. An empty `sets` map means "Automatic" (no flags written). */
class FChoice(title: String, sub: String, val choices: List<Choice>) : FRow(title, sub)

class FRange(
    title: String,
    sub: String,
    val minKey: String,
    val maxKey: String,
    val minHint: String,
    val maxHint: String
) : FRow(title, sub)

class FValue(title: String, sub: String, val key: String, val numeric: Boolean, val hint: String) : FRow(title, sub)

class FSection(val title: String, val rows: List<FRow>)

private fun texture(label: String, level: String) =
    Choice(label, mapOf("DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to level))

private fun msaa(label: String, n: String) = Choice(label, mapOf("FIntDebugForceMSAASamples" to n))

private fun fps(n: Int, label: String = "$n FPS") = Choice(
    label,
    if (n > 60) mapOf(FPS_FLAG to "$n", FPS_LIMIT_FLAG to "False") else mapOf(FPS_FLAG to "$n")
)

private fun level(n: Int) = Choice(
    "Level $n",
    mapOf(QUALITY_FLAG to "$n", "DFIntDebugFRMQualityLevelOverride" to "$n")
)

private val MESH_LOW = mapOf(
    "DFIntCSGLevelOfDetailSwitchingDistance" to "0",
    "DFIntCSGLevelOfDetailSwitchingDistanceL12" to "0",
    "DFIntCSGLevelOfDetailSwitchingDistanceL23" to "0",
    "DFIntCSGLevelOfDetailSwitchingDistanceL34" to "0"
)

private val NO_GRASS = mapOf(
    "FIntFRMMinGrassDistance" to "0",
    "FIntFRMMaxGrassDistance" to "0",
    "FIntRenderGrassDetailStrength" to "0"
)

val PRESET_SECTIONS: List<FSection> = listOf(
    FSection(
        "Graphics", listOf(
            FChoice(
                "Texture quality", "Sharper textures use more memory",
                listOf(
                    Choice("Automatic", emptyMap()),
                    texture("Lowest", "0"), texture("Low", "1"), texture("Medium", "2"), texture("High", "3")
                )
            ),
            FChoice(
                "Anti aliasing", "Smooths jagged edges",
                listOf(
                    Choice("Automatic", emptyMap()),
                    msaa("Off", "1"), msaa("2x", "2"), msaa("4x", "4"), msaa("8x", "8")
                )
            ),
            FChoice(
                "Rendering mode", "Graphics engine Roblox uses",
                listOf(
                    Choice("Automatic", emptyMap()),
                    Choice("Vulkan", mapOf("FFlagDebugGraphicsPreferVulkan" to "True")),
                    Choice("OpenGL", mapOf("FFlagDebugGraphicsPreferOpenGL" to "True"))
                )
            ),
            FToggle("Gray sky", "Replaces the sky with plain gray", mapOf("FFlagDebugSkyGray" to "True")),
            FToggle(
                "Full resolution", "Ignore display scaling for a sharper picture",
                mapOf("DFFlagDisableDPIScale" to "True")
            ),
            FToggle("Disable shadows", "Turns off Roblox shadows", mapOf("FIntRenderShadowIntensity" to "0")),
            FToggle("Disable post effects", "Removes bloom, blur and color grading", mapOf("FFlagDisablePostFx" to "True"))
        )
    ),
    FSection(
        "Performance", listOf(
            FChoice(
                "Frame rate limit", "Above 60 turns off vsync and the Android game frame rate cap",
                listOf(
                    Choice("Default", emptyMap()),
                    fps(30), fps(45), fps(60), fps(90), fps(120), fps(144), fps(165), fps(240),
                    fps(9999, "Unlimited")
                )
            ),
            FToggle("FPS counter", "Shows frame rate in the corner", mapOf("FFlagDebugDisplayFPS" to "True"))
        )
    ),
    FSection(
        "World detail", listOf(
            FToggle("Mesh detail", "Lower detail for distant objects", MESH_LOW),
            FChoice(
                "Graphics quality override", "Locks the Roblox graphics level",
                listOf(Choice("Automatic", emptyMap()), level(1), level(3), level(5), level(7), level(10))
            ),
            FChoice(
                "Grass movement", "How much grass sways",
                listOf(
                    Choice("Default", emptyMap()),
                    Choice("None", mapOf("FIntGrassMovementReducedMotionFactor" to "0"))
                )
            ),
            FToggle("Remove grass", "Hides the built in terrain grass", NO_GRASS),
            FRange(
                "Grass distance", "Set both to 0 to hide grass",
                "FIntFRMMinGrassDistance", "FIntFRMMaxGrassDistance", "100", "290"
            )
        )
    ),
    FSection(
        "Interface", listOf(
            FToggle("Remove blur", "No blur behind Roblox menus", mapOf("FIntRobloxGuiBlurIntensity" to "0")),
            FValue("Hide GUIs", "Group ID you are in", "DFIntCanHideGuiGroupId", true, "Group ID"),
            FValue("Font size", "Changes the Roblox menu font size", "FIntFontSizePadding", true, "0 to 10")
        )
    ),
    FSection(
        "Network", listOf(
            FToggle("Reduce lag spikes", "Smooths out network output", mapOf("DFIntS2PhysicsSenderRate" to "38000")),
            FToggle(
                "Faster loading", "Preloads more assets to load games faster",
                mapOf("DFIntNumAssetsMaxToPreload" to "2147483647", "DFIntAssetPreloading" to "2147483647")
            ),
            FValue("MTU size", "Bytes sent per network packet", "DFIntConnectionMTUSize", true, "1200 to 1500")
        )
    ),
    FSection(
        "Debug", listOf(
            FValue(
                "Flag state", "Shows these flag values in game, comma separated",
                "FStringDebugShowFlagState", false, "FlagA,FlagB"
            ),
            FToggle(
                "Ping breakdown", "Prints ping details to the Roblox console",
                mapOf("DFFlagDebugPrintDataPingBreakDown" to "True")
            )
        )
    )
)

// ---------------------------------------------------------------------------
// Quick setup bundles
// ---------------------------------------------------------------------------

data class Bundle(val name: String, val sub: String, val sets: Map<String, String>)

val BUNDLES = listOf(
    Bundle(
        "Balanced, recommended", "Medium textures and 2x anti aliasing",
        mapOf(
            "DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to "2",
            "FIntDebugForceMSAASamples" to "2"
        )
    ),
    Bundle(
        "Performance", "Low textures, no shadows, no post effects",
        mapOf(
            "DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to "1",
            "FIntDebugForceMSAASamples" to "1", "FFlagDisablePostFx" to "True",
            "FIntRenderShadowIntensity" to "0", "FIntRobloxGuiBlurIntensity" to "0"
        )
    ),
    Bundle(
        "Quality", "High textures and 4x anti aliasing",
        mapOf(
            "DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to "3",
            "FIntDebugForceMSAASamples" to "4"
        )
    ),
    Bundle(
        "Potato", "Everything low for weak devices",
        mapOf(
            "DFFlagTextureQualityOverrideEnabled" to "True", "DFIntTextureQualityOverride" to "0",
            "FIntDebugForceMSAASamples" to "1", "FFlagDisablePostFx" to "True",
            "FIntRenderShadowIntensity" to "0", "FIntRobloxGuiBlurIntensity" to "0",
            QUALITY_FLAG to "1", "DFIntDebugFRMQualityLevelOverride" to "1"
        ) + MESH_LOW + NO_GRASS
    )
)

// ---------------------------------------------------------------------------
// Flag library (community documented, Roblox decides which ones it accepts)
// ---------------------------------------------------------------------------

data class KnownFlag(val name: String, val sample: String, val desc: String)

val KNOWN_FLAGS = listOf(
    KnownFlag(FPS_FLAG, "120", "Target frame rate for the task scheduler"),
    KnownFlag(FPS_LIMIT_FLAG, "False", "Lets the target frame rate go above 60"),
    KnownFlag("FFlagDebugDisplayFPS", "True", "Shows the frame rate counter"),
    KnownFlag("FStringDebugShowFlagState", "DFIntTaskSchedulerTargetFps", "Shows these flag values in game"),
    KnownFlag("DFFlagTextureQualityOverrideEnabled", "True", "Enables the texture quality override"),
    KnownFlag("DFIntTextureQualityOverride", "1", "Texture quality level, 0 lowest to 3 highest"),
    KnownFlag("FIntDebugForceMSAASamples", "1", "Forces the anti aliasing sample count"),
    KnownFlag("FFlagDisablePostFx", "True", "Disables post processing effects"),
    KnownFlag("FFlagDebugSkyGray", "True", "Replaces the sky with gray"),
    KnownFlag("FIntRenderShadowIntensity", "0", "Shadow strength, 0 turns shadows off"),
    KnownFlag("FIntRobloxGuiBlurIntensity", "0", "Blur strength behind menus"),
    KnownFlag("FIntFRMMinGrassDistance", "0", "Closest distance grass is drawn"),
    KnownFlag("FIntFRMMaxGrassDistance", "0", "Farthest distance grass is drawn"),
    KnownFlag("FIntRenderGrassDetailStrength", "0", "Grass detail amount"),
    KnownFlag("FIntGrassMovementReducedMotionFactor", "0", "How much grass sways"),
    KnownFlag(QUALITY_FLAG, "5", "Starting graphics quality level"),
    KnownFlag("DFIntDebugFRMQualityLevelOverride", "5", "Locks the graphics quality level"),
    KnownFlag("DFFlagDisableDPIScale", "True", "Ignores display scaling for sharper output"),
    KnownFlag("FFlagDebugGraphicsPreferVulkan", "True", "Prefers the Vulkan renderer"),
    KnownFlag("FFlagDebugGraphicsPreferOpenGL", "True", "Prefers the OpenGL renderer"),
    KnownFlag("DFIntCSGLevelOfDetailSwitchingDistance", "0", "Mesh level of detail switch distance"),
    KnownFlag("DFIntCSGLevelOfDetailSwitchingDistanceL12", "0", "Mesh LOD distance, level 1 to 2"),
    KnownFlag("DFIntCSGLevelOfDetailSwitchingDistanceL23", "0", "Mesh LOD distance, level 2 to 3"),
    KnownFlag("DFIntCSGLevelOfDetailSwitchingDistanceL34", "0", "Mesh LOD distance, level 3 to 4"),
    KnownFlag("DFIntCanHideGuiGroupId", "0", "Group ID allowed to hide the GUI"),
    KnownFlag("FIntFontSizePadding", "1", "Extra padding for menu fonts"),
    KnownFlag("DFIntConnectionMTUSize", "1400", "Network packet size in bytes"),
    KnownFlag("DFIntS2PhysicsSenderRate", "38000", "Physics network send rate"),
    KnownFlag("DFIntNumAssetsMaxToPreload", "2147483647", "How many assets to preload"),
    KnownFlag("DFIntAssetPreloading", "2147483647", "Asset preloading amount"),
    KnownFlag("DFFlagDebugPrintDataPingBreakDown", "True", "Prints a ping breakdown to the console")
)

data class Game(
    val id: String,
    val name: String,
    val pinned: Boolean = true,
    val last: Long = 0L,
    val code: String = "",
    val job: String = ""
)

data class HistoryEntry(val id: String, val name: String, val time: Long)

private class Setting(val key: String, val get: () -> Any, val set: (Any) -> Unit)

class AppState(context: Context) {
    private val prefs = context.getSharedPreferences("fm_v3", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("fm", Context.MODE_PRIVATE)

    // ---- settings ----
    var themeMode by mutableStateOf(1)          // 0 system, 1 dark, 2 light, 3 amoled
    var accent by mutableStateOf(0)             // -1 device color
    var font by mutableStateOf(0)
    var bgStyle by mutableStateOf(1)
    var animLevel by mutableStateOf(2)          // 0 off, 1 reduced, 2 full
    var haptics by mutableStateOf(true)
    var textScale by mutableStateOf(100)
    var toastTop by mutableStateOf(false)
    var msgLength by mutableStateOf(1)
    var mLaunch by mutableStateOf(true)
    var mFlags by mutableStateOf(true)
    var mLibrary by mutableStateOf(true)
    var mCopy by mutableStateOf(true)
    var mOther by mutableStateOf(true)
    var pkg by mutableStateOf(DEFAULT_PKG)
    var path by mutableStateOf(DEFAULT_PATH)
    var restartOnSave by mutableStateOf(false)
    var stopBeforeLaunch by mutableStateOf(true)
    var disableFps by mutableStateOf(false)
    var confirmReset by mutableStateOf(true)
    var clearCacheOnLaunch by mutableStateOf(false)
    var keepAwake by mutableStateOf(false)

    // ---- data ----
    val flags = mutableStateMapOf<String, String>()
    val profiles = mutableStateMapOf<String, String>()
    var activeProfile by mutableStateOf("Default")
    var appliedJson by mutableStateOf("")
    var everApplied by mutableStateOf(false)
    val games = mutableStateListOf<Game>()
    val history = mutableStateListOf<HistoryEntry>()
    val packages = mutableStateListOf<String>()
    val shellHistory = mutableStateListOf<String>()

    private val settings: List<Setting> = listOf(
        Setting("themeMode", { themeMode }, { themeMode = (it as Number).toInt() }),
        Setting("accent", { accent }, { accent = (it as Number).toInt() }),
        Setting("font", { font }, { font = (it as Number).toInt() }),
        Setting("bgStyle", { bgStyle }, { bgStyle = (it as Number).toInt() }),
        Setting("animLevel", { animLevel }, { animLevel = (it as Number).toInt() }),
        Setting("haptics", { haptics }, { haptics = it as Boolean }),
        Setting("textScale", { textScale }, { textScale = (it as Number).toInt() }),
        Setting("toastTop", { toastTop }, { toastTop = it as Boolean }),
        Setting("msgLength", { msgLength }, { msgLength = (it as Number).toInt() }),
        Setting("mLaunch", { mLaunch }, { mLaunch = it as Boolean }),
        Setting("mFlags", { mFlags }, { mFlags = it as Boolean }),
        Setting("mLibrary", { mLibrary }, { mLibrary = it as Boolean }),
        Setting("mCopy", { mCopy }, { mCopy = it as Boolean }),
        Setting("mOther", { mOther }, { mOther = it as Boolean }),
        Setting("pkg", { pkg }, { pkg = it as String }),
        Setting("path", { path }, { path = it as String }),
        Setting("restartOnSave", { restartOnSave }, { restartOnSave = it as Boolean }),
        Setting("stopBeforeLaunch", { stopBeforeLaunch }, { stopBeforeLaunch = it as Boolean }),
        Setting("disableFps", { disableFps }, { disableFps = it as Boolean }),
        Setting("confirmReset", { confirmReset }, { confirmReset = it as Boolean }),
        Setting("clearCacheOnLaunch", { clearCacheOnLaunch }, { clearCacheOnLaunch = it as Boolean }),
        Setting("keepAwake", { keepAwake }, { keepAwake = it as Boolean })
    )

    init {
        load()
    }

    val dirty: Boolean get() = toJson(flags) != appliedJson

    private fun load() {
        val all = prefs.all
        for (s in settings) all[s.key]?.let { runCatching { s.set(it) } }
        val pj = prefs.getString("profiles", null)
        if (pj != null) {
            runCatching {
                val o = JSONObject(pj)
                o.keys().forEach { profiles[it] = o.getString(it) }
            }
        } else {
            profiles["Default"] = legacy.getString("flags", null) ?: "{}"
            legacy.getString("presets", null)?.let { s ->
                runCatching {
                    val o = JSONObject(s)
                    o.keys().forEach { profiles[it] = o.getString(it) }
                }
            }
        }
        if (profiles.isEmpty()) profiles["Default"] = "{}"
        activeProfile = prefs.getString("active", "Default") ?: "Default"
        if (!profiles.containsKey(activeProfile)) activeProfile = profiles.keys.sorted().first()
        runCatching { flags.putAll(parseFlags(profiles[activeProfile] ?: "{}")) }
        appliedJson = prefs.getString("applied", null) ?: toJson(flags)
        everApplied = prefs.getBoolean("everApplied", false)
        runCatching {
            val a = JSONArray(prefs.getString("games", "[]"))
            for (i in 0 until a.length()) {
                val g = a.getJSONObject(i)
                games.add(Game(g.getString("id"), g.getString("name"), g.optBoolean("pinned", true), g.optLong("last", 0L), g.optString("code"), g.optString("job")))
            }
        }
        runCatching {
            val a = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until a.length()) {
                val g = a.getJSONObject(i)
                history.add(HistoryEntry(g.getString("id"), g.getString("name"), g.optLong("time", 0L)))
            }
        }
        runCatching {
            val a = JSONArray(prefs.getString("packages", "[]"))
            for (i in 0 until a.length()) packages.add(a.getString(i))
        }
        if (!packages.contains(DEFAULT_PKG)) packages.add(0, DEFAULT_PKG)
        runCatching {
            val a = JSONArray(prefs.getString("shellHistory", "[]"))
            for (i in 0 until a.length()) shellHistory.add(a.getString(i))
        }
    }

    private fun gamesJson(): JSONArray {
        val a = JSONArray()
        for (g in games) a.put(JSONObject().put("id", g.id).put("name", g.name).put("pinned", g.pinned).put("last", g.last).put("code", g.code).put("job", g.job))
        return a
    }

    fun save() {
        profiles[activeProfile] = toJson(flags)
        val e = prefs.edit()
        for (s in settings) {
            when (val v = s.get()) {
                is Int -> e.putInt(s.key, v)
                is Boolean -> e.putBoolean(s.key, v)
                is String -> e.putString(s.key, v)
            }
        }
        e.putString("profiles", JSONObject(profiles.toMap()).toString())
        e.putString("active", activeProfile)
        e.putString("applied", appliedJson)
        e.putBoolean("everApplied", everApplied)
        e.putString("games", gamesJson().toString())
        val h = JSONArray()
        for (x in history) h.put(JSONObject().put("id", x.id).put("name", x.name).put("time", x.time))
        e.putString("history", h.toString())
        val p = JSONArray()
        for (x in packages) p.put(x)
        e.putString("packages", p.toString())
        val sh = JSONArray()
        for (x in shellHistory) sh.put(x)
        e.putString("shellHistory", sh.toString())
        e.apply()
    }

    // ---- flags ----
    fun matches(sets: Map<String, String>): Boolean =
        sets.isNotEmpty() && sets.all { (k, v) -> flags[k].equals(v, ignoreCase = true) }

    fun setToggle(sets: Map<String, String>, on: Boolean) {
        if (on) flags.putAll(sets) else sets.keys.forEach { flags.remove(it) }
        save()
    }

    fun pick(choices: List<Choice>, idx: Int) {
        choices.forEach { c -> c.sets.keys.forEach { flags.remove(it) } }
        flags.putAll(choices[idx].sets)
        save()
    }

    fun currentChoice(choices: List<Choice>): Int {
        val hit = choices.indexOfFirst { it.sets.isNotEmpty() && matches(it.sets) }
        if (hit >= 0) return hit
        val keys = choices.flatMap { it.sets.keys }
        return if (keys.none { flags.containsKey(it) }) 0 else -1
    }

    fun putFlag(key: String, v: String?) {
        if (v.isNullOrBlank()) flags.remove(key) else flags[key] = v.trim()
        save()
    }

    // ---- profiles ----
    fun switchProfile(name: String) {
        if (name == activeProfile || !profiles.containsKey(name)) return
        profiles[activeProfile] = toJson(flags)
        activeProfile = name
        flags.clear()
        runCatching { flags.putAll(parseFlags(profiles[name] ?: "{}")) }
        save()
    }

    fun newProfile(name: String, copy: Boolean) {
        val n = name.trim()
        if (n.isEmpty() || profiles.containsKey(n)) return
        profiles[n] = if (copy) toJson(flags) else "{}"
        save()
    }

    fun renameProfile(old: String, new: String) {
        val n = new.trim()
        if (n.isEmpty() || profiles.containsKey(n) || !profiles.containsKey(old)) return
        if (old == activeProfile) profiles[old] = toJson(flags)
        profiles[n] = profiles[old] ?: "{}"
        profiles.remove(old)
        if (activeProfile == old) activeProfile = n
        save()
    }

    fun deleteProfile(name: String) {
        if (profiles.size <= 1) return
        profiles.remove(name)
        if (activeProfile == name) {
            activeProfile = profiles.keys.sorted().first()
            flags.clear()
            runCatching { flags.putAll(parseFlags(profiles[activeProfile] ?: "{}")) }
        }
        save()
    }

    // ---- library ----
    fun addHistory(id: String, name: String) {
        history.removeAll { it.id == id }
        history.add(0, HistoryEntry(id, name, System.currentTimeMillis()))
        while (history.size > 20) history.removeAt(history.size - 1)
        val i = games.indexOfFirst { it.id == id }
        if (i >= 0) games[i] = games[i].copy(last = System.currentTimeMillis())
        save()
    }

    // ---- backup ----
    fun exportBackup(): String {
        save()
        val s = JSONObject()
        for (x in settings) s.put(x.key, x.get())
        return JSONObject()
            .put("app", "TacoStrap").put("version", 3)
            .put("settings", s)
            .put("profiles", JSONObject(profiles.toMap()))
            .put("active", activeProfile)
            .put("games", gamesJson())
            .toString(2)
    }

    /** Returns a summary. Accepts a full backup or a plain flag JSON. */
    fun importBackup(text: String): String {
        val o = JSONObject(text)
        if (o.optString("app") != "TacoStrap") {
            val f = parseFlags(text)
            flags.putAll(f)
            save()
            return "Merged ${f.size} flags"
        }
        o.optJSONObject("settings")?.let { st ->
            for (x in settings) if (st.has(x.key)) runCatching { x.set(st.get(x.key)) }
        }
        o.optJSONObject("profiles")?.let { pr ->
            pr.keys().forEach { profiles[it] = pr.getString(it) }
        }
        val a = o.optString("active", activeProfile)
        if (profiles.containsKey(a)) {
            activeProfile = a
            flags.clear()
            runCatching { flags.putAll(parseFlags(profiles[a] ?: "{}")) }
        }
        o.optJSONArray("games")?.let { arr ->
            games.clear()
            for (i in 0 until arr.length()) {
                val g = arr.getJSONObject(i)
                games.add(Game(g.getString("id"), g.getString("name"), g.optBoolean("pinned", true), g.optLong("last", 0L), g.optString("code"), g.optString("job")))
            }
        }
        save()
        return "Backup restored"
    }

    fun resetSettings() {
        themeMode = 1; accent = 0; font = 0; bgStyle = 1; animLevel = 2; haptics = true
        textScale = 100; toastTop = false; msgLength = 1
        mLaunch = true; mFlags = true; mLibrary = true; mCopy = true; mOther = true
        restartOnSave = false; stopBeforeLaunch = true; disableFps = false
        confirmReset = true; clearCacheOnLaunch = false; keepAwake = false
        save()
    }
}

// ---------------------------------------------------------------------------
// Controller: runtime state and actions
// ---------------------------------------------------------------------------

enum class Cat { Launch, Flags, Library, Copy, Other }
enum class Level { OK, WARN, ERROR }

class ToastData(val id: Long, val level: Level, val text: String)

class AppController(val ctx: Context, val state: AppState, private val scope: CoroutineScope) {
    var shizuku by mutableStateOf(ShizukuState.NOT_RUNNING)
    var info by mutableStateOf(RobloxInfo())
    var busy by mutableStateOf(false)
    var busyLabel by mutableStateOf("")
    var toast by mutableStateOf<ToastData?>(null)
    var lastOutput by mutableStateOf("")
    var pathOk by mutableStateOf<Boolean?>(null)
    var view: View? = null

    private var toastJob: Job? = null
    private val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val ready: Boolean get() = shizuku == ShizukuState.READY

    fun tick() {
        if (state.haptics) view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun say(cat: Cat, level: Level, short: String, long: String = short) {
        if (level != Level.ERROR) {
            val on = when (cat) {
                Cat.Launch -> state.mLaunch
                Cat.Flags -> state.mFlags
                Cat.Library -> state.mLibrary
                Cat.Copy -> state.mCopy
                Cat.Other -> state.mOther
            }
            if (!on) return
        }
        val text = when (state.msgLength) {
            0 -> short.substringBefore('.').take(40)
            2 -> long
            else -> short
        }
        toastJob?.cancel()
        toast = ToastData(System.nanoTime(), level, text)
        toastJob = scope.launch {
            delay(if (level == Level.ERROR) 4200L else 2600L)
            toast = null
        }
    }

    fun dismissToast() {
        toast = null
    }

    private fun job(label: String, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            busyLabel = label
            try {
                block()
            } catch (e: Throwable) {
                say(Cat.Other, Level.ERROR, "Something went wrong", e.message ?: "Unknown error")
            } finally {
                busy = false
                busyLabel = ""
            }
        }
    }

    fun refresh() {
        shizuku = Engine.state()
    }

    fun requestPermission() {
        if (!Engine.requestPermission()) say(Cat.Other, Level.WARN, "Start Shizuku first")
        refresh()
    }

    fun openShizukuApp() {
        val i = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (i != null) ctx.startActivity(i)
        else say(Cat.Other, Level.WARN, "Couldn't open Shizuku", "Open the Shizuku app yourself and start its service.")
    }

    suspend fun probeNow() {
        if (!ready) return
        info = withContext(Dispatchers.IO) { Engine.probeRoblox(state.pkg) }
    }

    fun probe() {
        scope.launch { probeNow() }
    }

    fun apply(launch: Boolean) {
        state.save()
        if (!ready) {
            say(Cat.Flags, Level.ERROR, "Shizuku isn't ready", "Saved in the app, but the flag file wasn't written. Start Shizuku and grant permission.")
            return
        }
        job(if (launch) "Applying and launching" else "Applying flags") {
            val json = toJson(state.flags)
            val f = state.flags[FPS_FLAG]?.toIntOrNull()
            val cap = state.disableFps || (f != null && f > 60)
            val stop = if (launch) state.stopBeforeLaunch else state.restartOnSave
            val r = withContext(Dispatchers.IO) {
                Engine.apply(state.pkg, state.path, json, cap, stop, launch, state.clearCacheOnLaunch)
            }
            lastOutput = r.details
            if (r.ok) {
                state.appliedJson = json
                state.everApplied = true
                state.save()
                pathOk = true
                if (launch) say(Cat.Launch, Level.OK, "Saved and launching Roblox", "${r.details}. Roblox is starting with your flags.")
                else say(Cat.Flags, Level.OK, "Flags saved", r.details)
            } else {
                pathOk = false
                say(Cat.Flags, Level.ERROR, r.message, r.details.take(160))
            }
            probeNow()
        }
    }

    fun reset() {
        if (!ready) {
            say(Cat.Flags, Level.ERROR, "Shizuku isn't ready")
            return
        }
        job("Removing flag file") {
            val r = withContext(Dispatchers.IO) { Engine.reset(state.pkg, state.path) }
            if (r.ok) {
                state.appliedJson = ""
                state.everApplied = false
                state.save()
                say(Cat.Flags, Level.OK, "Flag file removed", "Removed the flag file. Your flags are still saved in the app.")
            } else say(Cat.Flags, Level.ERROR, "Couldn't remove it", r.out.take(160))
            probeNow()
        }
    }

    fun clearCache() {
        if (!ready) {
            say(Cat.Other, Level.ERROR, "Shizuku isn't ready")
            return
        }
        job("Clearing cache") {
            val r = withContext(Dispatchers.IO) { Engine.clearCache(state.pkg) }
            if (r.ok) say(Cat.Other, Level.OK, "Cache cleared") else say(Cat.Other, Level.ERROR, "Couldn't clear cache", r.out.take(160))
        }
    }

    fun testWrite() {
        if (!ready) {
            say(Cat.Other, Level.ERROR, "Shizuku isn't ready")
            return
        }
        job("Testing write access") {
            val r = withContext(Dispatchers.IO) { Engine.testWrite(state.path) }
            pathOk = r.out.contains("WRITE_OK")
            if (pathOk == true) say(Cat.Other, Level.OK, "Folder is writable") else say(Cat.Other, Level.ERROR, "Can't write there", r.out.take(160))
        }
    }

    fun forceStop() {
        if (!ready) return
        job("Stopping Roblox") {
            withContext(Dispatchers.IO) { Engine.forceStop(state.pkg) }
            say(Cat.Launch, Level.OK, "Roblox stopped")
            probeNow()
        }
    }

    fun join(t: JoinTarget, name: String, applyFirst: Boolean) {
        val url = buildDeepLink(t)
        state.addHistory(t.placeId, name)
        job("Joining $name") {
            if (applyFirst && ready) {
                val json = toJson(state.flags)
                val f = state.flags[FPS_FLAG]?.toIntOrNull()
                val cap = state.disableFps || (f != null && f > 60)
                val r = withContext(Dispatchers.IO) { Engine.apply(state.pkg, state.path, json, cap, true, false, false) }
                if (r.ok) {
                    state.appliedJson = json
                    state.everApplied = true
                    state.save()
                }
            }
            var ok = try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(state.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: Throwable) {
                false
            }
            if (!ok && ready) ok = withContext(Dispatchers.IO) { Engine.openDeepLink(state.pkg, url).ok }
            if (ok) say(Cat.Library, Level.OK, "Joining $name", "Opening $name in Roblox.")
            else say(Cat.Library, Level.ERROR, "Couldn't open the game", "Is Roblox installed? Package: ${state.pkg}")
            probeNow()
        }
    }

    fun copy(label: String, text: String) {
        clip.setPrimaryClip(ClipData.newPlainText(label, text))
        say(Cat.Copy, Level.OK, "Copied", "Copied $label to the clipboard.")
    }

    fun paste(): String = clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()

    fun share(text: String, title: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, title))
    }

    fun diagnostics(dev: DeviceInfo?): String = buildString {
        appendLine("TacoStrap diagnostics")
        appendLine("Shizuku: $shizuku")
        appendLine("Package: ${state.pkg}  installed=${info.installed}  version=${info.version}")
        appendLine("Running: ${info.running}  pid=${info.pid}")
        appendLine("Flag file: ${state.path}  writable=$pathOk")
        appendLine("Profile: ${state.activeProfile}  flags=${state.flags.size}  unsaved=${state.dirty}")
        if (dev != null) appendLine("Device: ${dev.model}  Android ${dev.android} (SDK ${dev.sdk})")
        if (lastOutput.isNotBlank()) appendLine("Last output: $lastOutput")
    }
}

// ===========================================================================
// Theme
// ===========================================================================

@Immutable
class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val cardHigh: Color,
    val bar: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val text: Color,
    val textDim: Color,
    val line: Color,
    val ok: Color,
    val warn: Color,
    val bad: Color
)

val LocalPalette = staticCompositionLocalOf<Palette> { error("Palette not provided") }
val LocalAnim = staticCompositionLocalOf { 2 }
val LocalTick = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun ms(base: Int): Int = when (LocalAnim.current) {
    0 -> 0
    1 -> base * 6 / 10
    else -> base
}

private fun makePalette(dark: Boolean, amoled: Boolean, a1: Color, a2: Color): Palette {
    val onA = if (a1.luminance() > 0.5f) Color(0xFF0E1015) else Color.White
    return if (dark) {
        val bg = if (amoled) Color(0xFF000000) else Color(0xFF151518)
        val card = if (amoled) Color(0xFF111114) else Color(0xFF232327)
        val high = if (amoled) Color(0xFF1B1B1F) else Color(0xFF2E2E33)
        val bar = if (amoled) Color(0xFF0D0D10) else Color(0xFF303034)
        Palette(
            true, bg, card, high, bar, a1, a2, onA, lerp(card, a1, 0.30f),
            Color(0xFFEDEDF1), Color(0xFFB6B6BF), Color(0xFF3E3E45),
            Color(0xFF4ADE80), Color(0xFFFFC107), Color(0xFFFF5C5C)
        )
    } else {
        Palette(
            false, Color(0xFFF4F4F8), Color.White, Color(0xFFEBEBF1), Color.White, a1, a2, onA,
            lerp(Color.White, a1, 0.18f), Color(0xFF1A1B1F), Color(0xFF5F6068), Color(0xFFD9D9E1),
            Color(0xFF16A34A), Color(0xFFD97706), Color(0xFFDC2626)
        )
    }
}

@Composable
private fun ac(x: Color, d: Int): Color = animateColorAsState(x, tween(d), label = "pal").value

@Composable
private fun animatedPalette(p: Palette): Palette {
    val d = ms(450)
    return Palette(
        p.dark, ac(p.bg, d), ac(p.card, d), ac(p.cardHigh, d), ac(p.bar, d), ac(p.accent, d), ac(p.accent2, d),
        ac(p.onAccent, d), ac(p.accentSoft, d), ac(p.text, d), ac(p.textDim, d), ac(p.line, d), p.ok, p.warn, p.bad
    )
}

private fun schemeFor(p: Palette): ColorScheme = if (p.dark) darkColorScheme(
    primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.text,
    secondary = p.accent2, onSecondary = p.onAccent, background = p.bg, onBackground = p.text,
    surface = p.card, onSurface = p.text, surfaceVariant = p.cardHigh, onSurfaceVariant = p.textDim,
    outline = p.line, error = p.bad
) else lightColorScheme(
    primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.text,
    secondary = p.accent2, onSecondary = p.onAccent, background = p.bg, onBackground = p.text,
    surface = p.card, onSurface = p.text, surfaceVariant = p.cardHigh, onSurfaceVariant = p.textDim,
    outline = p.line, error = p.bad
)

private fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

private fun accentColors(ctx: Context, idx: Int, dark: Boolean): Pair<Color, Color> {
    if (idx < 0 && Build.VERSION.SDK_INT >= 31) {
        val s = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        return Pair(s.primary, s.tertiary)
    }
    val a = ACCENTS[idx.coerceIn(0, ACCENTS.size - 1)]
    return Pair(a.a, a.b)
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun TacoTheme(state: AppState, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val sysDark = isSystemInDarkTheme()
    val dark = when (state.themeMode) {
        0 -> sysDark
        2 -> false
        else -> true
    }
    val (a1, a2) = accentColors(ctx, state.accent, dark)
    val pal = animatedPalette(makePalette(dark, state.themeMode == 3, a1, a2))
    val fam = FONTS.getOrNull(state.font)?.second ?: FontFamily.Default
    val typo = remember(fam) { Typography().withFont(fam) }
    val scheme = schemeFor(pal)
    val base = LocalDensity.current
    val dens = Density(base.density, base.fontScale * state.textScale / 100f)

    val view = LocalView.current
    SideEffect {
        val w = view.context.findActivity()?.window
        if (w != null) {
            w.statusBarColor = android.graphics.Color.TRANSPARENT
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalPalette provides pal,
        LocalAnim provides state.animLevel,
        LocalDensity provides dens
    ) {
        MaterialTheme(colorScheme = scheme, typography = typo) {
            CompositionLocalProvider(LocalContentColor provides pal.text) {
                ProvideTextStyle(typo.bodyLarge, content)
            }
        }
    }
}

@Composable
fun AppBackground(style: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val animate = LocalAnim.current > 0
    val inf = rememberInfiniteTransition(label = "bg")
    val t1 = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "t1")
    val t2 = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "t2")
    Canvas(modifier.fillMaxSize()) {
        drawRect(p.bg)
        when (style) {
            1 -> {
                val a = if (animate) t1.value else 0.4f
                val b = if (animate) t2.value else 0.6f
                drawRect(
                    Brush.radialGradient(
                        listOf(p.accent.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(size.width * (0.1f + 0.8f * a), size.height * (0.08f + 0.3f * b)),
                        radius = size.minDimension * 0.95f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(p.accent2.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * (0.9f - 0.7f * b), size.height * (0.85f - 0.3f * a)),
                        radius = size.minDimension * 0.8f
                    )
                )
            }
            2 -> drawRect(
                Brush.verticalGradient(
                    listOf(p.accent.copy(alpha = 0.2f), Color.Transparent),
                    endY = size.height * 0.65f
                )
            )
            3 -> {
                val step = 26.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(p.text.copy(alpha = 0.07f), 1.2.dp.toPx(), Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            else -> {}
        }
    }
}

// ===========================================================================
// Icons (drawn with Canvas, no extra dependency)
// ===========================================================================

enum class Ic {
    Home, Library, Tools, Flag, Gear, Check, Close, ChevronRight, ChevronDown, Play, Plus, Trash, Search,
    Refresh, Warning, Info, Copy, Share, Terminal, Download, Bolt, Bookmark, Pulse, Edit, Clock, Folder,
    Bell, Gamepad, Sliders, Shield
}

private class PB(val p: Path, val s: Float) {
    fun m(x: Number, y: Number) = p.moveTo(x.toFloat() * s, y.toFloat() * s)
    fun l(x: Number, y: Number) = p.lineTo(x.toFloat() * s, y.toFloat() * s)
    fun q(a: Number, b: Number, c: Number, d: Number) =
        p.quadraticBezierTo(a.toFloat() * s, b.toFloat() * s, c.toFloat() * s, d.toFloat() * s)
    fun z() = p.close()
}

private class Pen(val d: DrawScope, val c: Color, val s: Float, val w: Float) {
    val st = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun line(a: Number, b: Number, x: Number, y: Number) = d.drawLine(
        c, Offset(a.toFloat() * s, b.toFloat() * s), Offset(x.toFloat() * s, y.toFloat() * s),
        strokeWidth = w, cap = StrokeCap.Round
    )

    fun circle(x: Number, y: Number, r: Number) =
        d.drawCircle(c, r.toFloat() * s, Offset(x.toFloat() * s, y.toFloat() * s), style = st)

    fun dot(x: Number, y: Number, r: Number) =
        d.drawCircle(c, r.toFloat() * s, Offset(x.toFloat() * s, y.toFloat() * s))

    fun rect(x: Number, y: Number, w2: Number, h: Number, r: Number) = d.drawRoundRect(
        c, Offset(x.toFloat() * s, y.toFloat() * s), Size(w2.toFloat() * s, h.toFloat() * s),
        CornerRadius(r.toFloat() * s), style = st
    )

    fun path(fill: Boolean = false, build: PB.() -> Unit) {
        val p = Path()
        PB(p, s).build()
        d.drawPath(p, c, style = if (fill) Fill else st)
    }

    fun draw(ic: Ic) {
        when (ic) {
            Ic.Home -> {
                path { m(4, 11); l(12, 4); l(20, 11) }
                path { m(6, 10); l(6, 20); l(10, 20); l(10, 15); l(14, 15); l(14, 20); l(18, 20); l(18, 10) }
            }
            Ic.Library -> {
                rect(3.5, 3.5, 7, 7, 1.8); rect(3.5, 13.5, 7, 7, 1.8); rect(13.5, 13.5, 7, 7, 1.8)
                path { m(17, 2.5); l(21.5, 7); l(17, 11.5); l(12.5, 7); z() }
            }
            Ic.Tools -> {
                circle(7, 7, 3); line(7, 10, 7, 20.5); rect(14.5, 3, 5, 6, 1.5); line(17, 9, 17, 20.5)
            }
            Ic.Flag -> {
                line(5, 3, 5, 21)
                path { m(5, 4.5); l(19, 4.5); l(15, 9.5); l(19, 14.5); l(5, 14.5) }
            }
            Ic.Gear -> {
                circle(12, 12, 3.2); circle(12, 12, 6.2)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    line(12 + 6.2 * cos(a), 12 + 6.2 * sin(a), 12 + 9 * cos(a), 12 + 9 * sin(a))
                }
            }
            Ic.Check -> path { m(5, 12.5); l(10, 17.5); l(19, 7) }
            Ic.Close -> { line(6, 6, 18, 18); line(18, 6, 6, 18) }
            Ic.ChevronRight -> path { m(9, 6); l(15, 12); l(9, 18) }
            Ic.ChevronDown -> path { m(6, 9); l(12, 15); l(18, 9) }
            Ic.Play -> path(fill = true) { m(7, 4.5); l(19, 12); l(7, 19.5); z() }
            Ic.Plus -> { line(12, 5, 12, 19); line(5, 12, 19, 12) }
            Ic.Trash -> {
                line(4, 7, 20, 7)
                path { m(9, 7); l(9, 4.5); l(15, 4.5); l(15, 7) }
                path { m(6, 7); l(7, 20); l(17, 20); l(18, 7) }
                line(10, 10.5, 10, 17); line(14, 10.5, 14, 17)
            }
            Ic.Search -> { circle(10.5, 10.5, 6.5); line(15.5, 15.5, 20.5, 20.5) }
            Ic.Refresh -> {
                d.drawArc(c, -40f, 290f, false, Offset(4f * s, 4f * s), Size(16f * s, 16f * s), style = st)
                val a = Math.toRadians(250.0)
                val px = 12 + 8 * cos(a)
                val py = 12 + 8 * sin(a)
                path(fill = true) {
                    m(px - 3.4 * sin(a), py + 3.4 * cos(a))
                    l(px + 2.6 * cos(a), py + 2.6 * sin(a))
                    l(px - 2.6 * cos(a), py - 2.6 * sin(a))
                    z()
                }
            }
            Ic.Warning -> {
                path { m(12, 3.5); l(21.5, 20); l(2.5, 20); z() }
                line(12, 9.5, 12, 14.5); dot(12, 17.3, 1.1)
            }
            Ic.Info -> { circle(12, 12, 9); line(12, 11, 12, 16.5); dot(12, 7.8, 1.1) }
            Ic.Copy -> {
                rect(8, 8, 12, 12, 2.2)
                path { m(16, 8); l(16, 5.5); q(16, 4, 14.5, 4); l(5.5, 4); q(4, 4, 4, 5.5); l(4, 14.5); q(4, 16, 5.5, 16); l(8, 16) }
            }
            Ic.Share -> {
                circle(6, 12, 2.5); circle(18, 6, 2.5); circle(18, 18, 2.5)
                line(8.2, 10.8, 15.8, 7.2); line(8.2, 13.2, 15.8, 16.8)
            }
            Ic.Terminal -> {
                rect(3, 4.5, 18, 15, 3)
                path { m(7, 9.5); l(10.5, 12.5); l(7, 15.5) }
                line(12.5, 15.5, 17, 15.5)
            }
            Ic.Download -> {
                line(12, 4, 12, 15)
                path { m(7, 10.5); l(12, 15.5); l(17, 10.5) }
                line(5, 20, 19, 20)
            }
            Ic.Bolt -> path(fill = true) { m(13, 2.5); l(5, 13.5); l(11, 13.5); l(10, 21.5); l(19, 10); l(13, 10); z() }
            Ic.Bookmark -> path { m(6, 3.5); l(18, 3.5); l(18, 21); l(12, 17); l(6, 21); z() }
            Ic.Pulse -> path { m(2.5, 12); l(7, 12); l(10, 5); l(14, 19); l(17, 12); l(21.5, 12) }
            Ic.Edit -> {
                path { m(4, 20); l(4, 16); l(16, 4); l(20, 8); l(8, 20); z() }
                line(13.5, 6.5, 17.5, 10.5)
            }
            Ic.Clock -> { circle(12, 12, 9); path { m(12, 7); l(12, 12); l(15.5, 14) } }
            Ic.Folder -> path {
                m(3, 18); l(3, 6.5); q(3, 5, 4.5, 5); l(9, 5); l(11.5, 7.5); l(19.5, 7.5); q(21, 7.5, 21, 9)
                l(21, 18); q(21, 19.5, 19.5, 19.5); l(4.5, 19.5); q(3, 19.5, 3, 18); z()
            }
            Ic.Bell -> {
                path { m(6.5, 17.5); l(6.5, 11); q(6.5, 5.5, 12, 5.5); q(17.5, 5.5, 17.5, 11); l(17.5, 17.5) }
                line(4.5, 17.5, 19.5, 17.5); line(10, 20.5, 14, 20.5)
            }
            Ic.Gamepad -> {
                rect(2.5, 7, 19, 11, 5.5); line(7.5, 10.5, 7.5, 14.5); line(5.5, 12.5, 9.5, 12.5)
                dot(15.5, 11.5, 1); dot(18, 13.8, 1)
            }
            Ic.Sliders -> {
                line(4, 7, 6.7, 7); line(11.3, 7, 20, 7); circle(9, 7, 2.3)
                line(4, 17, 12.7, 17); line(17.3, 17, 20, 17); circle(15, 17, 2.3)
            }
            Ic.Shield -> path { m(12, 3); l(19.5, 6); l(19.5, 12); q(19.5, 18, 12, 21); q(4.5, 18, 4.5, 12); l(4.5, 6); z() }
        }
    }
}

@Composable
fun Icon24(ic: Ic, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        Pen(this, tint, s, 2f * s).draw(ic)
    }
}

// ===========================================================================
// Building blocks
// ===========================================================================

@Composable
fun Modifier.bounce(enabled: Boolean = true, down: Float = 0.97f, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) down else 1f, spring(dampingRatio = 0.55f, stiffness = 650f), label = "bounce")
    val tick = LocalTick.current
    return this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled) {
            tick()
            onClick()
        }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.card,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(24.dp)).background(color),
        content = content
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text, Modifier.padding(start = 14.dp, top = 10.dp, bottom = 0.dp),
        color = LocalPalette.current.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun PageHeader(title: String, sub: String? = null) {
    val p = LocalPalette.current
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 4.dp)) {
        Text(title, fontSize = 36.sp, fontWeight = FontWeight.Medium)
        if (sub != null) Text(sub, fontSize = 16.sp, color = p.textDim, lineHeight = 22.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun SettingRow(
    title: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val p = LocalPalette.current
    val base = if (onClick != null) modifier.fillMaxWidth().bounce(enabled, 0.985f, onClick) else modifier.fillMaxWidth()
    Row(base.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (enabled) p.text else p.textDim)
            if (sub != null) Text(sub, fontSize = 13.5.sp, color = p.textDim, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun ToggleRow(title: String, sub: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    SettingRow(title, sub, onClick = { onChange(!checked) }, enabled = enabled) {
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun NavRow(title: String, sub: String? = null, icon: Ic? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    SettingRow(
        title, sub, onClick = onClick,
        leading = if (icon != null) ({ Icon24(icon, p.accent) }) else null,
        trailing = { Icon24(Ic.ChevronRight, p.textDim, size = 20.dp) }
    )
}

@Composable
fun ValueBox(text: String, modifier: Modifier = Modifier, active: Boolean = false) {
    val p = LocalPalette.current
    Box(
        modifier.widthIn(min = 88.dp).clip(RoundedCornerShape(14.dp)).background(p.cardHigh).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (active) p.accent else p.textDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ValueRow(title: String, sub: String?, value: String, active: Boolean = false, onClick: () -> Unit) {
    SettingRow(title, sub, onClick = onClick) { ValueBox(value, active = active) }
}

enum class Btn { Primary, Tonal, Outline, Danger, Text }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Btn = Btn.Primary,
    icon: Ic? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 48.dp
) {
    val p = LocalPalette.current
    val bg = when (style) {
        Btn.Primary -> p.accent
        Btn.Tonal -> p.accentSoft
        Btn.Danger -> p.bad.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val fg = when (style) {
        Btn.Primary -> p.onAccent
        Btn.Danger -> p.bad
        else -> p.accent
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier.height(height).alpha(if (enabled) 1f else 0.45f).clip(shape)
            .bounce(enabled && !loading, 0.95f, onClick).background(bg)
            .then(if (style == Btn.Outline) Modifier.border(1.5.dp, p.line, shape) else Modifier)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon24(icon, fg, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (selected) p.accent else p.cardHigh, tween(ms(220)), label = "pillbg")
    val fg by animateColorAsState(if (selected) p.onAccent else p.text, tween(ms(220)), label = "pillfg")
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50)).bounce(true, 0.94f, onClick).background(bg).padding(horizontal = 18.dp, vertical = 10.dp),
        color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium
    )
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

data class TabItem(val label: String, val icon: Ic)

@Composable
fun TabStrip(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val xs = remember(items.size) { mutableStateListOf<Float>().also { l -> repeat(items.size) { l.add(0f) } } }
    val ws = remember(items.size) { mutableStateListOf<Float>().also { l -> repeat(items.size) { l.add(0f) } } }
    var viewport by remember { mutableStateOf(0) }
    val padPx = with(density) { 14.dp.toPx() }
    val ix by animateFloatAsState(xs.getOrElse(selected) { 0f } + padPx, spring(dampingRatio = 0.8f, stiffness = 380f), label = "ix")
    val iw by animateFloatAsState((ws.getOrElse(selected) { 0f } - 2 * padPx).coerceAtLeast(0f), spring(dampingRatio = 0.8f, stiffness = 380f), label = "iw")

    LaunchedEffect(selected, xs.getOrElse(selected) { 0f }, viewport) {
        val x = xs.getOrElse(selected) { 0f }
        val w = ws.getOrElse(selected) { 0f }
        if (viewport > 0 && w > 0f) scroll.animateScrollTo((x + w / 2f - viewport / 2f).toInt().coerceIn(0, scroll.maxValue))
    }

    Column(Modifier.fillMaxWidth().onSizeChanged { viewport = it.width }) {
        Box(Modifier.horizontalScroll(scroll)) {
            Row {
                items.forEachIndexed { i, item ->
                    val on = i == selected
                    val col by animateColorAsState(if (on) p.accent else p.text, tween(ms(200)), label = "tabc")
                    Column(
                        Modifier
                            .onGloballyPositioned {
                                val nx = it.positionInParent().x
                                val nw = it.size.width.toFloat()
                                if (xs[i] != nx) xs[i] = nx
                                if (ws[i] != nw) ws[i] = nw
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .bounce(true, 0.94f) { onSelect(i) }
                            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon24(item.icon, col)
                        Spacer(Modifier.height(6.dp))
                        Text(item.label, color = col, fontSize = 16.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomStart).offset { IntOffset(ix.roundToInt(), 0) }
                    .width(with(density) { iw.toDp() }).height(3.dp)
                    .clip(RoundedCornerShape(50)).background(p.accent)
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line.copy(alpha = 0.5f)))
    }
}

@Composable
fun AppField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String = "",
    singleLine: Boolean = true,
    mono: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    minHeight: Dp = 56.dp
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) p.accent else p.line, tween(ms(180)), label = "fb")
    val style = TextStyle(color = p.text, fontSize = 16.sp, fontFamily = if (mono) FontFamily.Monospace else null)
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style,
        singleLine = singleLine,
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Column(
                Modifier.fillMaxWidth().heightIn(min = minHeight).clip(shape).background(p.cardHigh)
                    .border(1.5.dp, border, shape).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (label != null) Text(label, fontSize = 12.sp, color = if (focused) p.accent else p.textDim)
                Box {
                    if (value.isEmpty() && hint.isNotEmpty()) Text(hint, color = p.textDim.copy(alpha = 0.6f), fontSize = 16.sp)
                    inner()
                }
            }
        }
    )
}

@Composable
fun MiniField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    BasicTextField(
        value = value,
        onValueChange = { s -> if (s.all { it.isDigit() } && s.length <= 10) onChange(s) },
        textStyle = TextStyle(color = p.text, fontSize = 17.sp),
        singleLine = true,
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier.widthIn(min = 76.dp).clip(RoundedCornerShape(16.dp)).background(p.cardHigh).padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) Text(hint, color = p.textDim, fontSize = 17.sp)
                inner()
            }
        }
    )
}

@Composable
fun Appear(index: Int, seen: MutableSet<Int>, content: @Composable () -> Unit) {
    val anim = LocalAnim.current
    var vis by remember { mutableStateOf(anim == 0 || index in seen) }
    LaunchedEffect(Unit) {
        if (!vis) {
            delay(minOf(index, 8) * 45L)
            vis = true
            seen.add(index)
        }
    }
    AnimatedVisibility(vis, enter = fadeIn(tween(ms(350))) + slideInVertically(tween(ms(350))) { it / 8 }) { content() }
}

@Composable
fun EmptyState(icon: Ic, title: String, sub: String) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(p.accentSoft), contentAlignment = Alignment.Center) {
            Icon24(icon, p.accent, size = 32.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, fontSize = 14.sp, color = p.textDim, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun LogoMark(size: Dp = 46.dp) {
    val p = LocalPalette.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3.4f)).background(Brush.linearGradient(listOf(p.accent, p.accent2))),
        contentAlignment = Alignment.Center
    ) { Text("\uD83C\uDF2E", fontSize = (size.value * 0.5f).sp) }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, icon: Ic? = null) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(p.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon24(icon, p.accent, size = 16.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(label, fontSize = 12.sp, color = p.textDim)
        }
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun StatusOrb(color: Color, icon: Ic, busy: Boolean, size: Dp = 72.dp) {
    val p = LocalPalette.current
    val inf = rememberInfiniteTransition(label = "orb")
    val spin = inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "spin")
    val sweep by animateFloatAsState(if (busy) 100f else 360f, tween(ms(600)), label = "sweep")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 5.dp.toPx()
            val tl = Offset(w / 2, w / 2)
            val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(p.line, 0f, 360f, false, tl, sz, style = Stroke(w))
            drawArc(color, if (busy) spin.value else -90f, sweep, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Icon24(icon, color, size = size / 2.4f)
    }
}

@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
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
        drawPath(fill, Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, p.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ===========================================================================
// Dialogs and toast
// ===========================================================================

@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "Cancel",
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val p = LocalPalette.current
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(shown, enter = fadeIn(tween(ms(180))) + scaleIn(initialScale = 0.92f, animationSpec = tween(ms(240)))) {
            Column(
                Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(p.card).padding(24.dp)
            ) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                content()
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AppButton(dismissText, onDismiss, style = Btn.Text)
                    if (confirmText != null && onConfirm != null) {
                        Spacer(Modifier.width(6.dp))
                        AppButton(confirmText, onConfirm, style = if (danger) Btn.Danger else Btn.Primary)
                    }
                }
            }
        }
    }
}

@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    AppDialog(title, onDismiss) {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).bounce(true, 0.98f) { onPick(i); onDismiss() }
                        .padding(horizontal = 8.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) p.accent else p.line, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(11.dp).clip(CircleShape).background(p.accent))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(o, fontSize = 17.sp, color = if (on) p.accent else p.text, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun InputDialog(
    title: String,
    initial: String,
    label: String,
    hint: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
    confirmText: String = "Save",
    validate: (String) -> String? = { null },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf(initial) }
    val err = validate(text)
    AppDialog(title, onDismiss, confirmText, { if (err == null) { onConfirm(text); onDismiss() } }) {
        AppField(text, { text = it }, label = label, hint = hint, singleLine = !multiline, keyboard = keyboard, mono = multiline, minHeight = if (multiline) 160.dp else 56.dp)
        if (err != null && text.isNotEmpty()) Text(err, color = p.bad, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmText: String, danger: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(title, onDismiss, confirmText, { onConfirm(); onDismiss() }, danger = danger) {
        Text(text, color = LocalPalette.current.textDim, fontSize = 16.sp, lineHeight = 22.sp)
    }
}

@Composable
fun ToastHost(c: AppController, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val t = c.toast
    var last by remember { mutableStateOf<ToastData?>(null) }
    LaunchedEffect(t) { if (t != null) last = t }
    val top = c.state.toastTop
    AnimatedVisibility(
        visible = t != null,
        modifier = modifier,
        enter = fadeIn(tween(ms(200))) + slideInVertically(spring(dampingRatio = 0.75f)) { if (top) -it else it },
        exit = fadeOut(tween(ms(160))) + slideOutVertically(tween(ms(200))) { if (top) -it else it }
    ) {
        val d = last
        if (d != null) {
            val col = when (d.level) {
                Level.OK -> p.ok
                Level.WARN -> p.warn
                Level.ERROR -> p.bad
            }
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().shadow(10.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
                    .background(p.cardHigh).bounce(true, 0.98f) { c.dismissToast() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(col.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon24(if (d.level == Level.OK) Ic.Check else if (d.level == Level.WARN) Ic.Warning else Ic.Close, col, size = 18.dp)
                }
                Spacer(Modifier.width(12.dp))
                Text(d.text, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ===========================================================================
// Activity and root
// ===========================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { App() }
    }
}

enum class Dest(val label: String, val icon: Ic) {
    Home("Home", Ic.Home),
    Library("Library", Ic.Library),
    Tools("Tools", Ic.Tools),
    Flags("FastFlags", Ic.Flag),
    Settings("Settings", Ic.Gear)
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { AppState(ctx.applicationContext) }
    val ctrl = remember { AppController(ctx, state, scope) }
    val view = LocalView.current
    SideEffect { ctrl.view = view }
    val tick = remember { { ctrl.tick() } }
    TacoTheme(state) {
        CompositionLocalProvider(LocalTick provides tick) { Root(ctrl) }
    }
}

@Composable
fun Root(c: AppController) {
    val s = c.state
    var tab by remember { mutableStateOf(Dest.Home) }
    var prev by remember { mutableStateOf(Dest.Home) }
    val lHome = rememberLazyListState()
    val lLib = rememberLazyListState()
    val lTools = rememberLazyListState()
    val lFlags = rememberLazyListState()
    val lSet = rememberLazyListState()
    val dir = if (tab.ordinal >= prev.ordinal) 1 else -1
    val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val act = LocalContext.current.findActivity()

    fun go(d: Dest) {
        if (d != tab) {
            prev = tab
            tab = d
        }
    }

    BackHandler(enabled = tab != Dest.Home) { go(Dest.Home) }

    LaunchedEffect(Unit) {
        while (true) {
            c.refresh()
            delay(2000)
        }
    }
    LaunchedEffect(c.ready, s.pkg) { if (c.ready) c.probeNow() }
    LaunchedEffect(s.keepAwake) {
        val w = act?.window
        if (w != null) {
            if (s.keepAwake) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground(s.bgStyle)
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                Dest.values().forEach { d ->
                    AnimatedVisibility(
                        visible = d == tab,
                        enter = fadeIn(tween(ms(240))) + slideInHorizontally(tween(ms(280))) { (it * 0.1f).toInt() * dir },
                        exit = fadeOut(tween(ms(120))) + slideOutHorizontally(tween(ms(200))) { -(it * 0.1f).toInt() * dir }
                    ) {
                        when (d) {
                            Dest.Home -> HomeScreen(c, lHome) { go(it) }
                            Dest.Library -> LibraryScreen(c, lLib)
                            Dest.Tools -> ToolsScreen(c, lTools)
                            Dest.Flags -> FlagsScreen(c, lFlags)
                            Dest.Settings -> SettingsScreen(c, lSet)
                        }
                    }
                }
            }
            AnimatedVisibility(!imeUp) { BottomChrome(c, tab) { go(it) } }
        }
        ToastHost(
            c,
            Modifier.align(if (s.toastTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(if (s.toastTop) Modifier.statusBarsPadding().padding(top = 8.dp) else Modifier.navigationBarsPadding().padding(bottom = 150.dp))
        )
    }
}

@Composable
fun BottomChrome(c: AppController, tab: Dest, onTab: (Dest) -> Unit) {
    val p = LocalPalette.current
    val s = c.state
    var pkgDlg by remember { mutableStateOf(false) }
    var addDlg by remember { mutableStateOf(false) }
    val label = if (s.pkg == DEFAULT_PKG) "Roblox" else s.pkg.substringAfterLast('.')

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(p.bar).navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppButton("$label \u25BE", { pkgDlg = true }, style = Btn.Tonal)
            AppButton("Save and Launch", { c.apply(true) }, Modifier.weight(1.4f), loading = c.busy)
            Box(Modifier.weight(1f)) {
                AppButton("Save", { c.apply(false) }, Modifier.fillMaxWidth(), enabled = !c.busy)
                if (s.dirty) Box(Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(p.warn))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Dest.values().forEach { d -> NavItem(d, d == tab, Modifier.weight(1f)) { onTab(d) } }
        }
    }

    if (pkgDlg) {
        val opts = s.packages.toList() + "Add package\u2026"
        ChoiceDialog("Launch target", opts, s.packages.indexOf(s.pkg), {
            if (it == s.packages.size) addDlg = true
            else {
                s.pkg = s.packages[it]
                s.save()
                c.probe()
            }
        }, { pkgDlg = false })
    }
    if (addDlg) {
        InputDialog(
            "Add package", "", "Package name", "com.roblox.client", confirmText = "Add",
            validate = { if (validPackage(it)) null else "Not a valid package name" },
            onConfirm = {
                if (!s.packages.contains(it)) s.packages.add(it)
                s.pkg = it
                s.save()
                c.probe()
            },
            onDismiss = { addDlg = false }
        )
    }
}

@Composable
private fun NavItem(d: Dest, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val w by animateDpAsState(if (selected) 64.dp else 34.dp, spring(dampingRatio = 0.7f, stiffness = 420f), label = "navw")
    val bgA by animateFloatAsState(if (selected) 1f else 0f, tween(ms(220)), label = "navbg")
    val col by animateColorAsState(if (selected) p.accent else p.text, tween(ms(200)), label = "navc")
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).bounce(true, 0.92f, onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.width(w).height(32.dp).clip(RoundedCornerShape(50)).background(p.accentSoft.copy(alpha = bgA)),
            contentAlignment = Alignment.Center
        ) { Icon24(d.icon, col) }
        Text(d.label, fontSize = 12.sp, color = col, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun IconBtn(icon: Ic, tint: Color? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(42.dp).clip(CircleShape).bounce(true, 0.88f, onClick), contentAlignment = Alignment.Center) {
        Icon24(icon, tint ?: p.textDim, size = 22.dp)
    }
}

@Composable
private fun Screen(list: LazyListState, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun GameAvatar(id: String, name: String, size: Int = 46) {
    val hue = (id.hashCode().toLong().let { if (it < 0) -it else it } % 360).toFloat()
    val a = Color.hsv(hue, 0.55f, 0.95f)
    val b = Color.hsv((hue + 40f) % 360f, 0.7f, 0.75f)
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
        Text(name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.4f).sp)
    }
}

// ===========================================================================
// Home
// ===========================================================================

@Composable
private fun CheckRow(title: String, sub: String, level: Int, action: String? = null, o
