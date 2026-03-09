package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.CustomerRegistrationRequest
import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import com.solodev.fleet.modules.users.domain.model.Role
import com.solodev.fleet.modules.users.domain.model.RoleId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RegisterCustomerUseCaseTest {

    private val customerRepository = mockk<CustomerRepository>()
    private val userRepository     = mockk<UserRepository>()
    private val tokenRepository    = mockk<VerificationTokenRepository>()
    private val useCase = RegisterCustomerUseCase(customerRepository, userRepository, tokenRepository)

    private val customerRole = Role(id = RoleId("role-customer"), name = "CUSTOMER")

    private val validRequest = CustomerRegistrationRequest(
        email               = "customer@fleet.ph",
        passwordRaw         = "secure-pass",
        firstName           = "Ana",
        lastName            = "Lim",
        phone               = "+63917000003",
        driversLicense      = "DL-CUST-001",
        driverLicenseExpiry = "2030-06-30",
    )

    @Test
    fun shouldRegisterCustomer_WhenAllDataIsUnique() = runBlocking {
        val savedCustomer = slot<Customer>()
        coEvery { customerRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { customerRepository.findByDriverLicense("DL-CUST-001") } returns null
        coEvery { userRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns customerRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } returnsArgument 0
        coEvery { customerRepository.save(capture(savedCustomer)) } returnsArgument 0

        val result = useCase.execute(validRequest)

        assertThat(result.email).isEqualTo("customer@fleet.ph")
        assertThat(result.firstName).isEqualTo("Ana")
        assertThat(savedCustomer.captured.userId).isNotNull()
    }

    @Test
    fun shouldLinkCustomerToUser_WhenRegistrationSucceeds() = runBlocking {
        val savedCustomer = slot<Customer>()
        coEvery { customerRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { customerRepository.findByDriverLicense("DL-CUST-001") } returns null
        coEvery { userRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns customerRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } returnsArgument 0
        coEvery { customerRepository.save(capture(savedCustomer)) } returnsArgument 0

        useCase.execute(validRequest)

        assertThat(savedCustomer.captured.userId).isNotNull()
    }

    @Test
    fun shouldIssueVerificationToken_WhenRegistrationSucceeds() = runBlocking {
        coEvery { customerRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { customerRepository.findByDriverLicense("DL-CUST-001") } returns null
        coEvery { userRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns customerRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } returnsArgument 0
        coEvery { customerRepository.save(any()) } returnsArgument 0

        useCase.execute(validRequest)

        coVerify(exactly = 1) { tokenRepository.save(any()) }
    }

    @Test
    fun shouldThrowIllegalArgument_WhenCustomerEmailAlreadyExists() {
        coEvery { customerRepository.findByEmail("customer@fleet.ph") } returns mockk()

        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseAlreadyExists() {
        coEvery { customerRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { customerRepository.findByDriverLicense("DL-CUST-001") } returns mockk()

        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenUserEmailAlreadyExists() {
        coEvery { customerRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { customerRepository.findByDriverLicense("DL-CUST-001") } returns null
        coEvery { userRepository.findByEmail("customer@fleet.ph") } returns mockk()

        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseIsExpired() {
        coEvery { customerRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { customerRepository.findByDriverLicense("DL-CUST-001") } returns null
        coEvery { userRepository.findByEmail("customer@fleet.ph") } returns null
        val expired = validRequest.copy(driverLicenseExpiry = "2020-01-01")

        assertThatThrownBy { runBlocking { useCase.execute(expired) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("expired")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenLicenseExpiryIsMalformed() {
        coEvery { customerRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { customerRepository.findByDriverLicense("DL-CUST-001") } returns null
        coEvery { userRepository.findByEmail("customer@fleet.ph") } returns null
        val malformed = validRequest.copy(driverLicenseExpiry = "not-a-date")

        assertThatThrownBy { runBlocking { useCase.execute(malformed) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalState_WhenCustomerRoleNotFound() {
        coEvery { customerRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { customerRepository.findByDriverLicense("DL-CUST-001") } returns null
        coEvery { userRepository.findByEmail("customer@fleet.ph") } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns null

        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("CUSTOMER role")
    }
}
