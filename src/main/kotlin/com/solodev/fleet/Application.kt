package com.solodev.fleet

import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehicleRepositoryImpl
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import com.solodev.fleet.shared.plugins.*
import com.solodev.fleet.shared.utils.JwtService
import io.ktor.server.application.*
import io.ktor.server.netty.*
import redis.clients.jedis.Jedis

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

    val jedis = Jedis("localhost", 6379)
    val cacheManager = RedisCacheManager(jedis)

    val vehicleRepository = VehicleRepositoryImpl(cacheManager)

    val secret = environment.config.propertyOrNull("jwt.secret")?.getString()
        ?: "change-me-in-production-use-env-var-min-64-chars"
    val issuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "http://0.0.0.0:8080/"
    val audience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "http://0.0.0.0:8080/"
    val expiresIn = environment.config.propertyOrNull("jwt.expiresIn")?.getString()?.toLong() ?: 3600000L

    val jwtService = JwtService(secret, issuer, audience, expiresIn)

    configureRouting(jwtService, vehicleRepository)
}
