@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.flagmanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.flagmanager.FlagCatalog
import com.example.flagmanager.RobloxServerLocation
import com.example.flagmanager.TacoIcon
import com.example.flagmanager.TacoState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NxToolsScreen(state: TacoState) {
    val nx = LocalNx.current
    val context = LocalContext.current
    val fontLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) state.injectCustomFont(uri) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var serverIp by remember { mutableStateOf("") }
    var serverResult by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(false) }
    val resolver = remember { RobloxServerLocation() }
    DisposableEffect(Unit) { onDispose { resolver.close() } }

    val fontPresets = listOf("Default Roblox Font", "JetBrains Mono", "Inter Bold", "Comic Sans", "Product Sans")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Tools", style = NxType.display)
        Text("Diagnostics, backups, fonts and network helpers.", style = NxType.caption)
        Spacer(Modifier.height(18.dp))

        NxSection("Supported FastFlags (${FlagCatalog.ENTRIES.size})")
        NxCard {
            Text("These are the only flags Roblox reads from a local config.", style = NxType.caption)
            Spacer(Modifier.height(6.dp))
            FlagCatalog.byGroup().forEach { (group, entries) ->
                Spacer(Modifier.height(8.dp))
                Text(group.uppercase(), style = NxType.label, color = nx.accent)
                entries.forEach { e ->
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = NxType.body)
                            Text(e.key, style = NxType.monoSmall, color = nx.faint, maxLines = 1)
                            Text(e.detail, style = NxType.caption)
                        }
                        Spacer(Modifier.width(8.dp))
                        NxButton("Use", {
                            state.draftFlags[e.key] = e.recommended
                            NxActivity.add(context, "Set ${e.title}", e.recommended, "flag")
                        }, Modifier.width(60.dp), NxButtonKind.Secondary, height = 34.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        NxSection("Diagnostics")
        NxCard {
            diagRow("Deployment", if (state.useRoot) "Root" else "Shizuku", TacoIcon.Bolt)
            NxDivider(Modifier.padding(vertical = 6.dp))
            diagRow("Path", state.path, TacoIcon.Doc)
            NxDivider(Modifier.padding(vertical = 6.dp))
            diagRow("Package", state.pkg, TacoIcon.Gamepad)
            NxDivider(Modifier.padding(vertical = 6.dp))
            diagRow("Roblox", state.robloxVersion ?: "detecting…", TacoIcon.Info)
            NxDivider(Modifier.padding(vertical = 6.dp))
            diagRow("Listener", state.vpnStatus, TacoIcon.Globe)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                NxButton("Copy report", { state.copyDiagnostics() },
                    Modifier.weight(1f), NxButtonKind.Secondary, icon = TacoIcon.Copy)
                Spacer(Modifier.width(8.dp))
                NxButton("Clear cache", { state.clearCache() },
                    Modifier.weight(1f), NxButtonKind.Ghost, icon = TacoIcon.Refresh)
            }
        }
        Spacer(Modifier.height(16.dp))

        NxSection("Backup & restore")
        Row(Modifier.fillMaxWidth()) {
            NxButton("Export", { state.exportBackup() },
                Modifier.weight(1f), NxButtonKind.Secondary, icon = TacoIcon.Up)
            Spacer(Modifier.width(8.dp))
            NxButton("Import", { state.importBackup() },
                Modifier.weight(1f), NxButtonKind.Secondary, icon = TacoIcon.Doc)
        }
        Spacer(Modifier.height(4.dp))
        Text("Export includes every layer, pinned game and flag. Import replaces the current setup.",
            style = NxType.caption)
        Spacer(Modifier.height(16.dp))

        NxSection("Client fonts")
        NxCard {
            Text("Preset", style = NxType.label)
            Spacer(Modifier.height(8.dp))
            fontPresets.forEach { preset ->
                val active = state.selectedFontPreset == preset
                Row(
                    Modifier.fillMaxWidth().clickable {
                        state.injectPresetFont(preset)
                        state.selectedFontPreset = preset
                        NxActivity.add(context, "Font applied", preset, "tool")
                    }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50))
                        .background(if (active) nx.accent else nx.border))
                    Spacer(Modifier.width(10.dp))
                    Text(preset, style = NxType.body, color = if (active) nx.accent else nx.text)
                    Spacer(Modifier.weight(1f))
                    Text(preset, style = if (preset == "JetBrains Mono") NxType.monoSmall else NxType.caption,
                        color = nx.faint)
                }
            }
            Spacer(Modifier.height(8.dp))
            NxButton("Use custom font file", { fontLauncher.launch("font/*") },
                Modifier.fillMaxWidth(), NxButtonKind.Secondary, height = 40.dp,
                icon = TacoIcon.Type)
        }
        Spacer(Modifier.height(16.dp))

        NxSection("Local listener")
        NxCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Diagnostic listener", style = NxType.body)
                    Text("Binds a local port to inspect Roblox traffic. Optional.",
                        style = NxType.caption)
                }
                NxSwitch(state.vpnBypass, { enabled ->
                    state.vpnBypass = enabled
                    if (enabled) state.startVpnBypass(context) else state.stopVpnBypass(context)
                })
            }
            Spacer(Modifier.height(6.dp))
            NxPill("STATUS: ${state.vpnStatus.uppercase()}",
                if (state.vpnStatus == "active") nx.success else nx.dim,
                dot = state.vpnStatus == "active")
        }
        Spacer(Modifier.height(16.dp))

        NxSection("Server region")
        NxCard {
            Text("Resolve a Roblox server IP to see its datacenter region.", style = NxType.caption)
            Spacer(Modifier.height(10.dp))
            NxSearch(serverIp, { serverIp = it }, placeholder = "e.g. 1.2.3.4")
            Spacer(Modifier.height(10.dp))
            NxButton("Look up", {
                if (serverIp.isNotBlank()) {
                    resolving = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { resolver.lookup(serverIp.trim()) }
                        serverResult = if (result.isKnown) {
                            "${result.country} (${result.countryCode ?: "?"}) · ${result.ipAddress ?: serverIp}"
                        } else {
                            result.message ?: "Unknown region"
                        }
                        resolving = false
                    }
                }
            }, Modifier.fillMaxWidth(), NxButtonKind.Secondary,
                icon = TacoIcon.Globe, busy = resolving, height = 40.dp)
            if (serverResult != null) {
                Spacer(Modifier.height(10.dp))
                NxPill(serverResult!!, nx.accent, dot = true)
            }
        }
        Spacer(Modifier.height(16.dp))

        NxSection("Activity log")
        NxCard {
            if (NxActivity.events.isEmpty()) {
                Text("No events logged yet.", style = NxType.caption)
            } else {
                NxActivity.events.take(20).forEachIndexed { i, e ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(e.time, style = NxType.monoSmall, color = nx.faint)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = NxType.body)
                            if (e.detail.isNotBlank()) Text(e.detail, style = NxType.caption)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                NxButton("Clear log", { NxActivity.clear(context) },
                    Modifier.fillMaxWidth(), NxButtonKind.Ghost, height = 38.dp)
            }
        }
    }
}

@Composable
private fun diagRow(label: String, value: String, icon: TacoIcon) {
    val nx = LocalNx.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TacoIcon(icon, nx.dim, 16.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = NxType.caption, modifier = Modifier.width(90.dp))
        Text(value, style = NxType.monoSmall, modifier = Modifier.weight(1f))
    }
}
