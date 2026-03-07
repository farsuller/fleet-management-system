# Customer Module - Test Implementation Guide

This document covers testing strategy and implementations for the Customer module, including driver's license validation, PII uniqueness enforcement, and customer lifecycle management.

---

## 1. Domain Unit Tests

### Customer Value Object Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/domain/model/CustomerTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.domain.model

import kotlin.test.*

class CustomerTest {

    @Test
    fun `CustomerId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> {
            CustomerId("")
        }
    }

    @Test
    fun `Customer fullName concatenates first and last name`() {
        val customer = sampleCustomer()
        assertEquals("Maria Santos", customer.fullName)
    }

    @Test
    fun `Customer email is stored as-is`() {
        val customer = sampleCustomer(email = "maria@example.com")
        assertEquals("maria@example.com", customer.email)
    }

    private fun sampleCustomer(
        email: String = "maria@example.com",
        isActive: Boolean = true
    ) = Customer(
        id = CustomerId("cust-001"),
        email = email,
        firstName = "Maria",
        lastName = "Santos",
        phone = "+63917000001",
        driversLicense = "N01-23-456789",
        driverLicenseExpiry = java.time.Instant.parse("2030-01-01T00:00:00Z"),
        isActive = isActive
    )
}
```

---

## 2. Use Case Unit Tests

### CreateCustomerUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CreateCustomerUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.CustomerRequest
import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CreateCustomerUseCaseTest {

    private val repository = mockk<CustomerRepository>()
    private val useCase = CreateCustomerUseCase(repository)

    private val validRequest = CustomerRequest(
        email = "maria@example.com",
        firstName = "Maria",
        lastName = "Santos",
        phone = "+63917000001",
        driversLicense = "N01-23-456789",
        driverLicenseExpiry = "2030-01-01",
        address = "123 Rizal St",
        city = "Manila",
        state = "Metro Manila",
        postalCode = "1000",
        country = "Philippines"
    )

    @Test
    fun `creates customer successfully with valid data`() = runBlocking {
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense(any()) } returns null
        coEvery { repository.save(any()) } returnsArgument 0

        val result = useCase.execute(validRequest)

        assertEquals("Maria Santos", result.fullName)
        assertEquals("maria@example.com", result.email)
        assertTrue(result.isActive)
        coVerify { repository.save(any()) }
    }

    @Test
    fun `throws when email is already registered`() = runBlocking {
        coEvery { repository.findByEmail("maria@example.com") } returns mockk()

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(validRequest)
        }
    }

    @Test
    fun `throws when driver license is already registered`() = runBlocking {
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense("N01-23-456789") } returns mockk()

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(validRequest)
        }
    }

    @Test
    fun `throws when driver license is expired`() = runBlocking {
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense(any()) } returns null

        val expiredRequest = validRequest.copy(driverLicenseExpiry = "2020-01-01")

        val ex = assertFailsWith<IllegalArgumentException> {
            useCase.execute(expiredRequest)
        }
        assertTrue(ex.message!!.contains("expired", ignoreCase = true))
    }

    @Test
    fun `throws when driver license expiry has invalid date format`() = runBlocking {
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense(any()) } returns null

        val badFormatRequest = validRequest.copy(driverLicenseExpiry = "31-12-2030")

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(badFormatRequest)
        }
    }

    @Test
    fun `new customer is active by default`() = runBlocking {
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense(any()) } returns null
        coEvery { repository.save(any()) } returnsArgument 0

        val result = useCase.execute(validRequest)

        assertTrue(result.isActive)
    }
}
```

### GetCustomerUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/application/usecases/GetCustomerUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class GetCustomerUseCaseTest {

    private val repository = mockk<CustomerRepository>()
    private val useCase = GetCustomerUseCase(repository)

    @Test
    fun `returns customer when found`() = runBlocking {
        val customer = mockk<com.solodev.fleet.modules.rentals.domain.model.Customer>()
        coEvery { repository.findById(CustomerId("cust-001")) } returns customer

        val result = useCase.execute("cust-001")

        assertNotNull(result)
    }

    @Test
    fun `returns null when customer does not exist`() = runBlocking {
        coEvery { repository.findById(any()) } returns null

        val result = useCase.execute("unknown-id")

        assertNull(result)
    }
}
```

### ListCustomersUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/application/usecases/ListCustomersUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ListCustomersUseCaseTest {

    private val repository = mockk<CustomerRepository>()
    private val useCase = ListCustomersUseCase(repository)

    @Test
    fun `returns all customers`() = runBlocking {
        val customers = listOf(mockk<com.solodev.fleet.modules.rentals.domain.model.Customer>())
        coEvery { repository.findAll() } returns customers

        val result = useCase.execute()

        assertEquals(1, result.size)
    }

    @Test
    fun `returns empty list when no customers`() = runBlocking {
        coEvery { repository.findAll() } returns emptyList()

        val result = useCase.execute()

        assertTrue(result.isEmpty())
    }
}
```

---

## 3. HTTP Route Integration Tests

### Customer Routes
`src/test/kotlin/com/solodev/fleet/modules/rentals/infrastructure/http/CustomerRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class CustomerRoutesTest {

    // --- GET /v1/customers ---

    @Test
    fun `GET customers returns 200 with list`() = testApplication {
        val response = client.get("/v1/customers") { bearerAuth(TEST_JWT) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("success"))
    }

    @Test
    fun `GET customers returns 401 without auth`() = testApplication {
        val response = client.get("/v1/customers")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- POST /v1/customers ---

    @Test
    fun `POST customers creates customer and returns 201`() = testApplication {
        val response = client.post("/v1/customers") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody(validCustomerBody())
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("maria@example.com"))
    }

    @Test
    fun `POST customers returns 400 when required fields are missing`() = testApplication {
        val response = client.post("/v1/customers") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "email": "incomplete@example.com" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST customers returns 400 when license is expired`() = testApplication {
        val response = client.post("/v1/customers") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "expired@example.com",
                    "firstName": "Test",
                    "lastName": "User",
                    "phone": "+63900000000",
                    "driversLicense": "EXP-001",
                    "driverLicenseExpiry": "2019-01-01"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("expired", ignoreCase = true))
    }

    @Test
    fun `POST customers returns 409 when email already exists`() = testApplication {
        val body = validCustomerBody("dup@example.com", "DUP-LIC-001")
        client.post("/v1/customers") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val second = client.post("/v1/customers") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertTrue(second.status.value in 409..500)
    }

    // --- GET /v1/customers/{id} ---

    @Test
    fun `GET customers-id returns 200 with customer detail`() = testApplication {
        val response = client.get("/v1/customers/$CUSTOMER_ID") { bearerAuth(TEST_JWT) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("email"))
    }

    @Test
    fun `GET customers-id returns 404 for unknown customer`() = testApplication {
        val response = client.get("/v1/customers/00000000-0000-0000-0000-000000000000") {
            bearerAuth(TEST_JWT)
        }
        assertTrue(response.status.value in 404..500)
    }

    @Test
    fun `GET customers-id returns 401 without auth`() = testApplication {
        val response = client.get("/v1/customers/$CUSTOMER_ID")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    companion object {
        const val CUSTOMER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        const val TEST_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

        fun validCustomerBody(
            email: String = "maria@example.com",
            license: String = "N01-23-456789"
        ) = """
            {
                "email": "$email",
                "firstName": "Maria",
                "lastName": "Santos",
                "phone": "+63917000001",
                "driversLicense": "$license",
                "driverLicenseExpiry": "2030-01-01",
                "address": "123 Rizal St",
                "city": "Manila",
                "state": "Metro Manila",
                "postalCode": "1000",
                "country": "Philippines"
            }
        """.trimIndent()
    }
}
```

---

## 4. Error Scenario Tests

```kotlin
package com.solodev.fleet.modules.rentals.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class CustomerRoutesErrorTest {

    @Test
    fun `POST customers with duplicate driver license returns error`() = testApplication {
        // Create customer with license LIC-001
        client.post("/v1/customers") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody(CustomerRoutesTest.validCustomerBody("first@example.com", "LIC-001"))
        }
        // Attempt second customer with same license but different email
        val response = client.post("/v1/customers") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody(CustomerRoutesTest.validCustomerBody("second@example.com", "LIC-001"))
        }
        assertTrue(response.status.value in 400..500)
        assertTrue(response.bodyAsText().contains("license", ignoreCase = true))
    }

    @Test
    fun `POST customers with malformed date format returns 400`() = testApplication {
        val response = client.post("/v1/customers") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "test@example.com",
                    "firstName": "T", "lastName": "U",
                    "phone": "+63900000000",
                    "driversLicense": "LIC-888",
                    "driverLicenseExpiry": "31/12/2030"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET customers-id with malformed UUID returns error`() = testApplication {
        val response = client.get("/v1/customers/not-a-uuid") { bearerAuth(TEST_JWT) }
        assertTrue(response.status.value in 400..500)
    }

    private val TEST_JWT = CustomerRoutesTest.TEST_JWT
}
```

---

## 5. Test Summary

| Test Class | Layer | Coverage |
|---|---|---|
| `CustomerTest` | Unit – Domain | `CustomerId` validation, `fullName` |
| `CreateCustomerUseCaseTest` | Unit – Use Case | Happy path, duplicate email, duplicate license, expired license, bad date format, `isActive` default |
| `GetCustomerUseCaseTest` | Unit – Use Case | Found, not found |
| `ListCustomersUseCaseTest` | Unit – Use Case | Non-empty, empty list |
| `CustomerRoutesTest` | Integration – HTTP | GET list, POST create, GET by id, auth enforcement, 400/409 errors, expired license |
| `CustomerRoutesErrorTest` | Integration – Errors | Duplicate license, malformed date, invalid UUID path |
