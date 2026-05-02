package com.solodev.fleet.modules.vehicles.infrastructure.http

import com.solodev.fleet.modules.drivers.application.dto.AssignmentResponse
import com.solodev.fleet.modules.drivers.application.dto.DriverResponse
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.modules.vehicles.application.dto.BusRequest
import com.solodev.fleet.modules.vehicles.application.dto.BusResponse
import com.solodev.fleet.modules.vehicles.application.dto.BusUpdateRequest
import com.solodev.fleet.modules.vehicles.application.usecases.bus.CreateBusUseCase
import com.solodev.fleet.modules.vehicles.application.usecases.bus.DeleteBusUseCase
import com.solodev.fleet.modules.vehicles.application.usecases.bus.GetBusUseCase
import com.solodev.fleet.modules.vehicles.application.usecases.bus.ListBusesUseCase
import com.solodev.fleet.modules.vehicles.application.usecases.bus.UpdateBusUseCase
import com.solodev.fleet.modules.vehicles.domain.repository.BusRepository
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

fun Route.busRoutes(
    repository: BusRepository,
    driverRepository: DriverRepository,
) {
    val listBusesUseCase = ListBusesUseCase(repository)
    val getBusUseCase = GetBusUseCase(repository)
    val createBusUseCase = CreateBusUseCase(repository)
    val updateBusUseCase = UpdateBusUseCase(repository)
    val deleteBusUseCase = DeleteBusUseCase(repository)

    authenticate("auth-jwt") {
        route("/v1/buses") {
            get {
                val stateFilter = call.request.queryParameters["state"]
                val baseParams = call.paginationParams()
                val params =
                    if (stateFilter != null) {
                        baseParams.copy(filters = baseParams.filters + ("state" to stateFilter))
                    } else {
                        baseParams
                    }

                val result = listBusesUseCase.execute(params)
                call.respond(ApiResponse.success(result, call.requestId))
            }

            withRoles(UserRole.ADMIN, UserRole.FLEET_MANAGER) {
                post {
                    val request = call.receive<BusRequest>()
                    val bus = createBusUseCase.execute(request)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(BusResponse.fromDomain(bus), call.requestId),
                    )
                }

                route("/{id}") {
                    get {
                        val id =
                            call.parameters["id"]
                                ?: return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error("MISSING_ID", "Bus ID required", call.requestId),
                                )

                        val bus = getBusUseCase.execute(id)
                        if (bus != null) {
                            call.respond(ApiResponse.success(BusResponse.fromDomain(bus), call.requestId))
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error("NOT_FOUND", "Bus not found", call.requestId),
                            )
                        }
                    }

                    patch {
                        val id =
                            call.parameters["id"]
                                ?: return@patch call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error("MISSING_ID", "Bus ID required", call.requestId),
                                )

                        val request = call.receive<BusUpdateRequest>()
                        if (!request.hasUpdates()) {
                            return@patch call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error("NO_UPDATES", "No fields to update", call.requestId),
                            )
                        }

                        val updated = updateBusUseCase.execute(id, request)
                        if (updated != null) {
                            call.respond(ApiResponse.success(BusResponse.fromDomain(updated), call.requestId))
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error("NOT_FOUND", "Bus not found", call.requestId),
                            )
                        }
                    }

                    delete {
                        val id =
                            call.parameters["id"]
                                ?: return@delete call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error("MISSING_ID", "Bus ID required", call.requestId),
                                )

                        try {
                            val deleted = deleteBusUseCase.execute(id)
                            if (deleted) {
                                call.respond(
                                    ApiResponse.success(
                                        mapOf("message" to "Bus deleted successfully"),
                                        call.requestId,
                                    ),
                                )
                            } else {
                                call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error("NOT_FOUND", "Bus not found", call.requestId),
                                )
                            }
                        } catch (e: IllegalStateException) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "INVALID_STATE",
                                    e.message ?: "Cannot delete bus in current state",
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
                                    ApiResponse.error("MISSING_ID", "Bus ID required", call.requestId),
                                )

                        val bus =
                            getBusUseCase.execute(id)
                                ?: return@get call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error("NOT_FOUND", "Bus not found", call.requestId),
                                )

                        val assignment =
                            driverRepository.findActiveAssignmentByVehicle(bus.vehicle.id.value)
                                ?: return@get call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error("NOT_ASSIGNED", "No active driver for this bus", call.requestId),
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
                                    ApiResponse.error("MISSING_ID", "Bus ID required", call.requestId),
                                )

                        val bus =
                            getBusUseCase.execute(id)
                                ?: return@get call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error("NOT_FOUND", "Bus not found", call.requestId),
                                )

                        val history = driverRepository.findAssignmentHistoryByVehicle(bus.vehicle.id.value)
                        call.respond(
                            ApiResponse.success(history.map { AssignmentResponse.fromDomain(it) }, call.requestId),
                        )
                    }
                }
            }
        }
    }
}
