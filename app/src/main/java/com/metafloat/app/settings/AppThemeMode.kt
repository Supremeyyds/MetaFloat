package com.metafloat.app.settings

import androidx.annotation.StringRes
import com.metafloat.app.R

enum class AppThemeMode(@StringRes val labelResource: Int) {
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    SYSTEM(R.string.theme_system);

    fun usesDarkTheme(systemInDarkTheme: Boolean): Boolean {
        return when (this) {
            LIGHT -> false
            DARK -> true
            SYSTEM -> systemInDarkTheme
        }
    }

    companion object {
        fun fromStorageValue(value: String?): AppThemeMode {
            return entries.firstOrNull { it.name == value } ?: SYSTEM
        }
    }
}
