package com.solodev.fleet.modules.tracking.infrastructure.ratelimit

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Per-vehicle rate limiter using sliding window algorithm.
 * Tracks location update frequency per vehicle to prevent spam and reduce server load.
 *
 * Configuration:
 * - maxUpdatesPerMinute: Maximum location updates allowed per vehicle per minute
 * - windowSizeSeconds: Time window for tracking (default: 60 seconds)
 *
 * Example:
 * - Vehicle A sends 5 pings/minute → Allowed (within limit)
 * - Vehicle B sends 65 pings/minute → Rejected (exceeds limit)
 */
class LocationUpdateRateLimiter(
    private val maxUpdatesPerMinute: Int = 60,
    private val windowSizeSeconds: Int = 60
) {
    private val vehicleTimestamps = ConcurrentHashMap<String, MutableList<Instant>>()

    /**
     * Check if vehicle is allowed to send location update.
     * Returns true if within rate limit, false otherwise.
     */
    fun isAllowed(vehicleId: String): Boolean {
        val now = Instant.now()
        val windowStart = now.minusSeconds(windowSizeSeconds.toLong())

        // Get or create timestamp list for this vehicle
        val timestamps = vehicleTimestamps.getOrPut(vehicleId) { mutableListOf() }

        // Remove old timestamps outside the window
        timestamps.removeAll { it < windowStart }

        // Check if within rate limit
        if (timestamps.size >= maxUpdatesPerMinute) {
            return false
        }

        // Add current timestamp and allow
        timestamps.add(now)
        return true
    }

    /**
     * Get remaining quota for a vehicle.
     * Returns number of additional updates allowed before hitting limit.
     */
    fun getRemainingQuota(vehicleId: String): Int {
        val now = Instant.now()
        val windowStart = now.minusSeconds(windowSizeSeconds.toLong())

        val timestamps = vehicleTimestamps[vehicleId] ?: return maxUpdatesPerMinute
        val recentCount = timestamps.count { it >= windowStart }

        return maxOf(0, maxUpdatesPerMinute - recentCount)
    }

    /**
     * Get wait time in seconds before next update is allowed.
     * Returns 0 if vehicle can update immediately.
     */
    fun getWaitTimeSeconds(vehicleId: String): Long {
        val now = Instant.now()
        val windowStart = now.minusSeconds(windowSizeSeconds.toLong())

        val timestamps = vehicleTimestamps[vehicleId] ?: return 0
        val recentTimestamps = timestamps.filter { it >= windowStart }

        if (recentTimestamps.size < maxUpdatesPerMinute) {
            return 0 // Can update immediately
        }

        // Find oldest timestamp in window and calculate wait time
        val oldestInWindow = recentTimestamps.minOrNull() ?: return 0
        val windowExpireTime = oldestInWindow.plusSeconds(windowSizeSeconds.toLong())
        val waitSeconds = java.time.temporal.ChronoUnit.SECONDS.between(now, windowExpireTime)

        return maxOf(0, waitSeconds)
    }

    /**
     * Reset rate limit for a specific vehicle (admin only).
     */
    fun resetVehicle(vehicleId: String) {
        vehicleTimestamps.remove(vehicleId)
    }

    /**
     * Get stats for monitoring/debugging.
     */
    fun getStats(vehicleId: String): RateLimitStats {
        val now = Instant.now()
        val windowStart = now.minusSeconds(windowSizeSeconds.toLong())

        val timestamps = vehicleTimestamps[vehicleId] ?: emptyList()
        val recentCount = timestamps.count { it >= windowStart }

        return RateLimitStats(
            vehicleId = vehicleId,
            updatesInWindow = recentCount,
            maxUpdatesAllowed = maxUpdatesPerMinute,
            remainingQuota = maxOf(0, maxUpdatesPerMinute - recentCount),
            isRateLimited = recentCount >= maxUpdatesPerMinute,
            waitTimeSeconds = getWaitTimeSeconds(vehicleId)
        )
    }

    /**
     * Clean up old entries for vehicles that haven't sent updates recently.
     * Call periodically to prevent memory leak.
     */
    fun cleanup(maxInactiveSeconds: Int = 3600) {
        val cutoffTime = Instant.now().minusSeconds(maxInactiveSeconds.toLong())

        vehicleTimestamps.forEach { (vehicleId, timestamps) ->
            timestamps.removeAll { it < cutoffTime }
            if (timestamps.isEmpty()) {
                vehicleTimestamps.remove(vehicleId)
            }
        }
    }
}

/**
 * Statistics for a vehicle's rate limit status.
 */
data class RateLimitStats(
    val vehicleId: String,
    val updatesInWindow: Int,
    val maxUpdatesAllowed: Int,
    val remainingQuota: Int,
    val isRateLimited: Boolean,
    val waitTimeSeconds: Long
)

