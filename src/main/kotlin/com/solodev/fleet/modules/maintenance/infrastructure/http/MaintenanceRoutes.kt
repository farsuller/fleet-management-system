package com.solodev.fleet.modules.maintenance.infrastructure.http

import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceRequest
import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceResponse
import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceStatusUpdateRequest
import com.solodev.fleet.modules.maintenance.application.usecases.*
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.maintenanceRoutes(maintenanceRepository: MaintenanceRepository) {
    val scheduleMaintenanceUseCase = ScheduleMaintenanceUseCase(maintenanceRepository)
    val listVehicleMaintenanceUseCase = ListVehicleMaintenanceUseCase(maintenanceRepository)
    val startMaintenanceUseCase = StartMaintenanceUseCase(maintenanceRepository)
    val completeMaintenanceUseCase = CompleteMaintenanceUseCase(maintenanceRepository)
    val cancelMaintenanceUseCase = CancelMaintenanceUseCase(maintenanceRepository)

    authenticate("auth-jwt") {
        route("/v1/maintenance") {
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
                post("/start") {
                    val id =
                        call.parameters["id"]
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Vehicle ID required",
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
                                    "Vehicle ID required",
                                    call.requestId
                                )
                            )
                    try {
                        val request = call.receive<MaintenanceStatusUpdateRequest>()
                        val job =
                            completeMaintenanceUseCase.execute(
                                id,
                                request.laborCostCents,
                                request.partsCostCents
                            )
                        call.respond(
                            ApiResponse.success(MaintenanceResponse.fromDomain(job), call.requestId)
                        )
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
                                    "Vehicle ID required",
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
