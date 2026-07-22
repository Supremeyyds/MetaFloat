package com.metafloat.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionPolicyTest {
    @Test
    fun clamp_movesNegativeCoordinatesToOrigin() {
        assertEquals(
            0 to 0,
            OverlayPositionPolicy.clamp(-20, -30, 1080, 1920, 240, 120),
        )
    }

    @Test
    fun clamp_keepsOverlayInsideBottomRightEdge() {
        assertEquals(
            840 to 1800,
            OverlayPositionPolicy.clamp(1000, 1900, 1080, 1920, 240, 120),
        )
    }

    @Test
    fun clamp_repositionsLandscapeCoordinatesAfterPortraitRotation() {
        assertEquals(
            840 to 960,
            OverlayPositionPolicy.clamp(1680, 960, 1080, 1920, 240, 120),
        )
    }

    @Test
    fun clamp_repositionsAfterOverlayExpands() {
        assertEquals(
            700 to 1600,
            OverlayPositionPolicy.clamp(820, 1780, 1080, 1920, 380, 320),
        )
    }

    @Test
    fun clamp_usesOriginWhenOverlayIsLargerThanScreen() {
        assertEquals(
            0 to 0,
            OverlayPositionPolicy.clamp(100, 100, 320, 480, 640, 960),
        )
    }
}
