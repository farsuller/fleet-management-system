package com.solodev.fleet.modules.maintenance.infrastructure.http

import com.solodev.fleet.modules.maintenance.application.dto.IncidentResponse
import com.solodev.fleet.modules.maintenance.application.dto.ReportIncidentRequest
import com.solodev.fleet.modules.maintenance.application.usecases.ListIncidentsUseCase
import com.solodev.fleet.modules.maintenance.application.usecases.ReportIncidentUseCase
import com.solodev.fleet.modules.maintenance.domain.model.IncidentId
import com.solodev.fleet.modules.maintenance.domain.model.IncidentSeverity
import com.solodev.fleet.modules.maintenance.domain.model.IncidentStatus
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.incidentRoutes(
    maintenanceRepository: MaintenanceRepository,
    vehicleRepository: com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
) {
    val reportIncidentUseCase = ReportIncidentUseCase(maintenanceRepository)
    val listIncidentsUseCase = ListIncidentsUseCase(maintenanceRepository)

    authenticate("auth-jwt") {
        route("/v1/vehicles") {
            // New endpoint for Driver App: POST /api/v1/vehicles/{vehiclePlate}/incidents
            post("/{plate}/incidents") {
                val plate = call.parameters["plate"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("MISSING_PLATE", "Vehicle plate required", call.requestId)
                )

                try {
                    val request = call.receive<ReportIncidentRequest>()
                    val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()?.let { UUID.fromString(it) }
                    
                    val severity = try {
                        IncidentSeverity.valueOf(request.severity.uppercase())
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("INVALID_SEVERITY", "Unknown severity: ${request.severity}", call.requestId)
                        )
                    }

                    reportIncidentUseCase(
                        vehiclePlate = plate,
                        title = request.title,
                        description = request.description,
                        severity = severity,
                        reportedByUserId = userId,
                        odometerKm = request.odometerKm,
                        latitude = request.latitude,
                        longitude = request.longitude
                    ).onSuccess { incident ->
                        call.respond(HttpStatusCode.Created, ApiResponse.success(IncidentResponse.fromDomain(incident, plate), call.requestId))
                    }.onFailure { e ->
                        call.respond(HttpStatusCode.BadRequest, ApiResponse.error("REPORT_FAILED", e.message ?: "Failed to report incident", call.requestId))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error("INVALID_REQUEST", e.message ?: "Invalid request", call.requestId))
                }
            }

            // GET /api/v1/vehicles/{id}/incidents
            get("/{id}/incidents") {
                val id = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("MISSING_ID", "Vehicle ID required", call.requestId)
                )
                
                val vehicle = vehicleRepository.findById(com.solodev.fleet.modules.vehicles.domain.model.VehicleId(id))
                val plate = vehicle?.licensePlate
                
                val incidents = listIncidentsUseCase.getAllByVehicle(id)
                call.respond(ApiResponse.success(incidents.map { IncidentResponse.fromDomain(it, plate) }, call.requestId))
            }
        }

        // GET /v1/incidents — global list, optionally filtered by status
        route("/v1/incidents") {
            get {
                val statusParam = call.request.queryParameters["status"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val cursor = call.request.queryParameters["cursor"]

                val status = statusParam?.let { s ->
                    try { IncidentStatus.valueOf(s.uppercase()) }
                    catch (e: IllegalArgumentException) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("INVALID_STATUS", "Unknown status: $s", call.requestId)
                        )
                    }
                }

                val all = listIncidentsUseCase.getAll(status)
                val startIndex = cursor?.toIntOrNull() ?: 0
                val page = all.drop(startIndex).take(limit)

                call.respond(
                    ApiResponse.success(
                        data = page.map { IncidentResponse.fromDomain(it) },
                        requestId = call.requestId
                    )
                )
            }
        }
    }
}

