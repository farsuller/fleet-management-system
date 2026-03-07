package com.solodev.fleet.modules.tracking.infrastructure.resilience

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for RetryPolicy.
 * Tests retry logic with exponential backoff.
 */
class RetryPolicyTest {

    private lateinit var retryPolicy: RetryPolicy

    @BeforeEach
    fun setup() {
        retryPolicy = RetryPolicy(maxRetries = 3, initialDelayMs = 10, maxDelayMs = 100)
    }

    @Test
    fun `should execute successfully on first attempt`() {
        runBlocking {
            var attempts = 0

            val result = retryPolicy.execute("TestOperation") {
                attempts++
                "success"
            }

            assertEquals("success", result)
            assertEquals(1, attempts)
        }
    }

    @Test
    fun `should retry on transient failure`() {
        runBlocking {
            var attempts = 0

            val result = retryPolicy.execute("TestOperation") {
                attempts++
                if (attempts < 3) {
                    throw java.net.SocketTimeoutException("Timeout")
                }
                "success"
            }

            assertEquals("success", result)
            assertEquals(3, attempts)
        }
    }

    @Test
    fun `should give up after max retries`() {
        runBlocking {
            var attempts = 0

            assertFailsWith<java.net.SocketTimeoutException> {
                retryPolicy.execute("TestOperation") {
                    attempts++
                    throw java.net.SocketTimeoutException("Timeout")
                }
            }

            assertEquals(4, attempts) // 1 initial + 3 retries
        }
    }

    @Test
    fun `should not retry on non-transient failure`() {
        runBlocking {
            var attempts = 0

            assertFailsWith<IllegalArgumentException> {
                retryPolicy.execute("TestOperation") {
                    attempts++
                    throw IllegalArgumentException("Invalid argument")
                }
            }

            assertEquals(1, attempts) // No retries
        }
    }

    @Test
    fun `should handle connect exceptions as retryable`() {
        runBlocking {
            var attempts = 0

            val result = retryPolicy.execute("TestOperation") {
                attempts++
                if (attempts < 2) {
                    throw java.net.ConnectException("Connection refused")
                }
                "success"
            }

            assertEquals("success", result)
            assertEquals(2, attempts)
        }
    }
}

