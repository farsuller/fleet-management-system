package com.solodev.fleet.modules.vehicles.infrastructure.http

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.configurePostgres
import com.solodev.fleet.module
import com.solodev.fleet.modules.vehicles.application.dto.TruckRequest
import com.solodev.fleet.modules.vehicles.application.dto.TruckResponse
import com.solodev.fleet.modules.vehicles.application.dto.TruckUpdateRequest
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.TrucksTable
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TruckIntegrationTest : IntegrationTestBase() {
    private val adminId = UUID.randomUUID()
    private val adminEmail = "admin@fleet.ph"

    @BeforeEach
    fun setup() {
        cleanDatabase()
    }

    private fun seedTruck(plate: String = "TRK-123"): UUID {
        val id = UUID.randomUUID()
        transaction {
            VehiclesTable.insert {
                it[VehiclesTable.id] = id
                it[plateNumber] = plate
                it[make] = "Volvo"
                it[model] = "FH16"
                it[year] = 2024
                it[vehicleType] = "TRUCK"
                it[status] = "AVAILABLE"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
            TrucksTable.insert {
                it[vehicleId] = id
                it[payloadCapacityTons] = BigDecimal("25.5")
                it[cargoType] = "BOX"
                it[axleCount] = 4
                it[grossVehicleWeightKg] = 40000
                it[hasTrailerHitch] = true
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    @Test
    fun `should create a truck`() =
        testApplication {
            configurePostgres()
            application { module() }

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }

            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val request =
                TruckRequest(
                    licensePlate = "TRK-789",
                    make = "Scania",
                    model = "R500",
                    year = 2023,
                    payloadCapacityTons = 30.0,
                    cargoType = "REFRIGERATED",
                    axleCount = 3,
                    grossVehicleWeightKg = 35000,
                    hasTrailerHitch = true,
                )

            client
                .post("/v1/trucks") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.let { response ->
                    assertEquals(HttpStatusCode.Created, response.status)
                    val apiResponse = response.body<ApiResponse<TruckResponse>>()
                    assertTrue(apiResponse.success)
                    assertEquals("TRK-789", apiResponse.data!!.licensePlate)
                    assertEquals("REFRIGERATED", apiResponse.data!!.cargoType)
                }
        }

    @Test
    fun `should fail to create a truck with invalid cargo type`() =
        testApplication {
            configurePostgres()
            application { module() }

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }

            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val request =
                TruckRequest(
                    licensePlate = "TRK-INV",
                    make = "Scania",
                    model = "R500",
                    year = 2023,
                    cargoType = "INVALID_TYPE",
                )

            client
                .post("/v1/trucks") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.let { response ->
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                    val apiResponse = response.body<ApiResponse<Unit>>()
                    assertFalse(apiResponse.success)
                    assertTrue(apiResponse.error!!.message.contains("Invalid cargo type"))
                }
        }

    @Test
    fun `should list trucks`() =
        testApplication {
            configurePostgres()
            application { module() }
            seedTruck("TRK-001")
            seedTruck("TRK-002")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/trucks") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<PaginatedResponse<TruckResponse>>>()
                    assertTrue(apiResponse.success)
                    assertEquals(2, apiResponse.data!!.items.size)
                }
        }

    @Test
    fun `should get truck by id`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedTruck("TRK-GET-123")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/trucks/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<TruckResponse>>()
                    assertEquals("TRK-GET-123", apiResponse.data!!.licensePlate)
                    assertEquals("BOX", apiResponse.data!!.cargoType)
                }
        }

    @Test
    fun `should update truck`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedTruck("TRK-UPDATE-123")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val updateRequest =
                TruckUpdateRequest(
                    cargoType = "TANKER",
                    payloadCapacityTons = 15.0,
                )

            client
                .patch("/v1/trucks/$id") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<TruckResponse>>()
                    assertEquals("TANKER", apiResponse.data!!.cargoType)
                    assertEquals(15.0, apiResponse.data!!.payloadCapacityTons)
                }
        }

    @Test
    fun `should delete truck`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedTruck("TRK-DELETE-123")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .delete("/v1/trucks/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                }

            // Verify it's gone
            client
                .get("/v1/trucks/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.NotFound, response.status)
                }
        }
}
