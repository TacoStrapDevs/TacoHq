@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.flagmanager.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.flagmanager.LaunchMode
import com.example.flagmanager.LaunchState
import com.example.flagmanager.PACKS
import com.example.flagmanager.StepStatus
import com.example.flagmanager.TacoIcon
import com.example.flagmanager.TacoState

@Composable
fun NxHomeScreen(
    state: TacoState,
    onNavigate: (String) -> Unit,
    onPickTarget: () -> Unit
) {
    val nx = LocalNx.current
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val merged = state.mergedFlags()
    val canLaunch = state.shizuku.ok || state.useRoot

    var showChannel by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll)
            .padding(horizontal = 16.dp).padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        consoleHeader(state)
        Spacer(Modifier.height(12.dp))
        statusStrip(state)
        Spacer(Modifier.height(12.dp))

        hero(state, canLaunch, context)
        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(visible = state.launchState !is LaunchState.Idle) {
            pipeline(state, context)
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth()) {
            NxStat("Flags", merged.size.toString(), TacoIcon.Flag, Modifier.weight(1f))
            Spacer(Modifier.width(9.dp))
            NxStat("Profiles", state.layers.size.toString(), TacoIcon.Layers, Modifier.weight(1f))
            Spacer(Modifier.width(9.dp))
            NxStat("Games", state.games.size.toString(), TacoIcon.Gamepad, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))

        NxSection("Quick actions")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            quickAction(TacoIcon.Refresh, "Cache", "Clear Roblox cache") {
                nxTick(context, state.hapticsOn)
                state.clearCache()
                NxActivity.add(context, "Cache clear requested", state.pkg, "tool")
            }
            quickAction(TacoIcon.Doc, "Diagnostics", "Copy support info") {
                nxTick(context, state.hapticsOn)
                state.copyDiagnostics()
                NxActivity.add(context, "Diagnostics copied", "", "tool")
            }
        }
        Spacer(Modifier.height(18.dp))

        NxSection("One-tap presets", action = "All flags") { onNavigate("flags") }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            PACKS.forEach { p ->
                val active = p.sets.all { (k, v) -> state.draftFlags[k] == v }
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (active) nx.accent.copy(0.18f) else nx.surfaceAlt)
                        .border(1.dp, if (active) nx.accent else nx.border, RoundedCornerShape(14.dp))
                        .clickable {
                            nxTick(context, state.hapticsOn)
                            state.applyPack(p)
                            NxActivity.add(context, "Preset applied", p.name, "flag")
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column(Modifier.width(116.dp)) {
                        Text(p.name, style = NxType.body, color = if (active) nx.accent else nx.text)
                        Spacer(Modifier.height(3.dp))
                        Text(p.desc, style = NxType.caption, maxLines = 2)
                    }
                }
                Spacer(Modifier.width(9.dp))
            }
        }
        Spacer(Modifier.height(18.dp))

        NxSection("Session", action = "Open") { onNavigate("tools") }
        NxCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(nx.accent.copy(0.12f)), contentAlignment = Alignment.Center) {
                    TacoIcon(TacoIcon.Globe, nx.accent, 19.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.activeLayerName(), style = NxType.body)
                    Text(
                        state.robloxVersion?.let { "Roblox $it" } ?: "Detecting version…",
                        style = NxType.caption
                    )
                }
                NxPill(state.channel, nx.accent2, dot = true)
            }
            Spacer(Modifier.height(12.dp))
            NxDivider()
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                infoCell("Target", state.targetLabel(), Modifier.weight(1.2f)) { onPickTarget() }
                infoCell("Channel", state.channel, Modifier.weight(0.8f)) { showChannel = true }
                infoCell("Mode", if (state.useRoot) "Root" else "Shizuku", Modifier.weight(0.8f)) {
                    onNavigate("settings")
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        NxSection("Activity", action = "Clear") { NxActivity.clear(context) }
        NxCard {
            if (NxActivity.events.isEmpty()) {
                Text("Nothing logged yet. Launch Roblox or apply a preset to see activity here.",
                    style = NxType.caption)
            } else {
                NxActivity.events.take(6).forEachIndexed { i, e ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.size(7.dp).padding(top = 5.dp).clip(CircleShape)
                            .background(eventColor(e.kind)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = NxType.body)
                            if (e.detail.isNotBlank()) Text(e.detail, style = NxType.caption, maxLines = 1)
                        }
                        Text(e.time, style = NxType.monoSmall)
                    }
                }
            }
        }
    }

    if (showChannel) {
        NxDialog("Release channel", { showChannel = false }) {
            listOf("release", "zfeature", "zcanary", "live", "old").forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { state.channel = c; showChannel = false }
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape)
                        .background(if (c == state.channel) nx.accent else nx.border))
                    Spacer(Modifier.width(10.dp))
                    Text(c, style = NxType.body)
                }
            }
        }
    }
}

