package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DeactivateCustomerUseCaseTest {

    private val repository = mockk<CustomerRepository>()
    private val useCase = DeactivateCustomerUseCase(repository)

    @Test
    fun `shouldDeactivateActiveCustomer`() = runBlocking {
        // Arrange
        val active = sampleCustomer(isActive = true)
        val deactivated = active.copy(isActive = false)
        coEvery { repository.findById(CustomerId("cust-001")) } returns active
        coEvery { repository.save(deactivated) } returns deactivated

        // Act
        val result = useCase.execute("cust-001")

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.isActive).isFalse()
        coVerify { repository.save(deactivated) }
    }

    @Test
    fun `shouldReactivateInactiveCustomer`() = runBlocking {
        // Arrange
        val inactive = sampleCustomer(isActive = false)
        val reactivated = inactive.copy(isActive = true)
        coEvery { repository.findById(CustomerId("cust-001")) } returns inactive
        coEvery { repository.save(reactivated) } returns reactivated

        // Act
        val result = useCase.execute("cust-001")

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.isActive).isTrue()
        coVerify { repository.save(reactivated) }
    }

    @Test
    fun `shouldReturnNull_WhenCustomerNotFound`() = runBlocking {
        // Arrange
        coEvery { repository.findById(CustomerId("unknown")) } returns null

        // Act
        val result = useCase.execute("unknown")

        // Assert
        assertThat(result).isNull()
        coVerify(exactly = 0) { repository.save(any()) }
    }

    private fun sampleCustomer(isActive: Boolean = true) = Customer(
        id = CustomerId("cust-001"),
        userId = null,
        firstName = "Juan",
        lastName = "dela Cruz",
        email = "juan@example.com",
        phone = "+63-912-345-6789",
        driverLicenseNumber = "N01-23-456789",
        driverLicenseExpiry = Instant.parse("2028-12-31T00:00:00Z"),
        isActive = isActive,
        createdAt = Instant.EPOCH,
    )
}
