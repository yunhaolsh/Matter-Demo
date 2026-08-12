package com.example.matter.controller

import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.InvokeCallback
import chip.devicecontroller.WriteAttributesCallback
import chip.devicecontroller.model.AttributeWriteRequest
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.InvokeElement
import chip.devicecontroller.model.Status
import com.example.matter.api.MatterCapability
import com.example.matter.api.RawInvokeResult
import com.example.matter.api.RawWriteResult
import kotlinx.coroutines.CompletableDeferred

internal class MatterRawInteraction(
    private val controller: ChipDeviceController,
    private val interactionTimeoutMillis: Int,
) {
    suspend fun writeAttribute(
        devicePointer: Long,
        capability: MatterCapability,
        attributeId: Long,
        tlv: ByteArray,
        timedRequestTimeoutMillis: Int,
    ): RawWriteResult {
        val result = CompletableDeferred<RawWriteResult>()
        val request =
            AttributeWriteRequest.newInstance(
                capability.endpointId,
                capability.cluster.id,
                attributeId,
                tlv.copyOf(),
            )
        controller.write(
            object : WriteAttributesCallback {
                override fun onResponse(path: ChipAttributePath, status: Status) {
                    result.complete(
                        RawWriteResult(
                            endpointId = capability.endpointId,
                            clusterId = capability.cluster.id,
                            attributeId = attributeId,
                            statusCode = status.status.id,
                            clusterStatus = status.clusterStatus.orElse(null),
                        ),
                    )
                }

                override fun onError(path: ChipAttributePath?, error: Exception) {
                    result.completeExceptionally(error)
                }

                override fun onDone() {
                    if (!result.isCompleted) {
                        result.completeExceptionally(
                            IllegalStateException("Matter attribute write returned no status"),
                        )
                    }
                }
            },
            devicePointer,
            listOf(request),
            timedRequestTimeoutMillis,
            interactionTimeoutMillis,
        )
        return result.await()
    }

    suspend fun invokeCommand(
        devicePointer: Long,
        capability: MatterCapability,
        commandId: Long,
        tlv: ByteArray,
        timedRequestTimeoutMillis: Int,
    ): RawInvokeResult {
        val result = CompletableDeferred<RawInvokeResult>()
        val request =
            InvokeElement.newInstance(
                capability.endpointId,
                capability.cluster.id,
                commandId,
                tlv.copyOf(),
                null,
            )
        controller.invoke(
            object : InvokeCallback {
                override fun onResponse(response: InvokeElement, statusCode: Long) {
                    result.complete(
                        RawInvokeResult(
                            endpointId = response.endpointId.id.toInt(),
                            clusterId = response.clusterId.id,
                            commandId = response.commandId.id,
                            statusCode = statusCode,
                            tlv = response.tlvByteArray,
                            json = response.jsonString,
                        ),
                    )
                }

                override fun onError(error: Exception) {
                    result.completeExceptionally(error)
                }

                override fun onDone() {
                    if (!result.isCompleted) {
                        result.completeExceptionally(
                            IllegalStateException("Matter command invoke returned no response"),
                        )
                    }
                }
            },
            devicePointer,
            request,
            timedRequestTimeoutMillis,
            interactionTimeoutMillis,
        )
        return result.await()
    }
}
