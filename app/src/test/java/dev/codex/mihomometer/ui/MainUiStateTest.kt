package dev.codex.mihomometer.ui

import dev.codex.mihomometer.dashboard.DashboardDownloadMirror
import org.junit.Assert.assertEquals
import org.junit.Test

class MainUiStateTest {
    @Test
    fun defaultMirror_isDirectGithub() {
        assertEquals(DashboardDownloadMirror.DIRECT_GITHUB, MainUiState().downloadMirror)
    }
}
