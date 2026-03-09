package com.solodev.fleet.modules.drivers.domain.model

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

class DriverTest {

    @Test
    fun `DriverId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> {
            DriverId("")
        }
    }

    @Test
    fun `DriverId rejects whitespace-only value`() {
        assertFailsWith<IllegalArgumentException> {
            DriverId("   ")
        }
    }

    @Test
    fun `DriverId stores provided value`() {
        val id = DriverId("driver-001")
        assertEquals("driver-001", id.value)
    }

    @Test
    fun `Driver fullName concatenates first and last name`() {
        val driver = sampleDriver()
        assertEquals("Pedro Reyes", driver.fullName)
    }

    @Test
    fun `Driver is active by default`() {
        val driver = sampleDriver()
        assertTrue(driver.isActive)
    }

    @Test
    fun `Driver can be created as inactive`() {
        val driver = sampleDriver(isActive = false)
        assertFalse(driver.isActive)
    }

    @Test
    fun `Driver copy with toggled isActive produces deactivated driver`() {
        val active = sampleDriver(isActive = true)
        val deactivated = active.copy(isActive = false)
        assertFalse(deactivated.isActive)
        assertEquals(active.id, deactivated.id)
        assertEquals(active.email, deactivated.email)
    }

    @Test
    fun `Driver copy with toggled isActive produces reactivated driver`() {
        val inactive = sampleDriver(isActive = false)
        val reactivated = inactive.copy(isActive = true)
        assertTrue(reactivated.isActive)
        assertEquals(inactive.id, reactivated.id)
    }

    @Test
    fun `Driver rejects blank first name`() {
        assertFailsWith<IllegalArgumentException> {
            sampleDriver(firstName = "")
        }
    }

    @Test
    fun `Driver rejects blank last name`() {
        assertFailsWith<IllegalArgumentException> {
            sampleDriver(lastName = "")
        }
    }

    @Test
    fun `Driver rejects blank email`() {
        assertFailsWith<IllegalArgumentException> {
            sampleDriver(email = "")
        }
    }

    @Test
    fun `Driver rejects blank phone`() {
        assertFailsWith<IllegalArgumentException> {
            sampleDriver(phone = "")
        }
    }

    @Test
    fun `Driver rejects blank license number`() {
        assertFailsWith<IllegalArgumentException> {
            sampleDriver(licenseNumber = "")
        }
    }

    @Test
    fun `Driver createdAt defaults to Instant EPOCH`() {
        val driver = sampleDriver()
        assertEquals(Instant.EPOCH, driver.createdAt)
    }

    @Test
    fun `Driver stores optional address fields`() {
        val driver = sampleDriver().copy(
            address    = "456 Rizal Ave",
            city       = "Cebu",
            state      = "Cebu",
            postalCode = "6000",
            country    = "Philippines",
        )
        assertEquals("456 Rizal Ave", driver.address)
        assertEquals("Cebu", driver.city)
    }

    @Test
    fun `Driver userId is nullable`() {
        val driver = sampleDriver()
        assertNull(driver.userId)
    }

    private fun sampleDriver(
        firstName: String = "Pedro",
        lastName: String = "Reyes",
        email: String = "pedro@fleet.ph",
        phone: String = "+63917000001",
        licenseNumber: String = "LN-0001",
        isActive: Boolean = true,
    ) = Driver(
        id            = DriverId("driver-001"),
        firstName     = firstName,
        lastName      = lastName,
        email         = email,
        phone         = phone,
        licenseNumber = licenseNumber,
        licenseExpiry = Instant.parse("2030-01-01T00:00:00Z"),
        isActive      = isActive,
    )
}
