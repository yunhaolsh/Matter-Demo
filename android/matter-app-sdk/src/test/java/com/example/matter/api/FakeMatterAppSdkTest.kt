package com.example.matter.api

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMatterAppSdkTest {
    @Test
    fun commissioningEmitsOrderedStagesAndAddsDevice() = runTest {
        val sdk = FakeMatterAppSdk(stageDelayMillis = 0)
        val setupCode = sdk.parseSetupCode("MT:W0GU2OTB00KA0648G00")

        val events = sdk.commissionWifi(setupCode, WifiCredentials("Demo Wi-Fi", "password")).toList()

        assertEquals(
            listOf(
                CommissioningEvent.Preparing::class,
                CommissioningEvent.FindingDevice::class,
                CommissioningEvent.Connecting::class,
                CommissioningEvent.JoiningNetwork::class,
                CommissioningEvent.AddingToHome::class,
                CommissioningEvent.Completed::class,
            ),
            events.map { it::class },
        )
        assertEquals(3, sdk.devices.value.size)
    }

    @Test
    fun onOffCommandsUpdateState() = runTest {
        val sdk = FakeMatterAppSdk(stageDelayMillis = 0)

        sdk.setOnOff("lamp-1", false)
        assertFalse(sdk.readOnOff("lamp-1"))
        sdk.toggle("lamp-1")
        assertTrue(sdk.readOnOff("lamp-1"))
    }

    @Test
    fun removeDeletesDevice() = runTest {
        val sdk = FakeMatterAppSdk(stageDelayMillis = 0)

        sdk.removeDevice("lamp-1")

        assertEquals(listOf("plug-1"), sdk.devices.value.map { it.id })
    }
}
