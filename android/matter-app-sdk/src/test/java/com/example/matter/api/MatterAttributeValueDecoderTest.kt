package com.example.matter.api

import matter.tlv.AnonymousTag
import matter.tlv.TlvWriter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun value(boolean: Boolean): RawAttributeValue =
        RawAttributeValue(
            endpointId = 1,
            clusterId = 6,
            attributeId = 0,
            tlv = TlvWriter().put(AnonymousTag, boolean).getEncoded(),
            json = null,
        )
}
