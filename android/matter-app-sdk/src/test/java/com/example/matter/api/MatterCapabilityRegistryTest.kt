package com.example.matter.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatterCapabilityRegistryTest {
    @Test
    fun mapsOnOffCommandsFromDiscoveredCommandList() {
        val capability = MatterCapabilityRegistry.map(
            endpointId = 4,
            cluster = cluster(MatterCapabilityRegistry.ON_OFF_CLUSTER_ID, commands = setOf(0, 1)),
        ) as OnOffCapability

        assertTrue(capability.supportsOff)
        assertTrue(capability.supportsOn)
        assertFalse(capability.supportsToggle)
    }

    @Test
    fun mapsTemperatureMeasurementToTypedSensor() {
        val capability = MatterCapabilityRegistry.map(
            endpointId = 9,
            cluster = cluster(MatterCapabilityRegistry.TEMPERATURE_MEASUREMENT_CLUSTER_ID),
        ) as SensorCapability

        assertTrue(capability.kind == SensorKind.TEMPERATURE)
    }

    @Test
    fun levelCapabilityReflectsDiscoveredMoveToLevelCommand() {
        val supported = MatterCapabilityRegistry.map(
            endpointId = 2,
            cluster = cluster(MatterCapabilityRegistry.LEVEL_CONTROL_CLUSTER_ID, commands = setOf(0)),
        ) as LevelCapability
        val unsupported = MatterCapabilityRegistry.map(
            endpointId = 3,
            cluster = cluster(MatterCapabilityRegistry.LEVEL_CONTROL_CLUSTER_ID, commands = setOf(1)),
        ) as LevelCapability

        assertTrue(supported.supportsMoveToLevel)
        assertFalse(unsupported.supportsMoveToLevel)
    }

    @Test
    fun preservesUnknownClusterAsRawCapability() {
        val capability = MatterCapabilityRegistry.map(2, cluster(0xFFF1_1234L))

        assertTrue(capability is RawClusterCapability)
        assertTrue(capability.cluster.id == 0xFFF1_1234L)
    }

    @Test
    fun colorCapabilityUsesFeatureMap() {
        val capability = MatterCapabilityRegistry.map(
            endpointId = 5,
            cluster = cluster(MatterCapabilityRegistry.COLOR_CONTROL_CLUSTER_ID, featureMap = (1L shl 0) or (1L shl 4)),
        ) as ColorCapability

        assertTrue(capability.supportsHueSaturation)
        assertFalse(capability.supportsXy)
        assertTrue(capability.supportsColorTemperature)
    }

    @Test
    fun doorLockCapabilityUsesAcceptedCommands() {
        val capability = MatterCapabilityRegistry.map(
            endpointId = 6,
            cluster = cluster(MatterCapabilityRegistry.DOOR_LOCK_CLUSTER_ID, commands = setOf(0)),
        ) as DoorLockCapability

        assertTrue(capability.supportsLock)
        assertFalse(capability.supportsUnlock)
    }

    @Test
    fun thermostatCapabilityUsesAttributeList() {
        val capability = MatterCapabilityRegistry.map(
            endpointId = 7,
            cluster = cluster(MatterCapabilityRegistry.THERMOSTAT_CLUSTER_ID, attributes = setOf(0, 18)),
        ) as ThermostatCapability

        assertTrue(capability.hasLocalTemperature)
        assertFalse(capability.hasOccupiedCoolingSetpoint)
        assertTrue(capability.hasOccupiedHeatingSetpoint)
    }

    private fun cluster(
        id: Long,
        commands: Set<Long> = emptySet(),
        attributes: Set<Long> = emptySet(),
        featureMap: Long = 0,
    ) =
        MatterClusterCapabilities(
            id = id,
            featureMap = featureMap,
            attributeIds = attributes,
            acceptedCommandIds = commands,
            generatedCommandIds = emptySet(),
            eventIds = emptySet(),
            revision = 1,
        )
}
