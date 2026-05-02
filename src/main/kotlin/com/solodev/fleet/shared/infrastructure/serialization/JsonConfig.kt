package com.solodev.fleet.shared.infrastructure.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.Instant
import java.util.UUID

object JsonConfig {
    /** High-performance instance for API responses. Omits defaults to save bandwidth and allocations. */
    val instance: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            serializersModule =
                SerializersModule {
                    contextual(UUID::class) { UUIDSerializer }
                    contextual(Instant::class) { InstantSerializer }
                }
        }

    /** Preconfigured instance for debugging/logging with pretty printing. */
    val prettyInstance: Json =
        Json(instance) {
            prettyPrint = true
        }
}
