package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.CustomerRequest
import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CreateCustomerUseCaseTest {

    private val repository = mockk<CustomerRepository>()
    private val useCase = CreateCustomerUseCase(repository)

    private val validRequest = CustomerRequest(
        email = "maria@example.com",
        firstName = "Maria",
        lastName = "Santos",
        driversLicense = "DL12345678",
        driverLicenseExpiry = "2027-12-31",
        phone = "+63912345678",
        address = "123 Main St",
        city = "Manila",
        country = "Philippines"
    )

    @Test
    fun shouldCreateCustomer_WhenDataIsValid() = runBlocking {
        // Arrange
        val savedCustomer = slot<Customer>()
        coEvery { repository.findByEmail("maria@example.com") } returns null
        coEvery { repository.findByDriverLicense("DL12345678") } returns null
        coEvery { repository.save(capture(savedCustomer)) } returnsArgument 0

        // Act
        val result = useCase.execute(validRequest)

        // Assert
        assertThat(result.email).isEqualTo("maria@example.com")
        assertThat(result.firstName).isEqualTo("Maria")
        assertThat(savedCustomer.captured.email).isEqualTo("maria@example.com")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenEmailAlreadyRegistered() {
        // Arrange
        coEvery { repository.findByEmail("maria@example.com") } returns mockk()

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenDriverLicenseAlreadyRegistered() {
        // Arrange
        coEvery { repository.findByEmail("maria@example.com") } returns null
        coEvery { repository.findByDriverLicense("DL12345678") } returns mockk()

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenDriverLicenseIsExpired() {
        // Arrange
        coEvery { repository.findByEmail("maria@example.com") } returns null
        coEvery { repository.findByDriverLicense("DL12345678") } returns null
        val expiredRequest = validRequest.copy(driverLicenseExpiry = "2020-01-01")

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(expiredRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenDriverLicenseExpiryIsMalformed() {
        // Arrange
        coEvery { repository.findByEmail("maria@example.com") } returns null
        coEvery { repository.findByDriverLicense("DL12345678") } returns null
        val invalidRequest = validRequest.copy(driverLicenseExpiry = "invalid-date")

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(invalidRequest) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldBeActiveByDefault_WhenCustomerIsCreated() = runBlocking {
        // Arrange
        coEvery { repository.findByEmail("maria@example.com") } returns null
        coEvery { repository.findByDriverLicense("DL12345678") } returns null
        coEvery { repository.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(validRequest)

        // Assert
        assertThat(result.isActive).isTrue()
    }
}
