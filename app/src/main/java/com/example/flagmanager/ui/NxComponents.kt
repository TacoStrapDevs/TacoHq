@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.flagmanager.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.flagmanager.TacoIcon
import com.example.flagmanager.hapticCrunch
import com.example.flagmanager.hapticTick
import org.json.JSONArray
import org.json.JSONObject

/* ------------------------------------------------------------------ */
/* Persistence for UI-only preferences and the activity feed            */
/* ------------------------------------------------------------------ */

private const val UI_PREFS = "nx_ui"

fun nxPrefs(context: Context) =
    context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)

/** Haptics helpers that respect the persisted Haptics setting. */
fun nxTick(context: Context, enabled: Boolean) { if (enabled) hapticTick(context) }
fun nxCrunch(context: Context, enabled: Boolean) { if (enabled) hapticCrunch(context) }

data class NxEvent(
    val time: String,
    val title: String,
    val detail: String,
    val kind: String
)

object NxActivity {
    val events: SnapshotStateList<NxEvent> = mutableStateListOf()

    fun load(context: Context) {
        if (events.isNotEmpty()) return
        runCatching {
            val raw = nxPrefs(context).getString("activity", "[]") ?: "[]"
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                events += NxEvent(
                    o.optString("time"), o.optString("title"),
                    o.optString("detail"), o.optString("kind")
                )
            }
        }
    }

    fun add(context: Context, title: String, detail: String = "", kind: String = "info") {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        events.add(0, NxEvent(stamp, title, detail, kind))
        while (events.size > 60) events.removeAt(events.lastIndex)
        runCatching {
            val arr = JSONArray()
            events.take(60).forEach {
                arr.put(JSONObject().put("time", it.time).put("title", it.title)
                    .put("detail", it.detail).put("kind", it.kind))
            }
            nxPrefs(context).edit().putString("activity", arr.toString()).apply()
        }
    }

    fun clear(context: Context) {
        events.clear()
        nxPrefs(context).edit().remove("activity").apply()
    }
}

/* ------------------------------------------------------------------ */
/* Surfaces                                                             */
/* ------------------------------------------------------------------ */

@Composable
fun NxCard(
    modifier: Modifier = Modifier,
    accentTint: Float = 0f,
    corner: Dp = 18.dp,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val nx = LocalNx.current
    val motion = LocalNxMotion.current
    val shape = RoundedCornerShape(corner)
    val tint = nx.accent.copy(alpha = 0.10f * accentTint)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.985f else 1f, tween(90))
    var mod = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(Brush.verticalGradient(listOf(nx.surfaceAlt, nx.surface)))
        .background(tint)
        .border(borderWidth, nx.border.copy(alpha = if (accentTint > 0f) 0.85f else 0.55f), shape)
    if (onClick != null) {
        mod = mod.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
    }
    Column(mod.padding(16.dp), content = content)
}

@Composable
fun NxSection(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    val nx = LocalNx.current
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(3.dp, 13.dp).clip(CircleShape).background(nx.accent))
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = NxType.label, color = nx.accent)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action, style = NxType.caption, color = nx.dim,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onAction?.invoke() }
            )
        }
    }
}

@Composable
fun NxPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    dot: Boolean = false
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(if (filled) color.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = NxType.monoSmall, color = color, maxLines = 1)
    }
}

@Composable
fun NxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: NxButtonKind = NxButtonKind.Primary,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    icon: TacoIcon? = null,
    busy: Boolean = false
) {
    val nx = LocalNx.current
    val motion = LocalNxMotion.current
    val bg = when (kind) {
        NxButtonKind.Primary -> if (enabled) nx.accent else nx.surfaceAlt
        NxButtonKind.Secondary -> nx.surfaceAlt
        NxButtonKind.Ghost -> Color.Transparent
        NxButtonKind.Danger -> nx.danger.copy(alpha = 0.14f)
    }
    val fg = when (kind) {
        NxButtonKind.Primary -> if (enabled) nx.onAccent else nx.faint
        NxButtonKind.Danger -> nx.danger
        else -> if (enabled) nx.text else nx.faint
    }
    val shape = RoundedCornerShape(13.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val clickable = enabled && !busy
    val scale by animateFloatAsState(
        if (!clickable) 0.98f else if (pressed) 0.95f else 1f,
        tween(90)
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(height)
            .clip(shape)
            .background(bg)
            .border(
                1.dp,
                when (kind) {
                    NxButtonKind.Primary -> Color.Transparent
                    NxButtonKind.Danger -> nx.danger.copy(0.45f)
                    else -> nx.border
                },
                shape
            )
            .clickable(interactionSource = interaction, indication = null, enabled = clickable) {
                onClick()
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    TacoIcon(icon, fg, 17.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, style = NxType.body.copy(color = fg))
            }
        }
    }
}

enum class NxButtonKind { Primary, Secondary, Ghost, Danger }

