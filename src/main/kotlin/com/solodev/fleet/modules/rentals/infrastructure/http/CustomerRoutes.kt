package com.solodev.fleet.modules.rentals.infrastructure.http

import com.solodev.fleet.modules.rentals.application.dto.CustomerRequest
import com.solodev.fleet.modules.rentals.application.dto.CustomerResponse
import com.solodev.fleet.modules.rentals.application.usecases.CreateCustomerUseCase
import com.solodev.fleet.modules.rentals.application.usecases.GetCustomerUseCase
import com.solodev.fleet.modules.rentals.application.usecases.ListCustomersUseCase
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Customer management routes.
 *
 * Endpoints:
 * - GET /v1/customers - List all customers
 * - POST /v1/customers - Create a new customer
 * - GET /v1/customers/{id} - Get customer by ID
 */
fun Route.customerRoutes(customerRepository: CustomerRepository) {
    val createCustomerUseCase = CreateCustomerUseCase(customerRepository)
    val getCustomerUseCase = GetCustomerUseCase(customerRepository)
    val listCustomersUseCase = ListCustomersUseCase(customerRepository)

    authenticate("auth-jwt") {
        route("/v1/customers") {
            // List all customers
            get {
                val customers = listCustomersUseCase.execute()
                val response = customers.map { CustomerResponse.fromDomain(it) }
                call.respond(ApiResponse.success(response, call.requestId))
            }

            // Create a new customer
            post {
                try {
                    val request = call.receive<CustomerRequest>()
                    val customer = createCustomerUseCase.execute(request)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(CustomerResponse.fromDomain(customer), call.requestId)
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiResponse.error(
                            "VALIDATION_ERROR",
                            e.message ?: "Invalid request",
                            call.requestId
                        )
                    )
                }
            }

            // Get customer by ID
            route("/{id}") {
                get {
                    val id =
                        call.parameters["id"]
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Customer ID required",
                                    call.requestId
                                )
                            )

                    val customer = getCustomerUseCase.execute(id)
                    if (customer != null) {
                        call.respond(
                            ApiResponse.success(
                                CustomerResponse.fromDomain(customer),
                                call.requestId
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Customer not found", call.requestId)
                        )
                    }
                }
            }
        }
    }
}
