package com.remotedroid.server

import android.content.res.AssetManager
import com.remotedroid.input.CommandExecutor
import com.remotedroid.protocol.decodeClient
import com.remotedroid.protocol.encodeServer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.io.IOException

/**
 * Embedded Ktor CIO server that serves the PWA (assets/web) statically and opens a
 * token-authenticated command channel at /ws. Forwards commands to [CommandExecutor].
 */
class RemoteServer(
    private val port: Int,
    private val token: String,
    private val assets: AssetManager,
    private val exec: CommandExecutor,
) {
    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        if (server != null) return
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets)
            routing {
                webSocket("/ws") {
                    val provided = call.request.queryParameters["t"]
                    if (!Auth.isValid(token, provided)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                        return@webSocket
                    }
                    send(Frame.Text(encodeServer(exec.welcome())))
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val msg = runCatching { decodeClient(frame.readText()) }.getOrNull() ?: continue
                            exec.execute(msg)
                        }
                    }
                }
                get("/{path...}") {
                    val raw = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    val assetPath = "web/" + AssetContent.normalize(raw)
                    try {
                        val bytes = assets.open(assetPath).use { it.readBytes() }
                        call.respondBytes(bytes, ContentType.parse(AssetContent.mimeFor(assetPath)))
                    } catch (e: IOException) {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    fun stop() {
        server?.stop(500, 1000)
        server = null
    }
}
