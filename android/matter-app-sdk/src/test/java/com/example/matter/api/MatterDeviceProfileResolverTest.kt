package com.example.matter.api

import org.junit.Assert.assertEquals
import org.junit.Test

class MatterDeviceProfileResolverTest {
    @Test
    fun usesDeclaredSpeakerTypeInsteadOfGuessingFromOnOffAndLevelClusters() {
        val profile = MatterDeviceProfileResolver.resolve(
            node(endpoint(1, 0x0022, 0x0006, 0x0008)),
        )

        assertEquals(DeviceType.SPEAKER, profile.type)
        assertEquals("Speaker", profile.displayName)
        assertEquals("Speaker", profile.endpoints.single().displayName)
    }

    @Test
    fun namesDuplicateEndpointTypesInDiscoveryOrder() {
        val profile = MatterDeviceProfileResolver.resolve(
            node(
                endpoint(0, 0x0016, 0x001D),
                endpoint(7, 0x0101, 0x0006, 0x0008),
                endpoint(12, 0x0101, 0x0006, 0x0008),
            ),
        )

        assertEquals(listOf("Dimmable light 1", "Dimmable light 2"), profile.endpoints.map { it.displayName })
        assertEquals(DeviceType.LIGHT, profile.type)
    }

    @Test
    fun keepsUnknownOnOffEndpointGeneric() {
        val profile = MatterDeviceProfileResolver.resolve(
            node(endpoint(3, 0xFFF1_0001, 0x0006)),
        )

        assertEquals(DeviceType.UNKNOWN, profile.type)
        assertEquals("On/Off control", profile.displayName)
        assertEquals(0xFFF1_0001, profile.endpoints.single().deviceTypeId)
    }

    @Test
    fun fallsBackToTypedClusterWhenDeviceTypeListIsMissing() {
        val profile = MatterDeviceProfileResolver.resolve(
            node(endpoint(2, null, 0x0101)),
        )

        assertEquals(DeviceType.LOCK, profile.type)
        assertEquals("Door lock", profile.displayName)
    }

    private fun node(vararg endpoints: MatterEndpointCapabilities) =
        MatterNodeCapabilities("9", endpoints.toList())

    private fun endpoint(id: Int, deviceType: Long?, vararg clusterIds: Long) =
        MatterEndpointCapabilities(
            endpointId = id,
            deviceTypes = deviceType?.let { listOf(MatterDeviceType(it, 1)) }.orEmpty(),
            serverClusters = clusterIds.map(::cluster),
            clientClusterIds = emptySet(),
            parts = emptySet(),
            capabilities = clusterIds.map { MatterCapabilityRegistry.map(id, cluster(it)) },
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
