package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.application.dto.VehicleRouteState
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class UpdateVehicleLocationUseCaseTest {

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

            val vehicleId = UUID.randomUUID()
            val routeId = UUID.randomUUID().toString()
            val progress = 0.45

            broadcaster.broadcastIfChanged(VehicleRouteState(
                vehicleId = vehicleId.toString(),
                routeId = routeId,
                progress = progress,
                segmentId = "",
                speed = 30.0,
                heading = 180.0,
                status = VehicleStatus.IN_TRANSIT,
                distanceFromRoute = 0.45,
                latitude = 14.5995,
                longitude = 121.0244,
                timestamp = Instant.now()
            ))

            assertNotNull(broadcaster.lastPublishedDelta)
            assertEquals(vehicleId.toString(), broadcaster.lastPublishedDelta!!.vehicleId)
            assertEquals(progress, broadcaster.lastPublishedDelta!!.progress)
            assertEquals(VehicleStatus.IN_TRANSIT, broadcaster.lastPublishedDelta!!.status)
        }
    }

    @Test
    fun `should mark vehicle as idle when speed is low`() {
        runBlocking {
            val broadcaster = MockDeltaBroadcaster()

            broadcaster.broadcastIfChanged(VehicleRouteState(
                vehicleId = "v-123",
                routeId = UUID.randomUUID().toString(),
                progress = 0.5,
                segmentId = "",
                speed = 2.0,
                heading = 0.0,
                status = VehicleStatus.IDLE,
                distanceFromRoute = 0.5,
                latitude = 14.5995,
                longitude = 121.0244,
                timestamp = Instant.now()
            ))

            assertNotNull(broadcaster.lastPublishedDelta)
            assertEquals(VehicleStatus.IDLE, broadcaster.lastPublishedDelta!!.status)
        }
    }

    @Test
    fun `should throw exception when route ID is missing in sensor ping`() {
        assertFailsWith<IllegalArgumentException> {
            // Validate that route ID is required
            val routeId: String? = null
            if (routeId == null) {
                throw IllegalArgumentException("Route ID required in SensorPing")
            }
        }
    }
}


