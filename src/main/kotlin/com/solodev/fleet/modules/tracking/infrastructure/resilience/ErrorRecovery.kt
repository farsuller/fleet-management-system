package com.solodev.fleet.modules.tracking.infrastructure.resilience

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import org.slf4j.LoggerFactory

/**
 * Circuit Breaker pattern implementation for error recovery.
 * Prevents cascading failures when downstream services (PostGIS, Redis, Database) experience issues.
 *
 * **States**:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Failures detected, requests rejected immediately
 * - HALF_OPEN: Testing if service recovered, limited requests allowed
 *
 * **Configuration**:
 * - failureThreshold: Number of failures before opening circuit
 * - successThreshold: Number of successes in HALF_OPEN to close circuit
 * - timeout: How long to stay in OPEN state before trying HALF_OPEN
 */
class CircuitBreaker(
    private val name: String,
    private val failureThreshold: Int = 5,
    private val successThreshold: Int = 2,
    private val timeoutSeconds: Long = 60
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private var state = CircuitState.CLOSED
    private var failureCount = AtomicInteger(0)
    private var successCount = AtomicInteger(0)
    private var lastFailureTime: Instant? = null
    private var stateTransitionTime: Instant = Instant.now()

    /**
     * Execute operation with circuit breaker protection.
     * Throws CircuitBreakerOpenException if circuit is open.
     */
    suspend fun <T> execute(operation: suspend () -> T): T {
        return when (state) {
            CircuitState.CLOSED -> executeInClosed(operation)
            CircuitState.OPEN -> executeInOpen(operation)
            CircuitState.HALF_OPEN -> executeInHalfOpen(operation)
        }
    }

    private suspend fun <T> executeInClosed(operation: suspend () -> T): T {
        return try {
            val result = operation()
            failureCount.set(0) // Reset on success
            result
        } catch (e: Exception) {
            handleFailure(e)
            throw e
        }
    }

    private suspend fun <T> executeInOpen(operation: suspend () -> T): T {
        // Check if timeout expired, if so transition to HALF_OPEN
        val timeSinceOpen = java.time.temporal.ChronoUnit.SECONDS
            .between(stateTransitionTime, Instant.now())

        if (timeSinceOpen > timeoutSeconds) {
            logger.info("$name: Circuit breaker timeout expired, transitioning to HALF_OPEN")
            transitionTo(CircuitState.HALF_OPEN)
            return executeInHalfOpen(operation)
        }

        throw CircuitBreakerOpenException("$name: Circuit breaker is OPEN")
    }

    private suspend fun <T> executeInHalfOpen(operation: suspend () -> T): T {
        return try {
            val result = operation()
            successCount.incrementAndGet()

            // If we've had enough successes, close the circuit
            if (successCount.get() >= successThreshold) {
                logger.info("$name: Circuit breaker recovered, transitioning to CLOSED")
                transitionTo(CircuitState.CLOSED)
            }

            result
        } catch (e: Exception) {
            logger.warn("$name: Failure in HALF_OPEN state, reopening circuit")
            transitionTo(CircuitState.OPEN)
            throw e
        }
    }

    private fun handleFailure(e: Exception) {
        lastFailureTime = Instant.now()
        failureCount.incrementAndGet()

        if (failureCount.get() >= failureThreshold) {
            logger.error("$name: Failure threshold reached, opening circuit", e)
            transitionTo(CircuitState.OPEN)
        } else {
            logger.warn(
                "$name: Failure detected (${failureCount.get()}/$failureThreshold)",
                e
            )
        }
    }

    private fun transitionTo(newState: CircuitState) {
        state = newState
        stateTransitionTime = Instant.now()

        when (newState) {
            CircuitState.CLOSED -> {
                failureCount.set(0)
                successCount.set(0)
            }
            CircuitState.OPEN -> {
                // Stay open until timeout
            }
            CircuitState.HALF_OPEN -> {
                successCount.set(0)
            }
        }

        logger.info("$name: Circuit breaker transitioned to $newState")
    }

    fun getState(): CircuitState = state

    fun getStats(): CircuitBreakerStats {
        return CircuitBreakerStats(
            name = name,
            state = state,
            failureCount = failureCount.get(),
            successCount = successCount.get(),
            lastFailureTime = lastFailureTime,
            lastStateChange = stateTransitionTime
        )
    }

    fun reset() {
        transitionTo(CircuitState.CLOSED)
        logger.info("$name: Circuit breaker manually reset")
    }
}

