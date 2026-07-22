package com.metafloat.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerConnectionConfigTest {
    @Test
    fun endpointHttpUrl_withoutSecondaryPath() {
        val config = ControllerConnectionConfig()

        assertEquals(
            "http://127.0.0.1:9090/version",
            config.endpointHttpUrl("version").toString(),
        )
    }

    @Test
    fun endpointHttpUrl_withSecondaryPath() {
        val config = ControllerConnectionConfig(
            protocol = ControllerProtocol.HTTPS,
            host = "example.com",
            port = 9443,
            secondaryPath = "/mihomo/api/",
        )

        assertEquals(
            "https://example.com:9443/mihomo/api/traffic",
            config.endpointHttpUrl("/traffic").toString(),
        )
    }

    @Test
    fun websocketUrl_usesWsSchemeForHttp() {
        val config = ControllerConnectionConfig(protocol = ControllerProtocol.HTTP)

        assertEquals(
            "ws://127.0.0.1:9090/traffic",
            config.websocketUrl("traffic"),
        )
    }

    @Test
    fun websocketUrl_usesWssSchemeForHttps() {
        val config = ControllerConnectionConfig(protocol = ControllerProtocol.HTTPS)

        assertEquals(
            "wss://127.0.0.1:9090/traffic",
            config.websocketUrl("traffic"),
        )
    }

    @Test
    fun dashboardUrl_containsZashboardLoginParameters() {
        val config = ControllerConnectionConfig(
            host = "example.com",
            secondaryPath = "api/v1",
            secret = "a b&c",
            label = "home",
        )

        assertEquals(
            "http://appassets.androidplatform.net/zashboard/index.html" +
                "?type=clash&http=1&host=example.com&hostname=example.com&port=9090" +
                "&secondaryPath=api%2Fv1&secret=a+b%26c&label=home#/proxies",
            config.dashboardUrl(),
        )
    }
}
