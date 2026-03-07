package com.solodev.fleet.modules.tracking.infrastructure.idempotency

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.UUID

/**
 * Unit tests for IdempotencyKeyManager.
 * Tests request deduplication and idempotency key caching.
 */
class IdempotencyKeyManagerTest {

    private lateinit var manager: IdempotencyKeyManager

    @BeforeEach
    fun setup() {
        manager = IdempotencyKeyManager(ttlMinutes = 24 * 60)
    }

    @Test
    fun `should record first request successfully`() {
        val key = UUID.randomUUID().toString()
        val response = """{"status": "ok"}"""

        val isFirst = manager.recordRequest(key, response, 200)

        assertTrue(isFirst, "First request should return true")
    }

    @Test
    fun `should detect duplicate request`() {
        val key = UUID.randomUUID().toString()
        val response = """{"status": "ok"}"""

        // Record first request
        manager.recordRequest(key, response, 200)

        // Try to record same key again
        val isFirst = manager.recordRequest(key, response, 200)

        assertFalse(isFirst, "Duplicate request should return false")
    }

    @Test
    fun `should retrieve cached response`() {
        val key = UUID.randomUUID().toString()
        val response = """{"vehicleId": "v-123", "status": "ok"}"""

        manager.recordRequest(key, response, 200)

        val cached = manager.getCachedResponse(key)

        assertNotNull(cached)
        assertEquals(response, cached.responseBody)
        assertEquals(200, cached.httpStatus)
    }

    @Test
    fun `should return null for unknown key`() {
        val key = UUID.randomUUID().toString()

        val cached = manager.getCachedResponse(key)

        assertNull(cached)
    }

    @Test
    fun `should validate key format - valid UUID`() {
        val validKey = UUID.randomUUID().toString()

        assertTrue(manager.isValidKey(validKey))
    }

    @Test
    fun `should validate key format - alphanumeric`() {
        val validKey = "abc123def456"

        assertTrue(manager.isValidKey(validKey))
    }

    @Test
    fun `should validate key format - with hyphens`() {
        val validKey = "abc-123-def-456"

        assertTrue(manager.isValidKey(validKey))
    }

    @Test
    fun `should reject invalid key format - special chars`() {
        val invalidKey = "abc@123#def$456"

        assertFalse(manager.isValidKey(invalidKey))
    }

    @Test
    fun `should reject invalid key format - spaces`() {
        val invalidKey = "abc 123 def"

        assertFalse(manager.isValidKey(invalidKey))
    }

    @Test
    fun `should reject invalid key format - too long`() {
        val invalidKey = "a".repeat(300)

        assertFalse(manager.isValidKey(invalidKey))
    }

    @Test
    fun `should reject invalid key format - empty`() {
        val invalidKey = ""

        assertFalse(manager.isValidKey(invalidKey))
    }

    @Test
    fun `should cache different responses for different keys`() {
        val key1 = "key-1"
        val key2 = "key-2"
        val response1 = """{"result": "first"}"""
        val response2 = """{"result": "second"}"""

        manager.recordRequest(key1, response1, 200)
        manager.recordRequest(key2, response2, 201)

        val cached1 = manager.getCachedResponse(key1)
        val cached2 = manager.getCachedResponse(key2)

        assertEquals(response1, cached1?.responseBody)
        assertEquals(response2, cached2?.responseBody)
        assertEquals(200, cached1?.httpStatus)
        assertEquals(201, cached2?.httpStatus)
    }

    @Test
    fun `should preserve HTTP status code`() {
        val key = UUID.randomUUID().toString()
        val response = """{"error": "not found"}"""

        manager.recordRequest(key, response, 404)

        val cached = manager.getCachedResponse(key)

        assertEquals(404, cached?.httpStatus)
    }

    @Test
    fun `should handle large response payloads`() {
        val key = UUID.randomUUID().toString()
        val largeResponse = """{"data": "${"x".repeat(10000)}"}"""

        manager.recordRequest(key, largeResponse, 200)

        val cached = manager.getCachedResponse(key)

        assertEquals(largeResponse, cached?.responseBody)
    }

    @Test
    fun `should provide cache statistics`() {
        // Record 5 requests
        repeat(5) {
            val key = "key-$it"
            manager.recordRequest(key, """{"id": "$it"}""", 200)
        }

        val stats = manager.getStats()

        assertEquals(5, stats.totalCachedRequests)
        assertTrue(stats.activeRequests > 0)
        assertEquals(0, stats.expiredRequests)
    }

    @Test
    fun `should clear all cached requests`() {
        val key1 = "key-1"
        val key2 = "key-2"

        manager.recordRequest(key1, """{"data": "1"}""", 200)
        manager.recordRequest(key2, """{"data": "2"}""", 200)

        manager.clearAll()

        val cached1 = manager.getCachedResponse(key1)
        val cached2 = manager.getCachedResponse(key2)

        assertNull(cached1)
        assertNull(cached2)
    }

    @Test
    fun `should validate before recording`() {
        val invalidKey = "invalid@key"
        val response = """{"status": "ok"}"""

        val isValid = manager.isValidKey(invalidKey)

        assertFalse(isValid)
    }

    @Test
    fun `should handle concurrent requests with different keys`() {
        val keys = (1..10).map { "key-$it" }
        val responses = keys.map { """{"result": "$it"}""" }

        keys.forEachIndexed { index, key ->
            manager.recordRequest(key, responses[index], 200)
        }

        keys.forEachIndexed { index, key ->
            val cached = manager.getCachedResponse(key)
            assertEquals(responses[index], cached?.responseBody)
        }
    }

    @Test
    fun `should handle same key recorded multiple times`() {
        val key = UUID.randomUUID().toString()

        val first = manager.recordRequest(key, """{"attempt": "1"}""", 200)
        val second = manager.recordRequest(key, """{"attempt": "1"}""", 200)
        val third = manager.recordRequest(key, """{"attempt": "1"}""", 200)

        assertTrue(first)
        assertFalse(second)
        assertFalse(third)
    }

    @Test
    fun `should store timestamp with cached response`() {
        val key = UUID.randomUUID().toString()
        val response = """{"status": "ok"}"""

        manager.recordRequest(key, response, 200)

        val cached = manager.getCachedResponse(key)

        assertNotNull(cached?.timestamp)
    }

    @Test
    fun `should use configurable TTL`() {
        val shortTtlManager = IdempotencyKeyManager(ttlMinutes = 1)

        val key = UUID.randomUUID().toString()
        val responseData = """{"status": "ok"}"""

        shortTtlManager.recordRequest(key, responseData, 200)

        val cached = shortTtlManager.getCachedResponse(key)

        assertNotNull(cached)
    }
}

