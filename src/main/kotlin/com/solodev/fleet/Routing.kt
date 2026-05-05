package com.solodev.fleet

import com.solodev.fleet.modules.accounts.application.AccountingService
import com.solodev.fleet.modules.accounts.application.ReconciliationService
import com.solodev.fleet.modules.accounts.infrastructure.http.accountingRoutes
import com.solodev.fleet.modules.accounts.infrastructure.persistence.AccountRepositoryImpl
import com.solodev.fleet.modules.accounts.infrastructure.persistence.DriverRemittanceRepositoryImpl
import com.solodev.fleet.modules.accounts.infrastructure.persistence.InvoiceRepositoryImpl
import com.solodev.fleet.modules.accounts.infrastructure.persistence.LedgerRepositoryImpl
import com.solodev.fleet.modules.accounts.infrastructure.persistence.PaymentMethodRepositoryImpl
import com.solodev.fleet.modules.accounts.infrastructure.persistence.PaymentRepositoryImpl
import com.solodev.fleet.modules.drivers.infrastructure.http.driverRoutes
import com.solodev.fleet.modules.drivers.infrastructure.persistence.DriverRepositoryImpl
import com.solodev.fleet.modules.maintenance.infrastructure.http.incidentRoutes
import com.solodev.fleet.modules.maintenance.infrastructure.http.maintenanceRoutes
import com.solodev.fleet.modules.maintenance.infrastructure.persistence.MaintenanceRepositoryImpl
import com.solodev.fleet.modules.rentals.infrastructure.http.customerRoutes
import com.solodev.fleet.modules.rentals.infrastructure.http.rentalRoutes
import com.solodev.fleet.modules.rentals.infrastructure.persistence.CustomerRepositoryImpl
import com.solodev.fleet.modules.rentals.infrastructure.persistence.RentalRepositoryImpl
import com.solodev.fleet.modules.tracking.application.usecases.CoordinateReceptionService
import com.solodev.fleet.modules.tracking.application.usecases.UpdateVehicleLocationUseCase
import com.solodev.fleet.modules.tracking.infrastructure.http.trackingRoutes
import com.solodev.fleet.modules.tracking.infrastructure.metrics.SpatialMetrics
import com.solodev.fleet.modules.tracking.infrastructure.persistence.LocationHistoryRepository
import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGisAdapter
import com.solodev.fleet.modules.tracking.infrastructure.websocket.RedisDeltaBroadcaster
import com.solodev.fleet.modules.users.infrastructure.http.userRoutes
import com.solodev.fleet.modules.users.infrastructure.persistence.UserRepositoryImpl
import com.solodev.fleet.modules.users.infrastructure.persistence.VerificationTokenRepositoryImpl
import com.solodev.fleet.modules.vehicles.infrastructure.http.busRoutes
import com.solodev.fleet.modules.vehicles.infrastructure.http.truckRoutes
import com.solodev.fleet.modules.vehicles.infrastructure.http.vehicleRoutes
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.BusRepositoryImpl
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.TruckRepositoryImpl
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehicleRepositoryImpl
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import com.solodev.fleet.shared.utils.JwtService
import com.solodev.fleet.shared.utils.RsaDecryptor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.MeterRegistry
import redis.clients.jedis.JedisPool

