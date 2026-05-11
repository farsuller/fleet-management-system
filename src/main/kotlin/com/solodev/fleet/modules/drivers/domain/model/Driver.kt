package com.solodev.fleet.modules.drivers.domain.model

import java.time.Instant
import java.util.UUID

@JvmInline
value class DriverId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Driver ID cannot be blank" }
    }
}

enum class DriverStatus {
    PENDING,
    APPROVED,
    REJECTED,
}

data class Driver(
    val id: DriverId,
    val userId: UUID? = null, // optional link to users table (mobile app login)
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String,
    val phone: String?,
    val licenseNumber: String? = null,
    val licenseExpiry: Instant? = null,
    val licenseClass: String? = null,
    val address: String? = null,
    val city: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val status: DriverStatus = DriverStatus.PENDING,
    val availabilityStatus: Boolean = true,
    val createdAt: Instant = Instant.EPOCH,
) {
    init {
        require(firstName == null || firstName.isNotBlank()) { "First name cannot be blank" }
        require(lastName == null || lastName.isNotBlank()) { "Last name cannot be blank" }
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(phone == null || phone.isNotBlank()) { "Phone cannot be blank" }
        require(licenseNumber == null || licenseNumber.isNotBlank()) { "License number cannot be blank" }
    }

    val fullName: String get() = "$firstName $lastName"
}
