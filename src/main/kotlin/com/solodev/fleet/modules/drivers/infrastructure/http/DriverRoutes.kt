package com.solodev.fleet.modules.drivers.infrastructure.http

import com.solodev.fleet.modules.drivers.application.dto.*
import com.solodev.fleet.modules.drivers.application.usecases.*
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.driverRoutes(
    driverRepository: DriverRepository,
    userRepository: UserRepository,
    tokenRepository: VerificationTokenRepository,
) {
    val registerDriverUseCase  = RegisterDriverUseCase(driverRepository, userRepository, tokenRepository)
    val createDriverUseCase    = CreateDriverUseCase(driverRepository)
    val getDriverUseCase       = GetDriverUseCase(driverRepository)
    val listDriversUseCase     = ListDriversUseCase(driverRepository)
    val deactivateDriverUseCase = DeactivateDriverUseCase(driverRepository)

    // ── Public: mobile-app driver self-registration ───────────────────────────
    route("/v1/drivers/register") {
        post {
            try {
                val request = call.receive<DriverRegistrationRequest>()
                val driver  = registerDriverUseCase.execute(request)
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse.success(DriverResponse.fromDomain(driver), call.requestId)
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiResponse.error("VALIDATION_ERROR", e.message ?: "Invalid data", call.requestId)
                )
            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse.error("CONFLICT", e.message ?: "Driver already exists", call.requestId)
                )
            }
        }
    }

    // ── Authenticated: back-office CRUD + vehicle assignment ─────────────────
    authenticate("auth-jwt") {
        route("/v1/drivers") {

            // List all drivers
            get {
                val drivers = listDriversUseCase.execute()
                val response = drivers.map { d ->
                    val assignment = driverRepository.findActiveAssignmentByDriver(d.id)
                    DriverResponse.fromDomain(d, assignment)
                }
                call.respond(ApiResponse.success(response, call.requestId))
            }

            // Back-office create (no password — account created separately)
            post {
                try {
                    val request = call.receive<DriverRequest>()
                    val driver  = createDriverUseCase.execute(request)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(DriverResponse.fromDomain(driver), call.requestId)
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiResponse.error("VALIDATION_ERROR", e.message ?: "Invalid data", call.requestId)
                    )
                } catch (e: IllegalStateException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiResponse.error("CONFLICT", e.message ?: "Driver already exists", call.requestId)
                    )
                }
            }

            route("/{id}") {
                // Get single driver (with current vehicle assignment)
                get {
                    val id = call.parameters["id"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("MISSING_ID", "Driver ID required", call.requestId)
                        )
                    val driver = getDriverUseCase.execute(id)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Driver not found", call.requestId)
                        )
                    val assignment = driverRepository.findActiveAssignmentByDriver(driver.id)
                    call.respond(ApiResponse.success(DriverResponse.fromDomain(driver, assignment), call.requestId))
                }

                // Deactivate / reactivate
                patch("deactivate") {
                    val id = call.parameters["id"]
                        ?: return@patch call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("MISSING_ID", "Driver ID required", call.requestId)
                        )
                    val driver = deactivateDriverUseCase.execute(id)
                        ?: return@patch call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Driver not found", call.requestId)
                        )
                    call.respond(ApiResponse.success(DriverResponse.fromDomain(driver), call.requestId))
                }

                // Assign driver to a vehicle
                post("assign") {
                    val driverId = call.parameters["id"]
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("MISSING_ID", "Driver ID required", call.requestId)
                        )
                    try {
                        val request    = call.receive<AssignDriverRequest>()
                        val driverObj  = getDriverUseCase.execute(driverId)
                            ?: return@post call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error("NOT_FOUND", "Driver not found", call.requestId)
                            )
                        // Verify driver has no active assignment already
                        val existingDriverAssignment = driverRepository.findActiveAssignmentByDriver(driverObj.id)
                        if (existingDriverAssignment != null) {
                            return@post call.respond(
                                HttpStatusCode.Conflict,
                                ApiResponse.error(
                                    "ALREADY_ASSIGNED",
                                    "Driver is already assigned to vehicle ${existingDriverAssignment.vehicleId}",
                                    call.requestId
                                )
                            )
                        }
                        // Verify target vehicle is not already assigned to another driver
                        val existingVehicleAssignment = driverRepository.findActiveAssignmentByVehicle(request.vehicleId)
                        if (existingVehicleAssignment != null) {
                            return@post call.respond(
                                HttpStatusCode.Conflict,
                                ApiResponse.error(
                                    "VEHICLE_OCCUPIED",
                                    "Vehicle ${request.vehicleId} already has an active driver",
                                    call.requestId
                                )
                            )
                        }
                        val assignment = driverRepository.assignToVehicle(driverObj.id, request.vehicleId, request.notes)
                        call.respond(HttpStatusCode.Created, ApiResponse.success(AssignmentResponse.fromDomain(assignment), call.requestId))
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ApiResponse.error("VALIDATION_ERROR", e.message ?: "Invalid data", call.requestId)
                        )
                    }
                }

                // Release driver from current vehicle
                post("release") {
                    val driverId = call.parameters["id"]
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("MISSING_ID", "Driver ID required", call.requestId)
                        )
                    val driverObj = getDriverUseCase.execute(driverId)
                        ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Driver not found", call.requestId)
                        )
                    val released = driverRepository.releaseFromVehicle(driverObj.id)
                        ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_ASSIGNED", "Driver has no active vehicle assignment", call.requestId)
                        )
                    call.respond(ApiResponse.success(AssignmentResponse.fromDomain(released), call.requestId))
                }

                // Full assignment history for a driver
                get("assignments") {
                    val driverId = call.parameters["id"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("MISSING_ID", "Driver ID required", call.requestId)
                        )
                    val driverObj = getDriverUseCase.execute(driverId)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Driver not found", call.requestId)
                        )
                    val history = driverRepository.findAssignmentHistoryByDriver(driverObj.id)
                    call.respond(ApiResponse.success(history.map { AssignmentResponse.fromDomain(it) }, call.requestId))
                }
            }
        }

        // ── Vehicle-centric assignment queries (under /v1/vehicles) ───────────
        route("/v1/vehicles/{vehicleId}/driver") {
            // Who is driving this vehicle right now?
            get {
                val vehicleId = call.parameters["vehicleId"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("MISSING_ID", "Vehicle ID required", call.requestId)
                    )
                val assignment = driverRepository.findActiveAssignmentByVehicle(vehicleId)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse.error("NOT_ASSIGNED", "No active driver for this vehicle", call.requestId)
                    )
                val driver = driverRepository.findById(
                    com.solodev.fleet.modules.drivers.domain.model.DriverId(assignment.driverId)
                )
                call.respond(ApiResponse.success(
                    driver?.let { DriverResponse.fromDomain(it, assignment) },
                    call.requestId
                ))
            }

            // Assignment history for a vehicle
            get("history") {
                val vehicleId = call.parameters["vehicleId"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("MISSING_ID", "Vehicle ID required", call.requestId)
                    )
                val history = driverRepository.findAssignmentHistoryByVehicle(vehicleId)
                call.respond(ApiResponse.success(history.map { AssignmentResponse.fromDomain(it) }, call.requestId))
            }
        }
    }
}
