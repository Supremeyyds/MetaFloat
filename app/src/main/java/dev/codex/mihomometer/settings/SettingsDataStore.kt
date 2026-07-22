package dev.codex.mihomometer.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.settingsDataStore by preferencesDataStore(name = "settings")
