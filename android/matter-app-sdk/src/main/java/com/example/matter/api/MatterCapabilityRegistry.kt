package com.example.matter.api

internal object MatterCapabilityRegistry {
    fun map(endpointId: Int, cluster: MatterClusterCapabilities): MatterCapability {
        val cluster = cluster.withRequiredAttributes()
        return when (cluster.id) {
            ON_OFF_CLUSTER_ID -> OnOffCapability(
                endpointId = endpointId,
                cluster = cluster,
                supportsOff = cluster.acceptsOrMetadataUnavailable(OFF_COMMAND_ID),
                supportsOn = cluster.acceptsOrMetadataUnavailable(ON_COMMAND_ID),
                supportsToggle = cluster.acceptsOrMetadataUnavailable(TOGGLE_COMMAND_ID),
            )
            LEVEL_CONTROL_CLUSTER_ID -> LevelCapability(
                endpointId = endpointId,
                cluster = cluster,
                supportsMoveToLevel = cluster.acceptsOrMetadataUnavailable(MOVE_TO_LEVEL_COMMAND_ID),
            )
            COLOR_CONTROL_CLUSTER_ID -> ColorCapability(
                endpointId = endpointId,
                cluster = cluster,
                supportsHueSaturation = cluster.hasFeature(HUE_SATURATION_FEATURE),
                supportsXy = cluster.hasFeature(XY_FEATURE),
                supportsColorTemperature = cluster.hasFeature(COLOR_TEMPERATURE_FEATURE),
            )
            DOOR_LOCK_CLUSTER_ID -> DoorLockCapability(
                endpointId = endpointId,
                cluster = cluster,
                supportsLock = cluster.accepts(LOCK_DOOR_COMMAND_ID),
                supportsUnlock = cluster.accepts(UNLOCK_DOOR_COMMAND_ID),
            )
            THERMOSTAT_CLUSTER_ID -> ThermostatCapability(
                endpointId = endpointId,
                cluster = cluster,
                hasLocalTemperature = cluster.hasAttribute(LOCAL_TEMPERATURE_ATTRIBUTE_ID),
                hasOccupiedCoolingSetpoint = cluster.hasAttribute(OCCUPIED_COOLING_SETPOINT_ATTRIBUTE_ID),
                hasOccupiedHeatingSetpoint = cluster.hasAttribute(OCCUPIED_HEATING_SETPOINT_ATTRIBUTE_ID),
            )
            TEMPERATURE_MEASUREMENT_CLUSTER_ID -> SensorCapability(endpointId, cluster, SensorKind.TEMPERATURE)
            HUMIDITY_MEASUREMENT_CLUSTER_ID -> SensorCapability(endpointId, cluster, SensorKind.HUMIDITY)
            OCCUPANCY_SENSING_CLUSTER_ID -> SensorCapability(endpointId, cluster, SensorKind.OCCUPANCY)
            FAN_CONTROL_CLUSTER_ID -> FanCapability(
                endpointId,
                cluster,
                cluster.hasAttribute(PERCENT_SETTING_ATTRIBUTE_ID) && cluster.hasAttribute(PERCENT_CURRENT_ATTRIBUTE_ID),
            )
            WINDOW_COVERING_CLUSTER_ID -> WindowCoveringCapability(
                endpointId,
                cluster,
                cluster.hasAttribute(CURRENT_LIFT_PERCENT_100THS_ATTRIBUTE_ID),
            )
            MEDIA_PLAYBACK_CLUSTER_ID -> MediaPlaybackCapability(endpointId, cluster)
            ILLUMINANCE_MEASUREMENT_CLUSTER_ID -> SensorCapability(endpointId, cluster, SensorKind.ILLUMINANCE)
            PRESSURE_MEASUREMENT_CLUSTER_ID -> SensorCapability(endpointId, cluster, SensorKind.PRESSURE)
            else -> RawClusterCapability(endpointId, cluster)
        }
    }

