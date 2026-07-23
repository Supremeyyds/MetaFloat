package com.metafloat.app.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun `explicit light mode ignores a dark system theme`() {
        assertFalse(AppThemeMode.LIGHT.usesDarkTheme(systemInDarkTheme = true))
    }

    @Test
    fun `explicit dark mode ignores a light system theme`() {
        assertTrue(AppThemeMode.DARK.usesDarkTheme(systemInDarkTheme = false))
    }

    @Test
    fun `system mode follows the current system theme`() {
        assertFalse(AppThemeMode.SYSTEM.usesDarkTheme(systemInDarkTheme = false))
        assertTrue(AppThemeMode.SYSTEM.usesDarkTheme(systemInDarkTheme = true))
    }
}
