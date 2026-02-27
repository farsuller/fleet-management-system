package com.solodev.fleet.modules.tracking.infrastructure.http

import com.solodev.fleet.modules.tracking.application.dto.LocationUpdateDTO
import com.solodev.fleet.modules.tracking.application.usecases.UpdateVehicleLocationUseCase
import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

/** API routes for vehicle tracking and spatial features. */
fun Route.trackingRoutes(
        updateVehicleLocation: UpdateVehicleLocationUseCase,
        spatialAdapter: PostGISAdapter
) {
    route("/v1/tracking") {
        get("/routes") {
            val routes = spatialAdapter.findAllRoutes()
            call.respond(ApiResponse.success(routes, call.requestId))
        }

        authenticate("auth-jwt") {
            post("/vehicles/{id}/location") {
                val vehicleId =
                        call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val dto = call.receive<LocationUpdateDTO>()

                val rawLocation = Location(dto.latitude, dto.longitude)
                val routeId = dto.routeId?.let { UUID.fromString(it) }

                val snappedLocation =
                        updateVehicleLocation.execute(
                                vehicleId = vehicleId,
                                rawLocation = rawLocation,
                                routeId = routeId
                        )

                // Return the snapped location to the client (optional, but helpful for debugging)
                call.respond(
                        HttpStatusCode.OK,
                        ApiResponse.success(snappedLocation, call.requestId)
                )
            }
        }
    }
}
