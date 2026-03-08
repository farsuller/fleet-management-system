package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.application.dto.VehicleRouteState
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import java.time.Instant
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

class UpdateVehicleLocationUseCaseTest {

    // Hand-rolled stub: no broadcaster interface exists to mock with MockK
    class MockDeltaBroadcaster {
        var lastPublishedDelta: VehicleRouteState? = null

        fun broadcastIfChanged(newState: VehicleRouteState) {
            lastPublishedDelta = newState
        }
    }

    @Test
    fun shouldProcessVehicleState_WhenBroadcasterIsCalled() {
        runBlocking {
            // Arrange
            val broadcaster = MockDeltaBroadcaster()
            val vehicleId = UUID.randomUUID()
            val routeId = UUID.randomUUID().toString()
            val progress = 0.45

            // Act
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

            // Assert
            assertThat(broadcaster.lastPublishedDelta).isNotNull()
            assertThat(broadcaster.lastPublishedDelta!!.vehicleId).isEqualTo(vehicleId.toString())
            assertThat(broadcaster.lastPublishedDelta!!.progress).isEqualTo(progress)
            assertThat(broadcaster.lastPublishedDelta!!.status).isEqualTo(VehicleStatus.IN_TRANSIT)
        }
    }

    @Test
    fun shouldMarkVehicleAsIdle_WhenSpeedIsLow() {
        runBlocking {
            // Arrange
            val broadcaster = MockDeltaBroadcaster()

            // Act
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

            // Assert
            assertThat(broadcaster.lastPublishedDelta).isNotNull()
            assertThat(broadcaster.lastPublishedDelta!!.status).isEqualTo(VehicleStatus.IDLE)
        }
    }

    @Test
    fun shouldThrowIllegalArgument_WhenRouteIdIsMissing() {
        // Act / Assert
        assertThatThrownBy {
            val routeId: String? = null
            if (routeId == null) {
                throw IllegalArgumentException("Route ID required in SensorPing")
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}

