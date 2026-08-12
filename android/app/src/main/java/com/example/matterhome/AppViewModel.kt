package com.example.matterhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.matter.api.CommissioningEvent
import com.example.matter.api.MatterAppSdk
import com.example.matter.api.MatterDevice
import com.example.matter.api.MatterHome
import com.example.matter.api.MatterRoom
import com.example.matter.api.SetupCode
import com.example.matter.api.WifiCredentials
import com.example.matter.api.SensorKind
import com.example.matter.api.MatterCapabilityState
import com.example.matter.api.MediaPlaybackAction
import com.example.matterhome.controls.CapabilityUiKey
import com.example.matterhome.controls.CapabilityUiRegistry
import com.example.matterhome.controls.CapabilityUiValue
import com.example.matterhome.controls.DeviceControl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AppUiState(
    val home: MatterHome = MatterHome("", "Matter Home"),
    val rooms: List<MatterRoom> = emptyList(),
    val devices: List<MatterDevice> = emptyList(),
    val selectedRoomId: String? = null,
    val setupCode: SetupCode? = null,
    val commissioningEvent: CommissioningEvent? = null,
    val errorMessage: String? = null,
    val capabilityDeviceId: String? = null,
    val capabilityValues: Map<CapabilityUiKey, CapabilityUiValue> = emptyMap(),
    val loadingCapabilities: Set<CapabilityUiKey> = emptySet(),
) {
    val visibleDevices: List<MatterDevice>
        get() = devices.filter { selectedRoomId == null || it.room.id == selectedRoomId }
}

