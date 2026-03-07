package com.solodev.fleet

import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehicleRepositoryImpl
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import com.solodev.fleet.shared.plugins.*
import com.solodev.fleet.shared.utils.JwtService
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import redis.clients.jedis.Jedis
import kotlin.time.Duration.Companion.seconds

/**
 * Entry point for the application. Starts the Netty engine using the configuration defined in
 * application.yaml.
 */
fun main(args: Array<String>) {
    EngineMain.main(args)
}

/**
 * Main application module.
 *
 * This extension function acts as the central configuration hub for the Ktor application. It
 * coordinates the setup of all major subsystems including observability, serialization, error
 * handling (status pages), database connections, security, and routing.
 *
 * Order matters: RequestId must be configured early so it's available in error handlers.
 */
fun Application.module() {
    configureRequestId()
    configureObservability()
    configureSerialization()
    configureStatusPages()
    configureDatabases()
    configureSecurity()
    configureRateLimiting()

    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    var jedis: Jedis? = null
    val redisEnabled = environment.config.propertyOrNull("redis.enabled")?.getString()?.toBoolean() ?: true
    val cacheManager = if (redisEnabled) {
        try {
            val redisUrl = environment.config.propertyOrNull("redis.url")?.getString()
                ?: "redis://localhost:6379"
            jedis = Jedis(redisUrl)
            RedisCacheManager(jedis)
        } catch (e: Exception) {
            log.error("Failed to initialize Redis cache, falling back to no-cache mode", e)
            null
        }
    } else {
        null
    }

    // Initialize Micrometer registry
    val registry: MeterRegistry = SimpleMeterRegistry()
    val vehicleRepository = VehicleRepositoryImpl(cacheManager)

    val secret = environment.config.propertyOrNull("jwt.secret")?.getString()
        ?: "change-me-in-production-use-env-var-min-64-chars"
    val issuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "http://0.0.0.0:8080/"
    val audience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "http://0.0.0.0:8080/"
    val expiresIn = environment.config.propertyOrNull("jwt.expiresIn")?.getString()?.toLong() ?: 3600000L

    val jwtService = JwtService(secret, issuer, audience, expiresIn)


    configureRouting(
        jwtService = jwtService,
        vehicleRepo = vehicleRepository,
        jedis = jedis,
        registry = registry
    )
}
