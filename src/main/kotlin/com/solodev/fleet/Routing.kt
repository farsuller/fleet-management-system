package com.solodev.fleet

import com.solodev.fleet.modules.infrastructure.persistence.RentalRepositoryImpl
import com.solodev.fleet.modules.infrastructure.persistence.UserRepositoryImpl
import com.solodev.fleet.modules.infrastructure.persistence.VehicleRepositoryImpl
import com.solodev.fleet.modules.rentals.infrastructure.http.rentalRoutes
import com.solodev.fleet.modules.users.infrastructure.http.userRoutes
import com.solodev.fleet.modules.vehicles.infrastructure.http.vehicleRoutes
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Configures the application's routing.
 *
 * Defines the HTTP endpoints exposed by the application. Currently includes:
 * - Root path ("/"): Phase 1 verification endpoint.
 * - Health check ("/health"): Liveness probe for container orchestration.
 */
fun Application.configureRouting() {

    // Initialize the repository
    val vehicleRepo = VehicleRepositoryImpl()
    val rentalRepo = RentalRepositoryImpl()
    val userRepo = UserRepositoryImpl()

    routing {
        vehicleRoutes(vehicleRepo)

        rentalRoutes(rentalRepo)

        userRoutes(userRepo)

        get("/") {
            call.respond(
                ApiResponse.success(
                    mapOf("message" to "Fleet Management API v1"),
                    call.requestId
                )
            )
        }

        get("/health") {
            call.respond(
                ApiResponse.success(
                    data = mapOf("status" to "OK"),
                    requestId = call.requestId
                )
            )
        }
    }
}
