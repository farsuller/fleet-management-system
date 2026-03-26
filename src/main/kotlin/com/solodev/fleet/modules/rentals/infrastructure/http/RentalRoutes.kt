package com.solodev.fleet.modules.rentals.infrastructure.http

import com.solodev.fleet.modules.accounts.application.AccountingService
import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import com.solodev.fleet.modules.rentals.application.dto.UpdateRentalRequest
import com.solodev.fleet.modules.rentals.application.dto.RentalResponse
import com.solodev.fleet.modules.rentals.application.usecases.*
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.rentalRoutes(
    rentalRepository: RentalRepository,
    vehicleRepository: VehicleRepository,
    accountingService: AccountingService,
    issueInvoiceUseCase: com.solodev.fleet.modules.accounts.application.usecases.IssueInvoiceUseCase,
    invoiceRepository: com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
    ) {
    val createRentalUseCase = CreateRentalUseCase(rentalRepository, vehicleRepository)
    val getRentalUseCase = GetRentalUseCase(rentalRepository)
    val activateRentalUseCase = ActivateRentalUseCase(
        rentalRepository = rentalRepository,
        vehicleRepository = vehicleRepository,
        accountingService = accountingService
    )
    val completeRentalUseCase = CompleteRentalUseCase(
        rentalRepository = rentalRepository,
        vehicleRepository = vehicleRepository,
        issueInvoiceUseCase = issueInvoiceUseCase,
        invoiceRepository = invoiceRepository
    )
    val cancelRentalUseCase = CancelRentalUseCase(rentalRepository)
    val deleteRentalUseCase = DeleteRentalUseCase(rentalRepository)
    val listRentalsUseCase = ListRentalsUseCase(rentalRepository)
    val updateRentalUseCase = UpdateRentalUseCase(rentalRepository, vehicleRepository)

    authenticate("auth-jwt") {
        route("/v1/rentals") {
            get {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                val status = call.request.queryParameters["status"]?.let {
                    try { com.solodev.fleet.modules.rentals.domain.model.RentalStatus.valueOf(it) } catch (e: Exception) { null }
                }
                val vehicleId = call.request.queryParameters["vehicleId"]
                val customerId = call.request.queryParameters["customerId"]

                val (rentals, total) = listRentalsUseCase.execute(
                    page = page,
                    limit = limit,
                    status = status,
                    vehicleId = vehicleId,
                    customerId = customerId
                )
                
                val response = rentals.map { RentalResponse.fromDomain(it) }
                call.respond(ApiResponse.success(
                    data = response, 
                    requestId = call.requestId,
                    metadata = mapOf(
                        "total" to total.toString(),
                        "page" to page.toString(),
                        "limit" to limit.toString()
                    )
                ))
            }

            post {
                try {
                    val request = call.receive<RentalRequest>()
                    val rental = createRentalUseCase.execute(request)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(RentalResponse.fromDomain(rental), call.requestId)
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

            route("/{id}") {
                get {
                    val id =
                        call.parameters["id"]
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Rental ID required",
                                    call.requestId
                                )
                            )

                    val rentalWithDetails = getRentalUseCase.execute(id)
                    if (rentalWithDetails != null) {
                        call.respond(
                            ApiResponse.success(RentalResponse.fromDomain(rentalWithDetails), call.requestId)
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Rental not found", call.requestId)
                        )
                    }
                }

                post("/activate") {
                    val id =
                        call.parameters["id"]
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "Rental ID required",
                                    call.requestId
                                )
                            )

                    try {
                        val activatedWithDetails = activateRentalUseCase.execute(id)
                        call.respond(
                            ApiResponse.success(
                                RentalResponse.fromDomain(activatedWithDetails),
                                call.requestId
                            )
                        )
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error(
                                "NOT_FOUND",
                                e.message ?: "Rental not found",
                                call.requestId
                            )
                        )
                    } catch (e: IllegalStateException) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ApiResponse.error(
                                "INVALID_STATE",
                                e.message ?: "Invalid state",
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
                                    "Rental ID required",
                                    call.requestId
                                )
                            )

                    try {
                        val body =
                            try {
                                call.receive<Map<String, Int?>>()
                            } catch (e: Exception) {
                                emptyMap()
                            }
                        val finalMileage = body["finalMileage"] ?: body["endOdometer"]

                        val completedWithDetails = completeRentalUseCase.execute(id, finalMileage)
                        call.respond(
                            ApiResponse.success(
                                RentalResponse.fromDomain(completedWithDetails),
                                call.requestId
                            )
                        )
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error(
                                "NOT_FOUND",
                                e.message ?: "Rental not found",
                                call.requestId
                            )
                        )
                    } catch (e: IllegalStateException) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ApiResponse.error(
                                "INVALID_STATE",
                                e.message ?: "Invalid state",
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
                                    "Rental ID required",
                                    call.requestId
                                )
                            )

                    try {
                        val cancelledWithDetails = cancelRentalUseCase.execute(id)
                        call.respond(
                            ApiResponse.success(
                                RentalResponse.fromDomain(cancelledWithDetails),
                                call.requestId
                            )
                        )
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ApiResponse.error(
                                "VALIDATION_ERROR",
                                e.message ?: "Cannot cancel",
                                call.requestId
                            )
                        )
                    }
                }
 
                patch {
                    val id = call.parameters["id"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("MISSING_ID", "Rental ID required", call.requestId)
                    )

                    try {
                        val request = call.receive<UpdateRentalRequest>()
                        val updatedWithDetails = updateRentalUseCase.execute(id, request)
                        if (updatedWithDetails != null) {
                            call.respond(ApiResponse.success(RentalResponse.fromDomain(updatedWithDetails), call.requestId))
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error("NOT_FOUND", "Rental not found", call.requestId)
                            )
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ApiResponse.error("CONFLICT", e.message ?: "Update failed", call.requestId)
                        )
                    }
                }

                put {
                    // Supporting PUT as an alias for partial update to improve compatibility
                    val id = call.parameters["id"] ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("MISSING_ID", "Rental ID required", call.requestId)
                    )

                    try {
                        val request = call.receive<UpdateRentalRequest>()
                        val updatedWithDetails = updateRentalUseCase.execute(id, request)
                        if (updatedWithDetails != null) {
                            call.respond(ApiResponse.success(RentalResponse.fromDomain(updatedWithDetails), call.requestId))
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error("NOT_FOUND", "Rental not found", call.requestId)
                            )
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ApiResponse.error("CONFLICT", e.message ?: "Update failed", call.requestId)
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
                                    "Rental ID required",
                                    call.requestId
                                )
                            )

                    val deleted = deleteRentalUseCase.execute(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Rental not found", call.requestId)
                        )
                    }
                }
            }
        }
    }
}
