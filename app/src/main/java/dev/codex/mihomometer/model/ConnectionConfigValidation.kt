package dev.codex.mihomometer.model

enum class ConnectionConfigError {
    HOST_REQUIRED,
    HOST_INVALID,
    PORT_INVALID,
    SECONDARY_PATH_INVALID,
}

fun ControllerConnectionConfig.validate(portText: String): ConnectionConfigError? {
    val normalizedHost = host.trim()
    if (normalizedHost.isEmpty()) {
        return ConnectionConfigError.HOST_REQUIRED
    }

    val parsedPort = portText.toIntOrNull()
        ?.takeIf { it in MIN_PORT..MAX_PORT }
        ?: return ConnectionConfigError.PORT_INVALID

    if (secondaryPath.contains('?') || secondaryPath.contains('#')) {
        return ConnectionConfigError.SECONDARY_PATH_INVALID
    }

    return runCatching {
        copy(host = normalizedHost, port = parsedPort).baseHttpUrl()
    }.fold(
        onSuccess = { null },
        onFailure = { ConnectionConfigError.HOST_INVALID },
    )
}

private const val MIN_PORT = 1
private const val MAX_PORT = 65_535
