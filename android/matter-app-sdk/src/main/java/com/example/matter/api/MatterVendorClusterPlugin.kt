package com.example.matter.api

interface MatterVendorClusterPlugin {
    val id: String
    val clusterIds: Set<Long>

    fun map(endpointId: Int, cluster: MatterClusterCapabilities): VendorClusterDefinition?
}

data class VendorClusterDefinition(
    val displayName: String,
    val readableAttributeIds: Set<Long> = emptySet(),
    val writableAttributeIds: Set<Long> = emptySet(),
    val invokableCommandIds: Set<Long> = emptySet(),
    val subscribableEventIds: Set<Long> = emptySet(),
)

internal class MatterVendorClusterRegistry(plugins: List<MatterVendorClusterPlugin>) {
    private val pluginsByCluster = buildMap {
        plugins.forEach { plugin ->
            require(plugin.id.isNotBlank()) { "Vendor plugin ID cannot be blank" }
            plugin.clusterIds.forEach { clusterId ->
                require(clusterId > STANDARD_CLUSTER_ID_MAX) { "Vendor plugins cannot override standard Matter clusters" }
                require(put(clusterId, plugin) == null) { "More than one vendor plugin registered cluster $clusterId" }
            }
        }
    }

    fun map(endpointId: Int, cluster: MatterClusterCapabilities): VendorClusterCapability? {
        val plugin = pluginsByCluster[cluster.id] ?: return null
        val definition = plugin.map(endpointId, cluster) ?: return null
        require(definition.readableAttributeIds.all { it in cluster.attributeIds }) { "Plugin declared an undiscovered readable attribute" }
        require(definition.writableAttributeIds.all { it in cluster.attributeIds }) { "Plugin declared an undiscovered writable attribute" }
        require(definition.invokableCommandIds.all { it in cluster.acceptedCommandIds }) { "Plugin declared an undiscovered command" }
        require(definition.subscribableEventIds.all { it in cluster.eventIds }) { "Plugin declared an undiscovered event" }
        return VendorClusterCapability(
            endpointId,
            cluster,
            plugin.id,
            definition.displayName,
            definition.readableAttributeIds,
            definition.writableAttributeIds,
            definition.invokableCommandIds,
            definition.subscribableEventIds,
        )
    }

    private companion object {
        const val STANDARD_CLUSTER_ID_MAX = 0xFFFFL
    }
}
