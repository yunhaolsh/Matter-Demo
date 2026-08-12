package com.example.matter.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class MatterCapabilitySubscriptionManager(
    private val subscribe: (
        deviceId: String,
        capability: MatterCapability,
        attributeIds: Set<Long>,
        minIntervalSeconds: Int,
        maxIntervalSeconds: Int,
    ) -> Flow<MatterSubscriptionEvent>,
) {
    fun observe(deviceId: String, capabilities: MatterNodeCapabilities): Flow<CapabilitySubscriptionEvent> =
        channelFlow {
            capabilities.endpoints
                .asSequence()
                .flatMap { it.capabilities.asSequence() }
                .mapNotNull(::subscription)
                .forEach { definition ->
                    launch {
                        var state = definition.initialState()
                        subscribe(deviceId, definition.capability, definition.attributeIds, MIN_INTERVAL, MAX_INTERVAL)
                            .catch { error ->
                                if (error is CancellationException) throw error
                                send(CapabilitySubscriptionEvent.Unavailable(definition.key, error.message ?: "Subscription stopped"))
                            }
                            .collect { event ->
                                when (event) {
                                    is MatterSubscriptionEvent.AttributeChanged -> {
                                        state = definition.update(state, event.value)
                                        state?.let { send(CapabilitySubscriptionEvent.Updated(it)) }
                                    }
                                    is MatterSubscriptionEvent.Resubscribing -> send(
                                        CapabilitySubscriptionEvent.Resubscribing(
                                            definition.key,
                                            event.terminationCause,
                                            event.retryInMillis,
                                        ),
                                    )
                                    is MatterSubscriptionEvent.Established,
                                    is MatterSubscriptionEvent.EventReceived,
                                    -> Unit
                                }
                            }
                    }
                }
        }

    private fun subscription(capability: MatterCapability): SubscriptionDefinition? = when (capability) {
        is OnOffCapability -> definition(capability, CapabilityStateKind.ON_OFF, setOf(ON_OFF_ATTRIBUTE)) { _, value ->
            MatterCapabilityState.OnOff(key(capability, CapabilityStateKind.ON_OFF), MatterAttributeValueDecoder.boolean(value))
        }
        is LevelCapability -> definition(capability, CapabilityStateKind.LEVEL, setOf(CURRENT_LEVEL_ATTRIBUTE)) { _, value ->
            MatterCapabilityState.Level(key(capability, CapabilityStateKind.LEVEL), MatterAttributeValueDecoder.unsignedByte(value))
        }
        is ColorCapability -> {
            val attributes = buildSet {
                if (capability.supportsHueSaturation) {
                    add(CURRENT_HUE_ATTRIBUTE)
                    add(CURRENT_SATURATION_ATTRIBUTE)
                }
                if (capability.supportsColorTemperature) add(COLOR_TEMPERATURE_ATTRIBUTE)
            }
            definition(capability, CapabilityStateKind.COLOR, attributes) { previous, value ->
                val current = (previous as? MatterCapabilityState.Color)?.value ?: ColorState(null, null, null)
                val next = when (value.attributeId) {
                    CURRENT_HUE_ATTRIBUTE -> current.copy(hue = MatterAttributeValueDecoder.unsignedByte(value))
                    CURRENT_SATURATION_ATTRIBUTE -> current.copy(saturation = MatterAttributeValueDecoder.unsignedByte(value))
                    COLOR_TEMPERATURE_ATTRIBUTE -> current.copy(colorTemperatureMireds = MatterAttributeValueDecoder.unsignedShort(value))
                    else -> current
                }
                MatterCapabilityState.Color(key(capability, CapabilityStateKind.COLOR), next)
            }.takeIf { attributes.isNotEmpty() }
        }
        is DoorLockCapability -> definition(capability, CapabilityStateKind.LOCK, setOf(LOCK_STATE_ATTRIBUTE)) { _, value ->
            MatterCapabilityState.Lock(
                key(capability, CapabilityStateKind.LOCK),
                MatterValueConverter.lockState(MatterAttributeValueDecoder.unsignedByte(value)),
            )
        }
        is ThermostatCapability -> {
            val attributes = buildSet {
                if (capability.hasLocalTemperature) add(LOCAL_TEMPERATURE_ATTRIBUTE)
                if (capability.hasOccupiedCoolingSetpoint) add(COOLING_SETPOINT_ATTRIBUTE)
                if (capability.hasOccupiedHeatingSetpoint) add(HEATING_SETPOINT_ATTRIBUTE)
            }
            definition(capability, CapabilityStateKind.THERMOSTAT, attributes) { previous, value ->
                val current = (previous as? MatterCapabilityState.Thermostat)?.value ?: ThermostatState(null, null, null)
                val celsius = MatterValueConverter.temperatureCelsius(MatterAttributeValueDecoder.signedShort(value))
                val next = when (value.attributeId) {
                    LOCAL_TEMPERATURE_ATTRIBUTE -> current.copy(localTemperatureCelsius = celsius)
                    COOLING_SETPOINT_ATTRIBUTE -> current.copy(occupiedCoolingSetpointCelsius = celsius)
                    HEATING_SETPOINT_ATTRIBUTE -> current.copy(occupiedHeatingSetpointCelsius = celsius)
                    else -> current
                }
                MatterCapabilityState.Thermostat(key(capability, CapabilityStateKind.THERMOSTAT), next)
            }.takeIf { attributes.isNotEmpty() }
        }
        is SensorCapability -> definition(
            capability,
            CapabilityStateKind.SENSOR,
            setOf(MEASURED_VALUE_ATTRIBUTE),
            capability.kind,
        ) { _, value ->
            val raw = when (capability.kind) {
                SensorKind.TEMPERATURE, SensorKind.PRESSURE -> MatterAttributeValueDecoder.signedShort(value)?.toDouble()
                SensorKind.HUMIDITY, SensorKind.ILLUMINANCE -> MatterAttributeValueDecoder.unsignedShort(value)?.toDouble()
                SensorKind.OCCUPANCY -> MatterAttributeValueDecoder.unsignedByte(value)?.toDouble()
            }
            val converted = when (capability.kind) {
                SensorKind.TEMPERATURE, SensorKind.HUMIDITY -> raw?.div(100.0)
                else -> raw
            }
            MatterCapabilityState.Sensor(key(capability, CapabilityStateKind.SENSOR, capability.kind), converted)
        }
        is FanCapability -> if (capability.supportsPercent) {
            definition(capability, CapabilityStateKind.FAN, setOf(FAN_PERCENT_CURRENT_ATTRIBUTE)) { _, value ->
                MatterCapabilityState.Fan(key(capability, CapabilityStateKind.FAN), MatterAttributeValueDecoder.unsignedByte(value))
            }
        } else null
        is WindowCoveringCapability -> if (capability.supportsLiftPosition) {
            definition(capability, CapabilityStateKind.WINDOW_COVERING, setOf(WINDOW_LIFT_PERCENT_100THS_ATTRIBUTE)) { _, value ->
                MatterCapabilityState.WindowCovering(
                    key(capability, CapabilityStateKind.WINDOW_COVERING),
                    MatterAttributeValueDecoder.unsignedShort(value)?.div(100.0),
                )
            }
        } else null
        is MediaPlaybackCapability -> definition(
            capability,
            CapabilityStateKind.MEDIA_PLAYBACK,
            setOf(MEDIA_CURRENT_STATE_ATTRIBUTE),
        ) { _, value ->
            val state = when (MatterAttributeValueDecoder.unsignedByte(value)) {
                0 -> MediaPlaybackState.PLAYING
                1 -> MediaPlaybackState.PAUSED
                2 -> MediaPlaybackState.NOT_PLAYING
                3 -> MediaPlaybackState.BUFFERING
                else -> MediaPlaybackState.UNKNOWN
            }
            MatterCapabilityState.MediaPlayback(key(capability, CapabilityStateKind.MEDIA_PLAYBACK), state)
        }
        is VendorClusterCapability -> null
        is RawClusterCapability -> null
    }

    private fun definition(
        capability: MatterCapability,
        kind: CapabilityStateKind,
        attributeIds: Set<Long>,
        sensorKind: SensorKind? = null,
        update: (MatterCapabilityState?, RawAttributeValue) -> MatterCapabilityState,
    ) = SubscriptionDefinition(capability, key(capability, kind, sensorKind), attributeIds, update)

    private fun key(capability: MatterCapability, kind: CapabilityStateKind, sensorKind: SensorKind? = null) =
        CapabilityStateKey(capability.endpointId, kind, sensorKind)

    private data class SubscriptionDefinition(
        val capability: MatterCapability,
        val key: CapabilityStateKey,
        val attributeIds: Set<Long>,
        val update: (MatterCapabilityState?, RawAttributeValue) -> MatterCapabilityState,
    ) {
        fun initialState(): MatterCapabilityState? = null
    }

    private companion object {
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 60
        const val ON_OFF_ATTRIBUTE = 0L
        const val CURRENT_LEVEL_ATTRIBUTE = 0L
        const val CURRENT_HUE_ATTRIBUTE = 0L
        const val CURRENT_SATURATION_ATTRIBUTE = 1L
        const val COLOR_TEMPERATURE_ATTRIBUTE = 7L
        const val LOCK_STATE_ATTRIBUTE = 0L
        const val LOCAL_TEMPERATURE_ATTRIBUTE = 0L
        const val COOLING_SETPOINT_ATTRIBUTE = 17L
        const val HEATING_SETPOINT_ATTRIBUTE = 18L
        const val MEASURED_VALUE_ATTRIBUTE = 0L
        const val FAN_PERCENT_CURRENT_ATTRIBUTE = 3L
        const val WINDOW_LIFT_PERCENT_100THS_ATTRIBUTE = 14L
        const val MEDIA_CURRENT_STATE_ATTRIBUTE = 0L
    }
}
