package com.example.matterhome.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.matter.api.DeviceAvailability
import com.example.matter.api.DeviceType
import com.example.matter.api.MatterDevice
import com.example.matterhome.AppViewModel
import com.example.matterhome.theme.Green

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    contentPadding: PaddingValues,
    onAddDevice: () -> Unit,
    onDevice: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Matter Home", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(state.home.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDevice) {
                Icon(Icons.Default.Add, contentDescription = "Add device")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    item {
                        FilterChip(
                            selected = state.selectedRoomId == null,
                            onClick = { viewModel.selectRoom(null) },
                            label = { Text("All") },
                        )
                    }
                    items(state.rooms, key = { it.id }) { room ->
                        FilterChip(
                            selected = state.selectedRoomId == room.id,
                            onClick = { viewModel.selectRoom(room.id) },
                            label = { Text(room.name) },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawCircle(Green) }
                    }
                    Text("Local control", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text("${state.devices.count { it.availability == DeviceAvailability.ONLINE }} of ${state.devices.size} synchronized", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.visibleDevices.isEmpty()) {
                item { EmptyRoomState() }
            } else {
                items(state.visibleDevices, key = { it.id }) { device ->
                    DeviceRow(
                        device = device,
                        onOpen = { onDevice(device.id) },
                        onPower = { viewModel.setPower(device.id, !device.isOn) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: MatterDevice, onOpen: () -> Unit, onPower: () -> Unit) {
    val online = device.availability == DeviceAvailability.ONLINE
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (device.type == DeviceType.LIGHT) Icons.Default.Lightbulb else Icons.Default.ElectricalServices,
                contentDescription = null,
                tint = if (device.isOn && online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(device.room.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (online) "Online · ${if (device.isOn) "On" else "Off"}" else "Offline · Last state ${if (device.isOn) "on" else "off"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (online) Green else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPower, enabled = online, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = if (device.isOn) "Turn ${device.name} off" else "Turn ${device.name} on",
                    tint = if (device.isOn && online) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
            }
        }
    }
}

@Composable
private fun EmptyRoomState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No devices in this room", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Choose another room or add a device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
