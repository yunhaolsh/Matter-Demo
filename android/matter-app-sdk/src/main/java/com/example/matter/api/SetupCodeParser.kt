package com.example.matter.api

import matter.onboardingpayload.OnboardingPayloadParser

fun interface SetupCodeParser {
    fun parse(rawCode: String): SetupCode
}

class MatterSetupCodeParser : SetupCodeParser {
    private val parser = OnboardingPayloadParser()

    override fun parse(rawCode: String): SetupCode {
        val normalized = rawCode.trim()
        require(normalized.isNotEmpty()) { "Enter a setup code" }

        return runCatching {
            if (normalized.startsWith(QR_PREFIX, ignoreCase = true)) {
                val qrCode = normalized.uppercase()
                parser.parseQrCode(qrCode)
                SetupCode(qrCode, SetupCode.Format.QR)
            } else {
                val manualCode = normalized.filterNot { it == '-' || it.isWhitespace() }
                parser.parseManualPairingCode(manualCode)
                SetupCode(manualCode, SetupCode.Format.MANUAL)
            }
        }.getOrElse { error ->
            throw IllegalArgumentException("Use a valid Matter QR or manual setup code", error)
        }
    }

    private companion object {
        const val QR_PREFIX = "MT:"
    }
}
