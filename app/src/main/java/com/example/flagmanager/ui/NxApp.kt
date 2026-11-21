@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.flagmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.flagmanager.LaunchMode
import com.example.flagmanager.rememberImageBitmapFromUri
import com.example.flagmanager.TacoIcon
import com.example.flagmanager.TacoState

private data class NxTab(val route: String, val icon: TacoIcon, val label: String)

private val TABS = listOf(
    NxTab("home", TacoIcon.Home, "Home"),
    NxTab("flags", TacoIcon.Flag, "Flags"),
    NxTab("library", TacoIcon.Layers, "Library"),
    NxTab("games", TacoIcon.Gamepad, "Games"),
    NxTab("tools", TacoIcon.Bolt, "Tools")
)

/**
 * New top-level shell. Owns its own full-window theme, navigation and global
 * action bar; all screens are driven by the existing [TacoState] engine.
 */
@Composable
fun NxRoot(state: TacoState) {
    val context = LocalContext.current
    val prefs = remember { nxPrefs(context) }
    var themeIdx by remember {
        mutableStateOf(prefs.getInt("theme", 0).coerceIn(0, NX_THEMES.lastIndex))
    }
    var dense by remember { mutableStateOf(prefs.getBoolean("dense", false)) }
    var route by rememberSaveable { mutableStateOf("home") }
    val motion = !state.reduceMotion
    val fontFamily = nxFont(state.fontIdx)
    val bgBitmap = rememberImageBitmapFromUri(state.bgImageUri)

    LaunchedEffect(Unit) { NxActivity.load(context) }

    BackHandler(enabled = route != "home") { route = "home" }

    NxTheme(
        spec = NX_THEMES[themeIdx],
        fontFamily = fontFamily,
        motion = motion,
        dense = dense
    ) {
        val nx = LocalNx.current
        Box(Modifier.fillMaxSize().background(nx.bgDeep)) {
            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alpha = 1f
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = state.wallpaperDim)))
            }

            Box(Modifier.fillMaxSize().nxGrain(state.textureEnabled))

            Column(Modifier.fillMaxSize()) {
                topBar(
                    state = state,
                    onSettings = { route = "settings" },
                    onTheme = {
                        themeIdx = (themeIdx + 1) % NX_THEMES.size
                        prefs.edit().putInt("theme", themeIdx).apply()
                    }
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Crossfade(
                        targetState = route,
                        animationSpec = if (motion) tween(180) else tween(0),
                        label = "nx-route"
                    ) { r ->
                        when (r) {
                            "flags" -> NxFlagsScreen(state)
                            "library" -> NxLibraryScreen(state)
                            "games" -> NxGamesScreen(state)
                            "tools" -> NxToolsScreen(state)
                            "settings" -> NxSettingsScreen(
                                state = state,
                                themeIdx = themeIdx,
                                onTheme = { i ->
                                    themeIdx = i.coerceIn(0, NX_THEMES.lastIndex)
                                    prefs.edit().putInt("theme", themeIdx).apply()
                                },
                                dense = dense,
                                onDense = { d ->
                                    dense = d
                                    prefs.edit().putBoolean("dense", dense).apply()
                                }
                            )
                            else -> NxHomeScreen(state) { target -> route = target }
                        }
                    }
                }

                bottomNav(current = route) { route = it }
            }

            SnackbarHost(
                hostState = state.snackbar,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding().padding(bottom = 78.dp)
            ) { d ->
                Snackbar(
                    d,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = nx.surfaceAlt,
                    contentColor = nx.text,
                    actionColor = nx.accent
                )
            }
        }
    }
}

@Composable
private fun topBar(state: TacoState, onSettings: () -> Unit, onTheme: () -> Unit) {
    val nx = LocalNx.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(62.dp)
            .background(nx.bg.copy(alpha = 0.92f))
            .border(androidx.compose.foundation.BorderStroke(1.dp, nx.border.copy(0.5f)))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(nx.accent, nx.accent2))),
            contentAlignment = Alignment.Center
        ) {
            Text("T", style = NxType.body.copy(color = nx.onAccent))
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text("TACO", style = NxType.label, color = nx.text)
            Text("v${state.appVersion}", style = NxType.monoSmall, color = nx.faint)
        }
        Spacer(Modifier.weight(1f))
        if (state.pendingCount() > 0) {
            NxPill("${state.pendingCount()} UNSAVED", nx.warn, dot = true)
            Spacer(Modifier.width(6.dp))
        }
        NxIconButton(TacoIcon.Star, onTheme, size = 32.dp, tint = nx.accent2)
        Spacer(Modifier.width(5.dp))
        NxIconButton(TacoIcon.Sliders, onSettings, size = 32.dp)
        Spacer(Modifier.width(5.dp))
        Box(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(nx.accent)
                .clickable {
                    state.mode = LaunchMode.SAVE_LAUNCH
                    state.runLaunch()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TacoIcon(TacoIcon.Play, nx.onAccent, 13.dp)
                Spacer(Modifier.width(5.dp))
                Text("Save & Launch", style = NxType.caption.copy(color = nx.onAccent))
            }
        }
    }
}

@Composable
private fun bottomNav(current: String, onSelect: (String) -> Unit) {
    val nx = LocalNx.current
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().height(66.dp)
            .background(nx.bg.copy(alpha = 0.95f))
            .border(androidx.compose.foundation.BorderStroke(1.dp, nx.border.copy(0.5f)))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TABS.forEach { tab ->
            val route = tab.route
            val icon = tab.icon
            val label = tab.label
            val active = current == route
            val tint = if (active) nx.accent else nx.faint
            Column(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (active) nx.accent.copy(0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(route) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TacoIcon(icon, tint, 20.dp)
                Spacer(Modifier.height(3.dp))
                Text(label, style = NxType.caption.copy(color = tint))
            }
        }
    }
}