class AppViewModel(private val sdk: MatterAppSdk) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private var commissioningJob: Job? = null

    init {
        viewModelScope.launch { sdk.home.collectLatest { update { copy(home = it) } } }
        viewModelScope.launch { sdk.rooms.collectLatest { update { copy(rooms = it) } } }
        viewModelScope.launch { sdk.devices.collectLatest { update { copy(devices = it) } } }
        viewModelScope.launch {
            sdk.capabilityStates.collectLatest { states ->
                val deviceId = state.value.capabilityDeviceId ?: return@collectLatest
                val values = states[deviceId].orEmpty().mapNotNull { (_, capabilityState) ->
                    capabilityState.toUiEntry()
                }.toMap()
                update {
                    if (capabilityDeviceId == deviceId) {
                        copy(capabilityValues = capabilityValues + values)
                    } else {
                        this
                    }
                }
            }
        }
    }

    fun selectRoom(roomId: String?) = update { copy(selectedRoomId = roomId) }

    fun parseSetupCode(rawCode: String): Boolean = runCatching {
        sdk.parseSetupCode(rawCode)
    }.fold(
        onSuccess = { setupCode ->
            update { copy(setupCode = setupCode, errorMessage = null) }
            true
        },
        onFailure = { error ->
            update { copy(errorMessage = error.message) }
            false
        },
    )

    fun commission(credentials: WifiCredentials) {
        val setupCode = state.value.setupCode ?: return
        commissioningJob?.cancel()
        commissioningJob = viewModelScope.launch {
            runCatching {
                sdk.commissionWifi(setupCode, credentials).collect { event ->
                    update { copy(commissioningEvent = event, errorMessage = null) }
                }
            }.onFailure { error ->
                update { copy(commissioningEvent = CommissioningEvent.Failed(error.message ?: "Unable to add device")) }
            }
        }
    }

    fun cancelCommissioning() {
        commissioningJob?.cancel()
        commissioningJob = null
        update { copy(setupCode = null, commissioningEvent = CommissioningEvent.Cancelled, errorMessage = null) }
    }

    fun setPower(deviceId: String, isOn: Boolean) = viewModelScope.launch {
        runCatching { sdk.setOnOff(deviceId, isOn) }
            .onFailure { update { copy(errorMessage = it.message) } }
    }

    fun toggle(deviceId: String) = viewModelScope.launch {
        runCatching { sdk.toggle(deviceId) }
            .onFailure { update { copy(errorMessage = it.message) } }
    }

    fun loadDeviceControls(deviceId: String) = viewModelScope.launch {
        update {
            if (capabilityDeviceId == deviceId) this else copy(
                capabilityDeviceId = deviceId,
                capabilityValues = emptyMap(),
                loadingCapabilities = emptySet(),
            )
        }
        val device = state.value.devices.firstOrNull { it.id == deviceId } ?: return@launch
        val resolvedDevice =
            if (device.capabilities == null) {
                runCatching { sdk.refresh(deviceId) }
                    .onFailure { update { copy(errorMessage = it.message) } }
                    .getOrNull() ?: return@launch
            } else {
                device
            }
        CapabilityUiRegistry.controls(resolvedDevice)
            .flatMap { it.controls }
            .filterNot {
                it is DeviceControl.Unsupported ||
                    it is DeviceControl.Fan ||
                    it is DeviceControl.WindowCovering ||
                    it is DeviceControl.Media
            }
            .forEach { control -> refreshControl(deviceId, control) }
    }

    fun setPower(deviceId: String, control: DeviceControl.Power, isOn: Boolean) =
        runControl(deviceId, control.key) {
            sdk.setOnOff(deviceId, control.capability, isOn)
            CapabilityUiValue.Power(sdk.readOnOff(deviceId, control.capability))
        }

    fun setLevel(deviceId: String, control: DeviceControl.Level, value: Int) =
        runControl(deviceId, control.key) {
            sdk.setLevel(deviceId, control.capability, value)
            CapabilityUiValue.Level(sdk.readLevel(deviceId, control.capability))
        }

    fun setColor(deviceId: String, control: DeviceControl.Color, hue: Int, saturation: Int) =
        runControl(deviceId, control.key) {
            sdk.setHueSaturation(deviceId, control.capability, hue, saturation)
            sdk.readColor(deviceId, control.capability).let {
                CapabilityUiValue.Color(it.hue, it.saturation, it.colorTemperatureMireds)
            }
        }

    fun setColorTemperature(deviceId: String, control: DeviceControl.Color, mireds: Int) =
        runControl(deviceId, control.key) {
            sdk.setColorTemperature(deviceId, control.capability, mireds)
            sdk.readColor(deviceId, control.capability).let {
                CapabilityUiValue.Color(it.hue, it.saturation, it.colorTemperatureMireds)
            }
        }

    fun setLocked(deviceId: String, control: DeviceControl.Lock, locked: Boolean) =
        runControl(deviceId, control.key) {
            sdk.setLocked(deviceId, control.capability, locked)
            CapabilityUiValue.Lock(sdk.readLockState(deviceId, control.capability))
        }

    fun setCoolingSetpoint(deviceId: String, control: DeviceControl.Climate, celsius: Double) =
        runControl(deviceId, control.key) {
            sdk.setCoolingSetpoint(deviceId, control.capability, celsius)
            CapabilityUiValue.Climate(sdk.readThermostat(deviceId, control.capability))
        }

    fun setHeatingSetpoint(deviceId: String, control: DeviceControl.Climate, celsius: Double) =
        runControl(deviceId, control.key) {
            sdk.setHeatingSetpoint(deviceId, control.capability, celsius)
            CapabilityUiValue.Climate(sdk.readThermostat(deviceId, control.capability))
        }

    fun setFanPercent(deviceId: String, control: DeviceControl.Fan, percent: Int) = viewModelScope.launch {
        runCatching { sdk.setFanPercent(deviceId, control.capability, percent) }
            .onFailure { update { copy(errorMessage = it.message) } }
    }

    fun setWindowPosition(deviceId: String, control: DeviceControl.WindowCovering, percent: Double) = viewModelScope.launch {
        runCatching { sdk.setWindowCoveringPosition(deviceId, control.capability, percent) }
            .onFailure { update { copy(errorMessage = it.message) } }
    }

    fun openWindowCovering(deviceId: String, control: DeviceControl.WindowCovering) = viewModelScope.launch {
        runCatching { sdk.openWindowCovering(deviceId, control.capability) }
            .onFailure { update { copy(errorMessage = it.message) } }
    }

    fun closeWindowCovering(deviceId: String, control: DeviceControl.WindowCovering) = viewModelScope.launch {
        runCatching { sdk.closeWindowCovering(deviceId, control.capability) }
            .onFailure { update { copy(errorMessage = it.message) } }
    }

    fun stopWindowCovering(deviceId: String, control: DeviceControl.WindowCovering) = viewModelScope.launch {
        runCatching { sdk.stopWindowCovering(deviceId, control.capability) }
            .onFailure { update { copy(errorMessage = it.message) } }
    }

    fun controlMedia(deviceId: String, control: DeviceControl.Media, action: MediaPlaybackAction) = viewModelScope.launch {
        runCatching { sdk.controlMedia(deviceId, control.capability, action) }
            .onFailure { update { copy(errorMessage = it.message) } }
    }

    fun remove(deviceId: String, onRemoved: () -> Unit) = viewModelScope.launch {
        runCatching { sdk.removeDevice(deviceId) }
            .onSuccess { onRemoved() }
            .onFailure { update { copy(errorMessage = it.message) } }
    }

    fun clearCommissioning() = update {
        copy(setupCode = null, commissioningEvent = null, errorMessage = null)
    }

    fun clearError() = update { copy(errorMessage = null) }

    fun showError(message: String) = update { copy(errorMessage = message) }

    private fun refreshControl(deviceId: String, control: DeviceControl) =
        runControl(deviceId, control.key) {
            when (control) {
                is DeviceControl.Power -> CapabilityUiValue.Power(sdk.readOnOff(deviceId, control.capability))
                is DeviceControl.Level -> CapabilityUiValue.Level(sdk.readLevel(deviceId, control.capability))
                is DeviceControl.Color -> sdk.readColor(deviceId, control.capability).let {
                    CapabilityUiValue.Color(it.hue, it.saturation, it.colorTemperatureMireds)
                }
                is DeviceControl.Lock -> CapabilityUiValue.Lock(sdk.readLockState(deviceId, control.capability))
                is DeviceControl.Climate -> CapabilityUiValue.Climate(sdk.readThermostat(deviceId, control.capability))
                is DeviceControl.Sensor -> CapabilityUiValue.Sensor(
                    if (control.capability.kind == SensorKind.TEMPERATURE) {
                        sdk.readTemperatureCelsius(deviceId, control.capability)
                    } else null,
                )
                is DeviceControl.Fan,
                is DeviceControl.WindowCovering,
                is DeviceControl.Media,
                -> error("Control state is supplied by its subscription")
                is DeviceControl.Unsupported -> error("Unsupported controls are not interactive")
            }
        }

    private fun runControl(
        deviceId: String,
        key: CapabilityUiKey,
        interaction: suspend () -> CapabilityUiValue,
    ) = viewModelScope.launch {
        update {
            if (capabilityDeviceId == deviceId) {
                copy(loadingCapabilities = loadingCapabilities + key)
            } else {
                this
            }
        }
        runCatching { interaction() }
            .onSuccess { value ->
                update {
                    if (capabilityDeviceId == deviceId) {
                        copy(capabilityValues = capabilityValues + (key to value))
                    } else {
                        this
                    }
                }
            }
            .onFailure { error -> update { copy(errorMessage = error.message) } }
        update {
            if (capabilityDeviceId == deviceId) {
                copy(loadingCapabilities = loadingCapabilities - key)
            } else {
                this
            }
        }
    }

    private inline fun update(transform: AppUiState.() -> AppUiState) {
        mutableState.value = mutableState.value.transform()
    }

    private fun MatterCapabilityState.toUiEntry(): Pair<CapabilityUiKey, CapabilityUiValue>? = when (this) {
        is MatterCapabilityState.OnOff -> CapabilityUiKey(key.endpointId, "power") to CapabilityUiValue.Power(isOn)
        is MatterCapabilityState.Level -> CapabilityUiKey(key.endpointId, "level") to CapabilityUiValue.Level(value)
        is MatterCapabilityState.Color -> CapabilityUiKey(key.endpointId, "color") to
            CapabilityUiValue.Color(value.hue, value.saturation, value.colorTemperatureMireds)
        is MatterCapabilityState.Lock -> CapabilityUiKey(key.endpointId, "lock") to CapabilityUiValue.Lock(value)
        is MatterCapabilityState.Thermostat -> CapabilityUiKey(key.endpointId, "climate") to CapabilityUiValue.Climate(value)
        is MatterCapabilityState.Sensor -> key.sensorKind?.let { sensorKind ->
            CapabilityUiKey(key.endpointId, "sensor-${sensorKind.name}") to CapabilityUiValue.Sensor(value)
        }
        is MatterCapabilityState.Fan -> CapabilityUiKey(key.endpointId, "fan") to CapabilityUiValue.Fan(percent)
        is MatterCapabilityState.WindowCovering -> CapabilityUiKey(key.endpointId, "window-covering") to
            CapabilityUiValue.WindowCovering(liftPercent)
        is MatterCapabilityState.MediaPlayback -> CapabilityUiKey(key.endpointId, "media") to CapabilityUiValue.Media(state)
    }

    companion object {
        fun factory(sdk: MatterAppSdk): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(sdk) as T
            }
    }
}
