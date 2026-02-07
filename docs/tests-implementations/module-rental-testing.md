# Rental Module - Test Implementation Guide

This document details the testing strategy and implementations for the Rental module, focusing on the rental lifecycle and coordination between vehicles and customers.

---

## 1. Testing (The Quality Shield)

We follow the **Testing Pyramid** and **AAA (Arrange, Act, Assert)** pattern to ensure 100% reliability of the rental lifecycle.

### Use Case Unit Tests (Mocking Repository)
`src/test/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CreateRentalUseCaseTest.kt`

```kotlin
class CreateRentalUseCaseTest {
    private val rentalRepo = mockk<RentalRepository>()
    private val vehicleRepo = mockk<VehicleRepository>()
    private val useCase = CreateRentalUseCase(rentalRepo, vehicleRepo)

    @Test
    fun `should create rental when vehicle is available`() = runBlocking {
        // Arrange
        val request = RentalRequest(
            vehicleId = "v-1", customerId = "c-1",
            startDate = "2024-06-01T10:00:00Z", endDate = "2024-06-05T10:00:00Z"
        )
        val vehicle = createSampleVehicle(state = VehicleState.AVAILABLE)
        
        coEvery { vehicleRepo.findById(any()) } returns vehicle
        coEvery { rentalRepo.findConflictingRentals(any(), any(), any()) } returns emptyList()
        coEvery { rentalRepo.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(request)

        // Assert
        assertEquals(RentalStatus.RESERVED, result.status)
        coVerify { rentalRepo.save(any()) }
    }

    @Test
    fun `should fail when vehicle is already rented`() = runBlocking {
        // Arrange
        val request = RentalRequest(/*...*/)
        val vehicle = createSampleVehicle(state = VehicleState.RENTED)
        coEvery { vehicleRepo.findById(any()) } returns vehicle

        // Act & Assert
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(request)
        }
    }
}
```

### HTTP Integration Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/infrastructure/http/RentalRoutesTest.kt`

```kotlin
@Test
fun `POST rentals should return 201 Created`() = testApplication {
    configureTestDb()
    val response = client.post("/v1/rentals") {
        contentType(ContentType.Application.Json)
        setBody("""{ "vehicleId": "v1", "customerId": "c1", ... }""")
    }
    assertEquals(HttpStatusCode.Created, response.status)
}
```
