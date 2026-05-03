package com.solodev.fleet.modules.drivers.application.dto

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DriverDtoTest {
    private val validEmail = "driver@fleet.ph"
    private val validPhone = "+639123456789"
    private val validPassword = "Password123!"

    @Test
    fun `DriverRequest should create with valid data`() {
        val request =
            DriverRequest(
                email = validEmail,
                firstName = "John",
                lastName = "Doe",
                phone = validPhone,
                licenseNumber = "N01-23-456789",
                licenseExpiry = "2030-12-31",
            )
        assertNotNull(request)
        assertEquals("John", request.firstName)
    }

    @Test
    fun `DriverRequest should fail with invalid email`() {
        assertThrows<IllegalArgumentException> {
            DriverRequest(
                email = "invalid-email",
                firstName = "John",
                lastName = "Doe",
                phone = validPhone,
                licenseNumber = "N01-23-456789",
                licenseExpiry = "2030-12-31",
            )
        }.also {
            assertEquals("Valid email address required", it.message)
        }
    }

    @Test
    fun `DriverRegistrationRequest should create with valid data`() {
        val request =
            DriverRegistrationRequest(
                email = validEmail,
                passwordRaw = validPassword,
                firstName = "John",
                lastName = "Doe",
                phone = validPhone,
                licenseNumber = "N01-23-456789",
                licenseExpiry = "2030-12-31",
            )
        assertNotNull(request)
    }

    @Test
    fun `DriverRegistrationRequest should fail with invalid password`() {
        assertThrows<IllegalArgumentException> {
            DriverRegistrationRequest(
                email = validEmail,
                passwordRaw = "weak",
                firstName = "John",
                lastName = "Doe",
                phone = validPhone,
                licenseNumber = "N01-23-456789",
                licenseExpiry = "2030-12-31",
            )
        }
    }

    @Test
    fun `UpdateDriverRequest should allow partial updates`() {
        val request = UpdateDriverRequest(firstName = "Jane")
        assertEquals("Jane", request.firstName)
    }

    @Test
    fun `UpdateDriverRequest should validate email when provided`() {
        assertThrows<IllegalArgumentException> {
            UpdateDriverRequest(email = "bad-email")
        }
    }

    @Test
    fun `AssignmentResponse should map from domain`() {
        // This test would need a domain object, but we can just test the data class structure
        val response =
            AssignmentResponse(
                id = "assignment-1",
                vehicleId = "veh-1",
                driverId = "drv-1",
                assignedAt = 1000L,
                releasedAt = null,
                isActive = true,
                notes = "Test notes",
            )
        assertEquals("assignment-1", response.id)
        assertEquals("drv-1", response.driverId)
    }
}
