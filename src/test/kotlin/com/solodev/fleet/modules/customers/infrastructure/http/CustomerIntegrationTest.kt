package com.solodev.fleet.modules.customers.infrastructure.http

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.configurePostgres
import com.solodev.fleet.module
import com.solodev.fleet.modules.rentals.application.dto.CustomerDetailResponse
import com.solodev.fleet.modules.rentals.application.dto.CustomerRequest
import com.solodev.fleet.modules.rentals.application.dto.CustomerResponse
import com.solodev.fleet.modules.rentals.application.dto.UpdateCustomerRequest
import com.solodev.fleet.modules.rentals.infrastructure.persistence.CustomersTable
import com.solodev.fleet.shared.models.ApiResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
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

    private fun seedCustomer(email: String = "test@customer.ph"): UUID {
        val id = UUID.randomUUID()
        transaction {
            CustomersTable.insert {
                it[CustomersTable.id] = id
                it[firstName] = "Test"
                it[lastName] = "Customer"
                it[CustomersTable.email] = email
                it[phone] = "+639111111111"
                it[driverLicenseNumber] = "LIC-" + UUID.randomUUID().toString().take(8)
                it[driverLicenseExpiry] = LocalDate.now().plusYears(1)
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
                        json()
                    }
                }

            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val request =
                CustomerRequest(
                    firstName = "John",
                    lastName = "Doe",
                    email = "john@example.com",
                    phone = "+639000000000",
                    driversLicense = "DL-12345",
                    driverLicenseExpiry = LocalDate.now().plusYears(1).toString(),
                    address = "123 Street",
                    city = "Manila",
                    state = "NCR",
                    postalCode = "1000",
                    country = "Philippines",
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
                    assertEquals("john@example.com", apiResponse.data!!.email)
                }
        }

    @Test
    fun `should list customers`() =
        testApplication {
            configurePostgres()
            application { module() }
            seedCustomer("cust1@example.com")
            seedCustomer("cust2@example.com")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
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
            val id = seedCustomer("get@customer.ph")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .get("/v1/customers/$id") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<CustomerDetailResponse>>()
                    assertEquals("get@customer.ph", apiResponse.data!!.email)
                }
        }

    @Test
    fun `should update customer`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedCustomer("old@customer.ph")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")
            val updateRequest =
                UpdateCustomerRequest(
                    firstName = "Updated",
                    lastName = "Name",
                )

            client
                .patch("/v1/customers/$id") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<CustomerResponse>>()
                    assertEquals("Updated", apiResponse.data!!.firstName)
                    assertEquals("Name", apiResponse.data!!.lastName)
                }
        }

    @Test
    fun `should deactivate customer`() =
        testApplication {
            configurePostgres()
            application { module() }
            val id = seedCustomer("active@customer.ph")

            val client =
                createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val token = tokenFor(adminId.toString(), adminEmail, "ADMIN")

            client
                .patch("/v1/customers/$id/deactivate") {
                    bearerAuth(token)
                }.let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val apiResponse = response.body<ApiResponse<CustomerResponse>>()
                    assertTrue(!apiResponse.data!!.isActive)
                }
        }
}
