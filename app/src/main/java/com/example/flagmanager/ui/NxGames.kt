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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.flagmanager.TacoIcon
import com.example.flagmanager.TacoState
import com.example.flagmanager.parsePlaceId

@Composable
fun NxGamesScreen(state: TacoState) {
    val nx = LocalNx.current
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var assignFor by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Games", style = NxType.display)
        Text("Pin experiences and launch them with their own profile.", style = NxType.caption)
        Spacer(Modifier.height(14.dp))

        NxButton("Pin a game", {
            name = ""; place = ""; showAdd = true
        }, Modifier.fillMaxWidth(), NxButtonKind.Primary, icon = TacoIcon.Plus)
        Spacer(Modifier.height(18.dp))

        NxSection("Pinned (${state.games.size})")
        if (state.games.isEmpty()) {
            NxCard {
                NxEmpty(TacoIcon.Gamepad, "No pinned games",
                    "Add a place ID to launch directly into an experience.",
                    actionLabel = "Add game") { showAdd = true }
            }
        }
        state.games.forEach { (gameName, game) ->
            val isTarget = state.targetGame == gameName
            NxCard(accentTint = if (isTarget) 0.6f else 0f, corner = 16.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                            .background(nx.accent.copy(0.14f)),
                        contentAlignment = Alignment.Center
                    ) { TacoIcon(TacoIcon.Gamepad, nx.accent, 21.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(gameName, style = NxType.body)
                            if (isTarget) {
                                Spacer(Modifier.width(6.dp))
                                NxPill("TARGET", nx.accent)
                            }
                        }
                        Text("#${game.placeId} · ${game.profile ?: "base profile"}",
                            style = NxType.monoSmall)
                    }
                    NxIconButton(TacoIcon.Play, {
                        nxCrunch(context, state.hapticsOn)
                        state.targetGame = gameName
                        state.launchGameById(game.placeId)
                        NxActivity.add(context, "Launched $gameName", "#${game.placeId}", "launch")
                    }, size = 34.dp, tint = nx.accent)
                    Spacer(Modifier.width(5.dp))
                    NxIconButton(TacoIcon.Layers, { nxTick(context, state.hapticsOn); assignFor = gameName }, size = 34.dp)
                    Spacer(Modifier.width(5.dp))
                    NxIconButton(TacoIcon.Trash, {
                        nxTick(context, state.hapticsOn)
                        state.removeGame(gameName)
                        NxActivity.add(context, "Unpinned $gameName", kind = "flag")
                    }, size = 34.dp, tint = nx.danger)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))
        NxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    .background(nx.accent2.copy(0.12f)), contentAlignment = Alignment.Center) {
                    TacoIcon(TacoIcon.Bolt, nx.accent2, 17.dp)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Paste a full Roblox game link, a place ID, or 'placeId=12345'. " +
                        "Each pinned game can use a different FastFlag profile.",
                    style = NxType.caption, modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showAdd) {
        NxDialog("Pin a game", { showAdd = false }, confirm = "Pin", onConfirm = {
            val id = parsePlaceId(place)
            if (name.isNotBlank() && id != null) {
                state.addGame(name.trim(), id)
                NxActivity.add(context, "Pinned ${name.trim()}", "#$id", "flag")
            }
        }) {
            Text("Name", style = NxType.label)
            Spacer(Modifier.height(6.dp))
            NxSearch(name, { name = it }, placeholder = "e.g. Brookhaven RP")
            Spacer(Modifier.height(12.dp))
            Text("Place ID or link", style = NxType.label)
            Spacer(Modifier.height(6.dp))
            NxSearch(place, { place = it }, placeholder = "https://roblox.com/games/12345/…")
            if (place.isNotBlank() && parsePlaceId(place) == null) {
                Spacer(Modifier.height(6.dp))
                Text("Could not read a place ID from that text.", style = NxType.caption,
                    color = nx.danger)
            }
        }
    }

    assignFor?.let { gameName ->
        NxDialog("Profile for $gameName", { assignFor = null }) {
            Text("Base profile", style = NxType.body,
                modifier = Modifier.clickable {
                    state.setGameProfile(gameName, null); assignFor = null
                }.padding(vertical = 10.dp))
            state.layers.filter { it.id != "base" }.forEach { layer ->
                val active = state.games[gameName]?.profile == layer.name
                Row(Modifier.fillMaxWidth().clickable {
                    state.setGameProfile(gameName, layer.name); assignFor = null
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50))
                        .background(if (active) nx.accent else nx.border))
                    Spacer(Modifier.width(10.dp))
                    Text(layer.name, style = NxType.body,
                        color = if (active) nx.accent else nx.text)
                }
            }
        }
    }
}
