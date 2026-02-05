package com.solodev.fleet.modules.rentals.infrastructure.http

import com.solodev.fleet.modules.domain.models.RentalId
import com.solodev.fleet.modules.domain.ports.RentalRepository
import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import com.solodev.fleet.modules.rentals.application.dto.RentalResponse
import com.solodev.fleet.modules.rentals.application.usecases.ActivateRentalUseCase
import com.solodev.fleet.modules.rentals.application.usecases.CompleteRentalUseCase
import com.solodev.fleet.modules.rentals.application.usecases.CreateRentalUseCase
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.rentalRoutes(repository: RentalRepository) {
    val createUseCase = CreateRentalUseCase(repository)
    val activateUseCase = ActivateRentalUseCase(repository)
    val completeUseCase = CompleteRentalUseCase(repository)

    route("/v1/rentals") {
        post {
            val request = call.receive<RentalRequest>()
            val rental = createUseCase.execute(request)
            call.respond(ApiResponse.success(RentalResponse.fromDomain(rental), call.requestId))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get
            val rental = repository.findById(RentalId(id))
            if (rental == null) {
                call.respond(ApiResponse.error("NOT_FOUND", "Rental not found", call.requestId))
            } else {
                call.respond(ApiResponse.success(RentalResponse.fromDomain(rental), call.requestId))
            }
        }

        get {
            val rentals = repository.findAll()

            // Map List of Domain to List of DTO
            val responsePayload = rentals.map { RentalResponse.fromDomain(it) }

            call.respond(ApiResponse.success(responsePayload, call.requestId))
        }

        post("/{id}/activate") {
            val id = call.parameters["id"] ?: return@post
            val body = call.receive<Map<String, Int>>()
            val odometer = body["startOdometer"] ?: 0
            val rental = activateUseCase.execute(id, odometer)
            call.respond(ApiResponse.success(RentalResponse.fromDomain(rental), call.requestId))
        }

        post("/{id}/complete") {
            val id = call.parameters["id"] ?: return@post
            val body = call.receive<Map<String, Int>>()
            val odometer = body["endOdometer"] ?: 0
            val rental = completeUseCase.execute(id, odometer)
            call.respond(ApiResponse.success(RentalResponse.fromDomain(rental), call.requestId))
        }
    }
}
