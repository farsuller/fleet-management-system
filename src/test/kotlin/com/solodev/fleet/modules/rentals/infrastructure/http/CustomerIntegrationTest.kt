package com.solodev.fleet.modules.rentals.infrastructure.http

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.configurePostgres
import com.solodev.fleet.module
import com.solodev.fleet.modules.rentals.application.dto.CustomerRequest
import com.solodev.fleet.modules.rentals.application.dto.CustomerResponse
import com.solodev.fleet.modules.rentals.application.dto.UpdateCustomerRequest
import com.solodev.fleet.modules.rentals.infrastructure.persistence.CustomersTable
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

class CustomerIntegrationTest : IntegrationTestBase() {
    private val adminId = UUID.randomUUID()
    private val adminEmail = "admin@fleet.ph"

    @BeforeEach
    fun setup() {
        cleanDatabase()
    }

    private fun seedCustomer(
        email: String = "customer@example.com",
        license: String = "CL12345",
    ): UUID {
        val id = UUID.randomUUID()
        transaction {
            CustomersTable.insert {
                it[CustomersTable.id] = id
                it[firstName] = "Juan"
                it[lastName] = "Dela Cruz"
                it[CustomersTable.email] = email
                it[phone] = "+639123456789"
                it[driverLicenseNumber] = license
                it[driverLicenseExpiry] = LocalDate.now().plusYears(5)
                it[isActive] = true
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        return id
    }

    @Test
    fun `should create a customer`() =
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
                CustomerRequest(
                    email = "new-customer@example.com",
                    firstName = "Maria",
                    lastName = "Santos",
                    phone = "+639987654321",
                    driversLicense = "CN54321",
                    driverLicenseExpiry = "2029-12-31",
                )

            client
                .post("/v1/customers") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.let { response ->
                    assertEquals(HttpStatusCode.Created, response.status)
                    val apiResponse = response.body<ApiResponse<CustomerResponse>>()
                    assertTrue(apiResponse.success)
                    assertEquals("new-customer@example.com", apiResponse.data!!.email)
                }
        }

    @Test
    fun `should list customers`() =
        testApplication {
            configurePostgres()
            application { module() }
            seedCustomer("cust1@example.com", "L1")
            seedCustomer("cust2@example.com", "L2")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/customers") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<List<CustomerResponse>>>()
                    assertTrue(apiResponse.success)
                    assertEquals(2, apiResponse.data!!.size)
                }
        }

    @Test
    fun `should get customer by id`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedCustomer("get@example.com", "LGET")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/customers/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<com.solodev.fleet.modules.rentals.application.dto.CustomerDetailResponse>>()
                    assertEquals("get@example.com", apiResponse.data!!.email)
                }
        }

    @Test
    fun `should update customer`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedCustomer("update@example.com", "LUP")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val updateRequest =
                UpdateCustomerRequest(
                    firstName = "Maria Updated",
                )

            client
                .patch("/v1/customers/$id") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<CustomerResponse>>()
                    assertEquals("Maria Updated", apiResponse.data!!.firstName)
                }
        }

    @Test
    fun `should delete customer`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedCustomer("delete@example.com", "LDEL")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json(com.solodev.fleet.shared.infrastructure.serialization.JsonConfig.instance)
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .delete("/v1/customers/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                }

            // Verify it's gone
            client
                .get("/v1/customers/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.NotFound, response.status)
                }
        }
}
