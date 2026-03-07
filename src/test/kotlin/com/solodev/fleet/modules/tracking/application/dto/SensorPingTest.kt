package com.solodev.fleet.modules.tracking.application.dto

import com.solodev.fleet.shared.domain.model.Location
import java.time.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensorPingTest {
    @Test
    fun `should validate correct sensor ping data`() {
        val validPing = SensorPing(
            vehicleId = "v-123",
            location = Location(14.5, 121.5),
            speed = 30.0,
            heading = 180.0,
            accuracy = 5.0,
            timestamp = Instant.now(),
            routeId = "route-456"
        )

        assertTrue(validPing.isValid())
    }

    @Test
    fun `should reject speed greater than 100 m per s`() {
        val invalidPing = SensorPing(
            vehicleId = "v-123",
            location = Location(14.5, 121.5),
            speed = 150.0,
            heading = 180.0,
            timestamp = Instant.now(),
            routeId = "route-456"
        )

        assertFalse(invalidPing.isValid())
    }

    @Test
    fun `should reject heading greater than 360 degrees`() {
        val invalidPing = SensorPing(
            vehicleId = "v-123",
            location = Location(14.5, 121.5),
            speed = 30.0,
            heading = 400.0,
            timestamp = Instant.now(),
            routeId = "route-456"
        )

        assertFalse(invalidPing.isValid())
    }

    @Test
    fun `should reject negative accuracy`() {
        val invalidPing = SensorPing(
            vehicleId = "v-123",
            location = Location(14.5, 121.5),
            speed = 30.0,
            heading = 180.0,
            accuracy = -10.0,
            timestamp = Instant.now(),
            routeId = "route-456"
        )

        assertFalse(invalidPing.isValid())
    }

    @Test
    fun `should allow null speed heading accuracy`() {
        val validPing = SensorPing(
            vehicleId = "v-123",
            location = Location(14.5, 121.5),
            speed = null,
            heading = null,
            accuracy = null,
            timestamp = Instant.now(),
            routeId = "route-456"
        )

        assertTrue(validPing.isValid())
    }
}

