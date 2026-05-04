package com.solodev.fleet.modules.drivers.infrastructure.http

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.configurePostgres
import com.solodev.fleet.module
import com.solodev.fleet.modules.drivers.application.dto.DriverRequest
import com.solodev.fleet.modules.drivers.application.dto.DriverResponse
import com.solodev.fleet.modules.drivers.application.dto.UpdateDriverRequest
import com.solodev.fleet.modules.drivers.infrastructure.persistence.DriversTable
import com.solodev.fleet.shared.models.ApiResponse
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
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriverIntegrationTest : IntegrationTestBase() {
    private val adminId = UUID.randomUUID()
    private val adminEmail = "admin@fleet.ph"

    @BeforeEach
    fun setup() {
        cleanDatabase()
    }

    private fun seedDriver(
        email: String = "driver@fleet.ph",
        license: String = "L12345",
    ): UUID {
        val id = UUID.randomUUID()
        transaction {
            DriversTable.insert {
                it[DriversTable.id] = id
                it[firstName] = "Juan"
                it[lastName] = "Dela Cruz"
                it[DriversTable.email] = email
                it[phone] = "+639123456789"
                it[licenseNumber] = license
                it[licenseExpiry] = LocalDate.now().plusYears(5)
                it[availabilityStatus] = true
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    @Test
    fun `should create a driver`() =
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
                DriverRequest(
                    email = "new-driver@fleet.ph",
                    firstName = "Maria",
                    lastName = "Santos",
                    phone = "+639987654321",
                    licenseNumber = "N54321",
                    licenseExpiry = "2029-12-31",
                )

            client
                .post("/v1/drivers") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.let { response ->
                    assertEquals(HttpStatusCode.Created, response.status)
                    val apiResponse = response.body<ApiResponse<DriverResponse>>()
                    assertTrue(apiResponse.success)
                    assertEquals("new-driver@fleet.ph", apiResponse.data!!.email)
                }
        }

    @Test
    fun `should list drivers`() =
        testApplication {
            configurePostgres()
            application { module() }
            seedDriver("driver1@fleet.ph", "L1")
            seedDriver("driver2@fleet.ph", "L2")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/drivers") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<List<DriverResponse>>>()
                    assertTrue(apiResponse.success)
                    assertEquals(2, apiResponse.data!!.size)
                }
        }

    @Test
    fun `should get driver by id`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedDriver("get@fleet.ph", "LGET")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/drivers/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<DriverResponse>>()
                    assertEquals("get@fleet.ph", apiResponse.data!!.email)
                }
        }

    @Test
    fun `should update driver`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedDriver("update@fleet.ph", "LUP")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val updateRequest =
                UpdateDriverRequest(
                    firstName = "Maria Updated",
                )

            client
                .patch("/v1/drivers/$id") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<DriverResponse>>()
                    assertEquals("Maria Updated", apiResponse.data!!.firstName)
                }
        }

    @Test
    fun `should delete driver`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedDriver("delete@fleet.ph", "LDEL")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .delete("/v1/drivers/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                }

            // Verify it's gone
            client
                .get("/v1/drivers/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.NotFound, response.status)
                }
        }

    @Test
    fun `should deactivate driver`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedDriver("deactivate-int@fleet.ph", "LDEA")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .patch("/v1/drivers/$id/deactivate") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<DriverResponse>>()
                    assertTrue(apiResponse.success)
                    assertEquals(false, apiResponse.data!!.availabilityStatus)
                }

            // Toggle back
            client
                .patch("/v1/drivers/$id/deactivate") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<DriverResponse>>()
                    assertEquals(true, apiResponse.data!!.availabilityStatus)
                }
        }
}
