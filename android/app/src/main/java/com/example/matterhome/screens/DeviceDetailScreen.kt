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
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.matter.api.DeviceAvailability
import com.example.matter.api.DeviceType
import com.example.matter.api.LockState
import com.example.matter.api.SensorKind
import com.example.matterhome.AppUiState
import com.example.matterhome.AppViewModel
import com.example.matterhome.controls.CapabilityUiRegistry
import com.example.matterhome.controls.CapabilityUiValue
import com.example.matterhome.controls.DeviceControl
import com.example.matterhome.theme.Green
import kotlin.math.roundToInt

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
    LaunchedEffect(deviceId, device?.capabilities) {
        viewModel.loadDeviceControls(deviceId)
    }

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
            MissingDevice(onBack, Modifier.padding(padding))
        } else {
            val online = device.availability == DeviceAvailability.ONLINE
            val groups = CapabilityUiRegistry.controls(device)
            Column(
                Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                DeviceHeader(device.type, device.availability, groups.sumOf { it.controls.size })
                if (device.capabilities == null) {
                    LoadingCapabilities()
                } else if (groups.isEmpty()) {
                    NoProductControls()
                } else {
                    groups.forEachIndexed { index, group ->
                        if (index > 0) Divider(Modifier.padding(vertical = 24.dp))
                        if (groups.size > 1) {
                            Text(
                                "Device section ${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        group.controls.forEach { control ->
                            DynamicControl(viewModel, device.id, device.isOn, control, state, online)
                        }
                    }
                }
                Divider(Modifier.padding(top = 24.dp))
                Spacer(Modifier.height(16.dp))
                Text("Device information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                PropertyRow("Room", device.room.name)
                PropertyRow("Availability", device.availability.name.lowercase().replaceFirstChar(Char::uppercase))
                PropertyRow("Control path", device.connectionMode.name.lowercase().replaceFirstChar(Char::uppercase))
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmRemoval && device != null) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text("Remove ${device.name}?") },
            text = { Text("You will need to add this device again before controlling it from Matter Home.") },
            confirmButton = { Button(onClick = { viewModel.remove(device.id, onRemoved) }) { Text("Remove") } },
            dismissButton = { OutlinedButton(onClick = { confirmRemoval = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DeviceHeader(type: DeviceType, availability: DeviceAvailability, controlCount: Int) {
    val online = availability == DeviceAvailability.ONLINE
    Spacer(Modifier.height(20.dp))
    Icon(
        when (type) {
            DeviceType.LIGHT -> Icons.Default.Lightbulb
            DeviceType.PLUG -> Icons.Default.ElectricalServices
            DeviceType.UNKNOWN -> Icons.Default.DevicesOther
        },
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        when (availability) {
            DeviceAvailability.ONLINE -> "Available now"
            DeviceAvailability.CONNECTING -> "Connecting"
            DeviceAvailability.OFFLINE -> "Currently offline"
        },
        color = if (online) Green else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        "$controlCount discovered ${if (controlCount == 1) "control" else "controls"}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun DynamicControl(
    viewModel: AppViewModel,
    deviceId: String,
    deviceIsOn: Boolean,
    control: DeviceControl,
    state: AppUiState,
    online: Boolean,
) {
    val value = state.capabilityValues[control.key]
    val loading = control.key in state.loadingCapabilities
    when (control) {
        is DeviceControl.Power -> PowerControl(viewModel, deviceId, control, value, deviceIsOn, online, loading)
        is DeviceControl.Level -> LevelControl(viewModel, deviceId, control, value, online, loading)
        is DeviceControl.Color -> ColorControl(viewModel, deviceId, control, value, online, loading)
        is DeviceControl.Lock -> LockControl(viewModel, deviceId, control, value, online, loading)
        is DeviceControl.Climate -> ClimateControl(viewModel, deviceId, control, value, online, loading)
        is DeviceControl.Sensor -> SensorControl(control, value, loading)
        is DeviceControl.Unsupported -> UnsupportedControl(control)
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun PowerControl(
    viewModel: AppViewModel,
    deviceId: String,
    control: DeviceControl.Power,
    value: CapabilityUiValue?,
    deviceIsOn: Boolean,
    online: Boolean,
    loading: Boolean,
) {
    val isOn = (value as? CapabilityUiValue.Power)?.isOn ?: deviceIsOn
    ControlRow(Icons.Default.Lightbulb, "Power", if (isOn) "On" else "Off", loading) {
        Switch(
            checked = isOn,
            onCheckedChange = { viewModel.setPower(deviceId, control, it) },
            enabled = online && !loading && (if (isOn) control.capability.supportsOff else control.capability.supportsOn),
        )
    }
}

@Composable
private fun LevelControl(
    viewModel: AppViewModel,
    deviceId: String,
    control: DeviceControl.Level,
    value: CapabilityUiValue?,
    online: Boolean,
    loading: Boolean,
) {
    val remote = (value as? CapabilityUiValue.Level)?.value ?: control.capability.minimum
    var level by remember(control.key) { mutableFloatStateOf(remote.toFloat()) }
    LaunchedEffect(remote) { level = remote.toFloat() }
    Text("Brightness", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text("${((level / control.capability.maximum) * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(
        value = level,
        onValueChange = { level = it },
        onValueChangeFinished = { viewModel.setLevel(deviceId, control, level.roundToInt()) },
        valueRange = control.capability.minimum.toFloat()..control.capability.maximum.toFloat(),
        enabled = online && !loading && control.capability.supportsMoveToLevel,
    )
}

@Composable
private fun ColorControl(
    viewModel: AppViewModel,
    deviceId: String,
    control: DeviceControl.Color,
    value: CapabilityUiValue?,
    online: Boolean,
    loading: Boolean,
) {
    val color = value as? CapabilityUiValue.Color
    if (control.capability.supportsHueSaturation) {
        var hue by remember(control.key) { mutableFloatStateOf((color?.hue ?: 0).toFloat()) }
        var saturation by remember(control.key) { mutableFloatStateOf((color?.saturation ?: 0).toFloat()) }
        LaunchedEffect(color?.hue, color?.saturation) {
            hue = (color?.hue ?: hue.roundToInt()).toFloat()
            saturation = (color?.saturation ?: saturation.roundToInt()).toFloat()
        }
        Text("Color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Hue", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(hue, { hue = it }, valueRange = 0f..254f, enabled = online && !loading, onValueChangeFinished = {
            viewModel.setColor(deviceId, control, hue.roundToInt(), saturation.roundToInt())
        })
        Text("Saturation", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(saturation, { saturation = it }, valueRange = 0f..254f, enabled = online && !loading, onValueChangeFinished = {
            viewModel.setColor(deviceId, control, hue.roundToInt(), saturation.roundToInt())
        })
    }
    if (control.capability.supportsColorTemperature) {
        var mireds by remember(control.key) { mutableFloatStateOf((color?.temperatureMireds ?: 250).toFloat()) }
        LaunchedEffect(color?.temperatureMireds) { color?.temperatureMireds?.let { mireds = it.toFloat() } }
        Text("Color temperature", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Slider(mireds, { mireds = it }, valueRange = 153f..500f, enabled = online && !loading, onValueChangeFinished = {
            viewModel.setColorTemperature(deviceId, control, mireds.roundToInt())
        })
    }
}

@Composable
private fun LockControl(
    viewModel: AppViewModel,
    deviceId: String,
    control: DeviceControl.Lock,
    value: CapabilityUiValue?,
    online: Boolean,
    loading: Boolean,
) {
    val lockState = (value as? CapabilityUiValue.Lock)?.state
    ControlRow(Icons.Default.Lock, "Door lock", lockState?.displayName() ?: "Reading state", loading) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.setLocked(deviceId, control, false) },
                enabled = online && !loading && control.capability.supportsUnlock,
            ) { Text("Unlock") }
            Button(
                onClick = { viewModel.setLocked(deviceId, control, true) },
                enabled = online && !loading && control.capability.supportsLock,
            ) { Text("Lock") }
        }
    }
}

@Composable
private fun ClimateControl(
    viewModel: AppViewModel,
    deviceId: String,
    control: DeviceControl.Climate,
    value: CapabilityUiValue?,
    online: Boolean,
    loading: Boolean,
) {
    val climate = (value as? CapabilityUiValue.Climate)?.state
    ControlRow(Icons.Default.Thermostat, "Climate", climate?.localTemperatureCelsius?.formatCelsius() ?: "Reading", loading) {}
    climate?.occupiedHeatingSetpointCelsius?.let { remote ->
        SetpointSlider("Heating setpoint", remote, online && !loading) {
            viewModel.setHeatingSetpoint(deviceId, control, it)
        }
    }
    climate?.occupiedCoolingSetpointCelsius?.let { remote ->
        SetpointSlider("Cooling setpoint", remote, online && !loading) {
            viewModel.setCoolingSetpoint(deviceId, control, it)
        }
    }
}

@Composable
private fun SetpointSlider(label: String, remote: Double, enabled: Boolean, onSet: (Double) -> Unit) {
    var value by remember(label) { mutableFloatStateOf(remote.toFloat()) }
    LaunchedEffect(remote) { value = remote.toFloat() }
    Text("$label · ${value.toDouble().formatCelsius()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(value, { value = it }, valueRange = 10f..35f, steps = 49, enabled = enabled, onValueChangeFinished = {
        onSet((value * 2).roundToInt() / 2.0)
    })
}

@Composable
private fun SensorControl(control: DeviceControl.Sensor, value: CapabilityUiValue?, loading: Boolean) {
    val reading = (value as? CapabilityUiValue.Sensor)?.value
    val label = control.capability.kind.displayName()
    val displayed = when {
        loading -> "Reading"
        reading == null -> "No reading available"
        control.capability.kind == SensorKind.TEMPERATURE -> reading.formatCelsius()
        else -> reading.toString()
    }
    ControlRow(Icons.Default.Sensors, label, displayed, loading) {}
}

@Composable
private fun UnsupportedControl(control: DeviceControl.Unsupported) {
    Text("Additional capabilities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "${control.clusters.size} ${if (control.clusters.size == 1) "capability is" else "capabilities are"} available, but this app does not have a product control for them yet.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ControlRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    loading: Boolean,
    trailing: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else trailing()
    }
}

@Composable
private fun LoadingCapabilities() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text("Discovering device controls", Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun NoProductControls() {
    Text("No supported controls were discovered", style = MaterialTheme.typography.titleMedium)
    Text("The device is paired, but this app does not yet provide a control for its Matter capabilities.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MissingDevice(onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Device no longer exists", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onBack) { Text("Return home") }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun LockState.displayName(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun SensorKind.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)
private fun Double.formatCelsius(): String = "%.1f °C".format(this)
