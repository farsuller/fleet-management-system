package com.solodev.fleet.modules.tracking.application.dto

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals

class VehicleStateDeltaExtensionsTest {
    @Test
    fun `full() should include all fields`() {
        val vehicleUuid = UUID.randomUUID().toString()
        val state = VehicleRouteState(
            vehicleId = vehicleUuid,
            routeId = UUID.randomUUID().toString(),
            progress = 0.42,
            segmentId = "seg-1",
            speed = 30.0,
            heading = 180.0,
            status = VehicleStatus.IN_TRANSIT,
            distanceFromRoute = 5.0,
            latitude = 14.5995,
            longitude = 121.0244,
            timestamp = Instant.now()
        )

        val delta = VehicleStateDelta.full(state)

        assertEquals(UUID.fromString(vehicleUuid), delta.vehicleId)
        assertEquals(0.42, delta.routeProgress)
        assertEquals(180.0, delta.headingDeg)
        assertEquals(14.5995, delta.latitude)
        assertEquals(121.0244, delta.longitude)
        assertEquals(VehicleStatus.IN_TRANSIT, delta.status)
        assertEquals(5.0, delta.distanceFromRoute)
        assertNotNull(delta.timestamp)
    }

    @Test
    fun `diff() should only include changed fields`() {
        val vehicleUuid = UUID.randomUUID().toString()
        val lastState = VehicleRouteState(
            vehicleId = vehicleUuid,
            routeId = UUID.randomUUID().toString(),
            progress = 0.40,
            segmentId = "seg-1",
            speed = 30.0,
            heading = 180.0,
            status = VehicleStatus.IN_TRANSIT,
            distanceFromRoute = 5.0,
            latitude = 14.5995,
            longitude = 121.0244,
            timestamp = Instant.now()
        )

        val newState = lastState.copy(progress = 0.45)
        val delta = VehicleStateDelta.diff(lastState, newState)

        assertEquals(0.45, delta.routeProgress)
        assertNull(delta.headingDeg)
        assertNull(delta.latitude)
        assertNull(delta.longitude)
        assertNull(delta.status)
        assertNull(delta.distanceFromRoute)
    }

    @Test
    fun `diff() should return null fields when state unchanged`() {
        val vehicleUuid = UUID.randomUUID().toString()
        val state = VehicleRouteState(
            vehicleId = vehicleUuid,
            routeId = UUID.randomUUID().toString(),
            progress = 0.42,
            segmentId = "seg-1",
            speed = 30.0,
            heading = 180.0,
            status = VehicleStatus.IN_TRANSIT,
            distanceFromRoute = 5.0,
            latitude = 14.5995,
            longitude = 121.0244,
            timestamp = Instant.now()
        )

        val delta = VehicleStateDelta.diff(state, state)

        assertNull(delta.routeProgress)
        assertNull(delta.headingDeg)
        assertNull(delta.latitude)
        assertNull(delta.longitude)
        assertNull(delta.status)
        assertNull(delta.distanceFromRoute)
        assertFalse(delta.hasChanges())
    }
}

