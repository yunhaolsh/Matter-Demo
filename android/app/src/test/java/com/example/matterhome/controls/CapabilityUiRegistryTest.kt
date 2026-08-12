package com.example.matterhome.controls

import com.example.matter.api.ConnectionMode
import com.example.matter.api.DeviceAvailability
import com.example.matter.api.DeviceType
import com.example.matter.api.LevelCapability
import com.example.matter.api.MatterCapability
import com.example.matter.api.MatterClusterCapabilities
import com.example.matter.api.MatterDevice
import com.example.matter.api.MatterEndpointCapabilities
import com.example.matter.api.MatterNodeCapabilities
import com.example.matter.api.MatterRoom
import com.example.matter.api.OnOffCapability
import com.example.matter.api.RawClusterCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityUiRegistryTest {
    @Test
    fun groupsTypedControlsByEndpointAndSkipsRootEndpoint() {
        val rootRaw = RawClusterCapability(0, cluster(0x001D))
        val power = OnOffCapability(4, cluster(6), true, true, true)
        val level = LevelCapability(4, cluster(8), supportsMoveToLevel = true)

        val groups = CapabilityUiRegistry.controls(device(endpoint(0, rootRaw), endpoint(4, power, level)))

        assertEquals(listOf(4), groups.map { it.endpointId })
        assertTrue(groups.single().controls[0] is DeviceControl.Power)
        assertTrue(groups.single().controls[1] is DeviceControl.Level)
    }

    @Test
    fun aggregatesUnknownClustersIntoOneSafeUnsupportedControl() {
        val groups =
            CapabilityUiRegistry.controls(
                device(
                    endpoint(
                        2,
                        RawClusterCapability(2, cluster(0xFFF1_0001)),
                        RawClusterCapability(2, cluster(0xFFF1_0002)),
                    ),
                ),
            )

        val unsupported = groups.single().controls.single() as DeviceControl.Unsupported
        assertEquals(2, unsupported.clusters.size)
    }

    private fun endpoint(id: Int, vararg capabilities: MatterCapability) =
        MatterEndpointCapabilities(
            endpointId = id,
            deviceTypes = emptyList(),
            serverClusters = capabilities.map { it.cluster },
            clientClusterIds = emptySet(),
            parts = emptySet(),
            capabilities = capabilities.toList(),
        )

    private fun device(vararg endpoints: MatterEndpointCapabilities) =
        MatterDevice(
            id = "7",
            name = "Matter device",
            room = MatterRoom("room", "Room"),
            type = DeviceType.UNKNOWN,
            connectionMode = ConnectionMode.LOCAL,
            availability = DeviceAvailability.ONLINE,
            isOn = false,
            capabilities = MatterNodeCapabilities("7", endpoints.toList()),
        )

    private fun cluster(id: Long) =
        MatterClusterCapabilities(id, null, emptySet(), emptySet(), emptySet(), emptySet(), 1)
}
