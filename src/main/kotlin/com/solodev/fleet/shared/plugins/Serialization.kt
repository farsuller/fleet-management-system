package com.solodev.fleet.shared.plugins

import com.solodev.fleet.shared.infrastructure.serialization.InstantSerializer
import com.solodev.fleet.shared.infrastructure.serialization.UUIDSerializer
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.Instant
import java.util.UUID

/**
 * Configures content negotiation and serialization.
 *
 * Installs the `ContentNegotiation` plugin and registers the JSON serializer
 * (kotlinx.serialization).
 * - `prettyPrint`: Enables formatted JSON output for easier debugging.
 * - `ignoreUnknownKeys`: Prevents crashes when parsing JSON with extra fields.
 */
fun Application.configureSerialization() {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true

        serializersModule = SerializersModule {
            contextual(UUID::class) { UUIDSerializer }
            contextual(Instant::class) { InstantSerializer }
        }
    }

    install(ContentNegotiation) {
        json(json)
    }
}
