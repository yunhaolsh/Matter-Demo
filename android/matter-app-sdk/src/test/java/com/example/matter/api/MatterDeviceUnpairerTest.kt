package com.example.matter.api

import chip.devicecontroller.UnpairDeviceCallback
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MatterDeviceUnpairerTest {
    @Test
    fun completesOnlyAfterMatchingSuccessCallback() = runTest {
        MatterDeviceUnpairer().unpair(7) { callback -> callback.onSuccess(7) }
    }

    @Test
    fun propagatesRemoteFailure() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                runTest {
                    MatterDeviceUnpairer().unpair(7) { callback -> callback.onError(42, 7) }
                }
            }

        assertEquals("Unable to unpair Matter node 7 (42)", error.message)
    }

    @Test
    fun rejectsCallbackForAnotherNode() {
        assertThrows(IllegalStateException::class.java) {
            runTest {
                MatterDeviceUnpairer().unpair(7) { callback -> callback.onSuccess(8) }
            }
        }
    }

    @Test
    fun timesOutWhenControllerDoesNotRespond() {
        assertThrows(TimeoutCancellationException::class.java) {
            runTest {
                MatterDeviceUnpairer(timeoutMillis = 1).unpair(7) { _: UnpairDeviceCallback -> }
            }
        }
    }
}
