package com.solodev.fleet.modules.drivers.domain.model

import java.time.Instant
import java.util.UUID

@JvmInline
value class DriverId(val value: String) {
    init { require(value.isNotBlank()) { "Driver ID cannot be blank" } }
}

data class Driver(
    val id: DriverId,
    val userId: UUID? = null,          // optional link to users table (mobile app login)
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val licenseNumber: String,
    val licenseExpiry: Instant,
    val licenseClass: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.EPOCH,
) {
    init {
        require(firstName.isNotBlank()) { "First name cannot be blank" }
        require(lastName.isNotBlank()) { "Last name cannot be blank" }
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(phone.isNotBlank()) { "Phone cannot be blank" }
        require(licenseNumber.isNotBlank()) { "License number cannot be blank" }
    }

    val fullName: String get() = "$firstName $lastName"
}
