package com.solodev.fleet.modules.vehicles.infrastructure.http

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.configurePostgres
import com.solodev.fleet.module
import com.solodev.fleet.modules.vehicles.application.dto.OdometerRequest
import com.solodev.fleet.modules.vehicles.application.dto.VehicleRequest
import com.solodev.fleet.modules.vehicles.application.dto.VehicleResponse
import com.solodev.fleet.modules.vehicles.application.dto.VehicleStateRequest
import com.solodev.fleet.modules.vehicles.application.dto.VehicleUpdateRequest
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.models.PaginatedResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VehicleIntegrationTest : IntegrationTestBase() {

    private val adminId = UUID.randomUUID()
    private val adminEmail = "admin@fleet.ph"

    @BeforeEach
    fun setup() {
        cleanDatabase()
    }

    private fun seedVehicle(plate: String = "TEST-123"): UUID {
        val id = UUID.randomUUID()
        transaction {
            VehiclesTable.insert {
                it[VehiclesTable.id] = id
                it[plateNumber] = plate
                it[make] = "Toyota"
                it[model] = "Innova"
                it[year] = 2024
                it[status] = "AVAILABLE"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    @Test
    fun `should create a vehicle`() = testApplication {
        configurePostgres()
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
        val request = VehicleRequest(
            licensePlate = "NEW-789",
            make = "Honda",
            model = "Civic",
            year = 2023,
            vehicleType = "SEDAN"
        )

        client.post("/v1/vehicles") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.let { response ->
            assertEquals(HttpStatusCode.Created, response.status)
            val apiResponse = response.body<ApiResponse<VehicleResponse>>()
            assertTrue(apiResponse.success)
            assertEquals("NEW-789", apiResponse.data!!.licensePlate)
        }
    }

    @Test
    fun `should list vehicles`() = testApplication {
        configurePostgres()
        application { module() }
        seedVehicle("LIST-001")
        seedVehicle("LIST-002")

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

        client.get("/v1/vehicles") {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val apiResponse = response.body<ApiResponse<PaginatedResponse<VehicleResponse>>>()
            assertTrue(apiResponse.success)
            assertEquals(2, apiResponse.data!!.items.size)
        }
    }

    @Test
    fun `should get vehicle by id`() = testApplication {
        configurePostgres()
        application { module() }
        val id = seedVehicle("GET-123")

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

        client.get("/v1/vehicles/$id") {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val apiResponse = response.body<ApiResponse<VehicleResponse>>()
            assertEquals("GET-123", apiResponse.data!!.licensePlate)
        }
    }

    @Test
    fun `should update vehicle`() = testApplication {
        configurePostgres()
        application { module() }
        val id = seedVehicle("UPDATE-123")

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
        val updateRequest = VehicleUpdateRequest(
            make = "Mitsubishi",
            model = "Montero"
        )

        client.patch("/v1/vehicles/$id") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(updateRequest)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val apiResponse = response.body<ApiResponse<VehicleResponse>>()
            assertEquals("Mitsubishi", apiResponse.data!!.make)
            assertEquals("Montero", apiResponse.data!!.model)
        }
    }

    @Test
    fun `should delete vehicle`() = testApplication {
        configurePostgres()
        application { module() }
        val id = seedVehicle("DELETE-123")

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

        client.delete("/v1/vehicles/$id") {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.NoContent, response.status)
        }

        // Verify it's gone
        client.get("/v1/vehicles/$id") {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `should update vehicle state`() = testApplication {
        configurePostgres()
        application { module() }
        val id = seedVehicle("STATE-123")

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
        val stateRequest = VehicleStateRequest(state = "RENTED")

        client.patch("/v1/vehicles/$id/state") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(stateRequest)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val apiResponse = response.body<ApiResponse<VehicleResponse>>()
            assertEquals("RENTED", apiResponse.data!!.state)
        }
    }

    @Test
    fun `should record odometer reading`() = testApplication {
        configurePostgres()
        application { module() }
        val id = seedVehicle("ODO-123")

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
        val odoRequest = OdometerRequest(mileageKm = 5000)

        client.post("/v1/vehicles/$id/odometer") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(odoRequest)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val apiResponse = response.body<ApiResponse<VehicleResponse>>()
            assertEquals(5000, apiResponse.data!!.mileageKm)
        }
    }
}
