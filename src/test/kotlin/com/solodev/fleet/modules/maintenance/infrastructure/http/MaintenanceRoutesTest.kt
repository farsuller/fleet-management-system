package com.solodev.fleet.modules.maintenance.infrastructure.http

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.rentals.domain.repository.RentalWithDetails
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.shared.plugins.configureSerialization
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class MaintenanceRoutesTest {

    private val maintenanceRepository = mockk<MaintenanceRepository>()
    private val rentalRepository = mockk<RentalRepository>()

    private val jwtSecret = "test-secret-at-least-64-bytes-long-for-hmac-sha256-security-1234567890"
    private val jwtIssuer = "test-issuer"
    private val jwtAudience = "test-audience"

    private fun generateToken(userId: String = UUID.randomUUID().toString()): String {
        return JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withClaim("id", userId)
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    private fun Application.testModule() {
        configureSerialization()
        
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(
                    JWT.require(Algorithm.HMAC256(jwtSecret))
                        .withIssuer(jwtIssuer)
                        .withAudience(jwtAudience)
                        .build()
                )
                validate { credential ->
                    if (credential.payload.getClaim("id").asString() != null) {
                        JWTPrincipal(credential.payload)
                    } else null
                }
            }
        }

        routing {
            maintenanceRoutes(maintenanceRepository, rentalRepository)
        }
    }

    @Test
    fun `GET maintenance job by id should return enriched data and usage history`() = testApplication {
        application { testModule() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val jobId = "job-123"
        val vehicleId = "v-456"
        val plate = "ABC-123"
        
        val maintenanceJob = MaintenanceJob(
            id = MaintenanceJobId(jobId),
            jobNumber = "JOB-001",
            vehicleId = VehicleId(vehicleId),
            vehiclePlate = plate,
            vehicleMake = "Toyota",
            vehicleModel = "Hilux",
            status = MaintenanceStatus.SCHEDULED,
            jobType = MaintenanceJobType.PREVENTIVE,
            description = "Maintenance",
            priority = MaintenancePriority.NORMAL,
            scheduledDate = Instant.now()
        )

        val rental = mockk<Rental>()
        every { rental.rentalNumber } returns "RENT-001"
        every { rental.status } returns RentalStatus.COMPLETED
        every { rental.startDate } returns Instant.now().minusSeconds(86400)
        every { rental.endDate } returns Instant.now().minusSeconds(3600)
        every { rental.startOdometerKm } returns 1000
        every { rental.endOdometerKm } returns 1100
        
        val rentalWithDetails = RentalWithDetails(
            rental = rental,
            vehiclePlateNumber = plate,
            vehicleMake = "Toyota",
            vehicleModel = "Hilux",
            customerName = "John Doe"
        )
        
        coEvery { maintenanceRepository.findById(MaintenanceJobId(jobId)) } returns maintenanceJob
        coEvery { 
            rentalRepository.findAllPaged(
                page = 1,
                limit = 50,
                vehicleId = any(),
                status = null,
                customerId = null
            )
        } returns listOf(rentalWithDetails)

        val response = client.get("/v1/maintenance/jobs/$jobId") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val bodyString = response.bodyAsText()
        val body = Json.parseToJsonElement(bodyString).jsonObject
        
        val data = body["data"]?.jsonObject
        assertThat(data).isNotNull
        assertThat(data?.get("vehiclePlate")?.jsonPrimitive?.content).isEqualTo(plate)
        assertThat(data?.get("vehicleMake")?.jsonPrimitive?.content).isEqualTo("Toyota")
        
        val usageHistory = data?.get("usageHistory")?.jsonArray
        assertThat(usageHistory).isNotNull
        assertThat(usageHistory?.size).isEqualTo(1)
        assertThat(usageHistory?.get(0)?.jsonObject?.get("status")?.jsonPrimitive?.content).isEqualTo("COMPLETED")
    }
}
