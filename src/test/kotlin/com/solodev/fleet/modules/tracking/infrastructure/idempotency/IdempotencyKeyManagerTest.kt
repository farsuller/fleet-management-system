package com.solodev.fleet.modules.tracking.infrastructure.idempotency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    fun shouldReturnTrue_WhenFirstRequestRecorded() {
        // Arrange
        val key = UUID.randomUUID().toString()
        val response = """{"status": "ok"}"""

        // Act
        val isFirst = manager.recordRequest(key, response, 200)

        // Assert
        assertThat(isFirst).isTrue()
    }

    @Test
    fun shouldReturnFalse_WhenRequestIsDuplicate() {
        // Arrange
        val key = UUID.randomUUID().toString()
        val response = """{"status": "ok"}"""
        manager.recordRequest(key, response, 200)

        // Act
        val isFirst = manager.recordRequest(key, response, 200)

        // Assert
        assertThat(isFirst).isFalse()
    }

    @Test
    fun shouldReturnCachedResponse_WhenKeyExists() {
        // Arrange
        val key = UUID.randomUUID().toString()
        val response = """{"vehicleId": "v-123", "status": "ok"}"""
        manager.recordRequest(key, response, 200)

        // Act
        val cached = manager.getCachedResponse(key)

        // Assert
        assertThat(cached).isNotNull()
        assertThat(cached!!.responseBody).isEqualTo(response)
        assertThat(cached.httpStatus).isEqualTo(200)
    }

    @Test
    fun shouldReturnNull_WhenKeyNotFound() {
        // Arrange
        val key = UUID.randomUUID().toString()

        // Act
        val cached = manager.getCachedResponse(key)

        // Assert
        assertThat(cached).isNull()
    }

    @Test
    fun shouldReturnTrue_WhenKeyIsValidUuid() {
        // Arrange
        val validKey = UUID.randomUUID().toString()

        // Act / Assert
        assertThat(manager.isValidKey(validKey)).isTrue()
    }

    @Test
    fun shouldReturnTrue_WhenKeyIsAlphanumeric() {
        // Arrange
        val validKey = "abc123def456"

        // Act / Assert
        assertThat(manager.isValidKey(validKey)).isTrue()
    }

    @Test
    fun shouldReturnTrue_WhenKeyHasHyphens() {
        // Arrange
        val validKey = "abc-123-def-456"

        // Act / Assert
        assertThat(manager.isValidKey(validKey)).isTrue()
    }

    @Test
    fun shouldReturnFalse_WhenKeyHasSpecialChars() {
        // Arrange
        val invalidKey = "abc@123#def$456"

        // Act / Assert
        assertThat(manager.isValidKey(invalidKey)).isFalse()
    }

    @Test
    fun shouldReturnFalse_WhenKeyHasSpaces() {
        // Arrange
        val invalidKey = "abc 123 def"

        // Act / Assert
        assertThat(manager.isValidKey(invalidKey)).isFalse()
    }

    @Test
    fun shouldReturnFalse_WhenKeyIsTooLong() {
        // Arrange
        val invalidKey = "a".repeat(300)

        // Act / Assert
        assertThat(manager.isValidKey(invalidKey)).isFalse()
    }

    @Test
    fun shouldReturnFalse_WhenKeyIsEmpty() {
        // Arrange
        val invalidKey = ""

        // Act / Assert
        assertThat(manager.isValidKey(invalidKey)).isFalse()
    }

    @Test
    fun shouldCacheDifferentResponses_WhenKeysAreDifferent() {
        // Arrange
        val key1 = "key-1"
        val key2 = "key-2"
        val response1 = """{"result": "first"}"""
        val response2 = """{"result": "second"}"""
        manager.recordRequest(key1, response1, 200)
        manager.recordRequest(key2, response2, 201)

        // Act
        val cached1 = manager.getCachedResponse(key1)
        val cached2 = manager.getCachedResponse(key2)

        // Assert
        assertThat(cached1?.responseBody).isEqualTo(response1)
        assertThat(cached2?.responseBody).isEqualTo(response2)
        assertThat(cached1?.httpStatus).isEqualTo(200)
        assertThat(cached2?.httpStatus).isEqualTo(201)
    }

    @Test
    fun shouldPreserveHttpStatusCode_WhenResponseIsCached() {
        // Arrange
        val key = UUID.randomUUID().toString()
        val response = """{"error": "not found"}"""
        manager.recordRequest(key, response, 404)

        // Act
        val cached = manager.getCachedResponse(key)

        // Assert
        assertThat(cached?.httpStatus).isEqualTo(404)
    }

    @Test
    fun shouldHandleLargeResponsePayload_WhenCached() {
        // Arrange
        val key = UUID.randomUUID().toString()
        val largeResponse = """{"data": "${"x".repeat(10000)}"}"""
        manager.recordRequest(key, largeResponse, 200)

        // Act
        val cached = manager.getCachedResponse(key)

        // Assert
        assertThat(cached?.responseBody).isEqualTo(largeResponse)
    }

    @Test
    fun shouldReturnCacheStatistics_WhenRequestsRecorded() {
        // Arrange
        repeat(5) {
            val key = "key-$it"
            manager.recordRequest(key, """{"id": "$it"}""", 200)
        }

        // Act
        val stats = manager.getStats()

        // Assert
        assertThat(stats.totalCachedRequests).isEqualTo(5)
        assertThat(stats.activeRequests).isGreaterThan(0)
        assertThat(stats.expiredRequests).isEqualTo(0)
    }

    @Test
    fun shouldReturnNull_WhenCacheIsCleared() {
        // Arrange
        val key1 = "key-1"
        val key2 = "key-2"
        manager.recordRequest(key1, """{"data": "1"}""", 200)
        manager.recordRequest(key2, """{"data": "2"}""", 200)

        // Act
        manager.clearAll()

        // Assert
        assertThat(manager.getCachedResponse(key1)).isNull()
        assertThat(manager.getCachedResponse(key2)).isNull()
    }

    @Test
    fun shouldReturnFalse_WhenKeyIsInvalid() {
        // Arrange
        val invalidKey = "invalid@key"

        // Act / Assert
        assertThat(manager.isValidKey(invalidKey)).isFalse()
    }

    @Test
    fun shouldCacheAllResponses_WhenMultipleKeysCached() {
        // Arrange
        val keys = (1..10).map { "key-$it" }
        val responses = keys.map { """{"result": "$it"}""" }
        keys.forEachIndexed { index, key ->
            manager.recordRequest(key, responses[index], 200)
        }

        // Act / Assert
        keys.forEachIndexed { index, key ->
            val cached = manager.getCachedResponse(key)
            assertThat(cached?.responseBody).isEqualTo(responses[index])
        }
    }

    @Test
    fun shouldReturnFalseAfterFirst_WhenSameKeyRecordedMultipleTimes() {
        // Arrange
        val key = UUID.randomUUID().toString()

        // Act
        val first = manager.recordRequest(key, """{"attempt": "1"}""", 200)
        val second = manager.recordRequest(key, """{"attempt": "1"}""", 200)
        val third = manager.recordRequest(key, """{"attempt": "1"}""", 200)

        // Assert
        assertThat(first).isTrue()
        assertThat(second).isFalse()
        assertThat(third).isFalse()
    }

    @Test
    fun shouldStoreTimestamp_WhenResponseIsCached() {
        // Arrange
        val key = UUID.randomUUID().toString()
        manager.recordRequest(key, """{"status": "ok"}""", 200)

        // Act
        val cached = manager.getCachedResponse(key)

        // Assert
        assertThat(cached?.timestamp).isNotNull()
    }

    @Test
    fun shouldCacheResponse_WhenManagerHasCustomTtl() {
        // Arrange
        val shortTtlManager = IdempotencyKeyManager(ttlMinutes = 1)
        val key = UUID.randomUUID().toString()
        shortTtlManager.recordRequest(key, """{"status": "ok"}""", 200)

        // Act
        val cached = shortTtlManager.getCachedResponse(key)

        // Assert
        assertThat(cached).isNotNull()
    }
}

