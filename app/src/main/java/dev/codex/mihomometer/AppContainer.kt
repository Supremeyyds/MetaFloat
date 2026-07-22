package dev.codex.mihomometer

import android.content.Context
import dev.codex.mihomometer.dashboard.DashboardInstaller
import dev.codex.mihomometer.mihomo.MihomoApiClient
import dev.codex.mihomometer.settings.SettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)
    val mihomoApiClient: MihomoApiClient = MihomoApiClient(httpClient)
    val dashboardInstaller: DashboardInstaller = DashboardInstaller(appContext, httpClient)
}
