package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.CustomerRequest
import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

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
    fun `creates customer successfully with valid data`() = runBlocking {
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense(any()) } returns null
        coEvery { repository.save(any()) } returnsArgument 0

        val result = useCase.execute(validRequest)

        assertEquals("maria@example.com", result.email)
        assertEquals("Maria", result.firstName)
        coVerify { repository.save(any()) }
    }

    @Test
    fun `throws when email is already registered`(): Unit = runBlocking {
        coEvery { repository.findByEmail("maria@example.com") } returns mockk()

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(validRequest)
        }
    }

    @Test
    fun `throws when driver license is already registered`(): Unit = runBlocking {
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense("DL12345678") } returns mockk()

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(validRequest)
        }
    }

    @Test
    fun `throws when driver license is expired`(): Unit = runBlocking {
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense(any()) } returns null

        val expiredRequest = validRequest.copy(driverLicenseExpiry = "2020-01-01")
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(expiredRequest)
        }
    }

    @Test
    fun `throws when driver license expiry has invalid date format`(): Unit = runBlocking {
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense(any()) } returns null

        val invalidRequest = validRequest.copy(driverLicenseExpiry = "invalid-date")
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(invalidRequest)
        }
    }

    @Test
    fun `new customer is active by default`() = runBlocking {
        coEvery { repository.findByEmail(any()) } returns null
        coEvery { repository.findByDriverLicense(any()) } returns null
        coEvery { repository.save(any()) } returnsArgument 0

        val result = useCase.execute(validRequest)

        assertTrue(result.isActive)
    }
}
