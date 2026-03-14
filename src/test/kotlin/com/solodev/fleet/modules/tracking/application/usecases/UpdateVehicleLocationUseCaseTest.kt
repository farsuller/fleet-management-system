package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.application.dto.SensorPing
import com.solodev.fleet.modules.tracking.application.usecases.UpdateVehicleLocationCommand
import com.solodev.fleet.modules.tracking.application.dto.VehicleRouteState
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import com.solodev.fleet.modules.tracking.infrastructure.metrics.SpatialMetrics
import com.solodev.fleet.modules.tracking.infrastructure.persistence.LocationHistoryRepository
import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.modules.tracking.infrastructure.websocket.RedisDeltaBroadcaster
import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class UpdateVehicleLocationUseCaseTest {

    private val postGISAdapter = mockk<PostGISAdapter>()
    private val broadcaster = mockk<RedisDeltaBroadcaster>(relaxed = true)
    private val metrics = mockk<SpatialMetrics>(relaxed = true)
    private val historyRepository = mockk<LocationHistoryRepository>(relaxed = true)
    private val receptionService = mockk<CoordinateReceptionService>()

    private lateinit var useCase: UpdateVehicleLocationUseCase

    @BeforeEach
    fun setup() {
        useCase = UpdateVehicleLocationUseCase(
            postGISAdapter,
            broadcaster,
            metrics,
            historyRepository,
            receptionService
        )
    }

    @Test
    fun `should stop execution when reception is disabled`() = runBlocking {
        // Arrange
        val command = createDummyCommand()
        coEvery { receptionService.isReceptionEnabled() } returns false

        // Act
        useCase.execute(command)

        // Assert
        coVerify(exactly = 0) { postGISAdapter.snapToRoute(any(), any()) }
        coVerify(exactly = 0) { historyRepository.saveTrackingRecord(any()) }
    }

    @Test
    fun `should process and map all sensor fields when enabled`() = runBlocking {
        // Arrange
        val routeId = UUID.randomUUID()
        val command = createDummyCommand(routeId = routeId.toString())
        
        coEvery { receptionService.isReceptionEnabled() } returns true
        every { postGISAdapter.snapToRoute(any(), any()) } returns Pair(Location(14.6, 121.1), 0.5)
        
        val capturedState = slot<VehicleRouteState>()
        coEvery { historyRepository.saveTrackingRecord(capture(capturedState)) } returns UUID.randomUUID()

        // Act
        useCase.execute(command)

        // Assert
        val state = capturedState.captured
        assertThat(state.accelX).isEqualTo(command.accelX)
        assertThat(state.gyroZ).isEqualTo(command.gyroZ)
        assertThat(state.batteryLevel).isEqualTo(command.batteryLevel)
        assertThat(state.harshBrake).isTrue() // -5.0 < -4.0
        assertThat(state.sharpTurn).isTrue()  // 2.0 > 1.5
        
        coVerify { broadcaster.broadcastIfChanged(any(), any()) }
        coVerify { metrics.recordSnapDuration(any()) }
    }

    @Test
    fun `should mark vehicle as OFF_ROUTE when distance exceeds 100m`() = runBlocking {
        // Arrange
        val command = createDummyCommand()
        coEvery { receptionService.isReceptionEnabled() } returns true
        // 0.15 normalized progress * 1000.0 estimate = 150m (simplified)
        // Wait, the use case code says: val distanceFromRoute = snapResult.second * 1000.0
        // So snapResult.second (progress) 0.15 -> 150m
        every { postGISAdapter.snapToRoute(any(), any()) } returns Pair(Location(14.6, 121.1), 0.15)
        
        val capturedState = slot<VehicleRouteState>()
        coEvery { historyRepository.saveTrackingRecord(capture(capturedState)) } returns UUID.randomUUID()

        // Act
        useCase.execute(command)

        // Assert
        assertThat(capturedState.captured.status).isEqualTo(VehicleStatus.OFF_ROUTE)
    }

    @Test
    fun `should mark vehicle as IDLE when speed is low`() = runBlocking {
        // Arrange
        val command = createDummyCommand(speed = 0.2)
        coEvery { receptionService.isReceptionEnabled() } returns true
        every { postGISAdapter.snapToRoute(any(), any()) } returns Pair(Location(14.6, 121.1), 0.05)
        
        val capturedState = slot<VehicleRouteState>()
        coEvery { historyRepository.saveTrackingRecord(capture(capturedState)) } returns UUID.randomUUID()

        // Act
        useCase.execute(command)

        // Assert
        assertThat(capturedState.captured.status).isEqualTo(VehicleStatus.IDLE)
    }

    @Test
    fun `should throw exception when routeId is missing`() = runBlocking {
        // Arrange
        val command = createDummyCommand(routeId = null)
        coEvery { receptionService.isReceptionEnabled() } returns true

        // Act & Assert
        try {
            useCase.execute(command)
            assertThat(false).describedAs("Should have thrown IllegalArgumentException").isTrue()
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Route ID is required")
        }
    }

    private fun createDummyCommand(
        routeId: String? = UUID.randomUUID().toString(),
        speed: Double = 10.0
    ) = UpdateVehicleLocationCommand(
        vehicleId = UUID.randomUUID().toString(),
        latitude = 14.5,
        longitude = 121.0,
        speed = speed,
        heading = 90.0,
        recordedAt = Instant.now(),
        routeId = routeId,
        accelX = -5.0,
        gyroZ = 2.0,
        batteryLevel = 85,
        harshBrake = true,
        sharpTurn = true
    )
}
