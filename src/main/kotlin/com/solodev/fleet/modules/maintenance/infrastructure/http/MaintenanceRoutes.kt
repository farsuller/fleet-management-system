package com.solodev.fleet.modules.maintenance.infrastructure.http

import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceRequest
import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceResponse
import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceStatusUpdateRequest
import com.solodev.fleet.modules.maintenance.application.dto.VehicleUsageHistoryDto
import com.solodev.fleet.modules.maintenance.application.usecases.CancelMaintenanceUseCase
import com.solodev.fleet.modules.maintenance.application.usecases.CompleteMaintenanceUseCase
import com.solodev.fleet.modules.maintenance.application.usecases.GetMaintenanceJobUseCase
import com.solodev.fleet.modules.maintenance.application.usecases.ListAllMaintenanceUseCase
import com.solodev.fleet.modules.maintenance.application.usecases.ListVehicleMaintenanceUseCase
import com.solodev.fleet.modules.maintenance.application.usecases.ScheduleMaintenanceUseCase
import com.solodev.fleet.modules.maintenance.application.usecases.StartMaintenanceUseCase
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceStatus
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.maintenanceRoutes(
    maintenanceRepository: MaintenanceRepository,
    rentalRepository: RentalRepository
) {
    val scheduleMaintenanceUseCase = ScheduleMaintenanceUseCase(maintenanceRepository)
    val listAllMaintenanceUseCase = ListAllMaintenanceUseCase(maintenanceRepository)
    val listVehicleMaintenanceUseCase = ListVehicleMaintenanceUseCase(maintenanceRepository)
    val getMaintenanceJobUseCase = GetMaintenanceJobUseCase(maintenanceRepository)
    val startMaintenanceUseCase = StartMaintenanceUseCase(maintenanceRepository)
    val completeMaintenanceUseCase = CompleteMaintenanceUseCase(maintenanceRepository)
    val cancelMaintenanceUseCase = CancelMaintenanceUseCase(maintenanceRepository)

    authenticate("auth-jwt") {
        route("/v1/maintenance/jobs") {
            // List all maintenance jobs (optional ?status= filter)
            get {
                val statusParam = call.request.queryParameters["status"]
                val jobs = if (statusParam != null) {
                    val status = try {
                        MaintenanceStatus.valueOf(statusParam)
                    } catch (e: IllegalArgumentException) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("INVALID_STATUS", "Unknown status: $statusParam", call.requestId)
                        )
                    }
                    listAllMaintenanceUseCase.execute(status)
                } else {
                    listAllMaintenanceUseCase.execute()
                }
                call.respond(ApiResponse.success(jobs.map { MaintenanceResponse.fromDomain(it) }, call.requestId))
            }

            post {
                try {
                    val request = call.receive<MaintenanceRequest>()
                    val job = scheduleMaintenanceUseCase.execute(request)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(MaintenanceResponse.fromDomain(job), call.requestId)
                    )
                } catch (e: Exception) {
                    val status =
                        if (e is IllegalArgumentException) HttpStatusCode.UnprocessableEntity
                        else HttpStatusCode.BadRequest
                    call.respond(
                        status,
                        ApiResponse.error(
                            "MAINTENANCE_FAILED",
                            e.message ?: "Invalid request",
                            call.requestId
                        )
                    )
                }
            }

            get("/vehicle/{id}") {
                val id =
                    call.parameters["id"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error(
                                "MISSING_ID",
                                "Vehicle ID required",
                                call.requestId
                            )
                        )
                val jobs = listVehicleMaintenanceUseCase.execute(id)
                call.respond(
                    ApiResponse.success(
                        jobs.map { MaintenanceResponse.fromDomain(it) },
                        call.requestId
                    )
                )
            }

            route("/{id}") {
                get {
                    val id = call.parameters["id"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("MISSING_ID", "Job ID required", call.requestId)
                    )
                    try {
                        val job = getMaintenanceJobUseCase.execute(id)
                        
                        // Fetch vehicle history
                        val histories = rentalRepository.findAllPaged(
                            page = 1,
                            limit = 50,
                            vehicleId = job.vehicleId
                        ).map { 
                            VehicleUsageHistoryDto(
                                rentalNumber = it.rental.rentalNumber,
                                customerName = it.customerName ?: "Unknown",
                                startDate = it.rental.startDate.toEpochMilli(),
                                endDate = it.rental.endDate.toEpochMilli(),
                                startOdometer = it.rental.startOdometerKm,
                                endOdometer = it.rental.endOdometerKm,
                                status = it.rental.status.name
                            )
                        }
                        
                        call.respond(ApiResponse.success(MaintenanceResponse.fromDomain(job, histories), call.requestId))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", e.message ?: "Job not found", call.requestId)
                        )
                    }
                }

                post("/start") {
                    val id =
                        call.parameters["id"]
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Job ID required",
                                    call.requestId
                                )
                            )
                    try {
                        val job = startMaintenanceUseCase.execute(id)
                        call.respond(
                            ApiResponse.success(MaintenanceResponse.fromDomain(job), call.requestId)
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ApiResponse.error(
                                "INVALID_STATE",
                                e.message ?: "Cannot start job",
                                call.requestId
                            )
                        )
                    }
                }

                post("/complete") {
                    val id =
                        call.parameters["id"]
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Job ID required",
                                    call.requestId
                                )
                            )
                    try {
                        val request = call.receive<MaintenanceStatusUpdateRequest>()
                        val job = completeMaintenanceUseCase.execute(id, request.laborCostPhp, request.partsCostPhp)
                        call.respond(ApiResponse.success(MaintenanceResponse.fromDomain(job), call.requestId))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ApiResponse.error(
                                "INVALID_STATE",
                                e.message ?: "Cannot complete job",
                                call.requestId
                            )
                        )
                    }
                }

                post("/cancel") {
                    val id =
                        call.parameters["id"]
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Job ID required",
                                    call.requestId
                                )
                            )
                    try {
                        val job = cancelMaintenanceUseCase.execute(id)
                        call.respond(
                            ApiResponse.success(MaintenanceResponse.fromDomain(job), call.requestId)
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ApiResponse.error(
                                "INVALID_STATE",
                                e.message ?: "Cannot cancel job",
                                call.requestId
                            )
                        )
                    }
                }
            }
        }
    }
}
