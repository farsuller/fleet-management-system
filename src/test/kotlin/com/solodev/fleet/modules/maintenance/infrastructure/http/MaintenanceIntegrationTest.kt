package com.solodev.fleet.modules.maintenance.infrastructure.http

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.configurePostgres
import com.solodev.fleet.module
import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceRequest
import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceResponse
import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceStatusUpdateRequest
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobType
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePriority
import com.solodev.fleet.modules.maintenance.infrastructure.persistence.MaintenanceJobsTable
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import com.solodev.fleet.shared.models.ApiResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MaintenanceIntegrationTest : IntegrationTestBase() {
    private val adminId = UUID.randomUUID()
    private val adminEmail = "admin@fleet.ph"
    private lateinit var testVehicleId: UUID

    @BeforeEach
    fun setup() {
        cleanDatabase()
        testVehicleId = seedVehicle()
    }

    private fun seedVehicle(): UUID {
        val id = UUID.randomUUID()
        transaction {
            VehiclesTable.insert {
                it[VehiclesTable.id] = id
                it[plateNumber] = "MAIN-" + UUID.randomUUID().toString().take(5)
                it[make] = "Toyota"
                it[model] = "Coaster"
                it[year] = 2024
                it[status] = "AVAILABLE"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    private fun seedMaintenanceJob(): UUID {
        val id = UUID.randomUUID()
        transaction {
            MaintenanceJobsTable.insert {
                it[MaintenanceJobsTable.id] = id
                it[jobNumber] = "JOB-" + UUID.randomUUID().toString().take(8)
                it[vehicleId] = testVehicleId
                it[status] = "SCHEDULED"
                it[jobType] = "PREVENTIVE"
                it[description] = "Routine oil change and filter replacement"
                it[priority] = "NORMAL"
                it[scheduledDate] = Instant.now().plusSeconds(86400)
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    @Disabled("Temporarily disabled pending module enhancements")
    @Test
    fun `should schedule a maintenance job`() =
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
                MaintenanceRequest(
                    vehicleId = testVehicleId.toString(),
                    type = MaintenanceJobType.PREVENTIVE,
                    priority = MaintenancePriority.NORMAL,
                    description = "Test maintenance job description",
                    scheduledDate = Instant.now().plusSeconds(86400).toEpochMilli(),
                    estimatedCostPhp = 500000, // 5000.00
                )

            client
                .post("/v1/maintenance/jobs") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.let { response ->
                    assertEquals(HttpStatusCode.Created, response.status)
                    val apiResponse = response.body<ApiResponse<MaintenanceResponse>>()
                    assertTrue(apiResponse.success)
                    assertEquals("SCHEDULED", apiResponse.data!!.status.name)
                }
        }

    @Disabled("Temporarily disabled pending module enhancements")
    @Test
    fun `should start a maintenance job`() =
        testApplication {
            configurePostgres()
            application { module() }
            val jobId = seedMaintenanceJob()

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .post("/v1/maintenance/jobs/$jobId/start") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<MaintenanceResponse>>()
                    assertEquals("IN_PROGRESS", apiResponse.data!!.status.name)
                    assertNotNull(apiResponse.data!!.startedAt)
                }
        }

    @Disabled("Temporarily disabled pending module enhancements")
    @Test
    fun `should complete a maintenance job`() =
        testApplication {
            configurePostgres()
            application { module() }
            val jobId = seedMaintenanceJob()

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            // Start it first
            client.post("/v1/maintenance/jobs/$jobId/start") { bearerAuth(token) }

            val completeRequest =
                MaintenanceStatusUpdateRequest(
                    laborCostPhp = 200000,
                    partsCostPhp = 300000,
                )

            client
                .post("/v1/maintenance/jobs/$jobId/complete") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(completeRequest)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<MaintenanceResponse>>()
                    assertEquals("COMPLETED", apiResponse.data!!.status.name)
                    assertNotNull(apiResponse.data!!.completedAt)
                    assertEquals(500000L, apiResponse.data!!.totalCostPhp)
                }
        }

    @Disabled("Temporarily disabled pending module enhancements")
    @Test
    fun `should cancel a maintenance job`() =
        testApplication {
            configurePostgres()
            application { module() }
            val jobId = seedMaintenanceJob()

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .post("/v1/maintenance/jobs/$jobId/cancel") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<MaintenanceResponse>>()
                    assertEquals("CANCELLED", apiResponse.data!!.status.name)
                }
        }

    @Disabled("Temporarily disabled pending module enhancements")
    @Test
    fun `should list all maintenance jobs`() =
        testApplication {
            configurePostgres()
            application { module() }
            seedMaintenanceJob()
            seedMaintenanceJob()

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/maintenance/jobs") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<List<MaintenanceResponse>>>()
                    assertTrue(apiResponse.success)
                    assertTrue(apiResponse.data!!.size >= 2)
                }
        }
}
