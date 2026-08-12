package com.example.matter.api

import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class FakeMatterAppSdk(
    private val stageDelayMillis: Long = 500,
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

    override fun parseSetupCode(rawCode: String): SetupCode {
        val normalized = rawCode.trim()
        require(normalized.isNotEmpty()) { "Enter a setup code" }
        return when {
            normalized.startsWith("MT:", ignoreCase = true) && normalized.length >= 8 ->
                SetupCode(normalized, SetupCode.Format.QR)
            normalized.filter(Char::isDigit).length in 11..21 ->
                SetupCode(normalized.filter(Char::isDigit), SetupCode.Format.MANUAL)
            else -> throw IllegalArgumentException("Use a valid Matter QR or manual setup code")
        }
    }

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
