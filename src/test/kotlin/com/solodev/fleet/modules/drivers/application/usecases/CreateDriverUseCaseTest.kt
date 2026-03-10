package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.application.dto.DriverRequest
import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class CreateDriverUseCaseTest {

    private val repository = mockk<DriverRepository>()
    private val useCase = CreateDriverUseCase(repository)

    private val validRequest = DriverRequest(
        email         = "pedro@fleet.ph",
        firstName     = "Pedro",
        lastName      = "Reyes",
        phone         = "+63917000001",
        licenseNumber = "LN-0001",
        licenseExpiry = "2030-12-31",
        licenseClass  = "A",
        address       = "123 Main St",
        city          = "Manila",
        country       = "Philippines",
    )

    @Test
    fun shouldCreateDriver_WhenDataIsValid() = runBlocking {
        val savedDriver = slot<Driver>()
        coEvery { repository.findByEmail("pedro@fleet.ph") } returns null
        coEvery { repository.findByLicenseNumber("LN-0001") } returns null
        coEvery { repository.save(capture(savedDriver)) } returnsArgument 0

        val result = useCase.execute(validRequest)

        assertThat(result.email).isEqualTo("pedro@fleet.ph")
        assertThat(result.firstName).isEqualTo("Pedro")
        assertThat(savedDriver.captured.licenseNumber).isEqualTo("LN-0001")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenEmailAlreadyExists() {
        coEvery { repository.findByEmail("pedro@fleet.ph") } returns mockk()

        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseAlreadyExists() {
        coEvery { repository.findByEmail("pedro@fleet.ph") } returns null
        coEvery { repository.findByLicenseNumber("LN-0001") } returns mockk()

        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseIsExpired() {
        coEvery { repository.findByEmail("pedro@fleet.ph") } returns null
        coEvery { repository.findByLicenseNumber("LN-0001") } returns null
        val expired = validRequest.copy(licenseExpiry = "2020-01-01")

        assertThatThrownBy { runBlocking { useCase.execute(expired) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("expired")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseExpiryIsMalformed() {
        coEvery { repository.findByEmail("pedro@fleet.ph") } returns null
        coEvery { repository.findByLicenseNumber("LN-0001") } returns null
        val malformed = validRequest.copy(licenseExpiry = "not-a-date")

        assertThatThrownBy { runBlocking { useCase.execute(malformed) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldSetIsActiveTrue_WhenDriverCreated() = runBlocking {
        val savedDriver = slot<Driver>()
        coEvery { repository.findByEmail("pedro@fleet.ph") } returns null
        coEvery { repository.findByLicenseNumber("LN-0001") } returns null
        coEvery { repository.save(capture(savedDriver)) } returnsArgument 0

        useCase.execute(validRequest)

        assertThat(savedDriver.captured.isActive).isTrue()
    }

    @Test
    fun shouldSetNullUserId_WhenCreatedViaBackoffice() = runBlocking {
        val savedDriver = slot<Driver>()
        coEvery { repository.findByEmail("pedro@fleet.ph") } returns null
        coEvery { repository.findByLicenseNumber("LN-0001") } returns null
        coEvery { repository.save(capture(savedDriver)) } returnsArgument 0

        useCase.execute(validRequest)

        assertThat(savedDriver.captured.userId).isNull()
    }
}
