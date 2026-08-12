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

    override fun onCleared() {
        sdk.close()
        super.onCleared()
    }

    private inline fun update(transform: AppUiState.() -> AppUiState) {
        mutableState.value = mutableState.value.transform()
    }

    companion object {
        fun factory(sdk: MatterAppSdk): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(sdk) as T
            }
    }
}