    private fun MatterClusterCapabilities.withRequiredAttributes(): MatterClusterCapabilities =
        when (id) {
            ON_OFF_CLUSTER_ID -> copy(attributeIds = attributeIds + ON_OFF_ATTRIBUTE_ID)
            LEVEL_CONTROL_CLUSTER_ID -> copy(attributeIds = attributeIds + CURRENT_LEVEL_ATTRIBUTE_ID)
            COLOR_CONTROL_CLUSTER_ID -> copy(
                attributeIds = attributeIds + buildSet {
                    if (hasFeature(HUE_SATURATION_FEATURE)) {
                        add(CURRENT_HUE_ATTRIBUTE_ID)
                        add(CURRENT_SATURATION_ATTRIBUTE_ID)
                    }
                    if (hasFeature(COLOR_TEMPERATURE_FEATURE)) add(COLOR_TEMPERATURE_ATTRIBUTE_ID)
                },
            )
            DOOR_LOCK_CLUSTER_ID -> copy(attributeIds = attributeIds + LOCK_STATE_ATTRIBUTE_ID)
            TEMPERATURE_MEASUREMENT_CLUSTER_ID,
            HUMIDITY_MEASUREMENT_CLUSTER_ID,
            OCCUPANCY_SENSING_CLUSTER_ID,
            ILLUMINANCE_MEASUREMENT_CLUSTER_ID,
            PRESSURE_MEASUREMENT_CLUSTER_ID,
            -> copy(attributeIds = attributeIds + MEASURED_VALUE_ATTRIBUTE_ID)
            MEDIA_PLAYBACK_CLUSTER_ID -> copy(attributeIds = attributeIds + MEDIA_CURRENT_STATE_ATTRIBUTE_ID)
            else -> this
        }

    private fun MatterClusterCapabilities.accepts(commandId: Long): Boolean =
        commandId in acceptedCommandIds

    private fun MatterClusterCapabilities.acceptsOrMetadataUnavailable(commandId: Long): Boolean =
        acceptedCommandIds.isEmpty() || accepts(commandId)

    private fun MatterClusterCapabilities.hasAttribute(attributeId: Long): Boolean =
        attributeId in attributeIds

    private fun MatterClusterCapabilities.hasFeature(feature: Long): Boolean =
        featureMap?.and(feature) != 0L

    const val ON_OFF_CLUSTER_ID = 0x0006L
    const val LEVEL_CONTROL_CLUSTER_ID = 0x0008L
    const val DOOR_LOCK_CLUSTER_ID = 0x0101L
    const val THERMOSTAT_CLUSTER_ID = 0x0201L
    const val COLOR_CONTROL_CLUSTER_ID = 0x0300L
    const val ILLUMINANCE_MEASUREMENT_CLUSTER_ID = 0x0400L
    const val TEMPERATURE_MEASUREMENT_CLUSTER_ID = 0x0402L
    const val PRESSURE_MEASUREMENT_CLUSTER_ID = 0x0403L
    const val HUMIDITY_MEASUREMENT_CLUSTER_ID = 0x0405L
    const val OCCUPANCY_SENSING_CLUSTER_ID = 0x0406L
    const val WINDOW_COVERING_CLUSTER_ID = 0x0102L
    const val FAN_CONTROL_CLUSTER_ID = 0x0202L
    const val MEDIA_PLAYBACK_CLUSTER_ID = 0x0506L
    const val ON_OFF_ATTRIBUTE_ID = 0L
    private const val CURRENT_LEVEL_ATTRIBUTE_ID = 0L
    private const val CURRENT_HUE_ATTRIBUTE_ID = 0L
    private const val CURRENT_SATURATION_ATTRIBUTE_ID = 1L
    private const val COLOR_TEMPERATURE_ATTRIBUTE_ID = 7L
    private const val LOCK_STATE_ATTRIBUTE_ID = 0L
    private const val MEASURED_VALUE_ATTRIBUTE_ID = 0L
    private const val MEDIA_CURRENT_STATE_ATTRIBUTE_ID = 0L
    private const val PERCENT_CURRENT_ATTRIBUTE_ID = 3L
    private const val PERCENT_SETTING_ATTRIBUTE_ID = 2L
    private const val CURRENT_LIFT_PERCENT_100THS_ATTRIBUTE_ID = 14L
    private const val OFF_COMMAND_ID = 0L
    private const val ON_COMMAND_ID = 1L
    private const val TOGGLE_COMMAND_ID = 2L
    private const val MOVE_TO_LEVEL_COMMAND_ID = 0L
    private const val LOCK_DOOR_COMMAND_ID = 0L
    private const val UNLOCK_DOOR_COMMAND_ID = 1L
    private const val HUE_SATURATION_FEATURE = 1L shl 0
    private const val XY_FEATURE = 1L shl 3
    private const val COLOR_TEMPERATURE_FEATURE = 1L shl 4
    private const val LOCAL_TEMPERATURE_ATTRIBUTE_ID = 0L
    private const val OCCUPIED_COOLING_SETPOINT_ATTRIBUTE_ID = 17L
    private const val OCCUPIED_HEATING_SETPOINT_ATTRIBUTE_ID = 18L
}
