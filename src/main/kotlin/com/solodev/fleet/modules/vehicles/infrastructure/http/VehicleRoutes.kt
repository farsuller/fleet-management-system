package com.solodev.fleet.modules.vehicles.infrastructure.http

import com.solodev.fleet.modules.vehicles.application.dto.*
import com.solodev.fleet.modules.vehicles.application.usecases.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.Idempotency
import com.solodev.fleet.shared.plugins.UserRole
import com.solodev.fleet.shared.plugins.paginationParams
import com.solodev.fleet.shared.plugins.requestId
import com.solodev.fleet.shared.plugins.withRoles
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.vehicleRoutes(repository: VehicleRepository) {
    val createVehicleUseCaseC = CreateVehicleUseCase(repository)
    val getVehicleUseCase = GetVehicleUseCase(repository)
    val updateVehicleUseCase = UpdateVehicleUseCase(repository)
    val deleteVehicleUseCase = DeleteVehicleUseCase(repository)
    val listVehiclesUseCase = ListVehiclesUseCase(repository)
    val updateVehicleStateUseCase = UpdateVehicleStateUseCase(repository)
    val recordOdometerUseCase = RecordOdometerUseCase(repository)

    authenticate("auth-jwt") {
        route("/v1/vehicles") {
            get {
                val params = call.paginationParams() // Extracts limit and cursor
                val result = listVehiclesUseCase.execute(params)
                call.respond(ApiResponse.success(result, call.requestId))
            }

            withRoles(UserRole.ADMIN, UserRole.FLEET_MANAGER) {
                post {
                    val request = call.receive<VehicleRequest>()
                    val vehicle = createVehicleUseCaseC.execute(request)
                    call.respond(
                            HttpStatusCode.Created,
                            ApiResponse.success(VehicleResponse.fromDomain(vehicle), call.requestId)
                    )
                }

                route("/{id}") {
                    get {
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

                        val vehicle = getVehicleUseCase.execute(id)
                        if (vehicle != null) {
                            call.respond(
                                    ApiResponse.success(
                                            VehicleResponse.fromDomain(vehicle),
                                            call.requestId
                                    )
                            )
                        } else {
                            call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error(
                                            "NOT_FOUND",
                                            "Vehicle not found",
                                            call.requestId
                                    )
                            )
                        }
                    }

                    patch {
                        val id =
                                call.parameters["id"]
                                        ?: return@patch call.respond(
                                                HttpStatusCode.BadRequest,
                                                ApiResponse.error(
                                                        "MISSING_ID",
                                                        "Vehicle ID required",
                                                        call.requestId
                                                )
                                        )

                        val request = call.receive<VehicleUpdateRequest>()
                        if (!request.hasUpdates()) {
                            return@patch call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error(
                                            "NO_UPDATES",
                                            "No fields to update",
                                            call.requestId
                                    )
                            )
                        }

                        val updated = updateVehicleUseCase.execute(id, request)
                        if (updated != null) {
                            call.respond(
                                    ApiResponse.success(
                                            VehicleResponse.fromDomain(updated),
                                            call.requestId
                                    )
                            )
                        } else {
                            call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error(
                                            "NOT_FOUND",
                                            "Vehicle not found",
                                            call.requestId
                                    )
                            )
                        }
                    }

                    delete {
                        val id =
                                call.parameters["id"]
                                        ?: return@delete call.respond(
                                                HttpStatusCode.BadRequest,
                                                ApiResponse.error(
                                                        "MISSING_ID",
                                                        "Vehicle ID required",
                                                        call.requestId
                                                )
                                        )

                        val deleted = deleteVehicleUseCase.execute(id)
                        if (deleted) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error(
                                            "NOT_FOUND",
                                            "Vehicle not found",
                                            call.requestId
                                    )
                            )
                        }
                    }

                    patch("/state") {
                        val id =
                                call.parameters["id"]
                                        ?: return@patch call.respond(
                                                HttpStatusCode.BadRequest,
                                                ApiResponse.error(
                                                        "MISSING_ID",
                                                        "Vehicle ID required",
                                                        call.requestId
                                                )
                                        )

                        val request = call.receive<VehicleStateRequest>()

                        try {
                            val updated = updateVehicleStateUseCase.execute(id, request.state)
                            if (updated != null) {
                                call.respond(
                                        ApiResponse.success(
                                                VehicleResponse.fromDomain(updated),
                                                call.requestId
                                        )
                                )
                            } else {
                                call.respond(
                                        HttpStatusCode.NotFound,
                                        ApiResponse.error(
                                                "NOT_FOUND",
                                                "Vehicle not found",
                                                call.requestId
                                        )
                                )
                            }
                        } catch (e: IllegalArgumentException) {
                            call.respond(
                                    HttpStatusCode.UnprocessableEntity,
                                    ApiResponse.error(
                                            "INVALID_STATE",
                                            e.message ?: "Invalid state",
                                            call.requestId
                                    )
                            )
                        }
                    }

                    post("/odometer") {
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

                        val request = call.receive<OdometerRequest>()

                        try {
                            val updated = recordOdometerUseCase.execute(id, request.mileageKm)
                            if (updated != null) {
                                call.respond(
                                        ApiResponse.success(
                                                VehicleResponse.fromDomain(updated),
                                                call.requestId
                                        )
                                )
                            } else {
                                call.respond(
                                        HttpStatusCode.NotFound,
                                        ApiResponse.error(
                                                "NOT_FOUND",
                                                "Vehicle not found",
                                                call.requestId
                                        )
                                )
                            }
                        } catch (e: IllegalArgumentException) {
                            call.respond(
                                    HttpStatusCode.UnprocessableEntity,
                                    ApiResponse.error(
                                            "INVALID_MILEAGE",
                                            e.message ?: "Invalid mileage",
                                            call.requestId
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
