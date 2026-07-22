package com.metafloat.app.overlay

internal object OverlayPositionPolicy {
    fun clamp(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        overlayWidth: Int,
        overlayHeight: Int,
    ): Pair<Int, Int> {
        val maxX = (screenWidth - overlayWidth).coerceAtLeast(0)
        val maxY = (screenHeight - overlayHeight).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }
}