enum class CircuitState {
    CLOSED,     // Normal operation
    OPEN,       // Rejecting requests
    HALF_OPEN   // Testing recovery
}

class CircuitBreakerOpenException(message: String) : Exception(message)

data class CircuitBreakerStats(
    val name: String,
    val state: CircuitState,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: Instant?,
    val lastStateChange: Instant
)

/**
 * Retry policy with exponential backoff for handling transient failures.
 *
 * **Retry Strategy**:
 * - Retry only on transient failures (timeouts, temporary errors)
 * - Use exponential backoff to prevent overwhelming recovering service
 * - Max retries configurable
 *
 * **Example**:
 * - Attempt 1: Immediate
 * - Attempt 2: Wait 100ms, try again
 * - Attempt 3: Wait 200ms, try again
 * - Attempt 4: Wait 400ms, try again
 * - Give up if all fail
 */
class RetryPolicy(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 100,
    private val maxDelayMs: Long = 5000
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun <T> execute(
        operationName: String,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var delay = initialDelayMs

        repeat(maxRetries + 1) { attempt ->
            try {
                if (attempt > 0) {
                    logger.info("$operationName: Retry attempt ${attempt + 1}/${ maxRetries + 1} after ${delay}ms")
                    kotlinx.coroutines.delay(delay)
                    delay = min(delay * 2, maxDelayMs) // Exponential backoff
                }

                return operation()
            } catch (e: Exception) {
                lastException = e

                if (attempt < maxRetries && isRetryable(e)) {
                    logger.warn(
                        "$operationName: Transient failure on attempt ${attempt + 1}, will retry",
                        e
                    )
                } else {
                    logger.error(
                        "$operationName: Failed after ${attempt + 1} attempts",
                        e
                    )
                    throw e
                }
            }
        }

        throw lastException ?: Exception("$operationName: All retries exhausted")
    }

    private fun isRetryable(e: Exception): Boolean {
        return when (e) {
            is java.net.SocketTimeoutException -> true
            is java.net.ConnectException -> true
            is java.io.IOException -> true // Network errors
            is java.util.concurrent.TimeoutException -> true
            is kotlinx.coroutines.TimeoutCancellationException -> true
            else -> false
        }
    }
}

/**
 * Fallback mechanism for graceful degradation when services fail.
 *
 * **Strategy**:
 * - Try primary operation
 * - If fails and is recoverable, try fallback
 * - Log all failures for debugging
 */
class FallbackHandler<T>(
    private val operationName: String,
    private val primary: suspend () -> T,
    private val fallback: suspend () -> T?
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun execute(): T? {
        return try {
            logger.debug("$operationName: Executing primary operation")
            primary()
        } catch (e: Exception) {
            logger.warn("$operationName: Primary operation failed, trying fallback", e)
            try {
                fallback()?.also {
                    logger.info("$operationName: Fallback succeeded, service degraded but operational")
                }
            } catch (fallbackError: Exception) {
                logger.error("$operationName: Both primary and fallback failed", fallbackError)
                throw fallbackError
            }
        }
    }
}

/**
 * Health check for monitoring service status.
 */
interface HealthCheck {
    suspend fun check(): HealthStatus

    data class HealthStatus(
        val serviceName: String,
        val isHealthy: Boolean,
        val lastCheckTime: Instant,
        val errorMessage: String? = null,
        val responseTimeMs: Long = 0
    )
}

/**
 * Timeout handler for operations that take too long.
 */
suspend fun <T> withTimeout(
    timeoutMs: Long,
    operationName: String,
    operation: suspend () -> T
): T {
    return try {
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            operation()
        } ?: throw java.util.concurrent.TimeoutException(
            "$operationName: Operation exceeded timeout of ${timeoutMs}ms"
        )
    } catch (e: Exception) {
        throw java.util.concurrent.TimeoutException(
            "$operationName: Timeout or error after ${timeoutMs}ms"
        ).initCause(e)
    }
}


