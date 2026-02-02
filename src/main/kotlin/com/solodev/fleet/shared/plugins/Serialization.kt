package com.solodev.fleet.shared.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

/**
 * Configures content negotiation and serialization.
 *
 * Installs the `ContentNegotiation` plugin and registers the JSON serializer
 * (kotlinx.serialization).
 * - `prettyPrint`: Enables formatted JSON output for easier debugging.
 * - `ignoreUnknownKeys`: Prevents crashes when parsing JSON with extra fields.
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
        )
    }
}
