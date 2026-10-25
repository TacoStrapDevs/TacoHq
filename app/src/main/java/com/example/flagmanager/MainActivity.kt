package com.example.flagmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val bg = Color(0xFF17120F)
    val panel = Color(0xFF3A3532)
    val softPanel = Color(0xFF433E3A)
    val text = Color(0xFFF0E9E2)
    val muted = Color(0xFFB9B0A8)
    val gold = Color(0xFFE2A02A)
    val goldDark = Color(0xFFD9981B)

    var page by remember { mutableStateOf(3) }
    var texture by remember { mutableStateOf("Automatic") }
    var aa by remember { mutableStateOf("Automatic") }
    var render by remember { mutableStateOf("Automatic") }
    var graySky by remember { mutableStateOf(false) }
    var disableShadows by remember { mutableStateOf(false) }
    var disablePostFx by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            background = bg,
            surface = bg,
            surfaceVariant = panel,
            primary = gold,
            onPrimary = Color(0xFF1F150E),
            onSurface = text,
            onSurfaceVariant = muted,
            outline = Color(0xFF5A514D)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("6:19", color = text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("◔", color = text, fontSize = 18.sp)
                        Text("◍", color = text, fontSize = 17.sp)
                        Text("◫", color = text, fontSize = 18.sp)
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2929))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        )
                        Text("53", color = text, fontSize = 18.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "FastFlags",
                        fontSize = 58.sp,
                        letterSpacing = (-1.4).sp,
                        lineHeight = 60.sp,
                        color = text,
                        fontWeight = FontWeight.W400
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Settings for your current profile.",
                        fontSize = 22.sp,
                        color = text.copy(alpha = 0.92f),
                        fontWeight = FontWeight.W400
                    )
                }

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(gold.copy(alpha = 0.98f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("!", color = Color(0xFF1F1A16), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "Roblox may ignore flags\noutside its allowlist.",
                            color = text,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.W400
                        )
                    }
                    Text(
                        text = "Open editor",
                        color = gold,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "GRAPHICS",
                    color = text.copy(alpha = 0.82f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.padding(start = 20.dp)
                )

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { SettingRow("Texture quality", "Sharper textures use more memory", texture) }
                    item { SettingRow("Anti aliasing", "Smooths jagged edges", aa) }
                    item { SettingRow("Rendering mode", "Graphics engine Roblox uses", render) }
                    item {
                        ToggleRow(
                            title = "Gray sky",
                            subtitle = "Replaces the sky with plain gray",
                            checked = graySky,
                            onCheckedChange = { graySky = it }
                        )
                    }
                    item {
                        ToggleRow(
                            title = "Disable shadows",
                            subtitle = "Turns off Roblox shadows",
                            checked = disableShadows,
                            onCheckedChange = { disableShadows = it }
                        )
                    }
                    item {
                        ToggleRow(
                            title = "Disable post-processing",
                            subtitle = "Turns off bloom and color effects",
                            checked = disablePostFx,
                            onCheckedChange = { disablePostFx = it }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF7C5A2A))
                            .border(1.dp, Color(0xFFCC8B2D), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Roblox", color = text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(goldDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Save", color = Color(0xFF1E140E), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavItem("Home", page == 0) { page = 0 }
                    NavItem("Library", page == 1) { page = 1 }
                    NavItem("Mods", page == 2) { page = 2 }
                    NavItem("FastFlags", page == 3) { page = 3 }
                    NavItem("Settings", page == 4) { page = 4 }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, value: String) {
    val bg = Color(0xFF3B3532)
    val text = Color(0xFFF2EAE5)
    val muted = Color(0xFFB7AEA8)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = text, fontSize = 24.sp, fontWeight = FontWeight.W400)
            Text(subtitle, color = muted, fontSize = 15.sp, lineHeight = 18.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = text, fontSize = 18.sp, fontWeight = FontWeight.W400)
            Spacer(Modifier.width(6.dp))
            Text("▾", color = text, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val text = Color(0xFFF2EAE5)
    val muted = Color(0xFFB7AEA8)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = text, fontSize = 24.sp, fontWeight = FontWeight.W400)
            Text(subtitle, color = muted, fontSize = 15.sp, lineHeight = 18.sp)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFF2EAE5),
                checkedTrackColor = Color(0xFF8F8C88),
                uncheckedThumbColor = Color(0xFFF2EAE5),
                uncheckedTrackColor = Color(0xFF605A56),
                checkedBorderColor = Color.Transparent,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) Color(0xFFF1E9E2) else Color(0xFFB9B1AA)
    val size = if (selected) 18.sp else 16.sp

    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFFB07D25) else Color.Transparent)
        )
        Spacer(Modifier.height(8.dp))
        Text(label, color = tint, fontSize = size, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}
