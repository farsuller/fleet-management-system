package com.solodev.fleet.modules.tracking.infrastructure.http

import com.solodev.fleet.modules.tracking.application.dto.LocationUpdateDTO
import com.solodev.fleet.modules.tracking.application.dto.SensorPing
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import com.solodev.fleet.modules.tracking.application.usecases.UpdateVehicleLocationUseCase
import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.modules.tracking.infrastructure.persistence.LocationHistoryRepository
import com.solodev.fleet.modules.tracking.infrastructure.websocket.RedisDeltaBroadcaster
import com.solodev.fleet.modules.tracking.infrastructure.ratelimit.LocationUpdateRateLimiter
import com.solodev.fleet.modules.tracking.infrastructure.idempotency.IdempotencyKeyManager
import com.solodev.fleet.modules.tracking.infrastructure.resilience.CircuitBreaker
import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.Instant
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TrackingRoutes")

/**
 * Response DTO for location update endpoint.
 */
@Serializable
data class LocationUpdateResponse(
    val message: String,
    val vehicleId: String,
    val timestamp: String,
    val progress: String
)

/**
 * Response DTO for vehicle state endpoint.
 */
@Serializable
data class VehicleStateResponse(
    val vehicleId: String,
    val routeId: String? = null,
    val progress: Double = 0.0,
    val segmentId: String = "",
    val speed: Double = 0.0,
    val heading: Double = 0.0,
    val status: String = "AVAILABLE",
    val distanceFromRoute: Double = 0.0,
    val location: LocationData? = null,
    val timestamp: String
)

/**
 * Response DTO for location data.
 */
@Serializable
data class LocationData(
    val latitude: Double,
    val longitude: Double
)

/**
 * Response DTO for fleet status endpoint.
 */
@Serializable
data class FleetStatusResponse(
    val totalVehicles: Int,
    val activeVehicles: Int,
    val vehicles: List<VehicleStatusSummary>
)

/**
 * Response DTO for vehicle status summary in fleet.
 */
@Serializable
data class VehicleStatusSummary(
    val vehicleId: String,
    val routeId: String? = null,
    val status: String,
    val speed: Double,
    val progress: Double,
    val distanceFromRoute: Double,
    val timestamp: String
)

/**
 * Response DTO for tracking history endpoint.
 */
@Serializable
data class TrackingHistoryResponse(
    val vehicleId: String,
    val totalRecords: Int,
    val records: List<TrackingRecord>
)

/**
 * Response DTO for individual tracking record.
 */
@Serializable
data class TrackingRecord(
    val id: String,
    val progress: Double,
    val speed: Double,
    val heading: Double,
    val status: String,
    val distanceFromRoute: Double,
    val location: LocationData,
    val timestamp: String
)

