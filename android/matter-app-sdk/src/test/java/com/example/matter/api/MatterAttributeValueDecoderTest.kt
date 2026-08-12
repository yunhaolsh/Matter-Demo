package com.example.matter.api

import matter.tlv.AnonymousTag
import matter.tlv.TlvWriter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatterAttributeValueDecoderTest {
    @Test
    fun decodesAnonymousBooleanAttributeValues() {
        assertTrue(MatterAttributeValueDecoder.boolean(value(true)))
        assertFalse(MatterAttributeValueDecoder.boolean(value(false)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonBooleanAttributeValue() {
        val encoded = TlvWriter().put(AnonymousTag, 42).getEncoded()
        MatterAttributeValueDecoder.boolean(RawAttributeValue(1, 6, 0, encoded, null))
    }

    @Test
    fun decodesSignedUnsignedAndNullableValues() {
        assertEquals(254, MatterAttributeValueDecoder.unsignedByte(raw(TlvWriter().putUnsigned(AnonymousTag, 254).getEncoded())))
        assertEquals(500, MatterAttributeValueDecoder.unsignedShort(raw(TlvWriter().putUnsigned(AnonymousTag, 500).getEncoded())))
        assertEquals(-1234, MatterAttributeValueDecoder.signedShort(raw(TlvWriter().put(AnonymousTag, (-1234).toShort()).getEncoded())))
        assertNull(MatterAttributeValueDecoder.signedShort(raw(TlvWriter().putNull(AnonymousTag).getEncoded())))
    }

    private fun value(boolean: Boolean): RawAttributeValue =
        RawAttributeValue(
            endpointId = 1,
            clusterId = 6,
            attributeId = 0,
            tlv = TlvWriter().put(AnonymousTag, boolean).getEncoded(),
            json = null,
        )

    private fun raw(tlv: ByteArray) = RawAttributeValue(1, 6, 0, tlv, null)
}
