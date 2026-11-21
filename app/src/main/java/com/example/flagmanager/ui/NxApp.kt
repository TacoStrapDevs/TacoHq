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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var showTarget by remember { mutableStateOf(false) }
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
                slimHeader(
                    state = state,
                    title = routeTitle(route),
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
                            else -> NxHomeScreen(
                                state = state,
                                onNavigate = { target -> route = target },
                                onPickTarget = { showTarget = true }
                            )
                        }
                    }
                }

                actionBar(
                    state = state,
                    onPickTarget = { showTarget = true }
                )
                bottomNav(state = state, current = route) { route = it }
            }

            if (showTarget) {
                targetDialog(state, context) { showTarget = false }
            }

            SnackbarHost(
                hostState = state.snackbar,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding().padding(bottom = 136.dp)
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

private fun routeTitle(route: String): String = when (route) {
    "flags" -> "FastFlags"
    "library" -> "Library"
    "games" -> "Games"
    "tools" -> "Tools"
    "settings" -> "Settings"
    else -> "Home"
}

/** Minimal header: wordmark tick + current section. No logo tile. */
@Composable
private fun slimHeader(
    state: TacoState,
    title: String,
    onSettings: () -> Unit,
    onTheme: () -> Unit
) {
    val nx = LocalNx.current
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(54.dp)
            .background(nx.bg.copy(alpha = 0.94f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(3.dp, 17.dp).clip(RoundedCornerShape(2.dp)).background(nx.accent))
        Spacer(Modifier.width(9.dp))
        Text(title, style = NxType.title)
        Spacer(Modifier.width(7.dp))
        Text("TACOSTRAP", style = NxType.monoSmall, color = nx.faint)
        Spacer(Modifier.weight(1f))
        val pending = state.pendingCount()
        if (pending > 0) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(nx.warn))
            Spacer(Modifier.width(4.dp))
            Text("$pending", style = NxType.monoSmall, color = nx.warn)
            Spacer(Modifier.width(8.dp))
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
    }
}

/** Voidstrap-style bottom action bar: target on the left, save actions on the right. */
@Composable
private fun actionBar(state: TacoState, onPickTarget: () -> Unit) {
    val nx = LocalNx.current
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 400.dp
        Row(
            Modifier.fillMaxWidth()
                .background(nx.bg.copy(alpha = 0.97f))
                .border(androidx.compose.foundation.BorderStroke(1.dp, nx.border.copy(0.6f)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp))
                    .background(nx.surfaceAlt)
                    .border(1.dp, nx.border, RoundedCornerShape(11.dp))
                    .clickable {
                        nxTick(context, state.hapticsOn)
                        onPickTarget()
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape)
                        .background(if (state.shizuku.ok || state.useRoot) nx.success else nx.danger))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        state.targetLabel(), style = NxType.caption, color = nx.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    TacoIcon(TacoIcon.Down, nx.dim, 13.dp)
                }
            }
            Spacer(Modifier.width(8.dp))
            NxButton("Save", {
                nxTick(context, state.hapticsOn)
                state.applyDraft()
                NxActivity.add(context, "Flags saved", "${state.draftFlags.size} in draft", "flag")
            }, Modifier.width(if (narrow) 52.dp else 78.dp), NxButtonKind.Secondary, height = 40.dp)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.clip(RoundedCornerShape(11.dp)).background(nx.accent)
                    .clickable {
                        nxCrunch(context, state.hapticsOn)
                        state.runLaunch()
                    }
                    .height(40.dp)
                    .padding(horizontal = if (narrow) 12.dp else 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TacoIcon(TacoIcon.Play, nx.onAccent, 14.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (narrow) state.mode.label.substringBefore(" ").trim()
                            .ifEmpty { state.mode.label } else state.mode.label,
                        style = NxType.caption.copy(color = nx.onAccent),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun targetDialog(state: TacoState, context: android.content.Context, onDismiss: () -> Unit) {
    val nx = LocalNx.current
    NxDialog("Launch target", onDismiss, confirm = "Done") {
        Text("Home (Roblox app)", style = NxType.body,
            modifier = Modifier.clickable {
                nxTick(context, state.hapticsOn)
                state.targetGame = null; onDismiss()
            }.padding(vertical = 10.dp))
        state.games.forEach { (name, g) ->
            Row(Modifier.fillMaxWidth().clickable {
                nxTick(context, state.hapticsOn)
                state.targetGame = name; onDismiss()
            }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape)
                    .background(if (state.targetGame == name) nx.accent else nx.border))
                Spacer(Modifier.width(10.dp))
                Text("$name · ${g.placeId}", style = NxType.body)
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
