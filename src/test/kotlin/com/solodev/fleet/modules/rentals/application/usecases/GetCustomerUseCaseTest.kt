package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetCustomerUseCaseTest {
    private val repository = mockk<CustomerRepository>()
    private val useCase = GetCustomerUseCase(repository)

    @Test
    fun shouldReturnCustomer_WhenIdExists(): Unit =
        runBlocking {
            // Arrange
            val customer = mockk<Customer>()
            coEvery { repository.findById(CustomerId("cust-001")) } returns customer

            // Act
            val result = useCase.execute("cust-001")

            // Assert
            assertThat(result).isNotNull()
            assertThat(result).isEqualTo(customer)
        }

    @Test
    fun shouldReturnNull_WhenCustomerNotFound() =
        runBlocking {
            // Arrange
            coEvery { repository.findById(CustomerId("unknown")) } returns null

            // Act
            val result = useCase.execute("unknown")

            // Assert
            assertThat(result).isNull()
        }
}
