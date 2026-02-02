package com.example.shared.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import java.util.*

/** Request ID attribute key for storing correlation IDs. */
val RequestIdKey = AttributeKey<String>("RequestId")

/**
 * Configures request ID tracking for observability.
 *
 * - Extracts `X-Request-ID` header if present
 * - Generates a new UUID if not provided
 * - Stores in call attributes for access throughout request lifecycle
 * - Propagates to logs and responses
 */
fun Application.configureRequestId() {
    intercept(ApplicationCallPipeline.Setup) {
        val requestId = call.request.header("X-Request-ID") ?: generateRequestId()
        call.attributes.put(RequestIdKey, requestId)
    }
}

/** Generates a unique request ID. */
fun generateRequestId(): String = "req_${UUID.randomUUID()}"

/** Extension to retrieve request ID from call. */
val ApplicationCall.requestId: String
    get() = attributes.getOrNull(RequestIdKey) ?: generateRequestId()
