package com.solodev.fleet.modules.tracking.infrastructure.spatial

import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.domain.model.RouteSnapResult
import java.time.Instant
import java.util.UUID
import kotlin.compareTo
import kotlin.toString

class MatchingEngine(private val postGISAdapter: PostGISAdapter) {
    suspend fun snapPointToRoute(routeId: UUID, vehicleId: UUID, location: Location): RouteSnapResult {
        val snapResult = postGISAdapter.snapToRoute(location =  location, routeId =  routeId)
            ?: throw IllegalStateException("Failed to snap point to route")

        return RouteSnapResult(
            vehicleId = vehicleId.toString(),
            routeId = routeId.toString(),
            progress = 0.0, // Calculate from PostGIS result if available
            segmentId = "", // Extract from PostGIS if available
            distanceFromRoute = snapResult.second,
            snapPoint = snapResult.first,
            timestamp = Instant.now(),
            isOffRoute = snapResult.second > 100.0 // 100m tolerance
        )
    }
}