package com.example.matter.commissioning

internal data class MatterBleAdvertisementFilter(
    val serviceData: ByteArray,
    val serviceDataMask: ByteArray,
)

internal object MatterBleAdvertisement {
    fun filter(discriminator: Int, shortDiscriminator: Boolean): MatterBleAdvertisementFilter {
        return if (shortDiscriminator) {
            require(discriminator in 0..0x0F) { "Short discriminator must fit in 4 bits" }
            MatterBleAdvertisementFilter(
                serviceData = byteArrayOf(0, 0, discriminator.toByte()),
                serviceDataMask = byteArrayOf(0xFF.toByte(), 0, 0x0F),
            )
        } else {
            require(discriminator in 0..0x0FFF) { "Long discriminator must fit in 12 bits" }
            MatterBleAdvertisementFilter(
                serviceData =
                    byteArrayOf(
                        0,
                        discriminator.toByte(),
                        (discriminator shr 8).toByte(),
                    ),
                serviceDataMask = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            )
        }
    }
}
