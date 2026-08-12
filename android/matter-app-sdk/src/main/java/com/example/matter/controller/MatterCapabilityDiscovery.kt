package com.example.matter.controller

import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ChipStructs
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.NodeState
import com.example.matter.api.MatterClusterCapabilities
import com.example.matter.api.MatterCapabilityRegistry
import com.example.matter.api.MatterDeviceType
import com.example.matter.api.MatterDeviceProfileResolver
import com.example.matter.api.MatterEndpointCapabilities
import com.example.matter.api.MatterNodeCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

internal class MatterCapabilityDiscovery(
    private val controller: ChipDeviceController,
    private val timeoutMillis: Long = 120_000L,
) {
    suspend fun discover(nodeId: Long, devicePointer: Long): MatterNodeCapabilities =
        withTimeout(timeoutMillis) {
            val descriptors = linkedMapOf<Int, EndpointDescriptor>()
            val pending = ArrayDeque<Int>().apply { add(ROOT_ENDPOINT) }
            while (pending.isNotEmpty()) {
                val endpointId = pending.removeFirst()
                if (descriptors.containsKey(endpointId)) continue
                val descriptor = readDescriptor(devicePointer, endpointId)
                descriptors[endpointId] = descriptor
                descriptor.parts.filterNot(descriptors::containsKey).forEach(pending::addLast)
            }

            val metadata = readClusterMetadata(devicePointer, descriptors)
            val capabilities = MatterNodeCapabilities(
                nodeId = nodeId.toString(),
                endpoints = descriptors.map { (endpointId, descriptor) ->
                    val serverClusters = descriptor.serverClusterIds.map { clusterId ->
                        metadata[endpointId to clusterId] ?: MatterClusterCapabilities(clusterId)
                    }
                    MatterEndpointCapabilities(
                        endpointId = endpointId,
                        deviceTypes = descriptor.deviceTypes,
                        serverClusters = serverClusters,
                        clientClusterIds = descriptor.clientClusterIds,
                        parts = descriptor.parts,
                        capabilities = serverClusters.map { MatterCapabilityRegistry.map(endpointId, it) },
                    )
                },
            )
            capabilities.copy(profile = MatterDeviceProfileResolver.resolve(capabilities))
        }

    private suspend fun readDescriptor(devicePointer: Long, endpointId: Int): EndpointDescriptor {
        val cluster = ChipClusters.DescriptorCluster(devicePointer, endpointId)
        val deviceTypes = awaitAttribute<List<ChipStructs.DescriptorClusterDeviceTypeStruct>> { callback ->
            cluster.readDeviceTypeListAttribute(
                object : ChipClusters.DescriptorCluster.DeviceTypeListAttributeCallback {
                    override fun onSuccess(value: List<ChipStructs.DescriptorClusterDeviceTypeStruct>) = callback.success(value)
                    override fun onError(error: Exception) = callback.error(error)
                },
            )
        }
        val serverClusters = awaitAttribute<List<Long>> { callback ->
            cluster.readServerListAttribute(
                object : ChipClusters.DescriptorCluster.ServerListAttributeCallback {
                    override fun onSuccess(value: List<Long>) = callback.success(value)
                    override fun onError(error: Exception) = callback.error(error)
                },
            )
        }
        val clientClusters = awaitAttribute<List<Long>> { callback ->
            cluster.readClientListAttribute(
                object : ChipClusters.DescriptorCluster.ClientListAttributeCallback {
                    override fun onSuccess(value: List<Long>) = callback.success(value)
                    override fun onError(error: Exception) = callback.error(error)
                },
            )
        }
        val parts = awaitAttribute<List<Int>> { callback ->
            cluster.readPartsListAttribute(
                object : ChipClusters.DescriptorCluster.PartsListAttributeCallback {
                    override fun onSuccess(value: List<Int>) = callback.success(value)
                    override fun onError(error: Exception) = callback.error(error)
                },
            )
        }
        return EndpointDescriptor(
            deviceTypes = deviceTypes.map { MatterDeviceType(it.deviceType, it.revision) },
            serverClusterIds = serverClusters.toSet(),
            clientClusterIds = clientClusters.toSet(),
            parts = parts.toSet(),
        )
    }

    private suspend fun readClusterMetadata(
        devicePointer: Long,
        descriptors: Map<Int, EndpointDescriptor>,
    ): Map<Pair<Int, Long>, MatterClusterCapabilities> {
        val paths = descriptors.flatMap { (endpointId, descriptor) ->
            descriptor.serverClusterIds.flatMap { clusterId ->
                GLOBAL_ATTRIBUTE_IDS.map { attributeId ->
                    ChipAttributePath.newInstance(endpointId, clusterId, attributeId)
                }
            }
        }
        if (paths.isEmpty()) return emptyMap()

        val reports = paths.chunked(MAX_PATHS_PER_READ).flatMap { batch ->
            readAttributeBatch(devicePointer, batch)
        }

        return descriptors.flatMap { (endpointId, descriptor) ->
            descriptor.serverClusterIds.map { clusterId ->
                val values = reports.mapNotNull { it.getEndpointState(endpointId)?.getClusterState(clusterId) }
                (endpointId to clusterId) to
                    MatterClusterCapabilities(
                        id = clusterId,
                        featureMap = values.value(FEATURE_MAP_ATTRIBUTE_ID).asLong(),
                        attributeIds = values.value(ATTRIBUTE_LIST_ATTRIBUTE_ID).asLongSet(),
                        acceptedCommandIds = values.value(ACCEPTED_COMMAND_LIST_ATTRIBUTE_ID).asLongSet(),
                        generatedCommandIds = values.value(GENERATED_COMMAND_LIST_ATTRIBUTE_ID).asLongSet(),
                        eventIds = values.value(EVENT_LIST_ATTRIBUTE_ID).asLongSet(),
                        revision = values.value(CLUSTER_REVISION_ATTRIBUTE_ID).asInt(),
                    )
            }
        }.toMap()
    }

    private suspend fun readAttributeBatch(
        devicePointer: Long,
        paths: List<ChipAttributePath>,
    ): List<NodeState> {
        val reports = mutableListOf<NodeState>()
        val completed = CompletableDeferred<Unit>()
        controller.readAttributePath(
            object : ReportCallback {
                override fun onReport(nodeState: NodeState) {
                    reports += nodeState
                }

                override fun onError(
                    attributePath: ChipAttributePath?,
                    eventPath: ChipEventPath?,
                    error: Exception,
                ) = Unit

                override fun onDone() {
                    completed.complete(Unit)
                }
            },
            devicePointer,
            paths,
            timeoutMillis.toInt(),
        )
        completed.await()
        return reports
    }

    private suspend fun <T> awaitAttribute(register: (AttributeResult<T>) -> Unit): T {
        val result = CompletableDeferred<T>()
        register(AttributeResult(result))
        return result.await()
    }

    private class AttributeResult<T>(private val result: CompletableDeferred<T>) {
        fun success(value: T) {
            result.complete(value)
        }

        fun error(error: Exception) {
            result.completeExceptionally(error)
        }
    }

    private data class EndpointDescriptor(
        val deviceTypes: List<MatterDeviceType>,
        val serverClusterIds: Set<Long>,
        val clientClusterIds: Set<Long>,
        val parts: Set<Int>,
    )

    private fun List<chip.devicecontroller.model.ClusterState>.value(attributeId: Long): Any? =
        firstNotNullOfOrNull { it.getAttributeState(attributeId)?.value }

    private fun Any?.asLong(): Long? = (this as? Number)?.toLong()
    private fun Any?.asInt(): Int? = (this as? Number)?.toInt()
    private fun Any?.asLongSet(): Set<Long> =
        (this as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }?.toSet().orEmpty()

    private companion object {
        const val ROOT_ENDPOINT = 0
        const val MAX_PATHS_PER_READ = 48
        const val GENERATED_COMMAND_LIST_ATTRIBUTE_ID = 0xFFF8L
        const val ACCEPTED_COMMAND_LIST_ATTRIBUTE_ID = 0xFFF9L
        const val EVENT_LIST_ATTRIBUTE_ID = 0xFFFAL
        const val ATTRIBUTE_LIST_ATTRIBUTE_ID = 0xFFFBL
        const val FEATURE_MAP_ATTRIBUTE_ID = 0xFFFCL
        const val CLUSTER_REVISION_ATTRIBUTE_ID = 0xFFFDL
        val GLOBAL_ATTRIBUTE_IDS =
            listOf(
                GENERATED_COMMAND_LIST_ATTRIBUTE_ID,
                ACCEPTED_COMMAND_LIST_ATTRIBUTE_ID,
                EVENT_LIST_ATTRIBUTE_ID,
                ATTRIBUTE_LIST_ATTRIBUTE_ID,
                FEATURE_MAP_ATTRIBUTE_ID,
                CLUSTER_REVISION_ATTRIBUTE_ID,
            )
    }
}

private fun MatterClusterCapabilities(id: Long) =
    MatterClusterCapabilities(
        id = id,
        featureMap = null,
        attributeIds = emptySet(),
        acceptedCommandIds = emptySet(),
        generatedCommandIds = emptySet(),
        eventIds = emptySet(),
        revision = null,
    )
