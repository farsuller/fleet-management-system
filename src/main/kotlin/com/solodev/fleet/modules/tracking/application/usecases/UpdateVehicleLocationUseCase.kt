package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.application.dto.SensorPing
import com.solodev.fleet.modules.tracking.application.dto.VehicleRouteState
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import com.solodev.fleet.modules.tracking.infrastructure.metrics.SpatialMetrics
import com.solodev.fleet.modules.tracking.infrastructure.persistence.LocationHistoryRepository
import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.modules.tracking.infrastructure.websocket.RedisDeltaBroadcaster
import java.util.UUID

class UpdateVehicleLocationUseCase(
    private val postGISAdapter: PostGISAdapter,
    private val broadcaster: RedisDeltaBroadcaster,
    private val metrics: SpatialMetrics,
    private val historyRepository: LocationHistoryRepository
) {
    suspend fun execute(ping: SensorPing) {
        val startTime = System.currentTimeMillis()

        // 1. Snap to route using PostGIS
        val routeId = ping.routeId?.let { UUID.fromString(it) }
            ?: throw IllegalArgumentException("Route ID required in SensorPing")

        val snapResult = postGISAdapter.snapToRoute(
            location = ping.location,
            routeId = routeId
        ) ?: throw IllegalStateException("Failed to snap point to route")

        // 2. Determine if off-route (> 100m tolerance)
        val distanceFromRoute = snapResult.second * 1000.0 // Convert progress to meters estimate
        val isOffRoute = distanceFromRoute > 100.0

        val status = when {
            isOffRoute -> VehicleStatus.OFF_ROUTE
            ping.speed == null || ping.speed < 5.0 -> VehicleStatus.IDLE
            else -> VehicleStatus.IN_TRANSIT
        }

        // 3. Create state
        val vehicleIdString = ping.vehicleId
        val routeIdString = routeId.toString()
        val state = VehicleRouteState(
            vehicleId = vehicleIdString,
            routeId = routeIdString,
            progress = snapResult.second,
            segmentId = "", // Extract from route geometry if available
            speed = ping.speed ?: 0.0,
            heading = ping.heading ?: 0.0,
            status = status,
            distanceFromRoute = distanceFromRoute,
            latitude = ping.location.latitude,
            longitude = ping.location.longitude,
            timestamp = ping.timestamp
        )

        // 4. Persist to database
        historyRepository.saveTrackingRecord(state)

        // 5. Broadcast delta
        broadcaster.broadcastIfChanged(UUID.fromString(vehicleIdString), state)

        // 6. Record metrics
        val duration = System.currentTimeMillis() - startTime
        metrics.recordSnapDuration(duration)
    }
}