/** Configures the application's routing. */
fun Application.configureRouting(
    jwtService: JwtService,
    vehicleRepo: VehicleRepositoryImpl,
    jedisPool: JedisPool?,
    registry: MeterRegistry,
    emailService: com.solodev.fleet.shared.infrastructure.email.EmailService,
    cacheManager: RedisCacheManager?,
    idempotencyManager: com.solodev.fleet.modules.tracking.infrastructure.idempotency.IdempotencyKeyManager,
) {
    // ... rest of repository initializations ...
    val rentalRepo = RentalRepositoryImpl()
    val userRepo = UserRepositoryImpl()
    val tokenRepo = VerificationTokenRepositoryImpl()
    val customerRepo = CustomerRepositoryImpl()
    val maintenanceRepo = MaintenanceRepositoryImpl()
    val driverRepo = DriverRepositoryImpl()
    val busRepo = BusRepositoryImpl(vehicleRepo)
    val truckRepo = TruckRepositoryImpl(vehicleRepo)
    val invoiceRepo = InvoiceRepositoryImpl()
    val paymentRepo = PaymentRepositoryImpl()
    val accountRepo = AccountRepositoryImpl()
    val ledgerRepo = LedgerRepositoryImpl()
    val paymentMethodRepo = PaymentMethodRepositoryImpl()
    val remittanceRepo = DriverRemittanceRepositoryImpl()

    val accountingService = AccountingService(accountRepo = accountRepo, ledgerRepo = ledgerRepo)
    // ... rest of service initializations ...
    val issueInvoiceUseCase =
        com.solodev.fleet.modules.accounts.application.usecases.IssueInvoiceUseCase(
            invoiceRepo = invoiceRepo,
            accountRepo = accountRepo,
            ledgerRepo = ledgerRepo,
        )
    val reconciliationService =
        ReconciliationService(
            invoiceRepo = invoiceRepo,
            accountRepo = accountRepo,
            ledgerRepo = ledgerRepo,
        )

    // Phase 7: Tracking & Live Broadcasting
    val spatialAdapter = PostGisAdapter()
    val redisCache = cacheManager ?: RedisCacheManager(jedisPool)
    val spatialMetrics = SpatialMetrics(registry) // Micrometer registry from observability
    val deltaBroadcaster = RedisDeltaBroadcaster(redisCache, vehicleRepo, jedisPool)
    val locationHistoryRepository = LocationHistoryRepository() // Persist tracking records
    val coordinateReceptionService = CoordinateReceptionService(redisCache)
    val updateVehicleLocation =
        UpdateVehicleLocationUseCase(
            postGISAdapter = spatialAdapter,
            broadcaster = deltaBroadcaster,
            metrics = spatialMetrics,
            historyRepository = locationHistoryRepository,
            receptionService = coordinateReceptionService,
        )
    routing {
        // Interactive API Documentation
        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")

        rateLimit(RateLimitName("public_api")) {
            vehicleRoutes(vehicleRepo)
            busRoutes(busRepo, driverRepo)
            truckRoutes(truckRepo, driverRepo)
            rentalRoutes(
                rentalRepository = rentalRepo,
                vehicleRepository = vehicleRepo,
                accountingService = accountingService,
                issueInvoiceUseCase = issueInvoiceUseCase,
                invoiceRepository = invoiceRepo,
            )
            customerRoutes(
                customerRepository = customerRepo,
                rentalRepository = rentalRepo,
                vehicleRepository = vehicleRepo,
                driverRepository = driverRepo,
                userRepository = userRepo,
                tokenRepository = tokenRepo,
            )
            driverRoutes(
                driverRepository = driverRepo,
                userRepository = userRepo,
                tokenRepository = tokenRepo,
                jwtService = jwtService,
                vehicleRepository = vehicleRepo,
                emailService = emailService,
            )
            maintenanceRoutes(maintenanceRepository = maintenanceRepo, rentalRepository = rentalRepo)
            incidentRoutes(maintenanceRepository = maintenanceRepo, vehicleRepository = vehicleRepo)
            trackingRoutes(
                updateVehicleLocation = updateVehicleLocation,
                spatialAdapter = spatialAdapter,
                deltaBroadcaster = deltaBroadcaster,
                vehicleRepository = vehicleRepo,
                historyRepository = locationHistoryRepository,
                receptionService = coordinateReceptionService,
                idempotencyManager = idempotencyManager,
            )
        }

        rateLimit(RateLimitName("auth_strict")) {
            val rsaPublicKey =
                environment.config.propertyOrNull("rsa.publicKey")?.getString()
                    ?: System.getenv("RSA_PUBLIC_KEY") ?: System.getProperty("RSA_PUBLIC_KEY")
            val rsaPrivateKeyPem =
                environment.config.propertyOrNull("rsa.privateKey")?.getString()
                    ?: System.getenv("RSA_PRIVATE_KEY") ?: System.getProperty("RSA_PRIVATE_KEY")
            val rsaPrivateKey = rsaPrivateKeyPem?.let { RsaDecryptor.loadPrivateKey(it) }

            userRoutes(
                userRepository = userRepo,
                tokenRepository = tokenRepo,
                jwtService = jwtService,
                emailService = emailService,
                publicKey = rsaPublicKey,
                privateKey = rsaPrivateKey,
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
                    customerRepository = customerRepo,
                    rentalRepository = rentalRepo,
                    vehicleRepository = vehicleRepo,
                    remittanceRepository = remittanceRepo,
                    reconciliationService = reconciliationService,
                )
            }
        }

        rateLimit {
            get("/") {
                call.respond(
                    ApiResponse.success(
                        mapOf("message" to "Fleet Management API v1"),
                        call.requestId,
                    ),
                )
            }
        }

        get("/health") {
            call.respond(
                ApiResponse.success(data = mapOf("status" to "OK"), requestId = call.requestId),
            )
        }

        // Dummy route for development tools (Vite/Webpack) heartbeats
        get("/ws") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }
    }
}