@Composable
private fun consoleHeader(state: TacoState) {
    val nx = LocalNx.current
    Column {
        Text("TACOSTRAP // BOOTSTRAP CONSOLE", style = NxType.label, color = nx.accent)
        Spacer(Modifier.height(3.dp))
        Text("Deployment control", style = NxType.display)
        Spacer(Modifier.height(3.dp))
        Text(
            "Configure FastFlags, pick a target, and deploy through Shizuku or root.",
            style = NxType.caption
        )
    }
}

@Composable
private fun statusStrip(state: TacoState) {
    val nx = LocalNx.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(nx.surfaceAlt)
            .border(1.dp, nx.border.copy(0.7f), RoundedCornerShape(12.dp))
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        statusCell("SHIZUKU", if (state.shizuku.ok) "READY" else "OFF",
            if (state.shizuku.ok) nx.success else nx.danger)
        statusCell("ACCESS", if (state.useRoot) "ROOT" else "ADB", nx.accent2)
        statusCell("ROBLOX", state.robloxVersion ?: "…", nx.text)
        statusCell("CHANNEL", state.channel.uppercase(), nx.dim)
    }
}

@Composable
private fun RowScope.statusCell(label: String, value: String, valueColor: Color) {
    val nx = LocalNx.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = NxType.label, color = nx.faint)
        Spacer(Modifier.height(3.dp))
        Text(value, style = NxType.monoSmall.copy(color = valueColor), maxLines = 1)
    }
}

@Composable
private fun hero(state: TacoState, canLaunch: Boolean, context: Context) {
    val nx = LocalNx.current
    val motion = LocalNxMotion.current
    val running = state.launchState is LaunchState.Running
    val glow by rememberInfiniteTransition(label = "hero-glow").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = if (motion) infiniteRepeatable(
            tween(5200, easing = LinearEasing), RepeatMode.Reverse
        ) else infiniteRepeatable(tween(3_600_000), RepeatMode.Restart),
        label = "glow"
    )
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to nx.accent.copy(alpha = 0.14f + 0.16f * glow),
                        0.55f to nx.surfaceAlt,
                        1f to nx.accent2.copy(alpha = 0.06f + 0.14f * (1f - glow))
                    )
                )
            )
            .border(1.dp, nx.accent.copy(0.45f), RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DEPLOYMENT", style = NxType.label, color = nx.accent)
                    Spacer(Modifier.height(4.dp))
                    Text(state.targetLabel(), style = NxType.title)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(nx.accent.copy(0.18f)),
                    contentAlignment = Alignment.Center
                ) { TacoIcon(TacoIcon.Bolt, nx.accent, 21.dp) }
            }
            Spacer(Modifier.height(16.dp))
            NxSegmented(
                items = LaunchMode.entries.map { it.label },
                selected = state.mode.ordinal,
                onSelect = { state.mode = LaunchMode.entries[it] }
            )
             Spacer(Modifier.height(12.dp))
             Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                 Box(Modifier.size(7.dp).clip(CircleShape)
                     .background(if (canLaunch) nx.success else nx.warn))
                 Spacer(Modifier.width(8.dp))
                 Text(
                     if (canLaunch) "Ready to deploy ${state.allowedCount(state.draftFlags)} flags"
                     else "Enable Shizuku or root to deploy",
                     style = NxType.caption,
                     color = if (canLaunch) nx.dim else nx.warn,
                     modifier = Modifier.weight(1f)
                 )
                 if (running) NxPill("WORKING", nx.accent, dot = true)
             }
        }
    }
}

