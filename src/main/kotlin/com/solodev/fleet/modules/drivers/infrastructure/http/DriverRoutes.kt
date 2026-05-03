package com.solodev.fleet.modules.drivers.infrastructure.http

import com.solodev.fleet.modules.drivers.application.dto.AssignDriverRequest
import com.solodev.fleet.modules.drivers.application.dto.AssignmentResponse
import com.solodev.fleet.modules.drivers.application.dto.DriverLoginRequest
import com.solodev.fleet.modules.drivers.application.dto.DriverRegistrationRequest
import com.solodev.fleet.modules.drivers.application.dto.DriverRequest
import com.solodev.fleet.modules.drivers.application.dto.DriverResponse
import com.solodev.fleet.modules.drivers.application.dto.EndShiftRequest
import com.solodev.fleet.modules.drivers.application.dto.RefreshTokenRequest
import com.solodev.fleet.modules.drivers.application.dto.ShiftResponse
import com.solodev.fleet.modules.drivers.application.dto.StartShiftRequest
import com.solodev.fleet.modules.drivers.application.dto.UpdateDriverRequest
import com.solodev.fleet.modules.drivers.application.usecases.CreateDriverUseCase
import com.solodev.fleet.modules.drivers.application.usecases.DeactivateDriverUseCase
import com.solodev.fleet.modules.drivers.application.usecases.DeleteDriverUseCase
import com.solodev.fleet.modules.drivers.application.usecases.EndShiftUseCase
import com.solodev.fleet.modules.drivers.application.usecases.GetActiveShiftUseCase
import com.solodev.fleet.modules.drivers.application.usecases.GetDriverUseCase
import com.solodev.fleet.modules.drivers.application.usecases.ListDriversUseCase
import com.solodev.fleet.modules.drivers.application.usecases.LoginDriverUseCase
import com.solodev.fleet.modules.drivers.application.usecases.RegisterDriverUseCase
import com.solodev.fleet.modules.drivers.application.usecases.StartShiftUseCase
import com.solodev.fleet.modules.drivers.application.usecases.UpdateDriverUseCase
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import com.solodev.fleet.shared.utils.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.driverRoutes(
    driverRepository: DriverRepository,
    userRepository: UserRepository,
    tokenRepository: VerificationTokenRepository,
    jwtService: JwtService,
    vehicleRepository: VehicleRepository,
    emailService: com.solodev.fleet.shared.infrastructure.email.EmailService,
) {
    val registerDriverUseCase =
        RegisterDriverUseCase(driverRepository, userRepository, tokenRepository, emailService)
    val loginDriverUseCase = LoginDriverUseCase(userRepository, driverRepository, jwtService)
    val createDriverUseCase = CreateDriverUseCase(driverRepository)
    val getDriverUseCase = GetDriverUseCase(driverRepository)
    val listDriversUseCase = ListDriversUseCase(driverRepository)
    val deactivateDriverUseCase = DeactivateDriverUseCase(driverRepository)
    val updateDriverUseCase = UpdateDriverUseCase(driverRepository)
    val startShiftUseCase = StartShiftUseCase(driverRepository)
    val endShiftUseCase = EndShiftUseCase(driverRepository)
    val getActiveShiftUseCase = GetActiveShiftUseCase(driverRepository)
    val deleteDriverUseCase = DeleteDriverUseCase(driverRepository)

    // ── Public: mobile-app driver self-registration ───────────────────────────
    route("/v1/drivers/register") {
        post {
            try {
                val request = call.receive<DriverRegistrationRequest>()
                val driver = registerDriverUseCase.execute(request)
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse.success(DriverResponse.fromDomain(driver), call.requestId),
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiResponse.error(
                        "VALIDATION_ERROR",
                        e.message ?: "Invalid data",
                        call.requestId,
                    ),
                )
            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse.error(
                        "CONFLICT",
                        e.message ?: "Driver already exists",
                        call.requestId,
                    ),
                )
            }
        }
    }

    // ── Public: driver mobile-app login ──────────────────────────────────────
    route("/v1/auth/token") {
        post {
            try {
                val request = call.receive<DriverLoginRequest>()
                val response = loginDriverUseCase.execute(request.email, request.password)
                call.respond(response)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse.error(
                        "UNAUTHORIZED",
                        e.message ?: "Invalid credentials",
                        call.requestId,
                    ),
                )
            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse.error(
                        "FORBIDDEN",
                        e.message ?: "Access denied",
                        call.requestId,
                    ),
                )
            }
        }

        post("/refresh") {
            try {
                val request = call.receive<RefreshTokenRequest>()
                val claims =
                    jwtService.validateRefreshToken(request.refreshToken)
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            ApiResponse.error(
                                "INVALID_REFRESH_TOKEN",
                                "Refresh token is invalid or expired",
                                call.requestId,
                            ),
                        )
                val (id, email, roles) = claims
                val newAccessToken = jwtService.generateToken(id = id, email = email, roles = roles)
                val newRefreshToken = jwtService.generateRefreshToken(id = id, email = email, roles = roles)
                // Return only token fields; driverId is already held by the client
                call.respond(mapOf("accessToken" to newAccessToken, "refreshToken" to newRefreshToken))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse.error(
                        "INVALID_REFRESH_TOKEN",
                        "Refresh token is invalid or expired",
                        call.requestId,
                    ),
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
                val response =
                    drivers.map { d ->
                        val assignment = driverRepository.findActiveAssignmentByDriver(d.id)
                        DriverResponse.fromDomain(d, assignment)
                    }
                call.respond(ApiResponse.success(response, call.requestId))
            }

            // Back-office create (no password — account created separately)
            post {
                try {
                    val request = call.receive<DriverRequest>()
                    val driver = createDriverUseCase.execute(request)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(DriverResponse.fromDomain(driver), call.requestId),
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiResponse.error(
                            "VALIDATION_ERROR",
                            e.message ?: "Invalid data",
                            call.requestId,
                        ),
                    )
                } catch (e: IllegalStateException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiResponse.error(
                            "CONFLICT",
                            e.message ?: "Driver already exists",
                            call.requestId,
                        ),
                    )
                }
            }

            route("/{id}") {
                // Get single driver (with current vehicle assignment)
                get {
                    val id =
                        call.parameters["id"]
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Driver ID required",
                                    call.requestId,
                                ),
                            )
                    val driver =
                        getDriverUseCase.execute(id)
                            ?: return@get call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error(
                                    "NOT_FOUND",
                                    "Driver not found",
                                    call.requestId,
                                ),
                            )
                    val assignment = driverRepository.findActiveAssignmentByDriver(driver.id)
                    val vehicle =
                        assignment?.let {
                            vehicleRepository.findById(VehicleId(it.vehicleId))
                        }
                    call.respond(
                        ApiResponse.success(
                            DriverResponse.fromDomain(
                                driver,
                                assignment,
                                vehicleType = vehicle?.vehicleType?.name,
                                vehiclePlate = vehicle?.licensePlate,
                            ),
                            call.requestId,
                        ),
                    )
                }

                // Update driver
                patch {
                    val id =
                        call.parameters["id"]
                            ?: return@patch call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Driver ID required",
                                    call.requestId,
                                ),
                            )
                    try {
                        val request = call.receive<UpdateDriverRequest>()
                        val driver = updateDriverUseCase.execute(id, request)
                        call.respond(
                            ApiResponse.success(
                                DriverResponse.fromDomain(driver),
                                call.requestId,
                            ),
                        )
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ApiResponse.error(
                                "VALIDATION_ERROR",
                                e.message ?: "Invalid data",
                                call.requestId,
                            ),
                        )
                    }
                }

                // Deactivate / reactivate
                patch("deactivate") {
                    val id =
                        call.parameters["id"]
                            ?: return@patch call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Driver ID required",
                                    call.requestId,
                                ),
                            )
                    val driver =
                        deactivateDriverUseCase.execute(id)
                            ?: return@patch call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error(
                                    "NOT_FOUND",
                                    "Driver not found",
                                    call.requestId,
                                ),
                            )
                    call.respond(
                        ApiResponse.success(DriverResponse.fromDomain(driver), call.requestId),
                    )
                }

                // Assign driver to a vehicle
                post("assign") {
                    val driverId =
                        call.parameters["id"]
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Driver ID required",
                                    call.requestId,
                                ),
                            )
                    try {
                        val request = call.receive<AssignDriverRequest>()
                        val driverObj =
                            getDriverUseCase.execute(driverId)
                                ?: return@post call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse.error(
                                        "NOT_FOUND",
                                        "Driver not found",
                                        call.requestId,
                                    ),
                                )
                        // Verify driver has no active assignment already
                        val existingDriverAssignment =
                            driverRepository.findActiveAssignmentByDriver(driverObj.id)
                        if (existingDriverAssignment != null) {
                            return@post call.respond(
                                HttpStatusCode.Conflict,
                                ApiResponse.error(
                                    "ALREADY_ASSIGNED",
                                    "Driver is already assigned to vehicle ${existingDriverAssignment.vehicleId}",
                                    call.requestId,
                                ),
                            )
                        }
                        // Verify target vehicle is not already assigned to another driver
                        val existingVehicleAssignment =
                            driverRepository.findActiveAssignmentByVehicle(request.vehicleId)
                        if (existingVehicleAssignment != null) {
                            return@post call.respond(
                                HttpStatusCode.Conflict,
                                ApiResponse.error(
                                    "VEHICLE_OCCUPIED",
                                    "Vehicle ${request.vehicleId} already has an active driver",
                                    call.requestId,
                                ),
                            )
                        }
                        val assignment =
                            driverRepository.assignToVehicle(
                                driverObj.id,
                                request.vehicleId,
                                request.notes,
                            )
                        call.respond(
                            HttpStatusCode.Created,
                            ApiResponse.success(
                                AssignmentResponse.fromDomain(assignment),
                                call.requestId,
                            ),
                        )
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ApiResponse.error(
                                "VALIDATION_ERROR",
                                e.message ?: "Invalid data",
                                call.requestId,
                            ),
                        )
                    }
                }

                // Release driver from current vehicle
                post("release") {
                    val driverId =
                        call.parameters["id"]
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Driver ID required",
                                    call.requestId,
                                ),
                            )
                    val driverObj =
                        getDriverUseCase.execute(driverId)
                            ?: return@post call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error(
                                    "NOT_FOUND",
                                    "Driver not found",
                                    call.requestId,
                                ),
                            )
                    val released =
                        driverRepository.releaseFromVehicle(driverObj.id)
                            ?: return@post call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error(
                                    "NOT_ASSIGNED",
                                    "Driver has no active vehicle assignment",
                                    call.requestId,
                                ),
                            )
                    call.respond(
                        ApiResponse.success(
                            AssignmentResponse.fromDomain(released),
                            call.requestId,
                        ),
                    )
                }

                // Full assignment history for a driver
                get("assignments") {
                    val driverId =
                        call.parameters["id"]
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Driver ID required",
                                    call.requestId,
                                ),
                            )
                    val driverObj =
                        getDriverUseCase.execute(driverId)
                            ?: return@get call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error(
                                    "NOT_FOUND",
                                    "Driver not found",
                                    call.requestId,
                                ),
                            )
                    val history = driverRepository.findAssignmentHistoryByDriver(driverObj.id)
                    call.respond(
                        ApiResponse.success(
                            history.map { AssignmentResponse.fromDomain(it) },
                            call.requestId,
                        ),
                    )
                }

                delete {
                    val id =
                        call.parameters["id"]
                            ?: return@delete call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error("MISSING_ID", "Driver ID required", call.requestId),
                            )

                    val deleted = deleteDriverUseCase.execute(id)
                    if (deleted) {
                        call.respond(
                            ApiResponse.success(
                                mapOf("message" to "Driver deleted successfully"),
                                call.requestId,
                            ),
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Driver not found", call.requestId),
                        )
                    }
                }
            }
        }

        // ── Driver Shift Lifecycle (Mobile/Driver App) ──────────────────────────
        route("/v1/drivers/shifts") {
            post("start") {
                val userId =
                    call
                        .principal<io.ktor.server.auth.jwt.JWTPrincipal>()
                        ?.payload
                        ?.getClaim("id")
                        ?.asString()
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            ApiResponse.error(
                                "UNAUTHORIZED",
                                "Driver ID missing from token",
                                call.requestId,
                            ),
                        )

                try {
                    val request = call.receive<StartShiftRequest>()
                    val shift = startShiftUseCase.execute(userId, request.vehicleId, request.notes)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(ShiftResponse.fromDomain(shift), call.requestId),
                    )
                } catch (e: com.solodev.fleet.shared.exceptions.NotFoundException) {
                    throw e
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error(
                            "SHIFT_START_ERROR",
                            e.message ?: "Failed to start shift",
                            call.requestId,
                        ),
                    )
                }
            }

            post("end") {
                val userId =
                    call
                        .principal<io.ktor.server.auth.jwt.JWTPrincipal>()
                        ?.payload
                        ?.getClaim("id")
                        ?.asString()
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            ApiResponse.error(
                                "UNAUTHORIZED",
                                "Driver ID missing from token",
                                call.requestId,
                            ),
                        )

                try {
                    val request = call.receive<EndShiftRequest>()
                    val shift = endShiftUseCase.execute(userId, request.notes)
                    call.respond(
                        ApiResponse.success(ShiftResponse.fromDomain(shift), call.requestId),
                    )
                } catch (e: com.solodev.fleet.shared.exceptions.NotFoundException) {
                    throw e
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error(
                            "SHIFT_END_ERROR",
                            e.message ?: "Failed to end shift",
                            call.requestId,
                        ),
                    )
                }
            }

            get("active") {
                val userId =
                    call
                        .principal<io.ktor.server.auth.jwt.JWTPrincipal>()
                        ?.payload
                        ?.getClaim("id")
                        ?.asString()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            ApiResponse.error(
                                "UNAUTHORIZED",
                                "Driver ID missing from token",
                                call.requestId,
                            ),
                        )

                val shift = getActiveShiftUseCase.execute(userId)
                if (shift == null) {
                    call.respond(ApiResponse.success(null as ShiftResponse?, call.requestId))
                } else {
                    call.respond(
                        ApiResponse.success(ShiftResponse.fromDomain(shift), call.requestId),
                    )
                }
            }
        }

        // ── Vehicle-centric assignment queries (under /v1/vehicles) ───────────
        route("/v1/vehicles/{vehicleId}/driver") {
            // Who is driving this vehicle right now?
            get {
                val vehicleId =
                    call.parameters["vehicleId"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error(
                                "MISSING_ID",
                                "Vehicle ID required",
                                call.requestId,
                            ),
                        )
                val assignment =
                    driverRepository.findActiveAssignmentByVehicle(vehicleId)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error(
                                "NOT_ASSIGNED",
                                "No active driver for this vehicle",
                                call.requestId,
                            ),
                        )
                val driver =
                    driverRepository.findById(
                        com.solodev.fleet.modules.drivers.domain.model.DriverId(
                            assignment.driverId,
                        ),
                    )
                call.respond(
                    ApiResponse.success(
                        driver?.let { DriverResponse.fromDomain(it, assignment) },
                        call.requestId,
                    ),
                )
            }

            // Assignment history for a vehicle
            get("history") {
                val vehicleId =
                    call.parameters["vehicleId"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error(
                                "MISSING_ID",
                                "Vehicle ID required",
                                call.requestId,
                            ),
                        )
                val history = driverRepository.findAssignmentHistoryByVehicle(vehicleId)
                call.respond(
                    ApiResponse.success(
                        history.map { AssignmentResponse.fromDomain(it) },
                        call.requestId,
                    ),
                )
            }
        }
    }
}
