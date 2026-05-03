package com.solodev.fleet.shared.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

/**
 * Configures content negotiation and serialization.
 *
 * Installs the `ContentNegotiation` plugin and registers the JSON serializer
 * (kotlinx.serialization).
 * - `prettyPrint`: Enables formatted JSON output for easier debugging.
 * - `ignoreUnknownKeys`: Prevents crashes when parsing JSON with extra fields.
 */
fun Application.configureSerialization() {
    val json = com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance

    install(ContentNegotiation) {
        json(json)
    }
}
