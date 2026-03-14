package com.solodev.fleet.modules.tracking.infrastructure.http

import com.solodev.fleet.modules.tracking.application.dto.*
import com.solodev.fleet.modules.tracking.application.usecases.*
import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.modules.tracking.infrastructure.persistence.LocationHistoryRepository
import com.solodev.fleet.modules.tracking.infrastructure.websocket.RedisDeltaBroadcaster
import com.solodev.fleet.modules.tracking.infrastructure.ratelimit.LocationUpdateRateLimiter
import com.solodev.fleet.modules.tracking.infrastructure.idempotency.IdempotencyKeyManager
import com.solodev.fleet.modules.tracking.infrastructure.resilience.CircuitBreaker
import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.auth.jwt.JWTPrincipal
import com.solodev.fleet.shared.plugins.UserRole
import com.solodev.fleet.shared.plugins.Authorization
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.models.PaginationParams
import kotlinx.serialization.json.*

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
    val licensePlate: String = "",
    val make: String = "",
    val model: String = "",
    val routeId: String? = null,
    val status: String,
    val speed: Double,
    val progress: Double,
    val distanceFromRoute: Double,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val heading: Double = 0.0,
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

/** Request body for creating a route from a GeoJSON string. */
@Serializable
data class CreateRouteRequest(
    val name: String,
    val description: String? = null,
    /** Raw GeoJSON — Feature, FeatureCollection, or bare LineString geometry. */
    val geojson: String,
)

/**
 * Converts a GeoJSON string to a WKT LINESTRING.
 * Accepts Feature, FeatureCollection (uses first feature), or bare LineString geometry.
 * Returns null if no valid LineString can be extracted.
 */
private fun geoJsonLineStringToWkt(geojson: String): String? = runCatching {
    val root = Json.parseToJsonElement(geojson).jsonObject
    val geometry: JsonObject? = when (root["type"]?.jsonPrimitive?.content) {
        "FeatureCollection" -> root["features"]?.jsonArray?.firstOrNull()?.jsonObject?.get("geometry")?.jsonObject
        "Feature"           -> root["geometry"]?.jsonObject
        "LineString"        -> root
        else                -> null
    }
    if (geometry?.get("type")?.jsonPrimitive?.content != "LineString") return@runCatching null
    val coords = geometry["coordinates"]?.jsonArray ?: return@runCatching null
    val wktCoords = coords.joinToString(", ") { pt ->
        val arr = pt.jsonArray
        "${arr[0].jsonPrimitive.double} ${arr[1].jsonPrimitive.double}"
    }
    "LINESTRING($wktCoords)"
}.getOrNull()

