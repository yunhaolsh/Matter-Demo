package com.example.matter.api

import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf

class FakeMatterAppSdk(
    private val stageDelayMillis: Long = 500,
    private val setupCodeParser: SetupCodeParser = MatterSetupCodeParser(),
) : MatterAppSdk {
    private val livingRoom = MatterRoom("living", "Living room")
    private val bedroom = MatterRoom("bedroom", "Bedroom")
    private val entry = MatterRoom("entry", "Entry")

    private val mutableHome = MutableStateFlow(MatterHome("home-1", "Riverside Home"))
    override val home: StateFlow<MatterHome> = mutableHome.asStateFlow()

    private val mutableRooms = MutableStateFlow(listOf(livingRoom, bedroom, entry))
    override val rooms: StateFlow<List<MatterRoom>> = mutableRooms.asStateFlow()

    private val mutableDevices = MutableStateFlow(
        listOf(
            MatterDevice(
                id = "lamp-1",
                name = "Living room lamp",
                room = livingRoom,
                type = DeviceType.LIGHT,
                connectionMode = ConnectionMode.LOCAL,
                availability = DeviceAvailability.ONLINE,
                isOn = true,
            ),
            MatterDevice(
                id = "plug-1",
                name = "Entry plug",
                room = entry,
                type = DeviceType.PLUG,
                connectionMode = ConnectionMode.LOCAL,
                availability = DeviceAvailability.OFFLINE,
                isOn = false,
            ),
        ),
    )
    override val devices: StateFlow<List<MatterDevice>> = mutableDevices.asStateFlow()

    override fun parseSetupCode(rawCode: String): SetupCode = setupCodeParser.parse(rawCode)

    override fun commissionWifi(
        setupCode: SetupCode,
        credentials: WifiCredentials,
    ): Flow<CommissioningEvent> = flow {
        require(credentials.ssid.isNotBlank()) { "Wi-Fi network is required" }
        val stages = listOf(
            CommissioningEvent.Preparing,
            CommissioningEvent.FindingDevice,
            CommissioningEvent.Connecting,
            CommissioningEvent.JoiningNetwork,
            CommissioningEvent.AddingToHome,
        )
        for (stage in stages) {
            emit(stage)
            delay(stageDelayMillis)
        }
        val device = MatterDevice(
            id = "device-${UUID.randomUUID()}",
            name = "New Matter light",
            room = livingRoom,
            type = DeviceType.LIGHT,
            connectionMode = ConnectionMode.LOCAL,
            availability = DeviceAvailability.ONLINE,
            isOn = false,
        )
        mutableDevices.value = mutableDevices.value + device
        emit(CommissioningEvent.Completed(device))
    }

    override suspend fun refresh(deviceId: String): MatterDevice = requireDevice(deviceId)

    override suspend fun discoverCapabilities(deviceId: String): MatterNodeCapabilities =
        requireNotNull(requireDevice(deviceId).capabilities) { "Capabilities are unavailable" }

    override suspend fun setOnOff(deviceId: String, value: Boolean) {
        updateDevice(deviceId) { device ->
            check(device.availability == DeviceAvailability.ONLINE) { "Device is offline" }
            device.copy(isOn = value)
        }
    }

    override suspend fun toggle(deviceId: String) {
        setOnOff(deviceId, !readOnOff(deviceId))
    }

    override suspend fun readOnOff(deviceId: String): Boolean = requireDevice(deviceId).isOn

    override fun observeOnOff(deviceId: String): Flow<OnOffState> = devices.map { devices ->
        devices.firstOrNull { it.id == deviceId }?.let { device ->
            if (device.availability == DeviceAvailability.ONLINE) {
                OnOffState.Available(device.isOn)
            } else {
                OnOffState.Unavailable
            }
        } ?: OnOffState.Unavailable
    }

    override suspend fun setLevel(deviceId: String, capability: LevelCapability, level: Int) {
        requireDevice(deviceId)
        require(level in capability.minimum..capability.maximum) { "Level is outside the supported range" }
    }

    override suspend fun readLevel(deviceId: String, capability: LevelCapability): Int? {
        requireDevice(deviceId)
        return capability.maximum
    }

    override suspend fun readTemperatureCelsius(deviceId: String, capability: SensorCapability): Double? {
        requireDevice(deviceId)
        require(capability.kind == SensorKind.TEMPERATURE) { "Capability is not a temperature sensor" }
        return 21.5
    }

    override suspend fun setHueSaturation(deviceId: String, capability: ColorCapability, hue: Int, saturation: Int) {
        requireDevice(deviceId)
        require(hue in 0..254 && saturation in 0..254)
    }

    override suspend fun setColorTemperature(deviceId: String, capability: ColorCapability, mireds: Int) {
        requireDevice(deviceId)
        require(mireds in 1..65279)
    }

    override suspend fun readColor(deviceId: String, capability: ColorCapability) =
        ColorState(hue = 0, saturation = 0, colorTemperatureMireds = null)

    override suspend fun setLocked(deviceId: String, capability: DoorLockCapability, locked: Boolean, pin: ByteArray?) {
        requireDevice(deviceId)
    }

    override suspend fun readLockState(deviceId: String, capability: DoorLockCapability) = LockState.LOCKED

    override suspend fun readThermostat(deviceId: String, capability: ThermostatCapability) =
        ThermostatState(21.5, 24.0, 20.0)

    override suspend fun setCoolingSetpoint(deviceId: String, capability: ThermostatCapability, celsius: Double) {
        requireDevice(deviceId)
    }

    override suspend fun setHeatingSetpoint(deviceId: String, capability: ThermostatCapability, celsius: Double) {
        requireDevice(deviceId)
    }

    override suspend fun readRawAttribute(
        deviceId: String,
        capability: MatterCapability,
        attributeId: Long,
    ): RawAttributeValue {
        requireDevice(deviceId)
        return RawAttributeValue(capability.endpointId, capability.cluster.id, attributeId, byteArrayOf(), null)
    }

    override suspend fun writeRawAttribute(
        deviceId: String,
        capability: MatterCapability,
        attributeId: Long,
        tlv: ByteArray,
        timedRequestTimeoutMillis: Int,
    ): RawWriteResult {
        requireDevice(deviceId)
        MatterRawPathValidator.requireAttribute(capability, attributeId)
        MatterRawPathValidator.requireTimedRequestTimeout(timedRequestTimeoutMillis)
        return RawWriteResult(capability.endpointId, capability.cluster.id, attributeId, 0, null)
    }

    override suspend fun invokeRawCommand(
        deviceId: String,
        capability: MatterCapability,
        commandId: Long,
        tlv: ByteArray,
        timedRequestTimeoutMillis: Int,
    ): RawInvokeResult {
        requireDevice(deviceId)
        MatterRawPathValidator.requireCommand(capability, commandId)
        MatterRawPathValidator.requireTimedRequestTimeout(timedRequestTimeoutMillis)
        return RawInvokeResult(capability.endpointId, capability.cluster.id, commandId, 0, byteArrayOf(), null)
    }

    override fun subscribeRaw(
        deviceId: String,
        capability: MatterCapability,
        attributeIds: Set<Long>,
        eventIds: Set<Long>,
        minIntervalSeconds: Int,
        maxIntervalSeconds: Int,
    ): Flow<MatterSubscriptionEvent> {
        requireDevice(deviceId)
        MatterRawPathValidator.requireSubscription(
            capability,
            attributeIds,
            eventIds,
            minIntervalSeconds,
            maxIntervalSeconds,
        )
        return flowOf(MatterSubscriptionEvent.Established(1))
    }

    override suspend fun removeDevice(deviceId: String) {
        check(mutableDevices.value.any { it.id == deviceId }) { "Unknown device" }
        mutableDevices.value = mutableDevices.value.filterNot { it.id == deviceId }
    }

    override fun close() = Unit

    private fun requireDevice(deviceId: String): MatterDevice =
        mutableDevices.value.firstOrNull { it.id == deviceId }
            ?: error("Unknown device")

    private inline fun updateDevice(deviceId: String, transform: (MatterDevice) -> MatterDevice) {
        var found = false
        mutableDevices.value = mutableDevices.value.map { device ->
            if (device.id == deviceId) {
                found = true
                transform(device)
            } else {
                device
            }
        }
        check(found) { "Unknown device" }
    }
}
