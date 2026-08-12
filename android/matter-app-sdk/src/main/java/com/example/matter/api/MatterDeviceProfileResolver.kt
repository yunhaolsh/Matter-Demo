package com.example.matter.api

internal object MatterDeviceProfileResolver {
    fun resolve(capabilities: MatterNodeCapabilities): MatterDeviceProfile {
        val resolved = capabilities.endpoints
            .filter { it.endpointId != ROOT_ENDPOINT }
            .map(::resolveEndpoint)
        val duplicateNames = resolved.groupingBy { it.displayName }.eachCount()
        val nameIndexes = mutableMapOf<String, Int>()
        val endpoints = resolved.map { endpoint ->
            if (duplicateNames[endpoint.displayName] == 1) {
                endpoint
            } else {
                val index = nameIndexes.getOrDefault(endpoint.displayName, 0) + 1
                nameIndexes[endpoint.displayName] = index
                endpoint.copy(displayName = "${endpoint.displayName} $index")
            }
        }
        val primary = endpoints.firstOrNull { it.type != DeviceType.UNKNOWN }
        val representative = primary ?: endpoints.firstOrNull()
        return MatterDeviceProfile(
            type = primary?.type ?: DeviceType.UNKNOWN,
            displayName = representative?.displayName ?: "Matter device",
            endpoints = endpoints,
        )
    }

    private fun resolveEndpoint(endpoint: MatterEndpointCapabilities): MatterEndpointProfile {
        val definition = endpoint.deviceTypes
            .asSequence()
            .mapNotNull { type -> DEVICE_TYPES[type.id]?.let { type to it } }
            .firstOrNull { (_, definition) -> definition.type != DeviceType.UNKNOWN }
            ?: endpoint.deviceTypes.firstNotNullOfOrNull { type -> DEVICE_TYPES[type.id]?.let { type to it } }
        if (definition != null) {
            return MatterEndpointProfile(
                endpointId = endpoint.endpointId,
                type = definition.second.type,
                displayName = definition.second.name,
                deviceTypeId = definition.first.id,
            )
        }

        val fallback = fallbackDefinition(endpoint.capabilities)
        return MatterEndpointProfile(
            endpointId = endpoint.endpointId,
            type = fallback.type,
            displayName = fallback.name,
            deviceTypeId = endpoint.deviceTypes.firstOrNull()?.id,
        )
    }

    private fun fallbackDefinition(capabilities: List<MatterCapability>): DeviceTypeDefinition =
        when {
            capabilities.any { it is DoorLockCapability } -> DeviceTypeDefinition("Door lock", DeviceType.LOCK)
            capabilities.any { it is ThermostatCapability } -> DeviceTypeDefinition("Thermostat", DeviceType.THERMOSTAT)
            capabilities.any { it is SensorCapability } -> {
                val sensor = capabilities.filterIsInstance<SensorCapability>().first()
                DeviceTypeDefinition(sensor.kind.sensorName(), DeviceType.SENSOR)
            }
            capabilities.any { it is ColorCapability } -> DeviceTypeDefinition("Color control", DeviceType.UNKNOWN)
            capabilities.any { it is LevelCapability } -> DeviceTypeDefinition("Level control", DeviceType.UNKNOWN)
            capabilities.any { it is OnOffCapability } -> DeviceTypeDefinition("On/Off control", DeviceType.UNKNOWN)
            else -> DeviceTypeDefinition("Device section", DeviceType.UNKNOWN)
        }

    private fun SensorKind.sensorName(): String = when (this) {
        SensorKind.TEMPERATURE -> "Temperature sensor"
        SensorKind.HUMIDITY -> "Humidity sensor"
        SensorKind.OCCUPANCY -> "Occupancy sensor"
        SensorKind.ILLUMINANCE -> "Light sensor"
        SensorKind.PRESSURE -> "Pressure sensor"
    }

    private data class DeviceTypeDefinition(val name: String, val type: DeviceType)

