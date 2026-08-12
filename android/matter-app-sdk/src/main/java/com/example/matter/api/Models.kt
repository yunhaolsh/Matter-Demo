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
)

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
