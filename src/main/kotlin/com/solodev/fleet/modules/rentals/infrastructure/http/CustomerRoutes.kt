package com.solodev.fleet.modules.rentals.infrastructure.http

import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.rentals.application.dto.*
import com.solodev.fleet.modules.rentals.application.usecases.*
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.customerRoutes(
    customerRepository: CustomerRepository,
    rentalRepository: RentalRepository? = null,
    vehicleRepository: VehicleRepository? = null,
    driverRepository: DriverRepository? = null,
    userRepository: UserRepository? = null,
    tokenRepository: VerificationTokenRepository? = null,
) {
    val createCustomerUseCase    = CreateCustomerUseCase(customerRepository)
    val deactivateCustomerUseCase = DeactivateCustomerUseCase(customerRepository)
    val getCustomerUseCase       = GetCustomerUseCase(customerRepository)
    val listCustomersUseCase     = ListCustomersUseCase(customerRepository)
    val updateCustomerUseCase    = UpdateCustomerUseCase(customerRepository)

    // ── Public: mobile-app customer self-registration ─────────────────────────
    if (userRepository != null && tokenRepository != null) {
        val registerCustomerUseCase = RegisterCustomerUseCase(customerRepository, userRepository, tokenRepository)
        route("/v1/customers/register") {
            post {
                try {
                    val request  = call.receive<CustomerRegistrationRequest>()
                    val customer = registerCustomerUseCase.execute(request)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(CustomerResponse.fromDomain(customer), call.requestId)
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiResponse.error("VALIDATION_ERROR", e.message ?: "Invalid data", call.requestId)
                    )
                } catch (e: IllegalStateException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiResponse.error("CONFLICT", e.message ?: "Customer already exists", call.requestId)
                    )
                }
            }
        }
    }

    // ── Authenticated: back-office CRUD ───────────────────────────────────────
    authenticate("auth-jwt") {
        route("/v1/customers") {
            get {
                val customers = listCustomersUseCase.execute()
                call.respond(ApiResponse.success(customers.map { CustomerResponse.fromDomain(it) }, call.requestId))
            }

            post {
                try {
                    val request  = call.receive<CustomerRequest>()
                    val customer = createCustomerUseCase.execute(request)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(CustomerResponse.fromDomain(customer), call.requestId)
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiResponse.error("VALIDATION_ERROR", e.message ?: "Invalid request", call.requestId)
                    )
                }
            }

            route("/{id}") {
                // GET /v1/customers/{id} — returns detail with active driver + vehicle join
                get {
                    val id = call.parameters["id"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("MISSING_ID", "Customer ID required", call.requestId)
                        )

                    val customer = getCustomerUseCase.execute(id)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Customer not found", call.requestId)
                        )

                    // Build join detail: find active rental → vehicle → driver assigned to that vehicle
                    var assignedDriver: CustomerDriverSummary? = null
                    var activeVehicle: CustomerVehicleSummary? = null

                    if (rentalRepository != null && vehicleRepository != null && driverRepository != null) {
                        val activeRental = rentalRepository.findByCustomerId(CustomerId(id))
                            .firstOrNull { it.status == RentalStatus.ACTIVE }

                        if (activeRental != null) {
                            val vehicle = vehicleRepository.findById(activeRental.vehicleId)
                            if (vehicle != null) {
                                activeVehicle = CustomerVehicleSummary(
                                    vehicleId    = vehicle.id.value,
                                    licensePlate = vehicle.licensePlate,
                                    make         = vehicle.make,
                                    model        = vehicle.model,
                                    year         = vehicle.year,
                                )
                                val assignment = driverRepository.findActiveAssignmentByVehicle(vehicle.id.value)
                                if (assignment != null) {
                                    val driver = driverRepository.findById(DriverId(assignment.driverId))
                                    if (driver != null) {
                                        assignedDriver = CustomerDriverSummary(
                                            driverId      = driver.id.value,
                                            driverName    = driver.fullName,
                                            licenseNumber = driver.licenseNumber,
                                            phone         = driver.phone,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    call.respond(ApiResponse.success(
                        CustomerDetailResponse.fromDomain(customer, assignedDriver, activeVehicle),
                        call.requestId
                    ))
                }

                patch {
                    val id = call.parameters["id"]
                        ?: return@patch call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("MISSING_ID", "Customer ID required", call.requestId)
                        )
                    try {
                        val request = call.receive<UpdateCustomerRequest>()
                        val updated = updateCustomerUseCase.execute(id, request)
                            ?: return@patch call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error("NOT_FOUND", "Customer not found", call.requestId)
                            )
                        call.respond(ApiResponse.success(CustomerResponse.fromDomain(updated), call.requestId))
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ApiResponse.error("VALIDATION_ERROR", e.message ?: "Invalid request", call.requestId)
                        )
                    }
                }

                patch("deactivate") {
                    val id = call.parameters["id"]
                        ?: return@patch call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("MISSING_ID", "Customer ID required", call.requestId)
                        )
                    val updated = deactivateCustomerUseCase.execute(id)
                        ?: return@patch call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Customer not found", call.requestId)
                        )
                    call.respond(ApiResponse.success(CustomerResponse.fromDomain(updated), call.requestId))
                }
            }
        }
    }
}
