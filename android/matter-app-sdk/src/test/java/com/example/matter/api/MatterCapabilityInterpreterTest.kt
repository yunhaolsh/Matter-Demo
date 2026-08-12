package com.example.matter.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatterCapabilityInterpreterTest {
    @Test
    fun selectsOnOffEndpointFromDiscoveredServerClusters() {
        val capabilities = node(
            endpoint(0, deviceType = 0x0016, clusters = setOf(0x001D)),
            endpoint(3, deviceType = 0x010A, clusters = setOf(0x0006, 0x0008)),
        )

        assertEquals(3, MatterCapabilityInterpreter.onOffEndpoint(capabilities))
        assertEquals(3, MatterCapabilityInterpreter.onOffCapability(capabilities)?.endpointId)
        assertEquals(DeviceType.PLUG, MatterCapabilityInterpreter.deviceType(capabilities))
    }

    @Test
    fun doesNotGuessEndpointWhenOnOffWasNotDiscovered() {
        val capabilities = node(endpoint(1, deviceType = 0x0302, clusters = setOf(0x0402)))

        assertNull(MatterCapabilityInterpreter.onOffEndpoint(capabilities))
        assertEquals(DeviceType.UNKNOWN, MatterCapabilityInterpreter.deviceType(capabilities))
    }

    @Test
    fun recognizesLightDeviceTypeIndependentlyOfEndpointNumber() {
        val capabilities = node(endpoint(42, deviceType = 0x010D, clusters = setOf(0x0006, 0x0300)))

        assertEquals(42, MatterCapabilityInterpreter.onOffEndpoint(capabilities))
        assertEquals(DeviceType.LIGHT, MatterCapabilityInterpreter.deviceType(capabilities))
    }

    private fun node(vararg endpoints: MatterEndpointCapabilities) =
        MatterNodeCapabilities(nodeId = "7", endpoints = endpoints.toList())

    private fun endpoint(
        id: Int,
        deviceType: Long,
        clusters: Set<Long>,
    ) = MatterEndpointCapabilities(
        endpointId = id,
        deviceTypes = listOf(MatterDeviceType(deviceType, 1)),
        serverClusters = clusters.map(::cluster),
        clientClusterIds = emptySet(),
        parts = emptySet(),
        capabilities = clusters.map { MatterCapabilityRegistry.map(id, cluster(it)) },
    )

    private fun cluster(id: Long) = MatterClusterCapabilities(
        id = id,
        featureMap = 0,
        attributeIds = emptySet(),
        acceptedCommandIds = emptySet(),
        generatedCommandIds = emptySet(),
        eventIds = emptySet(),
        revision = 1,
    )
}
