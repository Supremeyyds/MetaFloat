package com.metafloat.app.overlay

import kotlin.math.pow
import kotlin.math.roundToLong

internal class TrafficReconnectPolicy(
    private val baseDelayMillis: Long = 1_000L,
    private val multiplier: Double = 1.5,
    private val maximumDelayMillis: Long = 30_000L,
    val frameTimeoutMillis: Long = 10_000L,
) {
    var consecutiveFailureCount: Int = 0
        private set

    fun onValidFrame() {
        consecutiveFailureCount = 0
    }

    fun onAttemptFailed(): TrafficRetryDecision {
        val exponentialDelayMillis =
            baseDelayMillis * multiplier.pow(consecutiveFailureCount.toDouble())
        val delayMillis = exponentialDelayMillis.roundToLong().coerceAtMost(maximumDelayMillis)
        if (delayMillis < maximumDelayMillis) {
            consecutiveFailureCount += 1
        }
        return TrafficRetryDecision(
            delayMillis = delayMillis,
            displayState = if (delayMillis >= maximumDelayMillis) {
                TrafficRetryDisplayState.DISCONNECTED
            } else {
                TrafficRetryDisplayState.RECONNECTING
            },
        )
    }

    fun hasFrameTimedOut(lastValidFrameAtMillis: Long, nowMillis: Long): Boolean {
        return nowMillis - lastValidFrameAtMillis >= frameTimeoutMillis
    }
}

internal data class TrafficRetryDecision(
    val delayMillis: Long,
    val displayState: TrafficRetryDisplayState,
)

internal enum class TrafficRetryDisplayState {
    RECONNECTING,
    DISCONNECTED,
}
