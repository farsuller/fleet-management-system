package com.solodev.fleet.modules.tracking.infrastructure.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
    fun shouldAllowVehicle_WhenWithinRateLimit() {
        // Arrange
        val vehicleId = "v-123"

        // Act / Assert
        repeat(10) {
            assertThat(rateLimiter.isAllowed(vehicleId)).isTrue()
        }
    }

    @Test
    fun shouldRejectVehicle_WhenRateLimitExceeded() {
        // Arrange
        val vehicleId = "v-456"
        repeat(60) { rateLimiter.isAllowed(vehicleId) }

        // Act
        val result = rateLimiter.isAllowed(vehicleId)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun shouldReturnCorrectRemainingQuota_WhenRequestsMade() {
        // Arrange
        val vehicleId = "v-789"
        repeat(10) { rateLimiter.isAllowed(vehicleId) }

        // Act
        val remaining = rateLimiter.getRemainingQuota(vehicleId)

        // Assert
        assertThat(remaining).isEqualTo(50)
    }

    @Test
    fun shouldReturnZeroQuota_WhenRateLimited() {
        // Arrange
        val vehicleId = "v-limit"
        repeat(60) { rateLimiter.isAllowed(vehicleId) }
        rateLimiter.isAllowed(vehicleId)

        // Act
        val remaining = rateLimiter.getRemainingQuota(vehicleId)

        // Assert
        assertThat(remaining).isEqualTo(0)
    }

    @Test
    fun shouldReturnPositiveWaitTime_WhenRateLimited() {
        // Arrange
        val vehicleId = "v-wait"
        repeat(60) { rateLimiter.isAllowed(vehicleId) }
        rateLimiter.isAllowed(vehicleId)

        // Act
        val waitTime = rateLimiter.getWaitTimeSeconds(vehicleId)

        // Assert
        assertThat(waitTime).isGreaterThan(0)
        assertThat(waitTime).isLessThanOrEqualTo(60)
    }

    @Test
    fun shouldReturnZeroWaitTime_WhenWithinLimit() {
        // Arrange
        val vehicleId = "v-nowait"
        repeat(5) { rateLimiter.isAllowed(vehicleId) }

        // Act
        val waitTime = rateLimiter.getWaitTimeSeconds(vehicleId)

        // Assert
        assertThat(waitTime).isEqualTo(0)
    }

    @Test
    fun shouldTrackVehiclesIndependently_WhenMultipleVehicles() {
        // Arrange
        val vehicle1 = "v-1"
        val vehicle2 = "v-2"
        repeat(30) { rateLimiter.isAllowed(vehicle1) }
        repeat(10) { rateLimiter.isAllowed(vehicle2) }

        // Act / Assert
        assertThat(rateLimiter.getRemainingQuota(vehicle1)).isEqualTo(30)
        assertThat(rateLimiter.getRemainingQuota(vehicle2)).isEqualTo(50)
    }

    @Test
    fun shouldReturnCorrectStats_WhenRequestsMade() {
        // Arrange
        val vehicleId = "v-stats"
        repeat(20) { rateLimiter.isAllowed(vehicleId) }

        // Act
        val stats = rateLimiter.getStats(vehicleId)

        // Assert
        assertThat(stats.vehicleId).isEqualTo(vehicleId)
        assertThat(stats.updatesInWindow).isEqualTo(20)
        assertThat(stats.maxUpdatesAllowed).isEqualTo(60)
        assertThat(stats.remainingQuota).isEqualTo(40)
        assertThat(stats.isRateLimited).isFalse()
    }

    @Test
    fun shouldMarkAsRateLimited_WhenLimitExceeded() {
        // Arrange
        val vehicleId = "v-limited"
        repeat(60) { rateLimiter.isAllowed(vehicleId) }
        rateLimiter.isAllowed(vehicleId)

        // Act
        val stats = rateLimiter.getStats(vehicleId)

        // Assert
        assertThat(stats.isRateLimited).isTrue()
        assertThat(stats.remainingQuota).isEqualTo(0)
    }

    @Test
    fun shouldResetVehicle_WhenCleanupCalled() {
        // Arrange
        val vehicleId = "v-reset"
        repeat(20) { rateLimiter.isAllowed(vehicleId) }

        // Act
        rateLimiter.resetVehicle(vehicleId)

        // Assert
        assertThat(rateLimiter.isAllowed(vehicleId)).isTrue()
        assertThat(rateLimiter.getRemainingQuota(vehicleId)).isEqualTo(59)
    }

    @Test
    fun shouldRespectCustomLimit_WhenLimiterCreatedWithCustomMax() {
        // Arrange
        val customLimiter = LocationUpdateRateLimiter(maxUpdatesPerMinute = 10)
        val vehicleId = "v-custom"
        repeat(10) { assertThat(customLimiter.isAllowed(vehicleId)).isTrue() }

        // Act
        val result = customLimiter.isAllowed(vehicleId)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun shouldAllowOnlyMaxRequests_WhenRapidFiring() {
        // Arrange
        val vehicleId = "v-rapid"
        var allowed = 0
        var rejected = 0

        // Act
        repeat(100) {
            if (rateLimiter.isAllowed(vehicleId)) allowed++ else rejected++
        }

        // Assert
        assertThat(allowed).isEqualTo(60)
        assertThat(rejected).isEqualTo(40)
    }

    @Test
    fun shouldTrackEachVehicleQuota_WhenMultipleVehicles() {
        // Arrange
        val vehicles = (1..10).map { "v-$it" }
        for (vehicle in vehicles) {
            repeat(50) { rateLimiter.isAllowed(vehicle) }
        }

        // Act / Assert
        for (vehicle in vehicles) {
            assertThat(rateLimiter.getRemainingQuota(vehicle)).isEqualTo(10)
        }
    }
}

