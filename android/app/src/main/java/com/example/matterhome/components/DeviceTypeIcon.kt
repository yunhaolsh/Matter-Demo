package com.example.matterhome.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Blinds
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.matter.api.DeviceType

fun DeviceType.icon(): ImageVector = when (this) {
    DeviceType.LIGHT -> Icons.Default.Lightbulb
    DeviceType.PLUG -> Icons.Default.ElectricalServices
    DeviceType.LOCK -> Icons.Default.Lock
    DeviceType.THERMOSTAT -> Icons.Default.Thermostat
    DeviceType.SPEAKER -> Icons.Default.Speaker
    DeviceType.SENSOR -> Icons.Default.Sensors
    DeviceType.FAN -> Icons.Default.Air
    DeviceType.SWITCH -> Icons.Default.ToggleOn
    DeviceType.WINDOW_COVERING -> Icons.Default.Blinds
    DeviceType.CAMERA -> Icons.Default.Videocam
    DeviceType.APPLIANCE -> Icons.Default.Kitchen
    DeviceType.UNKNOWN -> Icons.Default.DevicesOther
}
