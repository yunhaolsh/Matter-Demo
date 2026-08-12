package com.example.matter.commissioning

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MatterBleAdvertisementTest {
    @Test
    fun longDiscriminatorMatchesAllTwelveBits() {
        val filter = MatterBleAdvertisement.filter(discriminator = 0xF00, shortDiscriminator = false)

        assertArrayEquals(byteArrayOf(0, 0, 0x0F), filter.serviceData)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            filter.serviceDataMask,
        )
    }

    @Test
    fun shortDiscriminatorMatchesOnlyUpperFourDiscriminatorBits() {
        val filter = MatterBleAdvertisement.filter(discriminator = 0x0A, shortDiscriminator = true)

        assertArrayEquals(byteArrayOf(0, 0, 0x0A), filter.serviceData)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0, 0x0F), filter.serviceDataMask)
    }
}
