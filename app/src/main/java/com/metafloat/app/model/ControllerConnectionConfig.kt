package com.metafloat.app.model

import okhttp3.HttpUrl
import java.net.URLEncoder

data class ControllerConnectionConfig(
    val protocol: ControllerProtocol = ControllerProtocol.HTTP,
    val host: String = "127.0.0.1",
    val port: Int = 9090,
    val secondaryPath: String = "",
    val secret: String = "",
    val label: String = "",
) {
    fun normalizedSecondaryPath(): String {
        return secondaryPath.trim().trim('/')
    }

    fun baseHttpUrl(): HttpUrl {
        val builder = HttpUrl.Builder()
            .scheme(protocol.scheme)
            .host(host.trim())
            .port(port)

        normalizedSecondaryPath()
            .takeIf { it.isNotEmpty() }
            ?.split('/')
            ?.filter { it.isNotBlank() }
            ?.forEach(builder::addPathSegment)

        return builder.build()
    }

    fun endpointHttpUrl(endpoint: String): HttpUrl {
        val normalized = endpoint.trim().trim('/')
        val builder = baseHttpUrl().newBuilder()
        if (normalized.isNotEmpty()) {
            normalized.split('/').filter { it.isNotBlank() }.forEach(builder::addPathSegment)
        }
        return builder.build()
    }

    fun websocketUrl(endpoint: String): String {
        val httpUrl = endpointHttpUrl(endpoint)
        return when (httpUrl.scheme) {
            "https" -> httpUrl.toString().replaceFirst("https://", "wss://")
            else -> httpUrl.toString().replaceFirst("http://", "ws://")
        }
    }

    fun dashboardUrl(): String {
        val protocolFlag = if (protocol == ControllerProtocol.HTTPS) "https" else "http"
        val query = buildString {
            append("type=clash")
            append("&").append(protocolFlag).append("=1")
            append("&host=").append(encoded(host.trim()))
            append("&hostname=").append(encoded(host.trim()))
            append("&port=").append(port)
            if (normalizedSecondaryPath().isNotEmpty()) {
                append("&secondaryPath=").append(encoded(normalizedSecondaryPath()))
            }
            if (secret.isNotBlank()) {
                append("&secret=").append(encoded(secret.trim()))
            }
            if (label.isNotBlank()) {
                append("&label=").append(encoded(label.trim()))
            }
        }
        return "http://appassets.androidplatform.net/zashboard/index.html?$query#/proxies"
    }

    private fun encoded(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
