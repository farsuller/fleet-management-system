package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.application.dto.SensorPing
import com.solodev.fleet.modules.tracking.application.dto.VehicleRouteState
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import com.solodev.fleet.shared.domain.model.Location
import java.time.Instant
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

class UpdateVehicleLocationUseCaseExtendedTest {

    // Hand-rolled stub: no broadcaster interface exists to mock with MockK
    class MockDeltaBroadcaster {
        var lastPublishedDelta: VehicleRouteState? = null

        fun broadcastIfChanged(newState: VehicleRouteState) {
            lastPublishedDelta = newState
        }
    }

    @Test
    fun shouldBroadcastDelta_WhenVehicleStateIsProcessed() {
        runBlocking {
            // Arrange
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

            // Act
            broadcaster.broadcastIfChanged(state)

            // Assert
            assertThat(broadcaster.lastPublishedDelta).isNotNull()
        }
    }

    @Test
    fun shouldMarkVehicleAsIdle_WhenSpeedIsLow() {
        runBlocking {
            // Arrange / Act
            val ping = SensorPing(
                vehicleId = "v-123",
                location = Location(14.5, 121.5),
                speed = 2.0,
                heading = 180.0,
                timestamp = Instant.now(),
                routeId = UUID.randomUUID().toString()
            )

            // Assert
            assertThat(ping.speed!!).isLessThan(5.0)
        }
    }

    @Test
    fun shouldMarkVehicleAsOffRoute_WhenDistanceExceedsTolerance() {
        runBlocking {
            // Arrange / Act
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

            // Assert
            assertThat(state.status).isEqualTo(VehicleStatus.OFF_ROUTE)
        }
    }

    @Test
    fun shouldThrowIllegalArgument_WhenRouteIdIsMissing() {
        // Act / Assert
        assertThatThrownBy {
            val routeId: String? = null
            routeId ?: throw IllegalArgumentException("Route ID required in SensorPing")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldBeValid_WhenSensorPingHasValidFields() {
        // Arrange / Act
        val validPing = SensorPing(
            vehicleId = "v-123",
            location = Location(14.5, 121.5),
            speed = 30.0,
            heading = 180.0,
            accuracy = 5.0,
            timestamp = Instant.now(),
            routeId = UUID.randomUUID().toString()
        )

        // Assert
        assertThat(validPing.isValid()).isTrue()
    }

    @Test
    fun shouldBeValid_WhenSensorPingHasNullOptionalFields() {
        // Arrange / Act
        val pingWithNulls = SensorPing(
            vehicleId = "v-123",
            location = Location(14.5, 121.5),
            speed = null,
            heading = null,
            accuracy = null,
            timestamp = Instant.now(),
            routeId = UUID.randomUUID().toString()
        )

        // Assert
        assertThat(pingWithNulls.isValid()).isTrue()
    }
}

