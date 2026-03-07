package com.solodev.fleet.modules.tracking.infrastructure.idempotency

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Idempotency key manager for location update endpoints.
 * Prevents duplicate processing of the same request due to network retries.
 *
 * **How It Works**:
 * 1. Client generates idempotency-key (UUID) for each request
 * 2. Server receives request with idempotency-key header
 * 3. If key not seen before → Process request, cache response
 * 4. If key seen before → Return cached response (no reprocessing)
 *
 * **Example**:
 * - Request 1: POST /location with Idempotency-Key: abc123 → Process, cache result
 * - Request 2 (retry): POST /location with Idempotency-Key: abc123 → Return cached result
 *
 * **Expiration**: Keys automatically expire after configured TTL (default: 24 hours)
 */
class IdempotencyKeyManager(
    private val ttlMinutes: Int = 1440 // 24 hours default
) {
    data class CachedResponse(
        val responseBody: String,
        val timestamp: Instant,
        val httpStatus: Int
    )

    private val cache = ConcurrentHashMap<String, CachedResponse>()

    /**
     * Record a request processing result with idempotency key.
     * Returns true if this is a new key (first time seeing it).
     * Returns false if key already exists (duplicate request).
     */
    fun recordRequest(
        idempotencyKey: String,
        responseBody: String,
        httpStatus: Int = 200
    ): Boolean {
        val now = Instant.now()

        // Check if key already exists
        val existing = cache[idempotencyKey]
        if (existing != null && !isExpired(existing)) {
            return false // Duplicate request
        }

        // Store new response
        cache[idempotencyKey] = CachedResponse(
            responseBody = responseBody,
            timestamp = now,
            httpStatus = httpStatus
        )

        return true // First time processing this key
    }

    /**
     * Get cached response for idempotency key.
     * Returns null if key doesn't exist or is expired.
     */
    fun getCachedResponse(idempotencyKey: String): CachedResponse? {
        val cached = cache[idempotencyKey] ?: return null

        if (isExpired(cached)) {
            cache.remove(idempotencyKey)
            return null
        }

        return cached
    }

    /**
     * Validate idempotency key format.
     * Keys should be UUID-like format (alphanumeric with hyphens).
     */
    fun isValidKey(key: String): Boolean {
        return key.matches(Regex("^[a-zA-Z0-9-]{1,256}$"))
    }

    /**
     * Check if cached response has expired.
     */
    private fun isExpired(cached: CachedResponse): Boolean {
        val expirationTime = cached.timestamp.plusSeconds((ttlMinutes * 60L))
        return Instant.now() > expirationTime
    }

    /**
     * Clean up expired entries from cache.
     * Call periodically to prevent memory leak.
     */
    fun cleanup() {
        val expiredKeys = cache.entries
            .filter { isExpired(it.value) }
            .map { it.key }

        expiredKeys.forEach { cache.remove(it) }
    }

    /**
     * Get cache statistics for monitoring.
     */
    fun getStats(): IdempotencyStats {
        val totalSize = cache.size
        val expiredCount = cache.values.count { isExpired(it) }
        val activeCount = totalSize - expiredCount

        return IdempotencyStats(
            totalCachedRequests = totalSize,
            activeRequests = activeCount,
            expiredRequests = expiredCount,
            cacheCapacityUsed = "${(totalSize * 100) / 10000}%"
        )
    }

    /**
     * Clear all cached requests (admin only).
     */
    fun clearAll() {
        cache.clear()
    }
}

/**
 * Statistics about idempotency cache usage.
 */
data class IdempotencyStats(
    val totalCachedRequests: Int,
    val activeRequests: Int,
    val expiredRequests: Int,
    val cacheCapacityUsed: String
)

/**
 * Result of idempotency key processing.
 */
data class IdempotencyCheckResult(
    val idempotencyKey: String,
    val isFirstRequest: Boolean,
    val cachedResponse: String? = null,
    val cachedStatus: Int? = null
)

