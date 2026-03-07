package com.solodev.fleet.modules.tracking.infrastructure.resilience

import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for FallbackHandler.
 * Tests fallback mechanism for graceful degradation.
 */
class FallbackHandlerTest {

    @Test
    fun `should use primary operation when successful`() {
        runBlocking {
            var primaryCalled = false
            var fallbackCalled = false

            val result = FallbackHandler(
                operationName = "TestOp",
                primary = {
                    primaryCalled = true
                    "primary result"
                },
                fallback = {
                    fallbackCalled = true
                    "fallback result"
                }
            ).execute()

            assertEquals("primary result", result)
            assertTrue(primaryCalled)
            assertFalse(fallbackCalled)
        }
    }

    @Test
    fun `should use fallback when primary fails`() {
        runBlocking {
            var primaryCalled = false
            var fallbackCalled = false

            val result = FallbackHandler(
                operationName = "TestOp",
                primary = {
                    primaryCalled = true
                    throw Exception("Primary failed")
                },
                fallback = {
                    fallbackCalled = true
                    "fallback result"
                }
            ).execute()

            assertEquals("fallback result", result)
            assertTrue(primaryCalled)
            assertTrue(fallbackCalled)
        }
    }

    @Test
    fun `should propagate fallback failure`() {
        runBlocking {
            assertFailsWith<Exception> {
                FallbackHandler(
                    operationName = "TestOp",
                    primary = {
                        throw Exception("Primary failed")
                    },
                    fallback = {
                        throw Exception("Fallback also failed")
                    }
                ).execute()
            }
        }
    }

    @Test
    fun `should handle fallback returning null`() {
        runBlocking {
            val result = FallbackHandler(
                operationName = "TestOp",
                primary = {
                    throw Exception("Primary failed")
                },
                fallback = {
                    null
                }
            ).execute()

            assertEquals(null, result)
        }
    }

    @Test
    fun `should support both successful outcomes`() {
        runBlocking {
            // Primary succeeds
            val result1 = FallbackHandler(
                operationName = "TestOp",
                primary = { "primary" },
                fallback = { "fallback" }
            ).execute()

            assertEquals("primary", result1)

            // Primary fails, fallback succeeds
            val result2 = FallbackHandler(
                operationName = "TestOp",
                primary = { throw Exception("Failed") },
                fallback = { "fallback" }
            ).execute()

            assertEquals("fallback", result2)
        }
    }
}

