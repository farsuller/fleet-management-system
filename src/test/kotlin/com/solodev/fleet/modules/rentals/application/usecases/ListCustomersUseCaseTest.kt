package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ListCustomersUseCaseTest {

    private val repository = mockk<CustomerRepository>()
    private val useCase = ListCustomersUseCase(repository)

    @Test
    fun shouldReturnAllCustomers_WhenCustomersExist() = runBlocking {
        // Arrange
        val customers = listOf(mockk<com.solodev.fleet.modules.rentals.domain.model.Customer>())
        coEvery { repository.findAll() } returns customers

        // Act
        val result = useCase.execute()

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result).isEqualTo(customers)
    }

    @Test
    fun shouldReturnEmptyList_WhenNoCustomersExist() = runBlocking {
        // Arrange
        coEvery { repository.findAll() } returns emptyList()

        // Act
        val result = useCase.execute()

        // Assert
        assertThat(result).isEmpty()
    }
}
