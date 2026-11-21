@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.flagmanager.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.flagmanager.FlagLayer
import com.example.flagmanager.TacoIcon
import com.example.flagmanager.TacoState

@Composable
fun NxLibraryScreen(state: TacoState) {
    val nx = LocalNx.current
    val context = LocalContext.current
    var newName by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Profiles", style = NxType.display)
        Text("Layer FastFlags. Top profiles override the ones below.", style = NxType.caption)
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth()) {
            NxButton("Save current", {
                state.saveLayerFromCurrent("Profile ${state.layers.size + 1}")
                NxActivity.add(context, "Profile created", "From current flags", "flag")
            }, Modifier.weight(1f), NxButtonKind.Primary, icon = TacoIcon.Plus)
            Spacer(Modifier.width(8.dp))
            NxButton("Paste", {
                state.importLayerFromClipboard()
                NxActivity.add(context, "Profile imported", "From clipboard", "flag")
            }, Modifier.weight(0.7f), NxButtonKind.Secondary, icon = TacoIcon.Copy)
        }
        Spacer(Modifier.height(18.dp))

        NxSection("Active layers (${state.layers.size})")
        state.layers.forEachIndexed { index, layer ->
            layerCard(state, layer, index, context)
            Spacer(Modifier.height(9.dp))
        }

        Spacer(Modifier.height(12.dp))
        NxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    .background(nx.accent2.copy(0.12f)), contentAlignment = Alignment.Center) {
                    TacoIcon(TacoIcon.Info, nx.accent2, 17.dp)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Profiles can be assigned to pinned games from the Games tab. " +
                        "Disabled layers stay saved but are not merged.",
                    style = NxType.caption, modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showNew) {
        NxDialog("New profile", { showNew = false }, confirm = "Create", onConfirm = {
            if (newName.isNotBlank()) state.saveLayerFromCurrent(newName.trim())
        }) {
            NxSearch(newName, { newName = it }, placeholder = "Profile name")
        }
    }
}

@Composable
private fun layerCard(state: TacoState, layer: FlagLayer, index: Int, context: android.content.Context) {
    val nx = LocalNx.current
    var rename by remember(layer.id) { mutableStateOf(false) }
    var nameText by remember(layer.id) { mutableStateOf(layer.name) }
    val isBase = layer.id == "base"

    NxCard(accentTint = if (layer.enabled) 0.45f else 0f) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (layer.enabled) nx.accent.copy(0.16f) else nx.surface)
                    .border(1.dp, if (layer.enabled) nx.accent else nx.border, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}", style = NxType.mono, color = if (layer.enabled) nx.accent else nx.faint)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (rename) {
                    NxSearch(nameText, {
                        nameText = it
                    }, placeholder = "Name")
                } else {
                    Text(layer.name, style = NxType.body)
                }
                Text("${layer.flags.size} flags", style = NxType.caption)
            }
            if (!isBase) NxSwitch(layer.enabled, { state.setLayerEnabled(layer.id, it) })
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (rename) {
                NxButton("Rename", {
                    state.renameLayer(layer.id, nameText.trim())
                    rename = false
                }, Modifier.weight(1f), NxButtonKind.Primary, height = 34.dp)
                Spacer(Modifier.width(6.dp))
                NxButton("Cancel", { rename = false; nameText = layer.name },
                    Modifier.weight(1f), NxButtonKind.Ghost, height = 34.dp)
            } else {
                if (!isBase) {
                    NxIconButton(TacoIcon.Up, {
                        if (index > 0) state.moveLayer(index, index - 1)
                    }, size = 32.dp)
                    Spacer(Modifier.width(5.dp))
                    NxIconButton(TacoIcon.Down, {
                        if (index < state.layers.lastIndex) state.moveLayer(index, index + 1)
                    }, size = 32.dp)
                    Spacer(Modifier.width(5.dp))
                    NxIconButton(TacoIcon.Type, { rename = true }, size = 32.dp)
                    Spacer(Modifier.width(5.dp))
                    NxIconButton(TacoIcon.Copy, {
                        state.duplicateLayer(layer.id)
                        NxActivity.add(context, "Profile duplicated", layer.name, "flag")
                    }, size = 32.dp)
                    Spacer(Modifier.width(5.dp))
                    NxIconButton(TacoIcon.Trash, {
                        state.deleteLayer(layer.id)
                        NxActivity.add(context, "Profile deleted", layer.name, "flag")
                    }, size = 32.dp, tint = nx.danger)
                } else {
                    NxPill("BASE", nx.dim)
                    Spacer(Modifier.weight(1f))
                    Text("Always active", style = NxType.caption)
                }
            }
        }
    }
}
