package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.application.dto.UpdateDriverRequest
import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class UpdateDriverUseCaseTest {
    private val repository = mockk<DriverRepository>()
    private val useCase = UpdateDriverUseCase(repository)

    private val futureExpiry =
        LocalDate
            .now()
            .plusYears(2)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)

    @Test
    fun shouldThrowIllegalArgument_WhenDriverNotFound() {
        // Arrange
        coEvery { repository.findById(DriverId("missing-id")) } returns null

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("missing-id", UpdateDriverRequest()) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Driver not found")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenEmailAlreadyTakenByAnotherDriver() {
        // Arrange
        val existing = sampleDriver()
        coEvery { repository.findById(DriverId("driver-001")) } returns existing
        coEvery { repository.findByEmail("taken@fleet.ph") } returns mockk()

        // Act / Assert
        assertThatThrownBy {
            runBlocking {
                useCase.execute("driver-001", UpdateDriverRequest(email = "taken@fleet.ph"))
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldNotCheckEmail_WhenEmailIsUnchanged() =
        runBlocking {
            // Arrange
            val existing = sampleDriver()
            val savedSlot = slot<Driver>()
            coEvery { repository.findById(DriverId("driver-001")) } returns existing
            coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

            // Act - updating email to same value should not trigger uniqueness check
            val result = useCase.execute("driver-001", UpdateDriverRequest(email = existing.email))

            // Assert - `findByEmail` is NOT called when email is unchanged
            coVerify(exactly = 0) { repository.findByEmail(any()) }
            assertThat(result.email).isEqualTo(existing.email)
        }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseNumberAlreadyTaken() {
        // Arrange
        val existing = sampleDriver()
        coEvery { repository.findById(DriverId("driver-001")) } returns existing
        coEvery { repository.findByLicenseNumber("LN-TAKEN") } returns mockk()

        // Act / Assert
        assertThatThrownBy {
            runBlocking {
                useCase.execute("driver-001", UpdateDriverRequest(licenseNumber = "LN-TAKEN"))
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseExpiryFormatIsInvalid() {
        // Arrange
        val existing = sampleDriver()
        coEvery { repository.findById(DriverId("driver-001")) } returns existing

        // Act / Assert
        assertThatThrownBy {
            runBlocking {
                useCase.execute("driver-001", UpdateDriverRequest(licenseExpiry = "31-12-2030"))
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Expected YYYY-MM-DD")
    }

    @Test
    fun shouldUpdatePartialFields_WhenOnlySomeFieldsProvided() =
        runBlocking {
            // Arrange
            val existing = sampleDriver()
            val savedSlot = slot<Driver>()
            coEvery { repository.findById(DriverId("driver-001")) } returns existing
            coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

            // Act
            val result =
                useCase.execute(
                    "driver-001",
                    UpdateDriverRequest(
                        firstName = "Pedro",
                        city = "Cebu",
                    ),
                )

            // Assert
            assertThat(result.firstName).isEqualTo("Pedro")
            assertThat(result.city).isEqualTo("Cebu")
            // Unchanged fields preserved
            assertThat(result.email).isEqualTo(existing.email)
            assertThat(result.licenseNumber).isEqualTo(existing.licenseNumber)
            assertThat(result.phone).isEqualTo(existing.phone)
        }

    @Test
    fun shouldUpdateEmail_WhenNewEmailIsNotConflicting() =
        runBlocking {
            // Arrange
            val existing = sampleDriver()
            val savedSlot = slot<Driver>()
            coEvery { repository.findById(DriverId("driver-001")) } returns existing
            coEvery { repository.findByEmail("new@fleet.ph") } returns null
            coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

            // Act
            val result = useCase.execute("driver-001", UpdateDriverRequest(email = "new@fleet.ph"))

            // Assert
            assertThat(result.email).isEqualTo("new@fleet.ph")
            coVerify(exactly = 1) { repository.findByEmail("new@fleet.ph") }
        }

    @Test
    fun shouldUpdateLicenseNumber_WhenNewLicenseIsNotConflicting() =
        runBlocking {
            // Arrange
            val existing = sampleDriver()
            val savedSlot = slot<Driver>()
            coEvery { repository.findById(DriverId("driver-001")) } returns existing
            coEvery { repository.findByLicenseNumber("LN-NEW") } returns null
            coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

            // Act
            val result = useCase.execute("driver-001", UpdateDriverRequest(licenseNumber = "LN-NEW"))

            // Assert
            assertThat(result.licenseNumber).isEqualTo("LN-NEW")
            coVerify(exactly = 1) { repository.findByLicenseNumber("LN-NEW") }
        }

    @Test
    fun shouldToggleAvailabilityStatus_WhenAvailabilityStatusProvided() =
        runBlocking {
            // Arrange
            val existing = sampleDriver(availabilityStatus = true)
            val savedSlot = slot<Driver>()
            coEvery { repository.findById(DriverId("driver-001")) } returns existing
            coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

            // Act
            val result = useCase.execute("driver-001", UpdateDriverRequest(availabilityStatus = false))

            // Assert
            assertThat(result.availabilityStatus).isFalse()
        }

    @Test
    fun shouldUpdateLicenseExpiry_WhenValidFutureDateProvided() =
        runBlocking {
            // Arrange
            val existing = sampleDriver()
            val savedSlot = slot<Driver>()
            coEvery { repository.findById(DriverId("driver-001")) } returns existing
            coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

            val newExpiry = LocalDate.now().plusYears(5).toString() // YYYY-MM-DD

            // Act
            val result = useCase.execute("driver-001", UpdateDriverRequest(licenseExpiry = newExpiry))

            // Assert
            assertThat(result.licenseExpiry).isAfter(Instant.now())
        }

    // --- Helpers ---

    private fun sampleDriver(availabilityStatus: Boolean = true) =
        Driver(
            id = DriverId("driver-001"),
            firstName = "Juan",
            lastName = "dela Cruz",
            email = "juan@fleet.ph",
            phone = "+63912345678",
            licenseNumber = "LN-0001",
            licenseExpiry = futureExpiry,
            licenseClass = "B",
            city = "Manila",
            country = "Philippines",
            availabilityStatus = availabilityStatus,
        )
}
