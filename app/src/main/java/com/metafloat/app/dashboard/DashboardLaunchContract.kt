package com.metafloat.app.dashboard

import android.content.Intent
import com.metafloat.app.settings.AppThemeMode

internal data class DashboardLaunchConfig(
    val dashboardUrl: String = DashboardLaunchContract.DEFAULT_DASHBOARD_URL,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
)

internal object DashboardLaunchContract {
    private const val EXTRA_DASHBOARD_URL = "dashboard_url"
    private const val EXTRA_THEME_MODE = "theme_mode"
    const val DEFAULT_DASHBOARD_URL =
        "http://appassets.androidplatform.net/zashboard/index.html"

    fun fromIntent(intent: Intent): DashboardLaunchConfig {
        return DashboardLaunchConfig(
            dashboardUrl = intent.getStringExtra(EXTRA_DASHBOARD_URL)
                ?: DEFAULT_DASHBOARD_URL,
            themeMode = AppThemeMode.fromStorageValue(
                intent.getStringExtra(EXTRA_THEME_MODE),
            ),
        )
    }

    fun putInto(
        intent: Intent,
        dashboardUrl: String,
        themeMode: AppThemeMode,
    ): Intent {
        return intent
            .putExtra(EXTRA_DASHBOARD_URL, dashboardUrl)
            .putExtra(EXTRA_THEME_MODE, themeMode.name)
    }
}
