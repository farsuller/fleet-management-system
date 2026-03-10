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

    @Test
    fun `active customer is marked isActive true by default`() {
        val customer = sampleCustomer()
        assertTrue(customer.isActive)
    }

    @Test
    fun `copy with toggled isActive produces deactivated customer`() {
        val active = sampleCustomer(isActive = true)
        val deactivated = active.copy(isActive = false)
        assertFalse(deactivated.isActive)
        assertEquals(active.id, deactivated.id)
        assertEquals(active.email, deactivated.email)
    }

    @Test
    fun `copy with toggled isActive produces reactivated customer`() {
        val inactive = sampleCustomer(isActive = false)
        val reactivated = inactive.copy(isActive = true)
        assertTrue(reactivated.isActive)
        assertEquals(inactive.id, reactivated.id)
    }

    @Test
    fun `createdAt defaults to Instant EPOCH when not provided`() {
        val customer = sampleCustomer()
        assertEquals(Instant.EPOCH, customer.createdAt)
    }

    @Test
    fun `createdAt is stored when provided`() {
        val ts = Instant.parse("2025-01-15T08:00:00Z")
        val customer = sampleCustomer(createdAt = ts)
        assertEquals(ts, customer.createdAt)
    }

    private fun sampleCustomer(
        email: String = "maria@example.com",
        isActive: Boolean = true,
        createdAt: Instant = Instant.EPOCH,
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
        isActive = isActive,
        createdAt = createdAt,
    )
}

