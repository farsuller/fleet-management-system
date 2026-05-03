package com.solodev.fleet.modules.rentals.infrastructure.http

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.configurePostgres
import com.solodev.fleet.module
import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import com.solodev.fleet.modules.rentals.application.dto.RentalResponse
import com.solodev.fleet.modules.rentals.infrastructure.persistence.CustomersTable
import com.solodev.fleet.modules.rentals.infrastructure.persistence.RentalsTable
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import com.solodev.fleet.shared.models.ApiResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RentalIntegrationTest : IntegrationTestBase() {
    private val adminId = UUID.randomUUID()
    private val adminEmail = "admin@fleet.ph"

    @BeforeEach
    fun setup() {
        cleanDatabase()
    }

    private fun seedVehicle(plate: String = "V-123"): UUID {
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

    private fun seedCustomer(email: String = "cust@example.com"): UUID {
        val id = UUID.randomUUID()
        transaction {
            CustomersTable.insert {
                it[CustomersTable.id] = id
                it[firstName] = "Juan"
                it[lastName] = "Dela Cruz"
                it[CustomersTable.email] = email
                it[phone] = "+639123456789"
                it[driverLicenseNumber] = "CL-" + UUID.randomUUID().toString().take(8)
                it[driverLicenseExpiry] = LocalDate.now().plusYears(5)
                it[isActive] = true
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    private fun seedRental(
        vehicleId: UUID,
        customerId: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        transaction {
            RentalsTable.insert {
                it[RentalsTable.id] = id
                it[rentalNumber] = "RENT-" + UUID.randomUUID().toString().take(8)
                it[RentalsTable.vehicleId] = vehicleId
                it[RentalsTable.customerId] = customerId
                it[status] = "PENDING"
                it[startDate] = Instant.now().plus(1, ChronoUnit.DAYS)
                it[endDate] = Instant.now().plus(3, ChronoUnit.DAYS)
                it[dailyRate] = 250000 // 2500.00
                it[totalAmount] = 500000 // 5000.00
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    @Test
    fun `should create a rental`() =
        testApplication {
            configurePostgres()
            application { module() }
            val vId = seedVehicle("RENT-NEW")
            val cId = seedCustomer("new@example.com")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }

            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
            val end = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)

            val request =
                RentalRequest(
                    vehicleId = vId.toString(),
                    customerId = cId.toString(),
                    startDate = start.toString(),
                    endDate = end.toString(),
                )

            client
                .post("/v1/rentals") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.let { response ->
                    assertEquals(HttpStatusCode.Created, response.status)
                    val apiResponse = response.body<ApiResponse<RentalResponse>>()
                    assertTrue(apiResponse.success)
                    assertEquals(vId.toString(), apiResponse.data!!.vehicleId)
                }
        }

    @Test
    fun `should list rentals`() =
        testApplication {
            configurePostgres()
            application { module() }
            val vId = seedVehicle("LIST-V")
            val cId = seedCustomer("list@example.com")
            seedRental(vId, cId)

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/rentals") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<List<RentalResponse>>>()
                    assertTrue(apiResponse.success)
                    assertEquals(1, apiResponse.data!!.size)
                }
        }

    @Test
    fun `should get rental by id`() =
        testApplication {
            configurePostgres()
            application { module() }
            val vId = seedVehicle("GET-V")
            val cId = seedCustomer("get-r@example.com")
            val rId = seedRental(vId, cId)

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/rentals/$rId") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<RentalResponse>>()
                    assertEquals(rId.toString(), apiResponse.data!!.id)
                }
        }

    @Test
    fun `should delete rental`() =
        testApplication {
            configurePostgres()
            application { module() }
            val vId = seedVehicle("DEL-V")
            val cId = seedCustomer("del-r@example.com")
            val rId = seedRental(vId, cId)

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .delete("/v1/rentals/$rId") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                }

            // Verify it's gone
            client
                .get("/v1/rentals/$rId") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.NotFound, response.status)
                }
        }
}
