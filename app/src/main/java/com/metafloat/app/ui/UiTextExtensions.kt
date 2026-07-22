package com.metafloat.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metafloat.app.model.UiText

fun UiText.resolve(context: Context): String {
    return when (this) {
        is UiText.Dynamic -> value
        is UiText.Resource -> context.getString(resourceId, *arguments.toTypedArray())
    }
}

@Composable
fun UiText.resolve(): String {
    return when (this) {
        is UiText.Dynamic -> value
        is UiText.Resource -> stringResource(resourceId, *arguments.toTypedArray())
    }
}
