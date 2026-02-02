package com.solodev.fleet

import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Configures the application's routing.
 *
 * Defines the HTTP endpoints exposed by the application. Currently includes:
 * - Root path ("/"): Phase 1 verification endpoint.
 * - Health check ("/health"): Liveness probe for container orchestration.
 */
fun Application.configureRouting() {
        routing {
                get("/") {
                        call.respond(
                                ApiResponse(
                                        success = true,
                                        data =
                                                mapOf(
                                                        "message" to "Phase 1 setup is done",
                                                        "version" to "0.0.1",
                                                        "architecture" to
                                                                "Modular Monolith with Clean Architecture"
                                                ),
                                        requestId = call.requestId
                                )
                        )
                }

                get("/health") {
                        call.respond(
                                ApiResponse(
                                        success = true,
                                        data = mapOf("status" to "OK"),
                                        requestId = call.requestId
                                )
                        )
                }
        }
}
