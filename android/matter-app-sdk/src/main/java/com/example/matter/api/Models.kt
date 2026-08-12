package com.example.matter.api

enum class DeviceType { LIGHT, PLUG, UNKNOWN }

enum class ConnectionMode { LOCAL, HUB }

enum class DeviceAvailability { ONLINE, OFFLINE, CONNECTING }

data class MatterHome(
    val id: String,
    val name: String,
)

data class MatterRoom(
    val id: String,
    val name: String,
)

data class MatterDevice(
    val id: String,
    val name: String,
    val room: MatterRoom,
    val type: DeviceType,
    val connectionMode: ConnectionMode,
    val availability: DeviceAvailability,
    val isOn: Boolean,
    val capabilities: MatterNodeCapabilities? = null,
)

data class MatterNodeCapabilities(
    val nodeId: String,
    val endpoints: List<MatterEndpointCapabilities>,
)

data class MatterEndpointCapabilities(
    val endpointId: Int,
    val deviceTypes: List<MatterDeviceType>,
    val serverClusters: List<MatterClusterCapabilities>,
    val clientClusterIds: Set<Long>,
    val parts: Set<Int>,
    val capabilities: List<MatterCapability>,
)

data class MatterDeviceType(
    val id: Long,
    val revision: Int,
)

data class MatterClusterCapabilities(
    val id: Long,
    val featureMap: Long?,
    val attributeIds: Set<Long>,
    val acceptedCommandIds: Set<Long>,
    val generatedCommandIds: Set<Long>,
    val eventIds: Set<Long>,
    val revision: Int?,
)

sealed interface MatterCapability {
    val endpointId: Int
    val cluster: MatterClusterCapabilities
}

data class OnOffCapability(
    override val endpointId: Int,
    override val cluster: MatterClusterCapabilities,
    val supportsOff: Boolean,
    val supportsOn: Boolean,
    val supportsToggle: Boolean,
) : MatterCapability

data class LevelCapability(
    override val endpointId: Int,
    override val cluster: MatterClusterCapabilities,
    val minimum: Int = 0,
    val maximum: Int = 254,
    val supportsMoveToLevel: Boolean,
) : MatterCapability

data class ColorCapability(
    override val endpointId: Int,
    override val cluster: MatterClusterCapabilities,
    val supportsHueSaturation: Boolean,
    val supportsXy: Boolean,
    val supportsColorTemperature: Boolean,
) : MatterCapability

data class DoorLockCapability(
    override val endpointId: Int,
    override val cluster: MatterClusterCapabilities,
    val supportsLock: Boolean,
    val supportsUnlock: Boolean,
) : MatterCapability

data class ThermostatCapability(
    override val endpointId: Int,
    override val cluster: MatterClusterCapabilities,
    val hasLocalTemperature: Boolean,
    val hasOccupiedCoolingSetpoint: Boolean,
    val hasOccupiedHeatingSetpoint: Boolean,
) : MatterCapability

enum class SensorKind { TEMPERATURE, HUMIDITY, OCCUPANCY, ILLUMINANCE, PRESSURE }

data class SensorCapability(
    override val endpointId: Int,
    override val cluster: MatterClusterCapabilities,
    val kind: SensorKind,
) : MatterCapability

data class RawClusterCapability(
    override val endpointId: Int,
    override val cluster: MatterClusterCapabilities,
) : MatterCapability

data class ColorState(
    val hue: Int?,
    val saturation: Int?,
    val colorTemperatureMireds: Int?,
)

enum class LockState { NOT_FULLY_LOCKED, LOCKED, UNLOCKED, UNLATCHED, UNKNOWN }

data class ThermostatState(
    val localTemperatureCelsius: Double?,
    val occupiedCoolingSetpointCelsius: Double?,
    val occupiedHeatingSetpointCelsius: Double?,
)

class RawAttributeValue(
    val endpointId: Int,
    val clusterId: Long,
    val attributeId: Long,
    tlv: ByteArray,
    val json: String?,
) {
    val tlv: ByteArray = tlv.copyOf()
        get() = field.copyOf()
}

data class RawWriteResult(
    val endpointId: Int,
    val clusterId: Long,
    val attributeId: Long,
    val statusCode: Int,
    val clusterStatus: Int?,
)

class RawInvokeResult(
    val endpointId: Int,
    val clusterId: Long,
    val commandId: Long,
    val statusCode: Long,
    tlv: ByteArray?,
    val json: String?,
) {
    val tlv: ByteArray? = tlv?.copyOf()
        get() = field?.copyOf()
}

class RawEventValue(
    val endpointId: Int,
    val clusterId: Long,
    val eventId: Long,
    val eventNumber: Long,
    val priority: Int,
    val timestampType: Int,
    val timestampValue: Long,
    tlv: ByteArray,
    val json: String?,
) {
    val tlv: ByteArray = tlv.copyOf()
        get() = field.copyOf()
}

sealed interface MatterSubscriptionEvent {
    data class Established(val subscriptionId: Long) : MatterSubscriptionEvent
    data class Resubscribing(val terminationCause: Long, val retryInMillis: Long) : MatterSubscriptionEvent
    data class AttributeChanged(val value: RawAttributeValue) : MatterSubscriptionEvent
    data class EventReceived(val value: RawEventValue) : MatterSubscriptionEvent
}

@ConsistentCopyVisibility
data class SetupCode internal constructor(
    val value: String,
    val format: Format,
) {
    enum class Format { QR, MANUAL }
}

data class WifiCredentials(
    val ssid: String,
    val password: String,
)

sealed interface CommissioningEvent {
    data object Preparing : CommissioningEvent
    data object FindingDevice : CommissioningEvent
    data object Connecting : CommissioningEvent
    data object JoiningNetwork : CommissioningEvent
    data object AddingToHome : CommissioningEvent
    data class Completed(val device: MatterDevice) : CommissioningEvent
    data class Failed(val message: String) : CommissioningEvent
    data object Cancelled : CommissioningEvent
}

sealed interface OnOffState {
    data object Loading : OnOffState
    data class Available(val isOn: Boolean) : OnOffState
    data object Unavailable : OnOffState
}
