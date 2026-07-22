package dev.codex.mihomometer.mihomo

import dev.codex.mihomometer.model.ControllerConnectionConfig
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class MihomoApiClientTest {
    @Test
    fun fetchVersion_injectsBearerSecret() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("mihomo v1"))
        server.start()
        try {
            val config = ControllerConnectionConfig(
                host = server.hostName,
                port = server.port,
                secret = "secret-value",
            )
            val client = MihomoApiClient(okhttp3.OkHttpClient())

            val result = client.fetchVersion(config)
            val request = server.takeRequest()

            assertTrue(result.isSuccess)
            assertEquals("/version", request.path)
            assertEquals("Bearer secret-value", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fetchVersion_omitsAuthorizationWhenSecretIsBlank() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("mihomo v1"))
        server.start()
        try {
            val config = ControllerConnectionConfig(
                host = server.hostName,
                port = server.port,
                secret = "",
            )
            val client = MihomoApiClient(okhttp3.OkHttpClient())

            client.fetchVersion(config)
            val request = server.takeRequest()

            assertEquals(null, request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fetchVersion_returnsFailureForHttpError() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.start()
        try {
            val client = MihomoApiClient(okhttp3.OkHttpClient())
            val result = client.fetchVersion(
                ControllerConnectionConfig(host = server.hostName, port = server.port),
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fetchVersion_cancellationCancelsUnderlyingHttpCall() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeadersDelay(5, TimeUnit.SECONDS))
        server.start()
        try {
            val okHttpClient = okhttp3.OkHttpClient()
            val client = MihomoApiClient(okHttpClient)
            val job = launch {
                client.fetchVersion(
                    ControllerConnectionConfig(host = server.hostName, port = server.port),
                )
            }
            server.takeRequest(2, TimeUnit.SECONDS)

            job.cancelAndJoin()
            withTimeout(2_000) {
                while (okHttpClient.dispatcher.runningCallsCount() != 0) {
                    kotlinx.coroutines.delay(10)
                }
            }

            assertTrue(job.isCancelled)
            assertEquals(0, okHttpClient.dispatcher.runningCallsCount())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun observeTraffic_parsesWebSocketSamples() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(
                            """{"up":1024,"down":2048,"upTotal":4096,"downTotal":8192}""",
                        )
                    }
                },
            ),
        )
        server.start()
        try {
            val config = ControllerConnectionConfig(
                host = server.hostName,
                port = server.port,
            )
            val client = MihomoApiClient(okhttp3.OkHttpClient())

            val sample = client.observeTraffic(config).first()
            val request = server.takeRequest()

            assertEquals("/traffic", request.path)
            assertEquals(1024L, sample.upBytesPerSecond)
            assertEquals(2048L, sample.downBytesPerSecond)
            assertEquals(4096L, sample.upTotalBytes)
            assertEquals(8192L, sample.downTotalBytes)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun observeTraffic_malformedJsonCanReconnectToNextWebSocket() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("not-json")
                    }
                },
            ),
        )
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(
                            """{"up":7,"down":8,"upTotal":9,"downTotal":10}""",
                        )
                    }
                },
            ),
        )
        server.start()
        try {
            val client = MihomoApiClient(okhttp3.OkHttpClient())
            val config = ControllerConnectionConfig(host = server.hostName, port = server.port)

            val sample = withTimeout(5_000) {
                client.observeTraffic(config).retry(1).first()
            }

            assertEquals(7L, sample.upBytesPerSecond)
            assertEquals(8L, sample.downBytesPerSecond)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
