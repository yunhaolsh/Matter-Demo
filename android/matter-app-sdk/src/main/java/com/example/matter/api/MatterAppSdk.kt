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
    suspend fun refresh(deviceId: String): MatterDevice
    suspend fun setOnOff(deviceId: String, value: Boolean)
    suspend fun toggle(deviceId: String)
    suspend fun readOnOff(deviceId: String): Boolean
    fun observeOnOff(deviceId: String): Flow<OnOffState>
    suspend fun removeDevice(deviceId: String)
}
