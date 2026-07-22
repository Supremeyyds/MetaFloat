package dev.codex.mihomometer.model

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Reconnecting : ConnectionState
    data class Connected(val version: String? = null) : ConnectionState
    data class Disconnected(val reason: UiText) : ConnectionState
    data class Failed(val reason: UiText) : ConnectionState
}
