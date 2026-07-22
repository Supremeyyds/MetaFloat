package dev.codex.mihomometer

import android.app.Application

class MihomoMeterApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
