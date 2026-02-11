package com.solodev.fleet.shared.plugins

import com.solodev.fleet.shared.infrastructure.persistence.IdempotencyRepositoryImpl
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.util.*

val IdempotencyKey = AttributeKey<String>("IdempotencyKey")

/**
 * Plugin that enforces idempotency.
 * If a key is seen again, it returns the cached response instead of re-running the logic.
 */
val Idempotency = createRouteScopedPlugin(name = "Idempotency", createConfiguration = ::IdempotencyConfig) {
    val repository = pluginConfig.repository
    val headerName = pluginConfig.headerName
    val expiresInMinutes = pluginConfig.expiresInMinutes

    onCall { call ->
        val key = call.request.headers[headerName] ?: return@onCall
        call.attributes.put(IdempotencyKey, key)

        val existing = repository.find(key)
        if (existing != null) {
            if (existing.status != null && existing.body != null) {
                // RETURN CACHED: Request finished previously, send same response back.
                call.respondText(existing.body, ContentType.Application.Json, HttpStatusCode.fromValue(existing.status))
            } else {
                // CONFLICT: Request is currently being processed by another thread/instance.
                call.respond(HttpStatusCode.Conflict, "Request with this idempotency key is already in progress.")
            }
        } else {
            // NEW: Record that we are starting to process this key.
            repository.create(
                key = key,
                path = call.request.uri,
                method = call.request.httpMethod.value,
                ttlMinutes =  expiresInMinutes)
        }
    }

    /**
     * Capture the outgoing response to cache it for the next time this key is used.
     * Note: In production, you would use a custom transformation or interceptor to ensure
     * the body is captured in its final JSON string format.
     */
    onCallRespond { call, message ->
        val key = call.attributes.getOrNull(IdempotencyKey) ?: return@onCallRespond
        val status = call.response.status()?.value ?: 200

        // Simplified body capture.
        // For production, ensure your serialization pipeline allows string capture.
        val body = when (message) {
            is String -> message
            is TextContent -> message.text
            else -> message.toString()
        }

        repository.updateResponse(key, status, body)
    }
}

/**
 * Configuration for the Idempotency plugin.
 */
class IdempotencyConfig {
    var repository: IdempotencyRepositoryImpl = IdempotencyRepositoryImpl()
    var headerName: String = "Idempotency-Key"
    var expiresInMinutes: Long = 60
}