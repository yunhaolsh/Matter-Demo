package com.example.matter.api

import org.junit.Assert.assertThrows
import org.junit.Test

class MatterRawPathValidatorTest {
    private val capability =
        RawClusterCapability(
            endpointId = 3,
            cluster =
                MatterClusterCapabilities(
                    id = 0xFFF1_0001,
                    featureMap = null,
                    attributeIds = setOf(1, 2),
                    acceptedCommandIds = setOf(3),
                    generatedCommandIds = setOf(4),
                    eventIds = setOf(5),
                    revision = 1,
                ),
        )

    @Test
    fun acceptsDiscoveredRawPaths() {
        MatterRawPathValidator.requireAttribute(capability, 1)
        MatterRawPathValidator.requireCommand(capability, 3)
        MatterRawPathValidator.requireSubscription(capability, setOf(2), setOf(5), 0, 60)
        MatterRawPathValidator.requireTimedRequestTimeout(0)
    }

    @Test
    fun rejectsPathsThatWereNotDiscovered() {
        assertThrows(IllegalArgumentException::class.java) {
            MatterRawPathValidator.requireAttribute(capability, 99)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterRawPathValidator.requireCommand(capability, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterRawPathValidator.requireSubscription(capability, emptySet(), setOf(99), 1, 60)
        }
    }

    @Test
    fun rejectsEmptyOrInvalidSubscriptionRanges() {
        assertThrows(IllegalArgumentException::class.java) {
            MatterRawPathValidator.requireSubscription(capability, emptySet(), emptySet(), 1, 60)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterRawPathValidator.requireSubscription(capability, setOf(1), emptySet(), 10, 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatterRawPathValidator.requireSubscription(capability, setOf(1), emptySet(), -1, 5)
        }
    }

    @Test
    fun rejectsNegativeTimedRequestTimeout() {
        assertThrows(IllegalArgumentException::class.java) {
            MatterRawPathValidator.requireTimedRequestTimeout(-1)
        }
    }
}
