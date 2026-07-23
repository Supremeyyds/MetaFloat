package com.metafloat.app.dashboard

internal object DashboardSheetLayoutPolicy {
    private const val PHONE_PORTRAIT_FRACTION = 0.75f
    private const val COMPACT_PORTRAIT_FRACTION = 0.86f
    private const val LANDSCAPE_FRACTION = 0.88f
    private const val LARGE_SCREEN_FRACTION = 0.72f
    private const val COMPACT_HEIGHT_DP = 600f
    private const val LARGE_SCREEN_WIDTH_DP = 600f
    private const val MIN_HEIGHT_DP = 360f
    private const val MAX_HEIGHT_DP = 900f
    private const val MAX_SCREEN_FRACTION = 0.92f
    private const val DISMISS_SCREEN_FRACTION = 0.55f

    fun targetHeightDp(widthDp: Float, heightDp: Float): Float {
        if (widthDp <= 0f || heightDp <= 0f) {
            return 0f
        }

        val preferredFraction = when {
            widthDp > heightDp -> LANDSCAPE_FRACTION
            heightDp < COMPACT_HEIGHT_DP -> COMPACT_PORTRAIT_FRACTION
            widthDp >= LARGE_SCREEN_WIDTH_DP -> LARGE_SCREEN_FRACTION
            else -> PHONE_PORTRAIT_FRACTION
        }
        val heightRange = draggableHeightRangeDp(heightDp)
        return (heightDp * preferredFraction).coerceIn(heightRange)
    }

    fun draggableHeightRangeDp(heightDp: Float): ClosedFloatingPointRange<Float> {
        if (heightDp <= 0f) {
            return 0f..0f
        }

        val maximumHeight = minOf(heightDp * MAX_SCREEN_FRACTION, MAX_HEIGHT_DP)
        val minimumHeight = minOf(MIN_HEIGHT_DP, maximumHeight)
        return minimumHeight..maximumHeight
    }

    fun dismissThresholdDp(heightDp: Float): Float {
        val heightRange = draggableHeightRangeDp(heightDp)
        return (heightDp * DISMISS_SCREEN_FRACTION).coerceIn(heightRange)
    }
}
