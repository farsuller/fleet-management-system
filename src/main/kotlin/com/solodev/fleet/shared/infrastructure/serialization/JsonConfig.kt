package com.solodev.fleet.shared.infrastructure.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.Instant
import java.util.UUID

object JsonConfig {
    val instance: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            serializersModule =
                SerializersModule {
                    contextual(UUID::class) { UUIDSerializer }
                    contextual(Instant::class) { InstantSerializer }
                }
        }
}
