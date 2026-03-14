package com.solodev.fleet.modules.drivers.infrastructure.http

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.module
import com.solodev.fleet.configurePostgres
import com.solodev.fleet.modules.drivers.application.dto.StartShiftRequest
import com.solodev.fleet.modules.drivers.application.dto.ShiftResponse
import com.solodev.fleet.modules.drivers.infrastructure.persistence.DriversTable
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import com.solodev.fleet.shared.models.ApiResponse
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriverShiftIntegrationTest : IntegrationTestBase() {

    private val driverId = UUID.randomUUID()
    private val vehicleId = UUID.randomUUID()
    private val driverEmail = "driver@fleet.ph"

    @BeforeEach
    fun setup() {
        cleanDatabase()
        seedData()
    }

    private fun seedData() {
        transaction {
            // Seed a vehicle
            VehiclesTable.insert {
                it[id] = vehicleId
                it[plateNumber] = "SHIFT-123"
                it[make] = "Toyota"
                it[model] = "Innova"
                it[year] = 2024
                it[status] = "AVAILABLE"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }

            // Seed a driver
            DriversTable.insert {
                it[id] = driverId
                it[firstName] = "Test"
                it[lastName] = "Driver"
                it[email] = driverEmail
                it[phone] = "+639000000000"
                it[licenseNumber] = "LIC-SHIFT-123"
                it[licenseExpiry] = LocalDate.now().plusYears(5)
                it[isActive] = true
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
    }

    @Test
    fun `should manage shift lifecycle`() = testApplication {
        configurePostgres()
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val token = tokenFor(driverId.toString(), driverEmail, "DRIVER")

        // 1. Get Active Shift (should be null)
        client.get("/v1/drivers/shifts/active") {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val apiResponse = response.body<ApiResponse<ShiftResponse?>>()
            assertTrue(apiResponse.success)
            assertNull(apiResponse.data)
        }

        // 2. Start Shift
        client.post("/v1/drivers/shifts/start") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(StartShiftRequest(vehicleId.toString(), "Starting test shift"))
        }.let { response ->
            assertEquals(HttpStatusCode.Created, response.status)
            val apiResponse = response.body<ApiResponse<ShiftResponse>>()
            assertTrue(apiResponse.success)
            assertEquals(vehicleId.toString(), apiResponse.data!!.vehicleId)
            assertEquals(driverId.toString(), apiResponse.data!!.driverId)
            assertNotNull(apiResponse.data!!.startedAt)
            assertNull(apiResponse.data!!.endedAt)
            assertTrue(apiResponse.data!!.isActive)
        }

        // 3. Get Active Shift (should be present)
        client.get("/v1/drivers/shifts/active") {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val apiResponse = response.body<ApiResponse<ShiftResponse?>>()
            assertTrue(apiResponse.success)
            assertNotNull(apiResponse.data)
            assertEquals(vehicleId.toString(), apiResponse.data!!.vehicleId)
        }

        // 4. End Shift
        client.post("/v1/drivers/shifts/end") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(mapOf("notes" to "Ending test shift"))
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val apiResponse = response.body<ApiResponse<ShiftResponse>>()
            assertTrue(apiResponse.success)
            assertNotNull(apiResponse.data!!.endedAt)
            assertTrue(!apiResponse.data!!.isActive)
        }

        // 5. Get Active Shift (should be null again)
        client.get("/v1/drivers/shifts/active") {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val apiResponse = response.body<ApiResponse<ShiftResponse?>>()
            assertTrue(apiResponse.success)
            assertNull(apiResponse.data)
        }
    }

    @Test
    fun `should fail to start shift when driver does not exist`() = testApplication {
        configurePostgres()
        application { module() }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val unknownDriverId = UUID.randomUUID()
        val token = tokenFor(unknownDriverId.toString(), "unknown@fleet.ph", "DRIVER")

        client.post("/v1/drivers/shifts/start") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(StartShiftRequest(vehicleId.toString(), "Starting test shift"))
        }.let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }
}
