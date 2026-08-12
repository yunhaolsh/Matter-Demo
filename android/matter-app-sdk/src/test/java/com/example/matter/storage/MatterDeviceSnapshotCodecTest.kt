package com.example.matter.storage

import com.example.matter.api.ConnectionMode
import com.example.matter.api.DeviceAvailability
import com.example.matter.api.DeviceType
import com.example.matter.api.MatterClusterCapabilities
import com.example.matter.api.MatterDevice
import com.example.matter.api.MatterDeviceProfileResolver
import com.example.matter.api.MatterDeviceType
import com.example.matter.api.MatterEndpointCapabilities
import com.example.matter.api.MatterNodeCapabilities
import com.example.matter.api.MatterRoom
import org.junit.Assert.assertEquals
import org.junit.Test

class MatterDeviceSnapshotCodecTest {
    @Test
    fun roundTripsPublicDeviceDirectoryWithoutOperationalSecrets() {
        val endpoint = MatterEndpointCapabilities(
            endpointId = 4,
            deviceTypes = listOf(MatterDeviceType(0x010C, 2)),
            serverClusters = listOf(cluster(6), cluster(8), cluster(0x0300)),
            clientClusterIds = setOf(3),
            parts = emptySet(),
            capabilities = emptyList(),
        )
        val rawNode = MatterNodeCapabilities("7", listOf(endpoint))
        val profile = MatterDeviceProfileResolver.resolve(rawNode)
        val device = MatterDevice(
            "7", "Desk light", MatterRoom("office", "Office"), DeviceType.LIGHT,
            ConnectionMode.LOCAL, DeviceAvailability.ONLINE, true,
            rawNode.copy(profile = profile), profile,
        )

        val restored = MatterDeviceSnapshotCodec.decode(MatterDeviceSnapshotCodec.encode(device))

        assertEquals("Desk light", restored.name)
        assertEquals("Office", restored.room.name)
        assertEquals(DeviceType.LIGHT, restored.type)
        assertEquals("Color temperature light", restored.profile?.displayName)
        assertEquals(setOf(6L, 8L, 0x0300L), restored.capabilities?.endpoints?.single()?.serverClusters?.map { it.id }?.toSet())
        assertEquals(DeviceAvailability.CONNECTING, restored.availability)
    }

    private fun cluster(id: Long) = MatterClusterCapabilities(
        id, 0, setOf(0), setOf(0), emptySet(), emptySet(), 1,
    )
}
