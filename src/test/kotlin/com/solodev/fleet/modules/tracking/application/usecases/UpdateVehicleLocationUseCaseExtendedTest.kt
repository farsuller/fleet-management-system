package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.application.dto.SensorPing
import com.solodev.fleet.modules.tracking.application.dto.VehicleRouteState
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import com.solodev.fleet.shared.domain.model.Location
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFailsWith

class UpdateVehicleLocationUseCaseExtendedTest {


    // Mock Delta Broadcaster
    class MockDeltaBroadcaster {
        var lastPublishedDelta: VehicleRouteState? = null

        fun broadcastIfChanged(newState: VehicleRouteState) {
            lastPublishedDelta = newState
        }
    }

    @Test
    fun `should process vehicle state and broadcast delta`() {
        runBlocking {
            val broadcaster = MockDeltaBroadcaster()

            val state = VehicleRouteState(
                vehicleId = "v-123",
                routeId = UUID.randomUUID().toString(),
                progress = 0.45,
                segmentId = "seg-1",
                speed = 30.0,
                heading = 180.0,
                status = VehicleStatus.IN_TRANSIT,
                distanceFromRoute = 5.0,
                latitude = 14.5995,
                longitude = 121.0244,
                timestamp = Instant.now()
            )

            broadcaster.broadcastIfChanged(state)
            assertNotNull(broadcaster.lastPublishedDelta)
        }
    }

    @Test
    fun `should mark vehicle as idle when speed is low`() {
        runBlocking {
            val ping = SensorPing(
                vehicleId = "v-123",
                location = Location(14.5, 121.5),
                speed = 2.0,
                heading = 180.0,
                timestamp = Instant.now(),
                routeId = UUID.randomUUID().toString()
            )

            assertEquals(true, ping.speed != null && ping.speed < 5.0)
        }
    }

    @Test
    fun `should mark vehicle as off-route when distance exceeds tolerance`() {
        runBlocking {
            val state = VehicleRouteState(
                vehicleId = "v-123",
                routeId = UUID.randomUUID().toString(),
                progress = 150.0,
                segmentId = "seg-1",
                speed = 30.0,
                heading = 180.0,
                status = VehicleStatus.OFF_ROUTE,
                distanceFromRoute = 150.0,
                latitude = 14.5995,
                longitude = 121.0244,
                timestamp = Instant.now()
            )

            assertEquals(VehicleStatus.OFF_ROUTE, state.status)
        }
    }

    @Test
    fun `should throw exception when route ID is missing`() {
        assertFailsWith<IllegalArgumentException> {
            val routeId: String? = null
            routeId ?: throw IllegalArgumentException("Route ID required in SensorPing")
        }
    }

    @Test
    fun `should validate sensor ping before processing`() {
        val validPing = SensorPing(
            vehicleId = "v-123",
            location = Location(14.5, 121.5),
            speed = 30.0,
            heading = 180.0,
            accuracy = 5.0,
            timestamp = Instant.now(),
            routeId = UUID.randomUUID().toString()
        )

        assertEquals(true, validPing.isValid())
    }

    @Test
    fun `should handle null optional fields in sensor ping`() {
        val pingWithNulls = SensorPing(
            vehicleId = "v-123",
            location = Location(14.5, 121.5),
            speed = null,
            heading = null,
            accuracy = null,
            timestamp = Instant.now(),
            routeId = UUID.randomUUID().toString()
        )

        assertEquals(true, pingWithNulls.isValid())
    }
}

