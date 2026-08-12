package com.example.matter.api

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import matter.tlv.AnonymousTag
import matter.tlv.TlvWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatterCapabilitySubscriptionManagerTest {
    @Test
    fun mergesColorAttributeReportsIntoOneTypedState() = runTest {
        val color = ColorCapability(3, cluster(0x0300), true, false, true)
        val manager = manager(
            mapOf(
                color to listOf(
                    changed(color, 0, unsigned(21)),
                    changed(color, 1, unsigned(99)),
                    changed(color, 7, unsigned(250)),
                ),
            ),
        )

        val states = manager.observe("7", node(color)).take(3).toList()
            .map { (it as CapabilitySubscriptionEvent.Updated).state as MatterCapabilityState.Color }

        assertEquals(ColorState(21, 99, 250), states.last().value)
    }

    @Test
    fun oneFailedCapabilityDoesNotStopOtherSubscriptions() = runTest {
        val power = OnOffCapability(1, cluster(6), true, true, true)
        val level = LevelCapability(1, cluster(8), supportsMoveToLevel = true)
        val manager = MatterCapabilitySubscriptionManager { _, capability, _, _, _ ->
            flow {
                if (capability == power) error("power unavailable")
                emit(changed(level, 0, unsigned(100)))
            }
        }

        val events = manager.observe("7", node(power, level)).take(2).toList()

        assertTrue(events.any { it is CapabilitySubscriptionEvent.Unavailable })
        assertEquals(100, (events.filterIsInstance<CapabilitySubscriptionEvent.Updated>().single().state as MatterCapabilityState.Level).value)
    }

    private fun manager(events: Map<MatterCapability, List<MatterSubscriptionEvent>>) =
        MatterCapabilitySubscriptionManager { _, capability, _, _, _ -> flow {
            events.getValue(capability).forEach { emit(it) }
        } }

    private fun changed(capability: MatterCapability, attributeId: Long, tlv: ByteArray) =
        MatterSubscriptionEvent.AttributeChanged(
            RawAttributeValue(capability.endpointId, capability.cluster.id, attributeId, tlv, null),
        )

    private fun unsigned(value: Int) = TlvWriter().putUnsigned(AnonymousTag, value).getEncoded()

    private fun node(vararg capabilities: MatterCapability) = MatterNodeCapabilities(
        "7",
        listOf(
            MatterEndpointCapabilities(
                1,
                emptyList(),
                capabilities.map { it.cluster },
                emptySet(),
                emptySet(),
                capabilities.toList(),
            ),
        ),
    )

    private fun cluster(id: Long) = MatterClusterCapabilities(
        id, 0, setOf(0, 1, 7, 17, 18), emptySet(), emptySet(), emptySet(), 1,
    )
}
