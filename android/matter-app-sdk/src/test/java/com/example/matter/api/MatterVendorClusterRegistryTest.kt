package com.example.matter.api

import org.junit.Assert.assertEquals
import org.junit.Test

class MatterVendorClusterRegistryTest {
    @Test
    fun mapsOnlyDiscoveredPluginPaths() {
        val registry = MatterVendorClusterRegistry(listOf(plugin(setOf(1), setOf(2), setOf(3), setOf(4))))
        val capability = registry.map(6, cluster())!!

        assertEquals("Demo vendor control", capability.displayName)
        assertEquals(setOf(1L), capability.readableAttributeIds)
        MatterRawPathValidator.requireAttribute(capability, 1)
        MatterRawPathValidator.requireWritableAttribute(capability, 2)
        MatterRawPathValidator.requireCommand(capability, 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPluginPathsThatWereNotDiscovered() {
        MatterVendorClusterRegistry(listOf(plugin(setOf(99), emptySet(), emptySet(), emptySet())))
            .map(1, cluster())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsStandardClusterOverrides() {
        MatterVendorClusterRegistry(listOf(object : MatterVendorClusterPlugin {
            override val id = "bad"
            override val clusterIds = setOf(6L)
            override fun map(endpointId: Int, cluster: MatterClusterCapabilities) = null
        }))
    }

    private fun plugin(read: Set<Long>, write: Set<Long>, commands: Set<Long>, events: Set<Long>) =
        object : MatterVendorClusterPlugin {
            override val id = "demo.vendor"
            override val clusterIds = setOf(0xFFF1_0001L)
            override fun map(endpointId: Int, cluster: MatterClusterCapabilities) = VendorClusterDefinition(
                "Demo vendor control", read, write, commands, events,
            )
        }

    private fun cluster() = MatterClusterCapabilities(
        0xFFF1_0001L, 0, setOf(1, 2), setOf(3), emptySet(), setOf(4), 1,
    )
}
