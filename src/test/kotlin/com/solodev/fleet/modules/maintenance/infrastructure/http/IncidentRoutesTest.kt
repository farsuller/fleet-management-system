package com.solodev.fleet.modules.maintenance.infrastructure.http

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.solodev.fleet.modules.maintenance.application.dto.ReportIncidentRequest
import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.plugins.configureSerialization
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class IncidentRoutesTest {

    private val maintenanceRepository = mockk<MaintenanceRepository>()
    private val vehicleRepository = mockk<VehicleRepository>()

    private val jwtSecret = "test-secret-at-least-64-bytes-long-for-hmac-sha256-security-1234567890"
    private val jwtIssuer = "test-issuer"
    private val jwtAudience = "test-audience"

    private fun generateToken(userId: String = UUID.randomUUID().toString()): String {
        return JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withClaim("id", userId) // The 'validate' block in Security.kt checks for "id"
            .withClaim("userId", userId) // The 'incidentRoutes' block checks for "userId"
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
            incidentRoutes(maintenanceRepository, vehicleRepository)
        }
    }

    @Test
    fun `POST should report incident successfully`() = testApplication {
        application { testModule() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val plate = "B1234XYZ"
        val request = ReportIncidentRequest(
            title = "Flat Tire",
            description = "Hit a pothole",
            severity = "MEDIUM",
            odometerKm = 50000
        )

        coEvery { maintenanceRepository.saveIncident(any()) } answers { firstArg() }

        val response = client.post("/v1/vehicles/$plate/incidents") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val bodyString = response.bodyAsText()
        val body = Json.parseToJsonElement(bodyString).jsonObject
        assertThat(body["success"]?.jsonPrimitive?.boolean).isTrue()
        
        coVerify(exactly = 1) { maintenanceRepository.saveIncident(match { 
            it.vehicleId.value == plate && it.title == "Flat Tire" 
        }) }
    }

    @Test
    fun `GET should list incidents for a vehicle`() = testApplication {
        application { testModule() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val vehicleId = "v-1"
        val plate = "B1234XYZ"
        
        val vehicle = mockk<Vehicle>()
        every { vehicle.licensePlate } returns plate
        coEvery { vehicleRepository.findById(VehicleId(vehicleId)) } returns vehicle
        
        val incident = VehicleIncident(
            id = IncidentId(UUID.randomUUID()),
            vehicleId = VehicleId(vehicleId),
            title = "Engine Issue",
            description = "...",
            severity = IncidentSeverity.HIGH,
            status = IncidentStatus.REPORTED,
            reportedAt = Instant.now(),
            reportedByUserId = null
        )
        coEvery { maintenanceRepository.findIncidentsByVehicleId(VehicleId(vehicleId)) } returns listOf(incident)

        val response = client.get("/v1/vehicles/$vehicleId/incidents") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertThat(body["success"]?.jsonPrimitive?.boolean).isTrue()
        
        val data = body["data"]?.jsonArray
        assertThat(data).isNotNull
        assertThat(data?.size).isEqualTo(1)
        assertThat(data?.get(0)?.jsonObject?.get("vehiclePlate")?.jsonPrimitive?.content).isEqualTo(plate)
    }

    @Test
    fun `POST should return 400 for invalid severity`() = testApplication {
        application { testModule() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.post("/v1/vehicles/PLATE/incidents") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
            contentType(ContentType.Application.Json)
            setBody(ReportIncidentRequest("Title", "Desc", "INVALID_SEV"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val bodyString = response.bodyAsText()
        val body = Json.parseToJsonElement(bodyString).jsonObject
        assertThat(body["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content).isEqualTo("INVALID_SEVERITY")
    }
}
