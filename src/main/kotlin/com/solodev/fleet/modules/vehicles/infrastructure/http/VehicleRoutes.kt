package com.solodev.fleet.modules.vehicles.infrastructure.http

import com.solodev.fleet.modules.domain.ports.VehicleRepository
import com.solodev.fleet.modules.vehicles.application.dto.VehicleRequest
import com.solodev.fleet.modules.vehicles.application.dto.VehicleResponse
import com.solodev.fleet.modules.vehicles.application.usecases.CreateVehicleUseCase
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.vehicleRoutes(repository: VehicleRepository) {
    val createVehicleUseCase = CreateVehicleUseCase(repository)

    route("/v1/vehicles") {
        post {
            val request = call.receive<VehicleRequest>()
            val domainVehicle = createVehicleUseCase.execute(request)

            // Map Domain to DTO
            val responsePayload = VehicleResponse.fromDomain(domainVehicle)

            call.respond(ApiResponse.success(responsePayload, call.requestId))
        }

        get {
            val vehicles = repository.findAll()

            // Map List of Domain to List of DTO
            val responsePayload = vehicles.map { VehicleResponse.fromDomain(it) }

            call.respond(ApiResponse.success(responsePayload, call.requestId))
        }
    }
}
