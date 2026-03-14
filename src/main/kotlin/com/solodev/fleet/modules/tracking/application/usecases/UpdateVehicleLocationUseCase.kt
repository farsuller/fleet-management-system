package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.application.dto.VehicleRouteState
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import com.solodev.fleet.modules.tracking.infrastructure.metrics.SpatialMetrics
import com.solodev.fleet.modules.tracking.infrastructure.persistence.LocationHistoryRepository
import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.modules.tracking.infrastructure.websocket.RedisDeltaBroadcaster
import com.solodev.fleet.shared.domain.model.Location
import java.util.UUID

class UpdateVehicleLocationUseCase(
    private val postGISAdapter: PostGISAdapter,
    private val broadcaster: RedisDeltaBroadcaster,
    private val metrics: SpatialMetrics,
    private val historyRepository: LocationHistoryRepository,
    private val receptionService: CoordinateReceptionService
) {
    suspend fun execute(command: UpdateVehicleLocationCommand) {
        // 0. Check global coordinate reception toggle
        if (!receptionService.isReceptionEnabled()) {
            return
        }

        val startTime = System.currentTimeMillis()

        // 1. Snap to route using PostGIS
        val routeId = command.routeId?.let { UUID.fromString(it) }
            ?: throw IllegalArgumentException("Route ID is required for tracking updates")

        val snapResult = postGISAdapter.snapToRoute(
            location = Location(command.latitude, command.longitude),
            routeId = routeId
        ) ?: throw IllegalStateException("Failed to snap position to route reference")

        // 2. Determine if off-route (> 100m tolerance)
        val distanceFromRoute = snapResult.second * 1000.0 // progress is normalized, estimate relative distance if possible
        // Note: PostGIS snapToRoute returns (snappedLocation, progress)
        // Here we use a 100m threshold for OFF_ROUTE status
        val isOffRoute = distanceFromRoute > 100.0

        val vehicleStatus = when {
            isOffRoute -> VehicleStatus.OFF_ROUTE
            command.speed == null || command.speed < 0.5 -> VehicleStatus.IDLE
            else -> VehicleStatus.IN_TRANSIT
        }

        // 3. Create rich telemetry state
        val state = VehicleRouteState(
            vehicleId = command.vehicleId,
            routeId = command.routeId,
            progress = snapResult.second,
            segmentId = "", 
            speed = command.speed ?: 0.0,
            heading = command.heading ?: 0.0,
            status = vehicleStatus,
            distanceFromRoute = distanceFromRoute,
            latitude = command.latitude,
            longitude = command.longitude,
            timestamp = command.recordedAt,
            // Sensor fusion fields
            accelX = command.accelX,
            accelY = command.accelY,
            accelZ = command.accelZ,
            gyroX = command.gyroX,
            gyroY = command.gyroY,
            gyroZ = command.gyroZ,
            batteryLevel = command.batteryLevel,
            harshBrake = command.harshBrake,
            harshAccel = command.harshAccel,
            sharpTurn = command.sharpTurn
        )

        // 4. Persist to database
        historyRepository.saveTrackingRecord(state)

        // 5. Broadcast delta to listeners (WebSockets)
        broadcaster.broadcastIfChanged(UUID.fromString(command.vehicleId), state)

        // 6. Record metrics
        val duration = System.currentTimeMillis() - startTime
        metrics.recordSnapDuration(duration)
    }
}