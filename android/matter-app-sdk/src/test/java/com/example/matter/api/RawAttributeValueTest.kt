package com.example.matter.api

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RawAttributeValueTest {
    @Test
    fun protectsTlvFromExternalMutation() {
        val source = byteArrayOf(1, 2, 3)
        val value = RawAttributeValue(1, 6, 0, source, null)
        source[0] = 9
        val exposed = value.tlv
        exposed[1] = 9

        assertArrayEquals(byteArrayOf(1, 2, 3), value.tlv)
    }

    @Test
    fun protectsEventAndInvokeTlvFromExternalMutation() {
        val eventSource = byteArrayOf(4, 5)
        val invokeSource = byteArrayOf(6, 7)
        val event = RawEventValue(1, 6, 0, 1, 1, 0, 10, eventSource, null)
        val invoke = RawInvokeResult(1, 6, 1, 0, invokeSource, null)

        eventSource[0] = 9
        invokeSource[0] = 9
        event.tlv[1] = 9
        invoke.tlv!![1] = 9

        assertArrayEquals(byteArrayOf(4, 5), event.tlv)
        assertArrayEquals(byteArrayOf(6, 7), invoke.tlv)
    }
}
