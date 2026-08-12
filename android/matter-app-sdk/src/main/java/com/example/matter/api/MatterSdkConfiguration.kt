package com.example.matter.api

data class MatterSdkConfiguration(
    val vendorPlugins: List<MatterVendorClusterPlugin> = emptyList(),
    val attestationPolicy: DeviceAttestationPolicy = DeviceAttestationPolicy.Strict,
)

sealed interface DeviceAttestationPolicy {
    data object Strict : DeviceAttestationPolicy
    data object AllowDevelopmentDevices : DeviceAttestationPolicy
    data class Custom(val evaluate: (DeviceAttestationResult) -> Boolean) : DeviceAttestationPolicy
}

data class DeviceAttestationResult(
    val vendorId: Int,
    val productId: Int,
    val verificationResult: Long,
) {
    val passed: Boolean get() = verificationResult == 0L
}

data class CommissioningWindow(
    val setupCode: SetupCode?,
    val durationSeconds: Int,
    val enhanced: Boolean,
)
