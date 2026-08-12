package com.example.matter.api

internal object MatterCapabilityInterpreter {
    const val ON_OFF_CLUSTER_ID = 0x0006L

    fun onOffEndpoint(capabilities: MatterNodeCapabilities): Int? =
        capabilities.endpoints
            .asSequence()
            .filter { it.endpointId != 0 }
            .firstOrNull { endpoint ->
                endpoint.serverClusters.any { it.id == ON_OFF_CLUSTER_ID }
            }
            ?.endpointId

    fun deviceType(capabilities: MatterNodeCapabilities): DeviceType {
        val deviceTypeIds = capabilities.endpoints.flatMap { it.deviceTypes }.map { it.id }.toSet()
        return when {
            deviceTypeIds.any { it in LIGHT_DEVICE_TYPES } -> DeviceType.LIGHT
            deviceTypeIds.any { it in PLUG_DEVICE_TYPES } -> DeviceType.PLUG
            else -> DeviceType.UNKNOWN
        }
    }

    private val LIGHT_DEVICE_TYPES = setOf(0x0100L, 0x0101L, 0x010CL, 0x010DL)
    private val PLUG_DEVICE_TYPES = setOf(0x010AL, 0x010BL)
}
