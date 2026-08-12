package com.example.matter.commissioning

import org.junit.Assert.assertTrue
import org.junit.Test

class MatterBleDeviceNotFoundExceptionTest {
    @Test
    fun explainsHowToRestorePairingMode() {
        val error = MatterBleDeviceNotFoundException(IllegalStateException("timeout"))

        assertTrue(error.message.orEmpty().contains("pairing mode"))
        assertTrue(error.message.orEmpty().contains("Bluetooth"))
    }
}
