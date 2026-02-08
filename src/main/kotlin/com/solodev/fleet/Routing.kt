package com.solodev.fleet

import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepositoryImpl
import com.solodev.fleet.modules.maintenance.infrastructure.http.maintenanceRoutes
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepositoryImpl
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepositoryImpl
import com.solodev.fleet.modules.rentals.infrastructure.http.customerRoutes
import com.solodev.fleet.modules.rentals.infrastructure.http.rentalRoutes
import com.solodev.fleet.modules.users.domain.repository.UserRepositoryImpl
import com.solodev.fleet.modules.users.infrastructure.http.userRoutes
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepositoryImpl
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepositoryImpl
import com.solodev.fleet.modules.vehicles.infrastructure.http.vehicleRoutes
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import com.solodev.fleet.shared.utils.JwtService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Configures the application's routing. */
fun Application.configureRouting(jwtService: JwtService) {

    // Initialize the repository
    val vehicleRepo = VehicleRepositoryImpl()
    val rentalRepo = RentalRepositoryImpl()
    val userRepo = UserRepositoryImpl()
    val tokenRepo = VerificationTokenRepositoryImpl()
    val customerRepo = CustomerRepositoryImpl()
    val maintenanceRepo = MaintenanceRepositoryImpl()

    routing {
        vehicleRoutes(vehicleRepo)

        rentalRoutes(rentalRepository = rentalRepo, vehicleRepository = vehicleRepo)

        customerRoutes(customerRepository = customerRepo)

        userRoutes(userRepository = userRepo, tokenRepository = tokenRepo, jwtService = jwtService)

        maintenanceRoutes(maintenanceRepository = maintenanceRepo)

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
                    ApiResponse.success(data = mapOf("status" to "OK"), requestId = call.requestId)
            )
        }
    }
}
