package dev.codex.mihomometer.model

import androidx.annotation.StringRes

sealed interface UiText {
    data class Resource(
        @StringRes val resourceId: Int,
        val arguments: List<Any> = emptyList(),
    ) : UiText

    data class Dynamic(val value: String) : UiText
}
