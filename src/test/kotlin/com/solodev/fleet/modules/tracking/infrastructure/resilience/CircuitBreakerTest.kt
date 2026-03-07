package com.solodev.fleet.modules.tracking.infrastructure.resilience

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Unit tests for CircuitBreaker.
 * Tests circuit breaker pattern with state transitions.
 */
class CircuitBreakerTest {

    private lateinit var circuitBreaker: CircuitBreaker

    @BeforeEach
    fun setup() {
        circuitBreaker = CircuitBreaker(
            name = "TestBreaker",
            failureThreshold = 3,
            successThreshold = 2,
            timeoutSeconds = 5
        )
    }

    @Test
    fun `should start in CLOSED state`() {
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState())
    }

    @Test
    fun `should execute operation successfully in CLOSED state`() {
        runBlocking {
            val result = circuitBreaker.execute {
                "success"
            }

            assertEquals("success", result)
            assertEquals(CircuitState.CLOSED, circuitBreaker.getState())
        }
    }

    @Test
    fun `should transition to OPEN after failure threshold`() {
        runBlocking {
            repeat(3) {
                try {
                    circuitBreaker.execute {
                        throw Exception("Simulated failure")
                    }
                } catch (_: Exception) {
                    // Expected
                }
            }

            assertEquals(CircuitState.OPEN, circuitBreaker.getState())
        }
    }

    @Test
    fun `should reject requests in OPEN state`() {
        runBlocking {
            // Trigger opening
            repeat(3) {
                try {
                    circuitBreaker.execute {
                        throw Exception("Simulated failure")
                    }
                } catch (_: Exception) {
                    // Expected
                }
            }

            // Now circuit should be OPEN
            assertFailsWith<CircuitBreakerOpenException> {
                circuitBreaker.execute {
                    "should not execute"
                }
            }
        }
    }

    @Test
    fun `should reset failure count on success`() {
        runBlocking {
            // One failure
            try {
                circuitBreaker.execute {
                    throw Exception("Failure 1")
                }
            } catch (_: Exception) {
                // Expected
            }

            // Success
            circuitBreaker.execute {
                "success"
            }

            val stats = circuitBreaker.getStats()
            assertEquals(0, stats.failureCount)
        }
    }

    @Test
    fun `should accumulate failures`() {
        runBlocking {
            repeat(2) {
                try {
                    circuitBreaker.execute {
                        throw Exception("Failure ${it + 1}")
                    }
                } catch (_: Exception) {
                    // Expected
                }
            }

            val stats = circuitBreaker.getStats()
            assertEquals(2, stats.failureCount)
            assertEquals(CircuitState.CLOSED, circuitBreaker.getState())
        }
    }

    @Test
    fun `should provide circuit breaker statistics`() {
        runBlocking {
            try {
                circuitBreaker.execute {
                    throw Exception("Test failure")
                }
            } catch (_: Exception) {
                // Expected
            }

            val stats = circuitBreaker.getStats()

            assertEquals("TestBreaker", stats.name)
            assertEquals(CircuitState.CLOSED, stats.state)
            assertEquals(1, stats.failureCount)
            assertEquals(0, stats.successCount)
            assertNotNull(stats.lastFailureTime)
        }
    }

    @Test
    fun `should allow manual reset`() {
        runBlocking {
            // Trigger opening
            repeat(3) {
                try {
                    circuitBreaker.execute {
                        throw Exception("Failure")
                    }
                } catch (_: Exception) {
                    // Expected
                }
            }

            assertEquals(CircuitState.OPEN, circuitBreaker.getState())

            // Reset
            circuitBreaker.reset()

            assertEquals(CircuitState.CLOSED, circuitBreaker.getState())
        }
    }

    @Test
    fun `should handle different exception types`() {
        runBlocking {
            // Test with IOException
            try {
                circuitBreaker.execute {
                    throw java.io.IOException("Network error")
                }
            } catch (_: Exception) {
                // Expected
            }

            // Test with custom exception
            try {
                circuitBreaker.execute {
                    throw RuntimeException("Business logic error")
                }
            } catch (_: Exception) {
                // Expected
            }

            val stats = circuitBreaker.getStats()
            assertEquals(2, stats.failureCount)
        }
    }

    @Test
    fun `should allow successful operations after reset`() {
        runBlocking {
            // Trigger opening
            repeat(3) {
                try {
                    circuitBreaker.execute {
                        throw Exception("Failure")
                    }
                } catch (_: Exception) {
                    // Expected
                }
            }

            // Reset
            circuitBreaker.reset()

            // Execute should work now
            val result = circuitBreaker.execute {
                "success after reset"
            }

            assertEquals("success after reset", result)
        }
    }
}

