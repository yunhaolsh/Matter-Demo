package com.example.matterhome.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.matter.api.DeviceAvailability
import com.example.matter.api.DeviceType
import com.example.matterhome.AppViewModel
import com.example.matterhome.theme.Green

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    viewModel: AppViewModel,
    deviceId: String,
    onBack: () -> Unit,
    onRemoved: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val device = state.devices.firstOrNull { it.id == deviceId }
    var confirmRemoval by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "Device") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { confirmRemoval = true }, enabled = device != null) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove device")
                    }
                },
            )
        },
    ) { padding ->
        if (device == null) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Device no longer exists", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onBack) { Text("Return home") }
            }
        } else {
            val online = device.availability == DeviceAvailability.ONLINE
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(24.dp))
                Icon(
                    if (device.type == DeviceType.PLUG) Icons.Default.ElectricalServices else Icons.Default.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = if (device.isOn && online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(if (device.isOn) "On" else "Off", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (online) "Available now" else "Currently offline",
                    color = if (online) Green else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.setPower(device.id, !device.isOn) },
                        enabled = online,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Text(if (device.isOn) "Turn off" else "Turn on", modifier = Modifier.padding(start = 8.dp))
                    }
                    FilledTonalIconButton(
                        onClick = { viewModel.toggle(device.id) },
                        enabled = online,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Toggle power")
                    }
                }
                Spacer(Modifier.height(40.dp))
                Text("Device information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                PropertyRow("Room", device.room.name)
                PropertyRow("Availability", device.availability.name.lowercase().replaceFirstChar(Char::uppercase))
                PropertyRow("Control path", device.connectionMode.name.lowercase().replaceFirstChar(Char::uppercase))
                PropertyRow("Latest state", if (device.isOn) "On" else "Off")
            }
        }
    }

    if (confirmRemoval && device != null) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text("Remove ${device.name}?") },
            text = { Text("You will need to add this device again before controlling it from Matter Home.") },
            confirmButton = {
                Button(onClick = { viewModel.remove(device.id, onRemoved) }) { Text("Remove") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmRemoval = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}
