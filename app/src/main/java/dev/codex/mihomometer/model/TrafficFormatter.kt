package dev.codex.mihomometer.model

import java.util.Locale
import kotlin.math.abs

object TrafficFormatter {
    private val rateUnits = arrayOf("B", "KB", "MB", "GB", "TB")
    private val totalUnits = arrayOf("B", "KB", "MB", "GB", "TB")

    fun formatBytesPerSecondParts(value: Long): FormattedTraffic {
        return formatParts(value, rateUnits)
    }

    fun formatBytesPerSecond(value: Long): String {
        val parts = formatBytesPerSecondParts(value)
        return "${parts.number} ${parts.unit}/s"
    }

    fun formatBytesParts(value: Long): FormattedTraffic {
        return formatParts(value, totalUnits)
    }

    fun formatBytes(value: Long): String {
        val parts = formatBytesParts(value)
        return "${parts.number} ${parts.unit}"
    }

    private fun formatParts(value: Long, labels: Array<String>): FormattedTraffic {
        if (value == 0L) {
            return FormattedTraffic("0", labels[0])
        }

        val negative = value < 0
        var remaining = abs(value).toDouble()
        var index = 0
        while (remaining >= 1024.0 && index < labels.lastIndex) {
            remaining /= 1024.0
            index += 1
        }

        val formatted = if (remaining >= 10 || index == 0) {
            String.format(Locale.US, "%.0f", remaining)
        } else {
            String.format(Locale.US, "%.1f", remaining)
        }
        val number = buildString {
            if (negative) append('-')
            append(formatted)
        }
        return FormattedTraffic(number, labels[index])
    }
}

data class FormattedTraffic(
    val number: String,
    val unit: String,
)
