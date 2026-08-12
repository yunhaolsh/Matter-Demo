package com.example.matter.api

internal object MatterRawPathValidator {
    fun requireAttribute(capability: MatterCapability, attributeId: Long) {
        val allowed = if (capability is VendorClusterCapability) capability.readableAttributeIds else capability.cluster.attributeIds
        require(attributeId in allowed) {
            "Attribute was not discovered on this cluster"
        }
    }

    fun requireCommand(capability: MatterCapability, commandId: Long) {
        val allowed = if (capability is VendorClusterCapability) capability.invokableCommandIds else capability.cluster.acceptedCommandIds
        require(commandId in allowed) {
            "Command was not discovered in the cluster's accepted command list"
        }
    }

    fun requireSubscription(
        capability: MatterCapability,
        attributeIds: Set<Long>,
        eventIds: Set<Long>,
        minIntervalSeconds: Int,
        maxIntervalSeconds: Int,
    ) {
        require(attributeIds.isNotEmpty() || eventIds.isNotEmpty()) {
            "At least one attribute or event path is required"
        }
        val allowedAttributes = if (capability is VendorClusterCapability) capability.readableAttributeIds else capability.cluster.attributeIds
        val allowedEvents = if (capability is VendorClusterCapability) capability.subscribableEventIds else capability.cluster.eventIds
        require(attributeIds.all { it in allowedAttributes }) {
            "An attribute was not discovered on this cluster"
        }
        require(eventIds.all { it in allowedEvents }) {
            "An event was not discovered on this cluster"
        }
        require(minIntervalSeconds >= 0) { "Minimum interval cannot be negative" }
        require(maxIntervalSeconds >= minIntervalSeconds) {
            "Maximum interval must be greater than or equal to the minimum interval"
        }
    }

    fun requireWritableAttribute(capability: MatterCapability, attributeId: Long) {
        val allowed = if (capability is VendorClusterCapability) capability.writableAttributeIds else capability.cluster.attributeIds
        require(attributeId in allowed) { "Attribute is not writable through this capability" }
    }

    fun requireTimedRequestTimeout(timedRequestTimeoutMillis: Int) {
        require(timedRequestTimeoutMillis >= 0) { "Timed request timeout cannot be negative" }
    }
}
