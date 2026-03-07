package com.solodev.fleet.shared.domain.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class RouteSnapResult(
    val vehicleId: String,               // Vehicle UUID that was snapped
    val routeId: String,                 // Route UUID that point was snapped to
    val progress: Double,                // 0.0-1.0 (progress along route from ST_LineLocatePoint)
    val segmentId: String,               // Current road segment identifier
    val distanceFromRoute: Double,       // Meters from nearest route point (ST_Distance result)
    val snapPoint: Location,             // Snapped coordinates (ST_ClosestPoint result)
    @Contextual
    val timestamp: Instant,
    val isOffRoute: Boolean = false      // True if distance exceeds tolerance
)