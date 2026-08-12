package com.example.matter.api

import kotlin.math.roundToInt

internal object MatterValueConverter {
    fun temperatureCelsius(rawValue: Int?): Double? = rawValue?.div(100.0)

    fun temperatureHundredths(celsius: Double): Int {
        require(celsius.isFinite()) { "Temperature must be finite" }
        val rawValue = (celsius * 100).roundToInt()
        require(rawValue in (Short.MIN_VALUE + 1)..Short.MAX_VALUE) { "Temperature is outside the Matter range" }
        return rawValue
    }

    fun lockState(rawValue: Int?): LockState =
        when (rawValue) {
            0 -> LockState.NOT_FULLY_LOCKED
            1 -> LockState.LOCKED
            2 -> LockState.UNLOCKED
            3 -> LockState.UNLATCHED
            else -> LockState.UNKNOWN
        }
}
