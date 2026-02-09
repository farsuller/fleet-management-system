package com.solodev.fleet

import com.solodev.fleet.modules.accounts.infrastructure.http.accountingRoutes
import com.solodev.fleet.modules.accounts.infrastructure.persistence.*
import com.solodev.fleet.modules.maintenance.infrastructure.http.maintenanceRoutes
import com.solodev.fleet.modules.maintenance.infrastructure.persistence.MaintenanceRepositoryImpl
import com.solodev.fleet.modules.rentals.infrastructure.http.customerRoutes
import com.solodev.fleet.modules.rentals.infrastructure.http.rentalRoutes
import com.solodev.fleet.modules.rentals.infrastructure.persistence.CustomerRepositoryImpl
import com.solodev.fleet.modules.rentals.infrastructure.persistence.RentalRepositoryImpl
import com.solodev.fleet.modules.users.infrastructure.http.userRoutes
import com.solodev.fleet.modules.users.infrastructure.persistence.UserRepositoryImpl
import com.solodev.fleet.modules.users.infrastructure.persistence.VerificationTokenRepositoryImpl
import com.solodev.fleet.modules.vehicles.infrastructure.http.vehicleRoutes
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehicleRepositoryImpl
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import com.solodev.fleet.shared.utils.JwtService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
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
    val invoiceRepo = InvoiceRepositoryImpl()
    val paymentRepo = PaymentRepositoryImpl()
    val accountRepo = AccountRepositoryImpl()
    val ledgerRepo = LedgerRepositoryImpl()
    val paymentMethodRepo = PaymentMethodRepositoryImpl()

    routing {

        rateLimit(RateLimitName("public_api")) {
            vehicleRoutes(vehicleRepo)
            rentalRoutes(rentalRepository = rentalRepo, vehicleRepository = vehicleRepo)
            customerRoutes(customerRepository = customerRepo)
            maintenanceRoutes(maintenanceRepository = maintenanceRepo)
        }

        rateLimit(RateLimitName("auth_strict")) {
            userRoutes(userRepository = userRepo, tokenRepository = tokenRepo, jwtService = jwtService)
        }

        authenticate("auth-jwt") {
            rateLimit(RateLimitName("authenticated_api")) {
                accountingRoutes(
                    invoiceRepository = invoiceRepo,
                    paymentRepository = paymentRepo,
                    accountRepository = accountRepo,
                    ledgerRepository = ledgerRepo,
                    paymentMethodRepository = paymentMethodRepo
                )
            }
        }


        rateLimit {
            get("/") {
                call.respond(
                    ApiResponse.success(
                        mapOf("message" to "Fleet Management API v1"),
                        call.requestId
                    )
                )
            }
        }


        get("/health") {
            call.respond(
                ApiResponse.success(data = mapOf("status" to "OK"), requestId = call.requestId)
            )
        }
    }
}
