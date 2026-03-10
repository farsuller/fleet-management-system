package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.application.dto.DriverRegistrationRequest
import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.modules.users.domain.model.Role
import com.solodev.fleet.modules.users.domain.model.RoleId
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class RegisterDriverUseCaseTest {

    private val driverRepository  = mockk<DriverRepository>()
    private val userRepository    = mockk<UserRepository>()
    private val tokenRepository   = mockk<VerificationTokenRepository>()
    private val useCase = RegisterDriverUseCase(driverRepository, userRepository, tokenRepository)

    private val driverRole = Role(id = RoleId("role-driver"), name = "DRIVER")

    private val validRequest = DriverRegistrationRequest(
        email         = "driver@fleet.ph",
        passwordRaw   = "secret-pass",
        firstName     = "Jose",
        lastName      = "Cruz",
        phone         = "+63917000002",
        licenseNumber = "LN-MOB-001",
        licenseExpiry = "2030-12-31",
        licenseClass  = "B",
    )

    @Test
    fun shouldRegisterDriver_WhenAllDataIsUnique() = runBlocking {
        val savedDriver = slot<Driver>()
        coEvery { driverRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { driverRepository.findByLicenseNumber("LN-MOB-001") } returns null
        coEvery { userRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { userRepository.findRoleByName("DRIVER") } returns driverRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } returnsArgument 0
        coEvery { driverRepository.save(capture(savedDriver)) } returnsArgument 0

        val result = useCase.execute(validRequest)

        assertThat(result.email).isEqualTo("driver@fleet.ph")
        assertThat(result.firstName).isEqualTo("Jose")
        assertThat(savedDriver.captured.userId).isNotNull()
    }

    @Test
    fun shouldLinkDriverToUser_WhenRegistrationSucceeds() = runBlocking {
        val savedDriver = slot<Driver>()
        coEvery { driverRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { driverRepository.findByLicenseNumber("LN-MOB-001") } returns null
        coEvery { userRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { userRepository.findRoleByName("DRIVER") } returns driverRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } returnsArgument 0
        coEvery { driverRepository.save(capture(savedDriver)) } returnsArgument 0

        useCase.execute(validRequest)

        assertThat(savedDriver.captured.userId).isNotNull()
    }

    @Test
    fun shouldIssueVerificationToken_WhenRegistrationSucceeds() = runBlocking {
        coEvery { driverRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { driverRepository.findByLicenseNumber("LN-MOB-001") } returns null
        coEvery { userRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { userRepository.findRoleByName("DRIVER") } returns driverRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } returnsArgument 0
        coEvery { driverRepository.save(any()) } returnsArgument 0

        useCase.execute(validRequest)

        coVerify(exactly = 1) { tokenRepository.save(any()) }
    }

    @Test
    fun shouldThrowIllegalArgument_WhenDriverEmailAlreadyExists() {
        coEvery { driverRepository.findByEmail("driver@fleet.ph") } returns mockk()

        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseAlreadyExists() {
        coEvery { driverRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { driverRepository.findByLicenseNumber("LN-MOB-001") } returns mockk()

        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenUserEmailAlreadyExists() {
        coEvery { driverRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { driverRepository.findByLicenseNumber("LN-MOB-001") } returns null
        coEvery { userRepository.findByEmail("driver@fleet.ph") } returns mockk()

        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseIsExpired() {
        coEvery { driverRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { driverRepository.findByLicenseNumber("LN-MOB-001") } returns null
        coEvery { userRepository.findByEmail("driver@fleet.ph") } returns null
        val expired = validRequest.copy(licenseExpiry = "2020-01-01")

        assertThatThrownBy { runBlocking { useCase.execute(expired) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("expired")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseExpiryIsMalformed() {
        coEvery { driverRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { driverRepository.findByLicenseNumber("LN-MOB-001") } returns null
        coEvery { userRepository.findByEmail("driver@fleet.ph") } returns null
        val malformed = validRequest.copy(licenseExpiry = "bad-date")

        assertThatThrownBy { runBlocking { useCase.execute(malformed) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalState_WhenDriverRoleNotFound() {
        coEvery { driverRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { driverRepository.findByLicenseNumber("LN-MOB-001") } returns null
        coEvery { userRepository.findByEmail("driver@fleet.ph") } returns null
        coEvery { userRepository.findRoleByName("DRIVER") } returns null

        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("DRIVER role")
    }
}
