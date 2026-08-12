package com.example.matter.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MatterValueConverterTest {
    @Test
    fun convertsMatterTemperatureHundredthsToCelsius() {
        assertEquals(21.5, MatterValueConverter.temperatureCelsius(2150)!!, 0.0)
        assertEquals(-5.25, MatterValueConverter.temperatureCelsius(-525)!!, 0.0)
    }

    @Test
    fun preservesNullTemperature() {
        assertNull(MatterValueConverter.temperatureCelsius(null))
    }

    @Test
    fun convertsCelsiusToMatterHundredths() {
        assertEquals(2150, MatterValueConverter.temperatureHundredths(21.5))
        assertEquals(-525, MatterValueConverter.temperatureHundredths(-5.25))
    }

    @Test
    fun mapsDoorLockState() {
        assertEquals(LockState.LOCKED, MatterValueConverter.lockState(1))
        assertEquals(LockState.UNLOCKED, MatterValueConverter.lockState(2))
        assertEquals(LockState.UNKNOWN, MatterValueConverter.lockState(null))
        assertEquals(LockState.UNKNOWN, MatterValueConverter.lockState(99))
    }

    @Test
    fun rejectsMatterNullTemperatureSentinel() {
        assertThrows(IllegalArgumentException::class.java) {
            MatterValueConverter.temperatureHundredths(-327.68)
        }
    }
}
