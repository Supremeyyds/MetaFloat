package dev.codex.mihomometer.model

data class TrafficSample(
    val upBytesPerSecond: Long,
    val downBytesPerSecond: Long,
    val upTotalBytes: Long,
    val downTotalBytes: Long,
    val timestampMillis: Long,
)
