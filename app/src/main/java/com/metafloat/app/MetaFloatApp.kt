package com.metafloat.app

import android.app.Application

class MetaFloatApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
