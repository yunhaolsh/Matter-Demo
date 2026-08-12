package com.example.matter.storage

import com.example.matter.api.ConnectionMode
import com.example.matter.api.DeviceAvailability
import com.example.matter.api.DeviceType
import com.example.matter.api.MatterCapabilityRegistry
import com.example.matter.api.MatterClusterCapabilities
import com.example.matter.api.MatterDevice
import com.example.matter.api.MatterDeviceProfileResolver
import com.example.matter.api.MatterDeviceType
import com.example.matter.api.MatterEndpointCapabilities
import com.example.matter.api.MatterNodeCapabilities
import com.example.matter.api.MatterRoom
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

internal object MatterDeviceSnapshotCodec {
    fun encode(device: MatterDevice): String {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(FORMAT_VERSION)
            data.writeUTF(device.id)
            data.writeUTF(device.name)
            data.writeUTF(device.room.id)
            data.writeUTF(device.room.name)
            data.writeUTF(device.type.name)
            data.writeUTF(device.connectionMode.name)
            data.writeBoolean(device.isOn)
            val capabilities = device.capabilities
            data.writeBoolean(capabilities != null)
            capabilities?.let { node ->
                data.writeInt(node.endpoints.size)
                node.endpoints.forEach { endpoint -> writeEndpoint(data, endpoint) }
            }
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    fun decode(encoded: String): MatterDevice {
        val input = DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded)))
        return input.use { data ->
            require(data.readInt() == FORMAT_VERSION) { "Unsupported Matter device snapshot" }
            val id = data.readUTF()
            val name = data.readUTF()
            val room = MatterRoom(data.readUTF(), data.readUTF())
            val storedType = enumValueOrDefault(data.readUTF(), DeviceType.UNKNOWN)
            val connectionMode = enumValueOrDefault(data.readUTF(), ConnectionMode.LOCAL)
            val isOn = data.readBoolean()
            val capabilities = if (data.readBoolean()) {
                val endpoints = List(data.readInt()) { readEndpoint(data) }
                val node = MatterNodeCapabilities(id, endpoints)
                node.copy(profile = MatterDeviceProfileResolver.resolve(node))
            } else null
            MatterDevice(
                id = id,
                name = name,
                room = room,
                type = capabilities?.profile?.type ?: storedType,
                connectionMode = connectionMode,
                availability = DeviceAvailability.CONNECTING,
                isOn = isOn,
                capabilities = capabilities,
                profile = capabilities?.profile,
            )
        }
    }

    private fun writeEndpoint(data: DataOutputStream, endpoint: MatterEndpointCapabilities) {
        data.writeInt(endpoint.endpointId)
        data.writeInt(endpoint.deviceTypes.size)
        endpoint.deviceTypes.forEach {
            data.writeLong(it.id)
            data.writeInt(it.revision)
        }
        data.writeInt(endpoint.serverClusters.size)
        endpoint.serverClusters.forEach { writeCluster(data, it) }
        data.writeLongSet(endpoint.clientClusterIds)
        data.writeIntSet(endpoint.parts)
    }

    private fun readEndpoint(data: DataInputStream): MatterEndpointCapabilities {
        val endpointId = data.readInt()
        val deviceTypes = List(data.readInt()) { MatterDeviceType(data.readLong(), data.readInt()) }
        val clusters = List(data.readInt()) { readCluster(data) }
        return MatterEndpointCapabilities(
            endpointId = endpointId,
            deviceTypes = deviceTypes,
            serverClusters = clusters,
            clientClusterIds = data.readLongSet(),
            parts = data.readIntSet(),
            capabilities = clusters.map { MatterCapabilityRegistry.map(endpointId, it) },
        )
    }

    private fun writeCluster(data: DataOutputStream, cluster: MatterClusterCapabilities) {
        data.writeLong(cluster.id)
        data.writeBoolean(cluster.featureMap != null)
        cluster.featureMap?.let(data::writeLong)
        data.writeLongSet(cluster.attributeIds)
        data.writeLongSet(cluster.acceptedCommandIds)
        data.writeLongSet(cluster.generatedCommandIds)
        data.writeLongSet(cluster.eventIds)
        data.writeBoolean(cluster.revision != null)
        cluster.revision?.let(data::writeInt)
    }

    private fun readCluster(data: DataInputStream) = MatterClusterCapabilities(
        id = data.readLong(),
        featureMap = if (data.readBoolean()) data.readLong() else null,
        attributeIds = data.readLongSet(),
        acceptedCommandIds = data.readLongSet(),
        generatedCommandIds = data.readLongSet(),
        eventIds = data.readLongSet(),
        revision = if (data.readBoolean()) data.readInt() else null,
    )

    private fun DataOutputStream.writeLongSet(values: Set<Long>) {
        writeInt(values.size)
        values.sorted().forEach(::writeLong)
    }

    private fun DataInputStream.readLongSet(): Set<Long> = List(readInt()) { readLong() }.toSet()

    private fun DataOutputStream.writeIntSet(values: Set<Int>) {
        writeInt(values.size)
        values.sorted().forEach(::writeInt)
    }

    private fun DataInputStream.readIntSet(): Set<Int> = List(readInt()) { readInt() }.toSet()

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default

    private const val FORMAT_VERSION = 1
}
