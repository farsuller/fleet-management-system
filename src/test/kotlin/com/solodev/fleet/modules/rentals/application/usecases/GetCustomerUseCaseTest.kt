package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetCustomerUseCaseTest {

    private val repository = mockk<CustomerRepository>()
    private val useCase = GetCustomerUseCase(repository)

    @Test
    fun shouldReturnCustomer_WhenIdExists() = runBlocking {
        // Arrange
        val customer = mockk<com.solodev.fleet.modules.rentals.domain.model.Customer>()
        coEvery { repository.findById(CustomerId("cust-001")) } returns customer

        // Act
        val result = useCase.execute("cust-001")

        // Assert
        assertThat(result).isNotNull()
        assertThat(result).isEqualTo(customer)
    }

    @Test
    fun shouldReturnNull_WhenCustomerNotFound() = runBlocking {
        // Arrange
        coEvery { repository.findById(CustomerId("unknown")) } returns null

        // Act
        val result = useCase.execute("unknown")

        // Assert
        assertThat(result).isNull()
    }
}
