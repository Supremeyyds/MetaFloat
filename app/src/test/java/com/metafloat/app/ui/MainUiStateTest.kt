package com.metafloat.app.ui

import com.metafloat.app.dashboard.DashboardDownloadMirror
import org.junit.Assert.assertEquals
import org.junit.Test

class MainUiStateTest {
    @Test
    fun defaultMirror_isDirectGithub() {
        assertEquals(DashboardDownloadMirror.DIRECT_GITHUB, MainUiState().downloadMirror)
    }
}
