package dev.codex.mihomometer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficFormatterTest {
    @Test
    fun formatBytesPerSecond_usesExpectedUnits() {
        assertEquals("0 B/s", TrafficFormatter.formatBytesPerSecond(0))
        assertEquals("512 B/s", TrafficFormatter.formatBytesPerSecond(512))
        assertEquals("1.5 KB/s", TrafficFormatter.formatBytesPerSecond(1536))
        assertEquals("2.0 MB/s", TrafficFormatter.formatBytesPerSecond(2L * 1024L * 1024L))
        assertEquals("3.0 GB/s", TrafficFormatter.formatBytesPerSecond(3L * 1024L * 1024L * 1024L))
    }

    @Test
    fun formatBytesPerSecondParts_separatesNumberAndUnitForAlignedOverlay() {
        assertEquals(FormattedTraffic("1.5", "KB"), TrafficFormatter.formatBytesPerSecondParts(1536))
        assertEquals(FormattedTraffic("0", "B"), TrafficFormatter.formatBytesPerSecondParts(0))
    }

    @Test
    fun formatBytes_usesExpectedUnits() {
        assertEquals("768 B", TrafficFormatter.formatBytes(768))
        assertEquals("1.0 KB", TrafficFormatter.formatBytes(1024))
        assertEquals("2.5 MB", TrafficFormatter.formatBytes(2_621_440))
    }

    @Test
    fun formatBytesParts_separatesNumberAndUnitForAlignedTotals() {
        assertEquals(
            FormattedTraffic("8.5", "MB"),
            TrafficFormatter.formatBytesParts(8L * 1024L * 1024L + 512L * 1024L),
        )
        assertEquals(FormattedTraffic("318", "MB"), TrafficFormatter.formatBytesParts(333_447_168))
    }
}
