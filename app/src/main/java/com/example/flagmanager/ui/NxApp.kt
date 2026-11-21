@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.flagmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

                bottomNav(state = state, current = route) { route = it }
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
    val context = LocalContext.current
    val pending = state.pendingCount()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 400.dp
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(62.dp)
                .background(nx.bg.copy(alpha = 0.92f))
                .border(androidx.compose.foundation.BorderStroke(1.dp, nx.border.copy(0.5f)))
                .padding(horizontal = if (compact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(nx.accent, nx.accent2))),
                contentAlignment = Alignment.Center
            ) {
                Text("T", style = NxType.body.copy(color = nx.onAccent))
            }
            if (!compact) {
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("TACO", style = NxType.label, color = nx.text)
                    Text("v${state.appVersion}", style = NxType.monoSmall, color = nx.faint)
                }
            }
            Spacer(Modifier.weight(1f))
            if (pending > 0) {
                if (compact) {
                    NxPill("$pending", nx.warn, dot = true)
                } else {
                    NxPill("$pending UNSAVED", nx.warn, dot = true)
                }
                Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
            }
            NxIconButton(TacoIcon.Star, {
                nxTick(context, state.hapticsOn)
                onTheme()
            }, size = 32.dp, tint = nx.accent2)
            Spacer(Modifier.width(4.dp))
            NxIconButton(TacoIcon.Sliders, {
                nxTick(context, state.hapticsOn)
                onSettings()
            }, size = 32.dp)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(nx.accent)
                    .clickable {
                        nxCrunch(context, state.hapticsOn)
                        state.mode = LaunchMode.SAVE_LAUNCH
                        state.runLaunch()
                    }
                    .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TacoIcon(TacoIcon.Play, nx.onAccent, 13.dp)
                    if (!compact) {
                        Spacer(Modifier.width(5.dp))
                        Text("Save & Launch", style = NxType.caption.copy(color = nx.onAccent))
                    }
                }
            }
        }
    }
}

@Composable
private fun bottomNav(state: TacoState, current: String, onSelect: (String) -> Unit) {
    val nx = LocalNx.current
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().height(66.dp)
            .background(nx.bg.copy(alpha = 0.95f))
            .border(androidx.compose.foundation.BorderStroke(1.dp, nx.border.copy(0.5f)))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TABS.forEach { tab ->
            val selected = current == tab.route
            val tint = if (selected) nx.accent else nx.faint
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.90f else 1f, tween(90))
            Column(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(
                        if (selected) nx.accent.copy(if (pressed) 0.24f else 0.14f)
                        else if (pressed) nx.surfaceAlt else Color.Transparent
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null
                    ) {
                        nxTick(context, state.hapticsOn)
                        onSelect(tab.route)
                    }
                    .padding(horizontal = if (nx.bg.luminance() < 0.5f) 10.dp else 12.dp,
                        vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TacoIcon(tab.icon, tint, 20.dp)
                Spacer(Modifier.height(3.dp))
                Text(tab.label, style = NxType.caption.copy(color = tint), maxLines = 1)
            }
        }
    }
}
