package com.solodev.fleet.modules.rentals.infrastructure.http

import com.solodev.fleet.modules.domain.ports.RentalRepository
import com.solodev.fleet.modules.domain.ports.VehicleRepository
import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import com.solodev.fleet.modules.rentals.application.dto.RentalResponse
import com.solodev.fleet.modules.rentals.application.usecases.*
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.rentalRoutes(rentalRepository: RentalRepository, vehicleRepository: VehicleRepository) {
    val createRentalUseCase = CreateRentalUseCase(rentalRepository, vehicleRepository)
    val getRentalUseCase = GetRentalUseCase(rentalRepository)
    val activateRentalUseCase = ActivateRentalUseCase(rentalRepository, vehicleRepository)
    val completeRentalUseCase = CompleteRentalUseCase(rentalRepository, vehicleRepository)
    val cancelRentalUseCase = CancelRentalUseCase(rentalRepository)
    val listRentalsUseCase = ListRentalsUseCase(rentalRepository)

    route("/v1/rentals") {
        get {
            val rentals = listRentalsUseCase.execute()
            val response = rentals.map { RentalResponse.fromDomain(it) }
            call.respond(ApiResponse.success(response, call.requestId))
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

                val rental = getRentalUseCase.execute(id)
                if (rental != null) {
                    call.respond(
                        ApiResponse.success(RentalResponse.fromDomain(rental), call.requestId)
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
                    val activated = activateRentalUseCase.execute(id)
                    call.respond(
                        ApiResponse.success(
                            RentalResponse.fromDomain(activated),
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

                    val completed = completeRentalUseCase.execute(id, finalMileage)
                    call.respond(
                        ApiResponse.success(
                            RentalResponse.fromDomain(completed),
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
                    val cancelled = cancelRentalUseCase.execute(id)
                    call.respond(
                        ApiResponse.success(
                            RentalResponse.fromDomain(cancelled),
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
        }
    }
}
