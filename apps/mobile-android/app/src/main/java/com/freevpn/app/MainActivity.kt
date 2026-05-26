package com.freevpn.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class VpnRegion(
    val code: String,
    val country: String,
    val city: String,
    val latencyMs: Int,
    val loadPercent: Int,
    val supportsIpv6: Boolean,
)

class MainActivity : ComponentActivity() {
    private val connectedState = mutableStateOf(false)
    private val autoReconnectState = mutableStateOf(true)

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnTunnel()
            connectedState.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoReconnectState.value = VpnPrefs.isAutoReconnectEnabled(this)
        setContent {
            MaterialTheme {
                VpnHomeScreen(
                    connected = connectedState.value,
                    autoReconnect = autoReconnectState.value,
                    onToggleAutoReconnect = {
                        autoReconnectState.value = !autoReconnectState.value
                        VpnPrefs.setAutoReconnectEnabled(this, autoReconnectState.value)
                    },
                    onToggleConnection = {
                        if (connectedState.value) {
                            stopVpnTunnel()
                            connectedState.value = false
                        } else {
                            requestVpnPermissionAndConnect()
                        }
                    }
                )
            }
        }
    }

    private fun requestVpnPermissionAndConnect() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnTunnel()
            connectedState.value = true
        }
    }

    private fun startVpnTunnel() {
        val intent = Intent(this, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_CONNECT
        }
        startService(intent)
    }

    private fun stopVpnTunnel() {
        val intent = Intent(this, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }
}

@Composable
fun VpnHomeScreen(
    connected: Boolean,
    autoReconnect: Boolean,
    onToggleAutoReconnect: () -> Unit,
    onToggleConnection: () -> Unit
) {
    val regions = listOf(
        VpnRegion("US-NY", "United States", "New York", 39, 56, true),
        VpnRegion("DE-FRA", "Germany", "Frankfurt", 63, 48, true),
        VpnRegion("NL-AMS", "Netherlands", "Amsterdam", 58, 61, false),
        VpnRegion("JP-TYO", "Japan", "Tokyo", 92, 42, true)
    )

    var selected by remember { mutableStateOf(regions.first()) }
    var protocol by remember { mutableStateOf("WireGuard") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("FreeVPN", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Server: ${selected.country} / ${selected.city} (${selected.code})")
        Text("Latency: ${selected.latencyMs} ms · Load: ${selected.loadPercent}%")
        Text("Protocol: $protocol")
        Text("IPv6: ${if (selected.supportsIpv6) "Enabled" else "Disabled"}")

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onToggleConnection) {
                Text(if (connected) "Disconnect" else "Connect")
            }
            Button(onClick = {
                protocol = if (protocol == "WireGuard") "Hysteria2" else "WireGuard"
            }) {
                Text("Switch Protocol")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Auto reconnect: ${if (autoReconnect) "ON" else "OFF"}",
            modifier = Modifier.clickable(onClick = onToggleAutoReconnect)
        )

        Spacer(Modifier.height(16.dp))
        Text("Live Session (MVP mock)", style = MaterialTheme.typography.titleMedium)
        Text("Status: ${if (connected) "Connected" else "Disconnected"}")
        Text("Download: 38.4 Mbps · Upload: 11.7 Mbps")
        Text("Packet loss: 0.4% · Jitter: 7 ms")

        Spacer(Modifier.height(16.dp))
        Text("Available regions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(regions) { region ->
                RegionCard(region = region, isSelected = selected.code == region.code) {
                    selected = region
                }
            }
        }
    }
}

@Composable
fun RegionCard(region: VpnRegion, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Column {
                Text("${region.country} - ${region.city}")
                Text("${region.latencyMs} ms · load ${region.loadPercent}%")
            }
            Spacer(Modifier.weight(1f))
            Text(if (isSelected) "Selected" else "")
        }
    }
}
