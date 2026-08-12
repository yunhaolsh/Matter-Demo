package com.example.matter.api

import matter.tlv.AnonymousTag
import matter.tlv.TlvReader

internal object MatterAttributeValueDecoder {
    fun boolean(value: RawAttributeValue): Boolean =
        TlvReader(value.tlv).getBoolean(AnonymousTag)
}
