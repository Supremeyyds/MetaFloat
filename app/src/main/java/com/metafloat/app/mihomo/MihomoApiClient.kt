package com.metafloat.app.mihomo

import com.metafloat.app.model.ControllerConnectionConfig
import com.metafloat.app.model.TrafficSample
import com.metafloat.app.network.awaitResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException

class MihomoApiClient(
    private val client: OkHttpClient,
) {
    suspend fun fetchVersion(config: ControllerConnectionConfig): Result<String> {
        return try {
            val request = requestBuilder(config, config.endpointHttpUrl("version").toString()).build()
            val response = client.newCall(request).awaitResponse()
            response.use { body ->
                if (!body.isSuccessful) {
                    throw IOException("HTTP ${body.code}")
                }
                Result.success(body.body?.string()?.trim().orEmpty().toVersionLabel())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    fun observeTraffic(config: ControllerConnectionConfig): Flow<TrafficSample> = callbackFlow {
        val request = requestBuilder(config, config.websocketUrl("traffic"))
            .build()
        var socket: WebSocket? = null

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // The server keeps pushing JSON snapshots.
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    trySend(
                        TrafficSample(
                            upBytesPerSecond = json.optLong("up"),
                            downBytesPerSecond = json.optLong("down"),
                            upTotalBytes = json.optLong("upTotal"),
                            downTotalBytes = json.optLong("downTotal"),
                            timestampMillis = System.currentTimeMillis(),
                        ),
                    )
                } catch (throwable: Throwable) {
                    close(IOException("Invalid traffic payload", throwable))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close(IOException("WebSocket closed: $code $reason"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        })

        awaitClose {
            socket?.cancel()
        }
    }

    private fun requestBuilder(config: ControllerConnectionConfig, url: String): Request.Builder {
        val request = Request.Builder().url(url)
        if (config.secret.isNotBlank()) {
            request.header("Authorization", "Bearer ${config.secret.trim()}")
        }
        return request
    }

    private fun String.toVersionLabel(): String {
        if (isBlank()) {
            return ""
        }
        return runCatching {
            JSONObject(this).optString("version")
        }.getOrElse { this }
    }
}
