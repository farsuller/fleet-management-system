package com.example.shared.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable data class ErrorResponse(val error: String, val details: String? = null)

/**
 * Configures global error handling.
 *
 * Uses the `StatusPages` plugin to interception exceptions thrown during request processing and
 * returns appropriate HTTP responses.
 *
 * - Catches `Throwable` (all unchecked exceptions) and responds with 500 Internal Server Error.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = "Internal Server Error", details = cause.localizedMessage)
            )
        }

        // Add more specific exceptions here as we define them (e.g. DomainException)
    }
}
