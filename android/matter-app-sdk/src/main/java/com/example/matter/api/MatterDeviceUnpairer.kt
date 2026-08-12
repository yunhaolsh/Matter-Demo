package com.example.matter.api

import chip.devicecontroller.UnpairDeviceCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

internal class MatterDeviceUnpairer(
    private val timeoutMillis: Long = 30_000L,
) {
    suspend fun unpair(
        nodeId: Long,
        start: (UnpairDeviceCallback) -> Unit,
    ) = withTimeout(timeoutMillis) {
        val result = CompletableDeferred<Unit>()
        start(
            object : UnpairDeviceCallback {
                override fun onSuccess(remoteDeviceId: Long) {
                    if (remoteDeviceId == nodeId) {
                        result.complete(Unit)
                    } else {
                        result.completeExceptionally(
                            IllegalStateException("Unpair response was for unexpected node $remoteDeviceId"),
                        )
                    }
                }

                override fun onError(status: Int, remoteDeviceId: Long) {
                    result.completeExceptionally(
                        IllegalStateException("Unable to unpair Matter node $remoteDeviceId ($status)"),
                    )
                }
            },
        )
        result.await()
    }
}
