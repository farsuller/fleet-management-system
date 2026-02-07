# Customer Module - Test Implementation Guide

This document details the testing strategy and implementations for the Customer module, ensuring strict verification of driver credentials and PII data integrity.

---

## 1. Testing (The Quality Shield)

We use **AAA Pattern** tests to verify the strict business rules of customer management.

### Use Case Unit Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CreateCustomerUseCaseTest.kt`

```kotlin
class CreateCustomerUseCaseTest {
    private val repository = mockk<CustomerRepository>()
    private val useCase = CreateCustomerUseCase(repository)

    @Test
    fun `should fail when license is expired`() = runBlocking {
        // Arrange
        val expiredRequest = CustomerRequest(
            email = "test@user.com", firstName = "John", lastName = "Doe",
            phone = "123", driversLicense = "LIC123",
            driverLicenseExpiry = "2020-01-01" // Past date
        )

        // Act & Assert
        val ex = assertFailsWith<IllegalArgumentException> {
            useCase.execute(expiredRequest)
        }
        assertTrue(ex.message!!.contains("expired"))
    }

    @Test
    fun `should create customer when data is valid`() = runBlocking {
        // Arrange
        val validRequest = CustomerRequest(
            email = "john@example.com", firstName = "John", lastName = "Doe",
            phone = "123", driversLicense = "NEW-123",
            driverLicenseExpiry = "2030-01-01" // Future date
        )
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense(any()) } returns null
        coEvery { repository.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(validRequest)

        // Assert
        assertEquals("John Doe", result.fullName)
    }
}
```

### HTTP Integration Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/infrastructure/http/CustomerRoutesTest.kt`

```kotlin
@Test
fun `GET customers should return list with 200 OK`() = testApplication {
    configureTestDb()
    val response = client.get("/v1/customers")
    assertEquals(HttpStatusCode.OK, response.status)
}
```