@Composable
fun NxIconButton(
    icon: TacoIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalNx.current.dim,
    size: Dp = 34.dp,
    enabled: Boolean = true
) {
    val nx = LocalNx.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.90f else 1f, tween(90))
    Box(
        modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed && enabled) nx.accent.copy(0.16f) else nx.surfaceAlt)
            .border(1.dp, nx.border.copy(0.5f), RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) { TacoIcon(icon, if (enabled) tint else nx.faint, size * 0.52f) }
}

@Composable
fun NxSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val nx = LocalNx.current
    val motion = LocalNxMotion.current
    val color by animateColorAsState(
        if (checked) nx.accent else nx.border,
        if (motion) tween(180) else tween(0)
    )
    val thumb by animateFloatAsState(
        if (checked) 1f else 0f,
        if (motion) tween(180) else tween(0)
    )
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Box(
        modifier.size(42.dp, 24.dp).clip(shape).background(color)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                onChange(!checked)
            }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.size(18.dp).offset(x = 18.dp * thumb).clip(CircleShape)
                .background(if (checked) nx.onAccent else nx.dim)
        )
    }
}

@Composable
fun NxSwitchRow(
    title: String,
    sub: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: TacoIcon? = null
) {
    val nx = LocalNx.current
    Row(
        modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                .background(nx.accent.copy(0.10f)), contentAlignment = Alignment.Center) {
                TacoIcon(leading, nx.accent, 17.dp)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = NxType.body)
            if (sub != null) Text(sub, style = NxType.caption)
        }
        Spacer(Modifier.width(10.dp))
        NxSwitch(checked, onChange, enabled = enabled)
    }
}

@Composable
fun NxSearch(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…"
) {
    val nx = LocalNx.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(shape)
            .background(nx.surfaceAlt)
            .border(1.dp, nx.border.copy(0.6f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TacoIcon(TacoIcon.Search, nx.dim, 16.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = NxType.caption)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = NxType.body,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(nx.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            NxIconButton(TacoIcon.Close, { onValue("") }, size = 26.dp)
        }
    }
}

@Composable
fun NxSegmented(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val nx = LocalNx.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(nx.surfaceAlt)
            .border(1.dp, nx.border.copy(0.5f), shape).padding(3.dp)
    ) {
        items.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (active) nx.accent.copy(0.20f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, style = NxType.caption,
                    color = if (active) nx.accent else nx.dim
                )
            }
        }
    }
}

@Composable
fun NxStat(
    label: String,
    value: String,
    icon: TacoIcon,
    modifier: Modifier = Modifier,
    color: Color = LocalNx.current.accent
) {
    val nx = LocalNx.current
    NxCard(modifier, accentTint = 0.35f, corner = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(color.copy(0.14f)), contentAlignment = Alignment.Center) {
                TacoIcon(icon, color, 16.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, style = NxType.stat, color = color)
                Text(label.uppercase(), style = NxType.label)
            }
        }
    }
}

@Composable
fun NxEmpty(
    icon: TacoIcon,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val nx = LocalNx.current
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
            .background(nx.surfaceAlt), contentAlignment = Alignment.Center) {
            TacoIcon(icon, nx.faint, 28.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = NxType.title)
        Spacer(Modifier.height(5.dp))
        Text(sub, style = NxType.caption, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            NxButton(actionLabel, { onAction?.invoke() }, kind = NxButtonKind.Secondary, height = 40.dp)
        }
    }
}

@Composable
fun NxDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirm: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismiss: String = "Cancel",
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val nx = LocalNx.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = nx.surfaceAlt,
        title = { Text(title, style = NxType.title) },
        text = { Column(content = content) },
        confirmButton = {
            if (confirm != null) TextButton(onClick = { onConfirm?.invoke(); onDismiss() }) {
                Text(confirm, color = nx.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismiss, color = nx.dim) }
        }
    )
}

@Composable
fun NxDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalNx.current.border.copy(0.5f)))
}

@Composable
fun Modifier.nxGlow(color: Color, radius: Float = 18f): Modifier =
    this.drawBehind {
        drawCircle(
            Brush.radialGradient(listOf(color.copy(0.25f), Color.Transparent)),
            radius = radius,
            center = Offset(size.width / 2, size.height / 2)
        )
    }

/** Lightweight deterministic grain, matching the legacy app's texture toggle. */
fun Modifier.nxGrain(enabled: Boolean, alpha: Float = 0.035f): Modifier =
    if (!enabled) this else this.drawBehind {
        val seed = java.util.Random(42)
        repeat((size.width * size.height / 140).toInt().coerceAtMost(450)) {
            val x = seed.nextFloat() * size.width
            val y = seed.nextFloat() * size.height
            drawCircle(Color.White.copy(alpha = alpha), radius = 1f, center = Offset(x, y))
        }
    }
