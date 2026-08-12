package com.example.matter.api

import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MatterAppSdk : Closeable {
    val home: StateFlow<MatterHome>
    val rooms: StateFlow<List<MatterRoom>>
    val devices: StateFlow<List<MatterDevice>>

    fun parseSetupCode(rawCode: String): SetupCode
    fun commissionWifi(setupCode: SetupCode, credentials: WifiCredentials): Flow<CommissioningEvent>
    suspend fun discoverCapabilities(deviceId: String): MatterNodeCapabilities
    suspend fun refresh(deviceId: String): MatterDevice
    suspend fun setOnOff(deviceId: String, value: Boolean)
    suspend fun setOnOff(deviceId: String, capability: OnOffCapability, value: Boolean)
    suspend fun toggle(deviceId: String)
    suspend fun readOnOff(deviceId: String): Boolean
    suspend fun readOnOff(deviceId: String, capability: OnOffCapability): Boolean
    fun observeOnOff(deviceId: String): Flow<OnOffState>
    suspend fun setLevel(deviceId: String, capability: LevelCapability, level: Int)
    suspend fun readLevel(deviceId: String, capability: LevelCapability): Int?
    suspend fun readTemperatureCelsius(deviceId: String, capability: SensorCapability): Double?
    suspend fun setHueSaturation(deviceId: String, capability: ColorCapability, hue: Int, saturation: Int)
    suspend fun setColorTemperature(deviceId: String, capability: ColorCapability, mireds: Int)
    suspend fun readColor(deviceId: String, capability: ColorCapability): ColorState
    suspend fun setLocked(deviceId: String, capability: DoorLockCapability, locked: Boolean, pin: ByteArray? = null)
    suspend fun readLockState(deviceId: String, capability: DoorLockCapability): LockState
    suspend fun readThermostat(deviceId: String, capability: ThermostatCapability): ThermostatState
    suspend fun setCoolingSetpoint(deviceId: String, capability: ThermostatCapability, celsius: Double)
    suspend fun setHeatingSetpoint(deviceId: String, capability: ThermostatCapability, celsius: Double)
    suspend fun readRawAttribute(deviceId: String, capability: MatterCapability, attributeId: Long): RawAttributeValue
    suspend fun writeRawAttribute(
        deviceId: String,
        capability: MatterCapability,
        attributeId: Long,
        tlv: ByteArray,
        timedRequestTimeoutMillis: Int = 0,
    ): RawWriteResult
    suspend fun invokeRawCommand(
        deviceId: String,
        capability: MatterCapability,
        commandId: Long,
        tlv: ByteArray,
        timedRequestTimeoutMillis: Int = 0,
    ): RawInvokeResult
    fun subscribeRaw(
        deviceId: String,
        capability: MatterCapability,
        attributeIds: Set<Long> = emptySet(),
        eventIds: Set<Long> = emptySet(),
        minIntervalSeconds: Int = 1,
        maxIntervalSeconds: Int = 60,
    ): Flow<MatterSubscriptionEvent>
    suspend fun removeDevice(deviceId: String)
}
