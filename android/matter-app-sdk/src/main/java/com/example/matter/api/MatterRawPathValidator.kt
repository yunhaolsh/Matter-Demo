package com.example.matter.api

internal object MatterRawPathValidator {
    fun requireAttribute(capability: MatterCapability, attributeId: Long) {
        require(attributeId in capability.cluster.attributeIds) {
            "Attribute was not discovered on this cluster"
        }
    }

    fun requireCommand(capability: MatterCapability, commandId: Long) {
        require(commandId in capability.cluster.acceptedCommandIds) {
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
        require(attributeIds.all { it in capability.cluster.attributeIds }) {
            "An attribute was not discovered on this cluster"
        }
        require(eventIds.all { it in capability.cluster.eventIds }) {
            "An event was not discovered on this cluster"
        }
        require(minIntervalSeconds >= 0) { "Minimum interval cannot be negative" }
        require(maxIntervalSeconds >= minIntervalSeconds) {
            "Maximum interval must be greater than or equal to the minimum interval"
        }
    }

    fun requireTimedRequestTimeout(timedRequestTimeoutMillis: Int) {
        require(timedRequestTimeoutMillis >= 0) { "Timed request timeout cannot be negative" }
    }
}
