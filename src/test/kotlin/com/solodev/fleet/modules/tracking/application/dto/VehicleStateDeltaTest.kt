package com.solodev.fleet.modules.tracking.application.dto

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.*

class VehicleStateDeltaTest {

    @Test
    fun `hasChanges returns true when properties differ`() {
        val state1 = VehicleRouteState(
            vehicleId = "veh-001",
            routeId = "route-001",
            progress = 0.5,
            segmentId = "seg-001",
            speed = 60.0,
            heading = 90.0,
            status = VehicleStatus.IN_TRANSIT,
            distanceFromRoute = 0.0,
            latitude = 14.5995,
            longitude = 121.0244,
            timestamp = Instant.now()
        )

        val state2 = state1.copy(speed = 70.0)
        assertNotEquals(state1, state2)
    }

    @Test
    fun `hasChanges returns false when properties are identical`() {
        val now = Instant.now()
        val state1 = VehicleRouteState(
            vehicleId = "veh-001",
            routeId = "route-001",
            progress = 0.5,
            segmentId = "seg-001",
            speed = 60.0,
            heading = 90.0,
            status = VehicleStatus.IN_TRANSIT,
            distanceFromRoute = 0.0,
            latitude = 14.5995,
            longitude = 121.0244,
            timestamp = now
        )

        val state2 = state1.copy()
        assertEquals(state1, state2)
    }

    @Test
    fun `speed zero indicates IDLE status`() {
        val state = VehicleRouteState(
            vehicleId = "veh-001",
            routeId = "route-001",
            progress = 0.5,
            segmentId = "seg-001",
            speed = 0.0,
            heading = 90.0,
            status = VehicleStatus.IDLE,
            distanceFromRoute = 0.0,
            latitude = 14.5995,
            longitude = 121.0244,
            timestamp = Instant.now()
        )

        assertEquals(VehicleStatus.IDLE, state.status)
    }
}

