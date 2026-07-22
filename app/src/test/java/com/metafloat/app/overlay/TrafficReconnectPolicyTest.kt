package com.metafloat.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficReconnectPolicyTest {
    @Test
    fun failures_useOnePointFiveBackoffAndCapAtThirtySeconds() {
        val policy = TrafficReconnectPolicy()

        val decisions = List(10) { policy.onAttemptFailed() }

        assertEquals(
            listOf(1_000L, 1_500L, 2_250L, 3_375L, 5_063L, 7_594L, 11_391L, 17_086L, 25_629L, 30_000L),
            decisions.map { it.delayMillis },
        )
        assertTrue(
            decisions.dropLast(1).all {
                it.displayState == TrafficRetryDisplayState.RECONNECTING
            },
        )
        assertEquals(
            TrafficRetryDisplayState.DISCONNECTED,
            decisions.last().displayState,
        )
        assertEquals(30_000L, policy.onAttemptFailed().delayMillis)
    }

    @Test
    fun validFrame_resetsNextRetryToOneSecond() {
        val policy = TrafficReconnectPolicy()
        repeat(5) { policy.onAttemptFailed() }

        policy.onValidFrame()
        val retry = policy.onAttemptFailed()

        assertEquals(1_000L, retry.delayMillis)
        assertEquals(TrafficRetryDisplayState.RECONNECTING, retry.displayState)
    }

    @Test
    fun frameTimeout_startsAtTenSecondsWithoutAValidFrame() {
        val policy = TrafficReconnectPolicy()

        assertFalse(policy.hasFrameTimedOut(5_000L, 14_999L))
        assertTrue(policy.hasFrameTimedOut(5_000L, 15_000L))
    }

    @Test
    fun disconnectedPolicy_canRecoverAndStartBackoffFromBeginning() {
        val policy = TrafficReconnectPolicy()
        var retry = policy.onAttemptFailed()
        while (retry.displayState != TrafficRetryDisplayState.DISCONNECTED) {
            retry = policy.onAttemptFailed()
        }

        policy.onValidFrame()
        val retryAfterRecovery = policy.onAttemptFailed()

        assertEquals(1_000L, retryAfterRecovery.delayMillis)
        assertEquals(
            TrafficRetryDisplayState.RECONNECTING,
            retryAfterRecovery.displayState,
        )
    }
}