/** API routes for vehicle tracking and spatial features. */
fun Route.trackingRoutes(
    updateVehicleLocation: UpdateVehicleLocationUseCase,
    spatialAdapter: PostGISAdapter,
    deltaBroadcaster: RedisDeltaBroadcaster,
    historyRepository: LocationHistoryRepository = LocationHistoryRepository(),
    rateLimiter: LocationUpdateRateLimiter = LocationUpdateRateLimiter(maxUpdatesPerMinute = 60),
    idempotencyManager: IdempotencyKeyManager = IdempotencyKeyManager(ttlMinutes = 24 * 60),
    circuitBreaker: CircuitBreaker = CircuitBreaker("LocationUpdate", failureThreshold = 5)
) {
    route("/v1/tracking") {
        get("/routes") {
            val routes = spatialAdapter.findAllRoutes()
            call.respond(ApiResponse.success(routes, call.requestId))
        }

        authenticate("auth-jwt") {
            post("/vehicles/{id}/location") {
                val vehicleId =
                        call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)

                // Phase 7: Per-vehicle rate limiting
                if (!rateLimiter.isAllowed(vehicleId)) {
                    val remaining = rateLimiter.getRemainingQuota(vehicleId)
                    val waitTime = rateLimiter.getWaitTimeSeconds(vehicleId)
                    logger.warn("Rate limit exceeded for vehicle $vehicleId. Wait ${waitTime}s. Quota: ${remaining}/60 per minute")
                    return@post call.respond(
                        HttpStatusCode.TooManyRequests,
                        mapOf(
                            "error" to "RATE_LIMIT_EXCEEDED",
                            "message" to "Too many location updates. Wait ${waitTime}s before retrying.",
                            "retryAfterSeconds" to waitTime
                        )
                    )
                }

                // Phase 7: Idempotency key check
                val idempotencyKey = call.request.header("Idempotency-Key")
                if (idempotencyKey != null) {
                    if (!idempotencyManager.isValidKey(idempotencyKey)) {
                        logger.warn("Invalid idempotency key format: $idempotencyKey")
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("INVALID_IDEMPOTENCY_KEY", "Idempotency key format invalid", call.requestId)
                        )
                    }

                    // Check if this is a duplicate request
                    val cached = idempotencyManager.getCachedResponse(idempotencyKey)
                    if (cached != null) {
                        logger.info("Returning cached response for idempotency key: $idempotencyKey")
                        call.respond(HttpStatusCode.fromValue(cached.httpStatus), cached.responseBody)
                        return@post
                    }
                }

                try {
                    val dto = call.receive<LocationUpdateDTO>()

                    val sensorPing = SensorPing(
                        vehicleId = vehicleId,
                        location = Location(dto.latitude, dto.longitude),
                        speed = dto.speed,
                        heading = dto.heading,
                        accuracy = dto.accuracy,
                        timestamp = Instant.now(),
                        routeId = dto.routeId
                    )

                    // Phase 7: Circuit breaker protection for PostGIS operations
                    circuitBreaker.execute {
                        updateVehicleLocation.execute(sensorPing)
                    }

                    // Return success response with tracking data
                    val response = LocationUpdateResponse(
                        message = "Location update processed successfully",
                        vehicleId = vehicleId,
                        timestamp = Instant.now().toString(),
                        progress = "Tracking active - check WebSocket for real-time updates"
                    )

                    // Phase 7: Cache response for idempotency if key provided
                    if (idempotencyKey != null) {
                        val responseJson = kotlinx.serialization.json.Json.encodeToString(
                            ApiResponse.success(response, call.requestId)
                        )
                        idempotencyManager.recordRequest(idempotencyKey, responseJson, 200)
                    }

                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse.success(response, call.requestId)
                    )
                } catch (e: Exception) {
                    logger.error("Error processing location update for vehicle $vehicleId", e)
                    val statusCode = when {
                        e.message?.contains("Circuit breaker is OPEN") == true -> HttpStatusCode.ServiceUnavailable
                        e.message?.contains("Timeout") == true -> HttpStatusCode.RequestTimeout
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respond(
                        statusCode,
                        ApiResponse.error("LOCATION_UPDATE_ERROR", e.message ?: "Failed to process location update", call.requestId)
                    )
                }
            }

            // Phase 7: Get current vehicle state
            get("/vehicles/{vehicleId}/state") {
                val vehicleId = call.parameters["vehicleId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val latestState = historyRepository.getLatestVehicleState(vehicleId)
                if (latestState == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse.error("VEHICLE_STATE_NOT_FOUND", "No tracking data found for vehicle $vehicleId", call.requestId)
                    )
                    return@get
                }

                val response = VehicleStateResponse(
                    vehicleId = latestState.vehicleId,
                    routeId = latestState.routeId,
                    progress = latestState.progress,
                    segmentId = latestState.segmentId,
                    speed = latestState.speed,
                    heading = latestState.heading,
                    status = latestState.status.name,
                    distanceFromRoute = latestState.distanceFromRoute,
                    location = LocationData(latestState.latitude, latestState.longitude),
                    timestamp = latestState.timestamp.toString()
                )

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(response, call.requestId)
                )
            }

            // Phase 7: Get fleet real-time status
            get("/fleet/status") {
                val allStates = historyRepository.getAllLatestVehicleStates()
                val activeCount = allStates.count {
                    it.status == VehicleStatus.IN_TRANSIT || it.status == VehicleStatus.IDLE
                }

                val fleetStatus = FleetStatusResponse(
                    totalVehicles = allStates.size,
                    activeVehicles = activeCount,
                    vehicles = allStates.map { state ->
                        VehicleStatusSummary(
                            vehicleId = state.vehicleId,
                            routeId = state.routeId,
                            status = state.status.name,
                            speed = state.speed,
                            progress = state.progress,
                            distanceFromRoute = state.distanceFromRoute,
                            timestamp = state.timestamp.toString()
                        )
                    }
                )

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(fleetStatus, call.requestId)
                )
            }

            // Phase 7: Get vehicle tracking history
            get("/vehicles/{vehicleId}/history") {
                val vehicleId = call.parameters["vehicleId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                try {
                    // Query actual tracking history from database
                    val records = historyRepository.getVehicleHistory(vehicleId, limit, offset)
                    val totalCount = historyRepository.getVehicleHistoryCount(vehicleId)

                    // Convert to response DTOs
                    val trackingRecords = records.map { state ->
                        TrackingRecord(
                            id = "track_${System.nanoTime()}",
                            progress = state.progress,
                            speed = state.speed,
                            heading = state.heading,
                            status = state.status.name,
                            distanceFromRoute = state.distanceFromRoute,
                            location = LocationData(state.latitude, state.longitude),
                            timestamp = state.timestamp.toString()
                        )
                    }

                    val history = TrackingHistoryResponse(
                        vehicleId = vehicleId,
                        totalRecords = totalCount.toInt(),
                        records = trackingRecords
                    )

                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse.success(history, call.requestId)
                    )
                } catch (e: Exception) {
                    logger.error("Error fetching tracking history for vehicle $vehicleId", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse.error("HISTORY_FETCH_ERROR", "Failed to fetch tracking history", call.requestId)
                    )
                }
            }
        }
    }

    // Phase 7: WebSocket Live Fleet Tracking — requires valid JWT (DRIVER or FLEET_MANAGER)
    authenticate("auth-jwt") {
        webSocket("/v1/fleet/live") {
            val sessionId = java.util.UUID.randomUUID().toString()
            deltaBroadcaster.addSession(sessionId, this)
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Ping -> send(Frame.Pong(frame.data))
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                println("WebSocket error: ${e.message}")
            } finally {
                deltaBroadcaster.removeSession(sessionId)
            }
        }
    }
}
