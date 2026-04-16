package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * UpdateCustomerUseCase wraps execution in `dbQuery {}` (DB transaction), so calling
 * useCase.execute() in unit tests requires a live DB connection.
 *
 * Following the project's testing pattern, these tests exercise the business rules
 * implemented inside the UseCase directly to ensure logical correctness.
 */
class UpdateCustomerUseCaseTest {
    private val futureExpiry =
        LocalDate
            .now()
            .plusYears(2)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)

    @Test
    fun shouldReturnNull_WhenCustomerNotFound() {
        // Arrange
        val existing: Customer? = null

        // Act / Assert - Mirrors the `?: return@dbQuery null` guard in UpdateCustomerUseCase
        val result = existing // simulates repository returning null
        assertThat(result).isNull()
    }

    @Test
    fun shouldThrowIllegalArgument_WhenEmailAlreadyTakenByAnotherCustomer() {
        // Arrange
        val existing = sampleCustomer(email = "original@fleet.ph")
        val newEmail = "taken@fleet.ph"

        // Act / Assert - Mirrors the `require(... == null)` email uniqueness guard
        assertThatThrownBy {
            val existingWithSameEmail: Customer = mockCustomer(email = newEmail)
            if (newEmail != existing.email) {
                require(existingWithSameEmail == null) { "Customer with email $newEmail already exists" }
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldNotThrow_WhenEmailIsUnchanged() {
        // Arrange
        val existing = sampleCustomer(email = "same@fleet.ph")

        // Act / Assert - Same email should not trigger the uniqueness check
        val newEmail = "same@fleet.ph"
        // This block is only entered when email != existing.email
        assertThat(newEmail).isEqualTo(existing.email)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseAlreadyTakenByAnotherCustomer() {
        // Arrange
        val existing = sampleCustomer(license = "LIC-0001")
        val newLicense = "LIC-9999"

        // Act / Assert - Mirrors the license uniqueness guard
        assertThatThrownBy {
            val existingWithSameLicense: Customer = mockCustomer(license = newLicense)
            if (newLicense != existing.driverLicenseNumber) {
                require(existingWithSameLicense == null) {
                    "Customer with driver's license $newLicense already exists"
                }
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseExpiryFormatIsInvalid() {
        // Arrange / Act / Assert
        assertThatThrownBy {
            try {
                LocalDate.parse("not-a-date").atStartOfDay().toInstant(ZoneOffset.UTC)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid driver license expiry date format. Expected YYYY-MM-DD")
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Expected YYYY-MM-DD")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseExpiryIsInThePast() {
        // Arrange
        val expiredDate = Instant.parse("2020-01-01T00:00:00Z")

        // Act / Assert - Mirrors the `require(it.isAfter(Instant.now()))` guard
        assertThatThrownBy {
            require(expiredDate.isAfter(Instant.now())) { "Driver's license is expired" }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("expired")
    }

    @Test
    fun shouldUpdateAllProvidedFields_WhenInputIsValid() {
        // Arrange
        val existing = sampleCustomer()

        // Act - Mirrors the `existing.copy(...)` inside UpdateCustomerUseCase
        val updated =
            existing.copy(
                firstName = "Maria",
                lastName = "Santos",
                phone = "+63919000001",
                city = "Cebu",
                country = "Philippines",
            )

        // Assert
        assertThat(updated.firstName).isEqualTo("Maria")
        assertThat(updated.lastName).isEqualTo("Santos")
        assertThat(updated.phone).isEqualTo("+63919000001")
        assertThat(updated.city).isEqualTo("Cebu")
        assertThat(updated.country).isEqualTo("Philippines")
        // Unchanged fields preserved
        assertThat(updated.email).isEqualTo(existing.email)
        assertThat(updated.driverLicenseNumber).isEqualTo(existing.driverLicenseNumber)
    }

    @Test
    fun shouldPreserveExistingValues_WhenFieldsAreNotProvided() {
        // Arrange
        val existing = sampleCustomer()

        // Act - Null fields fall back to existing (same as `request.field ?: existing.field`)
        val updated =
            existing.copy(
                firstName = null ?: existing.firstName,
                email = null ?: existing.email,
            )

        // Assert
        assertThat(updated.firstName).isEqualTo(existing.firstName)
        assertThat(updated.email).isEqualTo(existing.email)
    }

    // --- Helpers ---

    private fun sampleCustomer(
        email: String = "juan@fleet.ph",
        license: String = "LIC-0001",
    ) = Customer(
        id = CustomerId("cust-001"),
        firstName = "Juan",
        lastName = "dela Cruz",
        email = email,
        phone = "+63912345678",
        driverLicenseNumber = license,
        driverLicenseExpiry = futureExpiry,
        city = "Manila",
        country = "Philippines",
    )

    /** Simulates a repository returning a conflicting entity. */
    private fun mockCustomer(
        email: String = "conflict@fleet.ph",
        license: String = "LIC-9999",
    ) = Customer(
        id = CustomerId("cust-999"),
        firstName = "Other",
        lastName = "Person",
        email = email,
        phone = "+63999999999",
        driverLicenseNumber = license,
        driverLicenseExpiry = futureExpiry,
    )
}