/** API routes for vehicle tracking and spatial features. */
fun Route.trackingRoutes(
    updateVehicleLocation: UpdateVehicleLocationUseCase,
    spatialAdapter: PostGISAdapter,
    deltaBroadcaster: RedisDeltaBroadcaster,
    vehicleRepository: VehicleRepository,
    historyRepository: LocationHistoryRepository = LocationHistoryRepository(),
    receptionService: CoordinateReceptionService,
    rateLimiter: LocationUpdateRateLimiter = LocationUpdateRateLimiter(maxUpdatesPerMinute = 60),
    idempotencyManager: IdempotencyKeyManager = IdempotencyKeyManager(ttlMinutes = 24 * 60),
    circuitBreaker: CircuitBreaker = CircuitBreaker("LocationUpdate", failureThreshold = 5)
) {
    route("/v1/tracking") {
        get("/routes/active") {
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
                        updateVehicleLocation.execute(
                            UpdateVehicleLocationCommand(
                                vehicleId = vehicleId,
                                latitude = dto.latitude,
                                longitude = dto.longitude,
                                speed = dto.speed,
                                heading = dto.heading,
                                accuracy = dto.accuracy,
                                routeId = dto.routeId,
                                recordedAt = Instant.now()
                            )
                        )
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

            // Phase 7: Get fleet real-time status — joins all vehicles with latest tracking state
            get("/fleet/status") {
                val allLatestStates = historyRepository.getAllLatestVehicleStates()
                val stateByVehicleId = allLatestStates.associateBy { it.vehicleId }
                val (allVehicles, _) = vehicleRepository.findAll(PaginationParams(limit = 500, cursor = null))

                val vehicleSummaries = allVehicles.map { vehicle ->
                    val state = stateByVehicleId[vehicle.id.value]
                    VehicleStatusSummary(
                        vehicleId         = vehicle.id.value,
                        licensePlate      = vehicle.licensePlate,
                        make              = vehicle.make,
                        model             = vehicle.model,
                        routeId           = state?.routeId,
                        status            = state?.status?.name ?: "OFFLINE",
                        speed             = state?.speed ?: 0.0,
                        progress          = state?.progress ?: 0.0,
                        distanceFromRoute = state?.distanceFromRoute ?: 0.0,
                        latitude          = state?.latitude ?: 0.0,
                        longitude         = state?.longitude ?: 0.0,
                        heading           = state?.heading ?: 0.0,
                        timestamp         = state?.timestamp?.toString() ?: "",
                    )
                }

                val activeCount = vehicleSummaries.count {
                    it.status == "IN_TRANSIT" || it.status == "IDLE"
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse.success(
                        FleetStatusResponse(
                            totalVehicles  = allVehicles.size,
                            activeVehicles = activeCount,
                            vehicles       = vehicleSummaries,
                        ),
                        call.requestId
                    )
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

    // ── Route Management ──────────────────────────────────────────────────────

    /**
     * POST /v1/tracking/routes
     * Accepts a GeoJSON LineString (Feature, FeatureCollection, or bare geometry)
     * and saves it as a route in the PostGIS `routes` table.
     */
    authenticate("auth-jwt") {
        route("/v1/tracking/routes") {
            post {
                try {
                    val body = call.receive<CreateRouteRequest>()

                    val wkt = geoJsonLineStringToWkt(body.geojson)
                        ?: return@post call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ApiResponse.error(
                                "INVALID_GEOJSON",
                                "GeoJSON must contain a LineString geometry",
                                call.requestId,
                            )
                        )

                    val route = spatialAdapter.createRoute(
                        name        = body.name,
                        description = body.description,
                        wktLineString = wkt,
                    )
                    call.respond(HttpStatusCode.Created, ApiResponse.success(route, call.requestId))
                } catch (e: Exception) {
                    logger.error("Error creating route", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse.error("ROUTE_CREATE_ERROR", e.message ?: "Failed to create route", call.requestId)
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

        // ── Driver sensor batch ping ─────────────────────────────────────────────────
        route("/v1/sensors/ping") {
            post {
                // Check coordinate reception toggle
                if (!receptionService.isReceptionEnabled()) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ApiResponse.error(
                            "COORDINATE_RECEPTION_DISABLED",
                            "Coordinate reception is currently disabled",
                            call.requestId,
                        )
                    )
                    return@post
                }

                // Batch processing logic
                val pings = call.receive<List<SensorPing>>()
                if (pings.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("EMPTY_BATCH", "Ping batch must not be empty", call.requestId)
                    )
                    return@post
                }

                // Idempotency check
                val idempotencyKey = call.request.headers["Idempotency-Key"]
                if (idempotencyKey != null) {
                    val cached = idempotencyManager.getCachedResponse(idempotencyKey)
                    if (cached != null) {
                        call.respond(HttpStatusCode.fromValue(cached.httpStatus), cached.responseBody)
                        return@post
                    }
                }

                var accepted = 0
                var rejected = 0

                pings.forEach { ping ->
                    if (!ping.isValid()) {
                        rejected++
                        return@forEach
                    }

                    val lat = ping.resolvedLatitude() ?: run { rejected++; return@forEach }
                    val lon = ping.resolvedLongitude() ?: run { rejected++; return@forEach }

                    try {
                        updateVehicleLocation.execute(
                            UpdateVehicleLocationCommand(
                                vehicleId = ping.vehicleId,
                                latitude = lat,
                                longitude = lon,
                                speed = ping.speed,
                                heading = ping.heading,
                                accuracy = ping.accuracy,
                                routeId = ping.routeId,
                                accelX = ping.accelX,
                                accelY = ping.accelY,
                                accelZ = ping.accelZ,
                                gyroX = ping.gyroX,
                                gyroY = ping.gyroY,
                                gyroZ = ping.gyroZ,
                                batteryLevel = ping.batteryLevel,
                                harshBrake = ping.hasHarshBrake(),
                                harshAccel = ping.hasHarshAccel(),
                                sharpTurn = ping.hasSharpTurn(),
                                recordedAt = ping.timestamp,
                            )
                        )
                        accepted++
                    } catch (e: Exception) {
                        logger.warn("SensorPing failed for vehicle=${ping.vehicleId}: ${e.message}")
                        rejected++
                    }
                }

                val response = ApiResponse.success(
                    SensorPingBatchResponse(accepted, rejected),
                    call.requestId
                )

                if (idempotencyKey != null) {
                    idempotencyManager.recordRequest(
                        idempotencyKey,
                        Json.encodeToString(ApiResponse.serializer(SensorPingBatchResponse.serializer()), response),
                        HttpStatusCode.Accepted.value
                    )
                }

                call.respond(HttpStatusCode.Accepted, response)
            }
        }

        // Phase 4: Admin Coordinate Reception Control
        route("/v1/tracking/admin/coordinate-reception") {
            install(Authorization) {
                requiredRoles = listOf(UserRole.ADMIN, UserRole.FLEET_MANAGER)
            }

            get {
                val status = receptionService.getStatus()
                call.respond(ApiResponse.success<CoordinateReceptionStatus>(status, call.requestId))
            }

            post {
                try {
                    val request = call.receive<CoordinateReceptionRequest>()
                    val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("id")?.asString() ?: "unknown"
                    val status = receptionService.setReceptionEnabled(request.enabled, userId)
                    call.respond(ApiResponse.success<CoordinateReceptionStatus>(status, call.requestId))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("TOGGLE_ERROR", "Failed to toggle reception: ${e.message}", call.requestId)
                    )
                }
            }
        }
    }
}
