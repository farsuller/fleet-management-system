package com.example

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Configures the application's routing.
 *
 * Defines the HTTP endpoints exposed by the application. Currently includes:
 * - Root path ("/"): Verified setup message.
 * - Health check ("/health"): Liveness probe returns "OK".
 */
fun Application.configureRouting() {
    routing {
        get("/") { call.respondText("Phase 1 setup is done") }
        get("/health") { call.respondText("OK") }
    }
}
