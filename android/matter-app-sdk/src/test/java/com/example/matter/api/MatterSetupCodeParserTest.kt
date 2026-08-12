package com.example.matter.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MatterSetupCodeParserTest {
    private val parser = MatterSetupCodeParser()

    @Test
    fun parsesOfficialQrCodeVector() {
        val result = parser.parse("mt:w0gu2otb00ka0648g00")

        assertEquals(SetupCode.Format.QR, result.format)
        assertEquals("MT:W0GU2OTB00KA0648G00", result.value)
    }

    @Test
    fun parsesOfficialManualCodeVectorAndRemovesSeparators() {
        val result = parser.parse("34970-112-332")

        assertEquals(SetupCode.Format.MANUAL, result.format)
        assertEquals("34970112332", result.value)
    }

    @Test
    fun rejectsCodeThatOnlyLooksLikeMatterPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("MT:DEMO123")
        }
    }
}
