package com.solodev.fleet.shared.plugins

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.ktor.server.application.*
import java.time.Duration


/**
 * Configures circuit breakers for external service calls.
 * Prevents cascading failures by failing fast when external services are down.
 */
fun Application.configureCircuitBreakers(): CircuitBreakerRegistry {
    val config = CircuitBreakerConfig.custom()
        .failureRateThreshold(50.0f) // Open circuit if 50% of calls fail
        .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before trying again
        .slidingWindowSize(10) // Track last 10 calls
        .build()

    return CircuitBreakerRegistry.of(config)
}

/**
 * Execute a suspendable function with circuit breaker protection.
 */
suspend fun <T> CircuitBreaker.executeSuspend(block: suspend () -> T): T {
    return this.executeSuspendFunction(block)  // ✅ Fixed
}