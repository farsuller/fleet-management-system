package com.solodev.fleet.modules.tracking.application.dto

import java.time.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VehicleRouteStateTest {
    @Test
    fun `isOffRoute() should return true when distance exceeds tolerance`() {
        val state = VehicleRouteState(
            vehicleId = "v-123",
            routeId = "route-456",
            progress = 0.42,
            segmentId = "seg-1",
            speed = 30.0,
            heading = 180.0,
            status = VehicleStatus.IN_TRANSIT,
            distanceFromRoute = 150.0,
            latitude = 14.5995,
            longitude = 121.0244,
            timestamp = Instant.now()
        )

        assertTrue(state.isOffRoute(toleranceMeters = 100.0))
    }

    @Test
    fun `isOffRoute() should return true when status is OFF_ROUTE`() {
        val state = VehicleRouteState(
            vehicleId = "v-123",
            routeId = "route-456",
            progress = 0.42,
            segmentId = "seg-1",
            speed = 30.0,
            heading = 180.0,
            status = VehicleStatus.OFF_ROUTE,
            distanceFromRoute = 50.0,
            latitude = 14.5995,
            longitude = 121.0244,
            timestamp = Instant.now()
        )

        assertTrue(state.isOffRoute(toleranceMeters = 100.0))
    }

    @Test
    fun `isOffRoute() should return false when within tolerance`() {
        val state = VehicleRouteState(
            vehicleId = "v-123",
            routeId = "route-456",
            progress = 0.42,
            segmentId = "seg-1",
            speed = 30.0,
            heading = 180.0,
            status = VehicleStatus.IN_TRANSIT,
            distanceFromRoute = 50.0,
            latitude = 14.5995,
            longitude = 121.0244,
            timestamp = Instant.now()
        )

        assertFalse(state.isOffRoute(toleranceMeters = 100.0))
    }
}

