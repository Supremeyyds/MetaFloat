package com.metafloat.app.model

enum class ControllerProtocol(val scheme: String) {
    HTTP("http"),
    HTTPS("https");

    companion object {
        fun fromScheme(value: String): ControllerProtocol {
            return entries.firstOrNull { it.scheme.equals(value, ignoreCase = true) } ?: HTTP
        }
    }
}
