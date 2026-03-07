package com.solodev.fleet.modules.rentals.domain.model

import java.time.Instant
import org.junit.jupiter.api.Test
import kotlin.test.*

class CustomerTest {

    @Test
    fun `CustomerId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> {
            CustomerId("")
        }
    }

    @Test
    fun `Customer fullName concatenates first and last name`() {
        val customer = sampleCustomer()
        assertEquals("Maria Santos", customer.fullName)
    }

    @Test
    fun `Customer email is stored as-is`() {
        val customer = sampleCustomer(email = "maria@example.com")
        assertEquals("maria@example.com", customer.email)
    }

    @Test
    fun `Customer driver license number is stored`() {
        val customer = sampleCustomer()
        assertNotNull(customer.driverLicenseNumber)
    }

    @Test
    fun `inactive customer is marked isActive false`() {
        val customer = sampleCustomer(isActive = false)
        assertFalse(customer.isActive)
    }

    private fun sampleCustomer(
        email: String = "maria@example.com",
        isActive: Boolean = true
    ) = Customer(
        id = CustomerId("cust-001"),
        firstName = "Maria",
        lastName = "Santos",
        email = email,
        driverLicenseNumber = "DL12345678",
        driverLicenseExpiry = Instant.parse("2027-12-31T00:00:00Z"),
        phone = "+63912345678",
        address = "123 Main St",
        city = "Manila",
        country = "Philippines",
        isActive = isActive
    )
}