@Composable
private fun pipeline(state: TacoState, context: Context) {
    val nx = LocalNx.current
    val launch = state.launchState
    val steps = when (launch) {
        is LaunchState.Running -> launch.steps
        is LaunchState.Success -> launch.steps
        is LaunchState.Failed -> launch.steps
        else -> emptyList()
    }
    NxCard(accentTint = 0.5f) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("PIPELINE", style = NxType.label, color = nx.accent)
            Spacer(Modifier.weight(1f))
            when (launch) {
                is LaunchState.Running -> NxPill("RUNNING", nx.accent, dot = true)
                is LaunchState.Success -> NxPill("COMPLETE", nx.success, dot = true)
                is LaunchState.Failed -> NxPill("FAILED", nx.danger, dot = true)
                else -> {}
            }
        }
        Spacer(Modifier.height(10.dp))
        val done = steps.count { it.status == StepStatus.Done || it.status == StepStatus.Skipped }
        LinearProgressIndicator(
            progress = { if (steps.isEmpty()) 0f else done.toFloat() / steps.size },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
            color = nx.accent, trackColor = nx.border
        )
        Spacer(Modifier.height(12.dp))
        steps.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                val c = when (s.status) {
                    StepStatus.Done -> nx.success
                    StepStatus.Running -> nx.accent
                    StepStatus.Failed -> nx.danger
                    StepStatus.Skipped -> nx.faint
                    StepStatus.Pending -> nx.border
                }
                Box(Modifier.size(7.dp).padding(top = 5.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = NxType.body)
                    if (s.detail.isNotBlank()) Text(s.detail, style = NxType.monoSmall, maxLines = 2)
                }
            }
        }
        if (launch is LaunchState.Failed) {
            Spacer(Modifier.height(8.dp))
            Text(launch.message, style = NxType.caption, color = nx.danger)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            if (launch is LaunchState.Running) {
                NxButton("Cancel", { state.cancelLaunch() }, kind = NxButtonKind.Ghost, height = 38.dp)
            }
            if (launch is LaunchState.Failed) {
                NxButton("Retry", {
                    state.retryLaunch()
                    NxActivity.add(context, "Launch retried", "", "launch")
                }, kind = NxButtonKind.Secondary, height = 38.dp)
            }
            if (launch is LaunchState.Success || launch is LaunchState.Failed) {
                Spacer(Modifier.width(8.dp))
                NxButton("Dismiss", { state.cancelLaunch() }, kind = NxButtonKind.Ghost, height = 38.dp)
            }
        }
    }
}

@Composable
private fun RowScope.quickAction(icon: TacoIcon, title: String, sub: String, onClick: () -> Unit) {
    val nx = LocalNx.current
    Column(
        Modifier.width(104.dp).clip(RoundedCornerShape(14.dp))
            .background(nx.surfaceAlt)
            .border(1.dp, nx.border.copy(0.6f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
            .background(nx.accent.copy(0.12f)), contentAlignment = Alignment.Center) {
            TacoIcon(icon, nx.accent, 17.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = NxType.body)
        Text(sub, style = NxType.caption)
    }
}

@Composable
private fun infoCell(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val nx = LocalNx.current
    Column(modifier.clickable(onClick = onClick).padding(end = 8.dp)) {
        Text(label.uppercase(), style = NxType.label)
        Spacer(Modifier.height(3.dp))
        Text(value, style = NxType.caption, color = nx.text, maxLines = 1)
    }
}

@Composable
private fun eventColor(kind: String): Color {
    val nx = LocalNx.current
    return when (kind) {
        "launch" -> nx.accent
        "flag" -> nx.success
        "error" -> nx.danger
        else -> nx.dim
    }
}
