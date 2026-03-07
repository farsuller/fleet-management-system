package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class ListCustomersUseCaseTest {

    private val repository = mockk<CustomerRepository>()
    private val useCase = ListCustomersUseCase(repository)

    @Test
    fun `returns all customers`() = runBlocking {
        val customers = listOf(mockk<com.solodev.fleet.modules.rentals.domain.model.Customer>())
        coEvery { repository.findAll() } returns customers

        val result = useCase.execute()

        assertEquals(1, result.size)
        assertEquals(customers, result)
    }

    @Test
    fun `returns empty list when no customers`() = runBlocking {
        coEvery { repository.findAll() } returns emptyList()

        val result = useCase.execute()

        assertTrue(result.isEmpty())
    }
}
