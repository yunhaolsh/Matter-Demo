package com.example.matter.api

internal object MatterCapabilityInterpreter {
    const val ON_OFF_CLUSTER_ID = 0x0006L

    fun onOffEndpoint(capabilities: MatterNodeCapabilities): Int? =
        onOffCapability(capabilities)?.endpointId

    fun onOffCapability(capabilities: MatterNodeCapabilities): OnOffCapability? =
        capabilities.endpoints
            .asSequence()
            .filter { it.endpointId != 0 }
            .flatMap { it.capabilities.asSequence() }
            .filterIsInstance<OnOffCapability>()
            .firstOrNull()

    fun deviceType(capabilities: MatterNodeCapabilities): DeviceType {
        return MatterDeviceProfileResolver.resolve(capabilities).type
    }
}
