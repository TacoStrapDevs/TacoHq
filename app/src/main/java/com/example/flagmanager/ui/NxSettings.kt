@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.flagmanager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.flagmanager.TacoIcon
import com.example.flagmanager.TacoState

@Composable
fun NxSettingsScreen(
    state: TacoState,
    themeIdx: Int,
    onTheme: (Int) -> Unit,
    dense: Boolean,
    onDense: (Boolean) -> Unit
) {
    val nx = LocalNx.current
    val context = LocalContext.current
    var showPkg by remember { mutableStateOf(false) }
    var pkgText by remember { mutableStateOf(state.pkg) }
    var showImport by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) { state.bgImageUri = uri.toString(); state.persist() } }
    val fontNames = listOf("Default", "Mono", "Serif", "Cursive", "Mono Alt")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Settings", style = NxType.display)
        Text("Make it yours. v${state.appVersion}", style = NxType.caption)
        Spacer(Modifier.height(18.dp))

        NxSection("Appearance")
        NxCard {
            Text("Window theme", style = NxType.label)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                NX_THEMES.forEachIndexed { i, spec ->
                    val active = i == themeIdx
                    Column(
                        Modifier.width(66.dp).clip(RoundedCornerShape(14.dp))
                            .background(spec.surfaceAlt)
                            .border(2.dp, if (active) spec.accent else Color.Transparent,
                                RoundedCornerShape(14.dp))
                            .clickable { onTheme(i) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row {
                            listOf(spec.accent, spec.accent2, spec.bg).forEach { c ->
                                Box(Modifier.size(12.dp).clip(CircleShape)
                                    .background(c)
                                    .border(1.dp, nx.border.copy(0.5f), CircleShape))
                                Spacer(Modifier.width(3.dp))
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(spec.name.split(' ').first(), style = NxType.caption,
                            color = if (active) spec.accent else nx.dim)
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            settingSlider("Corner radius", "${state.cardCornerRadius} dp",
                state.cardCornerRadius.toFloat(), 8f, 32f) {
                state.cardCornerRadius = it.toInt(); state.appearancePreset = -1; state.persist()
            }
            settingSlider("Surface opacity", "${(state.glassOpacity * 100).toInt()}%",
                state.glassOpacity, 0.55f, 1f) {
                state.glassOpacity = it; state.appearancePreset = -1; state.persist()
            }
            settingSlider("Wallpaper dim", "${(state.wallpaperDim * 100).toInt()}%",
                state.wallpaperDim, 0f, 0.8f) {
                state.wallpaperDim = it; state.persist()
            }
            Spacer(Modifier.height(4.dp))
            Spacer(Modifier.height(8.dp))
            Text("Font", style = NxType.label)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                fontNames.forEachIndexed { i, name ->
                    Box(
                        Modifier.clip(RoundedCornerShape(9.dp))
                            .background(if (state.fontIdx == i) nx.accent.copy(0.22f) else nx.surface)
                            .border(1.dp, if (state.fontIdx == i) nx.accent else nx.border,
                                RoundedCornerShape(9.dp))
                            .clickable { state.fontIdx = i; state.persist() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(name, style = NxType.caption,
                            color = if (state.fontIdx == i) nx.accent else nx.dim)
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                NxButton(if (state.bgImageUri.isEmpty()) "Set wallpaper" else "Change wallpaper",
                    { bgLauncher.launch("image/*") }, Modifier.weight(1f),
                    NxButtonKind.Secondary, icon = TacoIcon.Image, height = 40.dp)
                if (state.bgImageUri.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    NxButton("Remove", {
                        state.bgImageUri = ""; state.persist()
                    }, Modifier.weight(0.6f), NxButtonKind.Ghost, height = 40.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            NxSwitchRow("Background texture", "Subtle grain across the app", state.textureEnabled,
                { state.textureEnabled = it; state.persist() }, leading = TacoIcon.Image)
            NxSwitchRow("AMOLED black", "Pure black background", state.amoled,
                { state.amoled = it; state.appearancePreset = -1; state.persist() },
                leading = TacoIcon.Dot)
            NxSwitchRow("Compact layout", "Tighter spacing on all screens", dense, onDense,
                leading = TacoIcon.Home)
            NxSwitchRow("Reduce motion", "Disable animations and transitions", state.reduceMotion,
                { state.reduceMotion = it; state.persist() },
                leading = TacoIcon.Bolt)
            NxSwitchRow("Haptics", "Small vibration on interactions", state.hapticsOn,
                { state.hapticsOn = it; state.persist() },
                leading = TacoIcon.Dot)
        }
        Spacer(Modifier.height(16.dp))

        NxSection("Execution")
        NxCard {
            Row(Modifier.fillMaxWidth().clickable { showPkg = true }
                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Roblox package", style = NxType.body)
                    Text(state.pkg, style = NxType.monoSmall)
                }
                NxIconButton(TacoIcon.Type, { showPkg = true }, size = 30.dp)
            }
            NxDivider()
            Row(Modifier.fillMaxWidth().clickable { state.cyclePath() }
                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Settings path", style = NxType.body)
                    Text(state.path, style = NxType.monoSmall)
                }
                NxIconButton(TacoIcon.Refresh, { state.cyclePath() }, size = 30.dp)
            }
            NxDivider()
            NxSwitchRow("Root mode", "Use root instead of Shizuku", state.useRoot,
                { state.useRoot = it; state.persist() }, leading = TacoIcon.Bolt)
            NxSwitchRow("Apply on save", "Deploy flags whenever you save", state.applyOnSave,
                { state.applyOnSave = it; state.persist() }, leading = TacoIcon.Save)
            NxSwitchRow("Restart Roblox", "Force close the client before launch", state.restart,
                { state.restart = it; state.persist() }, leading = TacoIcon.Refresh)
            NxSwitchRow("FPS unlock", "Remove the client frame cap", state.fpsCap,
                { state.fpsCap = it; state.persist() }, leading = TacoIcon.Bolt)
            Spacer(Modifier.height(6.dp))
            if (!state.shizuku.ok) {
                NxButton("Grant Shizuku permission", { state.requestShizukuPermission() },
                    Modifier.fillMaxWidth(), NxButtonKind.Secondary, icon = TacoIcon.Info)
            }
        }
        Spacer(Modifier.height(16.dp))

        NxSection("Notifications")
        NxCard {
            NxSwitchRow("Flag messages", "Confirmations for flag changes", state.msgFlag,
                { state.msgFlag = it; state.persist() }, leading = TacoIcon.Flag)
            NxSwitchRow("Launch messages", "Pipeline progress toasts", state.msgLaunch,
                { state.msgLaunch = it; state.persist() }, leading = TacoIcon.Play)
            NxSwitchRow("Other messages", "Tools and backup results", state.msgOther,
                { state.msgOther = it; state.persist() }, leading = TacoIcon.Doc)
            NxSwitchRow("Join notifications", "Alert when a friend joins", state.joinNotifications,
                { state.joinNotifications = it; state.persist() }, leading = TacoIcon.Info)
            NxSwitchRow("Server location", "Resolve the server region", state.showServerLocation,
                { state.showServerLocation = it; state.persist() }, leading = TacoIcon.Globe)
            NxSwitchRow("Heads-up popups", "Show floating notifications", state.popupNotifications,
                { state.popupNotifications = it; state.persist() }, leading = TacoIcon.Dot)
        }
        Spacer(Modifier.height(16.dp))

        NxSection("Data")
        NxCard {
            NxButton("Import from URL", { showImport = true },
                Modifier.fillMaxWidth(), NxButtonKind.Secondary, icon = TacoIcon.Doc)
            Spacer(Modifier.height(8.dp))
            NxButton("Export backup", { state.exportBackup() },
                Modifier.fillMaxWidth(), NxButtonKind.Ghost, icon = TacoIcon.Up)
        }
        Spacer(Modifier.height(20.dp))
        Text("Taco Bootstrapper · open source · no login, no telemetry.",
            style = NxType.caption, modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }

    if (showPkg) {
        NxDialog("Roblox package", { showPkg = false }, confirm = "Set", onConfirm = {
            if (pkgText.isNotBlank()) { state.pkg = pkgText.trim(); state.persist() }
        }) {
            NxSearch(pkgText, { pkgText = it }, placeholder = "com.roblox.client")
        }
    }
    if (showImport) {
        NxDialog("Import flags from URL", { showImport = false }, confirm = "Import", onConfirm = {
            if (importUrl.isNotBlank()) state.importFromUrl(importUrl.trim())
        }) {
            NxSearch(importUrl, { importUrl = it }, placeholder = "https://…/ClientAppSettings.json")
        }
    }
}

@Composable
private fun settingSlider(
    label: String,
    value: String,
    current: Float,
    from: Float,
    to: Float,
    onChange: (Float) -> Unit
) {
    val nx = LocalNx.current
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = NxType.caption)
            Spacer(Modifier.weight(1f))
            Text(value, style = NxType.monoSmall, color = nx.accent)
        }
        Slider(
            value = current,
            onValueChange = onChange,
            valueRange = from..to,
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
    }
}
