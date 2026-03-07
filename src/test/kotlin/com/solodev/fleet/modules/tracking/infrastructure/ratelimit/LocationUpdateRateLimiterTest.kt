package com.solodev.fleet.modules.tracking.infrastructure.ratelimit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * Unit tests for LocationUpdateRateLimiter.
 * Tests per-vehicle rate limiting with sliding window algorithm.
 */
class LocationUpdateRateLimiterTest {

    private lateinit var rateLimiter: LocationUpdateRateLimiter

    @BeforeEach
    fun setup() {
        rateLimiter = LocationUpdateRateLimiter(maxUpdatesPerMinute = 60)
    }

    @Test
    fun `should allow vehicle within rate limit`() {
        val vehicleId = "v-123"

        // Allow 10 requests
        repeat(10) {
            assertTrue(rateLimiter.isAllowed(vehicleId))
        }
    }

    @Test
    fun `should reject vehicle exceeding rate limit`() {
        val vehicleId = "v-456"

        // Allow 60 requests (at limit)
        repeat(60) {
            assertTrue(rateLimiter.isAllowed(vehicleId))
        }

        // 61st request should be rejected
        assertFalse(rateLimiter.isAllowed(vehicleId))
    }

    @Test
    fun `should return correct remaining quota`() {
        val vehicleId = "v-789"

        // Use 10 requests
        repeat(10) {
            rateLimiter.isAllowed(vehicleId)
        }

        // Should have 50 remaining (60 - 10)
        assertEquals(50, rateLimiter.getRemainingQuota(vehicleId))
    }

    @Test
    fun `should return zero quota when rate limited`() {
        val vehicleId = "v-limit"

        // Use all 60 requests
        repeat(60) {
            rateLimiter.isAllowed(vehicleId)
        }

        // Try one more
        rateLimiter.isAllowed(vehicleId)

        // Should have zero remaining
        assertEquals(0, rateLimiter.getRemainingQuota(vehicleId))
    }

    @Test
    fun `should calculate wait time correctly`() {
        val vehicleId = "v-wait"

        // Use all 60 requests
        repeat(60) {
            rateLimiter.isAllowed(vehicleId)
        }

        // Try one more
        rateLimiter.isAllowed(vehicleId)

        // Should return wait time > 0
        val waitTime = rateLimiter.getWaitTimeSeconds(vehicleId)
        assertTrue(waitTime > 0, "Wait time should be greater than 0")
        assertTrue(waitTime <= 60, "Wait time should not exceed window size")
    }

    @Test
    fun `should return zero wait time when within limit`() {
        val vehicleId = "v-nowait"

        // Make 5 requests (within limit)
        repeat(5) {
            rateLimiter.isAllowed(vehicleId)
        }

        // Should have zero wait time
        assertEquals(0, rateLimiter.getWaitTimeSeconds(vehicleId))
    }

    @Test
    fun `should track different vehicles independently`() {
        val vehicle1 = "v-1"
        val vehicle2 = "v-2"

        // Vehicle 1: Use 30 requests
        repeat(30) {
            rateLimiter.isAllowed(vehicle1)
        }

        // Vehicle 2: Use 10 requests
        repeat(10) {
            rateLimiter.isAllowed(vehicle2)
        }

        // Check independent quotas
        assertEquals(30, rateLimiter.getRemainingQuota(vehicle1))
        assertEquals(50, rateLimiter.getRemainingQuota(vehicle2))
    }

    @Test
    fun `should provide statistics`() {
        val vehicleId = "v-stats"

        // Use 20 requests
        repeat(20) {
            rateLimiter.isAllowed(vehicleId)
        }

        val stats = rateLimiter.getStats(vehicleId)

        assertEquals(vehicleId, stats.vehicleId)
        assertEquals(20, stats.updatesInWindow)
        assertEquals(60, stats.maxUpdatesAllowed)
        assertEquals(40, stats.remainingQuota)
        assertFalse(stats.isRateLimited)
    }

    @Test
    fun `should mark as rate limited in stats`() {
        val vehicleId = "v-limited"

        // Use all 60 requests
        repeat(60) {
            rateLimiter.isAllowed(vehicleId)
        }

        // Try one more
        rateLimiter.isAllowed(vehicleId)

        val stats = rateLimiter.getStats(vehicleId)

        assertTrue(stats.isRateLimited)
        assertEquals(0, stats.remainingQuota)
    }

    @Test
    fun `should reset vehicle on cleanup`() {
        val vehicleId = "v-reset"

        // Use 20 requests
        repeat(20) {
            rateLimiter.isAllowed(vehicleId)
        }

        // Reset vehicle
        rateLimiter.resetVehicle(vehicleId)

        // Should allow new updates
        assertTrue(rateLimiter.isAllowed(vehicleId))
        assertEquals(59, rateLimiter.getRemainingQuota(vehicleId))
    }

    @Test
    fun `should allow customizable limit`() {
        val customLimiter = LocationUpdateRateLimiter(maxUpdatesPerMinute = 10)

        val vehicleId = "v-custom"

        // Allow 10 requests
        repeat(10) {
            assertTrue(customLimiter.isAllowed(vehicleId))
        }

        // 11th should be rejected
        assertFalse(customLimiter.isAllowed(vehicleId))
    }

    @Test
    fun `should handle multiple rapid requests`() {
        val vehicleId = "v-rapid"

        // Simulate rapid-fire requests
        var allowed = 0
        var rejected = 0

        repeat(100) {
            if (rateLimiter.isAllowed(vehicleId)) {
                allowed++
            } else {
                rejected++
            }
        }

        // First 60 allowed, rest rejected
        assertEquals(60, allowed)
        assertEquals(40, rejected)
    }

    @Test
    fun `should handle concurrent vehicles`() {
        val vehicles = (1..10).map { "v-$it" }

        // Each vehicle: 50 requests
        for (vehicle in vehicles) {
            repeat(50) {
                assertTrue(rateLimiter.isAllowed(vehicle))
            }
        }

        // Each should have 10 remaining
        for (vehicle in vehicles) {
            assertEquals(10, rateLimiter.getRemainingQuota(vehicle))
        }
    }
}

