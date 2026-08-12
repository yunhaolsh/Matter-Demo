package com.example.matter.api

import matter.tlv.AnonymousTag
import matter.tlv.TlvReader

internal object MatterAttributeValueDecoder {
    fun boolean(value: RawAttributeValue): Boolean =
        TlvReader(value.tlv).getBoolean(AnonymousTag)

    fun unsignedByte(value: RawAttributeValue): Int? = nullable(value) { getUByte(AnonymousTag).toInt() }

    fun unsignedShort(value: RawAttributeValue): Int? = nullable(value) { getUShort(AnonymousTag).toInt() }

    fun signedShort(value: RawAttributeValue): Int? = nullable(value) { getShort(AnonymousTag).toInt() }

    private fun <T> nullable(value: RawAttributeValue, decode: TlvReader.() -> T): T? {
        val reader = TlvReader(value.tlv)
        if (reader.isNull()) {
            reader.getNull(AnonymousTag)
            return null
        }
        return reader.decode()
    }
}
