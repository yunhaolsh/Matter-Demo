package com.example.matterhome.controls

import com.example.matter.api.ColorCapability
import com.example.matter.api.DoorLockCapability
import com.example.matter.api.DeviceType
import com.example.matter.api.LevelCapability
import com.example.matter.api.LockState
import com.example.matter.api.MatterCapability
import com.example.matter.api.MatterDevice
import com.example.matter.api.OnOffCapability
import com.example.matter.api.RawClusterCapability
import com.example.matter.api.SensorCapability
import com.example.matter.api.ThermostatCapability
import com.example.matter.api.ThermostatState

data class CapabilityUiKey(val endpointId: Int, val kind: String)

data class EndpointControlGroup(
    val endpointId: Int,
    val displayName: String,
    val type: DeviceType,
    val controls: List<DeviceControl>,
)

sealed interface DeviceControl {
    val key: CapabilityUiKey

    data class Power(val capability: OnOffCapability) : DeviceControl {
        override val key = CapabilityUiKey(capability.endpointId, "power")
    }

    data class Level(val capability: LevelCapability) : DeviceControl {
        override val key = CapabilityUiKey(capability.endpointId, "level")
    }

    data class Color(val capability: ColorCapability) : DeviceControl {
        override val key = CapabilityUiKey(capability.endpointId, "color")
    }

    data class Lock(val capability: DoorLockCapability) : DeviceControl {
        override val key = CapabilityUiKey(capability.endpointId, "lock")
    }

    data class Climate(val capability: ThermostatCapability) : DeviceControl {
        override val key = CapabilityUiKey(capability.endpointId, "climate")
    }

    data class Sensor(val capability: SensorCapability) : DeviceControl {
        override val key = CapabilityUiKey(capability.endpointId, "sensor-${capability.kind.name}")
    }

    data class Unsupported(
        override val key: CapabilityUiKey,
        val clusters: List<RawClusterCapability>,
    ) : DeviceControl
}

object CapabilityUiRegistry {
    fun controls(device: MatterDevice): List<EndpointControlGroup> =
        device.capabilities?.endpoints.orEmpty()
            .asSequence()
            .filter { it.endpointId != ROOT_ENDPOINT }
            .mapNotNull { endpoint ->
                mapEndpoint(endpoint.capabilities).takeIf { it.isNotEmpty() }?.let { controls ->
                    val profile = device.profile?.endpoints?.firstOrNull { it.endpointId == endpoint.endpointId }
                    EndpointControlGroup(
                        endpointId = endpoint.endpointId,
                        displayName = profile?.displayName ?: "Device section",
                        type = profile?.type ?: DeviceType.UNKNOWN,
                        controls = controls,
                    )
                }
            }
            .toList()

    private fun mapEndpoint(capabilities: List<MatterCapability>): List<DeviceControl> {
        val typed =
            capabilities.mapNotNull { capability ->
                when (capability) {
                    is OnOffCapability -> DeviceControl.Power(capability)
                    is LevelCapability -> DeviceControl.Level(capability)
                    is ColorCapability -> DeviceControl.Color(capability)
                    is DoorLockCapability -> DeviceControl.Lock(capability)
                    is ThermostatCapability -> DeviceControl.Climate(capability)
                    is SensorCapability -> DeviceControl.Sensor(capability)
                    is RawClusterCapability -> null
                }
            }
        val raw = capabilities.filterIsInstance<RawClusterCapability>()
        return if (raw.isEmpty()) typed
        else typed + DeviceControl.Unsupported(CapabilityUiKey(raw.first().endpointId, "unsupported"), raw)
    }

    private const val ROOT_ENDPOINT = 0
}

sealed interface CapabilityUiValue {
    data class Power(val isOn: Boolean) : CapabilityUiValue
    data class Level(val value: Int?) : CapabilityUiValue
    data class Color(val hue: Int?, val saturation: Int?, val temperatureMireds: Int?) : CapabilityUiValue
    data class Lock(val state: LockState) : CapabilityUiValue
    data class Climate(val state: ThermostatState) : CapabilityUiValue
    data class Sensor(val value: Double?) : CapabilityUiValue
}
