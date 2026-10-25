package com.example.flagmanager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val ROBLOX_PKG = "com.roblox.client"
private const val REGION_API_URL = "https://your-backend.example.com/api/roblox-region"

private fun saveSelectedRegion(ctx: Context, region: String) {
    val file = File(ctx.filesDir, "selected_region.json")
    val json = JSONObject()
    json.put("region", region)
    file.writeText(json.toString())
}

private fun loadSelectedRegion(ctx: Context): String {
    val file = File(ctx.filesDir, "selected_region.json")
    if (!file.exists()) return "Singapore"

    return try {
        val json = JSONObject(file.readText())
        json.optString("region", "Singapore")
    } catch (_: Exception) {
        "Singapore"
    }
}

private fun launchRoblox(ctx: Context): Boolean {
    return try {
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ROBLOX_PKG)
        if (launchIntent != null) {
            ctx.startActivity(launchIntent)
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}

private fun fetchActualRegion(jobId: String, onResult: (String?) -> Unit) {
    val encodedJobId = URLEncoder.encode(jobId, "UTF-8")
    val url = URL("$REGION_API_URL?jobId=$encodedJobId")

    Thread {
        var region: String? = null
        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doInput = true
            val code = connection.responseCode
            if (code in 200..299) {
                val body = connection.inputStream.bufferedReader().readText()
                if (body.isNotBlank()) {
                    region = JSONObject(body).optString("region", null)
                }
            }
        } catch (_: Exception) {
            region = null
        }

        onResult(region)
    }.start()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedRegion by remember { mutableStateOf(loadSelectedRegion(context)) }
    var actualRegion by remember { mutableStateOf<String?>(null) }
    var regionJobId by remember { mutableStateOf("") }
    var regionStatus by remember { mutableStateOf("No backend connected") }
    var regionMenuExpanded by remember { mutableStateOf(false) }
    var regionFetchRunning by remember { mutableStateOf(false) }

    val regionOptions = listOf("Singapore", "US East", "US West", "EU", "Asia")

    Column(
        modifier = Modifier
            .background(Color(0xFF121110))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Region + join status",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF2ECE6)
        )

        Text(
            text = if (actualRegion != null) {
                "Actual joined region: $actualRegion"
            } else {
                "Selected region: $selectedRegion"
            },
            fontSize = 16.sp,
            color = Color(0xFFB69CFF)
        )

        OutlinedTextField(
            value = regionJobId,
            onValueChange = { regionJobId = it },
            label = { Text("Roblox Job ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (regionJobId.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("Enter a Roblox Job ID first") }
                        return@Button
                    }

                    regionFetchRunning = true
                    regionStatus = "Checking region..."
                    fetchActualRegion(regionJobId) { region ->
                        actualRegion = region
                        regionFetchRunning = false

                        if (region != null) {
                            regionStatus = "Region reported: $region"
                            scope.launch { snackbarHostState.showSnackbar("Joined region: $region") }
                        } else {
                            regionStatus = "No region reported yet"
                            scope.launch { snackbarHostState.showSnackbar("No region reported yet") }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (regionFetchRunning) "Checking..." else "Check region")
            }

            Button(
                onClick = {
                    saveSelectedRegion(context, selectedRegion)
                    val ok = launchRoblox(context)
                    if (!ok) {
                        scope.launch { snackbarHostState.showSnackbar("Roblox is not installed") }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Launching Roblox — selected: $selectedRegion") }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Launch Roblox")
            }
        }

        Box {
            OutlinedButton(
                onClick = { regionMenuExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose selected region")
            }

            DropdownMenu(
                expanded = regionMenuExpanded,
                onDismissRequest = { regionMenuExpanded = false }
            ) {
                regionOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedRegion = option
                            actualRegion = null
                            saveSelectedRegion(context, option)
                            regionMenuExpanded = false
                            scope.launch { snackbarHostState.showSnackbar("Region saved: $option") }
                        }
                    )
                }
            }
        }

        Text(
            text = regionStatus,
            fontSize = 13.sp,
            color = Color(0xFFB5ADA4)
        )

        SnackbarHost(hostState = snackbarHostState)
    }
}
