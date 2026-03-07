package com.solodev.fleet.modules.tracking.infrastructure.websocket

import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import java.util.UUID

fun Route.configureWebSocketRoutes(broadcaster: RedisDeltaBroadcaster) {
    webSocket("/v1/fleet/live") {
        val sessionId = UUID.randomUUID().toString()
        broadcaster.addSession(sessionId, this)

        try {
            // Initial state is sent by broadcaster.addSession()

            // Handle incoming frames (heartbeat)
            for (frame in incoming) {
                when (frame) {
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    is Frame.Text -> {
                        // Handle client messages if needed
                    }
                    else -> {}
                }
            }
        } finally {
            broadcaster.removeSession(sessionId)
        }
    }
}