package com.metafloat.app.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardSheetLayoutPolicyTest {
    @Test
    fun `uses a taller layout on a regular portrait phone`() {
        assertEquals(
            686.25f,
            DashboardSheetLayoutPolicy.targetHeightDp(widthDp = 412f, heightDp = 915f),
            0.01f,
        )
    }

    @Test
    fun `keeps enough content height in landscape`() {
        assertEquals(
            362.56f,
            DashboardSheetLayoutPolicy.targetHeightDp(widthDp = 915f, heightDp = 412f),
            0.01f,
        )
    }

    @Test
    fun `caps the sheet height on a large display`() {
        assertEquals(
            900f,
            DashboardSheetLayoutPolicy.targetHeightDp(widthDp = 800f, heightDp = 1_280f),
            0.01f,
        )
    }

    @Test
    fun `allows the handle to resize a regular phone sheet`() {
        val heightRange = DashboardSheetLayoutPolicy.draggableHeightRangeDp(heightDp = 915f)

        assertEquals(
            360f,
            heightRange.start,
            0.01f,
        )
        assertEquals(
            841.8f,
            heightRange.endInclusive,
            0.01f,
        )
    }

    @Test
    fun `dismisses after a regular phone sheet is dragged below fifty five percent`() {
        assertEquals(
            503.25f,
            DashboardSheetLayoutPolicy.dismissThresholdDp(heightDp = 915f),
            0.01f,
        )
    }
}
