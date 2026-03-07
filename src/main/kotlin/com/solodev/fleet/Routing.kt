package com.solodev.fleet

import com.solodev.fleet.modules.accounts.application.AccountingService
import com.solodev.fleet.modules.accounts.application.ReconciliationService
import com.solodev.fleet.modules.accounts.infrastructure.http.accountingRoutes
import com.solodev.fleet.modules.accounts.infrastructure.persistence.*
import com.solodev.fleet.modules.maintenance.infrastructure.http.maintenanceRoutes
import com.solodev.fleet.modules.maintenance.infrastructure.persistence.MaintenanceRepositoryImpl
import com.solodev.fleet.modules.rentals.infrastructure.http.customerRoutes
import com.solodev.fleet.modules.rentals.infrastructure.http.rentalRoutes
import com.solodev.fleet.modules.rentals.infrastructure.persistence.CustomerRepositoryImpl
import com.solodev.fleet.modules.rentals.infrastructure.persistence.RentalRepositoryImpl
import com.solodev.fleet.modules.tracking.application.usecases.UpdateVehicleLocationUseCase
import com.solodev.fleet.modules.tracking.infrastructure.http.trackingRoutes
import com.solodev.fleet.modules.tracking.infrastructure.metrics.SpatialMetrics
import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.modules.tracking.infrastructure.persistence.LocationHistoryRepository
import com.solodev.fleet.modules.tracking.infrastructure.websocket.RedisDeltaBroadcaster
import com.solodev.fleet.modules.users.infrastructure.http.userRoutes
import com.solodev.fleet.modules.users.infrastructure.persistence.UserRepositoryImpl
import com.solodev.fleet.modules.users.infrastructure.persistence.VerificationTokenRepositoryImpl
import com.solodev.fleet.modules.vehicles.infrastructure.http.vehicleRoutes
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehicleRepositoryImpl
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import com.solodev.fleet.shared.utils.JwtService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.MeterRegistry
import redis.clients.jedis.JedisPool

/** Configures the application's routing. */
fun Application.configureRouting(
    jwtService: JwtService,
    vehicleRepo: VehicleRepositoryImpl,
    jedisPool: JedisPool?,
    registry: MeterRegistry
) {

    // Initialize other repositories
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

    val accountingService = AccountingService(accountRepo = accountRepo, ledgerRepo = ledgerRepo)
    val reconciliationService =
            ReconciliationService(
                    invoiceRepo = invoiceRepo,
                    accountRepo = accountRepo,
                    ledgerRepo = ledgerRepo
            )

    // Phase 7: Tracking & Live Broadcasting
    val spatialAdapter = PostGISAdapter()
    val redisCache = RedisCacheManager(jedisPool)
    val spatialMetrics = SpatialMetrics(registry) // Micrometer registry from observability
    val deltaBroadcaster = RedisDeltaBroadcaster(redisCache, vehicleRepo, jedisPool)
    val locationHistoryRepository = LocationHistoryRepository()  // Persist tracking records
    val updateVehicleLocation = UpdateVehicleLocationUseCase(
        postGISAdapter = spatialAdapter,
        broadcaster = deltaBroadcaster,
        metrics = spatialMetrics,
        historyRepository = locationHistoryRepository
    )
    routing {
        // Interactive API Documentation
        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")

        rateLimit(RateLimitName("public_api")) {
            vehicleRoutes(vehicleRepo)
            rentalRoutes(
                    rentalRepository = rentalRepo,
                    vehicleRepository = vehicleRepo,
                    accountingService = accountingService,
            )
            customerRoutes(customerRepository = customerRepo)
            maintenanceRoutes(maintenanceRepository = maintenanceRepo)
            trackingRoutes(
                updateVehicleLocation = updateVehicleLocation,
                spatialAdapter = spatialAdapter,
                deltaBroadcaster = deltaBroadcaster,
                historyRepository = locationHistoryRepository
            )
        }

        rateLimit(RateLimitName("auth_strict")) {
            userRoutes(
                    userRepository = userRepo,
                    tokenRepository = tokenRepo,
                    jwtService = jwtService
            )
        }

        authenticate("auth-jwt") {
            rateLimit(RateLimitName("authenticated_api")) {
                accountingRoutes(
                        invoiceRepository = invoiceRepo,
                        paymentRepository = paymentRepo,
                        accountRepository = accountRepo,
                        ledgerRepository = ledgerRepo,
                        paymentMethodRepository = paymentMethodRepo,
                        reconciliationService = reconciliationService
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
