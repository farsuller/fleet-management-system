package com.solodev.fleet.modules.vehicles.infrastructure.http

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.configurePostgres
import com.solodev.fleet.module
import com.solodev.fleet.modules.vehicles.application.dto.BusRequest
import com.solodev.fleet.modules.vehicles.application.dto.BusResponse
import com.solodev.fleet.modules.vehicles.application.dto.BusUpdateRequest
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.BusesTable
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
import kotlin.test.assertTrue

class BusIntegrationTest : IntegrationTestBase() {
    private val adminId = UUID.randomUUID()
    private val adminEmail = "admin@fleet.ph"

    @BeforeEach
    fun setup() {
        cleanDatabase()
    }

    private fun seedBus(plate: String = "BUS-123"): UUID {
        val id = UUID.randomUUID()
        transaction {
            VehiclesTable.insert {
                it[VehiclesTable.id] = id
                it[plateNumber] = plate
                it[make] = "Hino"
                it[model] = "Grand Coach"
                it[year] = 2024
                it[vehicleType] = "BUS"
                it[status] = "AVAILABLE"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
            BusesTable.insert {
                it[vehicleId] = id
                it[routeNumber] = "RT-101"
                it[doorCount] = 2
                it[standingCapacity] = 20
                it[hasAccessibilityRamp] = true
                it[hasAirConditioning] = true
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    @Test
    fun `should create a bus`() =
        testApplication {
            configurePostgres()
            application { module() }

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }

            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val request =
                BusRequest(
                    licensePlate = "BUS-789",
                    make = "Isuzu",
                    model = "F-Series",
                    year = 2023,
                    passengerCapacity = 50,
                    routeNumber = "RT-202",
                    doorCount = 2,
                    standingCapacity = 15,
                    hasAccessibilityRamp = true,
                    hasAirConditioning = true,
                )

            client
                .post("/v1/buses") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.let { response ->
                    assertEquals(HttpStatusCode.Created, response.status)
                    val apiResponse = response.body<ApiResponse<BusResponse>>()
                    assertTrue(apiResponse.success)
                    assertEquals("BUS-789", apiResponse.data!!.licensePlate)
                    assertEquals("RT-202", apiResponse.data!!.routeNumber)
                }
        }

    @Test
    fun `should list buses`() =
        testApplication {
            configurePostgres()
            application { module() }
            seedBus("BUS-001")
            seedBus("BUS-002")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/buses") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<PaginatedResponse<BusResponse>>>()
                    assertTrue(apiResponse.success)
                    assertEquals(2, apiResponse.data!!.items.size)
                }
        }

    @Test
    fun `should get bus by id`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedBus("BUS-GET-123")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/buses/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<BusResponse>>()
                    assertEquals("BUS-GET-123", apiResponse.data!!.licensePlate)
                    assertEquals("RT-101", apiResponse.data!!.routeNumber)
                }
        }

    @Test
    fun `should update bus`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedBus("BUS-UPDATE-123")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val updateRequest =
                BusUpdateRequest(
                    routeNumber = "RT-999",
                    standingCapacity = 30,
                )

            client
                .patch("/v1/buses/$id") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<BusResponse>>()
                    assertEquals("RT-999", apiResponse.data!!.routeNumber)
                    assertEquals(30, apiResponse.data!!.standingCapacity)
                }
        }

    @Test
    fun `should delete bus`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedBus("BUS-DELETE-123")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .delete("/v1/buses/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                }

            // Verify it's gone
            client
                .get("/v1/buses/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.NotFound, response.status)
                }
        }
}
