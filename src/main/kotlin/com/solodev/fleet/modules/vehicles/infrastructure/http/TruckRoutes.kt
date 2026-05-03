package com.solodev.fleet.modules.vehicles.infrastructure.http

import com.solodev.fleet.modules.drivers.application.dto.AssignmentResponse
import com.solodev.fleet.modules.drivers.application.dto.DriverResponse
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.modules.vehicles.application.dto.TruckRequest
import com.solodev.fleet.modules.vehicles.application.dto.TruckResponse
import com.solodev.fleet.modules.vehicles.application.dto.TruckUpdateRequest
import com.solodev.fleet.modules.vehicles.application.usecases.truck.CreateTruckUseCase
import com.solodev.fleet.modules.vehicles.application.usecases.truck.DeleteTruckUseCase
import com.solodev.fleet.modules.vehicles.application.usecases.truck.GetTruckUseCase
import com.solodev.fleet.modules.vehicles.application.usecases.truck.ListTrucksUseCase
import com.solodev.fleet.modules.vehicles.application.usecases.truck.UpdateTruckUseCase
import com.solodev.fleet.modules.vehicles.domain.repository.TruckRepository
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.UserRole
import com.solodev.fleet.shared.plugins.paginationParams
import com.solodev.fleet.shared.plugins.requestId
import com.solodev.fleet.shared.plugins.withRoles
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.truckRoutes(
    repository: TruckRepository,
    driverRepository: DriverRepository,
) {
    val listTrucksUseCase = ListTrucksUseCase(repository)
    val getTruckUseCase = GetTruckUseCase(repository)
    val createTruckUseCase = CreateTruckUseCase(repository)
    val updateTruckUseCase = UpdateTruckUseCase(repository)
    val deleteTruckUseCase = DeleteTruckUseCase(repository)

    authenticate("auth-jwt") {
        route("/v1/trucks") {
            get {
                val stateFilter = call.request.queryParameters["state"]
                val baseParams = call.paginationParams()
                val params =
                    if (stateFilter != null) {
                        baseParams.copy(filters = baseParams.filters + ("state" to stateFilter))
                    } else {
                        baseParams
                    }

                val result = listTrucksUseCase.execute(params)
                call.respond(ApiResponse.success(result, call.requestId))
            }

            withRoles(UserRole.ADMIN, UserRole.FLEET_MANAGER) {
                post {
                    val request = call.receive<TruckRequest>()
                    val truck = createTruckUseCase.execute(request)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(TruckResponse.fromDomain(truck), call.requestId),
                    )
                }

                route("/{id}") {
                    get {
                        val id =
                            call.parameters["id"]
                                ?: return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error("MISSING_ID", "Truck ID required", call.requestId),
                                )

                        val truck = getTruckUseCase.execute(id)
                        if (truck != null) {
                            call.respond(ApiResponse.success(TruckResponse.fromDomain(truck), call.requestId))
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error("NOT_FOUND", "Truck not found", call.requestId),
                            )
                        }
                    }

                    patch {
                        val id =
                            call.parameters["id"]
                                ?: return@patch call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error("MISSING_ID", "Truck ID required", call.requestId),
                                )

                        val request = call.receive<TruckUpdateRequest>()
                        if (!request.hasUpdates()) {
                            return@patch call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error("NO_UPDATES", "No fields to update", call.requestId),
                            )
                        }

                        val updated = updateTruckUseCase.execute(id, request)
                        if (updated != null) {
                            call.respond(ApiResponse.success(TruckResponse.fromDomain(updated), call.requestId))
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error("NOT_FOUND", "Truck not found", call.requestId),
                            )
                        }
                    }

                    delete {
                        val id =
                            call.parameters["id"]
                                ?: return@delete call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error("MISSING_ID", "Truck ID required", call.requestId),
                                )

                        try {
                            val deleted = deleteTruckUseCase.execute(id)
                            if (deleted) {
                                call.respond(
                                    ApiResponse.success(
                                        mapOf("message" to "Truck deleted successfully"),
                                        call.requestId,
                                    ),
                                )
                            } else {
                                call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error("NOT_FOUND", "Truck not found", call.requestId),
                                )
                            }
                        } catch (e: IllegalStateException) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "INVALID_STATE",
                                    e.message ?: "Cannot delete truck in current state",
                                    call.requestId,
                                ),
                            )
                        }
                    }

                    get("/driver") {
                        val id =
                            call.parameters["id"]
                                ?: return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error("MISSING_ID", "Truck ID required", call.requestId),
                                )

                        val truck =
                            getTruckUseCase.execute(id)
                                ?: return@get call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error("NOT_FOUND", "Truck not found", call.requestId),
                                )

                        val assignment =
                            driverRepository.findActiveAssignmentByVehicle(truck.vehicle.id.value)
                                ?: return@get call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error("NOT_ASSIGNED", "No active driver for this truck", call.requestId),
                                )
                        val driver = driverRepository.findById(DriverId(assignment.driverId))
                        call.respond(
                            ApiResponse.success(driver?.let { DriverResponse.fromDomain(it, assignment) }, call.requestId),
                        )
                    }

                    get("/driver/history") {
                        val id =
                            call.parameters["id"]
                                ?: return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error("MISSING_ID", "Truck ID required", call.requestId),
                                )

                        val truck =
                            getTruckUseCase.execute(id)
                                ?: return@get call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error("NOT_FOUND", "Truck not found", call.requestId),
                                )

                        val history = driverRepository.findAssignmentHistoryByVehicle(truck.vehicle.id.value)
                        call.respond(
                            ApiResponse.success(history.map { AssignmentResponse.fromDomain(it) }, call.requestId),
                        )
                    }
                }
            }
        }
    }
}
