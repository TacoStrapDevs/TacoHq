@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.flagmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flagmanager.FS
import com.example.flagmanager.Opt
import com.example.flagmanager.SECTIONS
import com.example.flagmanager.TacoIcon
import com.example.flagmanager.TacoState
import com.example.flagmanager.fsKeys

@Composable
fun NxFlagsScreen(state: TacoState) {
    val nx = LocalNx.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    val cats = listOf("All") + SECTIONS.map { it.first } + "Custom"

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("FastFlags", style = NxType.display)
            Text("Tune the client. Changes stay in draft until saved.", style = NxType.caption)
            Spacer(Modifier.height(12.dp))
            NxSearch(query, { query = it }, placeholder = "Search flags, keys, values…")
            Spacer(Modifier.height(10.dp))
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)) {
            cats.forEachIndexed { i, c ->
                val active = i == category
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (active) nx.accent else nx.surfaceAlt)
                        .clickable { category = i }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(c, style = NxType.caption,
                        color = if (active) nx.onAccent else nx.dim)
                }
                Spacer(Modifier.width(7.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp)
        ) {
            val q = query.trim()
            val filtered = SECTIONS.filter { (name, _) -> category == 0 || name == cats.getOrNull(category) }
                .map { (name, items) ->
                    name to items.filter { fs -> matches(fs, q, state) }
                }.filter { it.second.isNotEmpty() }

            if (category == 0 || category == cats.lastIndex) {
                val custom = state.draftFlags.keys
                    .filter { k -> !SECTIONS.any { (_, items) -> items.any { fs -> fsKeys(fs).contains(k) } } }
                    .filter { k -> q.isBlank() || k.contains(q, true) || (state.draftFlags[k] ?: "").contains(q, true) }
                if (custom.isNotEmpty() || category == cats.lastIndex) {
                    NxSection("Manual flags", action = "+ Add") { showAdd = true }
                    custom.forEach { key ->
                        manualRow(state, key, context)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (custom.isEmpty()) {
                        NxCard {
                            Text("No manual flags yet. Add any key/value pair not covered by presets.",
                                style = NxType.caption)
                            Spacer(Modifier.height(10.dp))
                            NxButton("Add flag", { showAdd = true },
                                kind = NxButtonKind.Secondary, height = 38.dp)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            filtered.forEach { (name, items) ->
                NxSection(name)
                items.forEach { fs ->
                    fsRow(fs, state, context)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (filtered.isEmpty() && (category != cats.lastIndex || state.draftFlags.isEmpty())) {
                NxCard { NxEmpty(TacoIcon.Search, "No flags found", "Try a different search or category.") }
            }

            Spacer(Modifier.height(14.dp))
            NxDivider()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                NxButton("Discard", { state.discardDraft() },
                    Modifier.weight(1f), NxButtonKind.Ghost)
                Spacer(Modifier.width(9.dp))
                NxButton("Save ${state.pendingCount()}", { state.applyDraft() },
                    Modifier.weight(1.4f), NxButtonKind.Primary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Allowlist-aware: flags Roblox does not honor locally are marked. " +
                    "FastFlags modify rendering and networking only — they are not exploits.",
                style = NxType.caption
            )
        }
    }

    if (showAdd) {
        addDialog(state, { showAdd = false })
    }
}

@Composable
private fun matches(fs: FS, q: String, state: TacoState): Boolean {
    if (q.isBlank()) return true
    if (fs.title.contains(q, true) || fs.sub.contains(q, true)) return true
    return fsKeys(fs).any { it.contains(q, true) }
}

@Composable
private fun fsRow(fs: FS, state: TacoState, context: android.content.Context) {
    val nx = LocalNx.current
    val draft = state.draftFlags
    when (fs) {
        is FS.Toggle -> {
            val on = fs.sets.all { (k, v) -> draft[k] == v }
            NxCard(onClick = {
                draft.entries.removeAll { e -> fs.sets.containsKey(e.key) }
                if (!on) fs.sets.forEach { (k, v) -> draft[k] = v }
                NxActivity.add(context, if (!on) "Enabled ${fs.title}" else "Disabled ${fs.title}",
                    kind = "flag")
            }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(fs.title, style = NxType.body)
                        Text(fs.sub, style = NxType.caption)
                    }
                    Spacer(Modifier.width(8.dp))
                    NxSwitch(on, { want ->
                        draft.entries.removeAll { e -> fs.sets.containsKey(e.key) }
                        if (want) fs.sets.forEach { (k, v) -> draft[k] = v }
                        NxActivity.add(context, if (want) "Enabled ${fs.title}" else "Disabled ${fs.title}",
                            kind = "flag")
                    })
                }
            }
        }
        is FS.Choice -> {
            val current = fs.opts.indexOfFirst { opt -> opt.sets.all { (k, v) -> draft[k] == v } }
                .let { if (it < 0) 0 else it }
            NxCard(accentTint = if (current > 0) 0.4f else 0f) {
                Text(fs.title, style = NxType.body)
                Text(fs.sub, style = NxType.caption)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    fs.opts.forEachIndexed { i, opt ->
                        val active = i == current
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .background(if (active) nx.accent.copy(0.22f) else nx.surface)
                                .border(1.dp, if (active) nx.accent else nx.border, RoundedCornerShape(9.dp))
                                .clickable {
                                    fsKeys(fs).forEach { draft.remove(it) }
                                    opt.sets.forEach { (k, v) -> draft[k] = v }
                                }
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Text(opt.label, style = NxType.caption,
                                color = if (active) nx.accent else nx.dim)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }
        is FS.Num, is FS.Range -> {
            val key = if (fs is FS.Num) fs.key else (fs as FS.Range).a
            val value = draft[key] ?: ""
            var text by remember(key) { mutableStateOf(value) }
            NxCard {
                Text(fs.title, style = NxType.body)
                Text(fs.sub, style = NxType.caption)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(key, style = NxType.monoSmall, color = nx.faint,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(88.dp).clip(RoundedCornerShape(9.dp))
                        .background(nx.bgDeep)
                        .border(1.dp, nx.border, RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = text,
                            onValueChange = { v ->
                                text = v
                                if (v.isNotBlank()) draft[key] = v else draft.remove(key)
                            },
                            singleLine = true,
                            textStyle = NxType.mono,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(nx.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun manualRow(state: TacoState, key: String, context: android.content.Context) {
    val nx = LocalNx.current
    var value by remember(key) { mutableStateOf(state.draftFlags[key] ?: "") }
    val allowed = state.isAllowed(key)
    NxCard(accentTint = 0.25f) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(key, style = NxType.mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!allowed) {
                        Spacer(Modifier.width(6.dp))
                        NxPill("local", nx.faint)
                    }
                }
                Text(if (state.isApplied(key, state.draftFlags[key])) "applied" else "draft",
                    style = NxType.caption)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(90.dp).clip(RoundedCornerShape(9.dp))
                .background(nx.bgDeep)
                .border(1.dp, nx.border, RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = { v ->
                        value = v
                        if (v.isNotBlank()) state.draftFlags[key] = v else state.draftFlags.remove(key)
                    },
                    singleLine = true,
                    textStyle = NxType.mono,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(nx.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(6.dp))
            NxIconButton(TacoIcon.Trash, {
                state.draftFlags.remove(key)
                NxActivity.add(context, "Removed $key", kind = "flag")
            }, size = 30.dp, tint = nx.danger)
        }
    }
}

@Composable
private fun addDialog(state: TacoState, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("True") }
    var kind by remember { mutableStateOf(0) }
    val nx = LocalNx.current
    val context = LocalContext.current
    NxDialog(
        "Add a flag",
        onDismiss,
        confirm = "Add",
        onConfirm = {
            if (key.isNotBlank()) {
                state.draftFlags[key.trim()] = value.trim()
                NxActivity.add(context, "Added ${key.trim()}", value, "flag")
            }
        }
    ) {
        Text("Key", style = NxType.label)
        Spacer(Modifier.height(6.dp))
        NxSearch(key, { key = it }, placeholder = "FFlagSomething / DFIntSomething")
        Spacer(Modifier.height(12.dp))
        Text("Type", style = NxType.label)
        Spacer(Modifier.height(6.dp))
        NxSegmented(listOf("Bool", "Number", "Text"), selected = kind, onSelect = { kind = it })
        Spacer(Modifier.height(12.dp))
        Text("Value", style = NxType.label)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (kind == 0) {
                NxSwitch(value.equals("true", true), {
                    value = if (it) "True" else "False"
                })
                Spacer(Modifier.width(10.dp))
                Text(value, style = NxType.mono, color = nx.accent)
            } else {
                NxSearch(value, { value = it }, placeholder = "Value")
            }
        }
    }
}
