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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RentalIntegrationTest : IntegrationTestBase() {
    private val adminId = UUID.randomUUID()
    private val adminEmail = "admin@fleet.ph"

    private lateinit var testVehicleId: UUID
    private lateinit var testCustomerId: UUID

    @BeforeEach
    fun setup() {
        cleanDatabase()
        testVehicleId = seedVehicle()
        testCustomerId = seedCustomer()
    }

    private fun seedVehicle(): UUID {
        val id = UUID.randomUUID()
        transaction {
            VehiclesTable.insert {
                it[VehiclesTable.id] = id
                it[plateNumber] = "RENT-" + UUID.randomUUID().toString().take(5)
                it[make] = "Toyota"
                it[model] = "Vios"
                it[year] = 2024
                it[status] = "AVAILABLE"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    private fun seedCustomer(): UUID {
        val id = UUID.randomUUID()
        transaction {
            CustomersTable.insert {
                it[CustomersTable.id] = id
                it[firstName] = "Rental"
                it[lastName] = "Tester"
                it[email] = "rental@tester.ph"
                it[phone] = "+6392222222222"
                it[driverLicenseNumber] = "DL-" + UUID.randomUUID().toString().take(8)
                it[driverLicenseExpiry] = LocalDate.now().plusYears(1)
                it[isActive] = true
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    private fun seedRental(): UUID {
        val id = UUID.randomUUID()
        transaction {
            RentalsTable.insert {
                it[RentalsTable.id] = id
                it[rentalNumber] = "RN-" + UUID.randomUUID().toString().take(8)
                it[customerId] = testCustomerId
                it[vehicleId] = testVehicleId
                it[status] = "RESERVED"
                it[startDate] = Instant.now().plusSeconds(3600)
                it[endDate] = Instant.now().plusSeconds(86400)
                it[dailyRate] = 1500
                it[totalAmount] = 1500
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

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val request =
                RentalRequest(
                    customerId = testCustomerId.toString(),
                    vehicleId = testVehicleId.toString(),
                    startDate = Instant.now().plusSeconds(3600).toString(),
                    endDate = Instant.now().plusSeconds(86400).toString(),
                    dailyRateAmount = 1500L,
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
                    assertEquals("RESERVED", apiResponse.data!!.status)
                }
        }

    @Test
    fun `should activate a rental`() =
        testApplication {
            configurePostgres()
            application { module() }
            val rentalId = seedRental()

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .post("/v1/rentals/$rentalId/activate") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<RentalResponse>>()
                    assertEquals("ACTIVE", apiResponse.data!!.status)
                    assertNotNull(apiResponse.data!!.actualStartDate)
                }
        }

    @Test
    fun `should complete a rental`() =
        testApplication {
            configurePostgres()
            application { module() }
            val rentalId = seedRental()

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            client.post("/v1/rentals/$rentalId/activate") {
                bearerAuth(token)
            }

            client
                .post("/v1/rentals/$rentalId/complete") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("endOdometer" to 1000))
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<RentalResponse>>()
                    assertEquals("COMPLETED", apiResponse.data!!.status)
                    assertEquals(1000, apiResponse.data!!.endOdometerKm)
                }
        }

    @Test
    fun `should cancel a rental`() =
        testApplication {
            configurePostgres()
            application { module() }
            val rentalId = seedRental()

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .post("/v1/rentals/$rentalId/cancel") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<RentalResponse>>()
                    assertEquals("CANCELLED", apiResponse.data!!.status)
                }
        }
}
