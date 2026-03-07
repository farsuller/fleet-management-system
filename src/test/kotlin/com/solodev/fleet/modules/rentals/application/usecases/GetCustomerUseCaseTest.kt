package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class GetCustomerUseCaseTest {

    private val repository = mockk<CustomerRepository>()
    private val useCase = GetCustomerUseCase(repository)

    @Test
    fun `returns customer when found`() = runBlocking {
        val customer = mockk<com.solodev.fleet.modules.rentals.domain.model.Customer>()
        coEvery { repository.findById(any()) } returns customer

        val result = useCase.execute("cust-001")

        assertNotNull(result)
        assertEquals(customer, result)
    }

    @Test
    fun `returns null when customer does not exist`() = runBlocking {
        coEvery { repository.findById(any()) } returns null

        val result = useCase.execute("unknown")

        assertNull(result)
    }
}
