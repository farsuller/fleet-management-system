package com.solodev.fleet

import com.solodev.fleet.modules.tracking.infrastructure.idempotency.IdempotencyKeyManager
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehicleRepositoryImpl
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import com.solodev.fleet.shared.infrastructure.email.NuntlyEmailService
import com.solodev.fleet.shared.plugins.configureDatabases
import com.solodev.fleet.shared.plugins.configureObservability
import com.solodev.fleet.shared.plugins.configureRateLimiting
import com.solodev.fleet.shared.plugins.configureRequestId
import com.solodev.fleet.shared.plugins.configureSecurity
import com.solodev.fleet.shared.plugins.configureSerialization
import com.solodev.fleet.shared.plugins.configureStatusPages
import com.solodev.fleet.shared.utils.JwtService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.net.URI
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Entry point for the application. Starts the Netty engine using the configuration defined in
 * application.yaml.
 */
fun main(args: Array<String>) {
    // Load .env file for local development
    val envFile = java.io.File(".env")
    println("[ENV] Current CWD: ${System.getProperty("user.dir")}")
    println("[ENV] Looking for .env at: ${envFile.absolutePath}")

    if (envFile.exists()) {
        println("[ENV] .env file located successfully.")
        envFile.readLines().forEach { line ->
            if (line.isNotBlank() && !line.startsWith("#")) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    // Strip inline comments
                    val rawValue = parts[1].split("#")[0].trim()
                    // Always set System property for local dev to override defaults
                    System.setProperty(key, rawValue)
                    println("[ENV] Loaded $key (Length: ${rawValue.length})")
                }
            }
        }
    } else {
        println("[ENV] .env file NOT FOUND in current directory.")
    }
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
            val host =
                buildString {
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

    // Email Service Initialization (Lazy)
    val emailService: com.solodev.fleet.shared.infrastructure.email.EmailService by lazy {
        val httpClient =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(
                        kotlinx.serialization.json.Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        val emailApiKey =
            environment.config
                .propertyOrNull("email.apiKey")
                ?.getString()
                ?.takeIf { it.isNotBlank() }
                ?: System.getProperty("EMAIL_API_KEY") ?: ""
        val emailSender =
            environment.config
                .propertyOrNull("email.sender")
                ?.getString()
                ?.takeIf { it.isNotBlank() }
                ?: System.getProperty("EMAIL_SENDER") ?: "Fleet Drive <noreply@fleetdrive.com>"
        val emailBaseUrl =
            environment.config
                .propertyOrNull("email.baseUrl")
                ?.getString()
                ?.takeIf { it.isNotBlank() }
                ?: System.getProperty("EMAIL_BASE_URL") ?: "https://api.nuntly.com"

        val nuntlyService =
            NuntlyEmailService(
                client = httpClient,
                apiKey = emailApiKey,
                sender = emailSender,
                baseUrl = emailBaseUrl,
            )

        // Wrapper to log token to console in development for easier testing
        object : com.solodev.fleet.shared.infrastructure.email.EmailService {
            override suspend fun sendVerificationEmail(
                email: String,
                token: String,
                isOtp: Boolean,
            ) {
                if (environment.config.propertyOrNull("ktor.deployment.environment")?.getString() == "development" ||
                    System.getProperty("APP_ENV") == "development"
                ) {
                    println("\n[DEV MODE] Verification for $email:")
                    if (isOtp) {
                        println("OTP CODE: $token")
                    } else {
                        println("VERIFY LINK: http://localhost:8080/v1/auth/verify?token=$token")
                    }
                    println("")
                }
                nuntlyService.sendVerificationEmail(email, token, isOtp)
            }
        }
    }

    // Phase 2: Background Cleanup Tasks
    val idempotencyManager = IdempotencyKeyManager(ttlMinutes = 24 * 60)

    // Launch cleanup job on a low-priority background dispatcher
    launch(Dispatchers.Default) {
        while (isActive) {
            delay(1.hours) // Clean up every hour
            try {
                log.info("Running background cleanup for IdempotencyKeyManager...")
                idempotencyManager.cleanup()
            } catch (e: Exception) {
                log.error("Idempotency cleanup failed", e)
            }
        }
    }

    configureRouting(
        jwtService = jwtService,
        vehicleRepo = vehicleRepository,
        jedisPool = jedisPool,
        registry = registry,
        emailService = emailService,
        cacheManager = cacheManager,
        idempotencyManager = idempotencyManager,
    )
}
