package com.solodev.fleet

import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehicleRepositoryImpl
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import com.solodev.fleet.shared.plugins.configureDatabases
import com.solodev.fleet.shared.plugins.configureObservability
import com.solodev.fleet.shared.plugins.configureRateLimiting
import com.solodev.fleet.shared.plugins.configureRequestId
import com.solodev.fleet.shared.plugins.configureSecurity
import com.solodev.fleet.shared.plugins.configureSerialization
import com.solodev.fleet.shared.plugins.configureStatusPages
import com.solodev.fleet.shared.utils.JwtService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.net.URI
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
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
    val corsAllowedOrigins =
        environment.config
            .propertyOrNull("cors.allowedOrigins")
            ?.getString()
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?: emptyList()

    install(CORS) {
        corsAllowedOrigins.forEach { origin ->
            val uri = URI(origin)
            val host = buildString {
                append(uri.host)
                if (uri.port != -1) {
                    append(":")
                    append(uri.port)
                }
            }
            allowHost(host, schemes = listOf(uri.scheme))
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

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

    var jedisPool: JedisPool? = null
    val redisEnabled =
        environment.config
            .propertyOrNull("redis.enabled")
            ?.getString()
            ?.toBoolean() ?: true
    val cacheManager =
        if (redisEnabled) {
            try {
                val redisUrl =
                    environment.config.propertyOrNull("redis.url")?.getString()
                        ?: "redis://localhost:6379"
                val poolConfig =
                    JedisPoolConfig().apply {
                        maxTotal = 8
                        maxIdle = 4
                        minIdle = 1
                        testOnBorrow = true
                    }
                jedisPool = JedisPool(poolConfig, java.net.URI(redisUrl))
                RedisCacheManager(jedisPool!!)
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

    val secret =
        environment.config.propertyOrNull("jwt.secret")?.getString()
            ?: "change-me-in-production-use-env-var-min-64-chars"
    val issuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "http://0.0.0.0:8080/"
    val audience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "http://0.0.0.0:8080/"
    val expiresIn =
        environment.config
            .propertyOrNull("jwt.expiresIn")
            ?.getString()
            ?.toLong() ?: 3600000L

    val jwtService = JwtService(secret, issuer, audience, expiresIn)

    configureRouting(
        jwtService = jwtService,
        vehicleRepo = vehicleRepository,
        jedisPool = jedisPool,
        registry = registry,
    )
}