    private val DEVICE_TYPES = mapOf(
        0x000AL to DeviceTypeDefinition("Door lock", DeviceType.LOCK),
        0x000FL to DeviceTypeDefinition("Switch", DeviceType.SWITCH),
        0x0015L to DeviceTypeDefinition("Contact sensor", DeviceType.SENSOR),
        0x0016L to DeviceTypeDefinition("Root node", DeviceType.UNKNOWN),
        0x0022L to DeviceTypeDefinition("Speaker", DeviceType.SPEAKER),
        0x002BL to DeviceTypeDefinition("Fan", DeviceType.FAN),
        0x002CL to DeviceTypeDefinition("Air quality sensor", DeviceType.SENSOR),
        0x002DL to DeviceTypeDefinition("Air purifier", DeviceType.APPLIANCE),
        0x0041L to DeviceTypeDefinition("Freeze detector", DeviceType.SENSOR),
        0x0043L to DeviceTypeDefinition("Water leak detector", DeviceType.SENSOR),
        0x0044L to DeviceTypeDefinition("Rain sensor", DeviceType.SENSOR),
        0x0045L to DeviceTypeDefinition("Soil sensor", DeviceType.SENSOR),
        0x0070L to DeviceTypeDefinition("Refrigerator", DeviceType.APPLIANCE),
        0x0072L to DeviceTypeDefinition("Room air conditioner", DeviceType.APPLIANCE),
        0x0073L to DeviceTypeDefinition("Laundry washer", DeviceType.APPLIANCE),
        0x0074L to DeviceTypeDefinition("Robot vacuum", DeviceType.APPLIANCE),
        0x0075L to DeviceTypeDefinition("Dishwasher", DeviceType.APPLIANCE),
        0x0076L to DeviceTypeDefinition("Smoke and CO alarm", DeviceType.SENSOR),
        0x0079L to DeviceTypeDefinition("Microwave oven", DeviceType.APPLIANCE),
        0x007BL to DeviceTypeDefinition("Oven", DeviceType.APPLIANCE),
        0x007CL to DeviceTypeDefinition("Laundry dryer", DeviceType.APPLIANCE),
        0x0100L to DeviceTypeDefinition("On/Off light", DeviceType.LIGHT),
        0x0101L to DeviceTypeDefinition("Dimmable light", DeviceType.LIGHT),
        0x0103L to DeviceTypeDefinition("Light switch", DeviceType.SWITCH),
        0x0104L to DeviceTypeDefinition("Dimmer switch", DeviceType.SWITCH),
        0x0105L to DeviceTypeDefinition("Color dimmer switch", DeviceType.SWITCH),
        0x0106L to DeviceTypeDefinition("Light sensor", DeviceType.SENSOR),
        0x0107L to DeviceTypeDefinition("Occupancy sensor", DeviceType.SENSOR),
        0x010AL to DeviceTypeDefinition("On/Off plug", DeviceType.PLUG),
        0x010BL to DeviceTypeDefinition("Dimmable plug", DeviceType.PLUG),
        0x010CL to DeviceTypeDefinition("Color temperature light", DeviceType.LIGHT),
        0x010DL to DeviceTypeDefinition("Extended color light", DeviceType.LIGHT),
        0x0140L to DeviceTypeDefinition("Intercom", DeviceType.SPEAKER),
        0x0141L to DeviceTypeDefinition("Audio doorbell", DeviceType.SPEAKER),
        0x0142L to DeviceTypeDefinition("Camera", DeviceType.CAMERA),
        0x0143L to DeviceTypeDefinition("Video doorbell", DeviceType.CAMERA),
        0x0144L to DeviceTypeDefinition("Floodlight camera", DeviceType.CAMERA),
        0x0145L to DeviceTypeDefinition("Snapshot camera", DeviceType.CAMERA),
        0x0146L to DeviceTypeDefinition("Chime", DeviceType.SPEAKER),
        0x0202L to DeviceTypeDefinition("Window covering", DeviceType.WINDOW_COVERING),
        0x0230L to DeviceTypeDefinition("Closure", DeviceType.WINDOW_COVERING),
        0x0301L to DeviceTypeDefinition("Thermostat", DeviceType.THERMOSTAT),
        0x0302L to DeviceTypeDefinition("Temperature sensor", DeviceType.SENSOR),
        0x0305L to DeviceTypeDefinition("Pressure sensor", DeviceType.SENSOR),
        0x0306L to DeviceTypeDefinition("Flow sensor", DeviceType.SENSOR),
        0x0307L to DeviceTypeDefinition("Humidity sensor", DeviceType.SENSOR),
        0x0850L to DeviceTypeDefinition("On/Off sensor", DeviceType.SENSOR),
    )

    private const val ROOT_ENDPOINT = 0
}
